package com.wowcraft.core;

import com.wowcraft.core.combat.Body;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.util.Vec3;

/** Minimal body for tests. */
public class TestBody implements Body {
    public Vec3 pos;
    public float yaw;
    public boolean moving;
    public boolean removed;
    public boolean killed;
    public Vec3 velocity = Vec3.ZERO;

    public TestBody(double x, double y, double z) {
        this.pos = new Vec3(x, y, z);
    }

    @Override
    public Vec3 position() {
        return pos;
    }

    @Override
    public float yaw() {
        return yaw;
    }

    @Override
    public double width() {
        return 0.6;
    }

    @Override
    public double height() {
        return 1.8;
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    @Override
    public String worldKey() {
        return "test";
    }

    @Override
    public void applyHealth(UnitState unit, double oldHealth, HitResult cause) {
    }

    @Override
    public void kill(UnitState unit, UnitState killer) {
        killed = true;
    }

    @Override
    public void teleport(Vec3 p) {
        pos = p;
    }

    @Override
    public void setVelocity(Vec3 v) {
        velocity = v;
    }

    @Override
    public void lookAt(Vec3 point) {
        yaw = (float) pos.yawTo(point);
    }

    @Override
    public void refreshMovement(UnitState unit) {
    }

    @Override
    public void despawn() {
        removed = true;
    }

    /** Face towards a point. */
    public TestBody facing(Vec3 p) {
        lookAt(p);
        return this;
    }
}
