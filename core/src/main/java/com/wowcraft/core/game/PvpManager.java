package com.wowcraft.core.game;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.bot.BotBrain;
import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.Currency;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Mth;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arenas (2v2, 3v3, 5v5 skirmish / rated), Solo Shuffle, battlegrounds (capture the flag, domination),
 * queues, bots filling empty spots, ratings (Elo-style MMR), honor and conquest.
 */
public final class PvpManager {
    public enum Mode {ARENA, SHUFFLE, CTF, DOMINATION}

    public enum State {WAITING_MAP, PREP, ACTIVE, ROUND_END, ENDED}

    /** A queue type. */
    public record Bracket(String id, Mode mode, int teamSize, L10n name) {
    }

    public static final Map<String, Bracket> BRACKETS = new LinkedHashMap<>();

    static {
        BRACKETS.put("2v2", new Bracket("2v2", Mode.ARENA, 2, L10n.of("Arena 2v2", "Арена 2х2")));
        BRACKETS.put("3v3", new Bracket("3v3", Mode.ARENA, 3, L10n.of("Arena 3v3", "Арена 3х3")));
        BRACKETS.put("5v5", new Bracket("5v5", Mode.ARENA, 5, L10n.of("Arena 5v5", "Арена 5х5")));
        BRACKETS.put("shuffle", new Bracket("shuffle", Mode.SHUFFLE, 3, L10n.of("Solo Shuffle", "Одиночная потасовка")));
        BRACKETS.put("ctf", new Bracket("ctf", Mode.CTF, 0, L10n.of("Battleground: Capture the Flag", "Поле боя: захват флага")));
        BRACKETS.put("domination", new Bracket("domination", Mode.DOMINATION, 0, L10n.of("Battleground: Domination", "Поле боя: господство")));
        BRACKETS.put("bg", new Bracket("bg", Mode.CTF, 0, L10n.of("Random Battleground", "Случайное поле боя")));
    }

    static final double PREP_ARENA = 20, PREP_BG = 25, ARENA_TIME_LIMIT = 15 * 60, BG_TIME_LIMIT = 15 * 60, END_DELAY = 8,
            BOT_FILL_DELAY = 10, BG_RESPAWN_WAVE = 15, CAPTURE_TIME = 7, DOMINATION_TARGET = 600;
    static final int CTF_CAPS = 3;

    private static final class QueueEntry {
        final List<UUID> players = new ArrayList<>();
        Bracket bracket;
        boolean rated;
        double since;
        double mmr;
    }

    public static final class Team {
        public final int index;
        public final String key;
        public final List<UUID> players = new ArrayList<>();
        public final List<UnitState> bots = new ArrayList<>();
        public int score;
        public double mmr;
        public final int color;

        Team(int index, String key) {
            this.index = index;
            this.key = key;
            this.color = index == 0 ? 0xFF3FA9F5 : 0xFFFF5040;
        }
    }

    /** Capture-the-flag flag. */
    static final class Flag {
        Vec3 base;
        Vec3 dropped;
        UnitState carrier;
        double droppedAt;
    }

    /** Domination capture node. */
    static final class Node {
        Vec3 pos;
        int owner = -1;
        int capturing = -1;
        double progress;
        final String name;

        Node(String name) {
            this.name = name;
        }
    }

    public static final class Match {
        public final String id;
        public final Bracket bracket;
        public final boolean rated;
        public final DungeonDef map;
        public InstanceRun run;
        public final Team[] teams = new Team[2];
        public State state = State.WAITING_MAP;
        double prepEnd, startTime, endAt, lastDampening, lastScoreTick, nextWave;
        int winner = -2;
        int dampening;
        // solo shuffle
        int round;
        final List<Object> shuffleRoster = new ArrayList<>();
        final Map<Object, Integer> roundWins = new HashMap<>();
        double nextRoundAt;
        // battlegrounds
        Flag[] flags;
        Node[] nodes;
        final Map<String, Integer> ratingChanges = new HashMap<>();
        boolean pendingReady;

        Match(String id, Bracket bracket, boolean rated, DungeonDef map) {
            this.id = id;
            this.bracket = bracket;
            this.rated = rated;
            this.map = map;
            teams[0] = new Team(0, "pvp_" + id + "_0");
            teams[1] = new Team(1, "pvp_" + id + "_1");
        }

        public boolean isArena() {
            return bracket.mode == Mode.ARENA || bracket.mode == Mode.SHUFFLE;
        }

        int teamOfKey(String key) {
            if (teams[0].key.equals(key)) return 0;
            if (teams[1].key.equals(key)) return 1;
            return -1;
        }
    }

    private final GameServer server;
    private final List<QueueEntry> queue = new ArrayList<>();
    private final Map<String, Match> matches = new LinkedHashMap<>();
    private final Map<UUID, Match> playerMatch = new HashMap<>();
    private final Map<Integer, Match> botMatch = new HashMap<>();
    private int nextMatchId = 1;
    private double nextQueueCheck;

    PvpManager(GameServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ lookup

    public Match matchOf(UnitState u) {
        if (u == null) return null;
        UnitState m = u.master();
        if (m.isPlayer()) return playerMatch.get(m.uuid);
        return botMatch.get(m.id);
    }

    public Match matchOfSession(PlayerSession s) {
        return playerMatch.get(s.uuid);
    }

    int teamIndex(Match m, UnitState u) {
        return m.teamOfKey(u.master().team);
    }

    List<UnitState> teamUnits(Match m, UnitState u) {
        int t = teamIndex(m, u);
        return t < 0 ? List.of() : unitsOf(m, m.teams[t]);
    }

    List<UnitState> enemyUnits(Match m, UnitState u) {
        int t = teamIndex(m, u);
        if (t < 0) return List.of();
        List<UnitState> out = new ArrayList<>();
        for (UnitState e : unitsOf(m, m.teams[1 - t])) {
            if (!e.isAlive()) continue;
            out.add(e);
            out.addAll(e.livingPets());
        }
        return out;
    }

    List<UnitState> unitsOf(Match m, Team t) {
        List<UnitState> out = new ArrayList<>();
        for (UUID p : t.players) {
            PlayerSession s = server.session(p);
            if (s != null && s.unit != null && playerMatch.get(p) == m) out.add(s.unit);
        }
        out.addAll(t.bots);
        return out;
    }

    /** Enemy frames shown in the arena UI. */
    List<UnitState> enemyFrames(UnitState me) {
        Match m = matchOf(me);
        if (m == null || !m.isArena()) return List.of();
        int t = teamIndex(m, me);
        if (t < 0) return List.of();
        return unitsOf(m, m.teams[1 - t]);
    }

    // ------------------------------------------------------------------ queue

    public void queue(PlayerSession s, String bracketId, boolean rated) {
        Bracket b = BRACKETS.get(bracketId == null ? "" : bracketId.toLowerCase());
        if (b == null) {
            server.msg(s, L10n.of("Unknown PvP bracket. Use 2v2, 3v3, 5v5, shuffle, ctf, domination.",
                    "Неизвестный режим. Доступно: 2v2, 3v3, 5v5, shuffle, ctf, domination."), 0xFFFF4040);
            return;
        }
        if (s.profile.spec() == null) {
            server.msg(s, L10n.of("Choose a class first.", "Сначала выберите класс."), 0xFFFF4040);
            return;
        }
        if (playerMatch.containsKey(s.uuid) || s.unit.instanceId != null) {
            server.msg(s, L10n.of("You can't queue right now.", "Сейчас нельзя встать в очередь."), 0xFFFF4040);
            return;
        }
        if (s.unit.auras().has("deserter") && b.mode != Mode.ARENA && b.mode != Mode.SHUFFLE) {
            server.msg(s, L10n.of("You are a deserter.", "Вы дезертир."), 0xFFFF4040);
            return;
        }
        leaveQueue(s.uuid, false);
        QueueEntry e = new QueueEntry();
        e.bracket = b;
        e.rated = rated && b.mode != Mode.CTF && b.mode != Mode.DOMINATION && !b.id.equals("bg");
        e.since = server.engine.now();
        Group g = server.groups.groupOf(s.uuid);
        if (g != null && b.mode != Mode.SHUFFLE) {
            if (!g.leader.equals(s.uuid)) {
                server.msg(s, L10n.of("Only the leader can queue the group.", "Ставить группу в очередь может только лидер."), 0xFFFF4040);
                return;
            }
            int cap = b.mode == Mode.ARENA ? b.teamSize : server.config.battlegroundTeamSize;
            if (g.members.size() > cap) {
                server.msg(s, L10n.of("Your group is too big for " + b.name.en() + ".", "Ваша группа слишком большая для режима «" + b.name.ru() + "»."), 0xFFFF4040);
                return;
            }
            for (UUID m : g.members) {
                PlayerSession ms = server.session(m);
                if (ms == null || ms.profile.spec() == null || playerMatch.containsKey(m)) continue;
                leaveQueue(m, false);
                e.players.add(m);
            }
        } else {
            e.players.add(s.uuid);
        }
        double sum = 0;
        for (UUID p : e.players) sum += mmrOf(server.session(p), b);
        e.mmr = sum / Math.max(1, e.players.size());
        queue.add(e);
        for (UUID p : e.players) {
            server.msg(p, L10n.of("Queued for " + b.name.en() + (e.rated ? " (rated)" : "") + ".",
                    "Вы в очереди: " + b.name.ru() + (e.rated ? " (рейтинговая)" : "") + "."), 0xFFFFD040);
            sendQueueStatus(p);
        }
    }

    public void leaveQueue(UUID uuid, boolean announce) {
        for (Iterator<QueueEntry> it = queue.iterator(); it.hasNext(); ) {
            QueueEntry e = it.next();
            if (e.players.contains(uuid)) {
                it.remove();
                for (UUID p : e.players) {
                    if (announce) server.msg(p, L10n.of("Left the PvP queue.", "Вы покинули очередь PvP."), 0xFFAAAAAA);
                    sendQueueStatus(p);
                }
            }
        }
    }

    String queueOf(UUID uuid) {
        for (QueueEntry e : queue) if (e.players.contains(uuid)) return e.bracket.id;
        return null;
    }

    double queueTime(UUID uuid) {
        for (QueueEntry e : queue) if (e.players.contains(uuid)) return server.engine.now() - e.since;
        return 0;
    }

    void sendQueueStatus(UUID uuid) {
        server.lfg.sendLfg(uuid);
    }

    static double mmrOf(PlayerSession s, Bracket b) {
        if (s == null) return 1500;
        return s.profile.pvp(b.id).mmr;
    }

    private void processQueue() {
        double now = server.engine.now();
        for (Bracket b : BRACKETS.values()) {
            List<QueueEntry> entries = new ArrayList<>();
            for (QueueEntry e : queue) if (e.bracket == b) entries.add(e);
            if (entries.isEmpty()) continue;
            int teamSize = b.mode == Mode.ARENA ? b.teamSize : b.mode == Mode.SHUFFLE ? 3 : server.config.battlegroundTeamSize;
            int total = b.mode == Mode.SHUFFLE ? 6 : teamSize * 2;
            int queuedPlayers = 0;
            for (QueueEntry e : entries) queuedPlayers += e.players.size();
            boolean waitedLong = now - entries.get(0).since >= BOT_FILL_DELAY;
            if (queuedPlayers >= total || (waitedLong && server.config.allowBots)) {
                // fill both teams, keeping premade groups together
                List<QueueEntry> a = new ArrayList<>(), c = new ArrayList<>();
                int na = 0, nc = 0;
                entries.sort((x, y) -> Double.compare(x.since, y.since));
                for (QueueEntry e : entries) {
                    if (b.mode == Mode.SHUFFLE) {
                        if (na + nc + e.players.size() > 6) continue;
                        a.add(e);
                        na += e.players.size();
                        continue;
                    }
                    if (na + e.players.size() <= teamSize && (na <= nc || nc + e.players.size() > teamSize)) {
                        a.add(e);
                        na += e.players.size();
                    } else if (nc + e.players.size() <= teamSize) {
                        c.add(e);
                        nc += e.players.size();
                    }
                }
                if (na + nc == 0) continue;
                if (!server.config.allowBots && na + nc < total) continue;
                for (QueueEntry e : a) queue.remove(e);
                for (QueueEntry e : c) queue.remove(e);
                createMatch(b, a, c, teamSize);
            }
        }
    }

    private DungeonDef pickMap(Bracket b) {
        DungeonDef.Type type = b.mode == Mode.ARENA || b.mode == Mode.SHUFFLE ? DungeonDef.Type.ARENA : DungeonDef.Type.BATTLEGROUND;
        List<DungeonDef> maps = new ArrayList<>();
        for (DungeonDef d : Dungeons.ofType(type)) {
            if (type == DungeonDef.Type.BATTLEGROUND) {
                String f = d.rooms.get(0).feature();
                if (b.mode == Mode.CTF && !b.id.equals("bg") && !f.startsWith("ctf")) continue;
                if (b.mode == Mode.DOMINATION && !f.startsWith("domination")) continue;
            }
            maps.add(d);
        }
        return maps.isEmpty() ? null : maps.get(server.rng.nextInt(maps.size()));
    }

    private void createMatch(Bracket b, List<QueueEntry> a, List<QueueEntry> c, int teamSize) {
        DungeonDef map = pickMap(b);
        if (map == null) {
            server.platform.log("No PvP map for " + b.id);
            return;
        }
        Bracket actual = b;
        if (b.id.equals("bg")) {
            String f = map.rooms.get(0).feature();
            actual = BRACKETS.get(f.startsWith("domination") ? "domination" : "ctf");
        }
        boolean rated = !a.isEmpty() && a.get(0).rated;
        Match m = new Match("m" + (nextMatchId++), actual, rated, map);
        for (QueueEntry e : a) m.teams[0].players.addAll(e.players);
        for (QueueEntry e : c) m.teams[1].players.addAll(e.players);
        if (b.mode == Mode.SHUFFLE) {
            // shuffle: everybody is placed by rounds later; start with a 3/3 split
            List<UUID> all = new ArrayList<>(m.teams[0].players);
            m.teams[0].players.clear();
            for (int i = 0; i < all.size(); i++) (i % 2 == 0 ? m.teams[0] : m.teams[1]).players.add(all.get(i));
        }
        for (Team t : m.teams) {
            double sum = 0;
            for (UUID p : t.players) sum += mmrOf(server.session(p), actual);
            t.mmr = t.players.isEmpty() ? 1500 : sum / t.players.size();
        }
        if (m.teams[1].players.isEmpty()) m.teams[1].mmr = m.teams[0].mmr;
        if (m.teams[0].players.isEmpty()) m.teams[0].mmr = m.teams[1].mmr;
        matches.put(m.id, m);
        m.run = server.instances.createPvpMap(map, m.id);
        if (m.run == null) {
            matches.remove(m.id);
            for (Team t : m.teams) for (UUID p : t.players) server.msg(p, L10n.of("No free PvP map, try again.", "Нет свободной карты PvP, попробуйте снова."), 0xFFFF4040);
            return;
        }
        for (Team t : m.teams) {
            for (UUID p : t.players) {
                playerMatch.put(p, m);
                server.msg(p, L10n.of(actual.name.en() + ": match found! (" + map.name.en() + ")", actual.name.ru() + ": матч найден! (" + map.name.ru() + ")"),
                        0xFFFFD040);
            }
        }
        m.shuffleRoster.clear();
        int size = actual.mode == Mode.ARENA || actual.mode == Mode.SHUFFLE ? teamSize : server.config.battlegroundTeamSize;
        m.flags = null;
        m.nodes = null;
        m.teams[0].score = 0;
        m.teams[1].score = 0;
        m.prepEnd = -1;
        m.state = State.WAITING_MAP;
        m.round = 0;
        m.dampening = 0;
        m.endAt = -1;
        m.winner = -2;
        m.ratingChanges.clear();
        // remember the intended size for bot fill
        m.run.groupSize = size * 2;
        if (m.pendingReady) {
            m.pendingReady = false;
            onMapReady(m.run);
        }
    }

    /** Map built: spawn bots, move players in. */
    void onMapReady(InstanceRun run) {
        Match m = matches.get(run.pvpMatchId);
        if (m == null) return;
        if (m.run == null) {
            // map built synchronously: finish setting up the match first
            m.run = run;
            m.pendingReady = true;
            return;
        }
        int size = run.groupSize / 2;
        Vec3[] starts = {run.abs(run.layout.markers.get(0)), run.abs(run.layout.markers.get(1))};
        for (Team t : m.teams) {
            for (UUID p : new ArrayList<>(t.players)) {
                PlayerSession s = server.session(p);
                if (s == null || s.unit == null || s.unit.inCombat() && s.unit.instanceId != null) {
                    t.players.remove(p);
                    playerMatch.remove(p);
                    continue;
                }
                enterPlayer(m, t, s, starts[t.index]);
            }
            // bots fill the rest
            while (t.players.size() + t.bots.size() < size && server.config.allowBots) {
                Role role = pickRole(m, t);
                Spec spec = server.bots.pickSpec(role, null);
                int ilvl = 130 + (int) Math.round((t.mmr - 1500) / 50.0);
                UnitState bot = server.bots.spawn(spec, Math.max(110, Math.min(160, ilvl)), run.worldKey,
                        starts[t.index].add(server.rng.range(-1.5, 1.5), 0, server.rng.range(-1.5, 1.5)), 0, t.key, m.id, run.id);
                if (bot == null) break;
                BotBrain brain = server.bots.brain(bot);
                if (brain != null) brain.reaction = Math.max(0.05, 0.35 - (t.mmr - 1500) / 4000.0);
                t.bots.add(bot);
                botMatch.put(bot.id, m);
                for (UnitState pet : bot.pets()) pet.team = t.key;
                run.bots.add(bot);
            }
        }
        if (m.bracket.mode == Mode.SHUFFLE) {
            for (Team t : m.teams) {
                m.shuffleRoster.addAll(t.players);
                m.shuffleRoster.addAll(t.bots);
            }
        }
        if (m.bracket.mode == Mode.CTF) {
            m.flags = new Flag[2];
            for (int i = 0; i < 2; i++) {
                m.flags[i] = new Flag();
                m.flags[i].base = run.abs(run.layout.markers.get(2 + i));
                server.platform.marker(run.worldKey, m.flags[i].base, i == 0 ? "flag_blue" : "flag_red", true);
            }
        } else if (m.bracket.mode == Mode.DOMINATION) {
            String[] names = {"Mill", "Stables", "Farm", "Mine", "Blacksmith"};
            int n = run.layout.markers.size() - 2;
            m.nodes = new Node[n];
            for (int i = 0; i < n; i++) {
                m.nodes[i] = new Node(names[i % names.length]);
                m.nodes[i].pos = run.abs(run.layout.markers.get(2 + i));
                server.platform.marker(run.worldKey, m.nodes[i].pos, "node_neutral", true);
            }
        }
        m.state = State.PREP;
        m.prepEnd = server.engine.now() + (m.isArena() ? PREP_ARENA : PREP_BG);
        broadcast(m, L10n.of("The match begins in " + (int) (m.prepEnd - server.engine.now()) + " seconds.",
                "Матч начнется через " + (int) (m.prepEnd - server.engine.now()) + " с."), 0xFFFFD040);
    }

    private Role pickRole(Match m, Team t) {
        int healers = 0, total = t.players.size() + t.bots.size();
        for (UnitState u : unitsOf(m, t)) if (u.role() == Role.HEALER) healers++;
        int size = m.run.groupSize / 2;
        if (m.bracket.mode == Mode.SHUFFLE) return healers == 0 && total == size - 1 ? Role.HEALER : Role.MELEE_DPS;
        int wantHealers = size >= 5 ? 2 : size >= 2 ? 1 : 0;
        if (healers < wantHealers && total >= size - wantHealers) return Role.HEALER;
        return server.rng.chance(0.5) ? Role.MELEE_DPS : Role.RANGED_DPS;
    }

    private void enterPlayer(Match m, Team t, PlayerSession s, Vec3 start) {
        PlayerProfile p = s.profile;
        if (s.unit.instanceId == null) {
            p.returnWorld = s.unit.worldKey();
            Vec3 pos = s.unit.position();
            p.returnX = pos.x();
            p.returnY = pos.y();
            p.returnZ = pos.z();
        }
        server.lfg.onLeave(s.uuid);
        s.unit.team = t.key;
        s.unit.instanceId = m.run.id;
        s.instanceId = m.run.id;
        s.pvpMatchId = m.id;
        for (UnitState pet : s.unit.pets()) {
            pet.team = t.key;
            pet.instanceId = m.run.id;
        }
        m.run.members.add(s.uuid);
        server.platform.teleport(s.uuid, m.run.worldKey, start, 0);
        for (UnitState pet : s.unit.livingPets()) server.platform.moveBody(pet, m.run.worldKey, start);
        restore(s.unit);
    }

    /** Full health, resources and cooldowns (match start / shuffle rounds). */
    private void restore(UnitState u) {
        if (u.isPlayer()) {
            PlayerSession s = server.session(u.uuid);
            if (s != null && s.ghost) {
                s.ghost = false;
                server.platform.setGhost(s.uuid, false);
            }
        }
        server.engine.resetUnit(u, true);
        u.cooldowns().resetAll();
        u.dr().reset();
        for (UnitState pet : u.livingPets()) server.engine.resetUnit(pet, true);
        if (u.isPlayer()) {
            PlayerSession s = server.session(u.uuid);
            if (s != null) server.applyCharacter(s);
        } else {
            server.bots.ensurePet(u);
        }
    }

    // ------------------------------------------------------------------ tick

    void tick() {
        double now = server.engine.now();
        if (now >= nextQueueCheck) {
            nextQueueCheck = now + 1;
            processQueue();
            for (QueueEntry e : queue) for (UUID p : e.players) if (((long) (now - e.since)) % 5 == 0) sendQueueStatus(p);
        }
        for (Match m : new ArrayList<>(matches.values())) {
            try {
                tickMatch(m, now);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void tickMatch(Match m, double now) {
        switch (m.state) {
            case WAITING_MAP -> {
            }
            case PREP -> {
                holdAtStart(m);
                if (now >= m.prepEnd) {
                    m.state = State.ACTIVE;
                    m.startTime = now;
                    m.lastDampening = now;
                    m.lastScoreTick = now;
                    m.nextWave = now + BG_RESPAWN_WAVE;
                    if (m.bracket.mode == Mode.SHUFFLE) m.round = 1;
                    if (m.isArena()) {
                        for (Team t : m.teams) for (UnitState u : unitsOf(m, t)) applyDampening(u, 10);
                        m.dampening = 10;
                    }
                    broadcast(m, L10n.of("The battle has begun!", "Битва началась!"), 0xFFFF4040);
                    server.platform.sound(m.run.worldKey, m.run.abs(m.run.layout.font), "pvp_start");
                }
            }
            case ACTIVE -> tickActive(m, now);
            case ROUND_END -> {
                if (now >= m.nextRoundAt) startShuffleRound(m);
            }
            case ENDED -> {
                if (now >= m.endAt) closeMatch(m);
            }
        }
        if (now - m.run.lastStatus >= 1.0) {
            m.run.lastStatus = now;
            sendStatus(m);
        }
    }

    private void holdAtStart(Match m) {
        for (Team t : m.teams) {
            Vec3 start = m.run.abs(m.run.layout.markers.get(t.index));
            for (UnitState u : unitsOf(m, t)) {
                if (u.position().distance(start) > (m.isArena() ? 4.5 : 9)) {
                    if (u.isPlayer()) server.platform.teleport(u.uuid, m.run.worldKey, start, 0);
                    else if (u.body != null) u.body.teleport(start);
                }
            }
        }
    }

    private void applyDampening(UnitState u, int stacks) {
        u.auras().all().stream().filter(a -> a.def.id.equals("dampening")).findFirst().ifPresentOrElse(a -> {
            a.stacks = Math.min(100, stacks);
            u.invalidateMods();
            u.auras().markDirty();
        }, () -> server.engine.applyAura(new EffectContext(server.engine, u, u, null, null, null), u, "dampening", Math.min(100, stacks), -1));
    }

    private void tickActive(Match m, double now) {
        double elapsed = now - m.startTime;
        switch (m.bracket.mode) {
            case ARENA, SHUFFLE -> {
                if (now - m.lastDampening >= 10) {
                    m.lastDampening = now;
                    m.dampening = Math.min(100, m.dampening + 1);
                    for (Team t : m.teams) for (UnitState u : unitsOf(m, t)) if (u.isAlive()) applyDampening(u, m.dampening);
                }
                boolean dead0 = teamDead(m, m.teams[0]), dead1 = teamDead(m, m.teams[1]);
                if (dead0 || dead1) {
                    int winner = dead0 && dead1 ? -1 : dead0 ? 1 : 0;
                    if (m.bracket.mode == Mode.SHUFFLE) endShuffleRound(m, winner);
                    else endMatch(m, winner);
                } else if (elapsed >= ARENA_TIME_LIMIT) {
                    if (m.bracket.mode == Mode.SHUFFLE) endShuffleRound(m, -1);
                    else endMatch(m, -1);
                }
            }
            case CTF -> {
                tickFlags(m, now);
                respawnWave(m, now);
                if (m.teams[0].score >= CTF_CAPS) endMatch(m, 0);
                else if (m.teams[1].score >= CTF_CAPS) endMatch(m, 1);
                else if (elapsed >= BG_TIME_LIMIT) endMatch(m, m.teams[0].score == m.teams[1].score ? -1 : m.teams[0].score > m.teams[1].score ? 0 : 1);
            }
            case DOMINATION -> {
                tickNodes(m, now);
                respawnWave(m, now);
                if (m.teams[0].score >= DOMINATION_TARGET || m.teams[1].score >= DOMINATION_TARGET) {
                    endMatch(m, m.teams[0].score >= m.teams[1].score ? 0 : 1);
                } else if (elapsed >= BG_TIME_LIMIT) {
                    endMatch(m, m.teams[0].score == m.teams[1].score ? -1 : m.teams[0].score > m.teams[1].score ? 0 : 1);
                }
            }
        }
    }

    private boolean teamDead(Match m, Team t) {
        List<UnitState> units = unitsOf(m, t);
        if (units.isEmpty()) return true;
        for (UnitState u : units) if (u.isAlive()) return false;
        return true;
    }

    // ------------------------------------------------------------------ battlegrounds

    private void respawnWave(Match m, double now) {
        if (now < m.nextWave) return;
        m.nextWave = now + BG_RESPAWN_WAVE;
        for (Team t : m.teams) {
            Vec3 base = m.run.abs(m.run.layout.markers.get(t.index));
            for (UnitState u : unitsOf(m, t)) {
                if (!u.isDead()) continue;
                if (u.isPlayer()) {
                    PlayerSession s = server.session(u.uuid);
                    if (s != null) {
                        s.ghost = false;
                        server.platform.setGhost(s.uuid, false);
                        server.platform.teleport(s.uuid, m.run.worldKey, base, 0);
                    }
                } else if (u.body != null) {
                    u.body.teleport(base.add(server.rng.range(-2, 2), 0, server.rng.range(-2, 2)));
                }
                server.engine.resurrect(u, u, 1.0);
                if (!u.isPlayer()) server.bots.ensurePet(u);
            }
        }
    }

    private void tickFlags(Match m, double now) {
        for (int i = 0; i < 2; i++) {
            Flag f = m.flags[i];
            // carrier died or left
            if (f.carrier != null && (f.carrier.isDead() || matchOf(f.carrier) != m)) {
                dropFlag(m, i, f.carrier.position());
            }
            if (f.dropped != null && now - f.droppedAt > 20) returnFlag(m, i, null);
        }
        for (int t = 0; t < 2; t++) {
            for (UnitState u : unitsOf(m, m.teams[t])) {
                if (!u.isAlive()) continue;
                Flag enemyFlag = m.flags[1 - t];
                Flag ownFlag = m.flags[t];
                Vec3 pos = u.position();
                // pick up the enemy flag
                if (enemyFlag.carrier == null) {
                    Vec3 at = enemyFlag.dropped != null ? enemyFlag.dropped : enemyFlag.base;
                    if (pos.distance(at) < 2.0 && !u.auras().isStealthed()) pickUp(m, 1 - t, u);
                }
                // return own dropped flag
                if (ownFlag.dropped != null && pos.distance(ownFlag.dropped) < 2.0) returnFlag(m, t, u);
                // capture
                if (enemyFlag.carrier == u && ownFlag.carrier == null && ownFlag.dropped == null && pos.distance(ownFlag.base) < 3.0) {
                    capture(m, t, u);
                }
            }
        }
        // flag carriers take more damage over time (anti-turtle), like the Focused Assault debuff
        for (Flag f : m.flags) {
            if (f.carrier != null && now - m.lastScoreTick >= 30) {
                m.lastScoreTick = now;
                server.engine.applyAura(new EffectContext(server.engine, f.carrier, f.carrier, null, null, null), f.carrier, "focused_assault", 1, -1);
            }
        }
    }

    private void pickUp(Match m, int flagTeam, UnitState u) {
        Flag f = m.flags[flagTeam];
        f.carrier = u;
        if (f.dropped != null) server.platform.marker(m.run.worldKey, f.dropped, flagTeam == 0 ? "flag_blue" : "flag_red", false);
        else server.platform.marker(m.run.worldKey, f.base, flagTeam == 0 ? "flag_blue" : "flag_red", false);
        f.dropped = null;
        server.engine.applyAura(new EffectContext(server.engine, u, u, null, null, null), u, flagTeam == 0 ? "carrying_flag_blue" : "carrying_flag_red", 1, -1);
        BotBrain b = server.bots.brain(u);
        if (b != null) b.passive = true;
        broadcast(m, L10n.of(u.name + " picked up the " + flagName(flagTeam).en() + "!", u.name + " подбирает " + flagName(flagTeam).ru() + "!"),
                m.teams[1 - flagTeam].color);
    }

    private static L10n flagName(int team) {
        return team == 0 ? L10n.of("Blue flag", "синий флаг") : L10n.of("Red flag", "красный флаг");
    }

    private void dropFlag(Match m, int flagTeam, Vec3 at) {
        Flag f = m.flags[flagTeam];
        UnitState carrier = f.carrier;
        f.carrier = null;
        if (carrier != null) {
            server.engine.removeAura(carrier, flagTeam == 0 ? "carrying_flag_blue" : "carrying_flag_red", null);
            server.engine.removeAura(carrier, "focused_assault", null);
            BotBrain b = server.bots.brain(carrier);
            if (b != null) b.passive = false;
        }
        f.dropped = at;
        f.droppedAt = server.engine.now();
        server.platform.marker(m.run.worldKey, at, flagTeam == 0 ? "flag_blue" : "flag_red", true);
        broadcast(m, L10n.of("The " + flagName(flagTeam).en() + " was dropped!", "Брошен " + flagName(flagTeam).ru() + "!"), 0xFFFFD040);
    }

    private void returnFlag(Match m, int flagTeam, UnitState by) {
        Flag f = m.flags[flagTeam];
        if (f.dropped != null) server.platform.marker(m.run.worldKey, f.dropped, flagTeam == 0 ? "flag_blue" : "flag_red", false);
        f.dropped = null;
        f.carrier = null;
        server.platform.marker(m.run.worldKey, f.base, flagTeam == 0 ? "flag_blue" : "flag_red", true);
        broadcast(m, L10n.of("The " + flagName(flagTeam).en() + " was returned" + (by != null ? " by " + by.name : "") + ".",
                flagName(flagTeam).ru() + " возвращен на базу" + (by != null ? " (" + by.name + ")" : "") + "."), m.teams[flagTeam].color);
    }

    private void capture(Match m, int team, UnitState u) {
        m.teams[team].score++;
        Flag f = m.flags[1 - team];
        f.carrier = null;
        server.engine.removeAura(u, team == 0 ? "carrying_flag_red" : "carrying_flag_blue", null);
        server.engine.removeAura(u, "focused_assault", null);
        BotBrain b = server.bots.brain(u);
        if (b != null) b.passive = false;
        server.platform.marker(m.run.worldKey, f.base, team == 0 ? "flag_red" : "flag_blue", true);
        broadcast(m, L10n.of(u.name + " captured the flag! (" + m.teams[0].score + " : " + m.teams[1].score + ")",
                u.name + " захватывает флаг! (" + m.teams[0].score + " : " + m.teams[1].score + ")"), m.teams[team].color);
        server.platform.sound(m.run.worldKey, u.position(), "flag_capture");
    }

    private void tickNodes(Match m, double now) {
        double dt = GameServer.TICK;
        for (Node n : m.nodes) {
            int[] count = new int[2];
            for (int t = 0; t < 2; t++) {
                for (UnitState u : unitsOf(m, m.teams[t])) if (u.isAlive() && u.position().distance(n.pos) < 7) count[t]++;
            }
            int present = count[0] > 0 && count[1] == 0 ? 0 : count[1] > 0 && count[0] == 0 ? 1 : -1;
            if (present >= 0 && present != n.owner) {
                if (n.capturing != present) {
                    n.capturing = present;
                    n.progress = 0;
                }
                n.progress += dt;
                if (n.progress >= CAPTURE_TIME) {
                    String old = n.owner < 0 ? "node_neutral" : n.owner == 0 ? "node_blue" : "node_red";
                    server.platform.marker(m.run.worldKey, n.pos, old, false);
                    n.owner = present;
                    n.capturing = -1;
                    n.progress = 0;
                    server.platform.marker(m.run.worldKey, n.pos, present == 0 ? "node_blue" : "node_red", true);
                    broadcast(m, L10n.of((present == 0 ? "Blue" : "Red") + " team captured the " + n.name + "!",
                            (present == 0 ? "Синие" : "Красные") + " захватили точку «" + n.name + "»!"), m.teams[present].color);
                }
            } else if (present < 0 && count[0] == 0 && count[1] == 0) {
                n.progress = Math.max(0, n.progress - dt * 0.5);
            }
        }
        if (now - m.lastScoreTick >= 2) {
            m.lastScoreTick = now;
            int[] owned = new int[2];
            for (Node n : m.nodes) if (n.owner >= 0) owned[n.owner]++;
            int[] rate = {0, 10, 16, 30, 40, 60};
            for (int t = 0; t < 2; t++) m.teams[t].score = (int) Math.min(DOMINATION_TARGET, m.teams[t].score + rate[Math.min(5, owned[t])]);
        }
    }

    /** Where a PvP bot should go when it has nothing to fight. */
    Vec3 objectiveFor(UnitState bot) {
        Match m = matchOf(bot);
        if (m == null || m.state == State.ENDED) return null;
        int t = teamIndex(m, bot);
        if (t < 0) return null;
        if (m.state == State.PREP) return m.run.abs(m.run.layout.markers.get(t));
        switch (m.bracket.mode) {
            case CTF -> {
                Flag own = m.flags[t], enemy = m.flags[1 - t];
                if (enemy.carrier == bot) return own.base;
                if (own.dropped != null) return own.dropped;
                if (own.carrier != null && own.carrier.isAlive()) return own.carrier.position();
                int idx = server.bots.formationIndex(bot);
                if (idx % 3 == 2) return own.base.add(Vec3.fromYaw(idx * 70).mul(4));
                if (enemy.carrier != null) return enemy.carrier.position();
                return enemy.dropped != null ? enemy.dropped : enemy.base;
            }
            case DOMINATION -> {
                Node best = null;
                double bd = Double.MAX_VALUE;
                int idx = server.bots.formationIndex(bot);
                for (int i = 0; i < m.nodes.length; i++) {
                    Node n = m.nodes[i];
                    double d = n.pos.distance(bot.position()) + (n.owner == t ? 60 : 0) + ((i + idx) % m.nodes.length == 0 ? -15 : 0);
                    if (d < bd) {
                        bd = d;
                        best = n;
                    }
                }
                return best != null ? best.pos.add(Vec3.fromYaw(idx * 50).mul(2.5)) : null;
            }
            default -> {
                // arena: move towards the middle to find the enemy
                return m.run.abs(m.run.layout.font);
            }
        }
    }

    // ------------------------------------------------------------------ deaths

    void onPlayerDeath(UnitState unit, UnitState killer) {
        Match m = matchOf(unit);
        if (m == null) return;
        if (m.flags != null) {
            for (int i = 0; i < 2; i++) if (m.flags[i].carrier == unit) dropFlag(m, i, unit.position());
        }
        if (unit.isPlayer() && !m.isArena()) {
            server.msg(unit.uuid, L10n.of("You will be resurrected at the next spirit healer wave.", "Вы воскреснете со следующей волной духа-целителя."),
                    0xFFAAAAAA);
        }
    }

    void onDamage(HitResult hit) {
    }

    void release(PlayerSession s) {
        // battleground ghosts wait for the next wave; nothing to do in arenas
    }

    CastResult castFilter(UnitState caster, Ability a) {
        Match m = matchOf(caster);
        if (m == null) return null;
        if (m.isArena() && (a.hasTag("resurrect") || a.hasTag("battle_res"))) return CastResult.REQUIREMENT;
        if (m.state == State.ENDED || m.state == State.ROUND_END) return a.isHelpful() ? null : CastResult.REQUIREMENT;
        return null;
    }

    // ------------------------------------------------------------------ solo shuffle

    /** Six rounds, every round different 3v3 teams; personal result = rounds won. */
    private static final int[][] SHUFFLE_SPLITS = {{0, 1, 2}, {0, 3, 4}, {0, 2, 5}, {0, 1, 4}, {0, 3, 5}, {0, 2, 4}};

    private void endShuffleRound(Match m, int winner) {
        if (winner >= 0) {
            for (UnitState u : unitsOf(m, m.teams[winner])) m.roundWins.merge(rosterKey(u), 1, Integer::sum);
        }
        broadcast(m, L10n.of("Round " + m.round + " " + (winner < 0 ? "is a draw." : "won by the " + (winner == 0 ? "blue" : "red") + " team."),
                "Раунд " + m.round + (winner < 0 ? ": ничья." : ": победа " + (winner == 0 ? "синих" : "красных") + ".")), 0xFFFFD040);
        if (m.round >= 6) {
            endMatch(m, -3);
            return;
        }
        m.state = State.ROUND_END;
        m.nextRoundAt = server.engine.now() + 5;
    }

    private Object rosterKey(UnitState u) {
        return u.isPlayer() ? u.uuid : (Object) Integer.valueOf(u.id);
    }

    private void startShuffleRound(Match m) {
        m.round++;
        int[] split = SHUFFLE_SPLITS[(m.round - 1) % SHUFFLE_SPLITS.length];
        m.teams[0].players.clear();
        m.teams[0].bots.clear();
        m.teams[1].players.clear();
        m.teams[1].bots.clear();
        for (int i = 0; i < m.shuffleRoster.size(); i++) {
            boolean teamA = false;
            for (int s : split) if (s == i) teamA = true;
            Team t = teamA ? m.teams[0] : m.teams[1];
            Object o = m.shuffleRoster.get(i);
            if (o instanceof UUID uuid) t.players.add(uuid);
            else if (o instanceof UnitState bot) t.bots.add(bot);
        }
        Vec3[] starts = {m.run.abs(m.run.layout.markers.get(0)), m.run.abs(m.run.layout.markers.get(1))};
        for (Team t : m.teams) {
            for (UnitState u : unitsOf(m, t)) {
                u.team = t.key;
                for (UnitState pet : u.pets()) pet.team = t.key;
                restore(u);
                if (u.isPlayer()) server.platform.teleport(u.uuid, m.run.worldKey, starts[t.index], 0);
                else if (u.body != null) u.body.teleport(starts[t.index]);
            }
        }
        m.state = State.PREP;
        m.prepEnd = server.engine.now() + 8;
        m.dampening = 0;
        broadcast(m, L10n.of("Round " + m.round + " of 6 begins soon.", "Раунд " + m.round + " из 6 скоро начнется."), 0xFFFFD040);
    }

    // ------------------------------------------------------------------ end of match

    private void endMatch(Match m, int winner) {
        if (m.state == State.ENDED) return;
        m.state = State.ENDED;
        m.winner = winner;
        m.endAt = server.engine.now() + END_DELAY;
        double now = server.engine.now();
        boolean bg = !m.isArena();
        if (m.bracket.mode == Mode.SHUFFLE) {
            for (Object o : m.shuffleRoster) {
                if (!(o instanceof UUID uuid)) continue;
                PlayerSession s = server.session(uuid);
                if (s == null) continue;
                int wins = m.roundWins.getOrDefault(uuid, 0);
                PlayerProfile.PvpRating r = s.profile.pvp(m.bracket.id);
                int change = m.rated ? (wins - 3) * 12 + (wins >= 4 ? 4 : 0) : 0;
                applyRating(r, change, wins >= 3);
                m.ratingChanges.put(uuid.toString(), change);
                pvpRewards(s, wins >= 3, m);
                server.msg(s, L10n.of("Solo Shuffle finished: " + wins + "/6 rounds won" + (m.rated ? " (" + signed(change) + " rating)" : "") + ".",
                        "Одиночная потасовка окончена: " + wins + "/6 раундов выиграно" + (m.rated ? " (" + signed(change) + " рейтинга)" : "") + "."), 0xFFFFD040);
            }
        } else {
            for (int t = 0; t < 2; t++) {
                Team team = m.teams[t];
                Team other = m.teams[1 - t];
                double expected = 1.0 / (1.0 + Math.pow(10, (other.mmr - team.mmr) / 400.0));
                double result = winner == -1 ? 0.5 : winner == t ? 1 : 0;
                for (UUID p : team.players) {
                    PlayerSession s = server.session(p);
                    if (s == null) continue;
                    int change = 0;
                    if (m.rated) {
                        PlayerProfile.PvpRating r = s.profile.pvp(m.bracket.id);
                        double k = r.played < 10 ? 48 : 24;
                        change = (int) Math.round(k * (result - expected));
                        // rating catches up to MMR faster when far below it
                        if (change > 0 && r.rating < r.mmr - 100) change += 8;
                        applyRating(r, change, winner == t);
                        r.mmr = Math.max(0, r.mmr + (int) Math.round(k * (result - expected)));
                    } else {
                        PlayerProfile.PvpRating r = s.profile.pvp(m.bracket.id);
                        r.played++;
                        if (winner == t) r.won++;
                    }
                    m.ratingChanges.put(p.toString(), change);
                    pvpRewards(s, winner == t, m);
                }
            }
            L10n res = winner == -1 ? L10n.of("Draw!", "Ничья!")
                    : L10n.of((winner == 0 ? "Blue" : "Red") + " team wins!", "Победа " + (winner == 0 ? "синих" : "красных") + "!");
            broadcast(m, res, winner < 0 ? 0xFFFFD040 : m.teams[winner].color);
        }
        for (Team t : m.teams) {
            for (UUID p : t.players) {
                PlayerSession s = server.session(p);
                if (s != null) server.worldData().arenaRatings.put(p.toString(), s.profile.pvp(m.bracket.id).rating);
            }
        }
        sendStatus(m);
    }

    private static String signed(int v) {
        return v >= 0 ? "+" + v : String.valueOf(v);
    }

    private void applyRating(PlayerProfile.PvpRating r, int change, boolean won) {
        r.rating = Math.max(0, r.rating + change);
        r.seasonHigh = Math.max(r.seasonHigh, r.rating);
        r.played++;
        r.weeklyPlayed++;
        if (won) {
            r.won++;
            r.weeklyWon++;
        }
    }

    private void pvpRewards(PlayerSession s, boolean won, Match m) {
        PlayerProfile p = s.profile;
        long honor = m.isArena() ? (won ? 40 : 15) : (won ? 100 : 40);
        Map<Currency, Long> cur = new LinkedHashMap<>();
        cur.put(Currency.HONOR, honor);
        if (m.rated && won) cur.put(Currency.CONQUEST, m.bracket.mode == Mode.SHUFFLE ? 60L : 50L);
        Rewards.giveCurrencies(server, s, cur, m.bracket.name);
        p.honorProgress += honor;
        while (p.honorProgress >= 800 + p.honorLevel * 40L) {
            p.honorProgress -= 800 + p.honorLevel * 40L;
            p.honorLevel++;
            server.msg(s, L10n.of("Honor level " + p.honorLevel + "!", "Уровень чести " + p.honorLevel + "!"), 0xFFE06666);
        }
        if (won) p.vault.pvpWins++;
        p.stat(won ? "pvp_wins" : "pvp_losses", 1);
        s.characterDirty = true;
    }

    private void closeMatch(Match m) {
        for (Team t : m.teams) {
            for (UUID p : new ArrayList<>(t.players)) {
                PlayerSession s = server.session(p);
                if (s != null) exitPlayer(m, s, false);
            }
        }
        for (Object o : m.shuffleRoster) {
            if (o instanceof UUID uuid) {
                PlayerSession s = server.session(uuid);
                if (s != null && playerMatch.get(uuid) == m) exitPlayer(m, s, false);
            }
        }
        for (Team t : m.teams) {
            for (UnitState bot : new ArrayList<>(t.bots)) {
                botMatch.remove(bot.id);
                server.bots.despawn(bot);
            }
            t.bots.clear();
        }
        for (Object o : m.shuffleRoster) {
            if (o instanceof UnitState bot && server.engine.unit(bot.id) != null) {
                botMatch.remove(bot.id);
                server.bots.despawn(bot);
            }
        }
        if (m.flags != null) {
            for (int i = 0; i < 2; i++) {
                server.platform.marker(m.run.worldKey, m.flags[i].base, i == 0 ? "flag_blue" : "flag_red", false);
                if (m.flags[i].dropped != null) server.platform.marker(m.run.worldKey, m.flags[i].dropped, i == 0 ? "flag_blue" : "flag_red", false);
            }
        }
        if (m.nodes != null) {
            for (Node n : m.nodes) {
                server.platform.marker(m.run.worldKey, n.pos, n.owner < 0 ? "node_neutral" : n.owner == 0 ? "node_blue" : "node_red", false);
            }
        }
        matches.remove(m.id);
        server.instances.close(m.run);
    }

    private void exitPlayer(Match m, PlayerSession s, boolean deserter) {
        playerMatch.remove(s.uuid);
        s.pvpMatchId = null;
        m.run.members.remove(s.uuid);
        UnitState u = s.unit;
        if (u != null) {
            u.team = "players";
            for (UnitState pet : u.pets()) pet.team = "players";
            server.engine.removeAura(u, "dampening", null);
            for (String id : new String[]{"carrying_flag_blue", "carrying_flag_red", "focused_assault"}) server.engine.removeAura(u, id, null);
        }
        if (s.ghost) {
            s.ghost = false;
            server.platform.setGhost(s.uuid, false);
        }
        if (u != null) {
            server.engine.resetUnit(u, true);
            server.applyCharacter(s);
        }
        server.instances.exit(s, m.run);
        if (deserter && u != null) {
            server.engine.applyAura(new EffectContext(server.engine, u, u, null, null, null), u, "deserter", 1, -1);
        }
        S2C.PvpStatus st = new S2C.PvpStatus();
        st.active = false;
        server.send(s, st);
    }

    /** Player leaves a match early. */
    public void leaveMatch(PlayerSession s) {
        Match m = playerMatch.get(s.uuid);
        if (m == null) return;
        boolean early = m.state != State.ENDED;
        if (early && m.rated) {
            PlayerProfile.PvpRating r = s.profile.pvp(m.bracket.id);
            applyRating(r, -20, false);
        }
        for (Team t : m.teams) t.players.remove(s.uuid);
        m.shuffleRoster.remove(s.uuid);
        exitPlayer(m, s, early && !m.isArena());
        // no real players left: end
        boolean any = false;
        for (Team t : m.teams) if (!t.players.isEmpty()) any = true;
        if (!any) closeMatch(m);
    }

    void onLeave(UUID uuid) {
        leaveQueue(uuid, false);
        Match m = playerMatch.get(uuid);
        if (m != null) {
            for (Team t : m.teams) t.players.remove(uuid);
            m.shuffleRoster.remove(uuid);
            playerMatch.remove(uuid);
            m.run.members.remove(uuid);
            boolean any = false;
            for (Team t : m.teams) if (!t.players.isEmpty()) any = true;
            if (!any) closeMatch(m);
        }
    }

    // ------------------------------------------------------------------ status

    private void broadcast(Match m, L10n text, int color) {
        for (Team t : m.teams) for (UUID p : t.players) server.warn(p, text, color);
        for (Object o : m.shuffleRoster) if (o instanceof UUID uuid && !m.teams[0].players.contains(uuid) && !m.teams[1].players.contains(uuid)) server.warn(uuid, text, color);
    }

    private void sendStatus(Match m) {
        double now = server.engine.now();
        for (Team t : m.teams) {
            for (UUID p : t.players) {
                PlayerSession s = server.session(p);
                if (s == null) continue;
                S2C.PvpStatus st = new S2C.PvpStatus();
                st.active = true;
                st.mode = m.bracket.mode.name();
                st.name = new S2C.Text(m.bracket.name.en() + " — " + m.map.name.en(), m.bracket.name.ru() + " — " + m.map.name.ru());
                st.countdown = m.state == State.PREP ? (float) Math.max(0, m.prepEnd - now) : 0;
                st.elapsed = m.state == State.ACTIVE || m.state == State.ENDED ? (float) (now - m.startTime) : 0;
                st.timeLimit = (float) (m.isArena() ? ARENA_TIME_LIMIT : BG_TIME_LIMIT);
                st.dampening = m.dampening;
                st.myTeam = t.index;
                for (Team tt : m.teams) {
                    S2C.PvpTeam pt = new S2C.PvpTeam();
                    pt.name = tt.index == 0 ? "Blue" : "Red";
                    pt.color = tt.color;
                    pt.score = m.bracket.mode == Mode.SHUFFLE ? 0 : tt.score;
                    for (UnitState u : unitsOf(m, tt)) {
                        pt.players.add(u.name);
                        pt.alive.add(u.isAlive());
                    }
                    st.teams.add(pt);
                }
                if (m.flags != null) {
                    for (int i = 0; i < 2; i++) {
                        Flag f = m.flags[i];
                        st.objectives.add((i == 0 ? "blue_flag:" : "red_flag:") + (f.carrier != null ? "carried:" + f.carrier.name : f.dropped != null ? "dropped" : "base"));
                    }
                }
                if (m.nodes != null) for (Node n : m.nodes) st.objectives.add("node:" + n.name + ":" + n.owner + ":" + Math.round(n.progress / CAPTURE_TIME * 100));
                if (m.bracket.mode == Mode.SHUFFLE) st.objectives.add("round:" + m.round + ":" + m.roundWins.getOrDefault(p, 0));
                if (m.state == State.ENDED) {
                    st.result = m.winner == -3 ? "shuffle" : m.winner == -1 ? "draw" : m.winner == t.index ? "win" : "loss";
                    st.ratingChange = m.ratingChanges.getOrDefault(p.toString(), 0);
                }
                server.send(s, st);
            }
        }
    }

    public List<Match> matches() {
        return new ArrayList<>(matches.values());
    }

    static String formatTime(double s) {
        return Mth.formatTime(s);
    }

    static boolean has(UnitState u, String aura) {
        for (AuraInstance a : u.auras().all()) if (a.def.id.equals(aura)) return true;
        return false;
    }
}
