package com.wowcraft.core.game;

import com.wowcraft.core.bot.BotBrain;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.item.Currency;
import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end simulations of the game server with a fake Minecraft platform and bots playing. */
class GameServerTest {
    FakePlatform platform;
    GameServer server;
    final Map<UUID, BotBrain> autopilot = new HashMap<>();
    private PrintStream originalErr;
    private ByteArrayOutputStream errors;

    @BeforeEach
    void setUp() {
        originalErr = System.err;
        errors = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errors, true));
        platform = new FakePlatform();
        Config cfg = new Config();
        server = new GameServer(platform, cfg, new WorldData());
        platform.server = server;
    }

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
        String err = errors.toString();
        if (!err.isBlank()) System.err.println(err);
        assertFalse(err.contains("Exception"), "Exceptions were printed during the simulation:\n" + err);
    }

    PlayerSession join(String name, Spec spec) {
        UUID id = UUID.randomUUID();
        UnitState u = platform.addPlayer(id, name, new Vec3(0, 64, 0));
        PlayerSession s = server.join(id, name, u, new PlayerProfile(), "en_us");
        server.chooseClass(s, spec);
        return s;
    }

    /** Lets a bot brain play the player's character. */
    void autoplay(PlayerSession s) {
        autopilot.put(s.uuid, new BotBrain(s.unit));
    }

    void tick(double seconds) {
        int n = (int) Math.round(seconds / GameServer.TICK);
        for (int i = 0; i < n; i++) step();
    }

    void step() {
        platform.moveUnits();
        for (var en : autopilot.entrySet()) {
            PlayerSession s = server.session(en.getKey());
            if (s == null || s.unit == null) continue;
            if (en.getValue().unit != s.unit) en.setValue(new BotBrain(s.unit));
            if (s.ghost) server.instances.release(s);
            en.getValue().tick(server);
        }
        server.tick();
    }

    List<UnitState> party(PlayerSession leader) {
        return server.groups.unitsOfGroup(leader.unit);
    }

    void moveParty(List<UnitState> party, Vec3 to) {
        int i = 0;
        for (UnitState u : party) {
            Vec3 p = to.add(Vec3.fromYaw(i * 70).mul(1.5));
            if (u.isPlayer()) platform.teleport(u.uuid, ((FakeBody) u.body).world, p, 0);
            else u.body.teleport(p);
            for (UnitState pet : u.livingPets()) pet.body.teleport(p.add(1, 0, 0));
            i++;
        }
    }

    /** Clears every pack and boss of a run in layout order. Returns the number of wipes. */
    int clearRun(InstanceRun run, PlayerSession leader, double maxPerPack) {
        Map<String, List<Layout.Spawn>> packs = new LinkedHashMap<>();
        for (Layout.Spawn sp : run.layout.spawns) packs.computeIfAbsent(sp.packId(), k -> new ArrayList<>()).add(sp);
        int wipes = 0;
        for (var en : packs.entrySet()) {
            Vec3 center = run.abs(en.getValue().get(0).pos());
            Vec3 stand = center.add(new Vec3(4, 0, 4));
            for (int attempt = 0; attempt < 3; attempt++) {
                moveParty(party(leader), stand);
                double t = 0;
                boolean done = false;
                while (t < maxPerPack) {
                    tick(0.5);
                    t += 0.5;
                    if (packDead(run, en.getKey()) && !anyNpcInCombat(run)) {
                        done = true;
                        break;
                    }
                    if (partyDead(leader)) break;
                }
                if (done) break;
                wipes++;
                tick(8); // wipe recovery (auto revive at checkpoint)
                for (UnitState u : party(leader)) if (u.isDead()) server.engine.resurrect(u, u, 1.0);
            }
            assertTrue(packDead(run, en.getKey()), "Pack " + en.getKey() + " not cleared in " + run.def.id + " " + describe(run, en.getKey(), leader));
            tick(3); // out of combat regen
            for (UnitState u : party(leader)) {
                if (u.isAlive() && !u.inCombat()) server.engine.rawHeal(u, u, u.maxHealth());
            }
        }
        return wipes;
    }

    boolean packDead(InstanceRun run, String packId) {
        for (UnitState n : run.npcs) if (packId.equals(n.packId) && n.isAlive()) return false;
        return true;
    }

    boolean anyNpcInCombat(InstanceRun run) {
        for (UnitState n : run.npcs) if (n.isAlive() && n.inCombat()) return true;
        return false;
    }

    boolean partyDead(PlayerSession leader) {
        for (UnitState u : party(leader)) if (u.isAlive()) return false;
        return true;
    }

    String describe(InstanceRun run, String packId, PlayerSession leader) {
        StringBuilder sb = new StringBuilder();
        for (UnitState n : run.npcs) {
            if (packId.equals(n.packId)) sb.append(n.name).append(' ').append(Math.round(n.healthFraction() * 100)).append("% ");
        }
        sb.append("| party: ");
        for (UnitState u : party(leader)) sb.append(u.name).append(' ').append(Math.round(u.healthFraction() * 100)).append("% ");
        return sb.toString();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void newCharacterGetsGearKeystoneAndCharacterSync() {
        PlayerSession s = join("Alice", Spec.FIRE);
        tick(1);
        assertEquals(80, s.profile.level);
        assertNotNull(s.profile.keystone);
        assertNotNull(platform.readEquipment(s.uuid).get(EquipSlot.MAIN_HAND));
        assertNotNull(s.profile.extraSlots.get(EquipSlot.TRINKET_1.name()) == null ? s.profile.extraSlots.get(EquipSlot.NECK.name()) : "ok");
        assertTrue(server.equipment(s).averageItemLevel() > 100, "starter item level");
        assertFalse(platform.sent(s.uuid, S2C.Character.class).isEmpty());
        assertFalse(platform.sent(s.uuid, S2C.Self.class).isEmpty());
        assertTrue(s.unit.knownAbilities.contains("fireball"));
        assertTrue(s.unit.maxHealth() > 1000);
    }

    @Test
    void commandsWork() {
        PlayerSession s = join("Bob", Spec.ARMS);
        List<L10n> out = server.command(s.uuid, "help", false);
        assertTrue(out.size() > 5);
        assertFalse(server.command(s.uuid, "dungeons", false).isEmpty());
        server.command(s.uuid, "class mage frost", false);
        assertEquals(Spec.FROST_MAGE, s.profile.spec());
        server.command(s.uuid, "bot healer", false);
        assertEquals(2, server.groups.groupOf(s.uuid).size());
        server.command(s.uuid, "gear 150", true);
        assertTrue(server.equipment(s).averageItemLevel() >= 149);
    }

    @Test
    void normalDungeonWithFollowerBots() {
        PlayerSession s = join("Tank", Spec.PROTECTION_WARRIOR);
        autoplay(s);
        server.lfg.fillWithBots(s);
        Group g = server.groups.groupOf(s.uuid);
        assertEquals(5, g.size());
        DungeonDef def = Dungeons.get("grimhold_depths");
        server.instances.requestEnter(s, def, Difficulty.NORMAL);
        tick(1);
        InstanceRun run = server.instances.runOf(s.unit);
        assertNotNull(run, "player should be inside the instance");
        assertEquals(InstanceRun.State.READY, run.state);
        assertFalse(run.npcs.isEmpty());
        for (UnitState bot : g.bots) assertEquals(run.id, bot.instanceId);
        long valorBefore = s.profile.currency(Currency.VALORSTONES);
        int wipes = clearRun(run, s, 240);
        tick(2);
        assertEquals(def.bossIds().size(), run.bossesKilled.size(), "all bosses killed");
        assertEquals(InstanceRun.State.COMPLETED, run.state);
        assertTrue(s.profile.currency(Currency.VALORSTONES) > valorBefore, "completion currency");
        System.out.println("Normal " + def.id + " cleared with " + wipes + " wipes in " + Math.round(server.engine.now()) + "s");
        server.instances.leave(s);
        assertNull(s.unit.instanceId);
        assertEquals(platform.defaultWorld(), s.unit.worldKey());
    }

    @Test
    void mythicPlusKeystoneRun() {
        PlayerSession s = join("Healer", Spec.RESTORATION_DRUID);
        autoplay(s);
        server.command(s.uuid, "gear 145", true);
        server.lfg.fillWithBots(s);
        s.profile.keystone = new PlayerProfile.Keystone("tidewrack_sanctum", 4);
        DungeonDef def = Dungeons.get("tidewrack_sanctum");
        server.instances.requestEnter(s, def, Difficulty.MYTHIC_PLUS);
        tick(1);
        InstanceRun run = server.instances.runOf(s.unit);
        assertNotNull(run);
        assertNotNull(run.mythic);
        assertTrue(run.npcs.isEmpty(), "enemies spawn when the key starts");
        server.instances.startKey(s);
        assertFalse(run.npcs.isEmpty());
        assertEquals(3, s.profile.keystone.level, "key depletes on start");
        tick(11);
        assertTrue(run.mythic.started());
        clearRun(run, s, 240);
        tick(2);
        assertTrue(run.mythic.completed, "keystone completed, forces " + run.mythic.forcesPercent());
        assertTrue(s.profile.mythicBest.containsKey(def.id));
        assertTrue(s.profile.vault.mythicRuns.contains(4));
        assertTrue(server.mythicRating(s.profile) > 0);
        assertFalse(server.worldData().leaderboard.get(def.id).isEmpty());
        System.out.println("M+4 " + def.id + ": " + Math.round(run.mythic.elapsed(server.engine.now())) + "s / " + run.mythic.timer + ", deaths "
                + run.mythic.deaths + ", new key +" + s.profile.keystone.level + ", rating " + server.mythicRating(s.profile));
    }

    @Test
    void arenaSkirmishAgainstBots() {
        PlayerSession s = join("Gladiator", Spec.ASSASSINATION);
        autoplay(s);
        server.command(s.uuid, "gear 140", true);
        server.pvp.queue(s, "3v3", false);
        tick(12);
        PvpManager.Match m = server.pvp.matchOfSession(s);
        assertNotNull(m, "match formed with bots");
        assertEquals(PvpManager.State.PREP, m.state);
        assertEquals(3, server.pvp.unitsOf(m, m.teams[0]).size());
        assertEquals(3, server.pvp.unitsOf(m, m.teams[1]).size());
        tick(21);
        assertEquals(PvpManager.State.ACTIVE, m.state);
        double t = 0;
        while (m.state == PvpManager.State.ACTIVE && t < 600) {
            tick(1);
            t++;
        }
        assertEquals(PvpManager.State.ENDED, m.state, "arena should end");
        System.out.println("Arena ended after " + t + "s, winner team " + m.winner + ", dampening " + m.dampening);
        tick(10);
        assertNull(server.pvp.matchOfSession(s));
        assertEquals("players", s.unit.team);
        assertTrue(s.profile.currency(Currency.HONOR) > 0);
    }

    @Test
    void captureTheFlagBattleground() {
        PlayerSession s = join("Flagger", Spec.HAVOC);
        autoplay(s);
        server.command(s.uuid, "gear 140", true);
        server.pvp.queue(s, "ctf", false);
        tick(12);
        PvpManager.Match m = server.pvp.matchOfSession(s);
        assertNotNull(m);
        tick(26);
        assertEquals(PvpManager.State.ACTIVE, m.state);
        double t = 0;
        while (m.state == PvpManager.State.ACTIVE && t < 16 * 60) {
            tick(1);
            t++;
        }
        assertEquals(PvpManager.State.ENDED, m.state);
        System.out.println("CTF ended after " + t + "s: " + m.teams[0].score + " - " + m.teams[1].score);
    }

    @Test
    void dominationBattleground() {
        PlayerSession s = join("Node", Spec.RETRIBUTION);
        autoplay(s);
        server.pvp.queue(s, "domination", false);
        tick(12);
        PvpManager.Match m = server.pvp.matchOfSession(s);
        assertNotNull(m);
        tick(26);
        double t = 0;
        while (m.state == PvpManager.State.ACTIVE && t < 16 * 60) {
            tick(1);
            t++;
        }
        assertEquals(PvpManager.State.ENDED, m.state);
        System.out.println("Domination ended after " + t + "s: " + m.teams[0].score + " - " + m.teams[1].score);
    }

    @Test
    void raidWithBots() {
        PlayerSession s = join("Raider", Spec.HOLY_PALADIN);
        autoplay(s);
        server.command(s.uuid, "gear 140", true);
        server.groups.convertToRaid(s);
        server.lfg.fillWithBots(s);
        Group g = server.groups.groupOf(s.uuid);
        assertEquals(10, g.size());
        DungeonDef def = Dungeons.get("throne_of_the_shattered_star");
        server.instances.requestEnter(s, def, Difficulty.RAID_NORMAL);
        tick(1);
        InstanceRun run = server.instances.runOf(s.unit);
        assertNotNull(run);
        int wipes = clearRun(run, s, 420);
        tick(2);
        assertEquals(def.bossIds().size(), run.bossesKilled.size());
        assertFalse(s.profile.lockouts.isEmpty());
        assertFalse(s.profile.vault.raidKills.isEmpty());
        System.out.println("Raid cleared with " + wipes + " wipes in " + Math.round(server.engine.now()) + "s");
    }

    @Test
    void groupInviteAndDungeonFinderQueue() {
        PlayerSession a = join("Ann", Spec.BLOOD);
        PlayerSession b = join("Ben", Spec.DISCIPLINE);
        server.groups.invite(a, "Ben");
        server.groups.accept(b);
        assertEquals(2, server.groups.groupOf(a.uuid).size());
        server.lfg.queue(a, "random", "heroic", null, false);
        tick(LfgManager.FOLLOWER_WAIT + 2);
        Group g = server.groups.groupOf(a.uuid);
        assertEquals(5, g.size(), "followers fill the group");
        assertNotNull(server.instances.runOf(a.unit), "group entered the dungeon");
        assertNotNull(server.instances.runOf(b.unit));
    }
}
