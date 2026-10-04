package com.wowcraft.core.combat;

/** Weapon damage per second and swing speed. */
public record WeaponInfo(double dps, double speed, boolean ranged, boolean twoHanded) {
    public static final WeaponInfo FISTS = new WeaponInfo(2, 2.0, false, false);

    /** Normalized speed used by "weapon damage" abilities (like WoW). */
    public double normalizedSpeed() {
        if (ranged) return 2.8;
        return twoHanded ? 3.3 : 2.4;
    }
}
