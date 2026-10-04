package com.wowcraft.core.npc;

import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** A boss fight in progress: script, telegraphs, boss-mod timers, adds, battle resurrections, wipe detection. */
public final class Encounter {
    public final String id;
    public final List<UnitState> bosses = new ArrayList<>();
    public final BossScript script;
    public final CombatEngine engine;
    public final EncounterHost host;
    /** Instance this encounter belongs to (may be null for world bosses). */
    public final Object instance;
    public final String bossTemplateId;
    public double pullTime;
    public boolean active;
    public boolean finished;
    public boolean victory;
    public int phase = 1;
    public final List<Telegraph> telegraphs = new ArrayList<>();
    public final List<BossTimer> timers = new ArrayList<>();
    public final List<UnitState> adds = new ArrayList<>();
    public int battleResCharges = 1;
    private double nextBattleRes;
    private final List<ScheduledAction> actions = new ArrayList<>();
    private final Map<String, Repeating> repeating = new HashMap<>();
    private final List<Double> firedThresholds = new ArrayList<>();
    private double wipeCheckAt;
    public double enrageAt = Double.POSITIVE_INFINITY;

    private record ScheduledAction(double at, Runnable run) {
    }

    private static final class Repeating {
        double next;
        final double interval;
        final Runnable run;
        final L10n label;
        BossTimer bar;

        Repeating(double next, double interval, Runnable run, L10n label) {
            this.next = next;
            this.interval = interval;
            this.run = run;
            this.label = label;
        }
    }

    public Encounter(String id, UnitState boss, BossScript script, CombatEngine engine, EncounterHost host, Object instance) {
        this.id = id;
        this.bosses.add(boss);
        this.script = script;
        this.engine = engine;
        this.host = host;
        this.instance = instance;
        this.bossTemplateId = boss.templateId;
        if (script != null) script.attach(this);
    }

    public UnitState boss() {
        return bosses.get(0);
    }

    public double now() {
        return engine.now();
    }

    public double elapsed() {
        return active ? engine.now() - pullTime : 0;
    }

    public void start() {
        if (active || finished) return;
        active = true;
        pullTime = engine.now();
        nextBattleRes = pullTime + 600;
        wipeCheckAt = pullTime + 3;
        if (script != null) script.onPull();
        host.timersChanged(this);
    }

    public void tick() {
        if (!active || finished) return;
        double now = engine.now();
        // battle res charges: +1 every 10 minutes
        if (now >= nextBattleRes) {
            battleResCharges++;
            nextBattleRes = now + 600;
        }
        // scheduled actions
        List<ScheduledAction> due = new ArrayList<>();
        for (Iterator<ScheduledAction> it = actions.iterator(); it.hasNext(); ) {
            ScheduledAction a = it.next();
            if (now >= a.at) {
                due.add(a);
                it.remove();
            }
        }
        for (ScheduledAction a : due) safe(a.run);
        for (Repeating r : new ArrayList<>(repeating.values())) {
            if (now >= r.next) {
                r.next = now + r.interval;
                safe(r.run);
                if (r.label != null) {
                    timers.remove(r.bar);
                    r.bar = new BossTimer(r.label, now, r.next, 0xFFE0A030);
                    timers.add(r.bar);
                    host.timersChanged(this);
                }
            }
        }
        // health thresholds
        if (script != null) {
            double frac = boss().healthFraction();
            for (double t : script.thresholds()) {
                if (frac <= t && !firedThresholds.contains(t)) {
                    firedThresholds.add(t);
                    safe(() -> script.onHealthBelow(t));
                }
            }
            safe(script::onTick);
        }
        // telegraphs
        for (Telegraph t : new ArrayList<>(telegraphs)) {
            if (t.follow != null && now < t.followUntil && t.follow.isAlive()) t.center = t.follow.position();
            if (now >= t.resolveAt) resolve(t);
        }
        // enrage
        if (now >= enrageAt) {
            enrageAt = Double.POSITIVE_INFINITY;
            for (UnitState b : bosses) {
                if (b.isAlive()) engine.applyAura(new EffectContext(engine, b, b, null, null, null), b, "boss_enrage", 1, -1);
            }
            host.warn(this, L10n.of("The boss is enraged!", "Босс впадает в бешенство!"), 0xFFFF2020);
        }
        // timers cleanup
        if (timers.removeIf(b -> b.end < now - 0.5)) host.timersChanged(this);
        // victory / wipe
        boolean anyBossAlive = false;
        for (UnitState b : bosses) if (b.isAlive()) anyBossAlive = true;
        if (!anyBossAlive) {
            end(true);
            return;
        }
        if (now >= wipeCheckAt) {
            wipeCheckAt = now + 1;
            boolean anyAlive = false;
            for (UnitState p : host.participants(this)) {
                if (p.isAlive() && boss().isEngagedWith(p)) {
                    anyAlive = true;
                    break;
                }
            }
            if (!anyAlive && (boss().threat() == null || boss().threat().isEmpty())) end(false);
        }
    }

    private void resolve(Telegraph t) {
        t.resolved = true;
        telegraphs.remove(t);
        host.telegraph(this, t, false);
        List<UnitState> inside = new ArrayList<>();
        for (UnitState u : host.participants(this)) if (u.isAlive() && t.contains(u)) inside.add(u);
        UnitState src = t.source != null ? t.source : boss();
        if (t.soakRequired > 0 && inside.size() < t.soakRequired) {
            if (t.onFail != null) {
                for (UnitState u : host.participants(this)) {
                    if (!u.isAlive()) continue;
                    EffectContext c = new EffectContext(engine, src, u, t.center, null, null);
                    t.onFail.apply(c);
                }
            }
            host.warn(this, L10n.of("Not enough players soaked!", "Недостаточно игроков поглотили удар!"), 0xFFFF4040);
        }
        if (t.onHit == null) return;
        for (UnitState u : inside) {
            EffectContext c = new EffectContext(engine, src, u, t.center, null, null);
            if (t.split && inside.size() > 1) c.scale = 1.0 / inside.size();
            safe(() -> t.onHit.apply(c));
        }
    }

    public void end(boolean victory) {
        if (finished) return;
        finished = true;
        active = false;
        this.victory = victory;
        for (Telegraph t : telegraphs) host.telegraph(this, t, false);
        telegraphs.clear();
        for (UnitState b : bosses) engine.removeAreasOf(b);
        timers.clear();
        actions.clear();
        repeating.clear();
        if (script != null) safe(() -> {
            if (victory) script.onVictory();
            else script.onWipe();
        });
        host.timersChanged(this);
        host.ended(this, victory);
    }

    // ------------------------------------------------------------------ API for scripts

    public void after(double delay, Runnable r) {
        actions.add(new ScheduledAction(engine.now() + delay, r));
    }

    /** Repeating mechanic with an optional boss-mod bar. */
    public void every(String key, double firstDelay, double interval, L10n label, Runnable r) {
        Repeating rep = new Repeating(engine.now() + firstDelay, interval, r, label);
        repeating.put(key, rep);
        if (label != null) {
            rep.bar = new BossTimer(label, engine.now(), rep.next, 0xFFE0A030);
            timers.add(rep.bar);
            host.timersChanged(this);
        }
    }

    public void cancel(String key) {
        Repeating r = repeating.remove(key);
        if (r != null && r.bar != null) {
            timers.remove(r.bar);
            host.timersChanged(this);
        }
    }

    public void cancelAll() {
        for (String k : new ArrayList<>(repeating.keySet())) cancel(k);
        actions.clear();
    }

    public void addTelegraph(Telegraph t) {
        telegraphs.add(t);
        host.telegraph(this, t, true);
    }

    public void bar(L10n label, double duration, int color) {
        timers.add(new BossTimer(label, engine.now(), engine.now() + duration, color));
        host.timersChanged(this);
    }

    public boolean thresholdFired(double t) {
        return firedThresholds.contains(t);
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }
}
