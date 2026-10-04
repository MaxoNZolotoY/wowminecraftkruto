package com.wowcraft.core.spec;

/** How a specialization's Mastery stat is applied by the combat engine. */
public enum MasteryKind {
    /** Increases damage of abilities carrying the mastery tag. */
    TAGGED_DAMAGE,
    /** Increases all damage done. */
    ALL_DAMAGE,
    /** Increases all healing done. */
    ALL_HEALING,
    /** Healing is more effective on injured targets (scales with missing health). */
    LOW_HEALTH_HEALING,
    /** Increases absorb shields. */
    ABSORBS,
    /** Reduces damage taken and increases attack power (tank). */
    DEFENSIVE,
    /** Increases pet / guardian damage. */
    PET_DAMAGE
}
