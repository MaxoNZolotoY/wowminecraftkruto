package com.wowcraft.core.npc;

import com.wowcraft.core.util.L10n;

/** A boss-mod style countdown bar shown to players ("Shadow Nova in 12s"). */
public final class BossTimer {
    private static int nextId = 1;

    public final int id = nextId++;
    public final L10n label;
    public final double start;
    public final double end;
    public final int color;

    public BossTimer(L10n label, double start, double end, int color) {
        this.label = label;
        this.start = start;
        this.end = end;
        this.color = color;
    }
}
