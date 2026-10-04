package com.wowcraft.core.npc;

import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.combat.WeaponInfo;
import com.wowcraft.core.game.Platform;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spawns, configures and runs all template-based NPCs (mobs, bosses, pets, totems). */
public final class NpcManager implements NpcContext {
    /** Callbacks into the game server. */
    public interface Listener {
        void onPull(NpcBrain brain);

        void onEvade(NpcBrain brain);

        boolean sameInstance(UnitState npc, UnitState target);
    }

    /** Spawn parameters. */
    public static final class SpawnSpec {
        public String worldKey;
        public Vec3 pos;
        public float yaw;
        public int level = 80;
        public Difficulty difficulty = Difficulty.WORLD;
        public double healthMult = 1.0;
        public double damageMult = 1.0;
        public boolean instanced;
        public int groupSize = 5;
        public String instanceId;
        public String packId;
        public UnitState owner;
        public String team;
    }

    private final CombatEngine engine;
    private final Platform platform;
    private final Listener listener;
    private final Map<Integer, NpcBrain> brains = new LinkedHashMap<>();
    private double nextPetRefresh;

    public NpcManager(CombatEngine engine, Platform platform, Listener listener) {
        this.engine = engine;
        this.platform = platform;
        this.listener = listener;
    }

    public UnitState spawn(String templateId, SpawnSpec s) {
        NpcTemplate t = NpcRegistry.get(templateId);
        if (t == null) {
            platform.log("Unknown NPC template " + templateId);
            return null;
        }
        UnitKind kind = s.owner != null ? (t.body == BodyType.TOTEM ? UnitKind.TOTEM : UnitKind.PET) : UnitKind.NPC;
        UnitState u = platform.createNpcBody(s.worldKey, s.pos, s.yaw, t, kind, t.name.en());
        if (u == null) return null;
        configure(u, t, s);
        NpcBrain brain = new NpcBrain(t, u, s.pos, s.yaw);
        if (t.rank == NpcRank.BOSS) brain.leash = 80;
        u.brain = brain;
        brains.put(u.id, brain);
        return u;
    }

    private void configure(UnitState u, NpcTemplate t, SpawnSpec s) {
        u.templateId = t.id;
        u.level = s.level;
        u.boss = t.rank == NpcRank.BOSS;
        u.elite = t.rank == NpcRank.ELITE || t.rank == NpcRank.MINIBOSS || t.rank == NpcRank.BOSS || t.rank == NpcRank.RARE;
        u.role = t.role;
        u.npcArmorReduction = t.armorReduction;
        u.instanceId = s.instanceId;
        u.packId = s.packId;
        u.forces = t.forcesValue();
        u.knownAbilities.clear();
        for (NpcSpell sp : t.spells) u.knownAbilities.add(sp.abilityId());
        u.permanentMods.clear();
        if (t.immuneCc && !u.boss) u.permanentMods.add(Modifier.immune("ALL"));
        if (s.owner != null) {
            UnitState o = s.owner;
            u.owner = o;
            u.team = o.team;
            u.groupId = o.groupId;
            if (!o.pets().contains(u)) o.pets().add(u);
            refreshPet(u, t);
        } else {
            u.team = s.team != null ? s.team : (t.friendly ? "friendly" : "monsters");
            double hp = NpcScaling.health(t, s.level, s.difficulty, s.healthMult, s.instanced, s.groupSize);
            engine.setMaxHealthDirect(u, hp, false);
            u.npcAttackPower = NpcScaling.attackPower(t, s.level, s.difficulty, s.damageMult);
            u.npcSpellPower = NpcScaling.spellPower(t, s.level, s.difficulty, s.damageMult);
        }
        u.mainHand = new WeaponInfo(0, t.meleeSpeed, false, true);
        u.invalidateMods();
        engine.resetUnit(u, true);
    }

    /** Pets scale with their owner. */
    private void refreshPet(UnitState pet, NpcTemplate t) {
        UnitState o = pet.owner;
        if (o == null) return;
        double power = Math.max(o.stats().attackPower, o.stats().spellPower);
        double powerScale = t.ownerPowerScale > 0 ? t.ownerPowerScale : 0.35;
        double hpScale = t.ownerHealthScale > 0 ? t.ownerHealthScale : 0.6;
        pet.npcAttackPower = power * powerScale;
        pet.npcSpellPower = power * powerScale;
        pet.level = o.level;
        double frac = pet.maxHealth() > 0 ? pet.healthFraction() : 1;
        engine.setMaxHealthDirect(pet, Math.max(10, o.maxHealth() * hpScale), false);
        engine.setHealth(pet, pet.maxHealth() * (frac <= 0 ? 1 : frac));
    }

    public void tick() {
        double now = engine.now();
        boolean refresh = now >= nextPetRefresh;
        if (refresh) nextPetRefresh = now + 5;
        for (NpcBrain b : new ArrayList<>(brains.values())) {
            if (engine.unit(b.unit.id) == null) {
                brains.remove(b.unit.id);
                continue;
            }
            if (refresh && b.unit.owner != null && b.unit.isAlive()) refreshPet(b.unit, b.template);
            try {
                b.tick(this);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    public NpcBrain brain(UnitState u) {
        return brains.get(u.id);
    }

    public List<NpcBrain> brains() {
        return new ArrayList<>(brains.values());
    }

    public void despawn(UnitState u) {
        brains.remove(u.id);
        platform.removeBody(u);
        engine.remove(u);
    }

    public void despawnInstance(String instanceId) {
        for (NpcBrain b : new ArrayList<>(brains.values())) {
            if (instanceId.equals(b.unit.instanceId)) despawn(b.unit);
        }
    }

    // ------------------------------------------------------------------ NpcContext

    @Override
    public CombatEngine engine() {
        return engine;
    }

    @Override
    public List<UnitState> aggroCandidates(UnitState npc, double radius) {
        List<UnitState> out = new ArrayList<>();
        for (UnitState u : engine.units()) {
            if (u == npc || u.isDead()) continue;
            if (!(u.isPlayerLike() || (u.owner != null && u.owner.isPlayerLike()))) continue;
            if (!engine.sameWorld(npc, u) || !engine.isHostile(npc, u)) continue;
            if (!listener.sameInstance(npc, u)) continue;
            if (engine.distance(npc, u) <= radius) out.add(u);
        }
        return out;
    }

    @Override
    public List<UnitState> enemiesInCombat(NpcBrain brain) {
        List<UnitState> out = new ArrayList<>();
        if (brain.unit.threat() == null) return out;
        for (UnitState u : brain.unit.threat().units()) if (u.isAlive()) out.add(u);
        return out;
    }

    @Override
    public UnitState lowestAlly(NpcBrain brain, double range) {
        UnitState best = null;
        for (NpcBrain b : brains.values()) {
            UnitState u = b.unit;
            if (u.isDead() || !engine.isFriendly(brain.unit, u)) continue;
            if (engine.distance(brain.unit, u) > range) continue;
            if (u.healthFraction() >= 0.99) continue;
            if (best == null || u.healthFraction() < best.healthFraction()) best = u;
        }
        return best;
    }

    @Override
    public boolean isValidTarget(NpcBrain brain, UnitState target) {
        return !target.ghost && listener.sameInstance(brain.unit, target);
    }

    @Override
    public void onPull(NpcBrain brain) {
        // social aggro: the whole pack joins
        if (brain.unit.packId != null) {
            List<UnitState> targets = brain.unit.threat() != null ? brain.unit.threat().units() : List.of();
            for (NpcBrain other : brains.values()) {
                if (other == brain || other.state != NpcBrain.State.IDLE) continue;
                if (!brain.unit.packId.equals(other.unit.packId)) continue;
                if (brain.unit.instanceId != null && !brain.unit.instanceId.equals(other.unit.instanceId)) continue;
                for (UnitState t : targets) engine.aggro(other.unit, t, 1);
            }
        }
        listener.onPull(brain);
    }

    @Override
    public void onEvade(NpcBrain brain) {
        listener.onEvade(brain);
    }
}
