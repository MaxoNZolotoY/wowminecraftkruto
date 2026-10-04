package com.wowcraft.core.npc;

import com.wowcraft.core.util.L10n;

public enum NpcRank {
    MINION("Minion", "Прислужник", 0.35, 0.6, 0),
    NORMAL("", "", 1.0, 1.0, 1),
    ELITE("Elite", "Элитный", 2.4, 1.5, 3),
    RARE("Rare", "Редкий", 4.0, 1.6, 0),
    MINIBOSS("Lieutenant", "Лейтенант", 7.0, 2.0, 8),
    BOSS("Boss", "Босс", 32.0, 2.6, 0),
    PET("", "", 1.0, 1.0, 0),
    TOTEM("", "", 0.05, 0, 0);

    public final L10n label;
    public final double healthMult;
    public final double damageMult;
    /** Default Mythic+ enemy forces value. */
    public final double forces;

    NpcRank(String en, String ru, double hp, double dmg, double forces) {
        this.label = L10n.of(en, ru);
        this.healthMult = hp;
        this.damageMult = dmg;
        this.forces = forces;
    }

    public boolean isBossLike() {
        return this == BOSS;
    }
}
