package com.wowcraft.core.spec;

import com.wowcraft.core.util.L10n;

public enum WowClass {
    WARRIOR("Warrior", "Воин", 0xFFC69B6D, ArmorType.PLATE),
    PALADIN("Paladin", "Паладин", 0xFFF48CBA, ArmorType.PLATE),
    HUNTER("Hunter", "Охотник", 0xFFAAD372, ArmorType.MAIL),
    ROGUE("Rogue", "Разбойник", 0xFFFFF468, ArmorType.LEATHER),
    PRIEST("Priest", "Жрец", 0xFFFFFFFF, ArmorType.CLOTH),
    DEATH_KNIGHT("Death Knight", "Рыцарь смерти", 0xFFC41E3A, ArmorType.PLATE),
    SHAMAN("Shaman", "Шаман", 0xFF0070DD, ArmorType.MAIL),
    MAGE("Mage", "Маг", 0xFF3FC7EB, ArmorType.CLOTH),
    WARLOCK("Warlock", "Чернокнижник", 0xFF8788EE, ArmorType.CLOTH),
    MONK("Monk", "Монах", 0xFF00FF98, ArmorType.LEATHER),
    DRUID("Druid", "Друид", 0xFFFF7C0A, ArmorType.LEATHER),
    DEMON_HUNTER("Demon Hunter", "Охотник на демонов", 0xFFA330C9, ArmorType.LEATHER),
    EVOKER("Evoker", "Пробудитель", 0xFF33937F, ArmorType.MAIL);

    public final L10n name;
    public final int color;
    public final ArmorType armor;

    WowClass(String en, String ru, int color, ArmorType armor) {
        this.name = L10n.of(en, ru);
        this.color = color;
        this.armor = armor;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static WowClass byId(String id) {
        if (id == null) return null;
        for (WowClass c : values()) if (c.id().equalsIgnoreCase(id) || c.name().equalsIgnoreCase(id)) return c;
        return null;
    }
}
