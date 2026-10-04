package com.wowcraft.core.spell;

/**
 * How much an effect deals / heals.
 *
 * @param ap        coefficient of attack power
 * @param sp        coefficient of spell power
 * @param weapon    fraction of (normalized) weapon damage
 * @param flat      flat amount
 * @param maxHealth fraction of the caster's maximum health
 * @param comboScaled multiply by combo points (or holy power etc.) spent
 * @param targetMaxHealth fraction of the target's maximum health (used by boss mechanics)
 */
public record Scaling(double ap, double sp, double weapon, double flat, double maxHealth, boolean comboScaled,
                      double targetMaxHealth) {
    public static Scaling ap(double c) {
        return new Scaling(c, 0, 0, 0, 0, false, 0);
    }

    public static Scaling sp(double c) {
        return new Scaling(0, c, 0, 0, 0, false, 0);
    }

    public static Scaling weapon(double fraction) {
        return new Scaling(0, 0, fraction, 0, 0, false, 0);
    }

    public static Scaling flat(double v) {
        return new Scaling(0, 0, 0, v, 0, false, 0);
    }

    public static Scaling hp(double fraction) {
        return new Scaling(0, 0, 0, 0, fraction, false, 0);
    }

    public static Scaling targetHp(double fraction) {
        return new Scaling(0, 0, 0, 0, 0, false, fraction);
    }

    public Scaling perCombo() {
        return new Scaling(ap, sp, weapon, flat, maxHealth, true, targetMaxHealth);
    }

    public Scaling plusFlat(double v) {
        return new Scaling(ap, sp, weapon, flat + v, maxHealth, comboScaled, targetMaxHealth);
    }

    public Scaling times(double f) {
        return new Scaling(ap * f, sp * f, weapon * f, flat * f, maxHealth * f, comboScaled, targetMaxHealth * f);
    }
}
