package com.wowcraft.core.aura;

/** Events auras can react to (procs). */
public enum TriggerType {
    /** Holder dealt damage (direct or periodic). */
    DAMAGE_DEALT,
    /** Holder dealt a critical hit. */
    CRIT_DEALT,
    /** Holder healed someone. */
    HEAL_DEALT,
    /** Holder took damage. */
    DAMAGE_TAKEN,
    /** Holder finished casting an ability. */
    CAST,
    /** Holder killed a unit. */
    KILL,
    /** Holder landed an auto-attack. */
    AUTO_ATTACK,
    /** Holder's health dropped below a threshold (value in trigger.threshold). */
    HEALTH_BELOW,
    /** Holder would die (cheat-death effects). */
    LETHAL_DAMAGE
}
