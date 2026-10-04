package com.wowcraft.core.util;

import java.util.List;
import java.util.SplittableRandom;

/** Small seeded random helper. */
public final class Rng {
    private SplittableRandom random;

    public Rng(long seed) {
        this.random = new SplittableRandom(seed);
    }

    public Rng() {
        this(System.nanoTime());
    }

    public void reseed(long seed) {
        this.random = new SplittableRandom(seed);
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public int nextInt(int bound) {
        return bound <= 0 ? 0 : random.nextInt(bound);
    }

    public int range(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    public double range(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    public <T> T pick(List<T> list) {
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    public <T> T pick(T[] arr) {
        return arr.length == 0 ? null : arr[random.nextInt(arr.length)];
    }

    public long nextLong() {
        return random.nextLong();
    }
}
