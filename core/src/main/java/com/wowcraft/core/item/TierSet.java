package com.wowcraft.core.item;

import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.util.L10n;

import java.util.List;

/** Class tier set with 2-piece and 4-piece bonuses. */
public record TierSet(String id, WowClass wowClass, L10n name, L10n[] pieceNames, List<Modifier> bonus2, List<Modifier> bonus4,
                      String aura2, String aura4, L10n desc2, L10n desc4) {
    public static final EquipType[] PIECES = {EquipType.HEAD, EquipType.SHOULDER, EquipType.CHEST, EquipType.HANDS, EquipType.LEGS};
}
