package com.wowcraft.core.bot;

import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.npc.Telegraph;
import com.wowcraft.core.util.Vec3;

import java.util.List;

/** What a bot can see. */
public interface BotContext {
    CombatEngine engine();

    /** Group members (players, bots and pets) including the bot itself. */
    List<UnitState> allies(UnitState bot);

    /** Hostile units relevant to the bot (engaged NPCs in PvE, enemy team in PvP). */
    List<UnitState> enemies(UnitState bot);

    /** Unit to follow out of combat (group leader). */
    UnitState leader(UnitState bot);

    /** Active ground warnings near the bot. */
    List<Telegraph> telegraphs(UnitState bot);

    boolean pvp(UnitState bot);

    /** PvP objective to move to when idle (flag, capture node), or null. */
    Vec3 objective(UnitState bot);

    /** Index of the bot within its group (for formation offsets). */
    int formationIndex(UnitState bot);
}
