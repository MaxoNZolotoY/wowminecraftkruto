package com.wowcraft.core.bot;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.util.Vec3;

import java.util.List;

/** Evaluates a {@link Rotation} for a unit and picks the next action. */
public final class RotationRunner {
    private RotationRunner() {
    }

    public record Decision(String abilityId, UnitState target, Vec3 point) {
    }

    /** What the bot knows about its surroundings. */
    public record Situation(UnitState enemy, List<UnitState> enemies, List<UnitState> allies) {
    }

    public static Decision decide(CombatEngine engine, UnitState self, Rotation rotation, Situation s) {
        if (rotation == null) return null;
        Decision d = pass(engine, self, rotation, s, true);
        if (d != null) return d;
        return pass(engine, self, rotation, s, false);
    }

    private static Decision pass(CombatEngine engine, UnitState self, Rotation rotation, Situation s, boolean urgent) {
        for (Rotation.Step step : rotation.steps) {
            if (step.panic() != urgent) continue;
            if (!self.knownAbilities.contains(step.abilityId())) continue;
            Ability a = Registry.ability(engine.resolveAbility(self, step.abilityId()));
            if (a == null) continue;
            UnitState target = null;
            Vec3 point = null;
            switch (step.target()) {
                case ENEMY -> {
                    target = s.enemy();
                    if (a.targetType == TargetType.SELF || a.targetType == TargetType.NONE) target = self;
                    if (target == null) continue;
                    if (a.targetType == TargetType.NONE && s.enemy() != null
                            && engine.distance(self, s.enemy()) > Math.max(8, a.range)) continue;
                }
                case SELF -> target = self;
                case LOWEST_ALLY -> {
                    target = lowestAlly(engine, self, s.allies(), a, step.minHealthDeficit());
                    if (target == null) continue;
                }
                case TANK -> {
                    target = tank(s.allies(), self);
                    if (target == null) continue;
                }
                case CASTING_ENEMY -> {
                    target = castingEnemy(engine, self, s, a);
                    if (target == null) continue;
                }
                case DISPEL_ALLY -> {
                    target = dispelTarget(engine, self, s.allies(), a);
                    if (target == null) continue;
                }
                case DEAD_ALLY -> {
                    for (UnitState u : s.allies()) {
                        if (u.isDead() && u.isPlayerLike() && engine.distance(self, u) <= a.range) {
                            target = u;
                            break;
                        }
                    }
                    if (target == null) continue;
                }
                case ENEMY_GROUND -> {
                    if (s.enemy() == null) continue;
                    target = s.enemy();
                    point = s.enemy().position();
                }
                case ALLY_GROUND -> {
                    point = allyCenter(s.allies(), self);
                    target = self;
                }
            }
            if (step.when() != null) {
                EffectContext ctx = new EffectContext(engine, self, target, point, a, null);
                if (!step.when().test(ctx)) continue;
            }
            CastResult r = engine.check(self, step.abilityId(), target);
            if (r == CastResult.OK) return new Decision(step.abilityId(), target, point);
        }
        return null;
    }

    private static UnitState lowestAlly(CombatEngine engine, UnitState self, List<UnitState> allies, Ability a, double minDeficit) {
        UnitState best = null;
        double bestFrac = 1.0 - minDeficit + 1e-9;
        if (minDeficit <= 0) bestFrac = 1.0;
        for (UnitState u : allies) {
            if (u.isDead()) continue;
            if (u != self && engine.distance(self, u) > a.range) continue;
            double f = u.healthFraction();
            if (f < bestFrac || (minDeficit <= 0 && best == null && f <= 1.0)) {
                if (minDeficit > 0 && f > 1.0 - minDeficit) continue;
                best = u;
                bestFrac = f;
            }
        }
        return best;
    }

    public static UnitState tank(List<UnitState> allies, UnitState self) {
        for (UnitState u : allies) if (u.isAlive() && u.role() == Role.TANK) return u;
        return null;
    }

    private static UnitState castingEnemy(CombatEngine engine, UnitState self, Situation s, Ability a) {
        UnitState e = s.enemy();
        if (e != null && e.isCasting() && e.cast().interruptible && engine.distance(self, e) <= a.range + 0.3
                && e.cast().progress(engine.now()) > 0.25) return e;
        for (UnitState u : s.enemies()) {
            if (u.isCasting() && u.cast().interruptible && engine.distance(self, u) <= a.range + 0.3
                    && u.cast().progress(engine.now()) > 0.25) return u;
        }
        return null;
    }

    private static UnitState dispelTarget(CombatEngine engine, UnitState self, List<UnitState> allies, Ability a) {
        if (a.dispelTypes.isEmpty()) return null;
        for (UnitState u : allies) {
            if (u.isDead() || engine.distance(self, u) > a.range) continue;
            for (AuraInstance au : u.auras().all()) {
                if (!au.removed && au.def.harmful && !au.def.passive && a.dispelTypes.contains(au.def.dispel)) return u;
            }
        }
        return null;
    }

    private static Vec3 allyCenter(List<UnitState> allies, UnitState self) {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (UnitState u : allies) {
            if (u.isDead()) continue;
            Vec3 p = u.position();
            x += p.x();
            y += p.y();
            z += p.z();
            n++;
        }
        return n == 0 ? self.position() : new Vec3(x / n, y / n, z / n);
    }
}
