package com.wowcraft.core.combat;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.aura.AuraList;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.mod.ModContext;
import com.wowcraft.core.mod.ModType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.mod.ModifierSet;
import com.wowcraft.core.resource.ResourcePool;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.stat.Ratings;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.stat.StatBlock;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Combat state of a unit (player, bot, NPC, pet...). Owned by the {@link CombatEngine}. */
public final class UnitState {
    public static final int DIRTY_HEALTH = 1, DIRTY_RESOURCES = 2, DIRTY_AURAS = 4, DIRTY_COOLDOWNS = 8,
            DIRTY_CAST = 16, DIRTY_STATS = 32, DIRTY_TARGET = 64, DIRTY_ABILITIES = 128;

    public final int id;
    public final UUID uuid;
    public String name;
    public final UnitKind kind;
    public Body body;
    public int level = 1;
    public String team = "monsters";
    public WowClass wowClass;
    public Spec spec;
    /** NPC template id (NPCs, pets, bots). */
    public String templateId;
    /** Role override for NPCs / bots (tank/healer/dps behaviour). */
    public Role role;
    public boolean boss;
    public boolean elite;

    // ---- health ----
    double health = 100;
    double maxHealth = 100;
    double baseMaxHealthOverride = -1;
    boolean dead;
    double deathTime;
    /** Dead player waiting for a resurrection inside an instance. */
    public boolean ghost;

    // ---- stats ----
    public final StatBlock baseStats = new StatBlock();
    public final StatBlock gearStats = new StatBlock();
    public WeaponInfo mainHand = WeaponInfo.FISTS;
    public WeaponInfo offHand;
    /** Flat physical damage reduction for NPCs that do not use the armor stat (0..1). */
    public double npcArmorReduction = 0.0;
    /** Multiplier for NPC ability / auto-attack damage (affixes, difficulty scaling). */
    public double damageMultiplier = 1.0;
    public double npcAttackPower = 0;
    public double npcSpellPower = 0;
    final DerivedStats stats = new DerivedStats();
    boolean statsDirty = true;

    // ---- modifiers ----
    /** Talents, gear effects, set bonuses, passives. */
    public final List<Modifier> permanentMods = new ArrayList<>();
    final ModifierSet mods = new ModifierSet();
    boolean modsDirty = true;

    final AuraList auras = new AuraList();
    final Cooldowns cooldowns = new Cooldowns();
    final ResourcePool resources = new ResourcePool();

    // ---- casting ----
    CastState cast;
    QueuedCast queued;
    double gcdEnd;
    double gcdDuration;
    final Map<School, Double> lockouts = new EnumMap<>(School.class);

    // ---- targeting / combat ----
    UnitState target;
    public boolean autoAttack;
    double nextSwingMain;
    double nextSwingOff;
    double lastCombatAt = -1e9;
    boolean inCombat;
    ThreatTable threat;
    final DrTracker dr = new DrTracker();
    double lastMovedAt = -1e9;
    /** Units this unit is engaged with (for NPC combat membership). */
    final Set<UnitState> engaged = new LinkedHashSet<>();

    // ---- ownership ----
    public UnitState owner;
    final List<UnitState> pets = new ArrayList<>();
    public double despawnAt = Double.POSITIVE_INFINITY;

    // ---- abilities ----
    public final Set<String> knownAbilities = new LinkedHashSet<>();
    public final Map<String, String> replacements = new HashMap<>();

    // ---- misc ----
    /** AI controller (NPC brain or bot). */
    public Object brain;
    /** Platform data (e.g. the Minecraft entity). */
    public Object platform;
    /** Party / raid id (players & bots). */
    public String groupId;
    /** Instance this unit belongs to. */
    public String instanceId;
    /** Pack id for NPC social aggro. */
    public String packId;
    /** Mythic+ enemy forces value. */
    public double forces;
    /** Arbitrary script data. */
    public final Map<String, Object> tags = new HashMap<>();
    int dirty = 0xFFFF;
    /** Recent damage taken (time, amount) for Death Strike style effects. */
    private final java.util.ArrayDeque<double[]> recentDamage = new java.util.ArrayDeque<>();

    public UnitState(int id, UUID uuid, String name, UnitKind kind) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.kind = kind;
        if (kind == UnitKind.NPC || kind == UnitKind.VANILLA) threat = new ThreatTable();
    }

    // ---------------------------------------------------------------- queries

    public boolean isPlayer() {
        return kind == UnitKind.PLAYER;
    }

    /** Players and bots: use player rules (DR, PvP, gear). */
    public boolean isPlayerLike() {
        return kind == UnitKind.PLAYER || kind == UnitKind.BOT;
    }

    public boolean isNpcLike() {
        return kind == UnitKind.NPC || kind == UnitKind.VANILLA;
    }

    public boolean isAlive() {
        return !dead && !ghost;
    }

    public boolean isDead() {
        return dead || ghost;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double healthFraction() {
        return maxHealth <= 0 ? 0 : health / maxHealth;
    }

    public Vec3 position() {
        return body != null ? body.position() : Vec3.ZERO;
    }

    public float yaw() {
        return body != null ? body.yaw() : 0f;
    }

    public double width() {
        return body != null ? body.width() : 0.6;
    }

    public double height() {
        return body != null ? body.height() : 1.8;
    }

    public Vec3 center() {
        return position().add(0, height() * 0.5, 0);
    }

    public Vec3 eye() {
        return position().add(0, height() * 0.85, 0);
    }

    public String worldKey() {
        return body != null ? body.worldKey() : "";
    }

    public AuraList auras() {
        return auras;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public ResourcePool resources() {
        return resources;
    }

    public CastState cast() {
        return cast;
    }

    public boolean isCasting() {
        return cast != null;
    }

    public UnitState target() {
        return target;
    }

    public ThreatTable threat() {
        return threat;
    }

    public boolean inCombat() {
        return inCombat;
    }

    public double gcdEnd() {
        return gcdEnd;
    }

    public double gcdDuration() {
        return gcdDuration;
    }

    public double lockoutUntil(School s) {
        return lockouts.getOrDefault(s, 0.0);
    }

    public DrTracker dr() {
        return dr;
    }

    public List<UnitState> pets() {
        return pets;
    }

    public List<UnitState> livingPets() {
        List<UnitState> out = new ArrayList<>();
        for (UnitState p : pets) if (p.isAlive() && (p.body == null || !p.body.isRemoved())) out.add(p);
        return out;
    }

    public boolean hasLivingPet() {
        for (UnitState p : pets) if (p.isAlive() && p.kind == UnitKind.PET) return true;
        return false;
    }

    public Role role() {
        if (role != null) return role;
        if (spec != null) return spec.role;
        return Role.MELEE_DPS;
    }

    public boolean hasCc(CcType t) {
        return auras.hasCc(t);
    }

    public boolean canMove() {
        for (AuraInstance a : auras.all()) if (!a.removed && a.def.cc != null && a.def.cc.preventsMovement) return false;
        return isAlive();
    }

    public boolean canCast() {
        for (AuraInstance a : auras.all()) if (!a.removed && a.def.cc != null && a.def.cc.preventsCasting) return false;
        return isAlive();
    }

    public boolean canAttack() {
        for (AuraInstance a : auras.all()) if (!a.removed && a.def.cc != null && a.def.cc.preventsAttacks) return false;
        return isAlive();
    }

    public boolean isSilenced() {
        return auras.hasCc(CcType.SILENCE);
    }

    /** Owner for pets, self otherwise (credit for damage / kills). */
    public UnitState master() {
        return owner != null ? owner.master() : this;
    }

    // ---------------------------------------------------------------- modifiers / stats

    public ModifierSet mods() {
        if (modsDirty) {
            mods.clear();
            for (Modifier m : permanentMods) mods.add(m);
            for (AuraInstance a : auras.all()) {
                if (a.removed) continue;
                double w = a.def.modsPerStack ? a.stacks : 1.0;
                for (Modifier m : a.def.mods) mods.add(m, w);
            }
            modsDirty = false;
        }
        return mods;
    }

    public void invalidateMods() {
        modsDirty = true;
        statsDirty = true;
        dirty |= DIRTY_STATS;
    }

    public DerivedStats stats() {
        if (statsDirty || modsDirty) recomputeStats();
        return stats;
    }

    public void setBaseMaxHealth(double value) {
        this.baseMaxHealthOverride = value;
        statsDirty = true;
    }

    void recomputeStats() {
        ModifierSet m = mods();
        DerivedStats d = stats;
        d.strength = statValue(Stat.STRENGTH, m);
        d.agility = statValue(Stat.AGILITY, m);
        d.intellect = statValue(Stat.INTELLECT, m);
        d.stamina = statValue(Stat.STAMINA, m);
        Stat primary = spec != null ? spec.primaryStat : Stat.STRENGTH;
        double primaryValue = primary == Stat.AGILITY ? d.agility : primary == Stat.INTELLECT ? d.intellect : d.strength;
        double mastery = Ratings.masteryPoints(statValue(Stat.MASTERY, m));
        d.masteryPoints = mastery;
        d.masteryPct = spec != null ? mastery * spec.masteryCoef : 0;
        if (isPlayerLike() || kind == UnitKind.PET) {
            double weaponAp = mainHand != null ? mainHand.dps() * 6 : 0;
            d.attackPower = Math.max(d.strength, d.agility) + (primary == Stat.INTELLECT ? 0 : weaponAp);
            if (primary == Stat.INTELLECT) d.attackPower = d.intellect * 0.5;
            d.spellPower = primary == Stat.INTELLECT ? d.intellect + weaponAp : primaryValue * 0.5;
            if (spec != null && spec.masteryKind == com.wowcraft.core.spec.MasteryKind.DEFENSIVE) {
                d.attackPower *= 1.0 + d.masteryPct / 200.0;
            }
        } else {
            d.attackPower = npcAttackPower;
            d.spellPower = npcSpellPower;
        }
        d.critPct = Ratings.critPct(statValue(Stat.CRIT, m)) + m.sum(ModType.CRIT_CHANCE, ModContext.NONE);
        double hasteRating = Ratings.hastePct(statValue(Stat.HASTE, m));
        d.hastePct = ((1 + hasteRating / 100.0) * m.product(ModType.HASTE, ModContext.NONE) - 1) * 100.0;
        d.versPct = Ratings.versPct(statValue(Stat.VERSATILITY, m));
        d.versDrPct = d.versPct / 2.0;
        d.leechPct = Ratings.leechPct(statValue(Stat.LEECH, m)) + m.sum(ModType.LEECH, ModContext.NONE);
        d.avoidancePct = Ratings.avoidancePct(statValue(Stat.AVOIDANCE, m)) + m.sum(ModType.AVOIDANCE, ModContext.NONE);
        d.speedPct = Ratings.speedPct(statValue(Stat.SPEED, m));
        d.armor = statValue(Stat.ARMOR, m) * m.product(ModType.ARMOR_PCT, ModContext.NONE);
        double hp;
        if (baseMaxHealthOverride > 0) {
            hp = baseMaxHealthOverride;
        } else {
            hp = Math.max(1, d.stamina * Ratings.HP_PER_STAMINA);
        }
        hp *= m.product(ModType.MAX_HEALTH_PCT, ModContext.NONE);
        d.maxHealth = Math.max(1, hp);
        double oldMax = maxHealth;
        if (Math.abs(oldMax - d.maxHealth) > 1e-6) {
            double frac = oldMax > 0 ? health / oldMax : 1.0;
            maxHealth = d.maxHealth;
            health = Math.min(maxHealth, Math.max(dead ? 0 : 1, frac * maxHealth));
            dirty |= DIRTY_HEALTH;
        }
        for (ResourceType t : resources.active()) {
            double base = t.defaultMax + m.sumRef(ModType.RESOURCE_MAX, t.name());
            if (Math.abs(resources.max(t) - base) > 1e-6 && t != ResourceType.RUNES) resources.setMax(t, base);
        }
        statsDirty = false;
        dirty |= DIRTY_STATS;
    }

    private double statValue(Stat s, ModifierSet m) {
        double v = baseStats.get(s) + gearStats.get(s) + m.sumRef(ModType.STAT_FLAT, s.name());
        return v * m.productRef(ModType.STAT_PCT, s.name());
    }

    // ---------------------------------------------------------------- dirty tracking for sync

    public void markDirty(int flags) {
        dirty |= flags;
    }

    public int consumeDirty() {
        int d = dirty;
        if (auras.consumeDirty()) d |= DIRTY_AURAS;
        if (cooldowns.consumeDirty()) d |= DIRTY_COOLDOWNS;
        if (resources.consumeDirty()) d |= DIRTY_RESOURCES;
        dirty = 0;
        return d;
    }

    public void recordDamageTaken(double now, double amount) {
        recentDamage.addLast(new double[]{now, amount});
        while (!recentDamage.isEmpty() && now - recentDamage.peekFirst()[0] > 10.0) recentDamage.removeFirst();
    }

    /** Damage taken in the last {@code seconds} seconds. */
    public double recentDamageTaken(double now, double seconds) {
        double sum = 0;
        for (double[] e : recentDamage) if (now - e[0] <= seconds) sum += e[1];
        return sum;
    }

    public boolean isEngagedWith(UnitState other) {
        return engaged.contains(other);
    }

    public Set<UnitState> engaged() {
        return engaged;
    }

    @Override
    public String toString() {
        return name + "#" + id;
    }

    /** A cast requested while busy (spell queue). */
    record QueuedCast(String abilityId, UnitState target, Vec3 point, double requestedAt) {
    }
}
