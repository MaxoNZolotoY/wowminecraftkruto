package com.wowcraft.core.aura;

import com.wowcraft.core.mod.ModFilter;
import com.wowcraft.core.spell.Effect;

/**
 * A proc: when {@link #type} happens and the filter matches, with {@link #chance}, run {@link #effect}.
 * Effects run with caster = aura holder and target = the other unit of the event (or the holder).
 *
 * @param icd       internal cooldown in seconds
 * @param consume   remove one stack of the aura after triggering
 * @param threshold extra numeric parameter (e.g. health fraction for HEALTH_BELOW)
 * @param onSelf    run the effect on the holder instead of the event's other unit
 */
public record Trigger(TriggerType type, ModFilter filter, double chance, double icd, boolean consume,
                      double threshold, boolean onSelf, Effect effect) {

    public static Trigger on(TriggerType type, Effect effect) {
        return new Trigger(type, ModFilter.ANY, 1.0, 0, false, 0, false, effect);
    }

    public Trigger filter(ModFilter f) {
        return new Trigger(type, f, chance, icd, consume, threshold, onSelf, effect);
    }

    public Trigger chance(double c) {
        return new Trigger(type, filter, c, icd, consume, threshold, onSelf, effect);
    }

    public Trigger icd(double s) {
        return new Trigger(type, filter, chance, s, consume, threshold, onSelf, effect);
    }

    public Trigger consumeStack() {
        return new Trigger(type, filter, chance, icd, true, threshold, onSelf, effect);
    }

    public Trigger threshold(double t) {
        return new Trigger(type, filter, chance, icd, consume, t, onSelf, effect);
    }

    public Trigger self() {
        return new Trigger(type, filter, chance, icd, consume, threshold, true, effect);
    }
}
