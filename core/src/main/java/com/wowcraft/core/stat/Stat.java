package com.wowcraft.core.stat;

import com.wowcraft.core.util.L10n;

/** Item / character stats. Ratings are converted into percentages by {@link Ratings}. */
public enum Stat {
    STRENGTH(Kind.PRIMARY, "Strength", "Сила", 0xFFC79C6E),
    AGILITY(Kind.PRIMARY, "Agility", "Ловкость", 0xFFABD473),
    INTELLECT(Kind.PRIMARY, "Intellect", "Интеллект", 0xFF69CCF0),
    STAMINA(Kind.PRIMARY, "Stamina", "Выносливость", 0xFFFFFFFF),
    CRIT(Kind.SECONDARY, "Critical Strike", "Критический удар", 0xFF1EFF00),
    HASTE(Kind.SECONDARY, "Haste", "Скорость", 0xFF1EFF00),
    MASTERY(Kind.SECONDARY, "Mastery", "Искусность", 0xFF1EFF00),
    VERSATILITY(Kind.SECONDARY, "Versatility", "Универсальность", 0xFF1EFF00),
    LEECH(Kind.TERTIARY, "Leech", "Самоисцеление", 0xFF1EFF00),
    AVOIDANCE(Kind.TERTIARY, "Avoidance", "Избегание", 0xFF1EFF00),
    SPEED(Kind.TERTIARY, "Speed", "Скорость передвижения", 0xFF1EFF00),
    ARMOR(Kind.DEFENSE, "Armor", "Броня", 0xFFFFFFFF);

    public enum Kind {PRIMARY, SECONDARY, TERTIARY, DEFENSE}

    public final Kind kind;
    public final L10n name;
    public final int color;

    Stat(Kind kind, String en, String ru, int color) {
        this.kind = kind;
        this.name = L10n.of(en, ru);
        this.color = color;
    }

    public static final Stat[] SECONDARIES = {CRIT, HASTE, MASTERY, VERSATILITY};
    public static final Stat[] TERTIARIES = {LEECH, AVOIDANCE, SPEED};
    public static final Stat[] VALUES = values();

    public static Stat byName(String name) {
        for (Stat s : VALUES) if (s.name().equalsIgnoreCase(name)) return s;
        return null;
    }
}
