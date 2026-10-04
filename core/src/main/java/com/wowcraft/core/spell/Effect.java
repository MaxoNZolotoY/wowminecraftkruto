package com.wowcraft.core.spell;

import com.wowcraft.core.util.L10n;

/** One step of what an ability / aura tick / proc does. */
@FunctionalInterface
public interface Effect {
    void apply(EffectContext ctx);

    /** Human readable description used in tooltips. Empty = not described. */
    default String describe(DescribeContext ctx) {
        return "";
    }

    /** Wraps a lambda with a fixed description. */
    static Effect described(Effect e, String en, String ru) {
        L10n text = L10n.of(en, ru);
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                e.apply(ctx);
            }

            @Override
            public String describe(DescribeContext d) {
                return text.get(d.lang());
            }
        };
    }
}
