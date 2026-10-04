package com.wowcraft.core.combat;

import com.wowcraft.core.util.Vec3;

/** What an AI-controlled unit wants its body to do this tick (the platform performs the pathfinding). */
public record MoveIntent(Kind kind, UnitState target, Vec3 point, double range, double speedMult) {
    public enum Kind {STOP, CHASE, MOVE_TO, FLEE, FOLLOW, WANDER}

    public static final MoveIntent STOP = new MoveIntent(Kind.STOP, null, null, 0, 1);

    public static MoveIntent chase(UnitState t, double range) {
        return new MoveIntent(Kind.CHASE, t, null, range, 1);
    }

    public static MoveIntent moveTo(Vec3 p) {
        return new MoveIntent(Kind.MOVE_TO, null, p, 0.8, 1);
    }

    public static MoveIntent moveTo(Vec3 p, double speedMult) {
        return new MoveIntent(Kind.MOVE_TO, null, p, 0.8, speedMult);
    }

    public static MoveIntent follow(UnitState t, double range) {
        return new MoveIntent(Kind.FOLLOW, t, null, range, 1.1);
    }

    public static MoveIntent flee(Vec3 from, double distance) {
        return new MoveIntent(Kind.FLEE, null, from, distance, 1);
    }
}
