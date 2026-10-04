package com.wowcraft.core.content.classes;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.aura.Trigger;
import com.wowcraft.core.aura.TriggerType;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.mod.ModFilter;
import com.wowcraft.core.mod.ModType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.spell.Cond;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.Selector;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;

import java.util.List;

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.CHI;
import static com.wowcraft.core.resource.ResourceType.ENERGY;
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.MONK;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class MonkContent extends ClassContent {
    public MonkContent() {
        super(MONK);
    }

    private static final Cond IS_WW = Cond.custom(u -> u.spec == WINDWALKER, "Windwalker", "Танцующий с ветром");

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        cc("leg_sweep", "Leg Sweep", "Удар ногой с разворота", CcType.STUN, 3, DispelType.NONE);
        reg(debuff("paralysis", "Paralysis", "Паралич").duration(60).cc(CcType.INCAPACITATE).breakOnAnyDamage().noPandemic());
        reg(buff("fortifying_brew", "Fortifying Brew", "Укрепляющий отвар").duration(15).mod(Modifier.maxHealth(0.2), Modifier.taken(-0.2)));
        reg(debuff("mortal_wounds_monk", "Mortal Wounds", "Смертельные раны").duration(10).mod(Modifier.healingTaken(-0.25)));
        reg(buff("diffuse_magic", "Diffuse Magic", "Распыление магии").duration(6).mod(magicTaken(-0.6)));
        // Brewmaster
        reg(debuff("stagger", "Stagger", "Пошатывание").duration(10).perCaster(false).school(School.PHYSICAL).tag("stagger").noRefresh()
                .periodic(0.5, custom("Delayed damage", "Отложенный урон", ctx -> {
                    if (ctx.aura == null) return;
                    double pool = ctx.aura.getData("pool");
                    double tick = Math.max(0, pool / 20.0);
                    if (tick < 1) return;
                    ctx.aura.setData("pool", pool - tick);
                    ctx.engine.dealRawDamage(ctx.withCaster(ctx.target, ctx.target), ctx.target, School.PHYSICAL, tick);
                })).unhastedTicks().desc("Damage is delayed and taken over time.", "Урон откладывается и наносится постепенно."));
        reg(passive("brewmaster_basics", "Stagger", "Пошатывание").mod(Modifier.taken(-0.35), Modifier.stat(Stat.STAMINA, 0.15),
                        Modifier.of(ModType.ARMOR_PCT, 0.5))
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, custom("Staggers part of the damage", "Часть урона откладывается", ctx -> {
                    double staggered = ctx.triggerAmount * (0.35 / 0.65);
                    AuraInstance s = ctx.caster.auras().get("stagger");
                    if (s == null) s = ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, "stagger", 1, -1);
                    if (s != null) {
                        s.setData("pool", s.getData("pool") + staggered);
                        s.expiresAt = ctx.now() + 10;
                    }
                })).filter(ModFilter.notTag("stagger")).self()));
        reg(buff("celestial_brew", "Celestial Brew", "Небесный отвар").duration(8).absorb().noPandemic());
        reg(debuff("keg_smash", "Keg Smash", "Удар бочонком").duration(15).mod(Modifier.speed(-0.2)).cc(CcType.SLOW));
        reg(debuff("breath_of_fire", "Breath of Fire", "Огненное дыхание").duration(12).school(School.FIRE).tag("dot")
                .mod(Modifier.damage(-0.05)).periodic(2, damage(School.FIRE, ap(0.1))));
        // Mistweaver
        reg(buff("renewing_mist", "Renewing Mist", "Заживляющий туман").duration(20).school(School.NATURE).tag("hot").dispel(DispelType.MAGIC)
                .periodic(2, heal(sp(0.32))));
        reg(buff("enveloping_mist", "Enveloping Mist", "Окутывающий туман").duration(6).school(School.NATURE).tag("hot").dispel(DispelType.MAGIC)
                .mod(Modifier.healingTaken(0.1)).periodic(1, heal(sp(0.55))));
        reg(buff("life_cocoon", "Life Cocoon", "Исцеляющий кокон").duration(12).absorb().mod(Modifier.healingTaken(0.5)).vfx("cocoon"));
        reg(buff("thunder_focus_tea", "Thunder Focus Tea", "Чай громовой концентрации").duration(30)
                .mod(Modifier.abilityHealing("vivify", 1.0), Modifier.abilityHealing("enveloping_mist", 1.0), Modifier.cooldown("rising_sun_kick", -9))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("vivify")).consumeStack())
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("enveloping_mist")).consumeStack()));
        reg(passive("mistweaver_basics", "Ancient Teachings", "Древние учения").mod(Modifier.healing(0.05))
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, custom("Damage dealt heals injured allies", "Урон исцеляет раненых союзников", ctx -> {
                    List<UnitState> l = Selector.injuredAllies(30, 2).select(ctx.withTarget(ctx.caster));
                    for (UnitState u : l) {
                        EffectContext hc = new EffectContext(ctx.engine, ctx.caster, u, null, null, ctx.aura);
                        ctx.engine.heal(hc, u, ctx.triggerAmount * 0.7 / Math.max(1, l.size()), true);
                    }
                })).icd(0.2)));
        // Windwalker
        reg(buff("storm_earth_and_fire", "Storm, Earth, and Fire", "Буря, земля и огонь").duration(15).mod(Modifier.damage(0.35)).vfx("sef"));
        reg(buff("touch_of_karma", "Touch of Karma", "Закрепление кармы").duration(10).absorb()
                .onRemove(karmaDamage()).onExpire(karmaDamage()));
        reg(buff("combo_breaker", "Blackout Kick!", "Удар черного быка!").duration(15).mod(Modifier.cost("blackout_kick", -1.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("blackout_kick")).consumeStack()));
        reg(passive("windwalker_basics", "Combo Breaker", "Комбо-удар").mod(Modifier.damage(0.05))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("combo_breaker")).filter(ModFilter.ability("tiger_palm")).chance(0.15))
                .trigger(Trigger.on(TriggerType.CAST, cooldown("rising_sun_kick", -1)).filter(ModFilter.ability("blackout_kick")))
                .trigger(Trigger.on(TriggerType.CAST, cooldown("fists_of_fury", -1)).filter(ModFilter.ability("blackout_kick"))));
        reg(buff("flying_serpent_kick", "Flying Serpent Kick", "Удар летящего змея").duration(0.1).hidden());

        // ------------------------------------------------------------ class abilities
        reg(ab("tiger_palm", "Tiger Palm", "Лапа тигра").cost(ENERGY, 50).gcd(1.0)
                .effect(damage(ap(0.65)), when(IS_WW, energize(CHI, 2)), when(Cond.custom(u -> u.spec == BREWMASTER, "Brewmaster", "Хмелевар"),
                        all(cooldown("purifying_brew", -1), cooldown("celestial_brew", -1)))).tag("builder", "weapon").vfx("palm"));
        reg(ab("blackout_kick", "Blackout Kick", "Удар черного быка").cost(CHI, 1).cooldown(0).gcd(1.0)
                .effect(damage(ap(1.0))).tag("weapon").vfx("kick"));
        reg(ab("rising_sun_kick", "Rising Sun Kick", "Удар восходящего солнца").cost(CHI, 2).cooldown(10).hastedCooldown().gcd(1.0)
                .effect(damage(ap(2.0)), aura("mortal_wounds_monk")).tag("weapon").vfx("rising_sun"));
        reg(ab("spinning_crane_kick", "Spinning Crane Kick", "Танцующий журавль").target(TargetType.NONE).cost(CHI, 2).gcd(1.0)
                .channel(1.5, 0.375).moving().tick(aroundSelf(8, damage(ap(0.28)))).tag("aoe").vfx("crane_kick"));
        reg(ab("roll", "Roll", "Кувырок").target(TargetType.SELF).cooldown(20).charges(2).offGcd().effect(dash(1.4)).tag("movement"));
        reg(ab("leg_sweep", "Leg Sweep", "Удар ногой с разворота").target(TargetType.NONE).cooldown(60).gcd(1.0)
                .effect(aroundSelf(6, aura("leg_sweep"))).tag("cc", "aoe").vfx("leg_sweep"));
        reg(ab("paralysis", "Paralysis", "Паралич").range(20).cooldown(45).cost(ENERGY, 20).gcd(1.0).effect(aura("paralysis")).tag("cc"));
        reg(ab("spear_hand_strike", "Spear Hand Strike", "Рука-копье").cooldown(15).offGcd().effect(interrupt(3)).tag("interrupt"));
        reg(ab("vivify", "Vivify", "Оживить").school(School.NATURE).target(TargetType.FRIENDLY).cast(1.5).cost(MANA, 3.4).cost(ENERGY, 30)
                .effect(heal(sp(1.5)), custom("Also heals allies with Renewing Mist", "Также исцеляет союзников с «Заживляющим туманом»", ctx -> {
                    for (UnitState u : ctx.engine.alliesAround(ctx.caster, ctx.caster.position(), 40)) {
                        if (u == ctx.target || u.auras().get("renewing_mist", ctx.caster) == null) continue;
                        ctx.engine.heal(ctx.withTarget(u), u, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(0.6), 1));
                    }
                })).tag("heal").vfx("mist_heal"));
        reg(ab("touch_of_death", "Touch of Death", "Касание смерти").cooldown(180).gcd(1.0)
                .requires(Cond.of(c -> c.target != null && (c.target.health() <= c.caster.maxHealth() || c.target.healthFraction() < 0.15),
                        "target health below your maximum health or below 15%", "здоровье цели меньше вашего макс. здоровья или ниже 15%"))
                .effect(custom("Kills the target, or deals damage equal to 35% of your maximum health",
                        "Убивает цель или наносит урон, равный 35% вашего макс. здоровья", ctx -> {
                            if (ctx.target == null) return;
                            double amount = ctx.target.health() <= ctx.caster.maxHealth() && !ctx.target.boss && !ctx.target.isPlayerLike()
                                    ? ctx.target.health() + 1 : ctx.caster.maxHealth() * 0.35;
                            ctx.engine.dealRawDamage(ctx, ctx.target, School.PHYSICAL, amount);
                        })).tag("execute", "cooldown").vfx("touch_of_death"));
        reg(ab("fortifying_brew", "Fortifying Brew", "Укрепляющий отвар").target(TargetType.SELF).cooldown(240).offGcd()
                .effect(selfAura("fortifying_brew"), healPct(0.2)).tag("defensive", "major_defensive"));
        reg(ab("provoke", "Provoke", "Провокация").range(30).cooldown(8).offGcd().effect(taunt()).tag("taunt"));
        reg(ab("detox", "Detox", "Детоксикация").school(School.NATURE).target(TargetType.FRIENDLY).cooldown(8).cost(ENERGY, 20)
                .effect(dispel(3, DispelType.POISON, DispelType.DISEASE)).tag("dispel"));
        reg(ab("expel_harm", "Expel Harm", "Устранение вреда").school(School.NATURE).target(TargetType.SELF).cooldown(15).cost(ENERGY, 15)
                .gcd(1.0).effect(selfHeal(ap(1.5)), when(IS_WW, energize(CHI, 1)), aroundSelf(8, damage(ap(0.3)))).tag("heal"));
        reg(ab("diffuse_magic", "Diffuse Magic", "Распыление магии").target(TargetType.SELF).cooldown(90).offGcd()
                .effect(dispel(5, DispelType.MAGIC), selfAura("diffuse_magic")).tag("defensive"));

        // ------------------------------------------------------------ Brewmaster
        reg(ab("keg_smash", "Keg Smash", "Удар бочонком").spec(BREWMASTER).range(15).cost(ENERGY, 40).cooldown(8).hastedCooldown().gcd(1.0)
                .effect(aoe(8, damage(ap(1.2)), aura("keg_smash")), cooldown("purifying_brew", -3), cooldown("celestial_brew", -3)).tag("aoe").vfx("keg"));
        reg(ab("breath_of_fire", "Breath of Fire", "Огненное дыхание").spec(BREWMASTER).school(School.FIRE).target(TargetType.NONE)
                .cooldown(15).hastedCooldown().gcd(1.0).effect(cone(90, 12, damage(ap(0.5)), aura("breath_of_fire"))).tag("aoe").vfx("breath_of_fire"));
        reg(ab("purifying_brew", "Purifying Brew", "Очищающий отвар").spec(BREWMASTER).target(TargetType.SELF).cooldown(20).charges(2)
                .hastedCooldown().offGcd().effect(custom("Purifies 50% of your staggered damage", "Очищает 50% отложенного урона", ctx -> {
                    AuraInstance s = ctx.caster.auras().get("stagger");
                    if (s != null) s.setData("pool", s.getData("pool") * 0.5);
                })).tag("defensive", "active_mitigation"));
        reg(ab("celestial_brew", "Celestial Brew", "Небесный отвар").spec(BREWMASTER).target(TargetType.SELF).cooldown(45).gcd(1.0)
                .effect(selfAbsorb("celestial_brew", ap(3.0))).tag("defensive", "active_mitigation"));
        reg(ab("invoke_niuzao", "Invoke Niuzao, the Black Ox", "Призыв Нюцзао, Черного Быка").spec(BREWMASTER).target(TargetType.SELF)
                .cooldown(180).gcd(1.0).effect(summon("niuzao", 25, 1)).tag("cooldown", "pet"));
        reg(ab("black_ox_brew", "Black Ox Brew", "Отвар Черного Быка").spec(BREWMASTER).target(TargetType.SELF).cooldown(120).offGcd()
                .effect(resetCooldown("purifying_brew"), resetCooldown("celestial_brew"), energize(ENERGY, 100)).tag("defensive"));
        reg(ab("rushing_jade_wind", "Rushing Jade Wind", "Порыв нефритового ветра").spec(BREWMASTER, WINDWALKER).target(TargetType.NONE)
                .cooldown(6).hastedCooldown().gcd(1.0).effect(aroundSelf(8, damage(ap(0.6)))).tag("aoe"));

        // ------------------------------------------------------------ Mistweaver
        reg(ab("renewing_mist", "Renewing Mist", "Заживляющий туман").spec(MISTWEAVER).school(School.NATURE).target(TargetType.FRIENDLY)
                .cost(MANA, 1.8).cooldown(9).charges(2).effect(aura("renewing_mist")).tag("heal", "hot").vfx("mist"));
        reg(ab("enveloping_mist", "Enveloping Mist", "Окутывающий туман").spec(MISTWEAVER).school(School.NATURE).target(TargetType.FRIENDLY)
                .cast(2.0).cost(MANA, 5.6).effect(aura("enveloping_mist")).tag("heal", "hot").vfx("mist"));
        reg(ab("soothing_mist", "Soothing Mist", "Успокаивающий туман").spec(MISTWEAVER).school(School.NATURE).target(TargetType.FRIENDLY)
                .cost(MANA, 0.4).channel(8, 1).tick(heal(sp(0.55))).tag("heal").vfx("mist"));
        reg(ab("revival", "Revival", "Возрождение").spec(MISTWEAVER).school(School.NATURE).target(TargetType.NONE).cooldown(180).cost(MANA, 4.4)
                .effect(onTargets(Selector.group(40), heal(sp(2.5)), dispel(1, DispelType.MAGIC, DispelType.POISON, DispelType.DISEASE)))
                .tag("heal", "raid_cd").vfx("revival"));
        reg(ab("life_cocoon", "Life Cocoon", "Исцеляющий кокон").spec(MISTWEAVER).school(School.NATURE).target(TargetType.FRIENDLY)
                .cooldown(120).offGcd().usableWhileCc().effect(absorb("life_cocoon", sp(4.5))).tag("external"));
        reg(ab("thunder_focus_tea", "Thunder Focus Tea", "Чай громовой концентрации").spec(MISTWEAVER).target(TargetType.SELF).cooldown(30).offGcd()
                .effect(selfAura("thunder_focus_tea")).tag("cooldown"));
        reg(ab("invoke_yulon", "Invoke Yu'lon, the Jade Serpent", "Призыв Юй-лун, Нефритовой Змеи").spec(MISTWEAVER).target(TargetType.SELF)
                .cooldown(120).effect(summon("yulon", 25, 1), custom("Heals your party over time", "Исцеляет группу", ctx -> {
                    for (int i = 0; i < 12; i++) {
                        ctx.engine.schedule(i * 2.0, () -> {
                            for (UnitState u : Selector.injuredAllies(40, 3).select(ctx.withTarget(ctx.caster)))
                                ctx.engine.heal(ctx.withTarget(u), u, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(0.5), 1));
                        });
                    }
                })).tag("raid_cd", "heal"));
        reg(ab("sheiluns_gift", "Sheilun's Gift", "Дар Шейлуна").spec(MISTWEAVER).school(School.NATURE).target(TargetType.FRIENDLY).cast(2.0)
                .cooldown(25).cost(MANA, 2.4).effect(onTargets(Selector.injuredAllies(30, 3), heal(sp(1.6)))).tag("heal", "aoe_heal"));

        // ------------------------------------------------------------ Windwalker
        reg(ab("fists_of_fury", "Fists of Fury", "Неистовые кулаки").spec(WINDWALKER).target(TargetType.NONE).cost(CHI, 3).cooldown(24)
                .hastedCooldown().gcd(1.0).channel(4, 0.8).tick(cone(90, 8, damage(ap(0.65)))).tag("aoe").vfx("fists_of_fury"));
        reg(ab("strike_of_the_windlord", "Strike of the Windlord", "Удар Владыки Ветра").spec(WINDWALKER).target(TargetType.NONE).cost(CHI, 2)
                .cooldown(40).gcd(1.0).effect(cone(90, 9, damage(ap(2.5)))).tag("aoe", "cooldown").vfx("windlord"));
        reg(ab("storm_earth_and_fire", "Storm, Earth, and Fire", "Буря, земля и огонь").spec(WINDWALKER).target(TargetType.SELF).cooldown(90)
                .charges(2).offGcd().effect(selfAura("storm_earth_and_fire")).tag("cooldown"));
        reg(ab("touch_of_karma", "Touch of Karma", "Закрепление кармы").spec(WINDWALKER).range(20).cooldown(90).offGcd()
                .effect(custom("Absorbs damage equal to 50% of your maximum health; absorbed damage is redirected to the target",
                        "Поглощает урон в размере 50% макс. здоровья и перенаправляет его на цель", ctx -> {
                            AuraInstance a = ctx.engine.applyAbsorb(ctx.withTarget(ctx.caster), ctx.caster, "touch_of_karma", ctx.caster.maxHealth() * 0.5);
                            if (a != null && ctx.target != null) a.setData("target", ctx.target.id);
                        })).tag("defensive"));
        reg(ab("flying_serpent_kick", "Flying Serpent Kick", "Удар летящего змея").spec(WINDWALKER).target(TargetType.SELF).cooldown(25).gcd(1.0)
                .effect(dash(2.2), delayed(0.6, aroundSelf(8, damage(ap(0.5)), aura("generic_slow")))).tag("movement"));
        reg(ab("whirling_dragon_punch", "Whirling Dragon Punch", "Удар крутящегося дракона").spec(WINDWALKER).target(TargetType.NONE).cooldown(24)
                .gcd(1.0).effect(aroundSelf(8, damage(ap(1.6)))).tag("aoe"));

        // ------------------------------------------------------------ kit
        kit.common("tiger_palm", "blackout_kick", "rising_sun_kick", "spinning_crane_kick", "roll", "leg_sweep", "paralysis",
                "spear_hand_strike", "vivify", "touch_of_death", "fortifying_brew", "provoke", "detox", "expel_harm");
        kit.spec(BREWMASTER, "keg_smash", "breath_of_fire", "purifying_brew", "celestial_brew", "invoke_niuzao", "black_ox_brew");
        kit.spec(MISTWEAVER, "renewing_mist", "enveloping_mist", "soothing_mist", "revival", "life_cocoon", "thunder_focus_tea", "invoke_yulon");
        kit.spec(WINDWALKER, "fists_of_fury", "strike_of_the_windlord", "storm_earth_and_fire", "touch_of_karma", "flying_serpent_kick");
        kit.passive(BREWMASTER, "brewmaster_basics");
        kit.passive(MISTWEAVER, "mistweaver_basics");
        kit.passive(WINDWALKER, "windwalker_basics");
        kit.bar(BREWMASTER, "keg_smash", "tiger_palm", "blackout_kick", "breath_of_fire", "spinning_crane_kick", "purifying_brew",
                "celestial_brew", "spear_hand_strike", "provoke", "fortifying_brew", "expel_harm", "invoke_niuzao");
        kit.bar(MISTWEAVER, "renewing_mist", "vivify", "enveloping_mist", "soothing_mist", "rising_sun_kick", "tiger_palm", "blackout_kick",
                "detox", "life_cocoon", "revival", "thunder_focus_tea", "invoke_yulon");
        kit.bar(WINDWALKER, "tiger_palm", "blackout_kick", "rising_sun_kick", "fists_of_fury", "spinning_crane_kick", "strike_of_the_windlord",
                "storm_earth_and_fire", "spear_hand_strike", "touch_of_karma", "touch_of_death", "flying_serpent_kick", "fortifying_brew");

        kit.rotation(BREWMASTER, of(
                self("fortifying_brew").when(selfHealthBelow(0.3)).urgent(),
                self("celestial_brew").when(selfHealthBelow(0.75)).urgent(),
                self("purifying_brew").when(Cond.custom(u -> {
                    AuraInstance s = u.auras().get("stagger");
                    return s != null && s.getData("pool") > u.maxHealth() * 0.15;
                }, "heavy stagger", "сильное пошатывание")).urgent(),
                self("expel_harm").when(selfHealthBelow(0.6)).urgent(),
                interrupt("spear_hand_strike"),
                use("keg_smash"),
                use("breath_of_fire").when(enemiesAround(12, 1)),
                use("blackout_kick"),
                use("rushing_jade_wind").when(enemiesAround(8, 3)),
                use("spinning_crane_kick").when(enemiesAround(8, 3).and(resourceAtLeast(ENERGY, 65))),
                use("tiger_palm").when(resourceAtLeast(ENERGY, 65))));
        kit.rotation(MISTWEAVER, of(
                heal("life_cocoon").below(0.25).urgent(),
                use("revival").on(com.wowcraft.core.bot.BotTarget.SELF).when(Cond.alliesBelow(0.45, 3)).urgent(),
                self("fortifying_brew").when(selfHealthBelow(0.3)).urgent(),
                dispel("detox"),
                heal("renewing_mist").below(0.95),
                heal("enveloping_mist").below(0.55),
                heal("vivify").below(0.75),
                use("rising_sun_kick"),
                use("blackout_kick"),
                use("tiger_palm")));
        kit.rotation(WINDWALKER, of(
                self("fortifying_brew").when(selfHealthBelow(0.3)).urgent(),
                use("touch_of_karma").when(selfHealthBelow(0.6)).urgent(),
                self("expel_harm").when(selfHealthBelow(0.5)).urgent(),
                interrupt("spear_hand_strike"),
                use("touch_of_death"),
                self("storm_earth_and_fire"),
                use("strike_of_the_windlord"),
                use("fists_of_fury"),
                use("rising_sun_kick"),
                use("spinning_crane_kick").when(enemiesAround(8, 3)),
                use("blackout_kick").when(resourceAtLeast(CHI, 3).or(Cond.hasAura("combo_breaker"))),
                use("tiger_palm").when(resourceBelow(CHI, 4)),
                use("blackout_kick")));

        // ------------------------------------------------------------ talents
        classTree(
                t("celerity", 0, 0, "Celerity", "Проворство").mod(Modifier.charges("roll", 1), Modifier.cooldown("roll", -5)),
                t("tigers_lust", 0, 1, "Tiger's Lust", "Тигриное рвение").mod(Modifier.speed(0.08)),
                t("improved_paralysis", 0, 2, "Improved Paralysis", "Улучшенный паралич").mod(Modifier.cooldown("paralysis", -15)),
                t("diffuse_magic_talent", 1, 0, "Diffuse Magic", "Распыление магии").grant("diffuse_magic"),
                t("ironshell_brew", 1, 1, "Ironshell Brew", "Отвар железной скорлупы").mod(Modifier.duration("fortifying_brew", 5), Modifier.cooldown("fortifying_brew", -60)),
                t("vivacious_vivification", 1, 2, "Vivacious Vivification", "Живое оживление").mod(Modifier.abilityHealing("vivify", 0.2)),
                t("ferocity_of_xuen", 2, 0, "Ferocity of Xuen", "Свирепость Сюэня").mod(Modifier.damage(0.04)),
                t("chi_wave", 2, 1, "Chi Wave", "Волна ци").mod(Modifier.healing(0.04)),
                t("bounce_back", 2, 2, "Bounce Back", "Ответный удар").mod(Modifier.taken(-0.04)));
        specTree(BREWMASTER,
                t("gift_of_the_ox", 0, 0, "Gift of the Ox", "Дар быка").mod(Modifier.abilityHealing("expel_harm", 0.3)),
                t("light_brewing", 0, 1, "Light Brewing", "Легкое пивоварение").mod(Modifier.cooldownPct("purifying_brew", -0.2)),
                t("black_ox_brew_talent", 0, 2, "Black Ox Brew", "Отвар Черного Быка").grant("black_ox_brew"),
                t("rushing_jade_wind_talent", 1, 0, "Rushing Jade Wind", "Порыв нефритового ветра").grant("rushing_jade_wind"),
                t("stormstouts_last_keg", 1, 1, "Stormstout's Last Keg", "Последний бочонок Буйных Портеров").mod(Modifier.charges("keg_smash", 1)),
                t("high_tolerance", 1, 2, "High Tolerance", "Высокая сопротивляемость").mod(Modifier.taken(-0.04)),
                t("celestial_flames", 2, 0, "Celestial Flames", "Небесное пламя").mod(Modifier.abilityDamage("breath_of_fire", 0.4)),
                t("improved_celestial_brew", 2, 1, "Improved Celestial Brew", "Улучшенный небесный отвар").mod(Modifier.of(ModType.ABSORB_DONE, ModFilter.ability("celestial_brew"), 0.3)),
                t("fortifying_brew_determination", 2, 2, "Niuzao's Resolve", "Решимость Нюцзао").mod(Modifier.cooldown("invoke_niuzao", -60)));
        specTree(MISTWEAVER,
                t("mist_wrap", 0, 0, "Mist Wrap", "Туманное покрывало").mod(Modifier.duration("enveloping_mist", 1)),
                t("rising_mist", 0, 1, "Rising Mist", "Восходящий туман").mod(Modifier.abilityDamage("rising_sun_kick", 0.2)),
                t("sheiluns_gift_talent", 0, 2, "Sheilun's Gift", "Дар Шейлуна").grant("sheiluns_gift"),
                t("focused_thunder", 1, 0, "Focused Thunder", "Сконцентрированный гром").mod(Modifier.cooldown("thunder_focus_tea", -10)),
                t("mana_tea", 1, 1, "Mana Tea", "Чай маны").mod(Modifier.regen(MANA.name(), 0.25)),
                t("calming_coalescence", 1, 2, "Calming Coalescence", "Успокаивающее слияние").mod(Modifier.of(ModType.ABSORB_DONE, ModFilter.ability("life_cocoon"), 0.3)),
                t("uplifted_spirits", 2, 0, "Uplifted Spirits", "Воспрянувший дух").mod(Modifier.cooldown("revival", -60)),
                t("secret_infusion", 2, 1, "Secret Infusion", "Тайная настойка").mod(Modifier.healing(0.06)),
                t("invokers_delight", 2, 2, "Invoker's Delight", "Отрада призывателя").mod(Modifier.cooldown("invoke_yulon", -30)));
        specTree(WINDWALKER,
                t("glory_of_the_dawn", 0, 0, "Glory of the Dawn", "Слава рассвета").mod(Modifier.abilityDamage("rising_sun_kick", 0.2)),
                t("hit_combo", 0, 1, "Hit Combo", "Серия ударов").mod(Modifier.damage(0.06)),
                t("whirling_dragon_punch_talent", 0, 2, "Whirling Dragon Punch", "Удар крутящегося дракона").grant("whirling_dragon_punch"),
                t("flashing_fists", 1, 0, "Flashing Fists", "Мелькающие кулаки").mod(Modifier.abilityDamage("fists_of_fury", 0.2)),
                t("shadowboxing_treads", 1, 1, "Shadowboxing Treads", "Боксерские тапочки").mod(Modifier.abilityDamage("blackout_kick", 0.25)),
                t("inner_peace", 1, 2, "Inner Peace", "Внутренний покой").mod(Modifier.ref(ModType.RESOURCE_MAX, ENERGY.name(), 30)),
                t("fury_of_xuen", 2, 0, "Fury of Xuen", "Ярость Сюэня").mod(Modifier.cooldown("storm_earth_and_fire", -15)),
                t("thunderfist", 2, 1, "Thunderfist", "Громовой кулак").mod(Modifier.abilityDamage("strike_of_the_windlord", 0.3)),
                t("dance_of_chi_ji", 2, 2, "Dance of Chi-Ji", "Танец Чи-Цзи").mod(Modifier.abilityDamage("spinning_crane_kick", 0.4)));
        hero(t("hero_master_of_harmony", 0, 0, "Master of Harmony", "Мастер гармонии").mod(Modifier.healing(0.06), Modifier.taken(-0.04)),
                t("hero_shado_pan", 0, 1, "Shado-Pan", "Шадо-Пан").mod(Modifier.damage(0.06), Modifier.haste(0.02)));
    }

    private static com.wowcraft.core.spell.Effect karmaDamage() {
        return custom("Redirects absorbed damage", "Перенаправляет поглощенный урон", ctx -> {
            if (ctx.aura == null) return;
            double absorbed = ctx.aura.absorbMax - Math.max(0, ctx.aura.absorbRemaining);
            UnitState t = ctx.engine.unit((int) ctx.aura.getData("target"));
            if (t == null || t.isDead() || absorbed <= 0) return;
            ctx.engine.dealRawDamage(new EffectContext(ctx.engine, ctx.target, t, null, null, ctx.aura), t, School.NATURE, absorbed * 0.7);
            ctx.engine.vfx("karma", ctx.target, t, null);
        });
    }
}
