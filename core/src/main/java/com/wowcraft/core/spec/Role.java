package com.wowcraft.core.spec;

import com.wowcraft.core.util.L10n;

public enum Role {
    TANK("Tank", "Танк", 0xFF3B82F6),
    HEALER("Healer", "Лекарь", 0xFF22C55E),
    MELEE_DPS("Melee DPS", "Боец ближнего боя", 0xFFEF4444),
    RANGED_DPS("Ranged DPS", "Боец дальнего боя", 0xFFF97316);

    public final L10n name;
    public final int color;

    Role(String en, String ru, int color) {
        this.name = L10n.of(en, ru);
        this.color = color;
    }

    public boolean isDps() {
        return this == MELEE_DPS || this == RANGED_DPS;
    }

    /** Group-finder role (melee and ranged are both "DPS"). */
    public Role lfgRole() {
        return isDps() ? MELEE_DPS : this;
    }
}
