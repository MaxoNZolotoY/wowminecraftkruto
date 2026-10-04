package com.wowcraft.core.item;

import com.wowcraft.core.util.L10n;

/**
 * Special effect of an item (trinkets, some weapons). The magnitude scales with item level;
 * PASSIVE effects are applied as an aura whose stack count encodes the magnitude, ON_USE effects
 * read the magnitude from the unit when the ability fires.
 */
public record ItemEffect(String id, L10n name, L10n description, Kind kind, String abilityId, String auraId, double magnitudeK) {
    public enum Kind {ON_USE, PASSIVE}

    public int magnitude(int ilvl) {
        return (int) Math.max(1, Math.round(ItemStats.budget(ilvl) * magnitudeK));
    }

    /** Description with the magnitude filled in ({X}). */
    public String describe(L10n.Lang lang, int ilvl) {
        return description.get(lang).replace("{X}", String.valueOf(magnitude(ilvl)));
    }

    /** Key in UnitState.tags holding the magnitude for an equipped on-use effect. */
    public String tagKey() {
        return "item_effect:" + id;
    }
}
