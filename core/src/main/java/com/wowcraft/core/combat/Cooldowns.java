package com.wowcraft.core.combat;

import java.util.HashMap;
import java.util.Map;

/** Ability cooldowns with charges. */
public final class Cooldowns {
    /** Per ability state. */
    public static final class Entry {
        public int charges;
        public int maxCharges;
        /** Time the next charge is restored (or the ability becomes ready). */
        public double readyAt;
        /** Duration of the current recharge (for UI sweeps). */
        public double duration;
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private boolean dirty;

    public Entry get(String abilityId) {
        return entries.get(abilityId);
    }

    public boolean isReady(String abilityId, double now) {
        Entry e = entries.get(abilityId);
        if (e == null) return true;
        update(e, now);
        return e.charges > 0;
    }

    public int charges(String abilityId, int maxCharges, double now) {
        Entry e = entries.get(abilityId);
        if (e == null) return maxCharges;
        e.maxCharges = maxCharges;
        update(e, now);
        return e.charges;
    }

    public double remaining(String abilityId, double now) {
        Entry e = entries.get(abilityId);
        if (e == null) return 0;
        update(e, now);
        if (e.charges > 0) return 0;
        return Math.max(0, e.readyAt - now);
    }

    /** Consume one charge and start (or continue) recharging. */
    public void trigger(String abilityId, double duration, int maxCharges, double now) {
        Entry e = entries.computeIfAbsent(abilityId, k -> {
            Entry n = new Entry();
            n.charges = maxCharges;
            n.maxCharges = maxCharges;
            return n;
        });
        e.maxCharges = maxCharges;
        update(e, now);
        if (duration <= 0) return;
        if (e.charges >= e.maxCharges) {
            e.readyAt = now + duration;
            e.duration = duration;
        }
        e.charges = Math.max(0, e.charges - 1);
        dirty = true;
    }

    /** Lock an ability for a fixed time without using charges (shared cooldowns, lockouts). */
    public void lock(String abilityId, double duration, double now) {
        Entry e = entries.computeIfAbsent(abilityId, k -> new Entry());
        e.maxCharges = Math.max(1, e.maxCharges);
        e.charges = 0;
        e.readyAt = Math.max(e.readyAt, now + duration);
        e.duration = duration;
        dirty = true;
    }

    /** Reduce (negative) or extend (positive) the current recharge. */
    public void adjust(String abilityId, double delta, double now) {
        Entry e = entries.get(abilityId);
        if (e == null) return;
        update(e, now);
        if (e.charges >= e.maxCharges) return;
        e.readyAt += delta;
        update(e, now);
        dirty = true;
    }

    /** Advance recharge by an amount of seconds (cooldown recovery rate effects). */
    public void advance(String abilityId, double seconds, double now) {
        adjust(abilityId, -seconds, now);
    }

    public void reset(String abilityId) {
        Entry e = entries.remove(abilityId);
        if (e != null) dirty = true;
    }

    public void resetAll() {
        if (!entries.isEmpty()) dirty = true;
        entries.clear();
    }

    private void update(Entry e, double now) {
        while (e.charges < e.maxCharges && now >= e.readyAt) {
            e.charges++;
            if (e.charges < e.maxCharges) {
                e.readyAt += e.duration;
            }
            dirty = true;
        }
    }

    public Map<String, Entry> all() {
        return entries;
    }

    public void updateAll(double now) {
        for (Entry e : entries.values()) update(e, now);
    }

    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void markDirty() {
        dirty = true;
    }
}
