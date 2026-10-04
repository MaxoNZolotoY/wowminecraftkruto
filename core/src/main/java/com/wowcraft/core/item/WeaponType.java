package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

public enum WeaponType {
    SWORD_1H("One-Handed Sword", "Одноручный меч", EquipType.ONE_HAND, 2.6),
    SWORD_2H("Two-Handed Sword", "Двуручный меч", EquipType.TWO_HAND, 3.6),
    AXE_1H("One-Handed Axe", "Одноручный топор", EquipType.ONE_HAND, 2.6),
    AXE_2H("Two-Handed Axe", "Двуручный топор", EquipType.TWO_HAND, 3.6),
    MACE_1H("One-Handed Mace", "Одноручное дробящее", EquipType.ONE_HAND, 2.6),
    MACE_2H("Two-Handed Mace", "Двуручное дробящее", EquipType.TWO_HAND, 3.6),
    DAGGER("Dagger", "Кинжал", EquipType.ONE_HAND, 1.8),
    FIST("Fist Weapon", "Кистевое оружие", EquipType.ONE_HAND, 2.6),
    WARGLAIVE("Warglaive", "Боевой клинок", EquipType.ONE_HAND, 2.6),
    POLEARM("Polearm", "Древковое оружие", EquipType.TWO_HAND, 3.6),
    STAFF("Staff", "Посох", EquipType.TWO_HAND, 3.6),
    BOW("Bow", "Лук", EquipType.RANGED, 3.0),
    GUN("Gun", "Огнестрельное оружие", EquipType.RANGED, 3.0),
    WAND("Wand", "Жезл", EquipType.ONE_HAND, 2.0),
    SHIELD("Shield", "Щит", EquipType.SHIELD, 0),
    OFF_HAND_HELD("Held In Off-hand", "Предмет для левой руки", EquipType.OFF_HAND, 0);

    public final L10n name;
    public final EquipType equipType;
    public final double speed;

    WeaponType(String en, String ru, EquipType equipType, double speed) {
        this.name = L10n.of(en, ru);
        this.equipType = equipType;
        this.speed = speed;
    }

    public boolean ranged() {
        return equipType == EquipType.RANGED;
    }

    public boolean dealsDamage() {
        return speed > 0;
    }

    public static WeaponType byName(String n) {
        for (WeaponType t : values()) if (t.name().equalsIgnoreCase(n)) return t;
        return null;
    }
}
