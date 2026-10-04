package com.wowcraft.core.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NPC aggro list. */
public final class ThreatTable {
    private final Map<UnitState, Double> threat = new LinkedHashMap<>();
    private UnitState tauntedBy;
    private double tauntUntil;
    private UnitState fixate;
    private double fixateUntil;
    private UnitState current;

    public void add(UnitState u, double amount) {
        if (u == null || amount <= 0 && threat.containsKey(u)) return;
        threat.merge(u, Math.max(0, amount), Double::sum);
    }

    public double get(UnitState u) {
        return threat.getOrDefault(u, 0.0);
    }

    public void remove(UnitState u) {
        threat.remove(u);
        if (current == u) current = null;
        if (tauntedBy == u) tauntedBy = null;
        if (fixate == u) fixate = null;
    }

    public void clear() {
        threat.clear();
        current = null;
        tauntedBy = null;
        fixate = null;
    }

    public boolean isEmpty() {
        return threat.isEmpty();
    }

    public List<UnitState> units() {
        return new ArrayList<>(threat.keySet());
    }

    public double topThreat() {
        double best = 0;
        for (double v : threat.values()) best = Math.max(best, v);
        return best;
    }

    public void taunt(UnitState u, double now, double duration) {
        double top = topThreat();
        threat.put(u, Math.max(get(u), top * 1.1 + 1));
        tauntedBy = u;
        tauntUntil = now + duration;
        current = u;
    }

    public void fixate(UnitState u, double now, double duration) {
        fixate = u;
        fixateUntil = now + duration;
        add(u, 1);
    }

    public void scale(UnitState u, double factor) {
        Double v = threat.get(u);
        if (v != null) threat.put(u, v * factor);
    }

    /** Pick the current target. WoW rule: a new target must exceed current threat by 10% (melee) / 30% (ranged). */
    public UnitState pick(double now, java.util.function.Predicate<UnitState> valid, java.util.function.Predicate<UnitState> inMelee) {
        threat.keySet().removeIf(u -> !valid.test(u));
        if (fixate != null && now < fixateUntil && valid.test(fixate)) return current = fixate;
        if (tauntedBy != null && now < tauntUntil && valid.test(tauntedBy)) return current = tauntedBy;
        if (threat.isEmpty()) return current = null;
        UnitState top = threat.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue)).get().getKey();
        if (current == null || !threat.containsKey(current)) return current = top;
        double cur = get(current);
        double needed = cur * (inMelee.test(top) ? 1.1 : 1.3);
        if (top != current && get(top) > needed) current = top;
        return current;
    }

    public UnitState current() {
        return current;
    }

    public Map<UnitState, Double> raw() {
        return threat;
    }
}
