package com.wowcraft.core.game;

import com.wowcraft.core.combat.Body;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.util.Vec3;

/** A simulated entity: moves in straight lines, no collisions. */
final class FakeBody implements Body {
    Vec3 pos;
    float yaw;
    String world;
    boolean moving;
    boolean removed;
    final double height;

    FakeBody(String world, Vec3 pos, double height) {
        this.world = world;
        this.pos = pos;
        this.height = height;
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
        return height;
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
        return world;
    }

    @Override
    public void applyHealth(UnitState unit, double oldHealth, HitResult cause) {
    }

    @Override
    public void kill(UnitState unit, UnitState killer) {
    }

    @Override
    public void teleport(Vec3 p) {
        pos = p;
    }

    @Override
    public void setVelocity(Vec3 velocity) {
        pos = pos.add(velocity.mul(3));
    }

    @Override
    public void lookAt(Vec3 point) {
        if (point.horizontalDistance(pos) > 0.01) yaw = (float) pos.yawTo(point);
    }

    @Override
    public void refreshMovement(UnitState unit) {
    }

    @Override
    public void despawn() {
        removed = true;
    }
}
