package com.wowcraft.core.bot;

/** Who a rotation step should be used on. */
public enum BotTarget {
    /** The current hostile target. */
    ENEMY,
    SELF,
    /** Ally with the lowest health fraction (healers). */
    LOWEST_ALLY,
    /** The group's tank (or the unit being attacked the most). */
    TANK,
    /** An enemy that is casting an interruptible spell (interrupts). */
    CASTING_ENEMY,
    /** An ally with a dispellable harmful effect. */
    DISPEL_ALLY,
    /** A dead ally (battle resurrection). */
    DEAD_ALLY,
    /** The target's position (ground spells). */
    ENEMY_GROUND,
    /** The center of the group (ground heals). */
    ALLY_GROUND
}
