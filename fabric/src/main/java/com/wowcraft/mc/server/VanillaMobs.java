package com.wowcraft.mc.server;

import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcScaling;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.mc.entity.WowNpcEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla Minecraft mobs join the WoW combat engine the first time they fight a player: they get a unit with
 * WoW-scaled health so abilities feel right against zombies, spiders and friends.
 */
final class VanillaMobs {
    private final McPlatform platform;
    private final Map<Integer, UnitState> units = new HashMap<>();
    private static final NpcTemplate TEMPLATE = new NpcTemplate("vanilla", "Creature", "Существо", NpcRank.NORMAL);

    VanillaMobs(McPlatform platform) {
        this.platform = platform;
    }

    boolean has(LivingEntity e) {
        return units.containsKey(e.getId());
    }

    UnitState get(LivingEntity e) {
        return units.get(e.getId());
    }

    /** Returns (creating if needed) the unit of a vanilla mob. levelHint 0 = auto. */
    UnitState ensure(LivingEntity e, int levelHint) {
        if (e instanceof PlayerEntity || e instanceof WowNpcEntity || e instanceof ArmorStandEntity || !e.isAlive()) return null;
        UnitState u = units.get(e.getId());
        if (u != null && platform.game.engine().unit(u.id) == u) return u;
        int level = levelHint > 0 ? levelHint : nearbyPlayerLevel(e);
        String name = e.getDisplayName().getString();
        LivingBody body = new LivingBody(platform, e);
        u = platform.game.engine().register(e.getId(), e.getUuid(), name, UnitKind.VANILLA, body);
        u.platform = e;
        u.templateId = "vanilla";
        u.level = level;
        u.team = e instanceof Monster ? "monsters" : "wildlife";
        float vanillaMax = e.getMaxHealth();
        NpcRank rank = vanillaMax >= 150 ? NpcRank.BOSS : vanillaMax >= 60 ? NpcRank.ELITE : NpcRank.NORMAL;
        u.boss = rank == NpcRank.BOSS;
        u.elite = rank == NpcRank.ELITE;
        double hp = NpcScaling.expectedPlayerDps(level) * 8 * Math.max(0.3, Math.min(6.0, vanillaMax / 20.0))
                * platform.game.config().vanillaMobHealthScale * (rank == NpcRank.BOSS ? 4 : 1);
        platform.game.engine().setMaxHealthDirect(u, Math.max(20, hp), true);
        double frac = Math.max(0.05, e.getHealth() / Math.max(1, vanillaMax));
        platform.game.engine().setHealth(u, u.maxHealth() * frac);
        u.npcArmorReduction = 0.1;
        u.npcAttackPower = NpcScaling.attackPower(TEMPLATE, level, Difficulty.WORLD, 1.0);
        units.put(e.getId(), u);
        return u;
    }

    private int nearbyPlayerLevel(LivingEntity e) {
        PlayerEntity p = e.getWorld().getClosestPlayer(e, 48);
        if (p != null && platform.game.session(p.getUuid()) != null) return platform.game.session(p.getUuid()).profile.level;
        return 1;
    }

    void remove(LivingEntity e) {
        UnitState u = units.remove(e.getId());
        if (u != null && platform.game.engine().unit(u.id) == u) platform.game.engine().remove(u);
    }

    /** Drops units of vanilla mobs that disappeared or calmed down. */
    void tick() {
        if (units.isEmpty()) return;
        for (var en : new ArrayList<>(units.entrySet())) {
            UnitState u = en.getValue();
            if (!(u.platform instanceof LivingEntity e) || e.isRemoved()) {
                units.remove(en.getKey());
                if (platform.game.engine().unit(u.id) == u) platform.game.engine().remove(u);
            }
        }
    }
}
