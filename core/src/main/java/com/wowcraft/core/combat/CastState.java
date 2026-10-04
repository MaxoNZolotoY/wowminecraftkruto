package com.wowcraft.core.combat;

import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.CastType;
import com.wowcraft.core.util.Vec3;

/** An in-progress cast, channel or empower. */
public final class CastState {
    public final Ability ability;
    public final UnitState target;
    public final Vec3 point;
    public final double start;
    public final double end;
    public final CastType type;
    public double nextTick;
    public double tickInterval;
    public int ticksDone;
    /** Empower stage durations (seconds per stage). */
    public double stageDuration;
    public int comboSpent;
    public boolean released;
    public final boolean interruptible;

    public CastState(Ability ability, UnitState target, Vec3 point, double start, double end, CastType type, boolean interruptible) {
        this.ability = ability;
        this.target = target;
        this.point = point;
        this.start = start;
        this.end = end;
        this.type = type;
        this.interruptible = interruptible;
    }

    public double progress(double now) {
        double d = end - start;
        return d <= 0 ? 1 : Math.max(0, Math.min(1, (now - start) / d));
    }

    public int empowerStage(double now) {
        if (stageDuration <= 0) return 1;
        int s = 1 + (int) Math.floor((now - start) / stageDuration);
        return Math.max(1, Math.min(ability.maxEmpowerStage, s));
    }
}
