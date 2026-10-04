package com.wowcraft.core.mythic;

import com.wowcraft.core.item.Currency;

/** Item level, crest and scaling tables for dungeons and Mythic+. */
public final class MythicTables {
    private MythicTables() {
    }

    public static final int NORMAL_DUNGEON_ILVL = 110;
    public static final int HEROIC_DUNGEON_ILVL = 120;
    public static final int MYTHIC_ZERO_ILVL = 130;

    /** End-of-run item level for a keystone level (0 = Mythic 0). */
    public static int endOfRunItemLevel(int level) {
        if (level <= 0) return MYTHIC_ZERO_ILVL;
        if (level <= 3) return 133;
        if (level == 4) return 136;
        if (level <= 6) return 139;
        if (level <= 8) return 143;
        if (level <= 9) return 146;
        if (level <= 11) return 146;
        return 149;
    }

    /** Great Vault item level for a keystone level. */
    public static int vaultItemLevel(int level) {
        if (level <= 0) return 133;
        if (level <= 3) return 140;
        if (level <= 5) return 143;
        if (level <= 7) return 146;
        if (level <= 9) return 149;
        if (level <= 11) return 150;
        return 153;
    }

    public static Currency crestFor(int level) {
        if (level <= 0) return Currency.CREST_CARVED;
        if (level <= 3) return Currency.CREST_CARVED;
        if (level <= 6) return Currency.CREST_RUNED;
        return Currency.CREST_GILDED;
    }

    public static int crestAmount(int level, boolean timed) {
        int base = 10 + Math.max(0, level - 7) * 2;
        return timed ? base : base / 2;
    }

    /** Enemy health / damage multiplier for a keystone level (like WoW: +7% per level to 10, +10% after). */
    public static double scaling(int level) {
        if (level <= 1) return 1.0;
        double m = 1.0;
        for (int l = 2; l <= level; l++) m *= l <= 10 ? 1.07 : 1.10;
        return m;
    }

    /** Raid difficulty item levels. */
    public static int raidItemLevel(String difficulty, int bossIndex) {
        int base = switch (difficulty) {
            case "lfr" -> 120;
            case "heroic" -> 140;
            case "mythic" -> 150;
            default -> 130;
        };
        return base + Math.min(2, bossIndex / 2) * 3;
    }
}
