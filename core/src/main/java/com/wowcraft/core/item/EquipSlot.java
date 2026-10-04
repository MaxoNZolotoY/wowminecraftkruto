package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

/** Character equipment slots (like the WoW character sheet). */
public enum EquipSlot {
    HEAD("Head", "Голова", 1.0, true),
    NECK("Neck", "Шея", 0.5625, false),
    SHOULDER("Shoulder", "Плечи", 0.75, true),
    BACK("Back", "Спина", 0.5625, false),
    CHEST("Chest", "Грудь", 1.0, true),
    WRIST("Wrist", "Запястья", 0.5625, true),
    HANDS("Hands", "Кисти рук", 0.75, true),
    WAIST("Waist", "Пояс", 0.75, true),
    LEGS("Legs", "Ноги", 1.0, true),
    FEET("Feet", "Ступни", 0.75, true),
    FINGER_1("Finger", "Палец", 0.5625, false),
    FINGER_2("Finger", "Палец", 0.5625, false),
    TRINKET_1("Trinket", "Аксессуар", 0.5, false),
    TRINKET_2("Trinket", "Аксессуар", 0.5, false),
    MAIN_HAND("Main Hand", "Правая рука", 1.0, false),
    OFF_HAND("Off Hand", "Левая рука", 0.5, false);

    public final L10n name;
    /** Stat budget multiplier. */
    public final double budget;
    /** Uses the class armor type (cloth/leather/mail/plate). */
    public final boolean armorSlot;

    EquipSlot(String en, String ru, double budget, boolean armorSlot) {
        this.name = L10n.of(en, ru);
        this.budget = budget;
        this.armorSlot = armorSlot;
    }

    public boolean isJewelry() {
        return this == NECK || this == FINGER_1 || this == FINGER_2;
    }

    public boolean isTrinket() {
        return this == TRINKET_1 || this == TRINKET_2;
    }

    /** Slots handled by vanilla Minecraft equipment (rendered on the player model). */
    public boolean isVanillaSlot() {
        return this == HEAD || this == CHEST || this == LEGS || this == FEET || this == MAIN_HAND || this == OFF_HAND;
    }

    public static EquipSlot byName(String n) {
        for (EquipSlot s : values()) if (s.name().equalsIgnoreCase(n)) return s;
        return null;
    }
}
