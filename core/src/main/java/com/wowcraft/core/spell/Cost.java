package com.wowcraft.core.spell;

import com.wowcraft.core.resource.ResourceType;

/**
 * Resource cost or generation. For mana, the amount is a percentage of base mana (10000),
 * e.g. 2.5 means 250 mana.
 *
 * @param upTo for spenders like finishers: spend up to this much beyond the base amount
 *             (all available combo points up to the max).
 */
public record Cost(ResourceType type, double amount, double upTo) {
    public static Cost of(ResourceType type, double amount) {
        return new Cost(type, amount, 0);
    }

    public static Cost mana(double pctOfBase) {
        return new Cost(ResourceType.MANA, pctOfBase * 100.0, 0);
    }

    /** Spend between {@code min} and {@code max} (e.g. combo points 1..5). */
    public static Cost range(ResourceType type, double min, double max) {
        return new Cost(type, min, max);
    }
}
