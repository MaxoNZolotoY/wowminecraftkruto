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
import com.wowcraft.core.spell.Selector;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.FURY;
import static com.wowcraft.core.spec.Spec.HAVOC;
import static com.wowcraft.core.spec.Spec.VENGEANCE;
import static com.wowcraft.core.spec.WowClass.DEMON_HUNTER;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class DemonHunterContent extends ClassContent {
    public DemonHunterContent() {
        super(DEMON_HUNTER);
    }

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(buff("metamorphosis", "Metamorphosis", "Метаморфоза").duration(24)
                .mod(Modifier.damage(0.2), Modifier.haste(0.2), Modifier.replace("chaos_strike", "annihilation"), Modifier.replace("blade_dance", "death_sweep"))
                .desc("Transformed into a demon: damage and haste increased by 20%, Chaos Strike and Blade Dance are empowered.",
                        "Облик демона: урон и скорость увеличены на 20%, «Удар Хаоса» и «Танец клинков» усилены.").vfx("metamorphosis"));
        reg(buff("blur", "Blur", "Затуманивание").duration(10).mod(Modifier.taken(-0.2), Modifier.of(ModType.AVOIDANCE, 30)));
        reg(buff("blade_dance_dodge", "Blade Dance", "Танец клинков").duration(1).mod(physicalTaken(-0.5)).hidden());
        cc("chaos_nova", "Chaos Nova", "Кольцо Хаоса", CcType.STUN, 2, DispelType.NONE);
        reg(debuff("imprison", "Imprison", "Пленение").duration(60).cc(CcType.INCAPACITATE).breakOnAnyDamage().noPandemic());
        reg(buff("immolation_aura", "Immolation Aura", "Обжигающий жар").duration(6).school(School.FIRE)
                .periodic(1, all(aroundSelf(8, damage(School.FIRE, ap(0.32))), energize(FURY, 3))).vfx("immolation_aura"));
        reg(buff("darkness", "Darkness", "Мрак").duration(1.5).mod(Modifier.taken(-0.15)).hidden());
        reg(buff("momentum", "Momentum", "Импульс").duration(6).mod(Modifier.damage(0.1)));
        reg(passive("havoc_basics", "Demonic Appetite", "Демонический аппетит").mod(Modifier.schoolDamage(School.CHAOS, 0.05))
                .trigger(Trigger.on(TriggerType.CAST, energize(FURY, 20)).filter(ModFilter.ability("chaos_strike")).chance(0.3))
                .trigger(Trigger.on(TriggerType.CAST, energize(FURY, 20)).filter(ModFilter.ability("annihilation")).chance(0.3))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("momentum")).filter(ModFilter.ability("fel_rush"))));
        reg(debuff("essence_break", "Essence Break", "Разрыв сущности").duration(4).mod(Modifier.taken(0.3)));
        // Vengeance
        reg(buff("soul_fragments", "Soul Fragments", "Фрагменты души").duration(20).stacks(5).desc("Consumed by Soul Cleave and Spirit Bomb.",
                "Расходуются «Раскалыванием души» и «Бомбой духов»."));
        reg(buff("demon_spikes", "Demon Spikes", "Демонические шипы").duration(6).mod(Modifier.of(ModType.ARMOR_PCT, 1.0), physicalTaken(-0.1)));
        reg(debuff("fiery_brand", "Fiery Brand", "Огненное клеймо").duration(10).school(School.FIRE).tag("dot").mod(Modifier.damage(-0.4))
                .periodic(1, damage(School.FIRE, ap(0.12))).desc("Damage dealt reduced by 40%.", "Наносимый урон уменьшен на 40%.").vfx("fiery_brand"));
        reg(debuff("sigil_of_flame", "Sigil of Flame", "Печать огня").duration(6).school(School.FIRE).tag("dot").periodic(1, damage(School.FIRE, ap(0.1))));
        cc("sigil_of_silence", "Sigil of Silence", "Печать немоты", CcType.SILENCE, 6, DispelType.MAGIC);
        cc("sigil_of_misery", "Sigil of Misery", "Печать страдания", CcType.FEAR, 15, DispelType.MAGIC);
        reg(buff("metamorphosis_vengeance", "Metamorphosis", "Метаморфоза").duration(15)
                .mod(Modifier.maxHealth(0.5), Modifier.of(ModType.ARMOR_PCT, 1.0), Modifier.gen("shear", 20)).vfx("metamorphosis"));
        reg(passive("vengeance_basics", "Demonic Wards", "Демонические обереги").mod(Modifier.taken(-0.1), Modifier.stat(Stat.STAMINA, 0.2),
                        Modifier.of(ModType.ARMOR_PCT, 0.6))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("soul_fragments", 2)).filter(ModFilter.ability("shear"))));

        // ------------------------------------------------------------ class abilities
        reg(ab("fel_rush", "Fel Rush", "Рывок Скверны").school(School.CHAOS).target(TargetType.NONE).cooldown(10).charges(2).gcd(0.5)
                .effect(dash(2.2), delayed(0.3, aroundSelf(6, damage(ap(0.5))))).tag("movement").vfx("fel_rush"));
        reg(ab("vengeful_retreat", "Vengeful Retreat", "Коварное отступление").target(TargetType.NONE).cooldown(25).offGcd()
                .effect(aroundSelf(8, damage(ap(0.3)), aura("generic_slow")), disengage(1.6)).tag("movement"));
        reg(ab("immolation_aura", "Immolation Aura", "Обжигающий жар").school(School.FIRE).target(TargetType.SELF).cooldown(30).hastedCooldown()
                .gcd(1.5).gen(FURY, 20).effect(aroundSelf(8, damage(ap(0.4))), selfAura("immolation_aura")).tag("aoe"));
        reg(ab("disrupt", "Disrupt", "Прерывание").school(School.CHAOS).cooldown(15).offGcd().gen(FURY, 30).effect(interrupt(3)).tag("interrupt"));
        reg(ab("chaos_nova", "Chaos Nova", "Кольцо Хаоса").school(School.CHAOS).target(TargetType.NONE).cost(FURY, 30).cooldown(45)
                .effect(aroundSelf(8, damage(ap(0.3)), aura("chaos_nova"))).tag("cc", "aoe").vfx("chaos_nova"));
        reg(ab("imprison", "Imprison", "Пленение").school(School.SHADOW).range(20).cooldown(45).effect(aura("imprison")).tag("cc"));
        reg(ab("throw_glaive", "Throw Glaive", "Бросок боевого клинка").range(30).cooldown(9).hastedCooldown().charges(1)
                .effect(projectile(40, damage(ap(0.6)), aura("generic_slow")), chain(3, 10, 1.0, false, damage(ap(0.3)))).tag("ranged").vfx("glaive"));
        reg(ab("consume_magic", "Consume Magic", "Поглощение магии").school(School.CHAOS).range(20).cooldown(10).gen(FURY, 20)
                .effect(dispel(1, DispelType.MAGIC)).tag("purge"));
        reg(ab("darkness", "Darkness", "Мрак").school(School.SHADOW).target(TargetType.NONE).cooldown(300)
                .effect(area(GroundArea.Def.allies("darkness", 8, 8, 1, aura("darkness")).centeredOnCaster().color(0xFF202040))).tag("raid_cd"));
        reg(ab("the_hunt", "The Hunt", "Охота").school(School.NATURE).range(50).cooldown(90).cast(1.0)
                .effect(charge(), damage(ap(3.0)), aura("generic_root")).tag("cooldown", "gap_closer").vfx("the_hunt"));
        reg(ab("sigil_of_misery", "Sigil of Misery", "Печать страдания").school(School.SHADOW).target(TargetType.GROUND).range(30).cooldown(90)
                .effect(delayed(2, onTargets(Selector.groundEnemies(8), aura("sigil_of_misery")))).tag("cc", "aoe").vfx("sigil"));

        // ------------------------------------------------------------ Havoc
        reg(ab("demons_bite", "Demon's Bite", "Укус демона").spec(HAVOC).gen(FURY, 25).effect(damage(ap(1.0))).tag("builder", "weapon").vfx("slash"));
        reg(ab("chaos_strike", "Chaos Strike", "Удар Хаоса").spec(HAVOC).school(School.CHAOS).cost(FURY, 40)
                .effect(damage(ap(1.7))).tag("weapon").vfx("chaos_strike"));
        reg(ab("annihilation", "Annihilation", "Аннигиляция").spec(HAVOC).school(School.CHAOS).cost(FURY, 40)
                .effect(damage(ap(2.2))).tag("weapon").hidden().vfx("annihilation"));
        reg(ab("blade_dance", "Blade Dance", "Танец клинков").spec(HAVOC).target(TargetType.NONE).cost(FURY, 35).cooldown(9).hastedCooldown()
                .effect(aroundSelf(8, damage(ap(1.3))), selfAura("blade_dance_dodge")).tag("aoe").vfx("blade_dance"));
        reg(ab("death_sweep", "Death Sweep", "Смертоносный взмах").spec(HAVOC).target(TargetType.NONE).cost(FURY, 35).cooldown(9).hastedCooldown()
                .effect(aroundSelf(8, damage(ap(1.8))), selfAura("blade_dance_dodge")).tag("aoe").hidden().vfx("death_sweep"));
        reg(ab("eye_beam", "Eye Beam", "Пронзающий взгляд").spec(HAVOC).school(School.CHAOS).target(TargetType.NONE).cost(FURY, 30).cooldown(40)
                .channel(2, 0.25).tick(onTargets(Selector.line(20, 3), damage(ap(0.32)))).tag("aoe").vfx("eye_beam"));
        reg(ab("metamorphosis", "Metamorphosis", "Метаморфоза").spec(HAVOC).school(School.CHAOS).target(TargetType.GROUND).range(40).cooldown(180)
                .effect(leap(), delayed(0.4, onTargets(Selector.groundEnemies(8), damage(ap(1.0)), aura("generic_stun"))), selfAura("metamorphosis"))
                .tag("cooldown").vfx("metamorphosis"));
        reg(ab("blur", "Blur", "Затуманивание").spec(HAVOC).target(TargetType.SELF).cooldown(60).offGcd().effect(selfAura("blur")).tag("defensive"));
        reg(ab("essence_break", "Essence Break", "Разрыв сущности").spec(HAVOC).school(School.CHAOS).target(TargetType.NONE).cooldown(40)
                .effect(cone(60, 10, damage(ap(1.2)), aura("essence_break"))).tag("cooldown"));
        reg(ab("felblade", "Felblade", "Клинок Скверны").school(School.FIRE).range(15).cooldown(15).hastedCooldown().gen(FURY, 40)
                .effect(charge(), damage(ap(0.8))).tag("gap_closer"));

        // ------------------------------------------------------------ Vengeance
        reg(ab("shear", "Shear", "Раскол").spec(VENGEANCE).gen(FURY, 10).effect(damage(ap(1.0))).tag("builder", "weapon").vfx("slash"));
        reg(ab("soul_cleave", "Soul Cleave", "Раскалывание души").spec(VENGEANCE).target(TargetType.NONE).cost(FURY, 30)
                .effect(cone(120, 8, damage(ap(1.2))), custom("Consumes up to 2 Soul Fragments, healing 6% each", "Поглощает до 2 фрагментов души, восполняя по 6% здоровья", ctx -> {
                    int n = Math.min(2, ctx.caster.auras().stacks("soul_fragments"));
                    if (n > 0) {
                        ctx.engine.removeStacks(ctx.caster, "soul_fragments", n);
                        ctx.engine.heal(ctx.withTarget(ctx.caster), ctx.caster, ctx.caster.maxHealth() * 0.06 * n, true);
                    }
                    ctx.engine.heal(ctx.withTarget(ctx.caster), ctx.caster, ctx.caster.maxHealth() * 0.03, true);
                })).tag("aoe", "heal").vfx("soul_cleave"));
        reg(ab("spirit_bomb", "Spirit Bomb", "Бомба духов").spec(VENGEANCE).school(School.FIRE).target(TargetType.NONE).cost(FURY, 40)
                .requires(Cond.auraStacks("soul_fragments", 1))
                .effect(custom("Consumes Soul Fragments to deal Fire damage to nearby enemies", "Поглощает фрагменты души, нанося урон от огня противникам рядом", ctx -> {
                    int n = Math.min(5, ctx.caster.auras().stacks("soul_fragments"));
                    ctx.engine.removeStacks(ctx.caster, "soul_fragments", n);
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 8)) {
                        ctx.engine.dealDamage(ctx.withTarget(u), u, School.FIRE, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, ap(0.4), 1) * n);
                    }
                    ctx.engine.heal(ctx.withTarget(ctx.caster), ctx.caster, ctx.caster.maxHealth() * 0.04 * n, true);
                })).tag("aoe").vfx("spirit_bomb"));
        reg(ab("demon_spikes", "Demon Spikes", "Демонические шипы").spec(VENGEANCE).target(TargetType.SELF).cooldown(20).charges(2).offGcd()
                .effect(selfAura("demon_spikes")).tag("defensive", "active_mitigation"));
        reg(ab("fiery_brand", "Fiery Brand", "Огненное клеймо").spec(VENGEANCE).school(School.FIRE).range(30).cooldown(60)
                .effect(damage(ap(1.0)), aura("fiery_brand")).tag("defensive"));
        reg(ab("sigil_of_flame", "Sigil of Flame", "Печать огня").spec(VENGEANCE, HAVOC).school(School.FIRE).target(TargetType.GROUND).range(30)
                .cooldown(30).hastedCooldown().gen(FURY, 30).effect(delayed(1, onTargets(Selector.groundEnemies(8), damage(ap(0.8)), aura("sigil_of_flame"))))
                .tag("aoe").vfx("sigil"));
        reg(ab("sigil_of_silence", "Sigil of Silence", "Печать немоты").spec(VENGEANCE).school(School.SHADOW).target(TargetType.GROUND).range(30)
                .cooldown(60).effect(delayed(1, onTargets(Selector.groundEnemies(8), interrupt(3), aura("sigil_of_silence")))).tag("interrupt", "aoe").vfx("sigil"));
        reg(ab("metamorphosis_vengeance", "Metamorphosis", "Метаморфоза").spec(VENGEANCE).school(School.CHAOS).target(TargetType.SELF).cooldown(180)
                .offGcd().effect(selfAura("metamorphosis_vengeance"), healPct(0.2)).tag("defensive", "major_defensive"));
        reg(ab("infernal_strike", "Infernal Strike", "Инфернальный удар").spec(VENGEANCE).school(School.FIRE).target(TargetType.GROUND).range(30)
                .cooldown(20).charges(2).offGcd().effect(leap(), delayed(0.4, onTargets(Selector.groundEnemies(6), damage(ap(0.6))))).tag("movement"));
        reg(ab("fel_devastation", "Fel Devastation", "Опустошение Скверной").spec(VENGEANCE).school(School.FIRE).target(TargetType.NONE)
                .cost(FURY, 50).cooldown(40).channel(2, 0.25).tick(cone(90, 12, damage(ap(0.35))), healPct(0.02)).tag("aoe", "heal").vfx("fel_devastation"));
        reg(ab("torment", "Torment", "Мучение").school(School.SHADOW).range(30).cooldown(8).offGcd().effect(taunt()).tag("taunt"));

        // ------------------------------------------------------------ kit
        kit.common("fel_rush", "vengeful_retreat", "immolation_aura", "disrupt", "chaos_nova", "imprison", "throw_glaive", "consume_magic",
                "darkness", "sigil_of_misery");
        kit.spec(HAVOC, "demons_bite", "chaos_strike", "blade_dance", "eye_beam", "metamorphosis", "blur", "sigil_of_flame");
        kit.spec(VENGEANCE, "shear", "soul_cleave", "spirit_bomb", "demon_spikes", "fiery_brand", "sigil_of_flame", "sigil_of_silence",
                "metamorphosis_vengeance", "infernal_strike", "fel_devastation", "torment");
        kit.passive(HAVOC, "havoc_basics");
        kit.passive(VENGEANCE, "vengeance_basics");
        kit.bar(HAVOC, "demons_bite", "chaos_strike", "blade_dance", "eye_beam", "immolation_aura", "throw_glaive", "fel_rush", "disrupt",
                "metamorphosis", "vengeful_retreat", "blur", "chaos_nova");
        kit.bar(VENGEANCE, "shear", "soul_cleave", "spirit_bomb", "immolation_aura", "sigil_of_flame", "fel_devastation", "demon_spikes",
                "disrupt", "torment", "fiery_brand", "metamorphosis_vengeance", "infernal_strike");

        kit.rotation(HAVOC, of(
                self("blur").when(selfHealthBelow(0.4)).urgent(),
                interrupt("disrupt"),
                use("metamorphosis").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND),
                use("eye_beam").when(resourceAtLeast(FURY, 30)),
                use("blade_dance").when(resourceAtLeast(FURY, 35)),
                self("immolation_aura"),
                use("chaos_strike").when(resourceAtLeast(FURY, 70)),
                use("throw_glaive").when(enemiesAround(10, 2)),
                use("chaos_strike").when(resourceAtLeast(FURY, 40)),
                use("demons_bite")));
        kit.rotation(VENGEANCE, of(
                self("metamorphosis_vengeance").when(selfHealthBelow(0.3)).urgent(),
                self("demon_spikes").when(Cond.hasAura("demon_spikes").not()).urgent(),
                use("fiery_brand").when(selfHealthBelow(0.6)).urgent(),
                interrupt("disrupt"),
                self("immolation_aura"),
                use("sigil_of_flame").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND),
                use("fel_devastation").when(selfHealthBelow(0.8)),
                use("spirit_bomb").when(Cond.auraStacks("soul_fragments", 4).and(enemiesAround(8, 2))),
                use("soul_cleave").when(resourceAtLeast(FURY, 60).or(selfHealthBelow(0.6))),
                use("shear")));

        // ------------------------------------------------------------ talents
        classTree(
                t("felblade_talent", 0, 0, "Felblade", "Клинок Скверны").grant("felblade"),
                t("improved_disrupt", 0, 1, "Improved Disrupt", "Улучшенное прерывание").mod(Modifier.cooldown("disrupt", -2)),
                t("chaos_fragments", 0, 2, "Chaos Fragments", "Фрагменты Хаоса").mod(Modifier.cooldown("chaos_nova", -15)),
                t("the_hunt_talent", 1, 0, "The Hunt", "Охота").grant("the_hunt"),
                t("illidari_knowledge", 1, 1, "Illidari Knowledge", "Знания Иллидари").mod(magicTaken(-0.05)),
                t("pursuit", 1, 2, "Pursuit", "Преследование").mod(Modifier.speed(0.1)),
                t("long_night", 2, 0, "Long Night", "Долгая ночь").mod(Modifier.cooldown("darkness", -120)),
                t("demonic_origins", 2, 1, "Demonic Origins", "Демоническое происхождение").mod(Modifier.cooldown("metamorphosis", -60), Modifier.cooldown("metamorphosis_vengeance", -60)),
                t("fel_flame_fortification", 2, 2, "Will of the Illidari", "Воля Иллидари").mod(Modifier.maxHealth(0.05)));
        specTree(HAVOC,
                t("insatiable_hunger", 0, 0, "Insatiable Hunger", "Неутолимый голод").mod(Modifier.gen("demons_bite", 5), Modifier.abilityDamage("demons_bite", 0.2)),
                t("chaos_theory", 0, 1, "Chaos Theory", "Теория хаоса").mod(Modifier.abilityCrit("chaos_strike", 10), Modifier.abilityCrit("annihilation", 10)),
                t("essence_break_talent", 0, 2, "Essence Break", "Разрыв сущности").grant("essence_break"),
                t("trail_of_ruin", 1, 0, "Trail of Ruin", "След разрушения").mod(Modifier.abilityDamage("blade_dance", 0.2), Modifier.abilityDamage("death_sweep", 0.2)),
                t("furious_gaze", 1, 1, "Furious Gaze", "Яростный взор").mod(Modifier.abilityDamage("eye_beam", 0.3)),
                t("momentum_talent", 1, 2, "Momentum", "Импульс").mod(Modifier.duration("momentum", 4)),
                t("cycle_of_hatred", 2, 0, "Cycle of Hatred", "Цикл ненависти").mod(Modifier.cooldown("eye_beam", -10)),
                t("demon_blades", 2, 1, "Demon Blades", "Демонические клинки").mod(Modifier.damage(0.05)),
                t("inertia", 2, 2, "Inertia", "Инерция").mod(Modifier.charges("fel_rush", 1)));
        specTree(VENGEANCE,
                t("fracture", 0, 0, "Fracture", "Разлом").mod(Modifier.abilityDamage("shear", 0.3), Modifier.gen("shear", 10)),
                t("agonizing_flames", 0, 1, "Agonizing Flames", "Мучительное пламя").mod(Modifier.abilityDamage("immolation_aura", 0.3), Modifier.speed(0.05)),
                t("feast_of_souls", 0, 2, "Feast of Souls", "Пиршество душ").mod(Modifier.abilityHealing("soul_cleave", 0.3)),
                t("calcified_spikes", 1, 0, "Calcified Spikes", "Кальцинированные шипы").mod(Modifier.duration("demon_spikes", 2)),
                t("burning_alive", 1, 1, "Burning Alive", "Сжигание заживо").mod(Modifier.duration("fiery_brand", 4)),
                t("quickened_sigils", 1, 2, "Quickened Sigils", "Ускоренные печати").mod(Modifier.cooldown("sigil_of_flame", -5), Modifier.cooldown("sigil_of_silence", -15)),
                t("darkglare_boon", 2, 0, "Darkglare Boon", "Дар Темного взора").mod(Modifier.cooldown("fel_devastation", -10)),
                t("soulcrush", 2, 1, "Soulcrush", "Сокрушение души").mod(Modifier.abilityDamage("spirit_bomb", 0.3)),
                t("down_in_flames", 2, 2, "Down in Flames", "Объятые пламенем").mod(Modifier.charges("fiery_brand", 1), Modifier.cooldown("fiery_brand", -12)));
        hero(t("hero_aldrachi_reaver", 0, 0, "Aldrachi Reaver", "Альдрачийский разоритель").mod(Modifier.damage(0.06), Modifier.leech(3)),
                t("hero_fel_scarred", 0, 1, "Fel-Scarred", "Изуродованный Скверной").mod(Modifier.schoolDamage(School.FIRE, 0.1), Modifier.duration("metamorphosis", 4)));
    }
}
