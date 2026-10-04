package com.wowcraft.core.combat;

import com.wowcraft.core.spell.Scaling;

/** Shared numeric formulas (base amounts, levels, XP). */
public final class Formulas {
    private Formulas() {
    }

    public static final int MAX_LEVEL = 80;
    public static final double CRIT_MULTIPLIER = 2.0;
    /** Damage dealt by player-like units to other player-like units is scaled down to lengthen PvP fights. */
    public static final double PVP_DAMAGE_MULT = 0.55;
    public static final double PVP_HEALING_MULT = 0.75;
    public static final double LEASH_RANGE = 48.0;
    public static final double COMBAT_TIMEOUT = 5.0;
    public static final double MIN_GCD = 0.75;
    public static final double AOE_SOFT_CAP = 8;

    /** Base (pre-modifier) amount of a scaling for a caster. */
    public static double base(UnitState caster, UnitState target, Scaling s, int comboSpent) {
        DerivedStats d = caster.stats();
        double v = s.flat();
        v += s.ap() * d.attackPower;
        v += s.sp() * d.spellPower;
        if (s.weapon() != 0) v += s.weapon() * weaponDamage(caster, caster.mainHand, true);
        v += s.maxHealth() * caster.maxHealth();
        if (target != null) v += s.targetMaxHealth() * target.maxHealth();
        if (s.comboScaled()) v *= Math.max(1, comboSpent);
        return v;
    }

    /** Damage of one weapon hit: (dps + AP/6) * speed. */
    public static double weaponDamage(UnitState u, WeaponInfo w, boolean normalized) {
        if (w == null) w = WeaponInfo.FISTS;
        double speed = normalized ? w.normalizedSpeed() : w.speed();
        double ap = u.stats().attackPower;
        return (w.dps() + ap / 6.0) * speed;
    }

    /** Base primary stat of a player at a level (no gear). */
    public static double basePrimary(int level) {
        return 10 + level * 2.0;
    }

    public static double baseStamina(int level) {
        return 12 + level * 3.0;
    }

    /** Experience required to go from level to level + 1. */
    public static long xpToNext(int level) {
        if (level >= MAX_LEVEL) return 0;
        return Math.round(400 + 120.0 * level + 9.0 * level * level);
    }

    /** Experience for killing a unit of the given level. */
    public static long killXp(int killerLevel, int victimLevel, boolean elite, boolean boss) {
        int diff = victimLevel - killerLevel;
        if (diff < -10) return 0;
        double base = 25 + victimLevel * 4.5;
        base *= 1.0 + Math.max(-0.8, Math.min(0.5, diff * 0.06));
        if (elite) base *= 2.5;
        if (boss) base *= 12;
        return Math.max(1, Math.round(base));
    }

    /** "Typical" ability damage at a level, used to scale vanilla mobs. */
    public static double typicalHit(int level) {
        return 18 + level * 9.0;
    }

    /** Health given to vanilla Minecraft mobs that enter WoW combat. */
    public static double vanillaMobHealth(double minecraftMaxHealth, int level, double scale) {
        return Math.max(10, minecraftMaxHealth * 0.18 * typicalHit(level) * scale);
    }

    /** Converts a fraction of Minecraft health damage into WoW-style damage on a player. */
    public static double vanillaDamageToWow(double mcAmount, double mcMax, double wowMax) {
        return mcMax <= 0 ? 0 : mcAmount / mcMax * wowMax;
    }
}
