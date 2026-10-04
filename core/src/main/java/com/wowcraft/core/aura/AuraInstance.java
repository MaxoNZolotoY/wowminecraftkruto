package com.wowcraft.core.aura;

import com.wowcraft.core.combat.UnitState;

import java.util.HashMap;
import java.util.Map;

/** A live aura on a unit. */
public final class AuraInstance {
    private static int nextId = 1;

    public final int instanceId = nextId++;
    public final AuraDef def;
    public final UnitState holder;
    /** Who applied the aura (may be dead / removed). */
    public final UnitState caster;
    public double appliedAt;
    public double expiresAt;
    public double duration;
    public double nextTickAt;
    public double tickInterval;
    public int stacks = 1;
    public double absorbRemaining;
    public double absorbMax;
    /** Free-form numbers for scripted auras (e.g. stored damage). */
    public final Map<String, Double> data = new HashMap<>();
    public boolean removed;
    /** Last time each trigger fired (internal cooldowns). */
    public final double[] triggerLastFired;

    public AuraInstance(AuraDef def, UnitState holder, UnitState caster) {
        this.def = def;
        this.holder = holder;
        this.caster = caster;
        this.triggerLastFired = new double[def.triggers.size()];
        java.util.Arrays.fill(triggerLastFired, -1e9);
    }

    public boolean isPermanent() {
        return Double.isInfinite(expiresAt);
    }

    public double remaining(double now) {
        return isPermanent() ? Double.POSITIVE_INFINITY : Math.max(0, expiresAt - now);
    }

    public double getData(String key) {
        return data.getOrDefault(key, 0.0);
    }

    public void setData(String key, double value) {
        data.put(key, value);
    }
}
