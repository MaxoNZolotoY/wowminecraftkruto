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

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.RUNES;
import static com.wowcraft.core.resource.ResourceType.RUNIC_POWER;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.DEATH_KNIGHT;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class DeathKnightContent extends ClassContent {
    public DeathKnightContent() {
        super(DEATH_KNIGHT);
    }

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(buff("anti_magic_shell", "Anti-Magic Shell", "Антимагический панцирь").duration(5).absorbMagic().mod(Modifier.immune("SILENCE"))
                .vfx("ams"));
        reg(buff("icebound_fortitude", "Icebound Fortitude", "Незыблемость льда").duration(8).mod(Modifier.taken(-0.3), Modifier.immune("STUN"))
                .vfx("icebound"));
        reg(buff("lichborne", "Lichborne", "Перерождение").duration(10).mod(Modifier.leech(10), Modifier.immune("FEAR"), Modifier.immune("SLEEP")));
        reg(debuff("chains_of_ice", "Chains of Ice", "Ледяные оковы").duration(8).school(School.FROST).mod(Modifier.speed(-0.7)).cc(CcType.SLOW)
                .dispel(DispelType.MAGIC));
        reg(debuff("blood_plague", "Blood Plague", "Кровавая чума").duration(24).school(School.SHADOW).tag("dot", "disease")
                .dispel(DispelType.DISEASE).periodic(3, all(damage(School.SHADOW, ap(0.16)), drainLast(0.5))));
        reg(debuff("frost_fever", "Frost Fever", "Озноб").duration(24).school(School.FROST).tag("dot", "disease", "frost")
                .dispel(DispelType.DISEASE).periodic(3, all(damage(School.FROST, ap(0.18)), energize(RUNIC_POWER, 2))));
        reg(debuff("virulent_plague", "Virulent Plague", "Вирулентная чума").duration(27).school(School.SHADOW).tag("dot", "disease")
                .dispel(DispelType.DISEASE).periodic(3, damage(School.SHADOW, ap(0.2))));
        reg(passive("death_and_decay_buff", "Death and Decay", "Смерть и разложение").hidden());
        cc("asphyxiate", "Asphyxiate", "Удушение", CcType.STUN, 4, DispelType.NONE);
        // Blood
        reg(buff("bone_shield", "Bone Shield", "Костяной щит").duration(30).stacks(10).mod(Modifier.taken(-0.02))
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, ctx -> {
                }).consumeStack().icd(2)).vfx("bone_shield"));
        reg(buff("blood_shield", "Blood Shield", "Щит крови").duration(10).absorb().noPandemic());
        reg(buff("vampiric_blood", "Vampiric Blood", "Кровь вампира").duration(10).mod(Modifier.maxHealth(0.3), Modifier.healingTaken(0.3)).vfx("vampiric_blood"));
        reg(buff("dancing_rune_weapon", "Dancing Rune Weapon", "Танцующее руническое оружие").duration(8)
                .mod(Modifier.taken(-0.25), Modifier.damage(0.25)).vfx("rune_weapon"));
        dr("rune_tap", "Rune Tap", "Захват рун", 0.2, 4);
        reg(passive("blood_basics", "Veteran of the Third War", "Ветеран Третьей войны")
                .mod(Modifier.stat(Stat.STAMINA, 0.15), Modifier.of(ModType.ARMOR_PCT, 0.5)));
        // Frost
        reg(buff("killing_machine", "Killing Machine", "Машина смерти").duration(10).stacks(2)
                .mod(Modifier.abilityCrit("obliterate", 100))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("obliterate")).consumeStack()));
        reg(buff("rime", "Rime", "Иней").duration(15).mod(Modifier.cost("howling_blast", -1.0), Modifier.abilityDamage("howling_blast", 2.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("howling_blast")).consumeStack()));
        reg(buff("pillar_of_frost", "Pillar of Frost", "Ледяной столп").duration(12).mod(Modifier.stat(Stat.STRENGTH, 0.25)).vfx("pillar_of_frost"));
        reg(buff("empower_rune_weapon", "Empower Rune Weapon", "Усиление рунического оружия").duration(20)
                .mod(Modifier.haste(0.15), Modifier.regen(RUNES.name(), 0.5)).periodic(5, all(energize(RUNES, 1), energize(RUNIC_POWER, 5))));
        reg(passive("frost_dk_basics", "Killing Machine", "Машина смерти").mod(Modifier.damage(-0.05))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, selfAura("killing_machine")).chance(0.3))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("rime")).filter(ModFilter.ability("obliterate")).chance(0.45)));
        // Unholy
        reg(debuff("festering_wound", "Festering Wound", "Гнойная рана").duration(30).stacks(6).school(School.SHADOW)
                .desc("Bursts when struck by Scourge Strike, Apocalypse or Death Coil.", "Лопается от «Удара Плети» и «Апокалипсиса»."));
        reg(buff("dark_transformation", "Dark Transformation", "Темное превращение").duration(15).mod(Modifier.damage(1.0)).vfx("dark_transformation"));
        reg(buff("sudden_doom", "Sudden Doom", "Внезапная обреченность").duration(10).mod(Modifier.cost("death_coil", -1.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("death_coil")).consumeStack()));
        reg(passive("unholy_basics", "Sudden Doom", "Внезапная обреченность").mod(Modifier.schoolDamage(School.SHADOW, 0.05))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, selfAura("sudden_doom")).chance(0.2)));

        // ------------------------------------------------------------ class abilities
        reg(ab("death_strike", "Death Strike", "Удар смерти").cost(RUNIC_POWER, 40)
                .effect(damage(ap(1.2)), custom("Heals you for 25% of damage taken in the last 5 sec (minimum 7% of maximum health)",
                        "Восполняет 25% урона, полученного за последние 5 сек. (не менее 7% макс. здоровья)", ctx -> {
                            double amount = Math.max(ctx.caster.maxHealth() * 0.07, ctx.caster.recentDamageTaken(ctx.now(), 5) * 0.25);
                            var hit = ctx.engine.heal(ctx.withTarget(ctx.caster), ctx.caster, amount, true);
                            if (ctx.caster.spec == BLOOD && hit != null) {
                                double shield = hit.amount * ctx.caster.stats().masteryPct / 100.0;
                                ctx.engine.applyAbsorb(ctx.withTarget(ctx.caster), ctx.caster, "blood_shield", shield);
                            }
                        })).tag("weapon", "heal").vfx("death_strike"));
        reg(ab("death_grip", "Death Grip", "Хватка смерти").school(School.SHADOW).range(30).cooldown(25).offGcd()
                .effect(pull(), taunt()).tag("utility", "taunt").vfx("death_grip"));
        reg(ab("mind_freeze", "Mind Freeze", "Заморозка разума").school(School.FROST).range(15).cooldown(15).offGcd().effect(interrupt(3))
                .tag("interrupt"));
        reg(ab("anti_magic_shell", "Anti-Magic Shell", "Антимагический панцирь").school(School.SHADOW).target(TargetType.SELF).cooldown(60)
                .offGcd().gen(RUNIC_POWER, 10).effect(selfAbsorb("anti_magic_shell", hp(0.3))).tag("defensive"));
        reg(ab("icebound_fortitude", "Icebound Fortitude", "Незыблемость льда").school(School.FROST).target(TargetType.SELF).cooldown(180).offGcd()
                .usableWhileCc().effect(selfAura("icebound_fortitude")).tag("defensive", "major_defensive"));
        reg(ab("death_and_decay", "Death and Decay", "Смерть и разложение").school(School.SHADOW).target(TargetType.GROUND).range(30)
                .cost(RUNES, 1).cooldown(30).gen(RUNIC_POWER, 10)
                .effect(area(GroundArea.Def.enemies("death_and_decay", 8, 10, 1, damage(School.SHADOW, ap(0.15))).color(0xFF40FF40)))
                .tag("aoe").vfx("death_and_decay"));
        reg(ab("raise_ally", "Raise Ally", "Воскрешение союзника").school(School.SHADOW).target(TargetType.DEAD_FRIENDLY).cost(RUNIC_POWER, 30)
                .cooldown(600).effect(resurrect(0.6)).tag("battle_res"));
        reg(ab("chains_of_ice", "Chains of Ice", "Ледяные оковы").school(School.FROST).range(30).cost(RUNES, 1).effect(aura("chains_of_ice")).tag("slow"));
        reg(ab("dark_command", "Dark Command", "Темная власть").school(School.SHADOW).range(30).cooldown(8).offGcd().effect(taunt()).tag("taunt"));
        reg(ab("lichborne", "Lichborne", "Перерождение").school(School.SHADOW).target(TargetType.SELF).cooldown(120).offGcd().usableWhileCc()
                .effect(selfAura("lichborne")).tag("defensive"));
        reg(ab("raise_dead", "Raise Dead", "Воскрешение мертвых").school(School.SHADOW).target(TargetType.SELF).cooldown(120)
                .effect(when(Cond.custom(u -> u.spec == UNHOLY, "Unholy", "Нечестивость"), when(Cond.hasPet().not(), summon("ghoul", 0, 1)),
                        summon("ghoul", 60, 1))).tag("pet"));
        reg(ab("asphyxiate", "Asphyxiate", "Удушение").school(School.SHADOW).range(20).cooldown(45).effect(aura("asphyxiate")).tag("cc"));
        reg(ab("anti_magic_zone", "Anti-Magic Zone", "Зона антимагии").school(School.SHADOW).target(TargetType.NONE).cooldown(120)
                .effect(area(GroundArea.Def.allies("anti_magic_zone", 8, 8, 1, aura("anti_magic_zone_buff")).centeredOnCaster().color(0xFF60FF60)))
                .tag("raid_cd"));
        reg(buff("anti_magic_zone_buff", "Anti-Magic Zone", "Зона антимагии").duration(1.5).mod(magicTaken(-0.2)).hidden());

        // ------------------------------------------------------------ Blood
        reg(ab("heart_strike", "Heart Strike", "Удар в сердце").spec(BLOOD).cost(RUNES, 1).gen(RUNIC_POWER, 10)
                .effect(damage(ap(0.9)), onTargets(Selector.enemiesAroundTargetExcept(5), damage(ap(0.6)))).tag("weapon").vfx("blood_strike"));
        reg(ab("marrowrend", "Marrowrend", "Перемалывание костей").spec(BLOOD).cost(RUNES, 2).gen(RUNIC_POWER, 20)
                .effect(damage(ap(1.4)), selfAura("bone_shield", 3)).tag("weapon").vfx("marrowrend"));
        reg(ab("blood_boil", "Blood Boil", "Вскипание крови").spec(BLOOD).school(School.SHADOW).target(TargetType.NONE).cooldown(7.5)
                .charges(2).hastedCooldown().effect(aroundSelf(10, damage(ap(0.6)), aura("blood_plague"))).tag("aoe").vfx("blood_boil"));
        reg(ab("vampiric_blood", "Vampiric Blood", "Кровь вампира").spec(BLOOD).target(TargetType.SELF).cooldown(90).offGcd()
                .effect(selfAura("vampiric_blood")).tag("defensive", "major_defensive"));
        reg(ab("dancing_rune_weapon", "Dancing Rune Weapon", "Танцующее руническое оружие").spec(BLOOD).target(TargetType.SELF).cooldown(120)
                .effect(selfAura("dancing_rune_weapon"), selfAura("bone_shield", 5)).tag("defensive", "cooldown"));
        reg(ab("rune_tap", "Rune Tap", "Захват рун").spec(BLOOD).target(TargetType.SELF).cost(RUNES, 1).cooldown(25).charges(2).offGcd()
                .effect(selfAura("rune_tap")).tag("defensive"));
        reg(ab("blooddrinker", "Blooddrinker", "Кровопийца").spec(BLOOD).school(School.SHADOW).range(30).cooldown(30).cost(RUNES, 1)
                .channel(3, 0.75).tick(damage(ap(0.35)), drainLast(1.0)).tag("heal"));

        // ------------------------------------------------------------ Frost
        reg(ab("obliterate", "Obliterate", "Уничтожение").spec(FROST_DK).cost(RUNES, 2).gen(RUNIC_POWER, 20)
                .effect(damage(ap(1.7))).tag("weapon", "frost").vfx("obliterate"));
        reg(ab("frost_strike", "Frost Strike", "Ледяной удар").spec(FROST_DK).school(School.FROST).cost(RUNIC_POWER, 30)
                .effect(damage(ap(1.25))).tag("frost").vfx("frost_strike"));
        reg(ab("howling_blast", "Howling Blast", "Воющий ветер").spec(FROST_DK).school(School.FROST).ranged().cost(RUNES, 1).gen(RUNIC_POWER, 10)
                .effect(damage(ap(0.6)), aura("frost_fever"), aoe(8, damage(ap(0.35)), aura("frost_fever"))).tag("frost", "aoe").vfx("howling_blast"));
        reg(ab("remorseless_winter", "Remorseless Winter", "Беспощадная зима").spec(FROST_DK).school(School.FROST).target(TargetType.NONE)
                .cooldown(20).cost(RUNES, 1).gen(RUNIC_POWER, 10)
                .effect(area(GroundArea.Def.enemies("remorseless_winter", 8, 8, 1, all(damage(School.FROST, ap(0.22)), aura("generic_slow"))).following()
                        .color(0xFF80FFFF))).tag("aoe", "frost").vfx("remorseless_winter"));
        reg(ab("pillar_of_frost", "Pillar of Frost", "Ледяной столп").spec(FROST_DK).target(TargetType.SELF).cooldown(60).offGcd()
                .effect(selfAura("pillar_of_frost")).tag("cooldown"));
        reg(ab("empower_rune_weapon", "Empower Rune Weapon", "Усиление рунического оружия").spec(FROST_DK).target(TargetType.SELF).cooldown(120)
                .offGcd().effect(selfAura("empower_rune_weapon"), energize(RUNES, 1), energize(RUNIC_POWER, 5)).tag("cooldown"));
        reg(ab("frostwyrms_fury", "Frostwyrm's Fury", "Ярость ледяного змея").spec(FROST_DK).school(School.FROST).target(TargetType.NONE)
                .cooldown(90).effect(onTargets(Selector.line(25, 6), damage(ap(3.0)), aura("generic_stun"))).tag("aoe", "cooldown").vfx("frostwyrm"));
        reg(ab("breath_of_sindragosa", "Breath of Sindragosa", "Дыхание Синдрагосы").spec(FROST_DK).school(School.FROST).target(TargetType.NONE)
                .cooldown(120).cost(RUNIC_POWER, 50).channel(6, 1).moving()
                .tick(cone(90, 12, damage(ap(0.7))), energize(RUNES, 1)).tag("aoe", "cooldown").vfx("frost_breath"));

        // ------------------------------------------------------------ Unholy
        reg(ab("festering_strike", "Festering Strike", "Гнойный удар").spec(UNHOLY).cost(RUNES, 2).gen(RUNIC_POWER, 20)
                .effect(damage(ap(1.4)), aura("festering_wound", 3)).tag("weapon").vfx("plague_strike"));
        reg(ab("scourge_strike", "Scourge Strike", "Удар Плети").spec(UNHOLY).cost(RUNES, 1).gen(RUNIC_POWER, 10)
                .effect(damage(ap(0.8)), damage(School.SHADOW, ap(0.5)), burstWounds(1)).tag("weapon").vfx("scourge_strike"));
        reg(ab("death_coil", "Death Coil", "Лик смерти").spec(UNHOLY).school(School.SHADOW).ranged().cost(RUNIC_POWER, 30)
                .effect(projectile(30, damage(ap(1.4))), onTargets(Selector.pets(), heal(ap(0.5)))).vfx("death_coil"));
        reg(ab("outbreak", "Outbreak", "Вспышка болезни").spec(UNHOLY).school(School.SHADOW).ranged().cost(RUNES, 1)
                .effect(aura("virulent_plague"), aoe(10, aura("virulent_plague"))).tag("dot", "aoe"));
        reg(ab("apocalypse", "Apocalypse", "Апокалипсис").spec(UNHOLY).cooldown(45).gen(RUNIC_POWER, 20)
                .effect(damage(ap(1.2)), burstWounds(4), summon("army_ghoul", 15, 4)).tag("cooldown").vfx("apocalypse"));
        reg(ab("army_of_the_dead", "Army of the Dead", "Войско мертвых").spec(UNHOLY).target(TargetType.SELF).cooldown(180).cost(RUNES, 1)
                .effect(summon("army_ghoul", 30, 6)).tag("cooldown", "pet"));
        reg(ab("dark_transformation", "Dark Transformation", "Темное превращение").spec(UNHOLY).target(TargetType.SELF).cooldown(45)
                .requires(Cond.hasPet()).effect(onTargets(Selector.pets(), aura("dark_transformation"))).tag("cooldown", "pet"));
        reg(ab("epidemic", "Epidemic", "Эпидемия").spec(UNHOLY).school(School.SHADOW).target(TargetType.NONE).cost(RUNIC_POWER, 30)
                .effect(custom("Damages every enemy infected with Virulent Plague", "Наносит урон всем противникам с «Вирулентной чумой»", ctx -> {
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 40)) {
                        if (u.auras().get("virulent_plague", ctx.caster) == null) continue;
                        ctx.engine.dealDamage(ctx.withTarget(u), u, School.SHADOW, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, ap(0.5), 1));
                    }
                })).tag("aoe"));

        // ------------------------------------------------------------ kit
        kit.common("death_strike", "death_grip", "mind_freeze", "anti_magic_shell", "icebound_fortitude", "death_and_decay", "raise_ally",
                "chains_of_ice", "dark_command", "lichborne", "raise_dead");
        kit.spec(BLOOD, "heart_strike", "marrowrend", "blood_boil", "vampiric_blood", "dancing_rune_weapon", "rune_tap");
        kit.spec(FROST_DK, "obliterate", "frost_strike", "howling_blast", "remorseless_winter", "pillar_of_frost", "empower_rune_weapon",
                "frostwyrms_fury");
        kit.spec(UNHOLY, "festering_strike", "scourge_strike", "death_coil", "outbreak", "apocalypse", "army_of_the_dead",
                "dark_transformation", "epidemic");
        kit.passive(BLOOD, "blood_basics");
        kit.passive(FROST_DK, "frost_dk_basics");
        kit.passive(UNHOLY, "unholy_basics");
        kit.defaultPet.put(UNHOLY, "ghoul");
        kit.bar(BLOOD, "marrowrend", "heart_strike", "blood_boil", "death_strike", "death_and_decay", "rune_tap", "death_grip",
                "mind_freeze", "dark_command", "vampiric_blood", "dancing_rune_weapon", "icebound_fortitude");
        kit.bar(FROST_DK, "obliterate", "frost_strike", "howling_blast", "remorseless_winter", "death_strike", "pillar_of_frost",
                "empower_rune_weapon", "mind_freeze", "frostwyrms_fury", "death_grip", "anti_magic_shell", "icebound_fortitude");
        kit.bar(UNHOLY, "festering_strike", "scourge_strike", "death_coil", "outbreak", "apocalypse", "dark_transformation",
                "death_and_decay", "mind_freeze", "army_of_the_dead", "death_strike", "anti_magic_shell", "epidemic");

        kit.rotation(BLOOD, of(
                self("vampiric_blood").when(selfHealthBelow(0.4)).urgent(),
                self("icebound_fortitude").when(selfHealthBelow(0.3)).urgent(),
                use("death_strike").when(selfHealthBelow(0.65).or(resourceAtLeast(RUNIC_POWER, 90))).urgent(),
                interrupt("mind_freeze"),
                use("marrowrend").when(Cond.custom(u -> u.auras().stacks("bone_shield") < 5, "low Bone Shield", "мало «Костяного щита»")),
                use("blood_boil").when(Cond.targetHasAura("blood_plague").not().or(enemiesAround(10, 2))),
                use("death_and_decay").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND).when(enemiesAround(8, 2)),
                self("dancing_rune_weapon"),
                use("heart_strike").when(resourceAtLeast(RUNES, 2)),
                use("death_strike").when(resourceAtLeast(RUNIC_POWER, 60)),
                use("blood_boil")));
        kit.rotation(FROST_DK, of(
                self("icebound_fortitude").when(selfHealthBelow(0.3)).urgent(),
                use("death_strike").when(selfHealthBelow(0.45)).urgent(),
                interrupt("mind_freeze"),
                self("pillar_of_frost"),
                self("empower_rune_weapon").when(resourceBelow(RUNES, 2)),
                use("remorseless_winter").when(enemiesAround(8, 2)),
                use("howling_blast").when(Cond.targetHasAura("frost_fever").not().or(Cond.hasAura("rime"))),
                use("obliterate").when(Cond.hasAura("killing_machine")),
                use("frost_strike").when(resourceAtLeast(RUNIC_POWER, 70)),
                use("obliterate"),
                use("frost_strike")));
        kit.rotation(UNHOLY, of(
                self("icebound_fortitude").when(selfHealthBelow(0.3)).urgent(),
                use("death_strike").when(selfHealthBelow(0.45)).urgent(),
                self("raise_dead").when(Cond.hasPet().not()),
                interrupt("mind_freeze"),
                use("outbreak").when(Cond.targetHasAura("virulent_plague").not()),
                self("dark_transformation"),
                use("apocalypse").when(Cond.targetHasAura("festering_wound")),
                use("epidemic").when(enemiesAround(10, 3).and(resourceAtLeast(RUNIC_POWER, 30))),
                use("death_coil").when(resourceAtLeast(RUNIC_POWER, 80).or(Cond.hasAura("sudden_doom"))),
                use("festering_strike").when(Cond.targetHasAura("festering_wound").not()),
                use("scourge_strike"),
                use("death_coil")));

        // ------------------------------------------------------------ talents
        classTree(
                t("blinding_sleet", 0, 0, "Blinding Sleet", "Ослепляющая наледь").mod(Modifier.cooldown("chains_of_ice", 0), Modifier.speed(0.05)),
                t("asphyxiate_talent", 0, 1, "Asphyxiate", "Удушение").grant("asphyxiate"),
                t("improved_death_strike", 0, 2, "Improved Death Strike", "Улучшенный удар смерти").mod(Modifier.abilityHealing("death_strike", 0.2),
                        Modifier.cost("death_strike", -0.15)),
                t("anti_magic_zone_talent", 1, 0, "Anti-Magic Zone", "Зона антимагии").grant("anti_magic_zone"),
                t("anti_magic_barrier", 1, 1, "Anti-Magic Barrier", "Антимагический барьер").mod(Modifier.cooldown("anti_magic_shell", -20),
                        Modifier.duration("anti_magic_shell", 2)),
                t("unholy_endurance", 1, 2, "Unholy Endurance", "Нечестивая стойкость").mod(Modifier.maxHealth(0.05)),
                t("acclimation", 2, 0, "Acclimation", "Акклиматизация").mod(Modifier.cooldown("icebound_fortitude", -60)),
                t("death_pact", 2, 1, "Unholy Bond", "Нечестивые узы").mod(Modifier.damage(0.04)),
                t("rune_mastery", 2, 2, "Rune Mastery", "Мастерство рун").mod(Modifier.stat(Stat.STRENGTH, 0.04)));
        specTree(BLOOD,
                t("relish_in_blood", 0, 0, "Relish in Blood", "Упоение кровью").mod(Modifier.abilityHealing("death_strike", 0.1)),
                t("blooddrinker_talent", 0, 1, "Blooddrinker", "Кровопийца").grant("blooddrinker"),
                t("heartbreaker", 0, 2, "Heartbreaker", "Сердцеед").mod(Modifier.gen("heart_strike", 4)),
                t("foul_bulwark", 1, 0, "Foul Bulwark", "Порочный бастион").mod(Modifier.maxHealth(0.06)),
                t("improved_vampiric_blood", 1, 1, "Improved Vampiric Blood", "Улучшенная кровь вампира").mod(Modifier.duration("vampiric_blood", 4),
                        Modifier.cooldown("vampiric_blood", -20)),
                t("ossuary", 1, 2, "Ossuary", "Склеп").mod(Modifier.cost("death_strike", -0.12)),
                t("bonestorm", 2, 0, "Bonestorm", "Буря костей").mod(Modifier.abilityDamage("blood_boil", 0.4)),
                t("consumption", 2, 1, "Consumption", "Поглощение").mod(Modifier.leech(4)),
                t("everlasting_bond", 2, 2, "Everlasting Bond", "Вечные узы").mod(Modifier.duration("dancing_rune_weapon", 4), Modifier.cooldown("dancing_rune_weapon", -30)));
        specTree(FROST_DK,
                t("murderous_efficiency", 0, 0, "Murderous Efficiency", "Убийственная эффективность").mod(Modifier.abilityDamage("obliterate", 0.15)),
                t("runic_attenuation", 0, 1, "Runic Attenuation", "Руническое затухание").mod(Modifier.abilityDamage("frost_strike", 0.2)),
                t("freezing_fog", 0, 2, "Freezing Fog", "Ледяной туман").mod(Modifier.abilityDamage("howling_blast", 0.3)),
                t("frostscythe", 1, 0, "Frostscythe", "Ледяная коса").mod(Modifier.abilityDamage("remorseless_winter", 0.3)),
                t("gathering_storm", 1, 1, "Gathering Storm", "Надвигающаяся буря").mod(Modifier.cooldown("remorseless_winter", -5), Modifier.abilityDamage("howling_blast", 0.1)),
                t("obliteration", 1, 2, "Obliteration", "Уничтожение").mod(Modifier.duration("pillar_of_frost", 4)),
                t("breath_of_sindragosa_talent", 2, 0, "Breath of Sindragosa", "Дыхание Синдрагосы").grant("breath_of_sindragosa"),
                t("icecap", 2, 1, "Icecap", "Ледяная шапка").mod(Modifier.cooldown("pillar_of_frost", -15)),
                t("absolute_zero", 2, 2, "Absolute Zero", "Абсолютный ноль").mod(Modifier.cooldown("frostwyrms_fury", -30), Modifier.abilityDamage("frostwyrms_fury", 0.3)));
        specTree(UNHOLY,
                t("infected_claws", 0, 0, "Infected Claws", "Зараженные когти").mod(Modifier.of(ModType.PET_DAMAGE, 0.1)),
                t("bursting_sores", 0, 1, "Bursting Sores", "Лопающиеся язвы").mod(Modifier.abilityDamage("scourge_strike", 0.2)),
                t("ebon_fever", 0, 2, "Ebon Fever", "Черная лихорадка").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("virulent_plague"), 0.3)),
                t("unholy_command", 1, 0, "Unholy Command", "Нечестивая власть").mod(Modifier.cooldown("dark_transformation", -10)),
                t("coil_of_devastation", 1, 1, "Coil of Devastation", "Разрушительный лик").mod(Modifier.abilityDamage("death_coil", 0.2)),
                t("improved_festering_strike", 1, 2, "Improved Festering Strike", "Улучшенный гнойный удар").mod(Modifier.abilityDamage("festering_strike", 0.2)),
                t("army_of_the_damned", 2, 0, "Army of the Damned", "Армия проклятых").mod(Modifier.cooldown("army_of_the_dead", -60), Modifier.cooldown("apocalypse", -10)),
                t("unholy_assault", 2, 1, "Unholy Assault", "Нечестивый натиск").mod(Modifier.haste(0.05)),
                t("superstrain", 2, 2, "Superstrain", "Суперштамм").mod(Modifier.tagDamage("disease", 0.2)));
        hero(t("hero_deathbringer", 0, 0, "Deathbringer", "Вестник смерти").mod(Modifier.damage(0.06), Modifier.maxHealth(0.04)),
                t("hero_san_layn", 0, 1, "San'layn", "Сан'лейн").mod(Modifier.leech(5), Modifier.of(ModType.PET_DAMAGE, 0.1)));
    }

    /** Bursts up to N Festering Wounds on the target. */
    private static com.wowcraft.core.spell.Effect burstWounds(int max) {
        return custom("Bursts up to " + max + " Festering Wound(s)", "Вскрывает до " + max + " гнойных ран", ctx -> {
            if (ctx.target == null) return;
            AuraInstance w = ctx.target.auras().get("festering_wound", ctx.caster);
            if (w == null) return;
            int n = Math.min(max, w.stacks);
            for (int i = 0; i < n; i++) {
                EffectContext c = ctx.copy();
                ctx.engine.dealDamage(c, ctx.target, School.SHADOW, com.wowcraft.core.combat.Formulas.base(ctx.caster, ctx.target, ap(0.45), 1));
                ctx.engine.energize(ctx.caster, RUNIC_POWER, 3);
            }
            ctx.engine.removeStacks(ctx.target, "festering_wound", n);
        });
    }
}
