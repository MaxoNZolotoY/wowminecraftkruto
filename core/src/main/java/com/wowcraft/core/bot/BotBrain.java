package com.wowcraft.core.bot;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.MoveIntent;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.npc.Telegraph;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * AI for player-like bots (follower dungeons, raid fillers, arena / battleground opponents and teammates).
 * Uses the spec's rotation (APL) for abilities and simple role logic for positioning.
 */
public final class BotBrain {
    public final UnitState unit;
    public final Rotation rotation;
    /** Reaction delay: lower = better bot (scaled by difficulty / MMR). */
    public double reaction = 0.25;
    private double nextThink;
    private double nextMedallionCheck;
    private UnitState focus;
    public boolean passive;

    public BotBrain(UnitState unit) {
        this.unit = unit;
        this.rotation = Content.kit(unit.spec.wowClass).rotations.get(unit.spec);
    }

    public void tick(BotContext ctx) {
        CombatEngine engine = ctx.engine();
        double now = engine.now();
        if (unit.isDead()) {
            unit.move = MoveIntent.STOP;
            return;
        }
        if (now < nextThink) return;
        nextThink = now + 0.1 + engine.rng().nextDouble() * 0.05;

        // PvP trinket out of long crowd control
        if (ctx.pvp(unit) && now >= nextMedallionCheck) {
            nextMedallionCheck = now + 0.5;
            for (AuraInstance a : unit.auras().all()) {
                if (a.def.harmful && a.def.cc != null && a.def.cc.isHardCc() && a.remaining(now) > 2.5 && unit.healthFraction() < 0.7) {
                    engine.cast(unit, "gladiators_medallion", unit, null);
                    break;
                }
            }
        }

        List<UnitState> allies = ctx.allies(unit);
        List<UnitState> enemies = ctx.enemies(unit);

        // 1) step out of danger
        Vec3 safe = escapeTelegraphs(ctx);
        if (safe != null) {
            unit.move = MoveIntent.moveTo(safe, 1.2);
            if (unit.isCasting() && !engine.canCastWhileMoving(unit, unit.cast().ability)) engine.cancelCast(unit, false);
            castInstantOnly(ctx, allies, enemies);
            return;
        }
        Vec3 soak = soakTarget(ctx);

        // 2) choose a target
        UnitState target = chooseTarget(ctx, allies, enemies);
        if (target != null) engine.setTarget(unit, target);
        boolean fighting = target != null && !passive;

        // 3) abilities
        if (!unit.isCasting() && fighting || !unit.isCasting() && unit.role() == Role.HEALER && anyInjured(allies)) {
            RotationRunner.Situation sit = new RotationRunner.Situation(target, enemies, allies);
            RotationRunner.Decision d = RotationRunner.decide(engine, unit, rotation, sit);
            if (d != null && now >= nextThink - 0.1 + reaction * 0) {
                CastResult r = engine.cast(unit, d.abilityId(), d.target(), d.point());
                if (r.ok() && d.target() != null && d.target() != unit && unit.body != null) unit.body.lookAt(d.target().position());
            }
        }
        if (fighting && unit.role() == Role.TANK && !ctx.pvp(unit)) tankTaunt(ctx, allies, enemies);
        if (fighting && unit.mainHand != null && unit.role() != Role.HEALER) {
            if (!unit.autoAttack) engine.startAutoAttack(unit, target);
        }

        // 4) movement
        if (soak != null) {
            unit.move = MoveIntent.moveTo(soak, 1.1);
        } else if (unit.isCasting() && !engine.canCastWhileMoving(unit, unit.cast().ability)) {
            unit.move = MoveIntent.STOP;
            if (unit.body != null && unit.cast().target != null && unit.cast().target != unit) unit.body.lookAt(unit.cast().target.position());
        } else if (fighting) {
            positionInCombat(ctx, target, allies);
        } else {
            Vec3 obj = ctx.objective(unit);
            UnitState leader = ctx.leader(unit);
            if (obj != null && (leader == null || ctx.pvp(unit))) {
                unit.move = unit.position().distance(obj) > 1.5 ? MoveIntent.moveTo(obj, 1.0) : MoveIntent.STOP;
            } else if (leader != null && leader != unit) {
                double d = engine.distance(unit, leader);
                if (d > 40 && unit.body != null) unit.body.teleport(leader.position());
                int idx = ctx.formationIndex(unit);
                Vec3 spot = leader.position().add(Vec3.fromYaw(leader.yaw() + 150 + idx * 30).mul(2.5 + idx * 0.6));
                unit.move = unit.position().distance(spot) > 2.5 ? MoveIntent.moveTo(spot, 1.15) : MoveIntent.STOP;
            } else {
                unit.move = MoveIntent.STOP;
            }
        }
    }

    private boolean anyInjured(List<UnitState> allies) {
        for (UnitState a : allies) if (a.isAlive() && a.healthFraction() < 0.95) return true;
        return false;
    }

    private UnitState chooseTarget(BotContext ctx, List<UnitState> allies, List<UnitState> enemies) {
        CombatEngine engine = ctx.engine();
        List<UnitState> live = new ArrayList<>();
        for (UnitState e : enemies) if (e.isAlive() && engine.canTarget(unit, e)) live.add(e);
        if (live.isEmpty()) return null;
        if (ctx.pvp(unit)) {
            // focus: keep focus while alive and close, prefer low health / healers
            if (focus != null && focus.isAlive() && live.contains(focus) && engine.distance(unit, focus) < 45 && engine.rng().chance(0.97)) return focus;
            UnitState best = null;
            double bestScore = Double.MAX_VALUE;
            for (UnitState e : live) {
                double d = engine.distance(unit, e);
                if (d > 50) continue;
                double score = e.healthFraction() * 100 + d * 1.5 + (e.role() == Role.HEALER ? -15 : 0) + (e.kind == com.wowcraft.core.combat.UnitKind.PET ? 60 : 0);
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            focus = best;
            return best;
        }
        // PvE: only fight things the group is fighting
        List<UnitState> engaged = new ArrayList<>();
        for (UnitState e : live) {
            boolean fightingUs = false;
            for (UnitState a : allies) if (e.isEngagedWith(a) || (e.threat() != null && e.threat().raw().containsKey(a))) fightingUs = true;
            if (fightingUs) engaged.add(e);
        }
        if (engaged.isEmpty()) return null;
        if (unit.role() == Role.TANK) {
            // pick up whatever is attacking others, else the closest
            for (UnitState e : engaged) {
                UnitState t = e.target();
                if (t != null && t != unit && t.isPlayerLike() && engine.distance(unit, e) < 25) return e;
            }
            UnitState cur = unit.target();
            if (cur != null && engaged.contains(cur)) return cur;
            return closest(engine, engaged);
        }
        // DPS / healers assist the tank, else the leader's target
        UnitState tank = RotationRunner.tank(allies, unit);
        if (tank != null && tank != unit && tank.target() != null && engaged.contains(tank.target())) {
            UnitState tt = tank.target();
            // kill priority adds (explosive orbs, shades) first
            for (UnitState e : engaged) {
                if (e.templateId != null && (e.templateId.equals("explosive_orb") || e.templateId.equals("void_emissary"))) return e;
            }
            return tt;
        }
        UnitState leader = ctx.leader(unit);
        if (leader != null && leader.target() != null && engaged.contains(leader.target())) return leader.target();
        return closest(engine, engaged);
    }

    private UnitState closest(CombatEngine engine, List<UnitState> list) {
        UnitState best = null;
        double bd = Double.MAX_VALUE;
        for (UnitState e : list) {
            double d = engine.distance(unit, e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private void tankTaunt(BotContext ctx, List<UnitState> allies, List<UnitState> enemies) {
        CombatEngine engine = ctx.engine();
        for (UnitState e : enemies) {
            if (!e.isAlive() || e.target() == null || e.target() == unit) continue;
            if (!e.target().isPlayerLike() || e.boss && e.target().role() == Role.TANK) continue;
            if (engine.distance(unit, e) > 30) continue;
            for (String id : unit.knownAbilities) {
                Ability a = Registry.ability(id);
                if (a != null && a.hasTag("taunt") && engine.check(unit, id, e) == CastResult.OK) {
                    engine.cast(unit, id, e, null);
                    return;
                }
            }
        }
    }

    private void positionInCombat(BotContext ctx, UnitState target, List<UnitState> allies) {
        CombatEngine engine = ctx.engine();
        Role role = unit.role();
        boolean melee = unit.spec != null && unit.spec.isMelee() && role != Role.HEALER;
        if (melee) {
            double d = engine.distance(unit, target);
            if (d > 2.5 || !engine.isFacing(unit, target)) {
                // tanks face the boss away from the group, dps prefer the back
                unit.move = MoveIntent.chase(target, 1.5);
            } else {
                unit.move = MoveIntent.STOP;
                if (unit.body != null) unit.body.lookAt(target.position());
            }
            return;
        }
        if (role == Role.HEALER) {
            UnitState anchor = RotationRunner.tank(allies, unit);
            if (anchor == null || anchor == unit) anchor = target;
            double d = engine.distance(unit, anchor);
            if (d > 22 || !engine.lineOfSight(unit, anchor)) unit.move = MoveIntent.chase(anchor, 15);
            else if (target != null && engine.distance(unit, target) < 6) unit.move = MoveIntent.flee(target.position(), 10);
            else unit.move = MoveIntent.STOP;
            return;
        }
        double d = engine.distance(unit, target);
        if (d > 26 || !engine.lineOfSight(unit, target)) unit.move = MoveIntent.chase(target, 20);
        else if (ctx.pvp(unit) && d < 7 && target.spec != null && target.spec.isMelee()) unit.move = MoveIntent.flee(target.position(), 14);
        else {
            unit.move = MoveIntent.STOP;
            if (unit.body != null) unit.body.lookAt(target.position());
        }
    }

    /** If standing in a harmful telegraph, returns a point just outside of it. */
    private Vec3 escapeTelegraphs(BotContext ctx) {
        for (Telegraph t : ctx.telegraphs(unit)) {
            if (t.resolved || t.soakRequired > 0 || t.split) continue;
            if (t.follow == unit) {
                // spread: move away from allies
                UnitState nearest = null;
                double nd = 99;
                for (UnitState a : ctx.allies(unit)) {
                    if (a == unit || a.isDead()) continue;
                    double d = a.position().distance(unit.position());
                    if (d < nd) {
                        nd = d;
                        nearest = a;
                    }
                }
                if (nearest != null && nd < t.radius + 1) {
                    Vec3 away = unit.position().sub(nearest.position()).horizontal().normalize();
                    if (away.lengthSq() < 1e-6) away = Vec3.fromYaw(ctx.engine().rng().range(0, 360));
                    return unit.position().add(away.mul(t.radius + 2));
                }
                continue;
            }
            if (!t.contains(unit)) continue;
            Vec3 c = t.center;
            Vec3 out;
            switch (t.shape) {
                case CONE, LINE -> {
                    Vec3 dir = Vec3.fromYaw(t.yaw + 90);
                    double side = unit.position().sub(c).dot(dir) >= 0 ? 1 : -1;
                    out = unit.position().add(dir.mul(side * (t.width > 0 ? t.width / 2 + 2 : 4)));
                }
                case RING -> {
                    Vec3 in = c.sub(unit.position()).horizontal().normalize();
                    out = c.add(in.mul(-Math.max(0, t.innerRadius - 1.5))).add(0, 0, 0);
                    out = new Vec3(c.x(), unit.position().y(), c.z());
                }
                default -> {
                    Vec3 away = unit.position().sub(c).horizontal().normalize();
                    if (away.lengthSq() < 1e-6) away = Vec3.fromYaw(ctx.engine().rng().range(0, 360));
                    out = c.add(away.mul(t.radius + 2.0));
                    out = new Vec3(out.x(), unit.position().y(), out.z());
                }
            }
            return out;
        }
        return null;
    }

    /** Soak / stack mechanics: non-tanks move into them if not enough people are inside. */
    private Vec3 soakTarget(BotContext ctx) {
        for (Telegraph t : ctx.telegraphs(unit)) {
            if (t.resolved) continue;
            boolean stack = t.split && t.follow != null && t.follow != unit;
            boolean soak = t.soakRequired > 0;
            if (!stack && !soak) continue;
            if (t.contains(unit)) continue;
            if (soak) {
                int inside = 0;
                for (UnitState a : ctx.allies(unit)) if (a.isAlive() && t.contains(a)) inside++;
                if (inside >= t.soakRequired) continue;
            }
            if (unit.role() == Role.TANK && !soak) continue;
            return new Vec3(t.center.x(), unit.position().y(), t.center.z());
        }
        return null;
    }

    private void castInstantOnly(BotContext ctx, List<UnitState> allies, List<UnitState> enemies) {
        // defensives / instant heals while moving are handled by the rotation's urgent steps
        RotationRunner.Situation sit = new RotationRunner.Situation(unit.target(), enemies, allies);
        RotationRunner.Decision d = RotationRunner.decide(ctx.engine(), unit, rotation, sit);
        if (d == null) return;
        Ability a = Registry.ability(ctx.engine().resolveAbility(unit, d.abilityId()));
        if (a == null) return;
        if (ctx.engine().castTime(unit, a) > 0.05 && !ctx.engine().canCastWhileMoving(unit, a)) return;
        if (a.castType == com.wowcraft.core.spell.CastType.CHANNEL && !ctx.engine().canCastWhileMoving(unit, a)) return;
        ctx.engine().cast(unit, d.abilityId(), d.target(), d.point());
    }
}
