package com.wowcraft.core.game;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.bot.BotContext;
import com.wowcraft.core.bot.RotationRunner;
import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.CastState;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.CombatListener;
import com.wowcraft.core.combat.Cooldowns;
import com.wowcraft.core.combat.Formulas;
import com.wowcraft.core.combat.GroundArea;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.combat.WorldAccess;
import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.mythic.MythicScore;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.BossScript;
import com.wowcraft.core.npc.BossScripts;
import com.wowcraft.core.npc.Encounter;
import com.wowcraft.core.npc.EncounterHost;
import com.wowcraft.core.npc.NpcBrain;
import com.wowcraft.core.npc.NpcManager;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.npc.Telegraph;
import com.wowcraft.core.player.CharacterBuilder;
import com.wowcraft.core.player.GreatVault;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.resource.ResourcePool;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.time.WeeklyReset;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Rng;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The WoW game server: owns the combat engine and every gameplay system. The Minecraft adapter feeds it
 * players, input and ticks; it answers with messages and platform calls.
 */
public final class GameServer implements CombatListener, EncounterHost, NpcManager.Listener, BotContext, WorldAccess {
    public static final double TICK = 0.05;

    final Platform platform;
    final Config config;
    final CombatEngine engine;
    final WorldData world;
    final Map<UUID, PlayerSession> sessions = new LinkedHashMap<>();
    final NpcManager npcs;
    final InstanceManager instances;
    final GroupManager groups;
    final LfgManager lfg;
    final PvpManager pvp;
    final BotManager bots;
    final Commands commands;
    final WeeklyReset reset = WeeklyReset.standard();
    final Rng rng = new Rng();
    final List<Encounter> worldEncounters = new ArrayList<>();
    private final Map<UUID, S2C.CombatTextBatch> pendingText = new HashMap<>();
    long week;
    private double nextWeekCheck;
    private long tickCount;

    public GameServer(Platform platform, Config config, WorldData world) {
        Content.bootstrap();
        this.platform = platform;
        this.config = config;
        this.world = world;
        this.engine = new CombatEngine(System.nanoTime());
        this.engine.setWorld(this);
        this.engine.addListener(this);
        this.engine.setCastFilter(this::castFilter);
        this.npcs = new NpcManager(engine, platform, this);
        this.groups = new GroupManager(this);
        this.instances = new InstanceManager(this);
        this.lfg = new LfgManager(this);
        this.pvp = new PvpManager(this);
        this.bots = new BotManager(this);
        this.commands = new Commands(this);
        this.week = reset.currentWeek();
    }

    // ================================================================== accessors

    public CombatEngine engine() {
        return engine;
    }

    public Platform platform() {
        return platform;
    }

    public Config config() {
        return config;
    }

    public WorldData worldData() {
        return world;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public InstanceManager instances() {
        return instances;
    }

    public GroupManager groups() {
        return groups;
    }

    public PvpManager pvp() {
        return pvp;
    }

    public LfgManager lfg() {
        return lfg;
    }

    public BotManager bots() {
        return bots;
    }

    public long week() {
        return week;
    }

    public PlayerSession session(UUID uuid) {
        return sessions.get(uuid);
    }

    public Collection<PlayerSession> sessions() {
        return sessions.values();
    }

    public PlayerSession sessionByName(String name) {
        for (PlayerSession s : sessions.values()) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    public PlayerSession sessionOf(UnitState u) {
        if (u == null || !u.isPlayer()) return null;
        return sessions.get(u.uuid);
    }

    // ================================================================== lifecycle

    /** A player entity joined (or respawned). Profile is loaded / created by the adapter. */
    public PlayerSession join(UUID uuid, String name, UnitState unit, PlayerProfile profile, String language) {
        PlayerSession s = sessions.get(uuid);
        if (s == null) {
            s = new PlayerSession(uuid, name, profile);
            sessions.put(uuid, s);
        }
        s.name = name;
        s.unit = unit;
        s.lang = L10n.Lang.fromCode(language);
        profile.uuid = uuid.toString();
        profile.name = name;
        world.names.put(uuid.toString(), name);
        unit.team = "players";
        unit.groupId = groups.groupIdOf(uuid);
        weeklyCheck(s);
        applyCharacter(s);
        s.characterDirty = true;
        instances.onJoin(s);
        groups.sendGroup(uuid);
        return s;
    }

    /** The player's entity changed (respawn): rebind the unit. */
    public void rebind(UUID uuid, UnitState unit) {
        PlayerSession s = sessions.get(uuid);
        if (s == null) return;
        UnitState old = s.unit;
        s.unit = unit;
        unit.team = old != null ? old.team : "players";
        unit.groupId = old != null ? old.groupId : groups.groupIdOf(uuid);
        unit.instanceId = old != null ? old.instanceId : null;
        if (old != null) {
            for (UnitState pet : new ArrayList<>(old.pets())) {
                pet.owner = unit;
                unit.pets().add(pet);
            }
            if (old != unit) engine.remove(old);
        }
        s.ghost = false;
        applyCharacter(s);
        engine.resetUnit(unit, false);
        s.characterDirty = true;
    }

    public void leave(UUID uuid) {
        PlayerSession s = sessions.remove(uuid);
        if (s == null) return;
        lfg.onLeave(uuid);
        pvp.onLeave(uuid);
        if (s.unit != null) {
            for (UnitState pet : new ArrayList<>(s.unit.pets())) npcs.despawn(pet);
            engine.remove(s.unit);
        }
        groups.onOffline(uuid);
    }

    public void shutdown() {
        instances.closeAll();
    }

    // ================================================================== character

    public void applyCharacter(PlayerSession s) {
        PlayerProfile p = s.profile;
        Spec spec = p.spec();
        UnitState u = s.unit;
        if (u == null) return;
        Equipment eq = platform.readEquipment(s.uuid);
        if (spec == null) {
            u.wowClass = null;
            u.spec = null;
            u.level = p.level;
            u.baseStats.clear();
            u.baseStats.set(com.wowcraft.core.stat.Stat.STAMINA, Formulas.baseStamina(p.level));
            u.baseStats.set(com.wowcraft.core.stat.Stat.STRENGTH, Formulas.basePrimary(p.level));
            u.gearStats.clear();
            if (eq != null) u.gearStats.add(eq.totalStats(Spec.ARMS));
            u.knownAbilities.clear();
            u.invalidateMods();
            u.stats();
        } else {
            CharacterBuilder.apply(engine, u, spec, p.level, p.talentsFor(spec), eq);
        }
        s.equipmentHash = equipmentHash(eq);
        s.characterDirty = true;
        ensurePet(s);
    }

    void ensurePet(PlayerSession s) {
        UnitState u = s.unit;
        if (u == null || u.spec == null || u.isDead()) return;
        ClassKit kit = Content.kit(u.spec.wowClass);
        String petId = kit.defaultPet.get(u.spec);
        // dismiss pets that don't fit the spec anymore
        for (UnitState pet : new ArrayList<>(u.pets())) {
            if (pet.despawnAt == Double.POSITIVE_INFINITY && pet.kind == UnitKind.PET && (petId == null || !petId.equals(pet.templateId))) {
                if (!isClassPet(pet.templateId)) continue;
                npcs.despawn(pet);
                u.pets().remove(pet);
            }
        }
        if (petId == null || u.hasLivingPet()) return;
        engine.summon(u, petId, 0, 0);
    }

    private static boolean isClassPet(String id) {
        return id != null && (id.equals("hunter_pet") || id.equals("ghoul") || id.equals("imp") || id.equals("felhunter") || id.equals("felguard")
                || id.equals("voidwalker"));
    }

    static long equipmentHash(Equipment eq) {
        if (eq == null) return 0;
        long h = 17;
        for (var en : eq.all().entrySet()) {
            h = h * 31 + en.getKey().ordinal();
            h = h * 31 + en.getValue().ilvl;
            h = h * 31 + (en.getValue().seed ^ (en.getValue().seed >>> 32));
            h = h * 31 + en.getValue().gems.hashCode();
        }
        return h;
    }

    public void chooseClass(PlayerSession s, Spec spec) {
        if (s.unit != null && s.unit.inCombat()) {
            msg(s, L10n.of("You can't change class in combat.", "Нельзя сменить класс в бою."), 0xFFFF4040);
            return;
        }
        PlayerProfile p = s.profile;
        boolean classChanged = p.wowClass() != spec.wowClass;
        if (classChanged && p.classChosen && !config.freeClassChange) {
            msg(s, L10n.of("Class changes are disabled on this server.", "Смена класса отключена на этом сервере."), 0xFFFF4040);
            return;
        }
        p.wowClass = spec.wowClass.id();
        p.spec = spec.id();
        if (!p.classChosen) {
            p.classChosen = true;
            if (config.startAtMaxLevel) p.level = Formulas.MAX_LEVEL;
            if (p.talentsFor(spec).isEmpty()) p.talents.put(spec.id(), new ArrayList<>(CharacterBuilder.defaultTalents(spec, p.level)));
            Rewards.starterKit(this, s);
        } else if (classChanged) {
            Rewards.starterGearForSpec(this, s, spec);
        }
        if (p.talentsFor(spec).isEmpty()) p.talents.put(spec.id(), new ArrayList<>(CharacterBuilder.defaultTalents(spec, p.level)));
        applyCharacter(s);
        engine.resetUnit(s.unit, false);
        msg(s, L10n.of("You are now " + spec.name.en() + " " + spec.wowClass.name.en() + ".",
                "Теперь вы: " + spec.wowClass.name.ru() + " (" + spec.name.ru() + ")."), spec.wowClass.color);
        groups.sendGroupToAll(s.uuid);
    }

    // ================================================================== tick

    public void tick() {
        tickCount++;
        engine.tick(TICK);
        npcs.tick();
        bots.tick();
        double now = engine.now();
        for (Encounter e : new ArrayList<>(worldEncounters)) {
            e.tick();
            if (e.finished) worldEncounters.remove(e);
        }
        instances.tick();
        pvp.tick();
        groups.tick();
        lfg.tick();
        if (now >= nextWeekCheck) {
            nextWeekCheck = now + 60;
            long w = reset.currentWeek();
            if (w != week) {
                week = w;
                for (PlayerSession s : sessions.values()) weeklyCheck(s);
            }
        }
        for (PlayerSession s : sessions.values()) {
            if (s.unit == null) continue;
            if (now - s.lastEquipmentCheck > 1.0) {
                s.lastEquipmentCheck = now;
                Equipment eq = platform.readEquipment(s.uuid);
                long h = equipmentHash(eq);
                if (h != s.equipmentHash) {
                    applyCharacter(s);
                }
                if (s.unit.spec != null && !s.unit.isDead() && !s.unit.hasLivingPet() && now - s.unit.lastCombatAtPublic() > 3) ensurePet(s);
            }
            Sync.syncPlayer(this, s, now);
        }
        flushCombatText();
    }

    void weeklyCheck(PlayerSession s) {
        PlayerProfile p = s.profile;
        if (p.vault.week != week) GreatVault.rollRewards(p, p.spec(), week, rng);
        if (p.lockoutWeek != week) {
            p.lockouts.clear();
            p.lockoutWeek = week;
            for (PlayerProfile.PvpRating r : p.pvp.values()) {
                r.weeklyPlayed = 0;
                r.weeklyWon = 0;
            }
        }
    }

    // ================================================================== input

    public void handle(UUID uuid, Object msg) {
        PlayerSession s = sessions.get(uuid);
        if (s == null || s.unit == null) return;
        try {
            Input.handle(this, s, msg);
        } catch (RuntimeException e) {
            platform.log("Error handling " + msg.getClass().getSimpleName() + ": " + e);
            e.printStackTrace();
        }
    }

    /** Text command (/wow ...). Returns response lines. */
    public List<L10n> command(UUID uuid, String args, boolean op) {
        PlayerSession s = sessions.get(uuid);
        if (s == null) return List.of(L10n.of("Not ready yet.", "Еще не готово."));
        return commands.run(s, args == null ? "" : args.trim(), op);
    }

    /** Called by the adapter when a player would die from vanilla damage. Returns true to cancel the death. */
    public boolean onVanillaLethal(UUID uuid) {
        PlayerSession s = sessions.get(uuid);
        if (s == null || s.unit == null) return false;
        if (s.ghost) return true;
        boolean inInstance = s.unit.instanceId != null;
        if (!s.unit.isDead()) engine.die(s.unit, null);
        return inInstance;
    }

    /** Should a killed player become a ghost instead of dying in Minecraft? */
    public boolean isGhost(UUID uuid) {
        PlayerSession s = sessions.get(uuid);
        return s != null && s.ghost;
    }

    /** Minecraft health changed by non-WoW sources (fall damage, food...). Amount in fractions of max health. */
    public void onVanillaHealthDelta(UUID uuid, double fractionDelta, boolean fromMob) {
        PlayerSession s = sessions.get(uuid);
        if (s == null || s.unit == null || s.unit.isDead()) return;
        UnitState u = s.unit;
        if (fractionDelta < 0) {
            engine.environmentalDamage(u, -fractionDelta * u.maxHealth(), null);
        } else if (!u.inCombat()) {
            engine.rawHeal(u, u, fractionDelta * u.maxHealth());
        }
    }

    // ================================================================== messaging helpers

    public void send(PlayerSession s, Object msg) {
        platform.send(s.uuid, msg);
    }

    public void send(UUID uuid, Object msg) {
        platform.send(uuid, msg);
    }

    public void msg(PlayerSession s, L10n text, int color) {
        S2C.Chat c = new S2C.Chat();
        c.text = new S2C.Text(text.en(), text.ru());
        c.color = color;
        platform.send(s.uuid, c);
    }

    public void msg(UUID uuid, L10n text, int color) {
        PlayerSession s = sessions.get(uuid);
        if (s != null) msg(s, text, color);
    }

    public void warn(UUID uuid, L10n text, int color) {
        S2C.Warning w = new S2C.Warning();
        w.text = new S2C.Text(text.en(), text.ru());
        w.color = color;
        platform.send(uuid, w);
    }

    void warnRun(InstanceRun run, L10n text, int color) {
        for (UUID m : run.members) warn(m, text, color);
    }

    void sendTelegraph(InstanceRun run, Telegraph t, boolean added) {
        S2C.Telegraph msg = Sync.telegraph(t, added, engine.now());
        for (UUID m : run.members) platform.send(m, msg);
    }

    // ================================================================== spawning helpers

    UnitState spawnInRun(InstanceRun run, String templateId, Vec3 pos, float yaw) {
        return instances.spawnNpc(run, templateId, pos, yaw, null);
    }

    // ================================================================== participants

    /** Players and bots belonging to an instance. */
    List<UnitState> participants(InstanceRun run) {
        List<UnitState> out = new ArrayList<>();
        for (UUID m : run.members) {
            PlayerSession s = sessions.get(m);
            if (s != null && s.unit != null && run.id.equals(s.unit.instanceId)) out.add(s.unit);
        }
        out.addAll(run.bots);
        return out;
    }

    // ================================================================== cast rules

    private CastResult castFilter(UnitState caster, Ability a) {
        if (a.hasTag("battle_res")) {
            InstanceRun run = instances.runOf(caster);
            if (run != null) {
                Encounter e = run.activeEncounter();
                if (e != null) {
                    if (e.battleResCharges <= 0) return CastResult.REQUIREMENT;
                    e.battleResCharges--;
                }
            }
        }
        CastResult pr = pvp.castFilter(caster, a);
        if (pr != null) return pr;
        return CastResult.OK;
    }

    // ================================================================== CombatListener

    @Override
    public void onDamage(HitResult hit) {
        queueCombatText(hit);
        Group g = groups.groupOfUnit(hit.source != null ? hit.source.master() : null);
        if (g != null) g.meter.onHit(hit, engine.now());
        Group tg = groups.groupOfUnit(hit.target != null ? hit.target.master() : null);
        if (tg != null && tg != g) tg.meter.onHit(hit, engine.now());
        instances.onDamage(hit);
        pvp.onDamage(hit);
    }

    @Override
    public void onHeal(HitResult hit) {
        queueCombatText(hit);
        Group g = groups.groupOfUnit(hit.source != null ? hit.source.master() : null);
        if (g != null) g.meter.onHit(hit, engine.now());
    }

    private void queueCombatText(HitResult hit) {
        UnitState src = hit.source != null ? hit.source.master() : null;
        UnitState tgt = hit.target;
        if (src != null && src.isPlayer()) addText(src.uuid, hit);
        if (tgt != null && tgt.isPlayer() && (src == null || !tgt.uuid.equals(src.uuid))) addText(tgt.uuid, hit);
    }

    private void addText(UUID uuid, HitResult hit) {
        S2C.CombatTextBatch b = pendingText.computeIfAbsent(uuid, k -> new S2C.CombatTextBatch());
        if (b.events.size() > 40) return;
        S2C.CombatText t = new S2C.CombatText();
        t.sourceId = hit.source != null ? hit.source.id : -1;
        t.targetId = hit.target.id;
        t.amount = Math.round(hit.heal ? hit.effective() : hit.amount);
        t.absorbed = Math.round(hit.absorbed);
        t.crit = hit.crit;
        t.heal = hit.heal;
        t.periodic = hit.periodic;
        t.immune = hit.immune;
        t.school = hit.school.name();
        t.ability = hit.abilityId;
        Vec3 p = hit.target.position();
        t.x = p.x();
        t.y = p.y() + hit.target.height();
        t.z = p.z();
        b.events.add(t);
    }

    private void flushCombatText() {
        if (pendingText.isEmpty()) return;
        for (var en : pendingText.entrySet()) platform.send(en.getKey(), en.getValue());
        pendingText.clear();
    }

    @Override
    public void onDeath(UnitState unit, UnitState killer) {
        Group g = groups.groupOfUnit(unit);
        if (g != null) g.meter.onDeath(unit);
        if (unit.isPlayer()) {
            PlayerSession s = sessions.get(unit.uuid);
            if (s != null) {
                boolean ghostly = unit.instanceId != null || pvp.matchOf(unit) != null;
                if (ghostly) {
                    s.ghost = true;
                    s.ghostSince = engine.now();
                    engine.makeGhost(unit);
                    platform.setGhost(s.uuid, true);
                    instances.onPlayerDeath(s);
                    pvp.onPlayerDeath(unit, killer);
                    msg(s, L10n.of("You died. Wait for a resurrection or use /wow release.",
                            "Вы погибли. Дождитесь воскрешения или используйте /wow release."), 0xFFFF6060);
                }
            }
        } else if (unit.kind == UnitKind.BOT) {
            instances.onBotDeath(unit);
            pvp.onPlayerDeath(unit, killer);
        } else if (unit.isNpcLike()) {
            Rewards.onNpcKilled(this, unit, killer);
            instances.onNpcDeath(unit, killer);
            for (Encounter e : worldEncounters) if (e.adds.contains(unit) && e.script != null) e.script.onAddDeath(unit);
        }
    }

    @Override
    public void onResurrect(UnitState unit, UnitState by) {
        if (unit.isPlayer()) {
            PlayerSession s = sessions.get(unit.uuid);
            if (s != null && s.ghost) {
                s.ghost = false;
                platform.setGhost(s.uuid, false);
                if (by != null && by != unit) platform.teleport(s.uuid, by.worldKey(), by.position(), by.yaw());
            }
        }
    }

    @Override
    public void onVfx(String key, UnitState source, UnitState target, Vec3 point, double param) {
        S2C.Vfx v = new S2C.Vfx();
        v.key = key;
        v.sourceId = source != null ? source.id : -1;
        v.targetId = target != null ? target.id : -1;
        if (point != null) {
            v.x = point.x();
            v.y = point.y();
            v.z = point.z();
            v.hasPoint = true;
        }
        v.param = param;
        Vec3 at = point != null ? point : (target != null ? target.position() : source != null ? source.position() : null);
        String wk = source != null ? source.worldKey() : target != null ? target.worldKey() : null;
        if (at == null || wk == null) return;
        broadcastNear(wk, at, 48, v);
    }

    void broadcastNear(String worldKey, Vec3 at, double radius, Object msg) {
        double r2 = radius * radius;
        for (PlayerSession s : sessions.values()) {
            if (s.unit == null || !worldKey.equals(s.unit.worldKey())) continue;
            if (s.unit.position().distanceSq(at) <= r2) platform.send(s.uuid, msg);
        }
    }

    @Override
    public void onError(UnitState unit, CastResult result, Ability ability) {
        if (!unit.isPlayer()) return;
        S2C.Error e = new S2C.Error();
        e.code = result.name();
        e.ability = ability != null ? ability.id : null;
        platform.send(unit.uuid, e);
    }

    @Override
    public void onInterrupt(UnitState interrupter, UnitState target, Ability interrupted) {
        Group g = groups.groupOfUnit(interrupter.master());
        if (g != null) g.meter.onInterrupt(interrupter);
    }

    @Override
    public void onAreaCreated(GroundArea area) {
        broadcastNear(area.worldKey, area.center, 64, Sync.area(area, false, engine.now()));
    }

    @Override
    public void onAreaRemoved(GroundArea area) {
        broadcastNear(area.worldKey, area.center, 96, Sync.area(area, true, engine.now()));
    }

    @Override
    public void onUnitRemoved(UnitState unit) {
        instances.onUnitRemoved(unit);
        bots.onUnitRemoved(unit);
    }

    // ================================================================== WorldAccess

    @Override
    public boolean lineOfSight(String worldKey, Vec3 from, Vec3 to) {
        return platform.lineOfSight(worldKey, from, to);
    }

    @Override
    public Vec3 safeDestination(String worldKey, Vec3 from, Vec3 to) {
        return platform.safeDestination(worldKey, from, to);
    }

    @Override
    public UnitState spawnNpc(String worldKey, Vec3 pos, float yaw, String templateId, UnitState owner, int level) {
        NpcManager.SpawnSpec spec = new NpcManager.SpawnSpec();
        spec.worldKey = worldKey;
        spec.pos = pos;
        spec.yaw = yaw;
        spec.level = level;
        spec.owner = owner;
        spec.instanceId = owner != null ? owner.instanceId : null;
        InstanceRun run = owner != null ? instances.runOf(owner) : null;
        if (owner == null && run != null) return instances.spawnNpc(run, templateId, pos, yaw, null);
        UnitState u = npcs.spawn(templateId, spec);
        if (u != null && run != null) run.npcs.add(u);
        return u;
    }

    // ================================================================== NpcManager.Listener

    @Override
    public void onPull(NpcBrain brain) {
        NpcTemplate t = brain.template;
        if (t.rank != NpcRank.BOSS || brain.encounter != null) return;
        InstanceRun run = instances.runOf(brain.unit);
        BossScript script = BossScripts.create(t.bossScript);
        Encounter e = new Encounter(t.id, brain.unit, script, engine, this, run);
        brain.encounter = e;
        // council fights: other bosses in the same pack join the encounter
        if (brain.unit.packId != null) {
            for (NpcBrain other : npcs.brains()) {
                if (other == brain || other.template.rank != NpcRank.BOSS) continue;
                if (!brain.unit.packId.equals(other.unit.packId)) continue;
                if (brain.unit.instanceId != null && !brain.unit.instanceId.equals(other.unit.instanceId)) continue;
                e.bosses.add(other.unit);
                other.encounter = e;
            }
        }
        if (run != null) run.encounters.add(e);
        else worldEncounters.add(e);
        e.start();
        for (UnitState p : participants(e)) {
            if (p.isPlayer()) {
                S2C.Warning w = new S2C.Warning();
                w.text = new S2C.Text(t.name.en() + " engaged!", t.name.ru() + " вступает в бой!");
                w.color = 0xFFFFD040;
                w.sound = "pull";
                platform.send(p.uuid, w);
            }
        }
    }

    @Override
    public void onEvade(NpcBrain brain) {
    }

    @Override
    public boolean sameInstance(UnitState npc, UnitState target) {
        String a = npc.instanceId, b = target.master().instanceId;
        if (a == null) return b == null || pvp.matchOf(target) == null;
        return a.equals(b);
    }

    // ================================================================== EncounterHost

    @Override
    public List<UnitState> participants(Encounter e) {
        if (e.instance instanceof InstanceRun run) return participants(run);
        List<UnitState> out = new ArrayList<>();
        for (PlayerSession s : sessions.values()) {
            if (s.unit != null && engine.sameWorld(s.unit, e.boss()) && s.unit.position().distance(e.boss().position()) < 70) out.add(s.unit);
        }
        return out;
    }

    @Override
    public UnitState spawnAdd(Encounter e, String templateId, Vec3 pos) {
        if (e.instance instanceof InstanceRun run) return instances.spawnNpc(run, templateId, pos, e.boss().yaw(), null);
        NpcManager.SpawnSpec spec = new NpcManager.SpawnSpec();
        spec.worldKey = e.boss().worldKey();
        spec.pos = pos;
        spec.level = e.boss().level;
        return npcs.spawn(templateId, spec);
    }

    @Override
    public void telegraph(Encounter e, Telegraph t, boolean added) {
        S2C.Telegraph msg = Sync.telegraph(t, added, engine.now());
        for (UnitState p : participants(e)) if (p.isPlayer()) platform.send(p.uuid, msg);
    }

    @Override
    public void warn(Encounter e, L10n text, int color) {
        for (UnitState p : participants(e)) if (p.isPlayer()) warn(p.uuid, text, color);
    }

    @Override
    public void say(Encounter e, UnitState speaker, L10n text) {
        NpcTemplate t = NpcRegistry.get(speaker.templateId);
        String en = (t != null ? t.name.en() : speaker.name) + " yells: " + text.en();
        String ru = (t != null ? t.name.ru() : speaker.name) + " кричит: " + text.ru();
        for (UnitState p : participants(e)) if (p.isPlayer()) msg(p.uuid, L10n.of(en, ru), 0xFFFF4040);
    }

    @Override
    public void timersChanged(Encounter e) {
        S2C.BossTimers msg = Sync.timers(e, engine.now());
        for (UnitState p : participants(e)) if (p.isPlayer()) platform.send(p.uuid, msg);
    }

    @Override
    public void ended(Encounter e, boolean victory) {
        instances.onEncounterEnd(e, victory);
        if (!victory) {
            for (UnitState add : e.adds) if (add.isAlive()) npcs.despawn(add);
        }
    }

    // ================================================================== BotContext

    @Override
    public List<UnitState> allies(UnitState bot) {
        return bots.allies(bot);
    }

    @Override
    public List<UnitState> enemies(UnitState bot) {
        return bots.enemies(bot);
    }

    @Override
    public UnitState leader(UnitState bot) {
        return bots.leader(bot);
    }

    @Override
    public List<Telegraph> telegraphs(UnitState bot) {
        return bots.telegraphs(bot);
    }

    @Override
    public boolean pvp(UnitState bot) {
        return pvp.matchOf(bot) != null;
    }

    @Override
    public Vec3 objective(UnitState bot) {
        return pvp.objectiveFor(bot);
    }

    @Override
    public int formationIndex(UnitState bot) {
        return bots.formationIndex(bot);
    }

    // ================================================================== misc

    public double mythicRating(PlayerProfile p) {
        return MythicScore.totalRating(p);
    }

    public long tickCount() {
        return tickCount;
    }
}
