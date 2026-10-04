package com.wowcraft.core.spell;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.util.L10n;

import java.util.function.Predicate;

/** A condition evaluated against an effect context (ability requirements, conditional effects, bot rotations). */
public final class Cond {
    public final Predicate<EffectContext> test;
    /** Error / description text. */
    public final L10n text;

    public Cond(Predicate<EffectContext> test, L10n text) {
        this.test = test;
        this.text = text;
    }

    public boolean test(EffectContext ctx) {
        return test.test(ctx);
    }

    public static Cond of(Predicate<EffectContext> p, String en, String ru) {
        return new Cond(p, L10n.of(en, ru));
    }

    public Cond not() {
        return new Cond(test.negate(), L10n.of("not " + text.en(), "не " + text.ru()));
    }

    public Cond and(Cond o) {
        return new Cond(test.and(o.test), L10n.of(text.en() + " and " + o.text.en(), text.ru() + " и " + o.text.ru()));
    }

    public Cond or(Cond o) {
        return new Cond(test.or(o.test), L10n.of(text.en() + " or " + o.text.en(), text.ru() + " или " + o.text.ru()));
    }

    // ---- common conditions ----

    public static Cond targetHealthBelow(double fraction) {
        int pct = (int) Math.round(fraction * 100);
        return of(c -> c.target != null && c.target.healthFraction() < fraction,
                "target below " + pct + "% health", "здоровье цели ниже " + pct + "%");
    }

    public static Cond targetHealthAbove(double fraction) {
        int pct = (int) Math.round(fraction * 100);
        return of(c -> c.target != null && c.target.healthFraction() > fraction,
                "target above " + pct + "% health", "здоровье цели выше " + pct + "%");
    }

    public static Cond selfHealthBelow(double fraction) {
        int pct = (int) Math.round(fraction * 100);
        return of(c -> c.caster.healthFraction() < fraction,
                "below " + pct + "% health", "ваше здоровье ниже " + pct + "%");
    }

    public static Cond hasAura(String auraId) {
        return of(c -> c.caster.auras().has(auraId), "requires " + auraId, "требуется " + auraId);
    }

    public static Cond targetHasAura(String auraId) {
        return of(c -> c.target != null && c.target.auras().hasFrom(auraId, c.caster),
                "target affected by " + auraId, "на цели действует " + auraId);
    }

    public static Cond targetHasAnyAura(String auraId) {
        return of(c -> c.target != null && c.target.auras().has(auraId),
                "target affected by " + auraId, "на цели действует " + auraId);
    }

    public static Cond auraStacks(String auraId, int min) {
        return of(c -> c.caster.auras().stacks(auraId) >= min,
                min + "+ stacks of " + auraId, "не менее " + min + " зарядов " + auraId);
    }

    public static Cond resourceAtLeast(ResourceType t, double v) {
        return of(c -> c.caster.resources().get(t) >= v, t.name.en() + " >= " + v, t.name.ru() + " >= " + v);
    }

    public static Cond resourceBelow(ResourceType t, double v) {
        return of(c -> c.caster.resources().get(t) < v, t.name.en() + " < " + v, t.name.ru() + " < " + v);
    }

    public static Cond inForm(String form) {
        return of(c -> form.equals(c.caster.auras().currentForm()), "requires " + form + " form", "требуется облик: " + form);
    }

    public static Cond stealthed() {
        return of(c -> c.caster.auras().isStealthed(), "requires stealth", "требуется незаметность");
    }

    public static Cond behindTarget() {
        return of(c -> c.target != null && c.engine.isBehind(c.caster, c.target),
                "must be behind the target", "нужно находиться за спиной цели");
    }

    public static Cond targetIsCasting() {
        return of(c -> c.target != null && c.target.isCasting(), "target is casting", "цель произносит заклинание");
    }

    public static Cond enemiesAround(double radius, int min) {
        return of(c -> c.engine.enemiesAround(c.caster, c.caster.position(), radius).size() >= min,
                min + "+ enemies nearby", "не менее " + min + " противников рядом");
    }

    /** At least {@code count} group members within 40 yd are below the health fraction (AoE healing cooldowns). */
    public static Cond alliesBelow(double fraction, int count) {
        int pct = (int) Math.round(fraction * 100);
        return of(c -> {
            int n = 0;
            for (UnitState u : c.engine.groupMembersAround(c.caster, 40)) if (u.healthFraction() < fraction) n++;
            return n >= count;
        }, count + "+ allies below " + pct + "% health", "не менее " + count + " союзников со здоровьем ниже " + pct + "%");
    }

    public static Cond hasPet() {
        return of(c -> c.caster.hasLivingPet(), "requires a pet", "требуется питомец");
    }

    public static Cond targetIsPlayer() {
        return of(c -> c.target != null && c.target.isPlayerLike(), "target is a player", "цель — игрок");
    }

    public static Cond custom(Predicate<UnitState> casterTest, String en, String ru) {
        return of(c -> casterTest.test(c.caster), en, ru);
    }
}
