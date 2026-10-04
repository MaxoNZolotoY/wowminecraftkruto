package com.wowcraft.core.stat;

import java.util.Arrays;

/** A mutable bag of stat values. */
public final class StatBlock {
    private final double[] values = new double[Stat.VALUES.length];

    public StatBlock() {
    }

    public StatBlock(StatBlock other) {
        System.arraycopy(other.values, 0, values, 0, values.length);
    }

    public double get(Stat s) {
        return values[s.ordinal()];
    }

    public StatBlock set(Stat s, double v) {
        values[s.ordinal()] = v;
        return this;
    }

    public StatBlock add(Stat s, double v) {
        values[s.ordinal()] += v;
        return this;
    }

    public StatBlock add(StatBlock other) {
        for (int i = 0; i < values.length; i++) values[i] += other.values[i];
        return this;
    }

    public StatBlock scale(double f) {
        for (int i = 0; i < values.length; i++) values[i] *= f;
        return this;
    }

    public void clear() {
        Arrays.fill(values, 0);
    }

    public boolean isEmpty() {
        for (double v : values) if (v != 0) return false;
        return true;
    }

    public double[] raw() {
        return values;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (Stat s : Stat.VALUES) {
            double v = get(s);
            if (v != 0) sb.append(s.name()).append('=').append(Math.round(v)).append(' ');
        }
        return sb.append('}').toString();
    }
}
