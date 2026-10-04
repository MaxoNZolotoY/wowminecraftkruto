package com.wowcraft.core.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-wide persistent data (leaderboards, season). */
public final class WorldData {
    public int season = 1;
    /** dungeon id -> best runs (sorted). */
    public Map<String, List<Run>> leaderboard = new HashMap<>();
    /** player uuid -> last known name and rating (for the rating ladder). */
    public Map<String, String> names = new HashMap<>();
    public Map<String, Double> ratings = new HashMap<>();
    public Map<String, Integer> arenaRatings = new HashMap<>();

    public static final class Run {
        public String dungeon;
        public int level;
        public double time;
        public boolean timed;
        public long when;
        public List<String> party = new ArrayList<>();
    }

    public void record(Run run) {
        List<Run> list = leaderboard.computeIfAbsent(run.dungeon, k -> new ArrayList<>());
        list.add(run);
        list.sort((a, b) -> {
            if (a.timed != b.timed) return a.timed ? -1 : 1;
            if (a.level != b.level) return Integer.compare(b.level, a.level);
            return Double.compare(a.time, b.time);
        });
        while (list.size() > 20) list.remove(list.size() - 1);
    }
}
