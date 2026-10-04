package com.wowcraft.core.spell;

import com.wowcraft.core.util.L10n;

/** Damage schools. Physical damage is reduced by armor, the rest are "magic". */
public enum School {
    PHYSICAL("Physical", "Физический", 0xFFFFFF00),
    HOLY("Holy", "Свет", 0xFFFFE680),
    FIRE("Fire", "Огонь", 0xFFFF8000),
    NATURE("Nature", "Природа", 0xFF4DFF4D),
    FROST("Frost", "Лед", 0xFF80FFFF),
    SHADOW("Shadow", "Тьма", 0xFF8080FF),
    ARCANE("Arcane", "Тайная магия", 0xFFFF80FF),
    CHAOS("Chaos", "Хаос", 0xFFB040FF);

    public final L10n name;
    public final int color;

    School(String en, String ru, int color) {
        this.name = L10n.of(en, ru);
        this.color = color;
    }

    public boolean isMagic() {
        return this != PHYSICAL;
    }
}
