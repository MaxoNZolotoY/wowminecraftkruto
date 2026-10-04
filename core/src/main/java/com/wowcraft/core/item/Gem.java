package com.wowcraft.core.item;

import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.util.L10n;

/** Socket gems. */
public enum Gem {
    MASTERFUL_RUBY("masterful_ruby", "Masterful Ruby", "Рубин искусности", Stat.MASTERY, 70, 0xFFE0115F),
    QUICK_SAPPHIRE("quick_sapphire", "Quick Sapphire", "Быстрый сапфир", Stat.HASTE, 70, 0xFF0F52BA),
    DEADLY_EMERALD("deadly_emerald", "Deadly Emerald", "Смертоносный изумруд", Stat.CRIT, 70, 0xFF50C878),
    VERSATILE_AMBER("versatile_amber", "Versatile Amber", "Универсальный янтарь", Stat.VERSATILITY, 70, 0xFFFFBF00),
    STALWART_ONYX("stalwart_onyx", "Stalwart Onyx", "Стойкий оникс", Stat.STAMINA, 90, 0xFF353839),
    ELUSIVE_OPAL("elusive_opal", "Elusive Opal", "Неуловимый опал", Stat.AVOIDANCE, 60, 0xFFA8C3BC);

    public final String id;
    public final L10n name;
    public final Stat stat;
    public final int amount;
    public final int color;

    Gem(String id, String en, String ru, Stat stat, int amount, int color) {
        this.id = id;
        this.name = L10n.of(en, ru);
        this.stat = stat;
        this.amount = amount;
        this.color = color;
    }

    public static Gem byId(String id) {
        for (Gem g : values()) if (g.id.equals(id)) return g;
        return null;
    }
}
