package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

import java.util.List;

/** What kind of item this is, i.e. which slot(s) it fits. */
public enum EquipType {
    HEAD("Head", "Голова", List.of(EquipSlot.HEAD)),
    NECK("Neck", "Шея", List.of(EquipSlot.NECK)),
    SHOULDER("Shoulder", "Плечи", List.of(EquipSlot.SHOULDER)),
    BACK("Back", "Спина", List.of(EquipSlot.BACK)),
    CHEST("Chest", "Грудь", List.of(EquipSlot.CHEST)),
    WRIST("Wrist", "Запястья", List.of(EquipSlot.WRIST)),
    HANDS("Hands", "Кисти рук", List.of(EquipSlot.HANDS)),
    WAIST("Waist", "Пояс", List.of(EquipSlot.WAIST)),
    LEGS("Legs", "Ноги", List.of(EquipSlot.LEGS)),
    FEET("Feet", "Ступни", List.of(EquipSlot.FEET)),
    FINGER("Finger", "Палец", List.of(EquipSlot.FINGER_1, EquipSlot.FINGER_2)),
    TRINKET("Trinket", "Аксессуар", List.of(EquipSlot.TRINKET_1, EquipSlot.TRINKET_2)),
    ONE_HAND("One-Hand", "Одноручное", List.of(EquipSlot.MAIN_HAND, EquipSlot.OFF_HAND)),
    TWO_HAND("Two-Hand", "Двуручное", List.of(EquipSlot.MAIN_HAND)),
    RANGED("Ranged", "Дальний бой", List.of(EquipSlot.MAIN_HAND)),
    OFF_HAND("Held In Off-hand", "Левая рука", List.of(EquipSlot.OFF_HAND)),
    SHIELD("Shield", "Щит", List.of(EquipSlot.OFF_HAND));

    public final L10n name;
    public final List<EquipSlot> slots;

    EquipType(String en, String ru, List<EquipSlot> slots) {
        this.name = L10n.of(en, ru);
        this.slots = slots;
    }

    public boolean isWeapon() {
        return this == ONE_HAND || this == TWO_HAND || this == RANGED;
    }

    /** Budget multiplier for stats. */
    public double budget() {
        return switch (this) {
            case TWO_HAND, RANGED -> 1.0;
            case ONE_HAND, OFF_HAND, SHIELD -> 0.5;
            default -> slots.get(0).budget;
        };
    }

    public static EquipType byName(String n) {
        for (EquipType t : values()) if (t.name().equalsIgnoreCase(n)) return t;
        return null;
    }
}
