package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

/**
 * A named item that can drop. Armor templates with armor "ADAPTIVE" morph into the looter's armor type
 * (personal loot only gives usable items).
 */
public record ItemTemplate(String id, L10n name, EquipType equipType, WeaponType weaponType, String armor, String primary,
                           String[] secondaries, String effectId, L10n flavor, java.util.Set<com.wowcraft.core.spec.Role> roles) {
    public static final String ADAPTIVE = "ADAPTIVE";

    public boolean suitsRole(com.wowcraft.core.spec.Role r) {
        if (roles == null || roles.isEmpty()) return true;
        if (roles.contains(r)) return true;
        return r.isDps() && (roles.contains(com.wowcraft.core.spec.Role.MELEE_DPS) || roles.contains(com.wowcraft.core.spec.Role.RANGED_DPS))
                && (roles.contains(r) || (roles.contains(com.wowcraft.core.spec.Role.MELEE_DPS) && roles.contains(com.wowcraft.core.spec.Role.RANGED_DPS)));
    }
}
