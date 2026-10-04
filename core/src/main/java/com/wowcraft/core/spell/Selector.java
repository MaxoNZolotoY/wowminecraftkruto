package com.wowcraft.core.spell;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Mth;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chooses the units an effect applies to. */
public interface Selector {
    List<UnitState> select(EffectContext ctx);

    default L10n describe() {
        return L10n.of("", "");
    }

    static Selector target() {
        return ctx -> ctx.target == null ? List.of() : List.of(ctx.target);
    }

    static Selector self() {
        return ctx -> List.of(ctx.caster);
    }

    static Selector enemiesAroundCaster(double radius) {
        return named(ctx -> ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), ctx.engine.radius(ctx, radius)),
                "all enemies within " + fmt(radius) + " yd", "всем противникам в радиусе " + fmt(radius) + " м");
    }

    static Selector enemiesAroundTarget(double radius) {
        return named(ctx -> ctx.engine.enemiesAround(ctx.caster, ctx.targetPoint(), ctx.engine.radius(ctx, radius)),
                "enemies within " + fmt(radius) + " yd of the target", "противникам в радиусе " + fmt(radius) + " м от цели");
    }

    static Selector enemiesAroundTargetExcept(double radius) {
        return ctx -> {
            List<UnitState> list = new ArrayList<>(ctx.engine.enemiesAround(ctx.caster, ctx.targetPoint(), ctx.engine.radius(ctx, radius)));
            list.remove(ctx.target);
            return list;
        };
    }

    static Selector alliesAroundCaster(double radius) {
        return named(ctx -> ctx.engine.alliesAround(ctx.caster, ctx.caster.position(), ctx.engine.radius(ctx, radius)),
                "allies within " + fmt(radius) + " yd", "союзникам в радиусе " + fmt(radius) + " м");
    }

    static Selector alliesAroundTarget(double radius) {
        return named(ctx -> ctx.engine.alliesAround(ctx.caster, ctx.targetPoint(), ctx.engine.radius(ctx, radius)),
                "allies within " + fmt(radius) + " yd of the target", "союзникам в радиусе " + fmt(radius) + " м от цели");
    }

    /** The most injured allies (smart healing). */
    static Selector injuredAllies(double radius, int count) {
        return named(ctx -> {
            List<UnitState> list = new ArrayList<>(ctx.engine.alliesAround(ctx.caster, ctx.targetPoint(), ctx.engine.radius(ctx, radius)));
            list.sort(Comparator.comparingDouble(UnitState::healthFraction));
            int n = count + (int) ctx.caster.mods().sum(com.wowcraft.core.mod.ModType.EXTRA_TARGETS, com.wowcraft.core.mod.ModContext.of(ctx.ability));
            return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
        }, "up to " + count + " injured allies", "до " + count + " раненым союзникам");
    }

    static Selector randomEnemies(double radius, int count) {
        return ctx -> {
            List<UnitState> list = new ArrayList<>(ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), radius));
            java.util.Collections.shuffle(list, new java.util.Random(ctx.engine.rng().nextLong()));
            return list.size() > count ? new ArrayList<>(list.subList(0, count)) : list;
        };
    }

    /** Enemies in a frontal cone of the caster. */
    static Selector cone(double angleDeg, double range) {
        return named(ctx -> {
            List<UnitState> out = new ArrayList<>();
            Vec3 origin = ctx.caster.position();
            double yaw = ctx.caster.yaw();
            for (UnitState u : ctx.engine.enemiesAround(ctx.caster, origin, range)) {
                double diff = Math.abs(Mth.angleDiff(origin.yawTo(u.position()), yaw));
                if (diff <= angleDeg / 2 || origin.horizontalDistance(u.position()) < 1.0) out.add(u);
            }
            return out;
        }, "enemies in a " + (int) angleDeg + "° cone", "противникам в конусе " + (int) angleDeg + "°");
    }

    /** Enemies in a line in front of the caster. */
    static Selector line(double length, double width) {
        return named(ctx -> {
            List<UnitState> out = new ArrayList<>();
            Vec3 origin = ctx.caster.position();
            Vec3 dir = ctx.target != null ? ctx.target.position().sub(origin).horizontal().normalize() : Vec3.fromYaw(ctx.caster.yaw());
            if (dir.lengthSq() < 1e-6) dir = Vec3.fromYaw(ctx.caster.yaw());
            for (UnitState u : ctx.engine.enemiesAround(ctx.caster, origin, length + 2)) {
                Vec3 rel = u.position().sub(origin).horizontal();
                double along = rel.dot(dir);
                if (along < -0.5 || along > length) continue;
                double side = rel.sub(dir.mul(along)).length();
                if (side <= width / 2 + u.width() / 2) out.add(u);
            }
            return out;
        }, "enemies in a line", "противникам на линии");
    }

    /** Enemies in the ground area at the context point. */
    static Selector groundEnemies(double radius) {
        return named(ctx -> ctx.engine.enemiesAround(ctx.caster, ctx.targetPoint(), ctx.engine.radius(ctx, radius)),
                "enemies in the area", "противникам в области");
    }

    static Selector groundAllies(double radius) {
        return named(ctx -> ctx.engine.alliesAround(ctx.caster, ctx.targetPoint(), ctx.engine.radius(ctx, radius)),
                "allies in the area", "союзникам в области");
    }

    /** Group members (party / raid / team) within range of the caster, including the caster. */
    static Selector group(double radius) {
        return named(ctx -> ctx.engine.groupMembersAround(ctx.caster, radius),
                "party members within " + fmt(radius) + " yd", "членам группы в радиусе " + fmt(radius) + " м");
    }

    static Selector pets() {
        return ctx -> new ArrayList<>(ctx.caster.livingPets());
    }

    static Selector selfAndPets() {
        return ctx -> {
            List<UnitState> l = new ArrayList<>(ctx.caster.livingPets());
            l.add(0, ctx.caster);
            return l;
        };
    }

    static Selector named(Selector s, String en, String ru) {
        L10n d = L10n.of(en, ru);
        return new Selector() {
            @Override
            public List<UnitState> select(EffectContext ctx) {
                return s.select(ctx);
            }

            @Override
            public L10n describe() {
                return d;
            }
        };
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
