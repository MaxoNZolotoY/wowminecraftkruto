package com.wowcraft.core.npc;

import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.MoveIntent;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/** AI of hostile NPCs and pets: aggro, threat-based targeting, spell timers, movement, leash / evade. */
public final class NpcBrain {
    public enum State {IDLE, COMBAT, EVADING}

    public final NpcTemplate template;
    public final UnitState unit;
    public Vec3 home;
    public float homeYaw;
    public State state = State.IDLE;
    public double pullTime;
    private final double[] readyAt;
    public Encounter encounter;
    /** Extra leash for bosses in arenas etc. */
    public double leash = 55;
    private double nextThink;
    private double evadeStart;
    /** Patrol points (optional). */
    public List<Vec3> patrol = new ArrayList<>();
    private int patrolIndex;

    public NpcBrain(NpcTemplate template, UnitState unit, Vec3 home, float yaw) {
        this.template = template;
        this.unit = unit;
        this.home = home;
        this.homeYaw = yaw;
        this.readyAt = new double[template.spells.size()];
    }

    public void tick(NpcContext ctx) {
        CombatEngine engine = ctx.engine();
        double now = engine.now();
        if (unit.isDead()) {
            unit.move = MoveIntent.STOP;
            return;
        }
        if (unit.kind == UnitKind.PET || unit.kind == UnitKind.TOTEM) {
            tickPet(ctx);
            return;
        }
        if (now < nextThink) return;
        nextThink = now + 0.1;
        switch (state) {
            case IDLE -> idle(ctx);
            case COMBAT -> combat(ctx);
            case EVADING -> evade(ctx);
        }
    }

    private void idle(NpcContext ctx) {
        CombatEngine engine = ctx.engine();
        if (unit.threat() != null && !unit.threat().isEmpty()) {
            enterCombat(ctx);
            return;
        }
        if (template.friendly) {
            unit.move = MoveIntent.STOP;
            return;
        }
        for (UnitState u : ctx.aggroCandidates(unit, template.aggroRadius)) {
            double d = engine.distance(unit, u);
            if (u.auras().isStealthed() && d > 2.5) continue;
            if (!engine.lineOfSight(unit, u)) continue;
            engine.aggro(unit, u, 1);
            enterCombat(ctx);
            return;
        }
        // patrol / return home
        if (!patrol.isEmpty()) {
            Vec3 p = patrol.get(patrolIndex);
            if (unit.position().horizontalDistance(p) < 1.5) patrolIndex = (patrolIndex + 1) % patrol.size();
            unit.move = MoveIntent.moveTo(patrol.get(patrolIndex), 0.6);
        } else if (unit.position().horizontalDistance(home) > 2) {
            unit.move = MoveIntent.moveTo(home, 0.8);
        } else {
            unit.move = MoveIntent.STOP;
        }
    }

    private void enterCombat(NpcContext ctx) {
        state = State.COMBAT;
        pullTime = ctx.engine().now();
        for (int i = 0; i < readyAt.length; i++) readyAt[i] = pullTime + template.spells.get(i).initialDelay();
        ctx.onPull(this);
    }

    private void combat(NpcContext ctx) {
        CombatEngine engine = ctx.engine();
        double now = engine.now();
        UnitState target = unit.threat() == null ? null : unit.threat().pick(now,
                u -> u.isAlive() && engine.sameWorld(unit, u) && engine.canTarget(unit, u)
                        && u.position().distance(home) < leash + 20 && ctx.isValidTarget(this, u),
                u -> engine.distance(unit, u) <= 4);
        if (target == null) {
            startEvade(ctx);
            return;
        }
        if (unit.position().distance(home) > leash && encounter == null) {
            startEvade(ctx);
            return;
        }
        engine.setTarget(unit, target);
        unit.autoAttack = template.preferredRange <= 6;

        // spells
        if (!unit.isCasting() && unit.canCast()) {
            for (int i = 0; i < template.spells.size(); i++) {
                NpcSpell s = template.spells.get(i);
                if (now < readyAt[i]) continue;
                UnitState t = resolveTarget(ctx, s, target);
                if (t == null && s.target() != NpcSpell.Target.SELF) continue;
                Ability a = Registry.ability(s.abilityId());
                if (a == null) continue;
                if (s.when() != null && !s.when().test(new EffectContext(engine, unit, t, null, a, null))) continue;
                CastResult r = engine.cast(unit, s.abilityId(), t, t != null ? t.position() : null);
                if (r == CastResult.OK || r == CastResult.QUEUED) {
                    readyAt[i] = now + s.cooldown() * (0.9 + engine.rng().nextDouble() * 0.2);
                    break;
                } else if (r == CastResult.OUT_OF_RANGE || r == CastResult.NO_LINE_OF_SIGHT) {
                    // keep it ready; movement will fix it
                } else {
                    readyAt[i] = now + 1.0;
                }
            }
        }

        // movement
        if (template.stationary || unit.isCasting() || !unit.canMove()) {
            unit.move = MoveIntent.STOP;
            if (unit.body != null && unit.isCasting() && unit.cast().target != null && unit.cast().target != unit)
                unit.body.lookAt(unit.cast().target.position());
        } else if (template.role == Role.HEALER) {
            UnitState ally = ctx.lowestAlly(this, 30);
            UnitState anchor = ally != null ? ally : target;
            unit.move = MoveIntent.chase(anchor, 16);
        } else if (template.preferredRange > 6) {
            double d = engine.distance(unit, target);
            if (d > template.preferredRange || !engine.lineOfSight(unit, target)) unit.move = MoveIntent.chase(target, template.preferredRange * 0.8);
            else unit.move = MoveIntent.STOP;
        } else {
            unit.move = MoveIntent.chase(target, 1.5 + unit.width() / 2);
        }
    }

    private UnitState resolveTarget(NpcContext ctx, NpcSpell s, UnitState current) {
        CombatEngine engine = ctx.engine();
        Ability a = Registry.ability(s.abilityId());
        if (a != null && (a.targetType == TargetType.SELF || a.targetType == TargetType.NONE)) return unit;
        return switch (s.target()) {
            case CURRENT -> current;
            case SELF -> unit;
            case RANDOM_PLAYER -> {
                List<UnitState> c = ctx.enemiesInCombat(this);
                yield c.isEmpty() ? current : c.get(engine.rng().nextInt(c.size()));
            }
            case RANDOM_RANGED -> {
                List<UnitState> c = ctx.enemiesInCombat(this);
                c.removeIf(u -> engine.distance(unit, u) < 8);
                yield c.isEmpty() ? current : c.get(engine.rng().nextInt(c.size()));
            }
            case FARTHEST -> {
                UnitState best = current;
                double bd = -1;
                for (UnitState u : ctx.enemiesInCombat(this)) {
                    double d = engine.distance(unit, u);
                    if (d > bd) {
                        bd = d;
                        best = u;
                    }
                }
                yield best;
            }
            case HEALER -> {
                for (UnitState u : ctx.enemiesInCombat(this)) if (u.role() == Role.HEALER) yield u;
                yield current;
            }
            case LOWEST_ALLY -> ctx.lowestAlly(this, 30);
            case GROUND_AT_TARGET -> current;
        };
    }

    private void startEvade(NpcContext ctx) {
        state = State.EVADING;
        evadeStart = ctx.engine().now();
        if (unit.threat() != null) unit.threat().clear();
        unit.autoAttack = false;
        ctx.engine().setTarget(unit, null);
        ctx.engine().cancelCast(unit, false);
        ctx.onEvade(this);
    }

    private void evade(NpcContext ctx) {
        CombatEngine engine = ctx.engine();
        unit.move = MoveIntent.moveTo(home, 1.6);
        boolean home = unit.position().horizontalDistance(this.home) < 2.0;
        if (home || engine.now() - evadeStart > 8) {
            if (!home && unit.body != null) unit.body.teleport(this.home);
            engine.resetUnit(unit, true);
            engine.removeAreasOf(unit);
            state = State.IDLE;
            unit.move = MoveIntent.STOP;
        }
    }

    /** Pets: assist the owner, follow when idle. */
    private void tickPet(NpcContext ctx) {
        CombatEngine engine = ctx.engine();
        UnitState owner = unit.owner;
        if (owner == null || (owner.body != null && owner.body.isRemoved())) {
            if (unit.body != null) unit.body.despawn();
            return;
        }
        if (template.stationary || unit.kind == UnitKind.TOTEM) {
            unit.move = MoveIntent.STOP;
        }
        UnitState target = unit.target();
        if (target == null || target.isDead() || !engine.isHostile(unit, target)) {
            target = null;
            UnitState ot = owner.target();
            if (ot != null && ot.isAlive() && engine.isHostile(owner, ot) && (owner.inCombat() || ot.inCombat())) target = ot;
            if (target == null) {
                for (UnitState e : unit.engaged()) {
                    if (e.isAlive() && engine.isHostile(unit, e)) {
                        target = e;
                        break;
                    }
                }
            }
        }
        if (target != null && engine.distance(unit, target) > 60) target = null;
        engine.setTarget(unit, target);
        if (target == null) {
            unit.autoAttack = false;
            if (!template.stationary) {
                double d = engine.distance(unit, owner);
                if (d > 40 && unit.body != null) unit.body.teleport(owner.position());
                unit.move = d > 3.5 ? MoveIntent.follow(owner, 2.5) : MoveIntent.STOP;
            }
            return;
        }
        unit.autoAttack = template.preferredRange <= 6;
        double now = engine.now();
        if (!unit.isCasting() && unit.canCast()) {
            for (int i = 0; i < template.spells.size(); i++) {
                NpcSpell s = template.spells.get(i);
                if (now < readyAt[i]) continue;
                UnitState t = s.target() == NpcSpell.Target.SELF ? unit : target;
                CastResult r = engine.cast(unit, s.abilityId(), t, t.position());
                if (r.ok()) {
                    readyAt[i] = now + s.cooldown();
                    break;
                }
            }
        }
        if (template.stationary || unit.isCasting() || !unit.canMove()) {
            unit.move = MoveIntent.STOP;
        } else if (template.preferredRange > 6) {
            unit.move = engine.distance(unit, target) > template.preferredRange ? MoveIntent.chase(target, template.preferredRange * 0.8) : MoveIntent.STOP;
        } else {
            unit.move = MoveIntent.chase(target, 1.5);
        }
    }

    public boolean inCombat() {
        return state == State.COMBAT;
    }
}
