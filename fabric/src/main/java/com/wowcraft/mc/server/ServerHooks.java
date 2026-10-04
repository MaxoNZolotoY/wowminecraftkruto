package com.wowcraft.mc.server;

import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.game.GameServer;
import com.wowcraft.core.game.PlayerSession;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.mc.WowCraftMod;
import com.wowcraft.mc.entity.WowNpcEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

/** Minecraft events -> game server: joins, deaths, vanilla damage, melee clicks. */
public final class ServerHooks {
    private ServerHooks() {
    }

    static GameServer game() {
        return WowCraftMod.game();
    }

    static McPlatform platform() {
        return WowCraftMod.bridge();
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (game() == null) return;
            ServerPlayerEntity p = handler.player;
            join(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (game() == null) return;
            ServerPlayerEntity p = handler.player;
            PlayerSession s = game().session(p.getUuid());
            platform().restoreGhost(p);
            if (s != null) {
                WowCraftMod.persistence().saveProfile(s.profile);
                game().leave(p.getUuid());
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (game() == null) return;
            PlayerSession s = game().session(newPlayer.getUuid());
            if (s == null || s.unit == null) return;
            if (s.unit.body instanceof LivingBody b) b.entity = newPlayer;
            s.unit.platform = newPlayer;
            game().onRespawn(newPlayer.getUuid());
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ServerHooks::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (game() == null || platform().bypassDamage) return true;
            if (entity instanceof ServerPlayerEntity p) {
                PlayerSession s = game().session(p.getUuid());
                if (s == null || s.unit == null) return true;
                if (s.ghost) {
                    p.setHealth(1);
                    return false;
                }
                if (s.unit.isAlive()) {
                    game().engine().die(s.unit, null);
                    if (s.ghost) {
                        p.setHealth(1);
                        return false;
                    }
                }
                return true;
            }
            return true;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || game() == null || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            PlayerSession s = game().session(sp.getUuid());
            if (s == null || s.unit == null || !s.profile.classChosen || sp.isSpectator() || sp.isCreative() && !s.profile.classChosen) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity le) || entity == player) return ActionResult.PASS;
            UnitState target = platform().unitOf(le);
            if (target == null) return ActionResult.PASS;
            game().engine().setTarget(s.unit, target);
            if (game().engine().isHostile(s.unit, target) && target.isAlive()) game().engine().startAutoAttack(s.unit, target);
            return ActionResult.FAIL;
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (game() == null) return;
            if (entity instanceof LivingEntity le && !(entity instanceof PlayerEntity) && !(entity instanceof WowNpcEntity)) platform().vanilla.remove(le);
        });
    }

    static void join(ServerPlayerEntity p) {
        GameServer g = game();
        PlayerProfile profile = WowCraftMod.persistence().loadProfile(p.getUuid());
        UnitState u = g.engine().unit(p.getId());
        if (u == null || !p.getUuid().equals(u.uuid)) {
            if (u != null) g.engine().remove(u);
            u = g.engine().register(p.getId(), p.getUuid(), p.getName().getString(), UnitKind.PLAYER, new LivingBody(platform(), p));
        }
        u.platform = p;
        g.join(p.getUuid(), p.getName().getString(), u, profile, "en_us");
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        GameServer g = game();
        McPlatform pl = platform();
        if (g == null || pl.bypassDamage) return true;
        String type = source.getName();
        if (entity instanceof ServerPlayerEntity p) {
            PlayerSession s = g.session(p.getUuid());
            if (s == null || s.unit == null || !s.profile.classChosen) return true;
            if (s.ghost) return false;
            if ("outOfWorld".equals(type) || "genericKill".equals(type)) return true;
            Entity attacker = source.getAttacker();
            if (attacker instanceof PlayerEntity || attacker instanceof WowNpcEntity) return false;
            UnitState from = attacker instanceof LivingEntity le ? pl.vanilla.ensure(le, s.profile.level) : null;
            double fraction = amount / Math.max(1.0f, p.getMaxHealth());
            g.vanillaDamage(s.unit, fraction, from);
            return false;
        }
        if (entity instanceof WowNpcEntity) return true;
        UnitState u = pl.vanilla.get(entity);
        if (u == null) return true;
        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity) return false;
        UnitState from = attacker instanceof LivingEntity le ? pl.unitOf(le) : null;
        g.vanillaDamage(u, amount / Math.max(1.0f, entity.getMaxHealth()), from);
        return false;
    }
}
