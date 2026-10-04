package com.wowcraft.core.spell;

public enum TargetType {
    /** Requires a hostile target. */
    ENEMY,
    /** Friendly target, falls back to self. */
    FRIENDLY,
    /** Always the caster. */
    SELF,
    /** Ground location (uses crosshair point or the target's position). */
    GROUND,
    /** Any unit (friendly or hostile), falls back to self. */
    ANY,
    /** A dead friendly player (resurrection). */
    DEAD_FRIENDLY,
    /** No target needed (e.g. AoE around the caster). */
    NONE
}
