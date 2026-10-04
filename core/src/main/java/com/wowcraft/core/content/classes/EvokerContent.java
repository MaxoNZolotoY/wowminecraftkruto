package com.wowcraft.core.content.classes;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.aura.Trigger;
import com.wowcraft.core.aura.TriggerType;
import com.wowcraft.core.combat.GroundArea;
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
import static com.wowcraft.core.resource.ResourceType.ESSENCE;
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.EVOKER;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class EvokerContent extends ClassContent {
    public EvokerContent() {
        super(EVOKER);
    }

    private static final Cond HOSTILE_TARGET = Cond.of(c -> c.target != null && c.engine.isHostile(c.caster, c.target),
            "target is an enemy", "цель — противник");

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(debuff("fire_breath", "Fire Breath", "Огненное дыхание").duration(20).school(School.FIRE).tag("dot").dispel(DispelType.MAGIC)
                .periodic(2, damage(School.FIRE, sp(0.18))));
        reg(buff("hover", "Hover", "Парение").duration(6).mod(Modifier.of(ModType.CAST_WHILE_MOVING, 1), Modifier.speed(0.3)));
        dr("obsidian_scales", "Obsidian Scales", "Обсидиановая чешуя", 0.3, 12);
        reg(debuff("sleep_walk", "Sleep Walk", "Сомнамбулизм").duration(20).cc(CcType.SLEEP).breakOnAnyDamage().dispel(DispelType.MAGIC).noPandemic());
        reg(debuff("landslide", "Landslide", "Обвал").duration(15).cc(CcType.ROOT).breakOnDamage(0.1).dispel(DispelType.MAGIC));
        reg(buff("renewing_blaze", "Renewing Blaze", "Обновляющее пламя").duration(8).periodic(1, healPct(0.04)).unhastedTicks());
        reg(buff("fury_of_the_aspects", "Fury of the Aspects", "Ярость Аспектов").duration(40).mod(Modifier.haste(0.3)).vfx("lust"));
        reg(buff("blessing_of_the_bronze", "Blessing of the Bronze", "Благословение бронзы").duration(3600).persistent()
                .mod(Modifier.speed(0.05), Modifier.of(ModType.COOLDOWN_PCT, ModFilter.tag("movement"), -0.15)));
        reg(buff("essence_burst", "Essence Burst", "Взрыв сущности").duration(15).stacks(2)
                .mod(Modifier.cost("disintegrate", -1.0), Modifier.cost("pyre", -1.0), Modifier.cost("echo", -1.0), Modifier.cost("emerald_blossom", -1.0),
                        Modifier.cost("eruption", -1.0)).flatMods()
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.tag("essence_spender")).consumeStack()));
        reg(passive("evoker_basics", "Essence Burst", "Взрыв сущности")
                .trigger(Trigger.on(TriggerType.CAST, selfAura("essence_burst")).filter(ModFilter.ability("living_flame")).chance(0.2))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("essence_burst")).filter(ModFilter.ability("azure_strike")).chance(0.15)));
        // Devastation
        reg(buff("dragonrage", "Dragonrage", "Ярость дракона").duration(18).mod(Modifier.haste(0.15), Modifier.damage(0.1)).vfx("dragonrage"));
        reg(debuff("shattering_star", "Shattering Star", "Звездный раскол").duration(4).mod(Modifier.taken(0.2)));
        reg(passive("devastation_basics", "Charged Blast", "Заряженный взрыв").mod(Modifier.damage(0.18)));
        // Preservation
        reg(buff("dream_breath", "Dream Breath", "Дыхание сновидений").duration(16).school(School.NATURE).tag("hot").periodic(2, heal(sp(0.2))));
        reg(buff("reversion", "Reversion", "Реверсия").duration(12).school(School.ARCANE).tag("hot").dispel(DispelType.MAGIC).periodic(2, heal(sp(0.36))));
        reg(buff("echo", "Echo", "Эхо").duration(15).desc("Your next heal on this target is duplicated at 70%.", "Ваше следующее исцеление этой цели повторится на 70%."));
        reg(passive("echo_watch", "Echo", "Эхо").trigger(Trigger.on(TriggerType.HEAL_DEALT, custom("Echo duplicates heals", "Эхо повторяет исцеление", ctx -> {
            if (ctx.target == null) return;
            AuraInstance e = ctx.target.auras().get("echo");
            if (e == null || e.caster != ctx.caster) return;
            ctx.engine.removeAura(e, false);
            ctx.engine.rawHeal(ctx.caster, ctx.target, ctx.triggerAmount * 0.7);
            ctx.engine.vfx("echo", ctx.caster, ctx.target, null);
        }))));
        reg(buff("time_dilation", "Time Dilation", "Замедление времени").duration(8).mod(Modifier.taken(-0.5)).vfx("time_dilation"));
        reg(passive("preservation_basics", "Life-Binder", "Хранитель жизни").mod(Modifier.healing(0.05)));
        // Augmentation
        reg(buff("ebon_might", "Ebon Might", "Мощь черного камня").duration(10).mod(Modifier.stat(Stat.STRENGTH, 0.07), Modifier.stat(Stat.AGILITY, 0.07),
                Modifier.stat(Stat.INTELLECT, 0.07)).desc("Primary stat increased by 7%.", "Основная характеристика увеличена на 7%.").vfx("ebon_might"));
        reg(buff("prescience", "Prescience", "Предвидение").duration(18).mod(Modifier.crit(5)).vfx("prescience"));
        reg(debuff("breath_of_eons", "Breath of Eons", "Дыхание эпох").duration(10).perCaster(false).mod(Modifier.taken(0.1)));
        reg(buff("blistering_scales", "Blistering Scales", "Нестерпимая чешуя").duration(600).stacks(15).noRefresh()
                .mod(Modifier.of(ModType.ARMOR_PCT, 0.02))
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, all(custom("Damages the attacker", "Наносит урон атакующему", ctx -> {
                    if (ctx.target != null && ctx.aura != null && ctx.target != ctx.caster)
                        ctx.engine.dealRawDamage(new EffectContext(ctx.engine, ctx.aura.caster, ctx.target, null, null, ctx.aura), ctx.target, School.FIRE,
                                com.wowcraft.core.combat.Formulas.base(ctx.aura.caster, ctx.target, sp(0.25), 1));
                }))).icd(2).consumeStack()));
        reg(passive("augmentation_basics", "Timewalker", "Странник во времени").mod(Modifier.damage(0.03)));

        // ------------------------------------------------------------ class abilities
        reg(ab("azure_strike", "Azure Strike", "Лазурный удар").school(School.ARCANE).range(25).cost(MANA, 0.9)
                .effect(chain(2, 8, 1.0, false, damage(sp(0.55)))).vfx("azure_strike"));
        reg(ab("living_flame", "Living Flame", "Живое пламя").school(School.FIRE).target(TargetType.ANY).range(25).cast(2.0).cost(MANA, 2)
                .effect(projectile(30, when(HOSTILE_TARGET, damage(sp(1.05)), heal(sp(1.6))))).tag("heal").vfx("living_flame"));
        reg(ab("fire_breath", "Fire Breath", "Огненное дыхание").school(School.FIRE).target(TargetType.NONE).empower(3).cooldown(30).cost(MANA, 2.6)
                .effect(cone(90, 20, perEmpower(0.5, damage(sp(0.9))), aura("fire_breath"))).tag("aoe").vfx("fire_breath"));
        reg(ab("emerald_blossom", "Emerald Blossom", "Изумрудный цветок").school(School.NATURE).target(TargetType.FRIENDLY).cost(ESSENCE, 3)
                .effect(delayed(2, onTargets(Selector.injuredAllies(10, 3), heal(sp(1.3))))).tag("heal", "essence_spender").vfx("emerald_blossom"));
        reg(ab("verdant_embrace", "Verdant Embrace", "Изумрудные объятия").school(School.NATURE).target(TargetType.FRIENDLY).range(30).cooldown(24)
                .cost(MANA, 2).effect(custom("Fly to the ally", "Вы подлетаете к союзнику", ctx -> {
                    if (ctx.target != null && ctx.target != ctx.caster) ctx.engine.moveTo(ctx.caster, ctx.target.position(), true);
                }), heal(sp(2.5))).tag("heal", "movement"));
        reg(ab("hover", "Hover", "Парение").target(TargetType.SELF).cooldown(35).charges(2).offGcd().effect(selfAura("hover")).tag("movement"));
        reg(ab("obsidian_scales", "Obsidian Scales", "Обсидиановая чешуя").target(TargetType.SELF).cooldown(90).charges(1).offGcd().usableWhileCc()
                .effect(selfAura("obsidian_scales")).tag("defensive", "major_defensive"));
        reg(ab("quell", "Quell", "Подавление").range(25).cooldown(20).offGcd().effect(interrupt(4)).tag("interrupt"));
        reg(ab("sleep_walk", "Sleep Walk", "Сомнамбулизм").school(School.NATURE).range(25).cast(1.7).cooldown(15).cost(MANA, 1)
                .effect(aura("sleep_walk")).tag("cc"));
        reg(ab("tail_swipe", "Tail Swipe", "Удар хвостом").target(TargetType.NONE).cooldown(90).effect(aroundSelf(8, damage(sp(0.3)), aura("generic_knockdown")))
                .tag("cc", "aoe"));
        reg(ab("wing_buffet", "Wing Buffet", "Взмах крыльями").target(TargetType.NONE).cooldown(90).effect(cone(90, 15, knockback(1.4))).tag("utility"));
        reg(ab("fury_of_the_aspects", "Fury of the Aspects", "Ярость Аспектов").target(TargetType.SELF).cooldown(300).offGcd()
                .effect(custom("Increases haste of all party members by 30% for 40 sec", "Увеличивает скорость всех членов группы на 30% на 40 сек.", ctx -> {
                    for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, 60)) {
                        if (u.auras().has("exhaustion")) continue;
                        ctx.engine.applyAura(ctx.withTarget(u), u, "fury_of_the_aspects", 1, -1);
                        ctx.engine.applyAura(ctx.withTarget(u), u, "exhaustion", 1, -1);
                    }
                })).tag("cooldown", "lust"));
        reg(ab("expunge", "Expunge", "Нейтрализация").school(School.NATURE).target(TargetType.FRIENDLY).cooldown(8).cost(MANA, 1)
                .effect(dispel(3, DispelType.POISON)).tag("dispel"));
        reg(ab("cauterizing_flame", "Cauterizing Flame", "Прижигающее пламя").school(School.FIRE).target(TargetType.FRIENDLY).cooldown(60)
                .effect(dispel(3, DispelType.BLEED, DispelType.POISON, DispelType.CURSE, DispelType.DISEASE), heal(sp(0.8))).tag("dispel"));
        reg(ab("renewing_blaze", "Renewing Blaze", "Обновляющее пламя").school(School.FIRE).target(TargetType.SELF).cooldown(90).offGcd()
                .effect(selfAura("renewing_blaze")).tag("defensive"));
        reg(ab("blessing_of_the_bronze", "Blessing of the Bronze", "Благословение бронзы").school(School.ARCANE).target(TargetType.SELF)
                .effect(groupAura("blessing_of_the_bronze", 40)).tag("buff"));
        reg(ab("deep_breath", "Deep Breath", "Глубокий вдох").school(School.FIRE).target(TargetType.GROUND).range(50).cooldown(120)
                .effect(custom("Fly to the location, breathing fire on enemies in your path", "Перелет в точку с огненным дыханием по пути", ctx -> {
                    var from = ctx.caster.position();
                    var to = ctx.targetPoint();
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, from.lerp(to, 0.5), from.distance(to) / 2 + 6)) {
                        var rel = u.position().sub(from).horizontal();
                        var dir = to.sub(from).horizontal().normalize();
                        double along = rel.dot(dir);
                        double side = rel.sub(dir.mul(along)).length();
                        if (along >= -2 && along <= from.distance(to) + 2 && side <= 6) {
                            ctx.engine.dealDamage(ctx.withTarget(u), u, School.FIRE, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(2.0), 1));
                            ctx.engine.applyAura(ctx.withTarget(u), u, "generic_stun", 1, -1);
                        }
                    }
                    ctx.engine.moveTo(ctx.caster, to, true);
                })).tag("aoe", "cooldown", "movement").vfx("deep_breath"));
        reg(ab("landslide", "Landslide", "Обвал").target(TargetType.GROUND).range(30).cooldown(90).effect(onTargets(Selector.groundEnemies(8), aura("landslide")))
                .tag("cc", "aoe"));

        // ------------------------------------------------------------ Devastation
        reg(ab("disintegrate", "Disintegrate", "Распад").spec(DEVASTATION).school(School.ARCANE).range(25).cost(ESSENCE, 3).channel(3, 0.75)
                .tick(damage(sp(0.9)), aura("generic_slow")).tag("essence_spender").vfx("disintegrate"));
        reg(ab("pyre", "Pyre", "Погребальный костер").spec(DEVASTATION).school(School.FIRE).range(25).cost(ESSENCE, 3)
                .effect(aoe(8, damage(sp(1.0)))).tag("aoe", "essence_spender").vfx("pyre"));
        reg(ab("eternity_surge", "Eternity Surge", "Вечный прилив").spec(DEVASTATION).school(School.ARCANE).range(25).empower(3).cooldown(30)
                .effect(custom("Hits 1 / 2 / 3 targets depending on empower level", "Поражает 1/2/3 цели в зависимости от уровня усиления", ctx -> {
                    List<UnitState> l = new java.util.ArrayList<>();
                    if (ctx.target != null) l.add(ctx.target);
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.targetPoint(), 15)) {
                        if (l.size() >= Math.max(1, ctx.empowerStage) * 2) break;
                        if (!l.contains(u)) l.add(u);
                    }
                    for (UnitState u : l) ctx.engine.dealDamage(ctx.withTarget(u), u, School.ARCANE, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(2.2), 1));
                })).tag("aoe").vfx("eternity_surge"));
        reg(ab("dragonrage", "Dragonrage", "Ярость дракона").spec(DEVASTATION).target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("dragonrage"), selfAura("essence_burst", 2), aroundSelf(25, damage(sp(0.6)))).tag("cooldown"));
        reg(ab("shattering_star", "Shattering Star", "Звездный раскол").spec(DEVASTATION).school(School.ARCANE).range(25).cooldown(20)
                .effect(damage(sp(1.2)), aura("shattering_star"), selfAura("essence_burst")).tag("cooldown"));
        reg(ab("firestorm", "Firestorm", "Огненная буря").spec(DEVASTATION).school(School.FIRE).target(TargetType.GROUND).range(25).cast(2.0)
                .cooldown(20).effect(area(GroundArea.Def.enemies("firestorm", 8, 6, 1, damage(School.FIRE, sp(0.35))).color(0xFFFF6000))).tag("aoe"));

        // ------------------------------------------------------------ Preservation
        reg(ab("dream_breath", "Dream Breath", "Дыхание сновидений").spec(PRESERVATION).school(School.NATURE).target(TargetType.NONE).empower(3)
                .cooldown(30).cost(MANA, 4.5)
                .effect(onTargets(Selector.alliesAroundCaster(20), perEmpower(0.5, heal(sp(1.0))), aura("dream_breath"))).tag("heal", "aoe_heal").vfx("dream_breath"));
        reg(ab("spiritbloom", "Spiritbloom", "Дух цветения").spec(PRESERVATION).school(School.NATURE).target(TargetType.FRIENDLY).empower(3)
                .cooldown(30).cost(MANA, 3.8).effect(custom("Heals 1 / 2 / 3 injured allies depending on empower level",
                        "Исцеляет 1/2/3 раненых союзника в зависимости от уровня усиления", ctx -> {
                            List<UnitState> l = Selector.injuredAllies(30, Math.max(1, ctx.empowerStage)).select(ctx);
                            for (UnitState u : l) ctx.engine.heal(ctx.withTarget(u), u, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(3.0), 1));
                        })).tag("heal").vfx("spiritbloom"));
        reg(ab("echo", "Echo", "Эхо").spec(PRESERVATION).school(School.ARCANE).target(TargetType.FRIENDLY).cost(ESSENCE, 2)
                .effect(heal(sp(0.8)), aura("echo")).tag("heal", "essence_spender"));
        reg(ab("reversion", "Reversion", "Реверсия").spec(PRESERVATION).school(School.ARCANE).target(TargetType.FRIENDLY).cooldown(9).charges(2)
                .cost(MANA, 2).effect(aura("reversion")).tag("heal", "hot"));
        reg(ab("rewind", "Rewind", "Перемотка").spec(PRESERVATION).school(School.ARCANE).target(TargetType.NONE).cooldown(240)
                .effect(onTargets(Selector.group(40), custom("Heals 35% of maximum health", "Восполняет 35% макс. здоровья", ctx ->
                        ctx.engine.heal(ctx, ctx.target, ctx.target.maxHealth() * 0.35, true)))).tag("heal", "raid_cd").vfx("rewind"));
        reg(ab("time_dilation", "Time Dilation", "Замедление времени").spec(PRESERVATION).school(School.ARCANE).target(TargetType.FRIENDLY)
                .cooldown(60).offGcd().effect(aura("time_dilation")).tag("external"));
        reg(ab("naturalize", "Naturalize", "Природное очищение").spec(PRESERVATION).school(School.NATURE).target(TargetType.FRIENDLY).cooldown(8)
                .cost(MANA, 1).effect(dispel(3, DispelType.MAGIC, DispelType.POISON)).tag("dispel"));
        reg(ab("dream_flight", "Dream Flight", "Полет сновидений").spec(PRESERVATION).school(School.NATURE).target(TargetType.GROUND).range(40).cooldown(120)
                .effect(onTargets(Selector.groundAllies(15), heal(sp(2.0)), aura("dream_breath")), leap()).tag("heal", "raid_cd"));
        reg(ab("emerald_communion", "Emerald Communion", "Изумрудное единение").spec(PRESERVATION).school(School.NATURE).target(TargetType.SELF)
                .cooldown(180).channel(5, 1).tick(healPct(0.1), energize(MANA, 600)).tag("defensive"));

        // ------------------------------------------------------------ Augmentation
        reg(ab("ebon_might", "Ebon Might", "Мощь черного камня").spec(AUGMENTATION).school(School.FIRE).target(TargetType.SELF).cast(1.5).cooldown(30)
                .effect(selfAura("ebon_might"), onTargets(Selector.group(40), aura("ebon_might"))).tag("buff", "cooldown"));
        reg(ab("eruption", "Eruption", "Извержение").spec(AUGMENTATION).school(School.FIRE).range(25).cast(2.5).cost(ESSENCE, 2)
                .effect(aoe(6, damage(sp(1.5))), custom("Extends Ebon Might by 1 sec", "Продлевает «Мощь черного камня» на 1 сек.", ctx -> {
                    for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, 40)) {
                        AuraInstance a = u.auras().get("ebon_might");
                        if (a != null) a.expiresAt += 1;
                    }
                })).tag("aoe", "essence_spender").vfx("eruption"));
        reg(ab("upheaval", "Upheaval", "Сдвиг земли").spec(AUGMENTATION).school(School.FIRE).range(25).empower(3).cooldown(40)
                .effect(aoe(8, perEmpower(0.4, damage(sp(1.6))))).tag("aoe").vfx("upheaval"));
        reg(ab("prescience", "Prescience", "Предвидение").spec(AUGMENTATION).school(School.ARCANE).target(TargetType.FRIENDLY).cooldown(12).charges(2)
                .effect(aura("prescience")).tag("buff"));
        reg(ab("breath_of_eons", "Breath of Eons", "Дыхание эпох").spec(AUGMENTATION).school(School.ARCANE).target(TargetType.GROUND).range(40)
                .cooldown(120).effect(onTargets(Selector.groundEnemies(12), aura("breath_of_eons"), damage(sp(1.0))), leap()).tag("cooldown", "aoe"));
        reg(ab("blistering_scales", "Blistering Scales", "Нестерпимая чешуя").spec(AUGMENTATION).school(School.FIRE).target(TargetType.FRIENDLY)
                .cooldown(30).effect(aura("blistering_scales", 15)).tag("buff"));
        reg(ab("time_skip", "Time Skip", "Пропуск времени").spec(AUGMENTATION).school(School.ARCANE).target(TargetType.NONE).cooldown(180)
                .effect(custom("Reduces your party's cooldowns by 15 sec", "Сокращает время восстановления способностей группы на 15 сек.", ctx -> {
                    for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, 40)) {
                        if (u == ctx.caster) continue;
                        for (String id : new java.util.ArrayList<>(u.cooldowns().all().keySet())) u.cooldowns().adjust(id, -15, ctx.now());
                    }
                })).tag("cooldown"));

        // ------------------------------------------------------------ kit
        kit.common("azure_strike", "living_flame", "fire_breath", "emerald_blossom", "verdant_embrace", "hover", "obsidian_scales", "quell",
                "sleep_walk", "tail_swipe", "wing_buffet", "fury_of_the_aspects", "expunge", "cauterizing_flame", "renewing_blaze",
                "blessing_of_the_bronze", "deep_breath");
        kit.classPassives.add("evoker_basics");
        kit.spec(DEVASTATION, "disintegrate", "pyre", "eternity_surge", "dragonrage", "shattering_star");
        kit.spec(PRESERVATION, "dream_breath", "spiritbloom", "echo", "reversion", "rewind", "time_dilation", "naturalize", "emerald_communion");
        kit.spec(AUGMENTATION, "ebon_might", "eruption", "upheaval", "prescience", "breath_of_eons", "blistering_scales", "time_skip");
        kit.passive(DEVASTATION, "devastation_basics");
        kit.passive(PRESERVATION, "preservation_basics", "echo_watch");
        kit.passive(AUGMENTATION, "augmentation_basics");
        kit.bar(DEVASTATION, "living_flame", "azure_strike", "disintegrate", "pyre", "fire_breath", "eternity_surge", "shattering_star",
                "quell", "dragonrage", "hover", "obsidian_scales", "deep_breath");
        kit.bar(PRESERVATION, "living_flame", "reversion", "echo", "emerald_blossom", "dream_breath", "spiritbloom", "verdant_embrace",
                "naturalize", "time_dilation", "rewind", "hover", "obsidian_scales");
        kit.bar(AUGMENTATION, "living_flame", "azure_strike", "ebon_might", "eruption", "upheaval", "fire_breath", "prescience",
                "quell", "breath_of_eons", "blistering_scales", "hover", "obsidian_scales");

        kit.rotation(DEVASTATION, of(
                self("obsidian_scales").when(selfHealthBelow(0.35)).urgent(),
                self("renewing_blaze").when(selfHealthBelow(0.5)).urgent(),
                interrupt("quell"),
                self("dragonrage"),
                use("shattering_star"),
                use("fire_breath"),
                use("eternity_surge"),
                use("pyre").when(enemiesAround(25, 3).and(resourceAtLeast(ESSENCE, 3).or(Cond.hasAura("essence_burst")))),
                use("disintegrate").when(resourceAtLeast(ESSENCE, 3).or(Cond.hasAura("essence_burst"))),
                use("azure_strike").when(enemiesAround(25, 2)),
                use("living_flame")));
        kit.rotation(PRESERVATION, of(
                use("rewind").on(com.wowcraft.core.bot.BotTarget.SELF).when(Cond.alliesBelow(0.4, 3)).urgent(),
                heal("time_dilation").below(0.3).urgent(),
                self("obsidian_scales").when(selfHealthBelow(0.35)).urgent(),
                dispel("naturalize"),
                use("dream_breath").on(com.wowcraft.core.bot.BotTarget.SELF).when(Cond.alliesBelow(0.8, 3)),
                heal("spiritbloom").below(0.5),
                heal("echo").below(0.8).when(resourceAtLeast(ESSENCE, 2).or(Cond.hasAura("essence_burst"))),
                heal("reversion").below(0.9),
                heal("emerald_blossom").below(0.7).when(resourceAtLeast(ESSENCE, 3)),
                heal("living_flame").below(0.75),
                use("living_flame")));
        kit.rotation(AUGMENTATION, of(
                self("obsidian_scales").when(selfHealthBelow(0.35)).urgent(),
                interrupt("quell"),
                self("ebon_might").when(Cond.hasAura("ebon_might").not()),
                heal("prescience").on(com.wowcraft.core.bot.BotTarget.TANK).when(Cond.targetHasAnyAura("prescience").not()),
                use("upheaval"),
                use("fire_breath"),
                use("eruption").when(resourceAtLeast(ESSENCE, 2).or(Cond.hasAura("essence_burst"))),
                use("living_flame")));

        // ------------------------------------------------------------ talents
        classTree(
                t("natural_convergence", 0, 0, "Natural Convergence", "Естественное слияние").mod(Modifier.castTime("living_flame", -0.2)),
                t("aerial_mastery", 0, 1, "Aerial Mastery", "Мастерство полета").mod(Modifier.charges("hover", 1)),
                t("inherent_resistance", 0, 2, "Inherent Resistance", "Врожденная устойчивость").mod(magicTaken(-0.04)),
                t("landslide_talent", 1, 0, "Landslide", "Обвал").grant("landslide"),
                t("obsidian_bulwark", 1, 1, "Obsidian Bulwark", "Обсидиановый бастион").mod(Modifier.charges("obsidian_scales", 1)),
                t("instinctive_arcana", 1, 2, "Instinctive Arcana", "Инстинктивная магия").mod(Modifier.stat(Stat.INTELLECT, 0.04)),
                t("tip_the_scales", 2, 0, "Tip the Scales", "Перевес чаши").mod(Modifier.cooldown("fire_breath", -6)),
                t("lush_growth", 2, 1, "Lush Growth", "Пышный рост").mod(Modifier.abilityHealing("emerald_blossom", 0.3), Modifier.abilityHealing("verdant_embrace", 0.3)),
                t("extended_flight", 2, 2, "Extended Flight", "Продолжительный полет").mod(Modifier.duration("hover", 4)));
        specTree(DEVASTATION,
                t("ruby_embers", 0, 0, "Ruby Embers", "Рубиновые угли").mod(Modifier.abilityDamage("living_flame", 0.2)),
                t("arcane_intensity", 0, 1, "Arcane Intensity", "Чародейская интенсивность").mod(Modifier.abilityDamage("disintegrate", 0.15)),
                t("firestorm_talent", 0, 2, "Firestorm", "Огненная буря").grant("firestorm"),
                t("volatility", 1, 0, "Volatility", "Изменчивость").mod(Modifier.abilityDamage("pyre", 0.2)),
                t("eternitys_span", 1, 1, "Eternity's Span", "Пролет вечности").mod(Modifier.abilityDamage("eternity_surge", 0.3)),
                t("power_nexus", 1, 2, "Power Nexus", "Средоточие силы").mod(Modifier.ref(ModType.RESOURCE_MAX, ESSENCE.name(), 1)),
                t("animosity", 2, 0, "Animosity", "Неприязнь").mod(Modifier.duration("dragonrage", 5)),
                t("focusing_iris", 2, 1, "Focusing Iris", "Фокусирующая радужка").mod(Modifier.cooldown("shattering_star", -5)),
                t("scintillation", 2, 2, "Scintillation", "Мерцание").mod(Modifier.crit(4)));
        specTree(PRESERVATION,
                t("fluttering_seedlings", 0, 0, "Fluttering Seedlings", "Порхающие ростки").mod(Modifier.abilityHealing("emerald_blossom", 0.2)),
                t("grace_period", 0, 1, "Grace Period", "Льготный период").mod(Modifier.of(ModType.HEALING_DONE, ModFilter.aura("reversion"), 0.3)),
                t("dream_flight_talent", 0, 2, "Dream Flight", "Полет сновидений").grant("dream_flight"),
                t("time_lord", 1, 0, "Time Lord", "Повелитель времени").mod(Modifier.abilityHealing("echo", 0.3)),
                t("spiritual_clarity", 1, 1, "Spiritual Clarity", "Духовная ясность").mod(Modifier.cooldown("spiritbloom", -10)),
                t("temporal_artificer", 1, 2, "Temporal Artificer", "Временной умелец").mod(Modifier.cooldown("rewind", -60)),
                t("call_of_ysera", 2, 0, "Call of Ysera", "Зов Изеры").mod(Modifier.abilityHealing("dream_breath", 0.3)),
                t("emerald_communion_talent", 2, 1, "Lifebind", "Связь жизни").mod(Modifier.healing(0.05)),
                t("timeless_magic", 2, 2, "Timeless Magic", "Вневременная магия").mod(Modifier.regen(MANA.name(), 0.2)));
        specTree(AUGMENTATION,
                t("ricocheting_pyroclast", 0, 0, "Ricocheting Pyroclast", "Рикошетящий пирокласт").mod(Modifier.abilityDamage("eruption", 0.2)),
                t("tectonic_locus", 0, 1, "Tectonic Locus", "Тектонический локус").mod(Modifier.abilityDamage("upheaval", 0.3)),
                t("time_skip_talent", 0, 2, "Time Skip", "Пропуск времени").grant("time_skip"),
                t("momentum_shift", 1, 0, "Momentum Shift", "Сдвиг импульса").mod(Modifier.duration("ebon_might", 3)),
                t("infernos_blessing", 1, 1, "Inferno's Blessing", "Благословение инферно").mod(Modifier.duration("prescience", 6)),
                t("blistering_scales_talent", 1, 2, "Blistering Scales", "Нестерпимая чешуя").grant("blistering_scales"),
                t("interwoven_threads", 2, 0, "Interwoven Threads", "Переплетенные нити").mod(Modifier.cooldown("breath_of_eons", -30)),
                t("fate_mirror", 2, 1, "Fate Mirror", "Зеркало судьбы").mod(Modifier.damage(0.05)),
                t("dream_of_spring", 2, 2, "Dream of Spring", "Весенний сон").mod(Modifier.cost("eruption", -0.5)));
        hero(t("hero_flameshaper", 0, 0, "Flameshaper", "Формовщик пламени").mod(Modifier.schoolDamage(School.FIRE, 0.1), Modifier.healing(0.04)),
                t("hero_chronowarden", 0, 1, "Chronowarden", "Хранитель времени").mod(Modifier.haste(0.05), Modifier.healing(0.04)));
    }
}
