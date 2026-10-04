package com.wowcraft.core.npc;

import com.wowcraft.core.combat.Formulas;
import com.wowcraft.core.item.ItemStats;
import com.wowcraft.core.stat.Ratings;

/** Expected player power at a level, used to scale NPC health and damage. */
public final class NpcScaling {
    private NpcScaling() {
    }

    public static int expectedItemLevel(int level) {
        return level >= Formulas.MAX_LEVEL ? 125 : (int) Math.round(10 + level * 1.25);
    }

    public static double expectedPlayerHealth(int level) {
        int ilvl = expectedItemLevel(level);
        double sta = Formulas.baseStamina(level) + ItemStats.STAMINA_K * ItemStats.budget(ilvl) * 9.8;
        return sta * Ratings.HP_PER_STAMINA;
    }

    public static double expectedPlayerDps(int level) {
        int ilvl = expectedItemLevel(level);
        double primary = Formulas.basePrimary(level) + ItemStats.PRIMARY_K * ItemStats.budget(ilvl) * 9.0;
        double ap = primary + 0.72 * ilvl * 6;
        return ap * 2.4;
    }

    /** Health of an NPC. */
    public static double health(NpcTemplate t, int level, Difficulty d, double keystoneMult, boolean instanced, int groupSize) {
        double k = instanced ? 25 : 8;
        double hp = expectedPlayerDps(level) * k * t.rank.healthMult * t.healthMult * d.health * keystoneMult;
        if (d.isRaid() && t.rank == NpcRank.BOSS) hp *= Math.max(1.0, groupSize / 5.0);
        return Math.max(50, hp);
    }

    /** Damage per auto-attack swing (2.0 s reference) and spell power reference. */
    public static double attackPower(NpcTemplate t, int level, Difficulty d, double keystoneMult) {
        return expectedPlayerHealth(level) * 0.03 * t.rank.damageMult * t.damageMult * d.damage * keystoneMult;
    }

    public static double spellPower(NpcTemplate t, int level, Difficulty d, double keystoneMult) {
        return expectedPlayerHealth(level) * 0.03 * t.damageMult * d.damage * keystoneMult;
    }
}
