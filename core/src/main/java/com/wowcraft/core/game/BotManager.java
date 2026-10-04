package com.wowcraft.core.game;

import com.wowcraft.core.bot.BotBrain;
import com.wowcraft.core.combat.MoveIntent;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.npc.Encounter;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.npc.Telegraph;
import com.wowcraft.core.player.CharacterBuilder;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-like bots: follower-dungeon companions, raid fillers, arena and battleground players.
 * Each bot is a full character (class, spec, talents, generated gear) driven by {@link BotBrain}.
 */
public final class BotManager {
    private final GameServer server;
    private final Map<Integer, BotBrain> brains = new LinkedHashMap<>();
    /** bot unit id -> owning group id or PvP match id. */
    private final Map<Integer, String> owners = new LinkedHashMap<>();
    private int nameIndex;

    private static final String[] NAMES = {"Aldric", "Brenna", "Caelith", "Darvos", "Elowen", "Fenrik", "Galora", "Hadrik", "Isolde", "Jorvan",
            "Kaelen", "Lyria", "Morgath", "Nerys", "Orrin", "Perrin", "Quilla", "Rhogar", "Sylvara", "Thane", "Ulric", "Vaela", "Wystan", "Xandra",
            "Yorick", "Zephyra", "Aurelia", "Borin", "Cassia", "Dorn", "Eira", "Faelan", "Grimm", "Helka", "Ivar", "Junia", "Korrak", "Liora",
            "Malric", "Nima", "Oskar", "Petra", "Ragna", "Soren", "Tilda", "Ursa", "Varric", "Wren", "Ysolde", "Zarek"};

    BotManager(GameServer server) {
        this.server = server;
        registerTemplates();
    }

    private static void registerTemplates() {
        for (WowClass c : WowClass.values()) {
            String id = "bot_" + c.id();
            if (NpcRegistry.get(id) != null) continue;
            NpcTemplate t = new NpcTemplate(id, c.name.en(), c.name.ru(), com.wowcraft.core.npc.NpcRank.NORMAL).texture("bot_" + c.id()).tint(c.color);
            t.model("wowcraft:bot/" + c.id(), "wowcraft:player");
            NpcRegistry.register(t);
        }
    }

    public boolean isBot(UnitState u) {
        return u != null && brains.containsKey(u.id);
    }

    public BotBrain brain(UnitState u) {
        return brains.get(u.id);
    }

    // ------------------------------------------------------------------ creation

    /** Spawns a fully geared bot. */
    public UnitState spawn(Spec spec, int itemLevel, String worldKey, Vec3 pos, float yaw, String team, String owner, String instanceId) {
        NpcTemplate t = NpcRegistry.get("bot_" + spec.wowClass.id());
        String name = NAMES[(nameIndex++) % NAMES.length];
        UnitState u = server.platform.createNpcBody(worldKey, pos, yaw, t, UnitKind.BOT, name);
        if (u == null) return null;
        u.name = name;
        u.templateId = t.id;
        u.team = team;
        u.instanceId = instanceId;
        Equipment eq = Rewards.fullSet(spec, itemLevel, server.rng, "bot", itemLevel >= 130);
        int level = com.wowcraft.core.combat.Formulas.MAX_LEVEL;
        CharacterBuilder.apply(server.engine, u, spec, level, CharacterBuilder.defaultTalents(spec, level), eq);
        u.tags.put("bot_ilvl", Math.round(eq.averageItemLevel()));
        server.engine.resetUnit(u, false);
        BotBrain brain = new BotBrain(u);
        u.brain = brain;
        brains.put(u.id, brain);
        owners.put(u.id, owner);
        ensurePet(u);
        return u;
    }

    void ensurePet(UnitState bot) {
        if (bot.spec == null || bot.isDead() || bot.hasLivingPet()) return;
        ClassKit kit = Content.kit(bot.spec.wowClass);
        String petId = kit.defaultPet.get(bot.spec);
        if (petId != null) server.engine.summon(bot, petId, 0, 0);
    }

    /** Adds a bot to a group (party/raid fill). Spec chosen for the requested role, avoiding duplicates. */
    public UnitState addToGroup(Group g, Role role, Spec preferred, int itemLevel) {
        UnitState anchor = anchorOf(g);
        if (anchor == null) return null;
        Spec spec = preferred != null ? preferred : pickSpec(role, g);
        Vec3 pos = anchor.position().add(server.rng.range(-2, 2), 0, server.rng.range(-2, 2));
        UnitState bot = spawn(spec, itemLevel, anchor.worldKey(), pos, anchor.yaw(), anchor.team, g.id, anchor.instanceId);
        if (bot == null) return null;
        bot.groupId = g.id;
        for (UnitState pet : bot.pets()) pet.groupId = g.id;
        g.bots.add(bot);
        InstanceRun run = server.instances.runOfGroup(g);
        if (run != null && !run.bots.contains(bot)) {
            run.bots.add(bot);
            bot.instanceId = run.id;
        }
        server.groups.sendGroupToAll(g);
        return bot;
    }

    private UnitState anchorOf(Group g) {
        PlayerSession leader = server.session(g.leader);
        if (leader != null && leader.unit != null) return leader.unit;
        for (UUID m : g.members) {
            PlayerSession s = server.session(m);
            if (s != null && s.unit != null) return s.unit;
        }
        return null;
    }

    /** A spec for a role, preferring classes not yet in the group (buff/utility variety like real groups). */
    Spec pickSpec(Role role, Group g) {
        List<WowClass> present = new ArrayList<>();
        if (g != null) {
            for (UnitState u : server.groups.unitsOfGroupId(g)) if (u.wowClass != null) present.add(u.wowClass);
        }
        List<Spec> options = new ArrayList<>();
        for (Spec s : Spec.values()) {
            boolean roleOk = role == null || (role == Role.MELEE_DPS || role == Role.RANGED_DPS ? s.role == Role.MELEE_DPS || s.role == Role.RANGED_DPS : s.role == role);
            if (roleOk && !present.contains(s.wowClass)) options.add(s);
        }
        if (options.isEmpty()) {
            for (Spec s : Spec.values()) {
                boolean roleOk = role == null || (role == Role.MELEE_DPS || role == Role.RANGED_DPS ? s.role == Role.MELEE_DPS || s.role == Role.RANGED_DPS : s.role == role);
                if (roleOk) options.add(s);
            }
        }
        return options.get(server.rng.nextInt(options.size()));
    }

    public void removeBot(Group g, UnitState bot) {
        if (g != null) g.bots.remove(bot);
        InstanceRun run = server.instances.runOf(bot);
        if (run != null) run.bots.remove(bot);
        despawn(bot);
        if (g != null) server.groups.sendGroupToAll(g);
    }

    public void despawn(UnitState bot) {
        brains.remove(bot.id);
        owners.remove(bot.id);
        for (UnitState pet : new ArrayList<>(bot.pets())) server.npcs.despawn(pet);
        server.platform.removeBody(bot);
        server.engine.remove(bot);
    }

    public void onUnitRemoved(UnitState u) {
        if (brains.remove(u.id) != null) {
            owners.remove(u.id);
            for (Group g : server.groups.all()) g.bots.remove(u);
        }
    }

    // ------------------------------------------------------------------ tick

    void tick() {
        double now = server.engine.now();
        for (BotBrain b : new ArrayList<>(brains.values())) {
            UnitState u = b.unit;
            if (server.engine.unit(u.id) == null || (u.body != null && u.body.isRemoved())) {
                brains.remove(u.id);
                owners.remove(u.id);
                for (Group g : server.groups.all()) g.bots.remove(u);
                continue;
            }
            try {
                b.tick(server);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            // pets and out of combat recovery
            if (!u.isDead() && !u.inCombat() && now - u.lastCombatAtPublic() > 4) {
                if (((long) (now * 20)) % 40 == u.id % 40) ensurePet(u);
                if (u.healthFraction() < 1 && server.pvp.matchOf(u) == null) server.engine.rawHeal(u, u, u.maxHealth() * 0.04);
            }
            // world bots follow their leader across worlds
            UnitState leader = leader(u);
            if (leader != null && !server.engine.sameWorld(u, leader) && u.instanceId == null && leader.instanceId == null && u.body != null) {
                server.platform.moveBody(u, leader.worldKey(), leader.position());
            }
            if (leader != null && u.body != null && server.engine.sameWorld(u, leader) && !u.inCombat()
                    && u.position().distance(leader.position()) > 40) {
                u.body.teleport(leader.position().add(server.rng.range(-1.5, 1.5), 0, server.rng.range(-1.5, 1.5)));
            }
        }
    }

    // ------------------------------------------------------------------ BotContext data

    List<UnitState> allies(UnitState bot) {
        List<UnitState> out = new ArrayList<>();
        PvpManager.Match m = server.pvp.matchOf(bot);
        if (m != null) {
            out.addAll(server.pvp.teamUnits(m, bot));
        } else {
            Group g = server.groups.groupOfUnit(bot);
            if (g != null) out.addAll(server.groups.unitsOfGroupId(g));
            else out.add(bot);
        }
        List<UnitState> withPets = new ArrayList<>(out);
        for (UnitState u : out) withPets.addAll(u.livingPets());
        return withPets;
    }

    /** Allies of any player-like unit (used for UI suggestions). */
    List<UnitState> alliesOf(UnitState u) {
        return allies(u);
    }

    List<UnitState> enemies(UnitState bot) {
        PvpManager.Match m = server.pvp.matchOf(bot);
        if (m != null) return server.pvp.enemyUnits(m, bot);
        List<UnitState> allies = allies(bot);
        List<UnitState> out = new ArrayList<>();
        for (UnitState u : server.engine.units()) {
            if (u.isDead() || !u.isNpcLike() && u.kind != UnitKind.PET) continue;
            if (!server.engine.sameWorld(u, bot) || u.position().distance(bot.position()) > 45) continue;
            if (!server.engine.isHostile(bot, u)) continue;
            boolean engaged = false;
            if (u.threat() != null) {
                for (UnitState a : allies) {
                    if (u.threat().get(a) > 0) {
                        engaged = true;
                        break;
                    }
                }
            }
            if (!engaged && u.owner != null && allies.contains(u.owner.target())) engaged = false;
            if (engaged) out.add(u);
        }
        return out;
    }

    UnitState leader(UnitState bot) {
        if (server.pvp.matchOf(bot) != null) return null;
        Group g = server.groups.groupOfUnit(bot);
        if (g == null) return null;
        PlayerSession s = server.session(g.leader);
        if (s != null && s.unit != null && !s.ghost) return s.unit;
        for (UUID m : g.members) {
            PlayerSession o = server.session(m);
            if (o != null && o.unit != null && !o.ghost) return o.unit;
        }
        return null;
    }

    List<Telegraph> telegraphs(UnitState bot) {
        List<Telegraph> out = new ArrayList<>();
        InstanceRun run = server.instances.runOf(bot);
        if (run != null) {
            for (Encounter e : run.encounters) if (e.active) out.addAll(e.telegraphs);
            if (run.affixes != null) out.addAll(run.affixes.telegraphs());
        } else {
            for (Encounter e : server.worldEncounters) if (e.active) out.addAll(e.telegraphs);
        }
        return out;
    }

    int formationIndex(UnitState bot) {
        Group g = server.groups.groupOfUnit(bot);
        if (g != null) return g.bots.indexOf(bot) + 1;
        PvpManager.Match m = server.pvp.matchOf(bot);
        if (m != null) return server.pvp.teamUnits(m, bot).indexOf(bot);
        return 0;
    }

    /** Resurrects dead bots of an instance (after a wipe or on release). */
    void reviveAll(List<UnitState> bots, Vec3 at) {
        for (UnitState b : bots) {
            if (b.isDead()) {
                if (b.body != null && at != null) b.body.teleport(at.add(server.rng.range(-1.5, 1.5), 0, server.rng.range(-1.5, 1.5)));
                server.engine.resurrect(b, b, 1.0);
            }
        }
    }

    L10n describe(UnitState bot) {
        return L10n.of(bot.name + " (" + bot.spec.name.en() + " " + bot.wowClass.name.en() + ")",
                bot.name + " (" + bot.wowClass.name.ru() + ", " + bot.spec.name.ru() + ")");
    }
}
