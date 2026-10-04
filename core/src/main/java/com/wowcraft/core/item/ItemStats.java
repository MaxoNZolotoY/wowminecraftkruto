package com.wowcraft.core.item;

import com.wowcraft.core.combat.WeaponInfo;
import com.wowcraft.core.spec.ArmorType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.stat.StatBlock;

/** Item stat budgets: everything derives from item level (budget grows with ilvl^1.5). */
public final class ItemStats {
    private ItemStats() {
    }

    /** Per budget unit (ilvl^1.5); tuned so a full ilvl 140 set gives ~900 primary / ~1500 stamina. */
    public static final double PRIMARY_K = 0.0555;
    public static final double STAMINA_K = 0.0924;
    public static final double SECONDARY_K = 0.1044;
    public static final double ARMOR_PER_ILVL = 4.35;

    public static double budget(int ilvl) {
        return Math.pow(Math.max(1, ilvl), 1.5);
    }

    /** Stats an item gives to a character of the given spec. */
    public static StatBlock compute(ItemData item, Spec spec) {
        StatBlock s = new StatBlock();
        EquipType et = item.equipType();
        if (et == null) return s;
        double b = budget(item.ilvl) * et.budget() * item.quality().budget;
        boolean jewelry = et == EquipType.NECK || et == EquipType.FINGER;
        boolean trinket = et == EquipType.TRINKET;
        // primary
        if (!jewelry && item.primary != null) {
            Stat p = ItemData.ADAPTIVE.equals(item.primary) ? (spec != null ? spec.primaryStat : Stat.STRENGTH) : Stat.byName(item.primary);
            if (p != null) s.add(p, Math.round(b * PRIMARY_K * (trinket ? 0.8 : 1.0)));
        }
        // stamina
        if (!trinket) s.add(Stat.STAMINA, Math.round(b * STAMINA_K));
        // secondaries
        if (!item.secondaries.isEmpty()) {
            double total = b * SECONDARY_K * (jewelry ? 1.75 : 1.0) * (trinket ? 0.0 : 1.0);
            if (item.secondaries.size() == 1) {
                Stat a = Stat.byName(item.secondaries.get(0));
                if (a != null) s.add(a, Math.round(total));
            } else {
                Stat a = Stat.byName(item.secondaries.get(0));
                Stat c = Stat.byName(item.secondaries.get(1));
                if (a != null) s.add(a, Math.round(total * item.split));
                if (c != null) s.add(c, Math.round(total * (1 - item.split)));
            }
        }
        if (item.tertiary != null) {
            Stat t = Stat.byName(item.tertiary);
            if (t != null) s.add(t, Math.round(b * SECONDARY_K * 0.25));
        }
        // armor
        ArmorType at = item.armorType();
        if (et == EquipType.SHIELD) {
            s.add(Stat.ARMOR, Math.round(ARMOR_PER_ILVL * item.ilvl * 1.5));
        } else if (et == EquipType.BACK) {
            s.add(Stat.ARMOR, Math.round(ARMOR_PER_ILVL * item.ilvl * EquipSlot.BACK.budget * ArmorType.CLOTH.armorFactor));
        } else if (at != ArmorType.NONE && et.slots.get(0).armorSlot) {
            s.add(Stat.ARMOR, Math.round(ARMOR_PER_ILVL * item.ilvl * et.slots.get(0).budget * at.armorFactor));
        }
        // gems
        for (String g : item.gems) {
            Gem gem = Gem.byId(g);
            if (gem != null) s.add(gem.stat, gem.amount);
        }
        return s;
    }

    /** Weapon damage per second for a weapon item. */
    public static double weaponDps(ItemData item) {
        WeaponType wt = item.weaponType();
        if (wt == null || !wt.dealsDamage()) return 0;
        double k = switch (wt.equipType) {
            case TWO_HAND, RANGED -> 0.72;
            default -> wt == WeaponType.WAND ? 0.45 : 0.55;
        };
        return Math.round(item.ilvl * k * item.quality().budget * 10) / 10.0;
    }

    public static WeaponInfo weaponInfo(ItemData item) {
        WeaponType wt = item.weaponType();
        if (wt == null || !wt.dealsDamage()) return null;
        return new WeaponInfo(weaponDps(item), wt.speed, wt.ranged(), wt.equipType != EquipType.ONE_HAND);
    }

    /** Armor type a class wears, so personal loot / templates can adapt. */
    public static ArmorType armorFor(Spec spec) {
        return spec.wowClass.armor;
    }
}
