package com.wowcraft.core.aura;

import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Definition of a buff / debuff / passive. Immutable after {@link Builder#build()}. */
public final class AuraDef {
    public final String id;
    public final L10n name;
    public final L10n description;
    public final boolean harmful;
    public final School school;
    /** Base duration in seconds; 0 = until removed. */
    public final double duration;
    public final int maxStacks;
    /** Stacks gained per application. */
    public final int stacksPerApply;
    public final boolean pandemic;
    public final boolean refreshOnApply;
    public final DispelType dispel;
    public final List<Modifier> mods;
    public final boolean modsPerStack;
    public final CcType cc;
    public final double tickInterval;
    public final boolean hastedTicks;
    public final Effect tickEffect;
    public final boolean tickOnApply;
    public final Effect onApply;
    public final Effect onExpire;
    public final Effect onRemove;
    public final boolean absorb;
    public final boolean absorbMagicOnly;
    public final List<Trigger> triggers;
    /** Breaks when the holder takes more than this fraction of max health in a single hit (0 = never). */
    public final double breakOnDamageFraction;
    public final boolean breakOnAnyDamage;
    public final boolean stealth;
    /** Shapeshift form / stance name (abilities can require it). Only one form at a time. */
    public final String form;
    public final boolean hidden;
    public final boolean passive;
    /** Each caster keeps a separate instance on the target (DoTs from different players). */
    public final boolean perCaster;
    public final boolean persistsThroughDeath;
    /** Removed when the holder leaves combat. */
    public final boolean removeOutOfCombat;
    public final Set<String> tags;
    public final String vfx;
    public final int iconColor;

    private AuraDef(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.harmful = b.harmful;
        this.school = b.school;
        this.duration = b.duration;
        this.maxStacks = b.maxStacks;
        this.stacksPerApply = b.stacksPerApply;
        this.pandemic = b.pandemic;
        this.refreshOnApply = b.refreshOnApply;
        this.dispel = b.dispel;
        this.mods = Collections.unmodifiableList(new ArrayList<>(b.mods));
        this.modsPerStack = b.modsPerStack;
        this.cc = b.cc;
        this.tickInterval = b.tickInterval;
        this.hastedTicks = b.hastedTicks;
        this.tickEffect = b.tickEffect;
        this.tickOnApply = b.tickOnApply;
        this.onApply = b.onApply;
        this.onExpire = b.onExpire;
        this.onRemove = b.onRemove;
        this.absorb = b.absorb;
        this.absorbMagicOnly = b.absorbMagicOnly;
        this.triggers = Collections.unmodifiableList(new ArrayList<>(b.triggers));
        this.breakOnDamageFraction = b.breakOnDamageFraction;
        this.breakOnAnyDamage = b.breakOnAnyDamage;
        this.stealth = b.stealth;
        this.form = b.form;
        this.hidden = b.hidden;
        this.passive = b.passive;
        this.perCaster = b.perCaster;
        this.persistsThroughDeath = b.persistsThroughDeath;
        this.removeOutOfCombat = b.removeOutOfCombat;
        this.tags = Collections.unmodifiableSet(new HashSet<>(b.tags));
        this.vfx = b.vfx;
        this.iconColor = b.iconColor != 0 ? b.iconColor : (b.harmful ? 0xFFB02020 : 0xFF2060B0);
    }

    public boolean isPeriodic() {
        return tickInterval > 0 && tickEffect != null;
    }

    public static Builder buff(String id, String en, String ru) {
        return new Builder(id, L10n.of(en, ru), false);
    }

    public static Builder debuff(String id, String en, String ru) {
        return new Builder(id, L10n.of(en, ru), true);
    }

    public static Builder passive(String id, String en, String ru) {
        return new Builder(id, L10n.of(en, ru), false).passive().hidden();
    }

    public static final class Builder {
        private final String id;
        private final L10n name;
        private L10n description;
        private final boolean harmful;
        private School school = School.PHYSICAL;
        private double duration;
        private int maxStacks = 1;
        private int stacksPerApply = 1;
        private boolean pandemic = true;
        private boolean refreshOnApply = true;
        private DispelType dispel = DispelType.NONE;
        private final List<Modifier> mods = new ArrayList<>();
        private boolean modsPerStack = true;
        private CcType cc;
        private double tickInterval;
        private boolean hastedTicks = true;
        private Effect tickEffect;
        private boolean tickOnApply;
        private Effect onApply, onExpire, onRemove;
        private boolean absorb;
        private boolean absorbMagicOnly;
        private final List<Trigger> triggers = new ArrayList<>();
        private double breakOnDamageFraction;
        private boolean breakOnAnyDamage;
        private boolean stealth;
        private String form;
        private boolean hidden;
        private boolean passive;
        private boolean perCaster;
        private boolean persistsThroughDeath;
        private boolean removeOutOfCombat;
        private final Set<String> tags = new HashSet<>();
        private String vfx;
        private int iconColor;

        private Builder(String id, L10n name, boolean harmful) {
            this.id = id;
            this.name = name;
            this.harmful = harmful;
            this.perCaster = harmful;
        }

        public Builder desc(String en, String ru) {
            this.description = L10n.of(en, ru);
            return this;
        }

        public Builder school(School s) {
            this.school = s;
            return this;
        }

        public Builder duration(double seconds) {
            this.duration = seconds;
            return this;
        }

        public Builder stacks(int max) {
            this.maxStacks = max;
            return this;
        }

        public Builder stacksPerApply(int n) {
            this.stacksPerApply = n;
            return this;
        }

        public Builder noPandemic() {
            this.pandemic = false;
            return this;
        }

        /** Stacks refresh duration on each application (default behaviour; kept for readability). */
        public Builder refreshOnStack() {
            this.refreshOnApply = true;
            return this;
        }

        public Builder noRefresh() {
            this.refreshOnApply = false;
            return this;
        }

        public Builder dispel(DispelType d) {
            this.dispel = d;
            return this;
        }

        public Builder mod(Modifier... ms) {
            java.util.Collections.addAll(this.mods, ms);
            return this;
        }

        public Builder mods(Modifier... ms) {
            java.util.Collections.addAll(this.mods, ms);
            return this;
        }

        public Builder flatMods() {
            this.modsPerStack = false;
            return this;
        }

        public Builder cc(CcType type) {
            this.cc = type;
            if (type != null && type.breaksOnDamage) this.breakOnDamageFraction = 0.0001;
            return this;
        }

        public Builder periodic(double interval, Effect effect) {
            this.tickInterval = interval;
            this.tickEffect = effect;
            return this;
        }

        public Builder unhastedTicks() {
            this.hastedTicks = false;
            return this;
        }

        public Builder tickOnApply() {
            this.tickOnApply = true;
            return this;
        }

        public Builder onApply(Effect e) {
            this.onApply = e;
            return this;
        }

        public Builder onExpire(Effect e) {
            this.onExpire = e;
            return this;
        }

        public Builder onRemove(Effect e) {
            this.onRemove = e;
            return this;
        }

        public Builder absorb() {
            this.absorb = true;
            return this;
        }

        public Builder absorbMagic() {
            this.absorb = true;
            this.absorbMagicOnly = true;
            return this;
        }

        public Builder trigger(Trigger t) {
            this.triggers.add(t);
            return this;
        }

        public Builder breakOnDamage(double fractionOfMaxHealth) {
            this.breakOnDamageFraction = fractionOfMaxHealth;
            return this;
        }

        public Builder breakOnAnyDamage() {
            this.breakOnAnyDamage = true;
            return this;
        }

        /** Self-applied control that never breaks on damage (Ice Block). */
        public Builder noBreakAfter() {
            this.breakOnDamageFraction = 0;
            this.breakOnAnyDamage = false;
            return this;
        }

        public Builder noBreak() {
            this.breakOnDamageFraction = 0;
            this.breakOnAnyDamage = false;
            return this;
        }

        public Builder stealth() {
            this.stealth = true;
            return this;
        }

        public Builder form(String form) {
            this.form = form;
            return this;
        }

        public Builder hidden() {
            this.hidden = true;
            return this;
        }

        public Builder passive() {
            this.passive = true;
            this.persistsThroughDeath = true;
            this.pandemic = false;
            return this;
        }

        public Builder perCaster(boolean v) {
            this.perCaster = v;
            return this;
        }

        public Builder persistent() {
            this.persistsThroughDeath = true;
            return this;
        }

        public Builder combatOnly() {
            this.removeOutOfCombat = true;
            return this;
        }

        public Builder tag(String... t) {
            java.util.Collections.addAll(this.tags, t);
            return this;
        }

        public Builder vfx(String key) {
            this.vfx = key;
            return this;
        }

        public Builder color(int argb) {
            this.iconColor = argb;
            return this;
        }

        public AuraDef build() {
            return new AuraDef(this);
        }
    }
}
