package com.wowcraft.mc.server;

import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.game.GameServer;
import com.wowcraft.core.game.Platform;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.instance.Palette;
import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.util.Vec3;
import com.wowcraft.mc.WowCraftMod;
import com.wowcraft.mc.block.WowBlocks;
import com.wowcraft.mc.entity.WowEntities;
import com.wowcraft.mc.entity.WowNpcEntity;
import com.wowcraft.mc.item.WowItems;
import com.wowcraft.mc.item.WowWeaponItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The Minecraft side of the game: entities, worlds, blocks, inventories, networking. */
public final class McPlatform implements Platform {
    public static final RegistryKey<World> INSTANCE_WORLD = RegistryKey.of(RegistryKeys.WORLD, new Identifier(WowCraftMod.ID, "instances"));
    private static final UUID SPEED_MODIFIER = UUID.fromString("6f1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d");

    final MinecraftServer server;
    GameServer game;
    final BlockBuilder builder = new BlockBuilder();
    final VanillaMobs vanilla = new VanillaMobs(this);
    /** Previous game mode of ghosts (restored on resurrection). */
    private final Map<UUID, GameMode> ghostModes = new HashMap<>();
    /** Last WoW weapon held in the main hand (players may switch to tools without losing weapon stats). */
    private final Map<UUID, ItemData> lastWeapon = new HashMap<>();
    /** Set while the platform deals a lethal hit itself, so damage events let it through. */
    boolean bypassDamage;

    public McPlatform(MinecraftServer server) {
        this.server = server;
    }

    public GameServer game() {
        return game;
    }

    ServerWorld world(String key) {
        if (key == null) return server.getOverworld();
        ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(key)));
        return w != null ? w : server.getOverworld();
    }

    ServerPlayerEntity player(UUID uuid) {
        return server.getPlayerManager().getPlayer(uuid);
    }

    // ------------------------------------------------------------------ queries

    @Override
    public boolean lineOfSight(String worldKey, Vec3 from, Vec3 to) {
        ServerWorld w = world(worldKey);
        double dist = from.distance(to);
        if (dist < 1.5) return true;
        int steps = (int) Math.ceil(dist / 0.3);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        long lastKey = Long.MIN_VALUE;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            pos.set(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t, from.z() + (to.z() - from.z()) * t);
            long key = pos.asLong();
            if (key == lastKey) continue;
            lastKey = key;
            BlockState s = w.getBlockState(pos);
            if (!s.isAir() && s.isSolidBlock(w, pos)) return false;
        }
        return true;
    }

    @Override
    public Vec3 safeDestination(String worldKey, Vec3 from, Vec3 to) {
        ServerWorld w = world(worldKey);
        double dist = from.horizontalDistance(to);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.4));
        Vec3 last = from;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = from.lerp(to, t);
            pos.set(p.x(), p.y() + 0.1, p.z());
            if (w.getBlockState(pos).isSolidBlock(w, pos)) break;
            pos.set(p.x(), p.y() + 1.1, p.z());
            if (w.getBlockState(pos).isSolidBlock(w, pos)) break;
            last = p;
        }
        return last;
    }

    @Override
    public String instanceWorld() {
        return INSTANCE_WORLD.getValue().toString();
    }

    @Override
    public String defaultWorld() {
        return World.OVERWORLD.getValue().toString();
    }

    // ------------------------------------------------------------------ players

    @Override
    public void send(UUID player, Object message) {
        ServerPlayerEntity p = player(player);
        if (p != null) Net.send(p, message);
    }

    @Override
    public void teleport(UUID uuid, String worldKey, Vec3 pos, float yaw) {
        ServerPlayerEntity p = player(uuid);
        if (p == null) return;
        ServerWorld w = world(worldKey);
        double x, y, z;
        if (pos == null) {
            BlockPos spawn = w.getSpawnPos();
            x = spawn.getX() + 0.5;
            z = spawn.getZ() + 0.5;
            y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        } else {
            x = pos.x();
            y = pos.y();
            z = pos.z();
        }
        p.teleport(w, x, y, z, yaw, p.getPitch());
        p.fallDistance = 0;
    }

    @Override
    public void setGhost(UUID uuid, boolean ghost) {
        ServerPlayerEntity p = player(uuid);
        if (p == null) return;
        if (ghost) {
            if (!ghostModes.containsKey(uuid)) ghostModes.put(uuid, p.interactionManager.getGameMode());
            p.changeGameMode(GameMode.SPECTATOR);
        } else {
            GameMode prev = ghostModes.remove(uuid);
            if (p.isSpectator()) p.changeGameMode(prev != null && prev != GameMode.SPECTATOR ? prev : GameMode.SURVIVAL);
            p.setHealth(p.getMaxHealth());
        }
    }

    /** Restores a ghost's game mode on logout. */
    void restoreGhost(ServerPlayerEntity p) {
        GameMode prev = ghostModes.remove(p.getUuid());
        if (prev != null && p.isSpectator()) p.changeGameMode(prev);
    }

    private static final EquipmentSlot[] VANILLA_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    static EquipmentSlot toMc(EquipSlot s) {
        return switch (s) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            case MAIN_HAND -> EquipmentSlot.MAINHAND;
            case OFF_HAND -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    static EquipSlot toWow(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> EquipSlot.HEAD;
            case CHEST -> EquipSlot.CHEST;
            case LEGS -> EquipSlot.LEGS;
            case FEET -> EquipSlot.FEET;
            case MAINHAND -> EquipSlot.MAIN_HAND;
            case OFFHAND -> EquipSlot.OFF_HAND;
        };
    }

    @Override
    public Equipment readEquipment(UUID uuid) {
        Equipment eq = new Equipment();
        ServerPlayerEntity p = player(uuid);
        if (p == null) return eq;
        for (EquipmentSlot s : VANILLA_SLOTS) {
            ItemStack stack = p.getEquippedStack(s);
            ItemData d = WowItems.read(stack);
            if (s == EquipmentSlot.MAINHAND) {
                if (d != null && stack.getItem() instanceof WowWeaponItem) lastWeapon.put(uuid, d);
                else d = lastWeapon.get(uuid);
            }
            if (d != null) {
                EquipSlot ws = toWow(s);
                if (d.equipType() != null && d.equipType().slots.contains(ws)) eq.set(ws, d);
            }
        }
        return eq;
    }

    @Override
    public void equip(UUID uuid, EquipSlot slot, ItemData item) {
        ServerPlayerEntity p = player(uuid);
        EquipmentSlot es = toMc(slot);
        if (p == null || es == null) return;
        ItemStack old = p.getEquippedStack(es);
        p.equipStack(es, item == null ? ItemStack.EMPTY : WowItems.toStack(item));
        if (!old.isEmpty()) p.getInventory().offerOrDrop(old);
        if (es == EquipmentSlot.MAINHAND) {
            if (item != null) lastWeapon.put(uuid, item);
            else lastWeapon.remove(uuid);
        }
    }

    @Override
    public void replaceEquipped(UUID uuid, EquipSlot slot, ItemData item) {
        ServerPlayerEntity p = player(uuid);
        EquipmentSlot es = toMc(slot);
        if (p == null || es == null) return;
        ItemStack cur = p.getEquippedStack(es);
        if (!cur.isEmpty() && cur.getItem() == WowItems.itemFor(item)) WowItems.write(cur, item);
        else if (es == EquipmentSlot.MAINHAND && lastWeapon.containsKey(uuid)) lastWeapon.put(uuid, item);
        else p.equipStack(es, WowItems.toStack(item));
        if (es == EquipmentSlot.MAINHAND) lastWeapon.put(uuid, item);
    }

    @Override
    public void giveItem(UUID uuid, ItemData item) {
        ServerPlayerEntity p = player(uuid);
        if (p != null) p.getInventory().offerOrDrop(WowItems.toStack(item));
    }

    @Override
    public void giveVanilla(UUID uuid, String itemId, int count) {
        ServerPlayerEntity p = player(uuid);
        if (p == null) return;
        var item = Registries.ITEM.get(new Identifier(itemId));
        p.getInventory().offerOrDrop(new ItemStack(item, count));
    }

    void updatePlayerSpeed(ServerPlayerEntity p, UnitState u) {
        EntityAttributeInstance attr = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        double value = u.canMove() ? Math.max(-0.9, u.stats().speedPct / 100.0) : -1.0;
        EntityAttributeModifier cur = attr.getModifier(SPEED_MODIFIER);
        if (cur != null && Math.abs(cur.getValue() - value) < 1e-3) return;
        attr.removeModifier(SPEED_MODIFIER);
        if (Math.abs(value) > 1e-3) {
            attr.addTemporaryModifier(new EntityAttributeModifier(SPEED_MODIFIER, "wowcraft speed", value, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ------------------------------------------------------------------ npcs

    @Override
    public UnitState createNpcBody(String worldKey, Vec3 pos, float yaw, NpcTemplate template, UnitKind kind, String displayName) {
        ServerWorld w = world(worldKey);
        EntityType<WowNpcEntity> type = WowEntities.TYPES.get(template.body);
        WowNpcEntity e = type.create(w);
        if (e == null) return null;
        e.refreshPositionAndAngles(pos.x(), pos.y(), pos.z(), yaw, 0);
        e.setHeadYaw(yaw);
        e.setBodyYaw(yaw);
        e.setup(template, kind, displayName);
        if (!w.spawnEntity(e)) return null;
        LivingBody body = new LivingBody(this, e);
        UnitState u = game.engine().register(e.getId(), e.getUuid(), displayName, kind, body);
        u.platform = e;
        e.unit = u;
        return u;
    }

    @Override
    public void showGear(UnitState unit, Equipment equipment) {
        if (!(unit.platform instanceof WowNpcEntity e) || equipment == null) return;
        for (EquipmentSlot s : VANILLA_SLOTS) {
            ItemData d = equipment.get(toWow(s));
            e.equipStack(s, d != null ? WowItems.toStack(d) : ItemStack.EMPTY);
            e.setEquipmentDropChance(s, 0f);
        }
    }

    @Override
    public void removeBody(UnitState unit) {
        if (unit.platform instanceof WowNpcEntity e) {
            e.unit = null;
            e.discard();
        }
    }

    @Override
    public void moveBody(UnitState unit, String worldKey, Vec3 pos) {
        if (!(unit.platform instanceof WowNpcEntity e) || !(unit.body instanceof LivingBody body)) return;
        ServerWorld target = world(worldKey);
        if (e.getWorld() == target) {
            e.requestTeleport(pos.x(), pos.y(), pos.z());
            e.getNavigation().stop();
            return;
        }
        @SuppressWarnings("unchecked")
        EntityType<WowNpcEntity> type = (EntityType<WowNpcEntity>) e.getType();
        WowNpcEntity n = type.create(target);
        if (n == null) return;
        int id = e.getId();
        n.copyVisuals(e);
        n.refreshPositionAndAngles(pos.x(), pos.y(), pos.z(), e.getYaw(), 0);
        e.transferring = true;
        e.unit = null;
        e.discard();
        n.setId(id);
        n.unit = unit;
        unit.platform = n;
        body.entity = n;
        target.spawnEntity(n);
    }

    // ------------------------------------------------------------------ health / death

    void mirrorHealth(LivingBody body, UnitState unit, HitResult cause) {
        LivingEntity e = body.entity;
        if (unit.isDead()) return;
        float max = e.getMaxHealth();
        float hp = (float) Math.max(max * unit.healthFraction(), 0.5f);
        if (Math.abs(e.getHealth() - hp) > 0.01f) e.setHealth(hp);
        if (cause != null && !cause.heal && cause.amount > 0 && !cause.periodic && e.getWorld() instanceof ServerWorld sw) {
            sw.sendEntityDamage(e, e.getDamageSources().generic());
        }
    }

    void onBodyKilled(LivingBody body, UnitState unit, UnitState killer) {
        LivingEntity e = body.entity;
        if (e instanceof WowNpcEntity npc) {
            if (unit.kind == UnitKind.BOT) {
                npc.setWowDead(true);
                npc.getNavigation().stop();
            } else {
                npc.setHealth(0);
            }
            return;
        }
        if (e instanceof ServerPlayerEntity p) {
            if (game.isGhost(p.getUuid())) return;
            lethal(p, killer);
            return;
        }
        lethal(e, killer);
    }

    private void lethal(LivingEntity e, UnitState killer) {
        DamageSource src = killer != null && killer.master().platform instanceof PlayerEntity pk
                ? e.getDamageSources().playerAttack(pk) : e.getDamageSources().generic();
        bypassDamage = true;
        try {
            e.damage(src, Float.MAX_VALUE);
            if (e.isAlive() && !(e instanceof PlayerEntity)) e.setHealth(0);
        } finally {
            bypassDamage = false;
        }
    }

    /** An NPC body took vanilla damage (lava, fall, void, arrows). */
    public void onNpcDamaged(WowNpcEntity e, DamageSource source, float amount) {
        UnitState u = e.unit;
        if (u == null || u.isDead() || game == null) return;
        if (source.getName().equals("outOfWorld") || source.getName().equals("genericKill")) {
            game.engine().die(u, null);
            return;
        }
        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity) return;
        UnitState from = attacker instanceof LivingEntity le ? unitOf(le) : null;
        game.engine().environmentalDamage(u, u.maxHealth() * Math.min(0.5, amount / 40.0), from);
    }

    /** An NPC body was removed from the world by Minecraft (killed, unloaded, /kill). */
    public void onNpcRemoved(WowNpcEntity e) {
        UnitState u = e.unit;
        e.unit = null;
        if (u == null || game == null) return;
        if (game.engine().unit(u.id) == u) {
            if (u.kind == UnitKind.BOT) game.bots().despawn(u);
            else game.engine().remove(u);
        }
    }

    /** The combat unit of any living entity (players, WoW NPCs, vanilla mobs that joined combat). */
    @Override
    public UnitState unitByEntityId(int entityId, UnitState near) {
        ServerWorld w = world(near.worldKey());
        Entity e = w.getEntityById(entityId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return null;
        if (near.position().distance(new Vec3(le.getX(), le.getY(), le.getZ())) > 80) return null;
        return unitOf(le);
    }

    public UnitState unitOf(LivingEntity e) {
        if (e instanceof WowNpcEntity n) return n.unit;
        if (e instanceof ServerPlayerEntity p) {
            var s = game.session(p.getUuid());
            return s != null ? s.unit : null;
        }
        return vanilla.ensure(e, 0);
    }

    // ------------------------------------------------------------------ world

    @Override
    public void build(String worldKey, Vec3 origin, Layout layout, Palette palette, Runnable done) {
        builder.build(world(worldKey), origin, layout, palette, done);
    }

    @Override
    public void clear(String worldKey, Vec3 origin, Layout layout, Runnable done) {
        builder.clear(world(worldKey), origin, layout, done);
    }

    @Override
    public void forceLoad(String worldKey, Vec3 origin, Layout layout, boolean load) {
        ServerWorld w = world(worldKey);
        int x0 = ((int) Math.floor(origin.x()) + layout.minX - 2) >> 4, x1 = ((int) Math.floor(origin.x()) + layout.maxX + 2) >> 4;
        int z0 = ((int) Math.floor(origin.z()) + layout.minZ - 2) >> 4, z1 = ((int) Math.floor(origin.z()) + layout.maxZ + 2) >> 4;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++) w.setChunkForced(cx, cz, load);
    }

    @Override
    public void marker(String worldKey, Vec3 pos, String kind, boolean place) {
        ServerWorld w = world(worldKey);
        BlockPos p = BlockPos.ofFloored(pos.x(), pos.y(), pos.z());
        BlockState state = switch (kind) {
            case "font_of_power" -> WowBlocks.FONT_OF_POWER.getDefaultState();
            case "exit_portal" -> WowBlocks.EXIT_PORTAL.getDefaultState();
            case "flag_blue", "node_blue" -> Blocks.BLUE_BANNER.getDefaultState();
            case "flag_red", "node_red" -> Blocks.RED_BANNER.getDefaultState();
            case "node_neutral" -> Blocks.WHITE_BANNER.getDefaultState();
            default -> Blocks.GLOWSTONE.getDefaultState();
        };
        w.setBlockState(p, place ? state : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        if (kind.equals("exit_portal")) w.setBlockState(p.up(), place ? state : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    @Override
    public void sound(String worldKey, Vec3 pos, String sound) {
        SoundEvent ev = switch (sound) {
            case "level_up" -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case "keystone_start" -> SoundEvents.BLOCK_BEACON_ACTIVATE;
            case "keystone_go", "pvp_start" -> SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
            case "flag_capture" -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            default -> SoundEvents.BLOCK_BELL_USE;
        };
        world(worldKey).playSound(null, pos.x(), pos.y(), pos.z(), ev, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public void log(String message) {
        WowCraftMod.LOG.info(message);
    }

    // ------------------------------------------------------------------ tick

    public void attach(GameServer game) {
        this.game = game;
        // players already online (e.g. /reload or integrated server start order)
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ServerHooks.join(p);
    }

    public void tick() {
        preTick();
        game.tick();
        postTick();
    }

    /** Runs queued block work synchronously (server shutdown). */
    public void finishPendingWork() {
        int guard = 0;
        while (builder.busy() && guard++ < 10_000) builder.tick();
    }

    /** Before the game tick: movement flags, health mirroring for players. */
    void preTick() {
        for (UnitState u : game.engine().units()) {
            if (u.body instanceof LivingBody b) b.updateMoving();
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            var s = game.session(p.getUuid());
            // creative players can't be killed by Minecraft: a WoW death outside the ghost system just restores them
            if (s != null && s.unit != null && !s.ghost && s.unit.isDead() && p.isCreative() && !p.isDead()) {
                game.engine().resurrect(s.unit, s.unit, 1.0);
            }
            if (s == null || s.unit == null || s.ghost || s.unit.isDead() || p.isDead()) continue;
            float hp = (float) Math.max(0.5, p.getMaxHealth() * s.unit.healthFraction());
            if (Math.abs(p.getHealth() - hp) > 0.05f) p.setHealth(hp);
        }
        vanilla.tick();
    }

    void postTick() {
        builder.tick();
    }
}
