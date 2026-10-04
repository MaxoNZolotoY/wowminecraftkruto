package com.wowcraft.core.npc;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;

import java.util.List;

/** Services an encounter needs from the game server. */
public interface EncounterHost {
    /** Players and bots taking part (alive or dead). */
    List<UnitState> participants(Encounter e);

    UnitState spawnAdd(Encounter e, String templateId, Vec3 pos);

    void telegraph(Encounter e, Telegraph t, boolean added);

    void warn(Encounter e, L10n text, int color);

    void say(Encounter e, UnitState speaker, L10n text);

    void timersChanged(Encounter e);

    void ended(Encounter e, boolean victory);
}
