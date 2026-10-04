package com.wowcraft.core.spell;

import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An ability (spell / attack). Immutable after building. */
public final class Ability {
    public static final double MELEE_RANGE = 3.5;
    public static final double RANGED = 30.0;
    public static final double DEFAULT_GCD = 1.5;

    public final String id;
    public final L10n name;
    public final L10n description;
    public final WowClass wowClass;
    public final Set<Spec> specs;
    public final int unlockLevel;
    public final School school;
    public final TargetType targetType;
    public final double range;
    public final double minRange;
    public final CastType castType;
    public final double castTime;
    public final double channelDuration;
    public final double channelTickInterval;
    public final int maxEmpowerStage;
    public final double cooldown;
    public final boolean hastedCooldown;
    public final int charges;
    public final double gcd;
    public final List<Cost> costs;
    public final List<Cost> generates;
    public final List<Effect> effects;
    public final List<Effect> channelEffects;
    public final List<Cond> requirements;
    public final Set<String> tags;
    public final boolean castWhileMoving;
    public final boolean requiresFacing;
    public final boolean ignoresLineOfSight;
    public final boolean usableWhileCc;
    public final boolean usableWhileCasting;
    public final boolean breaksStealth;
    public final boolean interruptible;
    public final boolean passive;
    public final boolean hidden;
    public final boolean startsAutoAttack;
    /** Ability ids that share this ability's cooldown. */
    public final Set<String> sharedCooldown;
    public final String vfx;
    public final String animation;
    public final String sound;
    public final int iconColor;
    /** Aura id of a shapeshift form the ability needs; the caster shifts automatically (druids). */
    public final String requiredFormAura;
    /** Dispel types this ability removes (derived from its effects; used by AI). */
    public final java.util.Set<com.wowcraft.core.aura.DispelType> dispelTypes;

    private Ability(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.wowClass = b.wowClass;
        this.specs = b.specs.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(b.specs));
        this.unlockLevel = b.unlockLevel;
        this.school = b.school;
        this.targetType = b.targetType;
        this.range = b.range;
        this.minRange = b.minRange;
        this.castType = b.castType;
        this.castTime = b.castTime;
        this.channelDuration = b.channelDuration;
        this.channelTickInterval = b.channelTickInterval;
        this.maxEmpowerStage = b.maxEmpowerStage;
        this.cooldown = b.cooldown;
        this.hastedCooldown = b.hastedCooldown;
        this.charges = b.charges;
        this.gcd = b.gcd;
        this.costs = List.copyOf(b.costs);
        this.generates = List.copyOf(b.generates);
        this.effects = List.copyOf(b.effects);
        this.channelEffects = List.copyOf(b.channelEffects);
        this.requirements = List.copyOf(b.requirements);
        this.tags = Collections.unmodifiableSet(new HashSet<>(b.tags));
        this.castWhileMoving = b.castWhileMoving;
        this.requiresFacing = b.requiresFacing;
        this.ignoresLineOfSight = b.ignoresLineOfSight;
        this.usableWhileCc = b.usableWhileCc;
        this.usableWhileCasting = b.usableWhileCasting;
        this.breaksStealth = b.breaksStealth;
        this.interruptible = b.interruptible;
        this.passive = b.passive;
        this.hidden = b.hidden;
        this.startsAutoAttack = b.startsAutoAttack;
        this.sharedCooldown = Set.copyOf(b.sharedCooldown);
        this.vfx = b.vfx;
        this.animation = b.animation;
        this.sound = b.sound;
        this.iconColor = b.iconColor;
        this.requiredFormAura = b.requiredFormAura;
        java.util.Set<com.wowcraft.core.aura.DispelType> dt = java.util.EnumSet.noneOf(com.wowcraft.core.aura.DispelType.class);
        for (Effect e : b.effects) if (e instanceof Effects.DispelEffect de) dt.addAll(de.types);
        this.dispelTypes = java.util.Collections.unmodifiableSet(dt);
    }

    public boolean isHelpful() {
        return targetType == TargetType.FRIENDLY || targetType == TargetType.SELF || targetType == TargetType.DEAD_FRIENDLY
                || tags.contains("heal") || tags.contains("buff") || tags.contains("defensive");
    }

    public boolean hasTag(String t) {
        return tags.contains(t);
    }

    public boolean usableBy(Spec spec) {
        if (wowClass == null) return true;
        if (spec == null || spec.wowClass != wowClass) return false;
        return specs.isEmpty() || specs.contains(spec);
    }

    public double primaryCost(ResourceType t) {
        for (Cost c : costs) if (c.type() == t) return c.amount();
        return 0;
    }

    public static Builder builder(String id, String en, String ru) {
        return new Builder(id, L10n.of(en, ru));
    }

    public static final class Builder {
        private final String id;
        private final L10n name;
        private L10n description;
        private WowClass wowClass;
        private final Set<Spec> specs = new HashSet<>();
        private int unlockLevel = 1;
        private School school = School.PHYSICAL;
        private TargetType targetType = TargetType.ENEMY;
        private double range = MELEE_RANGE;
        private double minRange;
        private CastType castType = CastType.INSTANT;
        private double castTime;
        private double channelDuration;
        private double channelTickInterval = 1.0;
        private int maxEmpowerStage;
        private double cooldown;
        private boolean hastedCooldown;
        private int charges = 1;
        private double gcd = DEFAULT_GCD;
        private final List<Cost> costs = new ArrayList<>();
        private final List<Cost> generates = new ArrayList<>();
        private final List<Effect> effects = new ArrayList<>();
        private final List<Effect> channelEffects = new ArrayList<>();
        private final List<Cond> requirements = new ArrayList<>();
        private final Set<String> tags = new HashSet<>();
        private boolean castWhileMoving;
        private boolean requiresFacing = true;
        private boolean ignoresLineOfSight;
        private boolean usableWhileCc;
        private boolean usableWhileCasting;
        private boolean breaksStealth = true;
        private boolean interruptible = true;
        private boolean passive;
        private boolean hidden;
        private boolean startsAutoAttack;
        private final Set<String> sharedCooldown = new HashSet<>();
        private String vfx;
        private String animation;
        private String sound;
        private int iconColor;
        private String requiredFormAura;

        private Builder(String id, L10n name) {
            this.id = id;
            this.name = name;
        }

        public Builder desc(String en, String ru) {
            this.description = L10n.of(en, ru);
            return this;
        }

        public Builder cls(WowClass c) {
            this.wowClass = c;
            return this;
        }

        public Builder spec(Spec... s) {
            Collections.addAll(this.specs, s);
            if (s.length > 0 && wowClass == null) wowClass = s[0].wowClass;
            return this;
        }

        public Builder level(int lvl) {
            this.unlockLevel = lvl;
            return this;
        }

        public Builder school(School s) {
            this.school = s;
            return this;
        }

        public Builder target(TargetType t) {
            this.targetType = t;
            if (t == TargetType.SELF || t == TargetType.NONE) {
                this.range = 0;
                this.requiresFacing = false;
            }
            if (t == TargetType.FRIENDLY || t == TargetType.DEAD_FRIENDLY || t == TargetType.GROUND) {
                this.requiresFacing = false;
                if (range <= MELEE_RANGE) range = RANGED;
            }
            return this;
        }

        public Builder range(double r) {
            this.range = r;
            return this;
        }

        public Builder ranged() {
            this.range = RANGED;
            return this;
        }

        public Builder melee() {
            this.range = MELEE_RANGE;
            return this;
        }

        public Builder minRange(double r) {
            this.minRange = r;
            return this;
        }

        public Builder cast(double seconds) {
            this.castType = seconds > 0 ? CastType.CAST : CastType.INSTANT;
            this.castTime = seconds;
            return this;
        }

        public Builder channel(double duration, double tickInterval) {
            this.castType = CastType.CHANNEL;
            this.channelDuration = duration;
            this.channelTickInterval = tickInterval;
            return this;
        }

        public Builder empower(int maxStage) {
            this.castType = CastType.EMPOWER;
            this.maxEmpowerStage = maxStage;
            return this;
        }

        public Builder cooldown(double seconds) {
            this.cooldown = seconds;
            return this;
        }

        public Builder hastedCooldown() {
            this.hastedCooldown = true;
            return this;
        }

        public Builder charges(int n) {
            this.charges = n;
            return this;
        }

        public Builder gcd(double seconds) {
            this.gcd = seconds;
            return this;
        }

        public Builder offGcd() {
            this.gcd = 0;
            return this;
        }

        public Builder cost(ResourceType t, double amount) {
            this.costs.add(t == ResourceType.MANA ? Cost.mana(amount) : Cost.of(t, amount));
            return this;
        }

        public Builder costRange(ResourceType t, double min, double max) {
            this.costs.add(Cost.range(t, min, max));
            return this;
        }

        public Builder gen(ResourceType t, double amount) {
            this.generates.add(Cost.of(t, amount));
            return this;
        }

        public Builder effect(Effect... es) {
            Collections.addAll(this.effects, es);
            return this;
        }

        public Builder tick(Effect... es) {
            Collections.addAll(this.channelEffects, es);
            return this;
        }

        public Builder requires(Cond c) {
            this.requirements.add(c);
            return this;
        }

        public Builder tag(String... t) {
            Collections.addAll(this.tags, t);
            return this;
        }

        public Builder moving() {
            this.castWhileMoving = true;
            return this;
        }

        public Builder noFacing() {
            this.requiresFacing = false;
            return this;
        }

        public Builder ignoreLos() {
            this.ignoresLineOfSight = true;
            return this;
        }

        public Builder usableWhileCc() {
            this.usableWhileCc = true;
            return this;
        }

        public Builder usableWhileCasting() {
            this.usableWhileCasting = true;
            return this;
        }

        public Builder keepStealth() {
            this.breaksStealth = false;
            return this;
        }

        public Builder uninterruptible() {
            this.interruptible = false;
            return this;
        }

        public Builder passive() {
            this.passive = true;
            this.gcd = 0;
            return this;
        }

        public Builder hidden() {
            this.hidden = true;
            return this;
        }

        public Builder autoAttack() {
            this.startsAutoAttack = true;
            return this;
        }

        public Builder sharesCooldownWith(String... ids) {
            Collections.addAll(this.sharedCooldown, ids);
            return this;
        }

        public Builder vfx(String key) {
            this.vfx = key;
            return this;
        }

        public Builder anim(String key) {
            this.animation = key;
            return this;
        }

        public Builder sound(String key) {
            this.sound = key;
            return this;
        }

        public Builder color(int argb) {
            this.iconColor = argb;
            return this;
        }

        /** Requires (and auto-shifts into) a form aura. */
        public Builder form(String formAuraId) {
            this.requiredFormAura = formAuraId;
            return this;
        }

        public Ability build() {
            if (iconColor == 0) iconColor = school.color;
            if (targetType == TargetType.ENEMY && !tags.contains("utility") && !tags.contains("cc")) {
                startsAutoAttack = startsAutoAttack || range <= MELEE_RANGE + 0.01;
            }
            return new Ability(this);
        }
    }
}
