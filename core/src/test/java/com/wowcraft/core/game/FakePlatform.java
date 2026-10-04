package com.wowcraft.core.game;

import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.MoveIntent;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.instance.Palette;
import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory Minecraft stand-in: entities, inventories, messages, simple movement. */
final class FakePlatform implements Platform {
    GameServer server;
    int nextEntityId = 1000;
    final Map<UUID, Equipment> worn = new HashMap<>();
    final Map<UUID, List<ItemData>> inventories = new HashMap<>();
    final Map<UUID, List<Object>> messages = new HashMap<>();
    final Map<UUID, UnitState> players = new HashMap<>();
    final List<String> log = new ArrayList<>();
    final Map<String, Integer> markers = new HashMap<>();
    int built, cleared;

    CombatEngine engine() {
        return server.engine();
    }

    UnitState addPlayer(UUID uuid, String name, Vec3 pos) {
        FakeBody body = new FakeBody(defaultWorld(), pos, 1.8);
        UnitState u = engine().register(nextEntityId++, uuid, name, UnitKind.PLAYER, body);
        players.put(uuid, u);
        return u;
    }

    @Override
    public boolean lineOfSight(String worldKey, Vec3 from, Vec3 to) {
        return true;
    }

    @Override
    public Vec3 safeDestination(String worldKey, Vec3 from, Vec3 to) {
        return to;
    }

    @Override
    public String instanceWorld() {
        return "wowcraft:instances";
    }

    @Override
    public String defaultWorld() {
        return "minecraft:overworld";
    }

    @Override
    public void send(UUID player, Object message) {
        messages.computeIfAbsent(player, k -> new ArrayList<>()).add(message);
    }

    @Override
    public void teleport(UUID player, String worldKey, Vec3 pos, float yaw) {
        UnitState u = players.get(player);
        if (u == null) return;
        FakeBody b = (FakeBody) u.body;
        b.world = worldKey;
        if (pos != null) b.pos = pos;
        b.yaw = yaw;
    }

    @Override
    public void setGhost(UUID player, boolean ghost) {
    }

    @Override
    public Equipment readEquipment(UUID player) {
        return worn.computeIfAbsent(player, k -> new Equipment());
    }

    @Override
    public void equip(UUID player, EquipSlot slot, ItemData item) {
        Equipment eq = readEquipment(player);
        ItemData old = eq.get(slot);
        if (old != null) giveItem(player, old);
        eq.set(slot, item);
    }

    @Override
    public void replaceEquipped(UUID player, EquipSlot slot, ItemData item) {
        readEquipment(player).set(slot, item);
    }

    @Override
    public void giveItem(UUID player, ItemData item) {
        inventories.computeIfAbsent(player, k -> new ArrayList<>()).add(item);
    }

    @Override
    public UnitState createNpcBody(String worldKey, Vec3 pos, float yaw, NpcTemplate template, UnitKind kind, String displayName) {
        FakeBody body = new FakeBody(worldKey, pos, 1.8 * template.scale);
        body.yaw = yaw;
        return engine().register(nextEntityId++, UUID.randomUUID(), displayName, kind, body);
    }

    @Override
    public void removeBody(UnitState unit) {
        if (unit.body instanceof FakeBody fb) fb.removed = true;
    }

    @Override
    public void moveBody(UnitState unit, String worldKey, Vec3 pos) {
        if (unit.body instanceof FakeBody fb) {
            fb.world = worldKey;
            fb.pos = pos;
        }
    }

    @Override
    public void build(String worldKey, Vec3 origin, Layout layout, Palette palette, Runnable done) {
        built++;
        done.run();
    }

    @Override
    public void clear(String worldKey, Vec3 origin, Layout layout, Runnable done) {
        cleared++;
        done.run();
    }

    @Override
    public void forceLoad(String worldKey, Vec3 origin, Layout layout, boolean load) {
    }

    @Override
    public void marker(String worldKey, Vec3 pos, String kind, boolean place) {
        markers.merge(kind, place ? 1 : -1, Integer::sum);
    }

    @Override
    public void sound(String worldKey, Vec3 pos, String sound) {
    }

    @Override
    public void log(String message) {
        log.add(message);
    }

    /** Applies MoveIntents like the Minecraft entity AI would (straight lines, 0.28 blocks/tick). */
    void moveUnits() {
        for (UnitState u : engine().unitsSnapshot()) {
            if (!(u.body instanceof FakeBody b) || b.removed) continue;
            b.moving = false;
            if (u.isDead() || !u.canMove()) continue;
            MoveIntent m = u.move;
            if (m == null) continue;
            Vec3 dest = null;
            double stopAt = 0.4;
            switch (m.kind()) {
                case CHASE, FOLLOW -> {
                    if (m.target() != null && m.target().body != null) {
                        dest = m.target().position();
                        stopAt = Math.max(1.0, m.range() - 0.5);
                    }
                }
                case MOVE_TO -> dest = m.point();
                case FLEE -> {
                    if (m.point() != null) dest = b.pos.add(b.pos.sub(m.point()).normalize().mul(3));
                }
                default -> {
                }
            }
            if (dest == null) continue;
            double d = b.pos.horizontalDistance(dest);
            if (d <= stopAt) continue;
            double base = 0.28;
            if (u.isNpcLike() && u.templateId != null) {
                com.wowcraft.core.npc.NpcTemplate t = com.wowcraft.core.npc.NpcRegistry.get(u.templateId);
                if (t != null) base = t.stationary ? 0 : t.moveSpeed;
            }
            double speed = base * (m.speedMult() <= 0 ? 1 : m.speedMult()) * (1 + u.stats().speedPct / 100.0);
            double step = Math.min(speed, d - stopAt + 0.05);
            Vec3 dir = new Vec3(dest.x() - b.pos.x(), 0, dest.z() - b.pos.z()).normalize();
            b.pos = b.pos.add(dir.mul(step));
            b.yaw = (float) b.pos.yawTo(dest);
            b.moving = true;
        }
    }

    @SuppressWarnings("unchecked")
    <T> List<T> sent(UUID player, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Object o : messages.getOrDefault(player, List.of())) if (type.isInstance(o)) out.add((T) o);
        return out;
    }
}
