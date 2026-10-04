package com.wowcraft.core.spell;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.combat.Formulas;
import com.wowcraft.core.combat.GroundArea;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Mth;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Factory of the effect building blocks used by abilities, auras, procs and boss scripts. */
public final class Effects {
    private Effects() {
    }

    // ------------------------------------------------------------------ damage & healing

    public static Effect damage(Scaling s) {
        return damage(null, s);
    }

    public static Effect damage(School school, Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.target == null) return;
                School sc = school != null ? school : ctx.school();
                double base = Formulas.base(ctx.caster, ctx.target, s, ctx.comboSpent) * ctx.scale;
                ctx.engine.dealDamage(ctx, ctx.target, sc, base);
            }

            @Override
            public String describe(DescribeContext d) {
                School sc = school != null ? school : (d.ability() != null ? d.ability().school : School.PHYSICAL);
                return d.t("Deals ", "Наносит ") + amount(d, s) + " " + d.t(sc.name.en() + " damage", "ед. урона (" + sc.name.ru() + ")")
                        + (s.comboScaled() ? d.t(" per combo point", " за каждый прием серии") : "");
            }
        };
    }

    /** Damage that cannot crit and ignores most modifiers (boss mechanics, fall-like damage). */
    public static Effect rawDamage(School school, Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.target == null) return;
                double base = Formulas.base(ctx.caster, ctx.target, s, ctx.comboSpent) * ctx.scale;
                ctx.engine.dealRawDamage(ctx, ctx.target, school, base);
            }

            @Override
            public String describe(DescribeContext d) {
                return d.t("Deals ", "Наносит ") + amount(d, s) + " " + d.t(school.name.en() + " damage", "ед. урона (" + school.name.ru() + ")");
            }
        };
    }

    public static Effect heal(Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                UnitState t = ctx.target != null ? ctx.target : ctx.caster;
                double base = Formulas.base(ctx.caster, t, s, ctx.comboSpent) * ctx.scale;
                ctx.engine.heal(ctx, t, base);
            }

            @Override
            public String describe(DescribeContext d) {
                return d.t("Heals for ", "Восполняет ") + amount(d, s) + d.t(" health", " ед. здоровья");
            }
        };
    }

    public static Effect selfHeal(Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                double base = Formulas.base(ctx.caster, ctx.caster, s, ctx.comboSpent) * ctx.scale;
                ctx.engine.heal(ctx.withTarget(ctx.caster), ctx.caster, base);
            }

            @Override
            public String describe(DescribeContext d) {
                return d.t("Heals you for ", "Восполняет вам ") + amount(d, s) + d.t(" health", " ед. здоровья");
            }
        };
    }

    /** Applies an absorb shield aura worth the scaled amount. */
    public static Effect absorb(String auraId, Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                UnitState t = ctx.target != null ? ctx.target : ctx.caster;
                double base = Formulas.base(ctx.caster, t, s, ctx.comboSpent) * ctx.scale;
                ctx.engine.applyAbsorb(ctx, t, auraId, base);
            }

            @Override
            public String describe(DescribeContext d) {
                return d.t("Shields the target, absorbing ", "Окружает цель щитом, поглощающим ") + amount(d, s) + d.t(" damage", " ед. урона");
            }
        };
    }

    public static Effect selfAbsorb(String auraId, Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                double base = Formulas.base(ctx.caster, ctx.caster, s, ctx.comboSpent) * ctx.scale;
                ctx.engine.applyAbsorb(ctx.withTarget(ctx.caster), ctx.caster, auraId, base);
            }

            @Override
            public String describe(DescribeContext d) {
                return d.t("Shields you, absorbing ", "Окружает вас щитом, поглощающим ") + amount(d, s) + d.t(" damage", " ед. урона");
            }
        };
    }

    /** Heals the caster for a fraction of the damage dealt by the last effect in this context. */
    public static Effect drainLast(double fraction) {
        return described(ctx -> {
            if (ctx.lastAmount > 0) ctx.engine.heal(ctx.withTarget(ctx.caster), ctx.caster, ctx.lastAmount * fraction, true);
        }, "Heals you for " + (int) (fraction * 100) + "% of the damage dealt", "Исцеляет вас на " + (int) (fraction * 100) + "% от нанесенного урона");
    }

    // ------------------------------------------------------------------ auras

    public static Effect aura(String auraId) {
        return aura(auraId, 1);
    }

    public static Effect aura(String auraId, int stacks) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                UnitState t = ctx.target != null ? ctx.target : ctx.caster;
                ctx.engine.applyAura(ctx, t, auraId, stacks, -1);
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAura(d, auraId, false);
            }
        };
    }

    public static Effect auraFor(String auraId, double duration) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                UnitState t = ctx.target != null ? ctx.target : ctx.caster;
                ctx.engine.applyAura(ctx, t, auraId, 1, duration);
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAura(d, auraId, false);
            }
        };
    }

    public static Effect selfAura(String auraId) {
        return selfAura(auraId, 1);
    }

    public static Effect selfAura(String auraId, int stacks) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, auraId, stacks, -1);
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAura(d, auraId, true);
            }
        };
    }

    public static Effect selfAuraFor(String auraId, double duration) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, auraId, 1, duration);
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAura(d, auraId, true);
            }
        };
    }

    public static Effect removeSelfAura(String auraId) {
        return ctx -> ctx.engine.removeAura(ctx.caster, auraId, null);
    }

    public static Effect removeTargetAura(String auraId) {
        return ctx -> {
            if (ctx.target != null) ctx.engine.removeAura(ctx.target, auraId, null);
        };
    }

    /** Removes one stack of an aura from the caster. */
    public static Effect consumeStack(String auraId) {
        return ctx -> ctx.engine.removeStacks(ctx.caster, auraId, 1);
    }

    /** Extends the caster's aura on the target (or on self) by seconds. */
    public static Effect extendAura(String auraId, double seconds, boolean onSelf) {
        return described(ctx -> {
            UnitState t = onSelf ? ctx.caster : ctx.target;
            if (t == null) return;
            AuraInstance a = onSelf ? t.auras().get(auraId) : t.auras().get(auraId, ctx.caster);
            if (a != null && !a.isPermanent()) {
                a.expiresAt += seconds;
                a.duration += seconds;
                t.auras().markDirty();
            }
        }, "Extends " + auraName(auraId, L10n.Lang.EN) + " by " + (int) seconds + " sec",
                "Продлевает " + auraName(auraId, L10n.Lang.RU) + " на " + (int) seconds + " сек.");
    }

    // ------------------------------------------------------------------ resources & cooldowns

    public static Effect energize(ResourceType t, double amount) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                ctx.engine.energize(ctx.caster, t, amount);
            }

            @Override
            public String describe(DescribeContext d) {
                String a = fmt(amount);
                return amount >= 0 ? d.t("Generates " + a + " " + t.name.en(), "Создает " + a + " ед. (" + t.name.ru() + ")")
                        : d.t("Consumes " + fmt(-amount) + " " + t.name.en(), "Расходует " + fmt(-amount) + " ед. (" + t.name.ru() + ")");
            }
        };
    }

    public static Effect energizeTarget(ResourceType t, double amount) {
        return ctx -> {
            if (ctx.target != null) ctx.engine.energize(ctx.target, t, amount);
        };
    }

    public static Effect cooldown(String abilityId, double deltaSeconds) {
        return described(ctx -> ctx.engine.adjustCooldown(ctx.caster, abilityId, deltaSeconds),
                (deltaSeconds < 0 ? "Reduces" : "Increases") + " the cooldown of " + abilityName(abilityId, L10n.Lang.EN) + " by " + fmt(Math.abs(deltaSeconds)) + " sec",
                (deltaSeconds < 0 ? "Сокращает" : "Увеличивает") + " время восстановления способности «" + abilityName(abilityId, L10n.Lang.RU) + "» на " + fmt(Math.abs(deltaSeconds)) + " сек.");
    }

    public static Effect resetCooldown(String abilityId) {
        return described(ctx -> ctx.engine.resetCooldown(ctx.caster, abilityId),
                "Resets the cooldown of " + abilityName(abilityId, L10n.Lang.EN),
                "Сбрасывает время восстановления способности «" + abilityName(abilityId, L10n.Lang.RU) + "»");
    }

    // ------------------------------------------------------------------ control

    public static Effect interrupt(double lockout) {
        return described(ctx -> {
            if (ctx.target != null) ctx.engine.interrupt(ctx.caster, ctx.target, lockout);
        }, "Interrupts spellcasting and locks the school for " + fmt(lockout) + " sec",
                "Прерывает заклинание и блокирует школу магии на " + fmt(lockout) + " сек.");
    }

    /** Removes harmful (on allies) or beneficial (on enemies) auras of the given dispel types. */
    public static Effect dispel(int count, DispelType... types) {
        return new DispelEffect(count, types);
    }

    /** Dispel effect; its types are exposed so AI can decide who to dispel. */
    public static final class DispelEffect implements Effect {
        public final Set<DispelType> types;
        public final int count;

        DispelEffect(int count, DispelType... types) {
            this.types = new HashSet<>(List.of(types));
            this.count = count;
        }

        @Override
        public void apply(EffectContext ctx) {
            if (ctx.target != null) ctx.engine.dispel(ctx.caster, ctx.target, types, count);
        }

        @Override
        public String describe(DescribeContext d) {
            StringBuilder en = new StringBuilder(), ru = new StringBuilder();
            for (DispelType t : types) {
                if (en.length() > 0) {
                    en.append(", ");
                    ru.append(", ");
                }
                en.append(t.name.en());
                ru.append(t.name.ru());
            }
            return d.t("Removes " + count + " effect(s): " + en, "Снимает эффекты (" + count + "): " + ru);
        }
    }

    public static Effect taunt() {
        return described(ctx -> {
            if (ctx.target != null) ctx.engine.taunt(ctx.caster, ctx.target, 3.0);
        }, "Taunts the target to attack you", "Вынуждает цель атаковать вас");
    }

    // ------------------------------------------------------------------ movement

    /** Rushes to the target. */
    public static Effect charge() {
        return described(ctx -> {
            if (ctx.target != null) ctx.engine.moveTo(ctx.caster, ctx.target.position(), true);
        }, "Charges to the target", "Вы устремляетесь к цели");
    }

    /** Teleports forward up to the given distance. */
    public static Effect blink(double distance) {
        return described(ctx -> {
            Vec3 dir = Vec3.fromYaw(ctx.caster.yaw());
            ctx.engine.moveTo(ctx.caster, ctx.caster.position().add(dir.mul(distance)), false);
        }, "Teleports you " + fmt(distance) + " yd forward", "Переносит вас на " + fmt(distance) + " м вперед");
    }

    /** Leaps to the ground target point. */
    public static Effect leap() {
        return described(ctx -> ctx.engine.moveTo(ctx.caster, ctx.targetPoint(), true),
                "Leaps to the target location", "Совершает прыжок в указанную точку");
    }

    /** Knocks enemies away from the caster. */
    public static Effect knockback(double strength) {
        return described(ctx -> {
            if (ctx.target != null) ctx.engine.knockback(ctx.caster.position(), ctx.target, strength, 0.45);
        }, "Knocks back", "Отбрасывает");
    }

    /** Pulls the target to the caster (Death Grip). */
    public static Effect pull() {
        return described(ctx -> {
            if (ctx.target != null) ctx.engine.moveTo(ctx.target, ctx.caster.position().add(Vec3.fromYaw(ctx.caster.yaw()).mul(1.5)), true);
        }, "Pulls the target to you", "Притягивает цель к вам");
    }

    /** Leaps backwards. */
    public static Effect disengage(double strength) {
        return described(ctx -> {
            Vec3 back = Vec3.fromYaw(ctx.caster.yaw()).mul(-strength);
            ctx.engine.pushUnit(ctx.caster, new Vec3(back.x(), 0.55, back.z()));
        }, "Leaps backwards", "Отпрыгивает назад");
    }

    /** Dashes forward. */
    public static Effect dash(double strength) {
        return described(ctx -> {
            Vec3 fwd = Vec3.fromYaw(ctx.caster.yaw()).mul(strength);
            ctx.engine.pushUnit(ctx.caster, new Vec3(fwd.x(), 0.25, fwd.z()));
        }, "Dashes forward", "Совершает рывок вперед");
    }

    // ------------------------------------------------------------------ summons & areas

    public static Effect summon(String npcTemplateId, double duration, int count) {
        return described(ctx -> {
            for (int i = 0; i < count; i++) ctx.engine.summon(ctx.caster, npcTemplateId, duration, i);
        }, "Summons " + (count > 1 ? count + " " : "") + npcTemplateId.replace('_', ' ') + (duration > 0 ? " for " + fmt(duration) + " sec" : ""),
                "Призывает " + (count > 1 ? count + " " : "") + "существо" + (duration > 0 ? " на " + fmt(duration) + " сек." : ""));
    }

    /** Creates a persistent ground effect at the target point (or under the caster for SELF/NONE abilities). */
    public static Effect area(GroundArea.Def def) {
        return described(ctx -> ctx.engine.addArea(ctx, def),
                "Creates an area for " + fmt(def.duration()) + " sec", "Создает область на " + fmt(def.duration()) + " сек.");
    }

    // ------------------------------------------------------------------ composition

    /** Delays the effects by travel time (visual projectile). */
    public static Effect projectile(double speed, Effect... onHit) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                ctx.engine.launchProjectile(ctx, speed, List.of(onHit));
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAll(d, onHit);
            }
        };
    }

    public static Effect when(Cond c, Effect then) {
        return when(c, then, null);
    }

    public static Effect when(Cond c, Effect then, Effect otherwise) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (c.test(ctx)) then.apply(ctx);
                else if (otherwise != null) otherwise.apply(ctx);
            }

            @Override
            public String describe(DescribeContext d) {
                String a = then.describe(d);
                if (a.isEmpty()) return "";
                String s = d.t("If " + c.text.en() + ": ", "Если " + c.text.ru() + ": ") + lower(a);
                if (otherwise != null && !otherwise.describe(d).isEmpty()) s += d.t(". Otherwise: ", ". Иначе: ") + lower(otherwise.describe(d));
                return s;
            }
        };
    }

    public static Effect onTargets(Selector selector, Effect... effects) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                List<UnitState> targets = selector.select(ctx);
                int n = targets.size();
                for (UnitState u : targets) {
                    EffectContext c = ctx.withTarget(u);
                    c.aoeTargets = n;
                    if (n > Formulas.AOE_SOFT_CAP) c.scale *= Math.sqrt(Formulas.AOE_SOFT_CAP / n);
                    for (Effect e : effects) e.apply(c);
                }
            }

            @Override
            public String describe(DescribeContext d) {
                String inner = describeAll(d, effects);
                if (inner.isEmpty()) return "";
                String who = selector.describe().get(d.lang());
                return who.isEmpty() ? inner : inner + " (" + who + ")";
            }
        };
    }

    public static Effect aoe(double radius, Effect... effects) {
        return onTargets(Selector.enemiesAroundTarget(radius), effects);
    }

    public static Effect aroundSelf(double radius, Effect... effects) {
        return onTargets(Selector.enemiesAroundCaster(radius), effects);
    }

    public static Effect alliesAroundSelf(double radius, Effect... effects) {
        return onTargets(Selector.alliesAroundCaster(radius), effects);
    }

    public static Effect cone(double angle, double range, Effect... effects) {
        return onTargets(Selector.cone(angle, range), effects);
    }

    /** Hits the target and then jumps to nearby targets, each jump scaled by falloff. */
    public static Effect chain(int targets, double jumpRange, double falloff, boolean friendly, Effect effect) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.target == null) return;
                List<UnitState> hit = new ArrayList<>();
                UnitState cur = ctx.target;
                double scale = 1.0;
                int max = targets + (int) ctx.caster.mods().sum(com.wowcraft.core.mod.ModType.EXTRA_TARGETS, com.wowcraft.core.mod.ModContext.of(ctx.ability));
                for (int i = 0; i < max && cur != null; i++) {
                    hit.add(cur);
                    EffectContext c = ctx.withTarget(cur);
                    c.scale *= scale;
                    effect.apply(c);
                    if (i > 0) ctx.engine.vfx("chain", hit.get(i - 1), cur, null);
                    scale *= falloff;
                    UnitState from = cur;
                    List<UnitState> next = friendly ? ctx.engine.alliesAround(ctx.caster, from.position(), jumpRange)
                            : ctx.engine.enemiesAround(ctx.caster, from.position(), jumpRange);
                    cur = null;
                    double best = Double.MAX_VALUE;
                    for (UnitState u : next) {
                        if (hit.contains(u)) continue;
                        double score = friendly ? u.healthFraction() : u.position().distanceSq(from.position());
                        if (score < best) {
                            best = score;
                            cur = u;
                        }
                    }
                }
            }

            @Override
            public String describe(DescribeContext d) {
                return effect.describe(d) + d.t(", jumping to up to " + (targets - 1) + " additional targets", ", перескакивая еще на " + (targets - 1) + " целей");
            }
        };
    }

    public static Effect all(Effect... effects) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                for (Effect e : effects) e.apply(ctx);
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAll(d, effects);
            }
        };
    }

    public static Effect chance(double p, Effect e) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.engine.rng().chance(p)) e.apply(ctx);
            }

            @Override
            public String describe(DescribeContext d) {
                String inner = e.describe(d);
                return inner.isEmpty() ? "" : d.t((int) Math.round(p * 100) + "% chance: ", "С вероятностью " + (int) Math.round(p * 100) + "%: ") + lower(inner);
            }
        };
    }

    /** Scales the inner effect by the number of stacks of an aura on the caster. */
    public static Effect perStack(String auraId, Effect e) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                int st = ctx.caster.auras().stacks(auraId);
                if (st <= 0) return;
                EffectContext c = ctx.copy();
                c.scale *= st;
                e.apply(c);
            }

            @Override
            public String describe(DescribeContext d) {
                return e.describe(d) + d.t(" per stack of " + auraName(auraId, d.lang()), " за каждый заряд эффекта «" + auraName(auraId, d.lang()) + "»");
            }
        };
    }

    /** Multiplies amounts of the inner effect. */
    public static Effect scaled(double factor, Effect e) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                EffectContext c = ctx.copy();
                c.scale *= factor;
                e.apply(c);
            }

            @Override
            public String describe(DescribeContext d) {
                return e.describe(d);
            }
        };
    }

    /** Scales by empower stage (Evoker). */
    public static Effect perEmpower(double perStage, Effect e) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                EffectContext c = ctx.copy();
                c.scale *= 1.0 + perStage * (Math.max(1, ctx.empowerStage) - 1);
                e.apply(c);
            }

            @Override
            public String describe(DescribeContext d) {
                return e.describe(d) + d.t(" (+" + (int) (perStage * 100) + "% per empower level)", " (+" + (int) (perStage * 100) + "% за уровень усиления)");
            }
        };
    }

    /** Executes another ability's effects without cost / cooldown (procs, echoes). */
    public static Effect trigger(String abilityId) {
        return described(ctx -> ctx.engine.triggerAbility(ctx.caster, abilityId, ctx.target, ctx.point),
                "Triggers " + abilityName(abilityId, L10n.Lang.EN), "Активирует способность «" + abilityName(abilityId, L10n.Lang.RU) + "»");
    }

    public static Effect resurrect(double healthFraction) {
        return described(ctx -> {
            if (ctx.target != null) ctx.engine.resurrect(ctx.caster, ctx.target, healthFraction);
        }, "Resurrects a dead ally with " + (int) (healthFraction * 100) + "% health",
                "Воскрешает павшего союзника с " + (int) (healthFraction * 100) + "% здоровья");
    }

    public static Effect vfx(String key) {
        return ctx -> ctx.engine.vfx(key, ctx.caster, ctx.target, ctx.point);
    }

    public static Effect custom(String en, String ru, Consumer<EffectContext> fn) {
        return described(fn::accept, en, ru);
    }

    public static Effect hiddenCustom(Consumer<EffectContext> fn) {
        return fn::accept;
    }


    // ------------------------------------------------------------------ timing, pets, finishers

    /** Runs the effects after a delay (sigils, totems, boss telegraphs). The target / point are captured now. */
    public static Effect delayed(double seconds, Effect... effects) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                EffectContext c = ctx.copy();
                if (c.point == null) c.point = ctx.targetPoint();
                ctx.engine.schedule(seconds, () -> {
                    if (c.caster.isDead() && c.caster.isNpcLike()) return;
                    for (Effect e : effects) e.apply(c);
                });
            }

            @Override
            public String describe(DescribeContext d) {
                String inner = describeAll(d, effects);
                return inner.isEmpty() ? "" : d.t("After " + fmt(seconds) + " sec: ", "Через " + fmt(seconds) + " сек.: ") + lower(inner);
            }
        };
    }

    /** All the caster's living pets deal damage to the target (Kill Command, Felstorm...). */
    public static Effect petDamage(School school, Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.target == null) return;
                for (UnitState pet : ctx.caster.livingPets()) {
                    if (pet.kind == com.wowcraft.core.combat.UnitKind.TOTEM) continue;
                    double base = Formulas.base(ctx.caster, ctx.target, s, ctx.comboSpent) * ctx.scale;
                    EffectContext c = ctx.withCaster(pet, ctx.target);
                    ctx.engine.dealDamage(c, ctx.target, school != null ? school : School.PHYSICAL, base);
                    ctx.engine.vfx("pet_attack", pet, ctx.target, null);
                    if (pet.body != null) ctx.engine.setTarget(pet, ctx.target);
                }
            }

            @Override
            public String describe(DescribeContext d) {
                return d.t("Your pet deals ", "Ваш питомец наносит ") + amount(d, s) + d.t(" damage", " ед. урона");
            }
        };
    }

    /** Applies an aura whose duration depends on combo points spent (Rupture, Rip, Slice and Dice). */
    public static Effect auraPerCombo(String auraId, double base, double perPoint, boolean onSelf) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                UnitState t = onSelf ? ctx.caster : (ctx.target != null ? ctx.target : ctx.caster);
                double dur = base + perPoint * Math.max(1, ctx.comboSpent);
                ctx.engine.applyAura(onSelf ? ctx.withTarget(ctx.caster) : ctx, t, auraId, 1, dur);
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAura(d, auraId, onSelf) + d.t(" (" + fmt(perPoint) + " sec per combo point)", " (" + fmt(perPoint) + " сек. за прием серии)");
            }
        };
    }

    /** Spends up to {@code max} extra resource to multiply the following effect (Ferocious Bite). */
    public static Effect spendExtra(ResourceType t, double max, double bonusAtMax, Effect e) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                double avail = Math.min(max, ctx.caster.resources().get(t));
                ctx.engine.energize(ctx.caster, t, -avail);
                EffectContext c = ctx.copy();
                c.scale *= 1.0 + bonusAtMax * (avail / max);
                e.apply(c);
            }

            @Override
            public String describe(DescribeContext d) {
                return e.describe(d) + d.t(". Consumes up to " + fmt(max) + " extra " + t.name.en() + " for up to +" + Math.round(bonusAtMax * 100) + "% damage",
                        ". Расходует до " + fmt(max) + " доп. ед. ресурса, увеличивая урон до " + Math.round(bonusAtMax * 100) + "%");
            }
        };
    }

    /** Removes N stacks of an aura from the caster. */
    public static Effect consumeStacks(String auraId, int n) {
        return ctx -> ctx.engine.removeStacks(ctx.caster, auraId, n);
    }

    /** Applies an aura to all allies (party) around the caster. */
    public static Effect groupAura(String auraId, double radius) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, radius)) {
                    ctx.engine.applyAura(ctx.withTarget(u), u, auraId, 1, -1);
                }
            }

            @Override
            public String describe(DescribeContext d) {
                return describeAura(d, auraId, false) + d.t(" to party members within " + fmt(radius) + " yd", " членам группы в радиусе " + fmt(radius) + " м");
            }
        };
    }

    /** Heals the caster for a percentage of maximum health. */
    public static Effect healPct(double fraction) {
        return selfHeal(Scaling.hp(fraction));
    }

    /** Drops the caster from all NPC threat tables (Feign Death, Vanish). */
    public static Effect dropThreat() {
        return described(ctx -> {
            for (UnitState u : ctx.engine.units()) {
                if (u.threat() != null) u.threat().remove(ctx.caster);
            }
        }, "Removes you from enemy threat lists", "Сбрасывает угрозу противников");
    }


    /** Proc helper: deals a fraction of the triggering damage to up to {@code count} other enemies near the target. */
    public static Effect cleave(double fraction, double radius, int count) {
        return described(ctx -> {
            if (ctx.target == null || ctx.triggerAmount <= 0) return;
            int n = 0;
            for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.target.position(), radius)) {
                if (u == ctx.target) continue;
                if (n++ >= count) break;
                ctx.engine.dealRawDamage(ctx.withTarget(u), u, School.PHYSICAL, ctx.triggerAmount * fraction);
            }
        }, "Your single-target attacks also hit " + count + " nearby enemies for " + Math.round(fraction * 100) + "% damage",
                "Ваши атаки по одной цели также поражают " + count + " противников рядом (" + Math.round(fraction * 100) + "% урона)");
    }

    // ------------------------------------------------------------------ helpers

    public static Effect described(Effect e, String en, String ru) {
        return Effect.described(e, en, ru);
    }

    static String describeAll(DescribeContext d, Effect... effects) {
        StringBuilder sb = new StringBuilder();
        for (Effect e : effects) {
            String s = e.describe(d);
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(". ");
            sb.append(s);
        }
        return sb.toString();
    }

    static String amount(DescribeContext d, Scaling s) {
        if (d.unit() != null) {
            double v = Formulas.base(d.unit(), null, s, 1);
            v *= 1.0 + d.unit().stats().versPct / 100.0;
            return Mth.shortNumber(v);
        }
        StringBuilder sb = new StringBuilder();
        if (s.ap() != 0) sb.append(pct(s.ap())).append(d.t(" AP", " СА"));
        if (s.sp() != 0) sb.append(sb.length() > 0 ? " + " : "").append(pct(s.sp())).append(d.t(" SP", " СЗ"));
        if (s.weapon() != 0) sb.append(sb.length() > 0 ? " + " : "").append(pct(s.weapon())).append(d.t(" weapon damage", " урона оружия"));
        if (s.maxHealth() != 0) sb.append(sb.length() > 0 ? " + " : "").append(pct(s.maxHealth())).append(d.t(" max health", " макс. здоровья"));
        if (s.flat() != 0) sb.append(sb.length() > 0 ? " + " : "").append(fmt(s.flat()));
        return "(" + sb + ")";
    }

    static String describeAura(DescribeContext d, String auraId, boolean self) {
        AuraDef a = Registry.aura(auraId);
        if (a == null) return "";
        String name = a.name.get(d.lang());
        String dur = a.duration > 0 ? d.t(" for " + fmt(a.duration) + " sec", " на " + fmt(a.duration) + " сек.") : "";
        String desc = a.description != null ? ": " + a.description.get(d.lang()) : "";
        if (a.harmful) return d.t("Afflicts the target with " + name, "Накладывает «" + name + "»") + dur + desc;
        return (self ? d.t("Grants you " + name, "Дает вам «" + name + "»") : d.t("Grants " + name, "Дает «" + name + "»")) + dur + desc;
    }

    static String auraName(String id, L10n.Lang lang) {
        AuraDef a = Registry.aura(id);
        return a == null ? id : a.name.get(lang);
    }

    static String abilityName(String id, L10n.Lang lang) {
        Ability a = Registry.ability(id);
        return a == null ? id : a.name.get(lang);
    }

    static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    static String lower(String s) {
        if (s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
