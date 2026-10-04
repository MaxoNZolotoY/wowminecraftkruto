package com.wowcraft.core.npc;

import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for boss fights. Override the hooks and use the helpers to build mechanics.
 * Damage helpers are expressed in "boss spell power" units: 1.0 = ~3% of an expected player's health
 * at the content's intended item level (scaled by difficulty and keystone level).
 */
public abstract class BossScript {
    protected Encounter e;

    public static final int RED = 0xFFFF3030, ORANGE = 0xFFFF9020, PURPLE = 0xFFB040FF, BLUE = 0xFF40A0FF, GREEN = 0xFF40FF60,
            YELLOW = 0xFFFFE040, WHITE = 0xFFFFFFFF;

    void attach(Encounter e) {
        this.e = e;
    }

    // ------------------------------------------------------------------ hooks

    public void onPull() {
    }

    public void onTick() {
    }

    /** Health fractions (descending) at which {@link #onHealthBelow} fires. */
    public double[] thresholds() {
        return new double[0];
    }

    public void onHealthBelow(double fraction) {
    }

    public void onVictory() {
    }

    public void onWipe() {
    }

    public void onAddDeath(UnitState add) {
    }

    public Encounter encounter() {
        return e;
    }

    // ------------------------------------------------------------------ targets

    protected UnitState boss() {
        return e.boss();
    }

    protected List<UnitState> alivePlayers() {
        List<UnitState> out = new ArrayList<>();
        for (UnitState u : e.host.participants(e)) if (u.isAlive()) out.add(u);
        return out;
    }

    protected List<UnitState> randomPlayers(int n) {
        List<UnitState> l = alivePlayers();
        Collections.shuffle(l, new java.util.Random(e.engine.rng().nextLong()));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    /** Random players, preferring non-tanks. */
    protected List<UnitState> randomNonTanks(int n) {
        List<UnitState> l = alivePlayers();
        l.removeIf(u -> u.role() == Role.TANK && alivePlayers().size() > n);
        Collections.shuffle(l, new java.util.Random(e.engine.rng().nextLong()));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    protected UnitState tank() {
        UnitState t = boss().target();
        if (t != null && t.isAlive()) return t;
        for (UnitState u : alivePlayers()) if (u.role() == Role.TANK) return u;
        List<UnitState> l = alivePlayers();
        return l.isEmpty() ? null : l.get(0);
    }

    protected UnitState farthestPlayer() {
        UnitState best = null;
        double d = -1;
        for (UnitState u : alivePlayers()) {
            double dist = u.position().distance(boss().position());
            if (dist > d) {
                d = dist;
                best = u;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ effects

    /** Spell damage in boss spell-power units. */
    protected Effect hit(School school, double coefficient) {
        return Effects.damage(school, Scaling.sp(coefficient));
    }

    /** Damage as a fraction of the target's max health (unavoidable mechanics). */
    protected Effect pct(School school, double fraction) {
        return Effects.rawDamage(school, Scaling.targetHp(fraction));
    }

    protected Effect debuff(String auraId) {
        return Effects.aura(auraId);
    }

    protected Effect knock(double strength) {
        return Effects.knockback(strength);
    }

    // ------------------------------------------------------------------ mechanics

    protected void cast(String abilityId, UnitState target) {
        if (boss().isDead()) return;
        CastResult r = e.engine.cast(boss(), abilityId, target, target != null ? target.position() : null);
        if (!r.ok()) e.engine.triggerAbility(boss(), abilityId, target, null);
    }

    protected Telegraph circleAt(Vec3 center, double radius, double delay, int color, Effect onHit) {
        Telegraph t = Telegraph.circle(boss(), center, radius, e.now(), delay, color, onHit);
        e.addTelegraph(t);
        return t;
    }

    /** A circle that follows a player and then lands where they stand (spread mechanic). */
    protected Telegraph circleOn(UnitState player, double radius, double delay, int color, Effect onHit) {
        Telegraph t = Telegraph.circle(boss(), player.position(), radius, e.now(), delay, color, onHit).following(player, e.now() + delay * 0.85);
        e.addTelegraph(t);
        return t;
    }

    protected Telegraph coneFromBoss(double angle, double range, double delay, int color, Effect onHit) {
        UnitState b = boss();
        Telegraph t = Telegraph.cone(b, b.position(), b.yaw(), angle, range, e.now(), delay, color, onHit);
        e.addTelegraph(t);
        return t;
    }

    protected Telegraph coneAt(UnitState target, double angle, double range, double delay, int color, Effect onHit) {
        UnitState b = boss();
        float yaw = (float) b.position().yawTo(target.position());
        if (b.body != null) b.body.lookAt(target.position());
        Telegraph t = Telegraph.cone(b, b.position(), yaw, angle, range, e.now(), delay, color, onHit);
        e.addTelegraph(t);
        return t;
    }

    protected Telegraph lineTo(UnitState target, double length, double width, double delay, int color, Effect onHit) {
        UnitState b = boss();
        float yaw = (float) b.position().yawTo(target.position());
        Telegraph t = Telegraph.line(b, b.position(), yaw, length, width, e.now(), delay, color, onHit);
        e.addTelegraph(t);
        return t;
    }

    protected Telegraph ringAround(double inner, double outer, double delay, int color, Effect onHit) {
        Telegraph t = Telegraph.ring(boss(), boss().position(), inner, outer, e.now(), delay, color, onHit);
        e.addTelegraph(t);
        return t;
    }

    /** Players must stand together inside: damage is split. */
    protected Telegraph stackOn(UnitState player, double radius, double delay, Effect onHit) {
        Telegraph t = circleOn(player, radius, delay, BLUE, onHit);
        t.splitDamage();
        return t;
    }

    /** Soak circle: at least N players inside, otherwise everybody gets hit. */
    protected Telegraph soakAt(Vec3 center, double radius, double delay, int required, Effect onHit, Effect onFail) {
        Telegraph t = circleAt(center, radius, delay, YELLOW, onHit);
        t.soak(required, onFail);
        t.splitDamage();
        return t;
    }

    /** Damage to every participant (raid-wide AoE). */
    protected void raidDamage(Effect effect) {
        for (UnitState u : alivePlayers()) effect.apply(new EffectContext(e.engine, boss(), u, null, null, null));
    }

    protected UnitState add(String templateId, Vec3 pos) {
        UnitState u = e.host.spawnAdd(e, templateId, pos);
        if (u != null) {
            e.adds.add(u);
            UnitState t = tank();
            if (t != null) e.engine.aggro(u, randomPlayers(1).isEmpty() ? t : randomPlayers(1).get(0), 1);
        }
        return u;
    }

    protected List<UnitState> addsAround(String templateId, int count, double radius) {
        List<UnitState> out = new ArrayList<>();
        Vec3 c = boss().position();
        for (int i = 0; i < count; i++) {
            double ang = 360.0 * i / Math.max(1, count) + e.engine.rng().range(0, 30);
            UnitState u = add(templateId, c.add(Vec3.fromYaw(ang).mul(radius)));
            if (u != null) out.add(u);
        }
        return out;
    }

    /** A persistent damaging zone on the ground (removed when the encounter ends). */
    protected void poolAt(Vec3 center, double radius, double duration, int color, Effect tick) {
        EffectContext ctx = new EffectContext(e.engine, boss(), null, center, null, null);
        e.engine.addArea(ctx, com.wowcraft.core.combat.GroundArea.Def.enemies("boss_pool", radius, duration, 1.0, tick).color(color));
    }

    /** Applies an aura from the boss to a unit. */
    protected void applyAura(UnitState target, String auraId) {
        if (target == null || target.isDead()) return;
        e.engine.applyAura(new EffectContext(e.engine, boss(), target, null, null, null), target, auraId, 1, -1);
    }

    protected void buffBoss(String auraId) {
        applyAura(boss(), auraId);
    }

    /** Damage absorb shield on the boss worth a fraction of its maximum health. */
    protected void shieldBoss(String auraId, double fractionOfMaxHealth) {
        EffectContext ctx = new EffectContext(e.engine, boss(), boss(), null, null, null);
        e.engine.applyAbsorb(ctx, boss(), auraId, boss().maxHealth() * fractionOfMaxHealth);
    }

    /** Applies an effect to one unit with the boss as the source. */
    protected void hitUnit(UnitState target, Effect effect) {
        if (target == null || target.isDead()) return;
        effect.apply(new EffectContext(e.engine, boss(), target, target.position(), null, null));
    }

    protected boolean addsAlive() {
        for (UnitState a : e.adds) if (a.isAlive()) return true;
        return false;
    }

    /** The other bosses of a council fight. */
    protected List<UnitState> otherBosses() {
        List<UnitState> out = new ArrayList<>(e.bosses);
        out.remove(boss());
        return out;
    }

    protected Vec3 roomCenter() {
        return boss().brain instanceof NpcBrain nb && nb.home != null ? nb.home : bossPos();
    }

    protected void warn(String en, String ru) {
        e.host.warn(e, L10n.of(en, ru), ORANGE);
    }

    protected void warn(String en, String ru, int color) {
        e.host.warn(e, L10n.of(en, ru), color);
    }

    protected void say(String en, String ru) {
        e.host.say(e, boss(), L10n.of(en, ru));
    }

    protected void every(String key, double first, double interval, String en, String ru, Runnable r) {
        e.every(key, first, interval, en == null ? null : L10n.of(en, ru), r);
    }

    protected void after(double delay, Runnable r) {
        e.after(delay, r);
    }

    protected void enrageAfter(double seconds) {
        e.enrageAt = e.now() + seconds;
        e.bar(L10n.of("Enrage", "Бешенство"), seconds, RED);
    }

    protected void phase(int p) {
        e.phase = p;
    }

    protected Vec3 bossPos() {
        return boss().position();
    }

    protected double rnd(double a, double b) {
        return e.engine.rng().range(a, b);
    }

    /** A random point in a ring around the boss. */
    protected Vec3 randomPointAround(double minR, double maxR) {
        double ang = rnd(0, 360);
        return bossPos().add(Vec3.fromYaw(ang).mul(rnd(minR, maxR)));
    }
}
