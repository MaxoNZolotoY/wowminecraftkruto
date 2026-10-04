package com.wowcraft.core.combat;

import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.util.Vec3;

/** A persistent ground effect (Consecration, Blizzard, Death and Decay, Healing Rain, boss puddles...). */
public final class GroundArea {
    /**
     * @param affectsEnemies the tick effect runs on enemies of the caster inside the area
     * @param affectsAllies  the tick effect runs on allies of the caster inside the area
     * @param atCaster       centered on the caster instead of the target point
     * @param followsCaster  moves with the caster
     */
    public record Def(String id, double radius, double duration, double tickInterval, boolean affectsEnemies,
                      boolean affectsAllies, boolean atCaster, boolean followsCaster, Effect tickEffect, String vfx,
                      int color, boolean oncePerTick) {
        public static Def enemies(String id, double radius, double duration, double tick, Effect e) {
            return new Def(id, radius, duration, tick, true, false, false, false, e, id, 0xFFFF4040, false);
        }

        public static Def allies(String id, double radius, double duration, double tick, Effect e) {
            return new Def(id, radius, duration, tick, false, true, false, false, e, id, 0xFF40FF40, false);
        }

        public Def centeredOnCaster() {
            return new Def(id, radius, duration, tickInterval, affectsEnemies, affectsAllies, true, followsCaster, tickEffect, vfx, color, oncePerTick);
        }

        public Def following() {
            return new Def(id, radius, duration, tickInterval, affectsEnemies, affectsAllies, true, true, tickEffect, vfx, color, oncePerTick);
        }

        /** The tick effect runs once per tick (target = null, point = area center) instead of per unit. */
        public static Def scripted(String id, double radius, double duration, double tick, Effect e) {
            return new Def(id, radius, duration, tick, false, false, false, false, e, id, 0xFFFFFF40, true);
        }

        public Def color(int argb) {
            return new Def(id, radius, duration, tickInterval, affectsEnemies, affectsAllies, atCaster, followsCaster, tickEffect, vfx, argb, oncePerTick);
        }
    }

    private static int nextId = 1;

    public final int id = nextId++;
    public final Def def;
    public final UnitState caster;
    public final Ability ability;
    public Vec3 center;
    public final String worldKey;
    public final double start;
    public final double expiresAt;
    public double nextTick;
    public final double radius;

    public GroundArea(Def def, UnitState caster, Ability ability, Vec3 center, String worldKey, double now, double radius) {
        this.def = def;
        this.caster = caster;
        this.ability = ability;
        this.center = center;
        this.worldKey = worldKey;
        this.start = now;
        this.expiresAt = now + def.duration();
        this.nextTick = now;
        this.radius = radius;
    }
}
