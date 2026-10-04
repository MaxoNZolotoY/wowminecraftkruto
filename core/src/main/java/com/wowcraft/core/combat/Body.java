package com.wowcraft.core.combat;

import com.wowcraft.core.util.Vec3;

/** Platform-side representation of a unit (a Minecraft entity). */
public interface Body {
    Vec3 position();

    float yaw();

    double width();

    double height();

    /** Whether the unit moved horizontally since the previous tick (cancels casts). */
    boolean isMoving();

    /** The underlying entity no longer exists. */
    boolean isRemoved();

    /** Dimension / world key. Units only interact inside the same world. */
    String worldKey();

    /** Mirror a health change into the platform (hurt animation, display health, death). */
    void applyHealth(UnitState unit, double oldHealth, HitResult cause);

    /** The unit died in the combat engine. */
    void kill(UnitState unit, UnitState killer);

    void teleport(Vec3 pos);

    void setVelocity(Vec3 velocity);

    void lookAt(Vec3 point);

    /** Called when crowd control / movement-affecting state or stats changed. */
    void refreshMovement(UnitState unit);

    /** Stealth / visibility changed. */
    default void setStealthed(boolean stealthed) {
    }

    /** Remove the entity (summons expiring, instance cleanup). */
    void despawn();

    /** Play a body animation (arm swing, cast pose...). Placeholder for real animations. */
    default void playAnimation(String key) {
    }
}
