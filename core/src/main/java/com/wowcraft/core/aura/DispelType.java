package com.wowcraft.core.aura;

import com.wowcraft.core.util.L10n;

public enum DispelType {
    NONE("", "", 0xFF000000),
    MAGIC("Magic", "Магия", 0xFF3399FF),
    CURSE("Curse", "Проклятие", 0xFF9900FF),
    POISON("Poison", "Яд", 0xFF009900),
    DISEASE("Disease", "Болезнь", 0xFF996600),
    ENRAGE("Enrage", "Исступление", 0xFFFF3300),
    BLEED("Bleed", "Кровотечение", 0xFFAA0000);

    public final L10n name;
    public final int color;

    DispelType(String en, String ru, int color) {
        this.name = L10n.of(en, ru);
        this.color = color;
    }
}
