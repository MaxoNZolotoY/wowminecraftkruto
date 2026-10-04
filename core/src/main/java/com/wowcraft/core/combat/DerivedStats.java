package com.wowcraft.core.combat;

/** Computed combat stats of a unit. */
public final class DerivedStats {
    public double strength, agility, intellect, stamina;
    public double attackPower, spellPower;
    public double critPct, hastePct, masteryPoints, masteryPct, versPct, versDrPct;
    public double leechPct, avoidancePct, speedPct;
    public double armor;
    public double maxHealth;

    public double hasteMult() {
        return 1.0 + hastePct / 100.0;
    }
}
