package com.wowcraft.core.spell;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.util.Vec3;

/** Everything an effect needs to know while executing. Copy-on-write for target changes. */
public final class EffectContext {
    public final CombatEngine engine;
    public final UnitState caster;
    public UnitState target;
    public final UnitState primaryTarget;
    public Vec3 point;
    public final Ability ability;
    /** Aura that produced this effect (periodic ticks, procs, expiry), or null. */
    public final AuraInstance aura;
    public int comboSpent;
    public int empowerStage;
    /** Generic multiplier applied to amounts (falloff, soft caps, stacks...). */
    public double scale = 1.0;
    public boolean periodic;
    /** Number of targets hit by the current area effect (used for AoE soft caps). */
    public int aoeTargets = 1;
    /** Amount of the event that triggered a proc (damage dealt / taken, heal...). */
    public double triggerAmount;
    /** Whether the most recent damage / heal from this context crit. */
    public boolean lastCrit;
    public double lastAmount;
    /** School override (for effects whose school differs from the ability's). */
    public School schoolOverride;

    public EffectContext(CombatEngine engine, UnitState caster, UnitState target, Vec3 point, Ability ability, AuraInstance aura) {
        this.engine = engine;
        this.caster = caster;
        this.target = target;
        this.primaryTarget = target;
        this.point = point;
        this.ability = ability;
        this.aura = aura;
    }

    public EffectContext copy() {
        EffectContext c = new EffectContext(engine, caster, target, point, ability, aura);
        c.comboSpent = comboSpent;
        c.empowerStage = empowerStage;
        c.scale = scale;
        c.periodic = periodic;
        c.aoeTargets = aoeTargets;
        c.triggerAmount = triggerAmount;
        c.schoolOverride = schoolOverride;
        return c;
    }

    public EffectContext withTarget(UnitState t) {
        EffectContext c = copy();
        c.target = t;
        return c;
    }

    public EffectContext withCaster(UnitState newCaster, UnitState newTarget) {
        EffectContext c = new EffectContext(engine, newCaster, newTarget, point, ability, aura);
        c.comboSpent = comboSpent;
        c.empowerStage = empowerStage;
        c.scale = scale;
        c.periodic = periodic;
        c.triggerAmount = triggerAmount;
        c.schoolOverride = schoolOverride;
        return c;
    }

    public School school() {
        if (schoolOverride != null) return schoolOverride;
        if (ability != null) return ability.school;
        if (aura != null) return aura.def.school;
        return School.PHYSICAL;
    }

    public double now() {
        return engine.now();
    }

    public Vec3 targetPoint() {
        if (point != null) return point;
        if (target != null) return target.position();
        return caster.position();
    }
}
