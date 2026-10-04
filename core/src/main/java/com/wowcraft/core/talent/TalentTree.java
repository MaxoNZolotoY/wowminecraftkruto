package com.wowcraft.core.talent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A 3x3 talent grid with point gates: row r requires {@code GATE * r} points spent in the rows above.
 */
public final class TalentTree {
    public static final int ROWS = 3;
    public static final int COLS = 3;
    public static final int GATE = 2;
    public static final int[] CLASS_POINT_LEVELS = {10, 20, 30, 40, 50};
    public static final int[] SPEC_POINT_LEVELS = {15, 25, 35, 45, 55};
    public static final int HERO_LEVEL = 70;

    public final String id;
    public final boolean classTree;
    private final Map<String, TalentNode> nodes = new LinkedHashMap<>();

    public TalentTree(String id, boolean classTree, List<TalentNode> list) {
        this.id = id;
        this.classTree = classTree;
        for (TalentNode n : list) nodes.put(n.id, n);
    }

    public Collection<TalentNode> nodes() {
        return nodes.values();
    }

    public TalentNode node(String id) {
        return nodes.get(id);
    }

    public TalentNode at(int row, int col) {
        for (TalentNode n : nodes.values()) if (n.row == row && n.col == col) return n;
        return null;
    }

    public int pointsAt(int level) {
        int[] lv = classTree ? CLASS_POINT_LEVELS : SPEC_POINT_LEVELS;
        int p = 0;
        for (int l : lv) if (level >= l) p++;
        return p;
    }

    /** Points spent in rows strictly above {@code row}. */
    public int spentAbove(Set<String> chosen, int row) {
        int s = 0;
        for (String id : chosen) {
            TalentNode n = nodes.get(id);
            if (n != null && n.row < row) s++;
        }
        return s;
    }

    public int spent(Set<String> chosen) {
        int s = 0;
        for (String id : chosen) if (nodes.containsKey(id)) s++;
        return s;
    }

    public boolean canLearn(Set<String> chosen, String nodeId, int level) {
        TalentNode n = nodes.get(nodeId);
        if (n == null || chosen.contains(nodeId)) return false;
        if (spent(chosen) >= pointsAt(level)) return false;
        return spentAbove(chosen, n.row) >= GATE * n.row;
    }

    /** Whether removing the node keeps the remaining selection valid. */
    public boolean canUnlearn(Set<String> chosen, String nodeId) {
        if (!chosen.contains(nodeId)) return false;
        java.util.HashSet<String> rest = new java.util.HashSet<>(chosen);
        rest.remove(nodeId);
        return isValid(rest, Integer.MAX_VALUE);
    }

    public boolean isValid(Set<String> chosen, int level) {
        if (spent(chosen) > pointsAt(level)) return false;
        for (String id : chosen) {
            TalentNode n = nodes.get(id);
            if (n == null) continue;
            if (spentAbove(chosen, n.row) < GATE * n.row) return false;
        }
        return true;
    }

    /** Drops invalid / unknown nodes until the selection is valid. */
    public List<String> sanitize(Collection<String> chosen, int level) {
        List<String> ordered = new ArrayList<>();
        for (String id : chosen) if (nodes.containsKey(id)) ordered.add(id);
        ordered.sort((a, b) -> Integer.compare(nodes.get(a).row, nodes.get(b).row));
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String id : ordered) {
            if (canLearn(out, id, level)) out.add(id);
        }
        return new ArrayList<>(out);
    }
}
