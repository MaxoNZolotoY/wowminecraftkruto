package com.wowcraft.core.aura;

import com.wowcraft.core.util.L10n;

/** Crowd control categories with their effects and diminishing-returns category. */
public enum CcType {
    STUN("Stunned", "Оглушение", true, true, true, false, false, "STUN"),
    ROOT("Rooted", "Обездвиживание", true, false, false, false, false, "ROOT"),
    SILENCE("Silenced", "Немота", false, true, false, false, false, "SILENCE"),
    INCAPACITATE("Incapacitated", "Паралич", true, true, true, false, true, "INCAPACITATE"),
    DISORIENT("Disoriented", "Дезориентация", true, true, true, true, false, "DISORIENT"),
    FEAR("Feared", "Страх", true, true, true, true, false, "DISORIENT"),
    SLEEP("Asleep", "Сон", true, true, true, false, true, "INCAPACITATE"),
    DISARM("Disarmed", "Разоружение", false, false, true, false, false, "DISARM"),
    /** Slow is not a hard CC but is tracked for UI and bots. */
    SLOW("Slowed", "Замедление", false, false, false, false, false, null);

    public final L10n name;
    public final boolean preventsMovement;
    public final boolean preventsCasting;
    public final boolean preventsAttacks;
    public final boolean forcedMovement;
    public final boolean breaksOnDamage;
    /** Diminishing returns category; null = no DR. */
    public final String drCategory;

    CcType(String en, String ru, boolean move, boolean cast, boolean attack, boolean forced, boolean breaks, String dr) {
        this.name = L10n.of(en, ru);
        this.preventsMovement = move;
        this.preventsCasting = cast;
        this.preventsAttacks = attack;
        this.forcedMovement = forced;
        this.breaksOnDamage = breaks;
        this.drCategory = dr;
    }

    public boolean isHardCc() {
        return this != SLOW && this != ROOT && this != DISARM;
    }
}
