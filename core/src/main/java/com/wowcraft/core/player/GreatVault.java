package com.wowcraft.core.player;

import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.item.LootGenerator;
import com.wowcraft.core.mythic.MythicTables;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.Rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Weekly rewards: unlock slots by raiding, running Mythic+ and PvP / world content, then pick one item. */
public final class GreatVault {
    private GreatVault() {
    }

    public static final int[] MYTHIC_THRESHOLDS = {1, 4, 8};
    public static final int[] RAID_THRESHOLDS = {2, 4, 6};
    public static final int[] WORLD_THRESHOLDS = {3, 6, 9};

    /** Item level of each unlocked slot, per row: [mythic..., raid..., world...] (0 = locked). */
    public static int[][] slotLevels(PlayerProfile.VaultState v) {
        int[][] out = new int[3][3];
        List<Integer> runs = new ArrayList<>(v.mythicRuns);
        runs.sort(Comparator.reverseOrder());
        for (int i = 0; i < 3; i++) {
            if (runs.size() >= MYTHIC_THRESHOLDS[i]) out[0][i] = MythicTables.vaultItemLevel(runs.get(MYTHIC_THRESHOLDS[i] - 1));
        }
        List<Integer> raid = new ArrayList<>(v.raidKills);
        raid.sort(Comparator.reverseOrder());
        for (int i = 0; i < 3; i++) {
            if (raid.size() >= RAID_THRESHOLDS[i]) out[1][i] = raid.get(RAID_THRESHOLDS[i] - 1);
        }
        int world = v.pvpWins + v.worldActivities;
        for (int i = 0; i < 3; i++) {
            if (world >= WORLD_THRESHOLDS[i]) out[2][i] = 128 + i * 3;
        }
        return out;
    }

    /** Called at weekly reset: turns last week's progress into reward choices. */
    public static void rollRewards(PlayerProfile p, Spec spec, long newWeek, Rng rng) {
        PlayerProfile.VaultState v = p.vault;
        if (v.week == newWeek) return;
        List<ItemData> options = new ArrayList<>();
        if (v.week >= 0 && spec != null) {
            int[][] lv = slotLevels(v);
            String[] rows = {"Mythic+", "Raid", "World"};
            for (int r = 0; r < 3; r++) {
                for (int i = 0; i < 3; i++) {
                    if (lv[r][i] <= 0) continue;
                    ItemData d = LootGenerator.personalLoot(spec, "vault", lv[r][i], rng, "Great Vault (" + rows[r] + ")", "vault", 0.1);
                    options.add(d);
                }
            }
        }
        PlayerProfile.VaultState next = new PlayerProfile.VaultState();
        next.week = newWeek;
        next.pending = options;
        next.claimed = options.isEmpty();
        p.vault = next;
    }

    /** Claims one pending reward (index into pending). */
    public static ItemData claim(PlayerProfile p, int index) {
        PlayerProfile.VaultState v = p.vault;
        if (v.claimed || index < 0 || index >= v.pending.size()) return null;
        ItemData d = v.pending.get(index);
        v.claimed = true;
        v.pending = new ArrayList<>();
        return d;
    }
}
