package com.wowcraft.core.combat;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.util.Vec3;

/** Receives combat events (platform sync, meters, encounter scripts, Mythic+ runs...). */
public interface CombatListener {
    default void onDamage(HitResult hit) {
    }

    default void onHeal(HitResult hit) {
    }

    default void onCastStart(UnitState caster, Ability ability, UnitState target, double duration) {
    }

    default void onCastSuccess(UnitState caster, Ability ability, UnitState target) {
    }

    default void onCastFailed(UnitState caster, Ability ability, boolean interrupted) {
    }

    default void onAuraApplied(UnitState unit, AuraInstance aura) {
    }

    default void onAuraRemoved(UnitState unit, AuraInstance aura, boolean expired) {
    }

    default void onDeath(UnitState unit, UnitState killer) {
    }

    default void onCombatChanged(UnitState unit, boolean inCombat) {
    }

    /** Visual effect request: key + optional source/target/point. */
    default void onVfx(String key, UnitState source, UnitState target, Vec3 point, double param) {
    }

    /** Feedback for the unit's controller (e.g. "Not enough rage"). */
    default void onError(UnitState unit, CastResult result, Ability ability) {
    }

    default void onInterrupt(UnitState interrupter, UnitState target, Ability interrupted) {
    }

    default void onAreaCreated(GroundArea area) {
    }

    default void onAreaRemoved(GroundArea area) {
    }

    default void onResurrect(UnitState unit, UnitState by) {
    }

    default void onUnitRemoved(UnitState unit) {
    }

    default void onSummon(UnitState owner, UnitState summon) {
    }
}
