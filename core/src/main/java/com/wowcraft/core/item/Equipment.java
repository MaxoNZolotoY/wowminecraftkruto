package com.wowcraft.core.item;

import com.wowcraft.core.combat.WeaponInfo;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.stat.StatBlock;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Everything a character wears. */
public final class Equipment {
    private final EnumMap<EquipSlot, ItemData> items = new EnumMap<>(EquipSlot.class);

    public ItemData get(EquipSlot s) {
        return items.get(s);
    }

    public void set(EquipSlot s, ItemData item) {
        if (item == null) items.remove(s);
        else items.put(s, item);
    }

    public Map<EquipSlot, ItemData> all() {
        return items;
    }

    public StatBlock totalStats(Spec spec) {
        StatBlock s = new StatBlock();
        for (ItemData d : items.values()) s.add(ItemStats.compute(d, spec));
        return s;
    }

    public WeaponInfo mainHand() {
        ItemData d = items.get(EquipSlot.MAIN_HAND);
        return d == null ? null : ItemStats.weaponInfo(d);
    }

    public WeaponInfo offHand() {
        ItemData d = items.get(EquipSlot.OFF_HAND);
        return d == null ? null : ItemStats.weaponInfo(d);
    }

    /** Tier set id -> number of equipped pieces. */
    public Map<String, Integer> setCounts() {
        Map<String, Integer> m = new HashMap<>();
        for (ItemData d : items.values()) if (d.setId != null) m.merge(d.setId, 1, Integer::sum);
        return m;
    }

    /** Average item level over all 16 slots (two-handers count twice, like WoW). */
    public double averageItemLevel() {
        double sum = 0;
        int count = 0;
        for (EquipSlot slot : EquipSlot.values()) {
            ItemData d = items.get(slot);
            if (slot == EquipSlot.OFF_HAND && d == null) {
                ItemData mh = items.get(EquipSlot.MAIN_HAND);
                if (mh != null && mh.equipType() != EquipType.ONE_HAND) {
                    sum += mh.ilvl;
                    count++;
                    continue;
                }
            }
            sum += d == null ? 0 : d.ilvl;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    public Equipment copy() {
        Equipment e = new Equipment();
        for (var en : items.entrySet()) e.items.put(en.getKey(), en.getValue().copy());
        return e;
    }
}
