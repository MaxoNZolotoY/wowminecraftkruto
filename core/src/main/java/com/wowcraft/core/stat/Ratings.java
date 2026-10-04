package com.wowcraft.core.stat;

/**
 * Converts secondary ratings into percentages, with WoW-like diminishing returns.
 * Values are tuned for this mod's stat budgets (see ItemBudget).
 */
public final class Ratings {
    private Ratings() {
    }

    public static final double CRIT_PER_PCT = 35.0;
    public static final double HASTE_PER_PCT = 33.0;
    public static final double MASTERY_PER_POINT = 35.0;
    public static final double VERS_PER_PCT = 40.0;
    public static final double LEECH_PER_PCT = 21.0;
    public static final double AVOIDANCE_PER_PCT = 14.0;
    public static final double SPEED_PER_PCT = 10.0;

    public static final double BASE_CRIT = 5.0;
    public static final double BASE_MASTERY_POINTS = 8.0;

    /** WoW secondary stat diminishing returns brackets (pre-DR percent -> effective percent). */
    private static final double[][] DR = {
            {30, 1.0}, {39, 0.9}, {47, 0.8}, {54, 0.7}, {66, 0.6}, {126, 0.5}
    };

    public static double diminish(double pct) {
        if (pct <= 0) return pct;
        double out = 0, prev = 0;
        for (double[] b : DR) {
            double cap = b[0];
            if (pct <= cap) {
                out += (pct - prev) * b[1];
                return out;
            }
            out += (cap - prev) * b[1];
            prev = cap;
        }
        return out;
    }

    public static double critPct(double rating) {
        return BASE_CRIT + diminish(rating / CRIT_PER_PCT);
    }

    public static double hastePct(double rating) {
        return diminish(rating / HASTE_PER_PCT);
    }

    public static double masteryPoints(double rating) {
        return BASE_MASTERY_POINTS + diminish(rating / MASTERY_PER_POINT);
    }

    public static double versPct(double rating) {
        return diminish(rating / VERS_PER_PCT);
    }

    public static double leechPct(double rating) {
        return diminish(rating / LEECH_PER_PCT);
    }

    public static double avoidancePct(double rating) {
        return diminish(rating / AVOIDANCE_PER_PCT);
    }

    public static double speedPct(double rating) {
        return diminish(rating / SPEED_PER_PCT);
    }

    /** Armor damage reduction for a defender of the given level (0..0.85). */
    public static double armorReduction(double armor, int attackerLevel) {
        double k = 400 + 85.0 * Math.max(1, attackerLevel);
        double dr = armor / (armor + k);
        return Math.max(0, Math.min(0.85, dr));
    }

    public static final int HP_PER_STAMINA = 20;
}
