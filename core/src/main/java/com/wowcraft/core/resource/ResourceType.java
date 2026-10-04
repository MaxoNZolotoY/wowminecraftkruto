package com.wowcraft.core.resource;

import com.wowcraft.core.util.L10n;

/**
 * Class resources. Regeneration values are per second; "hasted" regeneration is multiplied by (1 + haste).
 * Decay applies out of combat and drives the resource towards {@link #oocTarget}.
 */
public enum ResourceType {
    MANA("Mana", "Мана", 0xFF0070DD, 10000, false, 100, 400, false, 0, -1),
    RAGE("Rage", "Ярость", 0xFFFF2020, 100, false, 0, 0, false, 2.0, 0),
    ENERGY("Energy", "Энергия", 0xFFFFE000, 100, false, 10, 10, true, 0, -1),
    FOCUS("Focus", "Концентрация", 0xFFFF8040, 100, false, 5, 5, true, 0, -1),
    RUNIC_POWER("Runic Power", "Сила рун", 0xFF00D1FF, 100, false, 0, 0, false, 2.0, 0),
    RUNES("Runes", "Руны", 0xFFB0B0D0, 6, true, 0, 0, true, 0, -1),
    COMBO_POINTS("Combo Points", "Приемы серии", 0xFFFFF569, 5, true, 0, 0, false, 0.5, 0),
    HOLY_POWER("Holy Power", "Энергия Света", 0xFFF2E699, 5, true, 0, 0, false, 0.4, 0),
    SOUL_SHARDS("Soul Shards", "Осколки души", 0xFF9482C9, 5, true, 0, 0.1, false, 0, 3),
    MAELSTROM("Maelstrom", "Энергия водоворота", 0xFF0080FF, 100, false, 0, 0, false, 3.0, 0),
    INSANITY("Insanity", "Безумие", 0xFF9900FF, 100, false, 0, 0, false, 3.0, 0),
    FURY("Fury", "Гнев", 0xFFC942FD, 100, false, 0, 0, false, 3.0, 0),
    ASTRAL_POWER("Astral Power", "Астральная сила", 0xFF4D85E6, 100, false, 0, 0, false, 2.0, 0),
    CHI("Chi", "Ци", 0xFF71FFE1, 5, true, 0, 0, false, 0.5, 0),
    ESSENCE("Essence", "Сущность", 0xFF3BC5A0, 5, true, 0.2, 0.5, true, 0, -1),
    ARCANE_CHARGES("Arcane Charges", "Заряды тайной магии", 0xFF6A5ACD, 4, true, 0, 0, false, 0.5, 0);

    public final L10n name;
    public final int color;
    public final double defaultMax;
    /** Shown as discrete pips instead of a bar. */
    public final boolean pips;
    public final double regenInCombat;
    public final double regenOutOfCombat;
    public final boolean hasted;
    public final double decayOutOfCombat;
    /** Value that out-of-combat decay/regen converges to; -1 = regenerates to max. */
    public final double oocTarget;

    ResourceType(String en, String ru, int color, double max, boolean pips, double regenIc, double regenOoc,
                 boolean hasted, double decay, double oocTarget) {
        this.name = L10n.of(en, ru);
        this.color = color;
        this.defaultMax = max;
        this.pips = pips;
        this.regenInCombat = regenIc;
        this.regenOutOfCombat = regenOoc;
        this.hasted = hasted;
        this.decayOutOfCombat = decay;
        this.oocTarget = oocTarget;
    }

    /** Resources that start empty when a unit is created / resets. */
    public boolean startsEmpty() {
        return oocTarget == 0;
    }

    public static ResourceType byName(String n) {
        for (ResourceType r : values()) if (r.name().equalsIgnoreCase(n)) return r;
        return null;
    }
}
