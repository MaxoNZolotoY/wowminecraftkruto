package com.wowcraft.core.item;

import com.wowcraft.core.spec.ArmorType;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.List;

/**
 * A concrete item (what an ItemStack carries). Plain fields so it serializes cleanly with Gson.
 * Stats are derived from item level, slot and the chosen stat pair by {@link ItemStats}.
 */
public final class ItemData {
    public static final String ADAPTIVE = "ADAPTIVE";

    public String id = "generated";
    public String name = "Item";
    public String nameRu = "Предмет";
    public String equipType;
    /** ArmorType name for armor slots, NONE otherwise. */
    public String armorType = ArmorType.NONE.name();
    public String weaponType;
    /** "ADAPTIVE" (follows spec), a Stat name, or null for no primary stat. */
    public String primary = ADAPTIVE;
    public int ilvl = 1;
    public String quality = ItemQuality.RARE.name();
    public List<String> secondaries = new ArrayList<>();
    public double split = 0.5;
    public String tertiary;
    public String setId;
    public String effectId;
    public String track;
    public int rank;
    public int sockets;
    public List<String> gems = new ArrayList<>();
    public String source;
    public String flavor;
    public String flavorRu;
    /** Minimum character level to equip. */
    public int requiredLevel = 1;
    /** PvP items get bonus item level in rated PvP. */
    public boolean pvp;
    public long seed;

    public ItemData copy() {
        ItemData d = new ItemData();
        d.id = id;
        d.name = name;
        d.nameRu = nameRu;
        d.equipType = equipType;
        d.armorType = armorType;
        d.weaponType = weaponType;
        d.primary = primary;
        d.ilvl = ilvl;
        d.quality = quality;
        d.secondaries = new ArrayList<>(secondaries);
        d.split = split;
        d.tertiary = tertiary;
        d.setId = setId;
        d.effectId = effectId;
        d.track = track;
        d.rank = rank;
        d.sockets = sockets;
        d.gems = new ArrayList<>(gems);
        d.source = source;
        d.flavor = flavor;
        d.flavorRu = flavorRu;
        d.requiredLevel = requiredLevel;
        d.pvp = pvp;
        d.seed = seed;
        return d;
    }

    public EquipType equipType() {
        return EquipType.byName(equipType);
    }

    public WeaponType weaponType() {
        return weaponType == null ? null : WeaponType.byName(weaponType);
    }

    public ArmorType armorType() {
        try {
            return ArmorType.valueOf(armorType);
        } catch (RuntimeException e) {
            return ArmorType.NONE;
        }
    }

    public ItemQuality quality() {
        try {
            return ItemQuality.valueOf(quality);
        } catch (RuntimeException e) {
            return ItemQuality.RARE;
        }
    }

    public UpgradeTrack track() {
        return track == null ? null : UpgradeTrack.byName(track);
    }

    public L10n displayName() {
        return L10n.of(name, nameRu);
    }

    public boolean canUpgrade() {
        UpgradeTrack t = track();
        return t != null && rank < t.ranks;
    }
}
