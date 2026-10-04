package com.wowcraft.core.item;

import com.wowcraft.core.player.PlayerProfile;

/** Upgrading items along their track with Valorstones and Crests. */
public final class ItemUpgrades {
    private ItemUpgrades() {
    }

    public enum Result {OK, MAX_RANK, NO_TRACK, NOT_ENOUGH_VALORSTONES, NOT_ENOUGH_CRESTS}

    public static long valorCost(ItemData d) {
        return UpgradeTrack.VALORSTONES_PER_RANK * Math.max(1, Math.round(d.equipType().budget() * 2));
    }

    public static long crestCost(ItemData d) {
        UpgradeTrack t = d.track();
        if (t == null) return 0;
        // the first half of each track is upgraded with the previous tier's crests in WoW; keep it simple here
        return d.rank < t.ranks / 2 ? UpgradeTrack.CRESTS_PER_RANK / 2 : UpgradeTrack.CRESTS_PER_RANK;
    }

    public static Result check(ItemData d, PlayerProfile p) {
        UpgradeTrack t = d.track();
        if (t == null) return Result.NO_TRACK;
        if (d.rank >= t.ranks) return Result.MAX_RANK;
        if (p.currency(Currency.VALORSTONES) < valorCost(d)) return Result.NOT_ENOUGH_VALORSTONES;
        if (p.currency(t.crest) < crestCost(d)) return Result.NOT_ENOUGH_CRESTS;
        return Result.OK;
    }

    /** Upgrades the item one rank if the player can pay. */
    public static Result upgrade(ItemData d, PlayerProfile p) {
        Result r = check(d, p);
        if (r != Result.OK) return r;
        UpgradeTrack t = d.track();
        p.spend(Currency.VALORSTONES, valorCost(d));
        p.spend(t.crest, crestCost(d));
        d.rank++;
        d.ilvl = t.itemLevel(d.rank);
        d.quality = ItemQuality.forItemLevel(d.ilvl).name();
        return Result.OK;
    }
}
