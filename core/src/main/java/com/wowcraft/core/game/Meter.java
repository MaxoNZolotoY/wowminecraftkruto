package com.wowcraft.core.game;

import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Damage / healing meter (like Details!) per group: current fight and overall. */
public final class Meter {
    public static final class Row {
        public String name;
        public int color;
        public double damage, healing, damageTaken;
        public int deaths, interrupts;
    }

    public static final class Segment {
        public String label = "";
        public double start = -1, end = -1;
        public final Map<String, Row> rows = new LinkedHashMap<>();

        public double duration(double now) {
            if (start < 0) return 0;
            return Math.max(1, (end >= 0 ? end : now) - start);
        }
    }

    public Segment current = new Segment();
    public Segment last;
    public final Segment overall = new Segment();
    private double lastActivity = -1;

    private Row row(Segment s, UnitState u) {
        UnitState m = u.master();
        return s.rows.computeIfAbsent(m.name, k -> {
            Row r = new Row();
            r.name = m.name;
            r.color = m.wowClass != null ? m.wowClass.color : 0xFFAAAAAA;
            return r;
        });
    }

    public void onHit(HitResult hit, double now) {
        if (current.start < 0) {
            current.start = now;
            if (overall.start < 0) overall.start = now;
        }
        lastActivity = now;
        double amount = hit.heal ? hit.effective() : hit.effective() + hit.absorbed;
        if (hit.source != null && hit.source.master().isPlayerLike() && (hit.heal || !hit.target.master().isPlayerLike() || hit.target.master() != hit.source.master())) {
            Row r = row(current, hit.source), o = row(overall, hit.source);
            if (hit.heal) {
                r.healing += amount;
                o.healing += amount;
            } else {
                r.damage += amount;
                o.damage += amount;
            }
        }
        if (!hit.heal && hit.target != null && hit.target.isPlayerLike()) {
            row(current, hit.target).damageTaken += amount;
            row(overall, hit.target).damageTaken += amount;
        }
    }

    public void onDeath(UnitState u) {
        if (!u.isPlayerLike()) return;
        row(current, u).deaths++;
        row(overall, u).deaths++;
    }

    public void onInterrupt(UnitState u) {
        if (!u.master().isPlayerLike()) return;
        row(current, u).interrupts++;
        row(overall, u).interrupts++;
    }

    /** Closes the current segment after 5 seconds of inactivity. */
    public void tick(double now, boolean groupInCombat) {
        if (current.start >= 0 && !groupInCombat && now - lastActivity > 5) {
            current.end = lastActivity;
            last = current;
            current = new Segment();
        }
    }

    public void reset() {
        current = new Segment();
        last = null;
        overall.rows.clear();
        overall.start = -1;
    }
}
