package com.wowcraft.core.mythic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** State of a Mythic+ keystone run. */
public final class MythicRun {
    public static final double COUNTDOWN = 10.0;

    public final String dungeonId;
    public final int level;
    public final List<Affix> affixes;
    public final double timer;
    public final double deathPenalty;
    public final double forcesRequired;
    public final int bossCount;
    public final UUID keyOwner;
    public double countdownEnd = -1;
    public double startTime = -1;
    public double finishTime = -1;
    public int deaths;
    public double forces;
    public final Set<String> bossesKilled = new LinkedHashSet<>();
    public boolean completed;
    public boolean abandoned;
    public final List<String> partyNames = new ArrayList<>();

    public MythicRun(String dungeonId, int level, List<Affix> affixes, double baseTimer, double forcesRequired, int bossCount, UUID keyOwner) {
        this.dungeonId = dungeonId;
        this.level = level;
        this.affixes = List.copyOf(affixes);
        boolean peril = affixes.contains(Affix.CHALLENGERS_PERIL);
        this.timer = baseTimer + (peril ? 90 : 0);
        this.deathPenalty = peril ? 15 : 5;
        this.forcesRequired = forcesRequired;
        this.bossCount = bossCount;
        this.keyOwner = keyOwner;
    }

    public boolean started() {
        return startTime >= 0;
    }

    public boolean has(Affix a) {
        return affixes.contains(a);
    }

    /** Elapsed time including death penalties. */
    public double elapsed(double now) {
        if (!started()) return 0;
        double end = finishTime >= 0 ? finishTime : now;
        return Math.max(0, end - startTime) + deaths * deathPenalty;
    }

    public double remaining(double now) {
        return timer - elapsed(now);
    }

    public double forcesPercent() {
        return forcesRequired <= 0 ? 100 : Math.min(100, forces / forcesRequired * 100.0);
    }

    public boolean objectivesDone() {
        return bossesKilled.size() >= bossCount && forces >= forcesRequired - 1e-6;
    }

    public boolean timed() {
        return completed && elapsed(finishTime) <= timer;
    }

    /** Health / damage multiplier for enemies from the keystone level. */
    public double scaling() {
        return MythicTables.scaling(level);
    }
}
