package com.wowcraft.core.combat;

/** Friend / foe policy. The game server swaps in PvP-aware rules. */
public interface Hostility {
    boolean hostile(UnitState a, UnitState b);

    default boolean friendly(UnitState a, UnitState b) {
        return !hostile(a, b) && sameSide(a, b);
    }

    default boolean sameSide(UnitState a, UnitState b) {
        UnitState ma = a.master(), mb = b.master();
        return ma.team != null && ma.team.equals(mb.team);
    }

    /** Default: different teams fight, "friendly" never fights. */
    Hostility TEAMS = new Hostility() {
        @Override
        public boolean hostile(UnitState a, UnitState b) {
            if (a == b) return false;
            UnitState ma = a.master(), mb = b.master();
            if (ma == mb) return false;
            if ("friendly".equals(ma.team) || "friendly".equals(mb.team)) return false;
            return ma.team == null || !ma.team.equals(mb.team);
        }
    };
}
