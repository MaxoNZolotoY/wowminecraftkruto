package com.wowcraft.core.mythic;

import java.util.ArrayList;
import java.util.List;

/**
 * Weekly affix rotation:
 * +2 Fortified / Tyrannical (alternating), +4 rotating affix, +7 rotating affix + Challenger's Peril,
 * +10 both Fortified and Tyrannical, +12 seasonal Xal'atath's Bargain.
 */
public final class AffixSchedule {
    private AffixSchedule() {
    }

    private static final Affix[] TIER_4 = {Affix.BOLSTERING, Affix.RAGING, Affix.SANGUINE, Affix.BURSTING, Affix.SPITEFUL, Affix.AFFLICTED,
            Affix.INCORPOREAL, Affix.ENTANGLING, Affix.BOLSTERING, Affix.SANGUINE};
    private static final Affix[] TIER_7 = {Affix.VOLCANIC, Affix.STORMING, Affix.QUAKING, Affix.EXPLOSIVE, Affix.NECROTIC, Affix.GRIEVOUS,
            Affix.VOLCANIC, Affix.STORMING, Affix.EXPLOSIVE, Affix.QUAKING};
    private static final Affix[] SEASONAL = {Affix.VOID_PULSAR, Affix.VOID_ASCENDANT};

    /** All affixes active for a keystone level in a given week. */
    public static List<Affix> affixes(long week, int level) {
        List<Affix> out = new ArrayList<>();
        int w = (int) Math.floorMod(week, 10);
        boolean fortWeek = w % 2 == 0;
        if (level >= 2) out.add(fortWeek ? Affix.FORTIFIED : Affix.TYRANNICAL);
        if (level >= 4) out.add(TIER_4[w]);
        if (level >= 7) {
            out.add(TIER_7[w]);
            out.add(Affix.CHALLENGERS_PERIL);
        }
        if (level >= 10) out.add(fortWeek ? Affix.TYRANNICAL : Affix.FORTIFIED);
        if (level >= 12) out.add(SEASONAL[(int) Math.floorMod(week, 2)]);
        return out;
    }

    /** The headline affixes of the week (shown in the group finder). */
    public static List<Affix> weekly(long week) {
        return affixes(week, 12);
    }
}
