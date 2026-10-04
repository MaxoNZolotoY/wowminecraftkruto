package com.wowcraft.core.combat;

import com.wowcraft.core.spell.School;

/** Outcome of a damage or heal event. */
public final class HitResult {
    public final UnitState source;
    public final UnitState target;
    public final boolean heal;
    public final School school;
    /** Amount after mitigation, before absorbs. */
    public double amount;
    /** Part of the amount soaked by absorb shields. */
    public double absorbed;
    /** Healing above max health. */
    public double overheal;
    public boolean crit;
    public boolean periodic;
    public boolean killed;
    public boolean immune;
    /** Damage / heal coming from an environmental or vanilla source. */
    public boolean environmental;
    public String abilityId;
    public String auraId;

    public HitResult(UnitState source, UnitState target, boolean heal, School school) {
        this.source = source;
        this.target = target;
        this.heal = heal;
        this.school = school;
    }

    /** Health actually removed / restored. */
    public double effective() {
        return heal ? amount - overheal : amount - absorbed;
    }
}
