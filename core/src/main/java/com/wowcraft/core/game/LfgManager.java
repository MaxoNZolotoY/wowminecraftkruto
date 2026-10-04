package com.wowcraft.core.game;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.mythic.Affix;
import com.wowcraft.core.mythic.AffixSchedule;
import com.wowcraft.core.mythic.MythicScore;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Group finder: Dungeon Finder / Raid Finder queues (role matching, follower bots fill empty roles)
 * and the Premade Group Finder for Mythic+ keys (listings, applications).
 */
public final class LfgManager {
    static final double FOLLOWER_WAIT = 20;

    /** A queued player or premade group. */
    static final class Ticket {
        final List<UUID> players = new ArrayList<>();
        final Map<UUID, Role> roles = new LinkedHashMap<>();
        String dungeonId;
        Difficulty difficulty;
        double since;
        boolean followers;
    }

    /** A premade group listing (usually a Mythic+ key). */
    public static final class Listing {
        public final String id;
        public final UUID leader;
        public String title;
        public String dungeonId;
        public int keyLevel;
        public double minRating;
        public final Map<UUID, Role> applicants = new LinkedHashMap<>();
        public double created;

        Listing(String id, UUID leader) {
            this.id = id;
            this.leader = leader;
        }
    }

    private final GameServer server;
    private final List<Ticket> tickets = new ArrayList<>();
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private int nextListing = 1;
    private double nextMatch;

    LfgManager(GameServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ dungeon / raid finder

    public void queue(PlayerSession s, String dungeonId, String difficultyName, String roleName, boolean followers) {
        Spec spec = s.profile.spec();
        if (spec == null) {
            server.msg(s, L10n.of("Choose a class first.", "Сначала выберите класс."), 0xFFFF4040);
            return;
        }
        Difficulty diff = difficultyName == null ? Difficulty.NORMAL : Difficulty.byName(difficultyName);
        if (diff == null || diff == Difficulty.MYTHIC_PLUS) {
            server.msg(s, L10n.of("Mythic+ groups are formed in the Premade Group Finder (/wow lfg list).",
                    "Группы для M+ собираются в заранее собранных группах (/wow lfg list)."), 0xFFFF4040);
            return;
        }
        DungeonDef def = dungeonId == null || dungeonId.equals("random") ? null : Dungeons.get(dungeonId);
        if (dungeonId != null && !dungeonId.equals("random") && def == null) {
            server.msg(s, L10n.of("Unknown dungeon: " + dungeonId, "Неизвестное подземелье: " + dungeonId), 0xFFFF4040);
            return;
        }
        if (def != null && !def.difficulties.contains(diff)) {
            server.msg(s, L10n.of("That difficulty is not available there.", "Эта сложность там недоступна."), 0xFFFF4040);
            return;
        }
        if (s.unit.instanceId != null || server.pvp.matchOfSession(s) != null) {
            server.msg(s, L10n.of("You can't queue from here.", "Отсюда нельзя встать в очередь."), 0xFFFF4040);
            return;
        }
        onLeave(s.uuid);
        Ticket t = new Ticket();
        t.dungeonId = def != null ? def.id : null;
        t.difficulty = diff;
        t.since = server.engine.now();
        t.followers = followers;
        Group g = server.groups.groupOf(s.uuid);
        if (g != null) {
            if (!g.leader.equals(s.uuid)) {
                server.msg(s, L10n.of("Only the leader can queue the group.", "Ставить группу в очередь может только лидер."), 0xFFFF4040);
                return;
            }
            for (UUID m : g.members) {
                PlayerSession ms = server.session(m);
                if (ms == null || ms.profile.spec() == null) continue;
                t.players.add(m);
                Role r = m.equals(s.uuid) && roleName != null ? GroupManager.parseRole(roleName) : null;
                if (r == null && g.roles.containsKey(m)) r = Role.valueOf(g.roles.get(m));
                t.roles.put(m, normalize(r != null ? r : ms.profile.spec().role));
            }
        } else {
            t.players.add(s.uuid);
            Role r = roleName != null ? GroupManager.parseRole(roleName) : null;
            t.roles.put(s.uuid, normalize(r != null ? r : spec.role));
        }
        tickets.add(t);
        for (UUID p : t.players) {
            server.msg(p, L10n.of("You are queued for " + (def != null ? def.name.en() : "a random dungeon") + " (" + diff.name.en() + ")" +
                            (followers ? " with followers." : "."),
                    "Вы в очереди: " + (def != null ? def.name.ru() : "случайное подземелье") + " (" + diff.name.ru() + ")" + (followers ? " с соратниками." : ".")),
                    0xFFFFD040);
            sendLfg(p);
        }
    }

    static Role normalize(Role r) {
        return r == Role.RANGED_DPS ? Role.MELEE_DPS : r;
    }

    void tick() {
        double now = server.engine.now();
        if (now < nextMatch) return;
        nextMatch = now + 1;
        for (Iterator<Listing> it = listings.values().iterator(); it.hasNext(); ) {
            Listing l = it.next();
            if (server.session(l.leader) == null) it.remove();
        }
        // try to build groups
        List<Ticket> pending = new ArrayList<>(tickets);
        pending.sort((a, b) -> Double.compare(a.since, b.since));
        for (Ticket anchor : pending) {
            if (!tickets.contains(anchor)) continue;
            boolean raid = anchor.difficulty.isRaid();
            int size = raid ? 10 : 5;
            int needTanks = raid ? 2 : 1, needHealers = raid ? 2 : 1;
            List<Ticket> chosen = new ArrayList<>();
            chosen.add(anchor);
            int[] have = count(anchor);
            for (Ticket t : pending) {
                if (t == anchor || !tickets.contains(t)) continue;
                if (t.difficulty != anchor.difficulty || !compatible(anchor, t)) continue;
                int[] c = count(t);
                if (have[0] + c[0] > needTanks || have[1] + c[1] > needHealers || have[2] + c[2] > size - needTanks - needHealers) continue;
                chosen.add(t);
                for (int i = 0; i < 3; i++) have[i] += c[i];
                if (have[0] + have[1] + have[2] >= size) break;
            }
            boolean full = have[0] + have[1] + have[2] >= size;
            boolean fill = server.config.allowBots && (anchor.followers || now - anchor.since >= FOLLOWER_WAIT || raid && now - anchor.since >= 5);
            if (full || fill) {
                for (Ticket t : chosen) tickets.remove(t);
                form(chosen, anchor, have, size, needTanks, needHealers);
            }
        }
    }

    private static boolean compatible(Ticket a, Ticket b) {
        return a.dungeonId == null || b.dungeonId == null || a.dungeonId.equals(b.dungeonId);
    }

    private static int[] count(Ticket t) {
        int[] c = new int[3];
        for (Role r : t.roles.values()) {
            if (r == Role.TANK) c[0]++;
            else if (r == Role.HEALER) c[1]++;
            else c[2]++;
        }
        return c;
    }

    private void form(List<Ticket> chosen, Ticket anchor, int[] have, int size, int needTanks, int needHealers) {
        DungeonDef def = null;
        for (Ticket t : chosen) if (t.dungeonId != null) def = Dungeons.get(t.dungeonId);
        if (def == null) {
            List<DungeonDef> options = new ArrayList<>();
            for (DungeonDef d : Dungeons.ofType(anchor.difficulty.isRaid() ? DungeonDef.Type.RAID : DungeonDef.Type.DUNGEON)) {
                if (d.difficulties.contains(anchor.difficulty)) options.add(d);
            }
            if (options.isEmpty()) return;
            def = options.get(server.rng.nextInt(options.size()));
        }
        // the anchor's leader leads the new group
        UUID leaderId = anchor.players.get(0);
        Group g = server.groups.groupOf(leaderId);
        if (g == null) g = server.groups.ensureGroup(leaderId);
        if (anchor.difficulty.isRaid()) g.raid = true;
        for (Ticket t : chosen) {
            for (UUID p : t.players) {
                if (!g.members.contains(p)) server.groups.addMember(g, p);
                g.roles.put(p, t.roles.get(p).name());
            }
        }
        // followers for missing roles
        int ilvl = anchor.difficulty == Difficulty.HEROIC ? 120 : anchor.difficulty == Difficulty.MYTHIC ? 130 : anchor.difficulty.isRaid() ? 125 : 112;
        for (int i = have[0]; i < needTanks; i++) server.bots.addToGroup(g, Role.TANK, null, ilvl);
        for (int i = have[1]; i < needHealers; i++) server.bots.addToGroup(g, Role.HEALER, null, ilvl);
        for (int i = have[2]; i < size - needTanks - needHealers; i++) server.bots.addToGroup(g, server.rng.chance(0.5) ? Role.MELEE_DPS : Role.RANGED_DPS, null, ilvl);
        server.groups.broadcast(g, L10n.of("Group formed for " + def.name.en() + "!", "Группа собрана: " + def.name.ru() + "!"), 0xFF40FF40);
        PlayerSession leader = server.session(g.leader);
        if (leader != null) server.instances.requestEnter(leader, def, anchor.difficulty);
        for (UUID p : g.members) sendLfg(p);
    }

    public void onLeave(UUID uuid) {
        for (Iterator<Ticket> it = tickets.iterator(); it.hasNext(); ) {
            Ticket t = it.next();
            if (t.players.contains(uuid)) {
                it.remove();
                for (UUID p : t.players) if (!p.equals(uuid)) server.msg(p, L10n.of("The queue was cancelled.", "Очередь отменена."), 0xFFAAAAAA);
            }
        }
        for (Listing l : listings.values()) l.applicants.remove(uuid);
        listings.values().removeIf(l -> l.leader.equals(uuid));
    }

    public void leaveQueue(PlayerSession s) {
        onLeave(s.uuid);
        server.pvp.leaveQueue(s.uuid, true);
        server.msg(s, L10n.of("You left all queues.", "Вы покинули все очереди."), 0xFFAAAAAA);
        sendLfg(s.uuid);
    }

    void onGroupChanged(Group g) {
        tickets.removeIf(t -> {
            for (UUID p : t.players) if (!g.members.contains(p) && t.players.size() > 1) return true;
            return false;
        });
    }

    // ------------------------------------------------------------------ premade group finder

    public void listKey(PlayerSession s, String title, double minRating) {
        PlayerProfile.Keystone key = s.profile.keystone;
        if (key == null) {
            server.msg(s, L10n.of("You have no keystone.", "У вас нет ключа."), 0xFFFF4040);
            return;
        }
        Group g = server.groups.ensureGroup(s.uuid);
        if (!g.leader.equals(s.uuid)) {
            server.msg(s, L10n.of("Only the group leader can list the group.", "Выставить группу может только лидер."), 0xFFFF4040);
            return;
        }
        listings.values().removeIf(l -> l.leader.equals(s.uuid));
        Listing l = new Listing("l" + (nextListing++), s.uuid);
        DungeonDef d = Dungeons.get(key.dungeonId);
        l.dungeonId = key.dungeonId;
        l.keyLevel = key.level;
        l.title = title != null && !title.isBlank() ? title : (d != null ? d.shortName : key.dungeonId) + " +" + key.level;
        l.minRating = minRating;
        l.created = server.engine.now();
        listings.put(l.id, l);
        server.msg(s, L10n.of("Your group is listed: " + l.title, "Ваша группа выставлена: " + l.title), 0xFF40FF40);
        for (PlayerSession o : server.sessions()) sendLfg(o.uuid);
    }

    public void delist(PlayerSession s) {
        listings.values().removeIf(l -> l.leader.equals(s.uuid));
        sendLfg(s.uuid);
    }

    public void apply(PlayerSession s, String listingId, String roleName) {
        Listing l = listings.get(listingId);
        if (l == null) return;
        Spec spec = s.profile.spec();
        if (spec == null) return;
        double rating = MythicScore.totalRating(s.profile);
        if (rating < l.minRating) {
            server.msg(s, L10n.of("Your rating is too low for this group.", "Ваш рейтинг слишком низок для этой группы."), 0xFFFF4040);
            return;
        }
        Role r = roleName != null ? GroupManager.parseRole(roleName) : null;
        l.applicants.put(s.uuid, normalize(r != null ? r : spec.role));
        server.msg(s, L10n.of("You applied to " + l.title + ".", "Вы подали заявку в группу «" + l.title + "»."), 0xFFFFD040);
        server.msg(l.leader, L10n.of(s.name + " (" + spec.name.en() + " " + spec.wowClass.name.en() + ", " + rating + ") applied to your group.",
                s.name + " (" + spec.wowClass.name.ru() + ", " + spec.name.ru() + ", " + rating + ") хочет вступить в вашу группу."), 0xFFFFD040);
        sendLfg(l.leader);
        sendLfg(s.uuid);
    }

    public void acceptApplicant(PlayerSession leader, String applicant, boolean accept) {
        Listing l = null;
        for (Listing x : listings.values()) if (x.leader.equals(leader.uuid)) l = x;
        if (l == null) return;
        UUID a;
        try {
            a = UUID.fromString(applicant);
        } catch (IllegalArgumentException e) {
            PlayerSession byName = server.sessionByName(applicant);
            if (byName == null) return;
            a = byName.uuid;
        }
        Role role = l.applicants.remove(a);
        if (role == null) return;
        if (!accept) {
            server.msg(a, L10n.of("Your application to " + l.title + " was declined.", "Ваша заявка в «" + l.title + "» отклонена."), 0xFFFF8040);
            sendLfg(a);
            sendLfg(leader.uuid);
            return;
        }
        Group g = server.groups.ensureGroup(leader.uuid);
        if (server.groups.addMember(g, a)) {
            g.roles.put(a, role.name());
            server.msg(a, L10n.of("You joined " + l.title + ".", "Вы вступили в группу «" + l.title + "»."), 0xFF40FF40);
            if (g.size() >= 5) listings.remove(l.id);
        }
        sendLfg(a);
        sendLfg(leader.uuid);
    }

    /** Fill the leader's group with follower bots (e.g. to run a key with friends but no tank). */
    public void fillWithBots(PlayerSession s) {
        Group g = server.groups.ensureGroup(s.uuid);
        if (!g.leader.equals(s.uuid)) return;
        if (!server.config.allowBots) {
            server.msg(s, L10n.of("Bots are disabled on this server.", "Боты отключены на этом сервере."), 0xFFFF4040);
            return;
        }
        int tanks = 0, healers = 0;
        for (UnitState u : server.groups.unitsOfGroupId(g)) {
            Role r = u.role();
            if (u.isPlayer() && g.roles.containsKey(u.uuid)) r = Role.valueOf(g.roles.get(u.uuid));
            if (r == Role.TANK) tanks++;
            else if (r == Role.HEALER) healers++;
        }
        int max = g.raid ? Math.min(server.config.maxRaidSize, 10) : 5;
        int ilvl = averageItemLevel(g);
        int wantTanks = g.raid ? 2 : 1, wantHealers = g.raid ? 2 : 1;
        while (g.size() < max) {
            Role r = tanks < wantTanks ? Role.TANK : healers < wantHealers ? Role.HEALER : server.rng.chance(0.5) ? Role.MELEE_DPS : Role.RANGED_DPS;
            if (r == Role.TANK) tanks++;
            if (r == Role.HEALER) healers++;
            if (server.bots.addToGroup(g, r, null, ilvl) == null) break;
        }
    }

    public void addBot(PlayerSession s, String what) {
        Group g = server.groups.ensureGroup(s.uuid);
        if (!g.leader.equals(s.uuid)) return;
        if (!server.config.allowBots) {
            server.msg(s, L10n.of("Bots are disabled on this server.", "Боты отключены на этом сервере."), 0xFFFF4040);
            return;
        }
        if (g.size() >= server.groups.maxSize(g)) {
            server.msg(s, L10n.of("Your group is full.", "Ваша группа заполнена."), 0xFFFF4040);
            return;
        }
        Spec spec = what != null ? Spec.byId(what) : null;
        Role role = spec == null ? GroupManager.parseRole(what) : null;
        UnitState bot = server.bots.addToGroup(g, role, spec, averageItemLevel(g));
        if (bot != null) server.groups.broadcast(g, server.bots.describe(bot), 0xFF80C0FF);
    }

    int averageItemLevel(Group g) {
        double sum = 0;
        int n = 0;
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            if (s == null) continue;
            Equipment eq = server.equipment(s);
            if (eq != null) {
                sum += eq.averageItemLevel();
                n++;
            }
        }
        return n == 0 ? 120 : (int) Math.max(100, Math.round(sum / n));
    }

    // ------------------------------------------------------------------ messages

    public void sendLfg(UUID uuid) {
        PlayerSession s = server.session(uuid);
        if (s == null) return;
        S2C.Lfg msg = new S2C.Lfg();
        for (Ticket t : tickets) {
            if (t.players.contains(uuid)) {
                msg.queue = (t.dungeonId != null ? t.dungeonId : "random") + ":" + t.difficulty.name();
                msg.queueTime = (float) (server.engine.now() - t.since);
            }
        }
        String pvpQueue = server.pvp.queueOf(uuid);
        if (pvpQueue != null) {
            msg.queue = "pvp:" + pvpQueue;
            msg.queueTime = (float) server.pvp.queueTime(uuid);
        }
        for (Listing l : listings.values()) {
            S2C.Listing li = new S2C.Listing();
            li.id = l.id;
            li.leader = server.worldData().names.getOrDefault(l.leader.toString(), "?");
            li.title = l.title;
            li.dungeon = l.dungeonId;
            li.keyLevel = l.keyLevel;
            Group g = server.groups.groupOf(l.leader);
            li.members = g != null ? g.size() : 1;
            li.minRating = l.minRating;
            if (g != null) {
                for (UnitState u : server.groups.unitsOfGroupId(g)) {
                    String role = u.role() != null ? u.role().name() : "MELEE_DPS";
                    if (u.isPlayer() && g.roles.containsKey(u.uuid)) role = g.roles.get(u.uuid);
                    li.roles.add(role);
                }
            }
            li.applied = l.applicants.containsKey(uuid);
            msg.listings.add(li);
            if (l.leader.equals(uuid)) {
                for (var en : l.applicants.entrySet()) {
                    PlayerSession a = server.session(en.getKey());
                    if (a == null) continue;
                    S2C.Applicant ap = new S2C.Applicant();
                    ap.uuid = en.getKey().toString();
                    ap.name = a.name;
                    ap.wowClass = a.profile.wowClass;
                    ap.spec = a.profile.spec;
                    ap.role = en.getValue().name();
                    ap.rating = MythicScore.totalRating(a.profile);
                    Equipment eq = server.equipment(a);
                    ap.itemLevel = eq != null ? Math.round(eq.averageItemLevel()) : 0;
                    msg.applicants.add(ap);
                }
            }
        }
        for (Affix a : AffixSchedule.weekly(server.week)) msg.weeklyAffixes.add(a.name());
        msg.mythicPool.addAll(Dungeons.mythicPool());
        server.send(s, msg);
    }

    public Set<String> listingIds() {
        return new LinkedHashSet<>(listings.keySet());
    }
}
