package com.wowcraft.core.game;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.mythic.MythicScore;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Parties and raids: invites, leader, roles, ready checks, bots as members. */
public final class GroupManager {
    private final GameServer server;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<UUID, String> memberOf = new HashMap<>();
    private int nextId = 1;

    GroupManager(GameServer server) {
        this.server = server;
    }

    public Group group(String id) {
        return id == null ? null : groups.get(id);
    }

    public Collection<Group> all() {
        return groups.values();
    }

    public String groupIdOf(UUID uuid) {
        return memberOf.get(uuid);
    }

    public Group groupOf(UUID uuid) {
        return group(memberOf.get(uuid));
    }

    public Group groupOfUnit(UnitState u) {
        if (u == null) return null;
        UnitState m = u.master();
        return m.groupId == null ? null : groups.get(m.groupId);
    }

    /** Units (players + bots) of the unit's group; just the unit itself when solo. */
    public List<UnitState> unitsOfGroup(UnitState me) {
        List<UnitState> out = new ArrayList<>();
        Group g = groupOfUnit(me);
        if (g == null) {
            out.add(me.master());
            return out;
        }
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            if (s != null && s.unit != null) out.add(s.unit);
        }
        out.addAll(g.bots);
        return out;
    }

    /** Creates a group led by the given player if they are not in one yet. */
    public Group ensureGroup(UUID leader) {
        Group g = groupOf(leader);
        if (g != null) return g;
        g = new Group("g" + (nextId++), leader);
        groups.put(g.id, g);
        memberOf.put(leader, g.id);
        setUnitGroup(leader, g.id);
        return g;
    }

    private void setUnitGroup(UUID uuid, String groupId) {
        PlayerSession s = server.session(uuid);
        if (s != null && s.unit != null) {
            s.unit.groupId = groupId;
            for (UnitState pet : s.unit.pets()) pet.groupId = groupId;
        }
    }

    public int maxSize(Group g) {
        return g.raid ? server.config.maxRaidSize : server.config.maxPartySize;
    }

    // ------------------------------------------------------------------ actions

    public void invite(PlayerSession from, String targetName) {
        PlayerSession target = server.sessionByName(targetName);
        if (target == null) {
            server.msg(from, L10n.of("Player not found: " + targetName, "Игрок не найден: " + targetName), 0xFFFF4040);
            return;
        }
        if (target == from) return;
        Group g = groupOf(from.uuid);
        if (g != null && !g.leader.equals(from.uuid)) {
            server.msg(from, L10n.of("Only the leader can invite.", "Приглашать может только лидер."), 0xFFFF4040);
            return;
        }
        if (groupOf(target.uuid) != null) {
            server.msg(from, L10n.of(target.name + " is already in a group.", target.name + " уже в группе."), 0xFFFF4040);
            return;
        }
        if (g != null && g.size() >= maxSize(g)) {
            server.msg(from, L10n.of("Your group is full.", "Ваша группа заполнена."), 0xFFFF4040);
            return;
        }
        target.pendingInviteFrom = from.uuid.toString();
        target.pendingInviteAt = server.engine.now();
        S2C.Invite inv = new S2C.Invite();
        inv.from = from.uuid.toString();
        inv.fromName = from.name;
        server.send(target, inv);
        server.msg(target, L10n.of(from.name + " invites you to a group. /wow accept or /wow decline", from.name + " приглашает вас в группу. /wow accept или /wow decline"),
                0xFF80C0FF);
        server.msg(from, L10n.of("You invited " + target.name + ".", "Вы пригласили игрока " + target.name + "."), 0xFF80C0FF);
    }

    public void accept(PlayerSession s) {
        if (s.pendingInviteFrom == null || server.engine.now() - s.pendingInviteAt > 120) {
            server.msg(s, L10n.of("You have no pending invitation.", "У вас нет приглашений."), 0xFFFF4040);
            return;
        }
        UUID leader = UUID.fromString(s.pendingInviteFrom);
        s.pendingInviteFrom = null;
        if (server.session(leader) == null) return;
        addMember(ensureGroup(leader), s.uuid);
    }

    /** Adds a player to a group (invite accepted, LFG). */
    public boolean addMember(Group g, UUID uuid) {
        if (g.members.contains(uuid)) return true;
        if (g.size() >= maxSize(g)) {
            // make room by removing a bot
            if (!g.bots.isEmpty()) server.bots.removeBot(g, g.bots.get(g.bots.size() - 1));
            else return false;
        }
        Group old = groupOf(uuid);
        if (old != null) leave(uuid, false);
        g.members.add(uuid);
        memberOf.put(uuid, g.id);
        setUnitGroup(uuid, g.id);
        PlayerSession s = server.session(uuid);
        broadcast(g, L10n.of((s != null ? s.name : "?") + " joins the group.", (s != null ? s.name : "?") + " присоединяется к группе."), 0xFF80C0FF);
        sendGroupToAll(g);
        return true;
    }

    public void decline(PlayerSession s) {
        if (s.pendingInviteFrom == null) return;
        UUID leader = UUID.fromString(s.pendingInviteFrom);
        s.pendingInviteFrom = null;
        server.msg(leader, L10n.of(s.name + " declines your invitation.", s.name + " отклоняет приглашение."), 0xFFFF8040);
    }

    public void leave(UUID uuid, boolean announce) {
        Group g = groupOf(uuid);
        if (g == null) return;
        g.members.remove(uuid);
        g.roles.remove(uuid);
        memberOf.remove(uuid);
        setUnitGroup(uuid, null);
        PlayerSession s = server.session(uuid);
        if (announce) broadcast(g, L10n.of((s != null ? s.name : "?") + " leaves the group.", (s != null ? s.name : "?") + " покидает группу."), 0xFF80C0FF);
        server.instances.onLeftGroup(uuid, g);
        server.lfg.onGroupChanged(g);
        if (g.members.isEmpty()) {
            disband(g);
        } else {
            if (g.leader.equals(uuid)) g.leader = g.members.iterator().next();
            if (g.members.size() == 1 && g.bots.isEmpty() && !server.instances.groupHasRun(g)) disband(g);
            else sendGroupToAll(g);
        }
        sendGroup(uuid);
    }

    public void disband(Group g) {
        for (UnitState bot : new ArrayList<>(g.bots)) server.bots.removeBot(g, bot);
        for (UUID m : new ArrayList<>(g.members)) {
            memberOf.remove(m);
            setUnitGroup(m, null);
            sendGroup(m);
        }
        g.members.clear();
        groups.remove(g.id);
        server.lfg.onGroupChanged(g);
    }

    public void kick(PlayerSession by, String name) {
        Group g = groupOf(by.uuid);
        if (g == null || !g.leader.equals(by.uuid)) return;
        for (UnitState bot : new ArrayList<>(g.bots)) {
            if (bot.name.equalsIgnoreCase(name)) {
                server.bots.removeBot(g, bot);
                sendGroupToAll(g);
                return;
            }
        }
        PlayerSession t = server.sessionByName(name);
        UUID target = t != null ? t.uuid : null;
        if (target == null) {
            for (UUID m : g.members) if (name.equalsIgnoreCase(server.worldData().names.get(m.toString()))) target = m;
        }
        if (target == null || target.equals(by.uuid)) return;
        leave(target, true);
        server.instances.kickFromRun(target);
    }

    public void promote(PlayerSession by, String name) {
        Group g = groupOf(by.uuid);
        if (g == null || !g.leader.equals(by.uuid)) return;
        PlayerSession t = server.sessionByName(name);
        if (t == null || !g.members.contains(t.uuid)) return;
        g.leader = t.uuid;
        broadcast(g, L10n.of(t.name + " is now the group leader.", t.name + " теперь лидер группы."), 0xFF80C0FF);
        sendGroupToAll(g);
    }

    public void convertToRaid(PlayerSession by) {
        Group g = ensureGroup(by.uuid);
        if (!g.leader.equals(by.uuid)) return;
        g.raid = !g.raid;
        if (!g.raid && g.size() > server.config.maxPartySize) {
            g.raid = true;
            server.msg(by, L10n.of("Too many members for a party.", "Слишком много участников для группы."), 0xFFFF4040);
        }
        broadcast(g, g.raid ? L10n.of("The group is now a raid.", "Группа преобразована в рейд.") : L10n.of("The raid is now a party.", "Рейд преобразован в группу."),
                0xFFFF8040);
        sendGroupToAll(g);
    }

    public void setRole(PlayerSession s, String role) {
        Group g = groupOf(s.uuid);
        Role r = parseRole(role);
        if (g != null && r != null) {
            g.roles.put(s.uuid, r.name());
            sendGroupToAll(g);
        }
    }

    static Role parseRole(String role) {
        if (role == null) return null;
        return switch (role.toLowerCase()) {
            case "tank", "танк" -> Role.TANK;
            case "healer", "heal", "хил", "лекарь" -> Role.HEALER;
            case "dps", "damage", "дд", "урон" -> Role.MELEE_DPS;
            default -> {
                try {
                    yield Role.valueOf(role.toUpperCase());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }

    public void readyCheck(PlayerSession s) {
        Group g = groupOf(s.uuid);
        if (g == null || !g.leader.equals(s.uuid)) return;
        g.readyCheckUntil = server.engine.now() + 30;
        g.ready.clear();
        g.ready.put(s.uuid, 1);
        for (UUID m : g.members) {
            S2C.Warning w = new S2C.Warning();
            w.text = new S2C.Text(s.name + " initiated a ready check", s.name + " начинает проверку готовности");
            w.color = 0xFFFFD040;
            w.sound = "ready_check";
            server.send(m, w);
        }
        sendGroupToAll(g);
    }

    public void answerReady(PlayerSession s, boolean ready) {
        Group g = groupOf(s.uuid);
        if (g == null || g.readyCheckUntil < server.engine.now()) return;
        g.ready.put(s.uuid, ready ? 1 : 2);
        sendGroupToAll(g);
        if (g.ready.size() >= g.members.size()) finishReadyCheck(g);
    }

    private void finishReadyCheck(Group g) {
        g.readyCheckUntil = -1;
        List<String> notReady = new ArrayList<>();
        for (UUID m : g.members) if (g.ready.getOrDefault(m, 0) != 1) notReady.add(server.worldData().names.getOrDefault(m.toString(), "?"));
        if (notReady.isEmpty()) broadcast(g, L10n.of("Everyone is ready.", "Все готовы."), 0xFF40FF40);
        else broadcast(g, L10n.of("Not ready: " + String.join(", ", notReady), "Не готовы: " + String.join(", ", notReady)), 0xFFFF8040);
        sendGroupToAll(g);
    }

    public void onOffline(UUID uuid) {
        Group g = groupOf(uuid);
        if (g == null) return;
        boolean anyOnline = false;
        for (UUID m : g.members) if (!m.equals(uuid) && server.session(m) != null) anyOnline = true;
        if (!anyOnline) {
            // nobody left online: bots go home
            for (UnitState bot : new ArrayList<>(g.bots)) server.bots.removeBot(g, bot);
        }
        if (g.leader.equals(uuid)) {
            for (UUID m : g.members) {
                if (server.session(m) != null) {
                    g.leader = m;
                    break;
                }
            }
        }
        sendGroupToAll(g);
    }

    public void tick() {
        double now = server.engine.now();
        for (Group g : groups.values()) {
            if (g.readyCheckUntil > 0 && now > g.readyCheckUntil) finishReadyCheck(g);
            boolean combat = false;
            for (UnitState u : unitsOfGroupId(g)) if (u.inCombat()) combat = true;
            g.meter.tick(now, combat);
        }
    }

    List<UnitState> unitsOfGroupId(Group g) {
        List<UnitState> out = new ArrayList<>();
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            if (s != null && s.unit != null) out.add(s.unit);
        }
        out.addAll(g.bots);
        return out;
    }

    // ------------------------------------------------------------------ messages

    void broadcast(Group g, L10n text, int color) {
        for (UUID m : g.members) server.msg(m, text, color);
    }

    public void sendGroupToAll(UUID member) {
        Group g = groupOf(member);
        if (g == null) sendGroup(member);
        else sendGroupToAll(g);
    }

    public void sendGroupToAll(Group g) {
        for (UUID m : g.members) sendGroup(m);
    }

    public void sendGroup(UUID uuid) {
        PlayerSession s = server.session(uuid);
        if (s == null) return;
        server.send(s, groupMessage(groupOf(uuid)));
    }

    S2C.Group groupMessage(Group g) {
        S2C.Group msg = new S2C.Group();
        if (g == null) return msg;
        msg.inGroup = true;
        msg.raid = g.raid;
        double now = server.engine.now();
        msg.readyCheck = g.readyCheckUntil > now;
        msg.readyCheckRemaining = (float) Math.max(0, g.readyCheckUntil - now);
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            S2C.GroupMember gm = new S2C.GroupMember();
            gm.uuid = m.toString();
            gm.name = s != null ? s.name : server.worldData().names.getOrDefault(m.toString(), "?");
            gm.online = s != null;
            gm.leader = g.leader.equals(m);
            gm.ready = g.ready.getOrDefault(m, 0);
            if (s != null) {
                Spec spec = s.profile.spec();
                gm.wowClass = s.profile.wowClass;
                gm.spec = s.profile.spec;
                gm.role = g.roles.containsKey(m) ? g.roles.get(m) : spec != null ? spec.role.name() : null;
                gm.entityId = s.unit != null ? s.unit.id : -1;
                Equipment eq = server.equipment(s);
                gm.itemLevel = eq != null ? Math.round(eq.averageItemLevel()) : 0;
                gm.rating = MythicScore.totalRating(s.profile);
            }
            msg.members.add(gm);
        }
        for (UnitState bot : g.bots) {
            S2C.GroupMember gm = new S2C.GroupMember();
            gm.uuid = "bot:" + bot.id;
            gm.name = bot.name;
            gm.online = true;
            gm.bot = true;
            gm.wowClass = bot.wowClass != null ? bot.wowClass.id() : null;
            gm.spec = bot.spec != null ? bot.spec.id() : null;
            gm.role = bot.role() != null ? bot.role().name() : null;
            gm.entityId = bot.id;
            Object ilvl = bot.tags.get("bot_ilvl");
            gm.itemLevel = ilvl instanceof Number n ? n.doubleValue() : 0;
            msg.members.add(gm);
        }
        return msg;
    }
}
