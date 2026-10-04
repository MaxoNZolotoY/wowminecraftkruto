package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

/** Item upgrade tracks (like WoW Dragonflight / The War Within). */
public enum UpgradeTrack {
    EXPLORER("Explorer", "Исследователь", 100, 8, Currency.CREST_WEATHERED),
    ADVENTURER("Adventurer", "Искатель приключений", 110, 8, Currency.CREST_WEATHERED),
    VETERAN("Veteran", "Ветеран", 120, 8, Currency.CREST_CARVED),
    CHAMPION("Champion", "Защитник", 130, 8, Currency.CREST_RUNED),
    HERO("Hero", "Герой", 140, 6, Currency.CREST_GILDED),
    MYTH("Myth", "Легенда", 150, 6, Currency.CREST_GILDED);

    public static final int ILVL_PER_RANK = 3;
    public static final int VALORSTONES_PER_RANK = 15;
    public static final int CRESTS_PER_RANK = 15;

    public final L10n name;
    public final int baseItemLevel;
    public final int ranks;
    public final Currency crest;

    UpgradeTrack(String en, String ru, int base, int ranks, Currency crest) {
        this.name = L10n.of(en, ru);
        this.baseItemLevel = base;
        this.ranks = ranks;
        this.crest = crest;
    }

    /** Item level at a rank (1-based). */
    public int itemLevel(int rank) {
        return baseItemLevel + (Math.max(1, Math.min(ranks, rank)) - 1) * ILVL_PER_RANK;
    }

    public int maxItemLevel() {
        return itemLevel(ranks);
    }

    /** Track + rank for a dropped item level (best fit). */
    public static UpgradeTrack forItemLevel(int ilvl) {
        UpgradeTrack best = EXPLORER;
        for (UpgradeTrack t : values()) if (ilvl >= t.baseItemLevel) best = t;
        return best;
    }

    public int rankFor(int ilvl) {
        return Math.max(1, Math.min(ranks, 1 + (ilvl - baseItemLevel) / ILVL_PER_RANK));
    }

    public static UpgradeTrack byName(String n) {
        for (UpgradeTrack t : values()) if (t.name().equalsIgnoreCase(n)) return t;
        return null;
    }
}
