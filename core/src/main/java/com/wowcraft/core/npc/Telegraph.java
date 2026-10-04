package com.wowcraft.core.npc;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.util.Mth;
import com.wowcraft.core.util.Vec3;

/** A ground warning (circle, ring, cone, line) that resolves after a delay, hitting units inside. */
public final class Telegraph {
    public enum Shape {CIRCLE, RING, CONE, LINE}

    private static int nextId = 1;

    public final int id = nextId++;
    public final Shape shape;
    public Vec3 center;
    public final double radius;
    public final double innerRadius;
    public final double angle;
    public final double length;
    public final double width;
    public float yaw;
    public final double start;
    public final double resolveAt;
    public final int color;
    public final UnitState source;
    /** The telegraph follows this unit until it resolves (spread mechanics). */
    public UnitState follow;
    public double followUntil;
    public final Effect onHit;
    /** Soak: at least this many players must be inside, otherwise {@link #onFail} hits everyone. */
    public int soakRequired;
    public Effect onFail;
    /** Damage is split between everyone inside (stack mechanics). */
    public boolean split;
    public boolean resolved;
    public String worldKey;

    private Telegraph(Shape shape, Vec3 center, double radius, double innerRadius, double angle, double length, double width, float yaw,
                      double start, double duration, int color, UnitState source, Effect onHit) {
        this.shape = shape;
        this.center = center;
        this.radius = radius;
        this.innerRadius = innerRadius;
        this.angle = angle;
        this.length = length;
        this.width = width;
        this.yaw = yaw;
        this.start = start;
        this.resolveAt = start + duration;
        this.color = color;
        this.source = source;
        this.onHit = onHit;
        this.worldKey = source != null ? source.worldKey() : "";
    }

    public static Telegraph circle(UnitState source, Vec3 center, double radius, double now, double duration, int color, Effect onHit) {
        return new Telegraph(Shape.CIRCLE, center, radius, 0, 360, 0, 0, 0, now, duration, color, source, onHit);
    }

    public static Telegraph ring(UnitState source, Vec3 center, double inner, double outer, double now, double duration, int color, Effect onHit) {
        return new Telegraph(Shape.RING, center, outer, inner, 360, 0, 0, 0, now, duration, color, source, onHit);
    }

    public static Telegraph cone(UnitState source, Vec3 origin, float yaw, double angle, double range, double now, double duration, int color, Effect onHit) {
        return new Telegraph(Shape.CONE, origin, range, 0, angle, range, 0, yaw, now, duration, color, source, onHit);
    }

    public static Telegraph line(UnitState source, Vec3 origin, float yaw, double length, double width, double now, double duration, int color, Effect onHit) {
        return new Telegraph(Shape.LINE, origin, length, 0, 0, length, width, yaw, now, duration, color, source, onHit);
    }

    public Telegraph following(UnitState u, double until) {
        this.follow = u;
        this.followUntil = until;
        return this;
    }

    public Telegraph soak(int required, Effect onFail) {
        this.soakRequired = required;
        this.onFail = onFail;
        return this;
    }

    public Telegraph splitDamage() {
        this.split = true;
        return this;
    }

    public double progress(double now) {
        double d = resolveAt - start;
        return d <= 0 ? 1 : Mth.clamp((now - start) / d, 0, 1);
    }

    /** Whether a unit is inside the shape (uses the unit's feet position and width). */
    public boolean contains(UnitState u) {
        Vec3 p = u.position();
        double pad = u.width() / 2.0;
        if (Math.abs(p.y() - center.y()) > 6) return false;
        double dist = p.horizontalDistance(center);
        return switch (shape) {
            case CIRCLE -> dist <= radius + pad;
            case RING -> dist <= radius + pad && dist >= innerRadius - pad;
            case CONE -> dist <= radius + pad && (dist < 1.0 || Math.abs(Mth.angleDiff(center.yawTo(p), yaw)) <= angle / 2);
            case LINE -> {
                Vec3 dir = Vec3.fromYaw(yaw);
                Vec3 rel = p.sub(center).horizontal();
                double along = rel.dot(dir);
                double side = rel.sub(dir.mul(along)).length();
                yield along >= -pad && along <= length + pad && side <= width / 2 + pad;
            }
        };
    }
}
