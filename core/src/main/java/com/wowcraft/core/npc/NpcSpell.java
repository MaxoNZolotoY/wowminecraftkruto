package com.wowcraft.core.npc;

import com.wowcraft.core.spell.Cond;

/**
 * An ability an NPC uses on its own timer.
 *
 * @param cooldown     seconds between uses
 * @param initialDelay seconds after the pull before the first use
 * @param target       who to use it on
 * @param when         optional condition
 */
public record NpcSpell(String abilityId, double cooldown, double initialDelay, Target target, Cond when) {
    public enum Target {CURRENT, RANDOM_PLAYER, RANDOM_RANGED, FARTHEST, SELF, LOWEST_ALLY, HEALER, GROUND_AT_TARGET}

    public static NpcSpell of(String id, double cd, double delay) {
        return new NpcSpell(id, cd, delay, Target.CURRENT, null);
    }

    public NpcSpell on(Target t) {
        return new NpcSpell(abilityId, cooldown, initialDelay, t, when);
    }

    public NpcSpell when(Cond c) {
        return new NpcSpell(abilityId, cooldown, initialDelay, target, c);
    }
}
