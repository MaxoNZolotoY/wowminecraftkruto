package com.wowcraft.core.npc;

import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.UnitState;

import java.util.List;

/** World knowledge NPC brains need. */
public interface NpcContext {
    CombatEngine engine();

    /** Units the NPC could notice and aggro (players, bots, their pets). */
    List<UnitState> aggroCandidates(UnitState npc, double radius);

    /** Enemies this NPC is fighting (for random-target spells). */
    List<UnitState> enemiesInCombat(NpcBrain brain);

    /** Most injured ally of an NPC (healer AI). */
    UnitState lowestAlly(NpcBrain brain, double range);

    boolean isValidTarget(NpcBrain brain, UnitState target);

    void onPull(NpcBrain brain);

    void onEvade(NpcBrain brain);
}
