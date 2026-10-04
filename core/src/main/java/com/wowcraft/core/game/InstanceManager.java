package com.wowcraft.core.game;

import com.wowcraft.core.combat.Formulas;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.instance.LayoutGenerator;
import com.wowcraft.core.instance.Palette;
import com.wowcraft.core.item.Currency;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.mythic.Affix;
import com.wowcraft.core.mythic.AffixSchedule;
import com.wowcraft.core.mythic.MythicRun;
import com.wowcraft.core.mythic.MythicScore;
import com.wowcraft.core.mythic.MythicTables;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.npc.Encounter;
import com.wowcraft.core.npc.NpcManager;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Mth;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Instances: builds dungeon / raid maps in the instance dimension, spawns enemies, runs Mythic+ keys,
 * checkpoints, deaths, lockouts and rewards. Arenas and battlegrounds also get their maps here.
 */
public final class InstanceManager {
    public static final int SLOT_SPACING = 1024, BASE_X = 10_000, BASE_Z = 10_000, BASE_Y = 100, MAX_SLOTS = 256;

    private final GameServer server;
    private final Map<String, InstanceRun> runs = new LinkedHashMap<>();
    private final Map<String, Layout> layouts = new HashMap<>();
    private final BitSet usedSlots = new BitSet();
    private int nextRunId = 1;

    InstanceManager(GameServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ lookup

    public InstanceRun run(String id) {
        return id == null ? null : runs.get(id);
    }

    public List<InstanceRun> all() {
        return new ArrayList<>(runs.values());
    }

    public InstanceRun runOf(UnitState u) {
        if (u == null) return null;
        UnitState m = u.master();
        return m.instanceId == null ? null : runs.get(m.instanceId);
    }

    public InstanceRun runOfGroup(Group g) {
        if (g == null) return null;
        for (InstanceRun r : runs.values()) if (g.id.equals(r.groupId) && r.state != InstanceRun.State.CLOSING && r.pvpMatchId == null) return r;
        return null;
    }

    public boolean groupHasRun(Group g) {
        return runOfGroup(g) != null;
    }

    public Layout layout(DungeonDef def) {
        return layouts.computeIfAbsent(def.id, k -> LayoutGenerator.generate(def));
    }

    // ------------------------------------------------------------------ entering

    /** Leader asks to enter a dungeon / raid with the group. */
    public void requestEnter(PlayerSession leader, DungeonDef def, Difficulty diff) {
        if (def == null) return;
        if (!def.difficulties.contains(diff)) {
            server.msg(leader, L10n.of(def.name.en() + " has no " + diff.name.en() + " difficulty.",
                    "В «" + def.name.ru() + "» нет сложности «" + diff.name.ru() + "»."), 0xFFFF4040);
            return;
        }
        if (leader.profile.level < Math.min(def.level, Formulas.MAX_LEVEL) - 5) {
            server.msg(leader, L10n.of("You need level " + (def.level - 5) + ".", "Требуется уровень " + (def.level - 5) + "."), 0xFFFF4040);
            return;
        }
        if (leader.unit.inCombat()) {
            server.msg(leader, L10n.of("You are in combat.", "Вы в бою."), 0xFFFF4040);
            return;
        }
        if (server.pvp.matchOfSession(leader) != null) return;
        Group g = server.groups.ensureGroup(leader.uuid);
        if (!g.leader.equals(leader.uuid)) {
            InstanceRun existing = runOfGroup(g);
            if (existing != null && existing.def == def) {
                teleportIn(leader, existing);
                return;
            }
            server.msg(leader, L10n.of("Only the group leader can do that.", "Это может сделать только лидер группы."), 0xFFFF4040);
            return;
        }
        int keyLevel = 0;
        if (diff == Difficulty.MYTHIC_PLUS) {
            PlayerProfile.Keystone key = findKey(g, def);
            if (key == null) {
                server.msg(leader, L10n.of("Nobody in the group has a keystone for " + def.name.en() + ".",
                        "Ни у кого в группе нет ключа для «" + def.name.ru() + "»."), 0xFFFF4040);
                return;
            }
            keyLevel = key.level;
        }
        InstanceRun existing = runOfGroup(g);
        if (existing != null) {
            if (existing.def == def && existing.difficulty == diff && existing.state != InstanceRun.State.COMPLETED) {
                for (UUID m : g.members) {
                    PlayerSession s = server.session(m);
                    if (s != null && !existing.id.equals(s.unit.instanceId)) teleportIn(s, existing);
                }
                return;
            }
            if (existing.mythic != null && existing.mythic.started() && !existing.mythic.completed) {
                server.msg(leader, L10n.of("Your group is in an active keystone run.", "Ваша группа проходит активный ключ."), 0xFFFF4040);
                return;
            }
            close(existing);
        }
        create(def, diff, keyLevel, g);
    }

    /** The keystone used for a run: the leader's first, then any member's. */
    PlayerProfile.Keystone findKey(Group g, DungeonDef def) {
        PlayerSession leader = server.session(g.leader);
        if (leader != null && leader.profile.keystone != null && def.id.equals(leader.profile.keystone.dungeonId)) return leader.profile.keystone;
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            if (s != null && s.profile.keystone != null && def.id.equals(s.profile.keystone.dungeonId)) return s.profile.keystone;
        }
        return null;
    }

    UUID keyOwner(Group g, DungeonDef def) {
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            if (s != null && s.profile.keystone != null && def.id.equals(s.profile.keystone.dungeonId)) {
                if (m.equals(g.leader)) return m;
            }
        }
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            if (s != null && s.profile.keystone != null && def.id.equals(s.profile.keystone.dungeonId)) return m;
        }
        return null;
    }

    /** Creates an instance without a group (server smoke tests, admin tools). */
    public InstanceRun createStandalone(DungeonDef def, Difficulty diff) {
        return create(def, diff, 0, null);
    }

    /** Creates a PvP map without a match (server smoke tests). */
    public InstanceRun createStandaloneMap(DungeonDef def) {
        return createPvpMap(def, "smoke");
    }

    InstanceRun create(DungeonDef def, Difficulty diff, int keyLevel, Group g) {
        int slot = usedSlots.nextClearBit(0);
        if (slot >= MAX_SLOTS) {
            if (g != null) server.groups.broadcast(g, L10n.of("All instance servers are busy.", "Все серверы подземелий заняты."), 0xFFFF4040);
            return null;
        }
        usedSlots.set(slot);
        Layout layout = layout(def);
        Vec3 origin = new Vec3(BASE_X + (slot % 16) * SLOT_SPACING, BASE_Y, BASE_Z + (slot / 16) * SLOT_SPACING);
        String id = "i" + (nextRunId++);
        InstanceRun run = new InstanceRun(id, def, diff, layout, origin, server.platform.instanceWorld(), slot);
        run.createdAt = server.engine.now();
        run.groupId = g != null ? g.id : null;
        if (g != null) run.groupSize = Math.max(def.isRaid() ? 10 : 5, g.size());
        if (diff == Difficulty.MYTHIC_PLUS && g != null) {
            UUID owner = keyOwner(g, def);
            List<Affix> affixes = AffixSchedule.affixes(server.week, keyLevel);
            double totalForces = 0;
            for (Layout.Spawn sp : layout.spawns) {
                NpcTemplate t = NpcRegistry.get(sp.templateId());
                if (t != null && !sp.boss()) totalForces += t.forcesValue();
            }
            run.mythic = new MythicRun(def.id, keyLevel, affixes, def.timer, Math.max(1, Math.floor(totalForces * 0.85)), def.bossIds().size(), owner);
            run.keyOwner = owner;
        }
        runs.put(id, run);
        if (g != null) {
            server.groups.broadcast(g, L10n.of("Preparing " + def.name.en() + " (" + diff.name.en() + (keyLevel > 0 ? " +" + keyLevel : "") + ")...",
                    "Подготовка: " + def.name.ru() + " (" + diff.name.ru() + (keyLevel > 0 ? " +" + keyLevel : "") + ")..."), 0xFFFFD040);
        }
        build(run);
        return run;
    }

    private void build(InstanceRun run) {
        Palette palette = Palette.get(run.def.palette);
        server.platform.forceLoad(run.worldKey, run.origin, run.layout, true);
        server.platform.build(run.worldKey, run.origin, run.layout, palette, () -> onBuilt(run));
    }

    private void onBuilt(InstanceRun run) {
        if (run.state == InstanceRun.State.CLOSING) return;
        run.state = InstanceRun.State.READY;
        server.platform.marker(run.worldKey, run.abs(run.layout.exit), "exit_portal", true);
        if (run.pvpMatchId != null) {
            server.pvp.onMapReady(run);
            return;
        }
        if (run.mythic != null) {
            server.platform.marker(run.worldKey, run.abs(run.layout.font), "font_of_power", true);
        } else {
            spawnAll(run);
        }
        Group g = server.groups.group(run.groupId);
        if (g != null) {
            for (UUID m : g.members) {
                PlayerSession s = server.session(m);
                if (s != null && s.unit != null && !s.unit.inCombat() && server.pvp.matchOfSession(s) == null) teleportIn(s, run);
            }
            for (UnitState bot : g.bots) moveBotIn(bot, run);
        }
    }

    private void spawnAll(InstanceRun run) {
        for (Layout.Spawn sp : run.layout.spawns) {
            spawnNpc(run, sp.templateId(), run.abs(sp.pos()), sp.yaw(), sp.packId());
        }
    }

    /** Spawns an NPC belonging to a run (layout spawns, affix adds, boss adds). */
    UnitState spawnNpc(InstanceRun run, String templateId, Vec3 pos, float yaw, String packId) {
        NpcTemplate t = NpcRegistry.get(templateId);
        if (t == null) {
            server.platform.log("Unknown NPC template in " + run.def.id + ": " + templateId);
            return null;
        }
        NpcManager.SpawnSpec spec = new NpcManager.SpawnSpec();
        spec.worldKey = run.worldKey;
        spec.pos = pos;
        spec.yaw = yaw;
        spec.level = Math.min(Formulas.MAX_LEVEL, Math.max(1, run.def.level));
        spec.difficulty = run.difficulty;
        spec.instanced = true;
        spec.groupSize = run.groupSize;
        spec.instanceId = run.id;
        spec.packId = packId;
        if (run.affixes != null) {
            double[] m = run.affixes.multipliers(t);
            spec.healthMult = m[0];
            spec.damageMult = m[1];
        } else if (run.mythic != null) {
            spec.healthMult = run.mythic.scaling();
            spec.damageMult = run.mythic.scaling();
        }
        UnitState u = server.npcs.spawn(templateId, spec);
        if (u == null) return null;
        run.npcs.add(u);
        if (t.rank == NpcRank.BOSS) run.bossUnits.putIfAbsent(templateId, u);
        if (run.bossesKilled.contains(templateId) && t.rank == NpcRank.BOSS) {
            // boss already killed (re-spawn after a reset): remove it again
            server.npcs.despawn(u);
            run.npcs.remove(u);
            return null;
        }
        return u;
    }

    public void teleportIn(PlayerSession s, InstanceRun run) {
        if (run.state == InstanceRun.State.BUILDING) {
            server.msg(s, L10n.of("The instance is still being prepared...", "Подземелье еще готовится..."), 0xFFFFD040);
            return;
        }
        if (s.unit.instanceId == null) saveReturnPoint(s);
        run.members.add(s.uuid);
        run.emptySince = -1;
        s.unit.instanceId = run.id;
        s.instanceId = run.id;
        for (UnitState pet : s.unit.pets()) pet.instanceId = run.id;
        Vec3 dest = run.checkpoint;
        server.platform.teleport(s.uuid, run.worldKey, dest, run.layout.entranceYaw);
        for (UnitState pet : s.unit.livingPets()) server.platform.moveBody(pet, run.worldKey, dest);
        server.msg(s, L10n.of("Entering " + run.def.name.en() + " (" + run.difficulty.name.en() + (run.keyLevel() > 0 ? " +" + run.keyLevel() : "") + ").",
                "Вход: " + run.def.name.ru() + " (" + run.difficulty.name.ru() + (run.keyLevel() > 0 ? " +" + run.keyLevel() : "") + ")."), 0xFFFFD040);
        if (run.mythic != null && !run.mythic.started()) {
            server.msg(s, L10n.of("Use the Font of Power or /wow start to activate the keystone.",
                    "Используйте Источник силы или /wow start, чтобы активировать ключ."), 0xFFA335EE);
        }
        sendStatus(run);
    }

    private void moveBotIn(UnitState bot, InstanceRun run) {
        if (!run.bots.contains(bot)) run.bots.add(bot);
        bot.instanceId = run.id;
        for (UnitState pet : bot.pets()) pet.instanceId = run.id;
        if (bot.isDead()) server.engine.resurrect(bot, bot, 1.0);
        Vec3 p = run.checkpoint.add(server.rng.range(-2, 2), 0, server.rng.range(-2, 2));
        server.platform.moveBody(bot, run.worldKey, p);
        for (UnitState pet : bot.livingPets()) server.platform.moveBody(pet, run.worldKey, p);
    }

    private void saveReturnPoint(PlayerSession s) {
        PlayerProfile p = s.profile;
        p.returnWorld = s.unit.worldKey();
        Vec3 pos = s.unit.position();
        p.returnX = pos.x();
        p.returnY = pos.y();
        p.returnZ = pos.z();
    }

    /** Leaves the instance, returning to where the player entered. */
    public void leave(PlayerSession s) {
        InstanceRun run = run(s.unit.instanceId);
        if (run == null && s.unit.instanceId == null) {
            server.msg(s, L10n.of("You are not in an instance.", "Вы не в подземелье."), 0xFFFF4040);
            return;
        }
        if (run != null && run.pvpMatchId != null) {
            server.pvp.leaveMatch(s);
            return;
        }
        exit(s, run);
    }

    void exit(PlayerSession s, InstanceRun run) {
        if (run != null) run.members.remove(s.uuid);
        s.unit.instanceId = null;
        s.instanceId = null;
        for (UnitState pet : s.unit.pets()) pet.instanceId = null;
        if (s.ghost || s.unit.isDead()) {
            s.ghost = false;
            server.platform.setGhost(s.uuid, false);
            server.engine.resurrect(s.unit, s.unit, 1.0);
        }
        PlayerProfile p = s.profile;
        String world = p.returnWorld != null ? p.returnWorld : server.platform.defaultWorld();
        Vec3 dest = p.returnWorld != null ? new Vec3(p.returnX, p.returnY, p.returnZ) : null;
        server.platform.teleport(s.uuid, world, dest, s.unit.yaw());
        for (UnitState pet : s.unit.livingPets()) if (dest != null) server.platform.moveBody(pet, world, dest);
        S2C.InstanceStatus st = new S2C.InstanceStatus();
        st.active = false;
        server.send(s, st);
        S2C.BossTimers clear = new S2C.BossTimers();
        server.send(s, clear);
        // group bots follow their leader out
        Group g = server.groups.groupOf(s.uuid);
        if (g != null && run != null && g.leader.equals(s.uuid)) {
            for (UnitState bot : new ArrayList<>(run.bots)) {
                if (!g.bots.contains(bot)) continue;
                run.bots.remove(bot);
                bot.instanceId = null;
                for (UnitState pet : bot.pets()) pet.instanceId = null;
                if (bot.isDead()) server.engine.resurrect(bot, bot, 1.0);
                if (dest != null) server.platform.moveBody(bot, world, dest);
            }
        }
    }

    public void kickFromRun(UUID uuid) {
        PlayerSession s = server.session(uuid);
        if (s == null || s.unit == null) return;
        InstanceRun run = run(s.unit.instanceId);
        if (run != null && run.pvpMatchId == null) exit(s, run);
    }

    void onLeftGroup(UUID uuid, Group g) {
        InstanceRun run = runOfGroup(g);
        if (run == null) return;
        PlayerSession s = server.session(uuid);
        if (s != null && s.unit != null && run.id.equals(s.unit.instanceId)) {
            server.msg(s, L10n.of("You left the group and are removed from the instance.", "Вы покинули группу и будете удалены из подземелья."), 0xFFFF8040);
            exit(s, run);
        }
    }

    /** The player came online (or respawned): restore instance membership or move them out. */
    void onJoin(PlayerSession s) {
        if (s.unit == null) return;
        String wk = s.unit.worldKey();
        if (wk == null || !wk.equals(server.platform.instanceWorld())) {
            s.unit.instanceId = null;
            return;
        }
        for (InstanceRun run : runs.values()) {
            if (run.members.contains(s.uuid) && run.state != InstanceRun.State.CLOSING) {
                s.unit.instanceId = run.id;
                s.instanceId = run.id;
                return;
            }
        }
        // stale position inside the instance world (server restart): send home
        s.unit.instanceId = null;
        PlayerProfile p = s.profile;
        String world = p.returnWorld != null ? p.returnWorld : server.platform.defaultWorld();
        Vec3 dest = p.returnWorld != null ? new Vec3(p.returnX, p.returnY, p.returnZ) : null;
        server.platform.teleport(s.uuid, world, dest, 0);
    }

    // ------------------------------------------------------------------ Mythic+

    public void startKey(PlayerSession s) {
        InstanceRun run = run(s.unit.instanceId);
        if (run == null || run.mythic == null) {
            server.msg(s, L10n.of("You are not in a keystone dungeon.", "Вы не в подземелье с ключом."), 0xFFFF4040);
            return;
        }
        MythicRun m = run.mythic;
        if (m.started() || m.countdownEnd > 0) return;
        Group g = server.groups.group(run.groupId);
        if (g != null) {
            for (UUID member : g.members) {
                PlayerSession ms = server.session(member);
                if (ms != null && (ms.unit == null || !run.id.equals(ms.unit.instanceId))) {
                    server.msg(s, L10n.of(ms.name + " is not in the dungeon yet.", ms.name + " еще не в подземелье."), 0xFFFF4040);
                    return;
                }
            }
        }
        // consume the key (it comes back upgraded on a timed run)
        PlayerSession owner = m.keyOwner != null ? server.session(m.keyOwner) : null;
        if (owner != null && owner.profile.keystone != null && run.def.id.equals(owner.profile.keystone.dungeonId)) {
            PlayerProfile.Keystone k = owner.profile.keystone;
            k.level = Math.max(2, k.level - 1);
            k.dungeonId = randomMythicDungeon(null);
            owner.characterDirty = true;
        }
        m.partyNames.clear();
        for (UnitState u : server.participants(run)) m.partyNames.add(u.name);
        run.affixes = new AffixHandler(server, run);
        spawnAll(run);
        m.countdownEnd = server.engine.now() + MythicRun.COUNTDOWN;
        for (UnitState u : server.participants(run)) {
            if (u.isPlayer()) server.platform.teleport(u.uuid, run.worldKey, run.abs(run.layout.entrance), run.layout.entranceYaw);
            else if (u.body != null) u.body.teleport(run.abs(run.layout.entrance));
        }
        StringBuilder en = new StringBuilder(), ru = new StringBuilder();
        for (Affix a : m.affixes) {
            if (!en.isEmpty()) {
                en.append(", ");
                ru.append(", ");
            }
            en.append(a.name.en());
            ru.append(a.name.ru());
        }
        server.warnRun(run, L10n.of("Keystone +" + m.level + " activated: " + en, "Ключ +" + m.level + " активирован: " + ru), 0xFFA335EE);
        server.platform.sound(run.worldKey, run.abs(run.layout.font), "keystone_start");
    }

    String randomMythicDungeon(String except) {
        List<String> pool = new ArrayList<>(Dungeons.mythicPool());
        if (except != null && pool.size() > 1) pool.remove(except);
        return pool.isEmpty() ? null : pool.get(server.rng.nextInt(pool.size()));
    }

    private void tickMythic(InstanceRun run, double now) {
        MythicRun m = run.mythic;
        if (m.countdownEnd > 0 && !m.started()) {
            // hold everyone at the start until the countdown ends
            Layout.PlacedRoom start = run.layout.rooms.get(0);
            for (UnitState u : server.participants(run)) {
                if (!start.contains(run.rel(u.position()))) {
                    if (u.isPlayer()) server.platform.teleport(u.uuid, run.worldKey, run.abs(run.layout.entrance), run.layout.entranceYaw);
                    else if (u.body != null) u.body.teleport(run.abs(run.layout.entrance));
                }
            }
            if (now >= m.countdownEnd) {
                m.startTime = now;
                server.warnRun(run, L10n.of("Go! The timer has started.", "Вперед! Таймер запущен."), 0xFF40FF40);
                server.platform.sound(run.worldKey, run.abs(run.layout.entrance), "keystone_go");
            }
        }
        if (m.started() && !m.completed) {
            if (run.affixes != null) run.affixes.tick();
            if (m.objectivesDone()) completeMythic(run, now);
        }
    }

    private void completeMythic(InstanceRun run, double now) {
        MythicRun m = run.mythic;
        m.completed = true;
        m.finishTime = now;
        run.state = InstanceRun.State.COMPLETED;
        if (run.affixes != null) run.affixes.clear();
        double elapsed = m.elapsed(now);
        boolean timed = elapsed <= m.timer;
        int upgrades = MythicScore.upgrades(elapsed, m.timer);
        double score = MythicScore.runScore(m.level, elapsed / m.timer, m.affixes.size());
        L10n result = timed
                ? L10n.of("Keystone timed! " + Mth.formatTime(elapsed) + " / " + Mth.formatTime(m.timer) + " (+" + upgrades + ")",
                "Ключ пройден в срок! " + Mth.formatTime(elapsed) + " / " + Mth.formatTime(m.timer) + " (+" + upgrades + ")")
                : L10n.of("Keystone completed out of time: " + Mth.formatTime(elapsed) + " / " + Mth.formatTime(m.timer),
                "Ключ пройден не в срок: " + Mth.formatTime(elapsed) + " / " + Mth.formatTime(m.timer));
        server.warnRun(run, result, timed ? 0xFF40FF40 : 0xFFFF8040);
        // leaderboard
        WorldData.Run rec = new WorldData.Run();
        rec.dungeon = run.def.id;
        rec.level = m.level;
        rec.time = elapsed;
        rec.timed = timed;
        rec.when = System.currentTimeMillis();
        rec.party.addAll(m.partyNames);
        server.worldData().record(rec);
        int lootIlvl = MythicTables.endOfRunItemLevel(m.level);
        List<PlayerSession> present = new ArrayList<>();
        for (UUID u : run.members) {
            PlayerSession s = server.session(u);
            if (s != null && s.unit != null && run.id.equals(s.unit.instanceId)) present.add(s);
        }
        // the chest: ~2 items per run, at least one roll per player at 25%
        int guaranteed = Math.min(2, present.size());
        List<PlayerSession> shuffled = new ArrayList<>(present);
        java.util.Collections.shuffle(shuffled, new java.util.Random(server.rng.nextLong()));
        for (int i = 0; i < shuffled.size(); i++) {
            PlayerSession s = shuffled.get(i);
            PlayerProfile p = s.profile;
            // best run
            PlayerProfile.MythicBest best = p.mythicBest.get(run.def.id);
            if (best == null || score > best.score) {
                best = new PlayerProfile.MythicBest();
                best.level = m.level;
                best.time = elapsed;
                best.timed = timed;
                best.score = Math.round(score * 10) / 10.0;
                best.when = rec.when;
                for (Affix a : m.affixes) best.affixes.add(a.name());
                best.party.addAll(m.partyNames);
                p.mythicBest.put(run.def.id, best);
            }
            p.vault.mythicRuns.add(m.level);
            p.stat("mythic_runs", 1);
            if (timed) p.stat("mythic_timed", 1);
            server.worldData().ratings.put(s.uuid.toString(), MythicScore.totalRating(p));
            Rewards.dungeonCompleted(server, s, run);
            boolean gets = i < guaranteed || server.rng.chance(0.25);
            if (gets && p.spec() != null) {
                ItemData item = com.wowcraft.core.item.LootGenerator.personalLoot(p.spec(), run.def.lootTable, lootIlvl, server.rng, run.def.id, run.def.palette, 0.08);
                Rewards.giveLoot(server, s, List.of(item), L10n.of(run.def.name.en() + " +" + m.level, run.def.name.ru() + " +" + m.level));
            }
            // keystones
            if (s.uuid.equals(m.keyOwner)) {
                int newLevel = timed ? m.level + upgrades : Math.max(2, m.level - 1);
                p.keystone = new PlayerProfile.Keystone(randomMythicDungeon(run.def.id), newLevel);
                DungeonDef nd = Dungeons.get(p.keystone.dungeonId);
                server.msg(s, L10n.of("Your new keystone: " + nd.name.en() + " +" + newLevel, "Ваш новый ключ: " + nd.name.ru() + " +" + newLevel), 0xFFA335EE);
            } else if (p.keystone == null) {
                p.keystone = new PlayerProfile.Keystone(randomMythicDungeon(null), Math.max(2, m.level - 1));
                DungeonDef nd = Dungeons.get(p.keystone.dungeonId);
                server.msg(s, L10n.of("You received a keystone: " + nd.name.en() + " +" + p.keystone.level,
                        "Вы получили ключ: " + nd.name.ru() + " +" + p.keystone.level), 0xFFA335EE);
            }
            s.characterDirty = true;
            server.msg(s, L10n.of("Mythic+ rating: " + MythicScore.totalRating(p), "Рейтинг M+: " + MythicScore.totalRating(p)), MythicScore.color(MythicScore.totalRating(p)));
        }
        server.platform.marker(run.worldKey, run.abs(run.layout.exit), "exit_portal", true);
        sendStatus(run);
    }

    // ------------------------------------------------------------------ deaths & releases

    void onPlayerDeath(PlayerSession s) {
        InstanceRun run = run(s.unit.instanceId);
        if (run == null || run.pvpMatchId != null) return;
        if (run.mythic != null && run.mythic.started() && !run.mythic.completed) {
            run.mythic.deaths++;
            server.warnRun(run, L10n.of(s.name + " died (+" + (int) run.mythic.deathPenalty + "s)", s.name + " погиб(ла) (+" + (int) run.mythic.deathPenalty + " с)"),
                    0xFFFF6060);
        }
        if (server.config.instantReleaseInInstances) s.releaseAvailableAt = server.engine.now();
        else s.releaseAvailableAt = server.engine.now() + 2;
    }

    void onBotDeath(UnitState bot) {
        InstanceRun run = runOf(bot);
        if (run != null && run.mythic != null && run.mythic.started() && !run.mythic.completed) run.mythic.deaths++;
    }

    /** Ghost releases to the last checkpoint. */
    public void release(PlayerSession s) {
        if (!s.ghost) return;
        InstanceRun run = run(s.unit.instanceId);
        if (run == null) {
            server.pvp.release(s);
            return;
        }
        if (server.engine.now() < s.releaseAvailableAt) return;
        Encounter active = run.activeEncounter();
        if (active != null && run.difficulty.isRaid()) {
            server.platform.teleport(s.uuid, run.worldKey, run.checkpoint, run.layout.entranceYaw);
            server.msg(s, L10n.of("You can return when the encounter ends (or get a battle resurrection).",
                    "Вы сможете вернуться после окончания боя (или после боевого воскрешения)."), 0xFFFF8040);
            return;
        }
        server.platform.teleport(s.uuid, run.worldKey, run.checkpoint, run.layout.entranceYaw);
        s.ghost = false;
        server.platform.setGhost(s.uuid, false);
        server.engine.resurrect(s.unit, s.unit, 1.0);
    }

    private void reviveAll(InstanceRun run, double fraction) {
        for (UUID m : run.members) {
            PlayerSession s = server.session(m);
            if (s == null || s.unit == null || !run.id.equals(s.unit.instanceId)) continue;
            if (s.ghost || s.unit.isDead()) {
                s.ghost = false;
                server.platform.setGhost(s.uuid, false);
                server.platform.teleport(s.uuid, run.worldKey, run.checkpoint, run.layout.entranceYaw);
                server.engine.resurrect(s.unit, s.unit, fraction);
            }
        }
        server.bots.reviveAll(run.bots, run.checkpoint);
    }

    // ------------------------------------------------------------------ combat events

    void onDamage(HitResult hit) {
        InstanceRun run = runOf(hit.target);
        if (run != null && run.affixes != null) run.affixes.onDamage(hit);
    }

    void onNpcDeath(UnitState npc, UnitState killer) {
        InstanceRun run = runOf(npc);
        if (run == null) return;
        if (run.affixes != null) run.affixes.onNpcDeath(npc);
        MythicRun m = run.mythic;
        if (m != null && m.started() && !m.completed && npc.owner == null) {
            double before = m.forcesPercent();
            m.forces += npc.forces;
            if (before < 100 && m.forcesPercent() >= 100) {
                server.warnRun(run, L10n.of("Enemy forces: 100%", "Силы противника: 100%"), 0xFF40FF40);
            }
        }
        for (Encounter e : run.encounters) {
            if (e.adds.contains(npc) && e.script != null) {
                try {
                    e.script.onAddDeath(npc);
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    void onUnitRemoved(UnitState u) {
        InstanceRun run = run(u.instanceId);
        if (run != null) {
            run.npcs.remove(u);
            run.bots.remove(u);
        }
    }

    void onEncounterEnd(Encounter e, boolean victory) {
        if (!(e.instance instanceof InstanceRun run)) return;
        if (!victory) {
            server.warnRun(run, L10n.of("Wipe! Regroup at the checkpoint.", "Вайп! Соберитесь у точки возрождения."), 0xFFFF4040);
            server.engine.schedule(4, () -> reviveAll(run, 1.0));
            run.encounters.remove(e);
            return;
        }
        double now = server.engine.now();
        List<String> killedIds = new ArrayList<>();
        for (UnitState b : e.bosses) if (b.templateId != null) killedIds.add(b.templateId);
        int bossIndex = 0;
        List<String> order = run.def.bossIds();
        for (String id : killedIds) {
            run.bossesKilled.add(id);
            if (run.mythic != null) run.mythic.bossesKilled.add(id);
            bossIndex = Math.max(bossIndex, order.indexOf(id));
        }
        NpcTemplate t = NpcRegistry.get(e.bossTemplateId);
        String bossName = t != null ? t.name.en() : e.bossTemplateId;
        String bossNameRu = t != null ? t.name.ru() : e.bossTemplateId;
        server.warnRun(run, L10n.of(bossName + " defeated! (" + Mth.formatTime(now - e.pullTime) + ")",
                bossNameRu + " повержен! (" + Mth.formatTime(now - e.pullTime) + ")"), 0xFF40FF40);
        // checkpoint moves to this boss room
        for (Layout.Spawn sp : run.layout.spawns) {
            if (sp.boss() && killedIds.contains(sp.templateId())) {
                run.checkpoint = run.abs(run.layout.rooms.get(sp.room()).center());
                break;
            }
        }
        // remove leftover adds
        for (UnitState add : e.adds) if (add.isAlive()) server.npcs.despawn(add);
        // rewards (Mythic+ bosses don't drop loot: the end-of-run chest does)
        boolean keystone = run.difficulty == Difficulty.MYTHIC_PLUS;
        int ilvl = Rewards.bossItemLevel(run, bossIndex);
        String lockKey = run.def.id + ":" + run.difficulty.name();
        for (UUID m : run.members) {
            PlayerSession s = server.session(m);
            if (s == null || s.unit == null || !run.id.equals(s.unit.instanceId)) continue;
            PlayerProfile p = s.profile;
            boolean locked = false;
            if (run.def.isRaid()) {
                java.util.Set<String> killed = p.lockouts.computeIfAbsent(lockKey, k -> new java.util.LinkedHashSet<>());
                for (String id : killedIds) {
                    if (killed.contains(id)) locked = true;
                    killed.add(id);
                }
                if (!locked) p.vault.raidKills.add(ilvl);
            }
            p.stat("boss_kills", 1);
            Rewards.giveXp(server, s, Rewards.xpForKill(e.boss(), p.level) * 3);
            if (keystone) continue;
            if (locked) {
                server.msg(s, L10n.of("You already looted this boss this week.", "Вы уже получали добычу с этого босса на этой неделе."), 0xFFAAAAAA);
                continue;
            }
            double chance = run.def.isRaid() ? 0.3 : 0.35;
            double tierChance = run.def.isRaid() && run.difficulty != Difficulty.LFR ? 0.35 : 0.0;
            ItemData item = Rewards.rollBossLoot(server, s, run, ilvl, chance, tierChance);
            java.util.Map<Currency, Long> cur = new java.util.LinkedHashMap<>();
            cur.put(Currency.GOLD, 15_0000L);
            if (run.def.isRaid()) {
                cur.put(switch (run.difficulty) {
                    case LFR -> Currency.CREST_WEATHERED;
                    case RAID_NORMAL -> Currency.CREST_CARVED;
                    case RAID_HEROIC -> Currency.CREST_RUNED;
                    default -> Currency.CREST_GILDED;
                }, 15L);
                cur.put(Currency.VALORSTONES, 25L);
            }
            Rewards.giveCurrencies(server, s, cur, L10n.of(bossName, bossNameRu));
            if (item != null) Rewards.giveLoot(server, s, List.of(item), L10n.of(bossName, bossNameRu));
        }
        reviveAll(run, 0.5);
        if (run.mythic == null && run.allBossesDead() && run.state != InstanceRun.State.COMPLETED) {
            run.state = InstanceRun.State.COMPLETED;
            server.warnRun(run, L10n.of(run.def.name.en() + " completed!", run.def.name.ru() + " пройдено!"), 0xFFFFD040);
            for (UUID m : run.members) {
                PlayerSession s = server.session(m);
                if (s != null && s.unit != null && run.id.equals(s.unit.instanceId)) {
                    if (!run.def.isRaid()) Rewards.dungeonCompleted(server, s, run);
                    if (run.difficulty == Difficulty.MYTHIC) s.profile.vault.mythicRuns.add(0);
                    else if (!run.def.isRaid()) s.profile.vault.worldActivities++;
                }
            }
            for (UUID m : run.members) server.msg(m, L10n.of("Leave with /wow leave or the exit portal.", "Выйти: /wow leave или портал выхода."), 0xFFFFD040);
        }
        sendStatus(run);
    }

    // ------------------------------------------------------------------ tick

    void tick() {
        double now = server.engine.now();
        for (InstanceRun run : new ArrayList<>(runs.values())) {
            if (run.state == InstanceRun.State.CLOSING) continue;
            for (Encounter e : new ArrayList<>(run.encounters)) {
                e.tick();
                if (e.finished) run.encounters.remove(e);
            }
            if (run.mythic != null) tickMythic(run, now);
            // members who left the instance world by other means
            for (UUID m : new ArrayList<>(run.members)) {
                PlayerSession s = server.session(m);
                if (s == null || s.unit == null) continue;
                if (run.id.equals(s.unit.instanceId) && !run.worldKey.equals(s.unit.worldKey())) {
                    run.members.remove(m);
                    s.unit.instanceId = null;
                    s.instanceId = null;
                }
            }
            // empty instances close after a while
            boolean anyInside = false;
            for (UUID m : run.members) {
                PlayerSession s = server.session(m);
                if (s != null && s.unit != null && run.id.equals(s.unit.instanceId)) anyInside = true;
            }
            if (run.pvpMatchId == null && run.state != InstanceRun.State.BUILDING) {
                if (anyInside) run.emptySince = -1;
                else if (run.emptySince < 0) run.emptySince = now;
                else {
                    double limit = run.state == InstanceRun.State.COMPLETED ? 30 : 600;
                    if (run.members.isEmpty() && now - run.emptySince > Math.min(limit, 60) || now - run.emptySince > limit) close(run);
                }
            }
            if (now - run.lastStatus >= 1.0 && run.pvpMatchId == null) {
                run.lastStatus = now;
                sendStatus(run);
            }
        }
    }

    // ------------------------------------------------------------------ closing

    public void close(InstanceRun run) {
        if (run.state == InstanceRun.State.CLOSING) return;
        run.state = InstanceRun.State.CLOSING;
        for (Encounter e : run.encounters) if (!e.finished) e.end(false);
        run.encounters.clear();
        if (run.affixes != null) run.affixes.clear();
        for (UUID m : new ArrayList<>(run.members)) {
            PlayerSession s = server.session(m);
            if (s != null && s.unit != null && run.id.equals(s.unit.instanceId)) exit(s, run);
        }
        for (UnitState n : new ArrayList<>(run.npcs)) server.npcs.despawn(n);
        run.npcs.clear();
        Group g = server.groups.group(run.groupId);
        for (UnitState bot : new ArrayList<>(run.bots)) {
            bot.instanceId = null;
            if (g == null || !g.bots.contains(bot)) server.bots.despawn(bot);
        }
        run.bots.clear();
        server.platform.marker(run.worldKey, run.abs(run.layout.font), "font_of_power", false);
        server.platform.marker(run.worldKey, run.abs(run.layout.exit), "exit_portal", false);
        server.platform.clear(run.worldKey, run.origin, run.layout, () -> {
            server.platform.forceLoad(run.worldKey, run.origin, run.layout, false);
            usedSlots.clear(run.slot);
            runs.remove(run.id);
        });
    }

    public void closeAll() {
        for (InstanceRun run : new ArrayList<>(runs.values())) close(run);
    }

    /** Resets (closes) the group's instance if nobody is inside. */
    public void reset(PlayerSession s) {
        Group g = server.groups.groupOf(s.uuid);
        InstanceRun run = g != null ? runOfGroup(g) : null;
        if (run == null) {
            server.msg(s, L10n.of("Nothing to reset.", "Нечего сбрасывать."), 0xFFAAAAAA);
            return;
        }
        if (g != null && !g.leader.equals(s.uuid)) return;
        for (UUID m : run.members) {
            PlayerSession ms = server.session(m);
            if (ms != null && ms.unit != null && run.id.equals(ms.unit.instanceId)) {
                server.msg(s, L10n.of("Cannot reset: players are still inside.", "Нельзя сбросить: внутри еще есть игроки."), 0xFFFF4040);
                return;
            }
        }
        close(run);
        server.msg(s, L10n.of(run.def.name.en() + " has been reset.", run.def.name.ru() + ": сброшено."), 0xFFFFD040);
    }

    /** Creates a map for a PvP match (no enemies, PvpManager handles players). */
    InstanceRun createPvpMap(DungeonDef def, String matchId) {
        int slot = usedSlots.nextClearBit(0);
        if (slot >= MAX_SLOTS) return null;
        usedSlots.set(slot);
        Layout layout = layouts.computeIfAbsent(def.id, k -> PvpMaps.generate(def));
        Vec3 origin = new Vec3(BASE_X + (slot % 16) * SLOT_SPACING, BASE_Y, BASE_Z + (slot / 16) * SLOT_SPACING);
        InstanceRun run = new InstanceRun("p" + (nextRunId++), def, Difficulty.WORLD, layout, origin, server.platform.instanceWorld(), slot);
        run.createdAt = server.engine.now();
        run.pvpMatchId = matchId;
        runs.put(run.id, run);
        build(run);
        return run;
    }

    // ------------------------------------------------------------------ status

    void sendStatus(InstanceRun run) {
        S2C.InstanceStatus st = status(run);
        for (UUID m : run.members) {
            PlayerSession s = server.session(m);
            if (s != null && s.unit != null && run.id.equals(s.unit.instanceId)) server.send(s, st);
        }
    }

    S2C.InstanceStatus status(InstanceRun run) {
        double now = server.engine.now();
        S2C.InstanceStatus st = new S2C.InstanceStatus();
        st.active = true;
        st.dungeon = run.def.id;
        st.name = new S2C.Text(run.def.name.en(), run.def.name.ru());
        st.difficulty = run.difficulty.name();
        st.keyLevel = run.keyLevel();
        for (String bossId : run.def.bossIds()) {
            NpcTemplate t = NpcRegistry.get(bossId);
            st.bossNames.add(t != null ? new S2C.Text(t.name.en(), t.name.ru()) : new S2C.Text(bossId, bossId));
            st.bossKilled.add(run.bossesKilled.contains(bossId));
        }
        st.completed = run.state == InstanceRun.State.COMPLETED;
        Encounter e = run.activeEncounter();
        st.battleRes = e != null ? e.battleResCharges : -1;
        MythicRun m = run.mythic;
        if (m != null) {
            for (Affix a : m.affixes) st.affixes.add(a.name());
            st.timer = (float) m.timer;
            st.elapsed = (float) m.elapsed(now);
            st.running = m.started() && !m.completed;
            st.countdown = m.countdownEnd > 0 && !m.started() ? (float) Math.max(0, m.countdownEnd - now) : 0;
            st.deaths = m.deaths;
            st.deathPenalty = (float) m.deathPenalty;
            st.forces = Math.round(m.forcesPercent() * 100) / 100.0;
            st.upgrades = m.completed ? (m.timed() ? MythicScore.upgrades(m.elapsed(now), m.timer) : 0) : MythicScore.upgrades(m.elapsed(now), m.timer);
        }
        return st;
    }
}
