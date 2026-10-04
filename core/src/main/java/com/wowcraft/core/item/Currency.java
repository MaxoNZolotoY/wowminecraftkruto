package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

public enum Currency {
    GOLD("Gold", "Золото", 0xFFFFD700, Long.MAX_VALUE),
    VALORSTONES("Valorstones", "Камни доблести", 0xFF4FC3F7, 2000),
    CREST_WEATHERED("Weathered Crest", "Выветренный герб", 0xFF1EFF00, 999),
    CREST_CARVED("Carved Crest", "Резной герб", 0xFF0070DD, 999),
    CREST_RUNED("Runed Crest", "Рунический герб", 0xFFA335EE, 999),
    CREST_GILDED("Gilded Crest", "Позолоченный герб", 0xFFFF8000, 999),
    HONOR("Honor", "Честь", 0xFFE06666, 15000),
    CONQUEST("Conquest", "Завоевание", 0xFFFF8000, 5000),
    RESTORED_COFFER_KEY("Coffer Key", "Ключ от сундука", 0xFFC0A060, 99);

    public final L10n name;
    public final int color;
    public final long cap;

    Currency(String en, String ru, int color, long cap) {
        this.name = L10n.of(en, ru);
        this.color = color;
        this.cap = cap;
    }

    /** Gold is stored in copper. */
    public static String formatGold(long copper) {
        long g = copper / 10000, s = (copper / 100) % 100, c = copper % 100;
        StringBuilder sb = new StringBuilder();
        if (g > 0) sb.append(g).append("g ");
        if (g > 0 || s > 0) sb.append(s).append("s ");
        sb.append(c).append("c");
        return sb.toString();
    }

    public static Currency byName(String n) {
        for (Currency c : values()) if (c.name().equalsIgnoreCase(n)) return c;
        return null;
    }
}
