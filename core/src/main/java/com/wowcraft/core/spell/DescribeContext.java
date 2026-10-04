package com.wowcraft.core.spell;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.util.L10n;

/** Context for generating tooltips: language and (optionally) the unit whose stats fill in numbers. */
public record DescribeContext(L10n.Lang lang, UnitState unit, Ability ability) {
    public String t(String en, String ru) {
        return lang == L10n.Lang.RU ? ru : en;
    }
}
