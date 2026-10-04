package com.wowcraft.core.spec;

import com.wowcraft.core.util.L10n;

public enum ArmorType {
    CLOTH("Cloth", "Ткань", 0.35),
    LEATHER("Leather", "Кожа", 0.55),
    MAIL("Mail", "Кольчуга", 0.77),
    PLATE("Plate", "Латы", 1.0),
    /** Rings, necks, cloaks, trinkets. */
    NONE("", "", 0.0);

    public final L10n name;
    /** Multiplier of armor value relative to plate. */
    public final double armorFactor;

    ArmorType(String en, String ru, double armorFactor) {
        this.name = L10n.of(en, ru);
        this.armorFactor = armorFactor;
    }
}
