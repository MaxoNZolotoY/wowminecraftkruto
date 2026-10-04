package com.wowcraft.core.mythic;

import com.wowcraft.core.player.PlayerProfile;

import java.util.Map;

/** Mythic+ rating, similar to WoW's Mythic+ Rating / Raider.IO score. */
public final class MythicScore {
    private MythicScore() {
    }

    /**
     * Score of a single run.
     *
     * @param timeRatio elapsed / timer (below 1 = timed)
     */
    public static double runScore(int level, double timeRatio, int affixCount) {
        double base = 20 + 15.0 * level + 10.0 * Math.max(0, affixCount - 1);
        if (timeRatio <= 1.0) {
            double bonus = Math.min(1.0, (1.0 - timeRatio) / 0.4) * 7.5;
            return base + bonus;
        }
        double penalty = Math.min(1.0, (timeRatio - 1.0) / 0.4) * 15.0 + 5;
        return Math.max(0, base - penalty);
    }

    public static double totalRating(PlayerProfile p) {
        double sum = 0;
        for (Map.Entry<String, PlayerProfile.MythicBest> e : p.mythicBest.entrySet()) sum += e.getValue().score;
        return Math.round(sum * 10) / 10.0;
    }

    /** Rating color (grey -> green -> blue -> purple -> orange -> pink), like Raider.IO. */
    public static int color(double rating) {
        if (rating >= 3000) return 0xFFFF80C0;
        if (rating >= 2500) return 0xFFFF8000;
        if (rating >= 2000) return 0xFFA335EE;
        if (rating >= 1500) return 0xFF0070DD;
        if (rating >= 750) return 0xFF1EFF00;
        return 0xFFBBBBBB;
    }

    /** Keystone upgrade levels for a finish (0 = depleted / not timed). */
    public static int upgrades(double elapsed, double timer) {
        if (elapsed <= timer * 0.6) return 3;
        if (elapsed <= timer * 0.8) return 2;
        if (elapsed <= timer) return 1;
        return 0;
    }
}
