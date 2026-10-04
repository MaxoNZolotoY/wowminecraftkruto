package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

public enum ItemQuality {
    POOR("Poor", "Низкое", 0xFF9D9D9D, 0.5),
    COMMON("Common", "Обычное", 0xFFFFFFFF, 0.8),
    UNCOMMON("Uncommon", "Необычное", 0xFF1EFF00, 0.92),
    RARE("Rare", "Редкое", 0xFF0070DD, 1.0),
    EPIC("Epic", "Эпическое", 0xFFA335EE, 1.05),
    LEGENDARY("Legendary", "Легендарное", 0xFFFF8000, 1.15);

    public final L10n name;
    public final int color;
    public final double budget;

    ItemQuality(String en, String ru, int color, double budget) {
        this.name = L10n.of(en, ru);
        this.color = color;
        this.budget = budget;
    }

    public static ItemQuality forItemLevel(int ilvl) {
        if (ilvl >= 140) return EPIC;
        if (ilvl >= 110) return RARE;
        if (ilvl >= 40) return UNCOMMON;
        return COMMON;
    }
}
