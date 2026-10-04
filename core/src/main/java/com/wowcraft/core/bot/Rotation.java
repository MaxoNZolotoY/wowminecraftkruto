package com.wowcraft.core.bot;

import com.wowcraft.core.spell.Cond;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An action priority list (APL) like SimulationCraft / Hekili: the first usable step wins.
 * Used by bots and by the "suggested next ability" highlight on the action bar.
 */
public final class Rotation {
    public final List<Step> steps;

    private Rotation(List<Step> steps) {
        this.steps = Collections.unmodifiableList(steps);
    }

    public static Rotation of(Step... steps) {
        List<Step> list = new ArrayList<>();
        Collections.addAll(list, steps);
        return new Rotation(list);
    }

    public static Step use(String abilityId) {
        return new Step(abilityId, null, BotTarget.ENEMY, 0, false);
    }

    public static Step self(String abilityId) {
        return new Step(abilityId, null, BotTarget.SELF, 0, false);
    }

    public static Step heal(String abilityId) {
        return new Step(abilityId, null, BotTarget.LOWEST_ALLY, 0, false);
    }

    public static Step interrupt(String abilityId) {
        return new Step(abilityId, null, BotTarget.CASTING_ENEMY, 0, false);
    }

    public static Step dispel(String abilityId) {
        return new Step(abilityId, null, BotTarget.DISPEL_ALLY, 0, false);
    }

    public static Step ground(String abilityId) {
        return new Step(abilityId, null, BotTarget.ENEMY_GROUND, 0, false);
    }

    /**
     * @param minHealthDeficit for heals: only consider allies below (1 - deficit) health
     * @param panic            a "panic" step that bots use before the regular priority
     */
    public record Step(String abilityId, Cond when, BotTarget target, double minHealthDeficit, boolean panic) {
        public Step when(Cond c) {
            return new Step(abilityId, when == null ? c : when.and(c), target, minHealthDeficit, panic);
        }

        public Step on(BotTarget t) {
            return new Step(abilityId, when, t, minHealthDeficit, panic);
        }

        /** Only heal allies missing at least this fraction of health. */
        public Step below(double healthFraction) {
            return new Step(abilityId, when, target, 1.0 - healthFraction, panic);
        }

        public Step urgent() {
            return new Step(abilityId, when, target, minHealthDeficit, true);
        }
    }
}
