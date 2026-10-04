package com.wowcraft.core.resource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Current values of all resources a unit uses. Runes are tracked individually. */
public final class ResourcePool {
    public static final double RUNE_RECHARGE = 10.0;
    public static final int RUNE_COUNT = 6;
    public static final int RUNES_RECHARGING_AT_ONCE = 3;

    private final EnumSet<ResourceType> active = EnumSet.noneOf(ResourceType.class);
    private final EnumMap<ResourceType, Double> current = new EnumMap<>(ResourceType.class);
    private final EnumMap<ResourceType, Double> max = new EnumMap<>(ResourceType.class);
    /** Progress (0..1) of each rune; 1 = ready. */
    private final double[] runes = new double[RUNE_COUNT];
    private boolean dirty = true;

    public void configure(Set<ResourceType> types) {
        active.clear();
        active.addAll(types);
        for (ResourceType t : types) {
            max.putIfAbsent(t, t.defaultMax);
            current.put(t, t.startsEmpty() ? 0.0 : (t.oocTarget > 0 ? t.oocTarget : max.get(t)));
        }
        for (int i = 0; i < RUNE_COUNT; i++) runes[i] = 1.0;
        dirty = true;
    }

    public Set<ResourceType> active() {
        return active;
    }

    public boolean has(ResourceType t) {
        return active.contains(t);
    }

    public double get(ResourceType t) {
        if (t == ResourceType.RUNES) return readyRunes();
        return current.getOrDefault(t, 0.0);
    }

    public double max(ResourceType t) {
        if (t == ResourceType.RUNES) return RUNE_COUNT;
        return max.getOrDefault(t, t.defaultMax);
    }

    public void setMax(ResourceType t, double value) {
        max.put(t, value);
        if (get(t) > value) current.put(t, value);
        dirty = true;
    }

    public void set(ResourceType t, double value) {
        if (t == ResourceType.RUNES) return;
        double clamped = Math.max(0, Math.min(max(t), value));
        Double old = current.put(t, clamped);
        if (old == null || Math.abs(old - clamped) > 1e-6) dirty = true;
    }

    /** Adds (or removes, if negative) a resource. Returns the amount actually changed. */
    public double add(ResourceType t, double amount) {
        if (t == ResourceType.RUNES) {
            if (amount < 0) return -spendRunes((int) Math.round(-amount));
            return gainRunes((int) Math.round(amount));
        }
        double before = get(t);
        set(t, before + amount);
        return get(t) - before;
    }

    public boolean canAfford(ResourceType t, double cost) {
        if (cost <= 0) return true;
        return get(t) + 1e-6 >= cost;
    }

    // ---- runes ----

    public int readyRunes() {
        int n = 0;
        for (double r : runes) if (r >= 1.0) n++;
        return n;
    }

    public double runeProgress(int i) {
        return runes[i];
    }

    private int spendRunes(int n) {
        int spent = 0;
        for (int i = 0; i < RUNE_COUNT && spent < n; i++) {
            if (runes[i] >= 1.0) {
                runes[i] = 0.0;
                spent++;
            }
        }
        dirty = true;
        return spent;
    }

    private int gainRunes(int n) {
        int gained = 0;
        // complete the most advanced recharging runes first
        while (gained < n) {
            int best = -1;
            for (int i = 0; i < RUNE_COUNT; i++) if (runes[i] < 1.0 && (best < 0 || runes[i] > runes[best])) best = i;
            if (best < 0) break;
            runes[best] = 1.0;
            gained++;
        }
        dirty = true;
        return gained;
    }

    /** Advances rune recharge: up to three runes recharge simultaneously. */
    public void tickRunes(double dt, double hasteMult) {
        if (!active.contains(ResourceType.RUNES)) return;
        int charging = 0;
        // most advanced runes recharge first
        Integer[] order = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(order, (a, b) -> Double.compare(runes[b], runes[a]));
        for (int idx : order) {
            if (runes[idx] >= 1.0) continue;
            if (charging >= RUNES_RECHARGING_AT_ONCE) break;
            runes[idx] = Math.min(1.0, runes[idx] + dt * hasteMult / RUNE_RECHARGE);
            charging++;
            dirty = true;
        }
    }

    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void markDirty() {
        dirty = true;
    }

    public Map<ResourceType, Double> snapshot() {
        EnumMap<ResourceType, Double> m = new EnumMap<>(ResourceType.class);
        for (ResourceType t : active) m.put(t, get(t));
        return m;
    }
}
