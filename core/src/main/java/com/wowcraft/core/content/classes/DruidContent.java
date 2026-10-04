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
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spell.Cond;
import com.wowcraft.core.spell.Selector;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.ASTRAL_POWER;
import static com.wowcraft.core.resource.ResourceType.COMBO_POINTS;
import static com.wowcraft.core.resource.ResourceType.ENERGY;
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.resource.ResourceType.RAGE;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.DRUID;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class DruidContent extends ClassContent {
    public DruidContent() {
        super(DRUID);
    }

    @Override
    public void register() {
        // ------------------------------------------------------------ forms
        reg(buff("cat_form", "Cat Form", "Облик кошки").form("cat").persistent().mod(Modifier.speed(0.3), Modifier.taken(0.0)).vfx("cat_form"));
        reg(buff("bear_form", "Bear Form", "Облик медведя").form("bear").persistent()
                .mod(Modifier.of(ModType.ARMOR_PCT, 2.2), Modifier.maxHealth(0.25), Modifier.stat(Stat.STAMINA, 0.15), Modifier.taken(-0.06)).vfx("bear_form"));
        reg(buff("moonkin_form", "Moonkin Form", "Облик лунного совуха").form("moonkin").persistent()
                .mod(Modifier.schoolDamage(School.ARCANE, 0.1), Modifier.schoolDamage(School.NATURE, 0.1), Modifier.of(ModType.ARMOR_PCT, 1.0)).vfx("moonkin_form"));
        reg(buff("travel_form", "Travel Form", "Походный облик").form("travel").persistent().mod(Modifier.speed(0.6)).vfx("travel_form"));
        // ------------------------------------------------------------ auras
        reg(debuff("moonfire", "Moonfire", "Лунный огонь").duration(16).school(School.ARCANE).tag("dot").dispel(DispelType.MAGIC)
                .periodic(2, all(damage(School.ARCANE, sp(0.18)), energize(ASTRAL_POWER, 0))));
        reg(debuff("sunfire", "Sunfire", "Солнечный огонь").duration(18).school(School.NATURE).tag("dot").dispel(DispelType.MAGIC)
                .periodic(2, damage(School.NATURE, sp(0.17))));
        reg(buff("regrowth", "Regrowth", "Восстановление").duration(12).school(School.NATURE).tag("hot").dispel(DispelType.MAGIC)
                .periodic(2, heal(sp(0.18))));
        reg(buff("rejuvenation", "Rejuvenation", "Омоложение").duration(12).school(School.NATURE).tag("hot").dispel(DispelType.MAGIC)
                .periodic(3, heal(sp(0.38))));
        dr("barkskin", "Barkskin", "Дубовая кожа", 0.2, 8);
        reg(debuff("entangling_roots", "Entangling Roots", "Гнев деревьев").duration(30).school(School.NATURE).cc(CcType.ROOT).breakOnDamage(0.1)
                .dispel(DispelType.MAGIC).noPandemic());
        reg(buff("stampeding_roar", "Stampeding Roar", "Тревожный рев").duration(8).mod(Modifier.speed(0.6)));
        reg(debuff("solar_beam", "Solar Beam", "Луч солнца").duration(1.5).cc(CcType.SILENCE).hidden());
        reg(debuff("cyclone", "Cyclone", "Смерч").duration(6).cc(CcType.DISORIENT).mod(Modifier.immuneDamage()).noPandemic());
        // Balance
        reg(buff("eclipse_solar", "Eclipse (Solar)", "Затмение (солнечное)").duration(15).mod(Modifier.schoolDamage(School.NATURE, 0.15),
                Modifier.castTime("wrath", -0.15)).vfx("eclipse_solar"));
        reg(buff("eclipse_lunar", "Eclipse (Lunar)", "Затмение (лунное)").duration(15).mod(Modifier.schoolDamage(School.ARCANE, 0.15),
                Modifier.castTime("starfire", -0.15)).vfx("eclipse_lunar"));
        reg(buff("celestial_alignment", "Celestial Alignment", "Парад планет").duration(20)
                .mod(Modifier.damage(0.1), Modifier.haste(0.1), Modifier.crit(10)).vfx("celestial_alignment"));
        reg(passive("balance_basics", "Eclipse", "Затмение")
                .trigger(Trigger.on(TriggerType.CAST, selfAura("eclipse_lunar")).filter(ModFilter.ability("wrath")).chance(0.35))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("eclipse_solar")).filter(ModFilter.ability("starfire")).chance(0.35)));
        // Feral
        reg(debuff("rake", "Rake", "Глубокая рана").duration(15).tag("bleed").dispel(DispelType.BLEED).periodic(3, damage(ap(0.32))));
        reg(debuff("rip", "Rip", "Разорвать").tag("bleed").dispel(DispelType.BLEED).periodic(2, damage(ap(0.3))));
        reg(debuff("thrash_cat", "Thrash", "Взбучка").duration(15).tag("bleed").dispel(DispelType.BLEED).periodic(3, damage(ap(0.15))));
        reg(debuff("rake_stun", "Rake", "Глубокая рана").duration(4).cc(CcType.STUN).noPandemic());
        reg(buff("tigers_fury", "Tiger's Fury", "Тигриное неистовство").duration(10).mod(Modifier.damage(0.15)).vfx("tigers_fury"));
        reg(buff("berserk_cat", "Berserk", "Берсерк").duration(20).mod(Modifier.of(ModType.COST_PCT, ModFilter.ANY, -0.5),
                Modifier.of(ModType.RESOURCE_GEN_FLAT, ModFilter.tag("builder"), 1)).vfx("berserk"));
        reg(buff("prowl", "Prowl", "Крадущийся зверь").stealth().mod(Modifier.speed(-0.1)).vfx("stealth"));
        reg(buff("predatory_swiftness", "Predatory Swiftness", "Хищная стремительность").duration(12).mod(Modifier.castTime("regrowth", -1.0),
                Modifier.cost("regrowth", -1.0)).trigger(Trigger.on(TriggerType.CAST, ctx -> {
        }).filter(ModFilter.ability("regrowth")).consumeStack()));
        reg(passive("feral_basics", "Predatory Swiftness", "Хищная стремительность").mod(Modifier.tagDamage("bleed", 0.1))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("predatory_swiftness")).filter(ModFilter.tag("finisher")).chance(0.4)));
        reg(debuff("maim", "Maim", "Калечение").duration(1).cc(CcType.STUN).noPandemic());
        // Guardian
        reg(buff("ironfur", "Ironfur", "Железный мех").duration(7).stacks(5).mod(Modifier.of(ModType.ARMOR_PCT, 0.6)).refreshOnStack());
        reg(buff("frenzied_regeneration", "Frenzied Regeneration", "Неистовое восстановление").duration(3).periodic(1, healPct(0.08)).unhastedTicks());
        dr("survival_instincts", "Survival Instincts", "Инстинкты выживания", 0.5, 6);
        reg(buff("incarnation_guardian", "Incarnation: Guardian of Ursoc", "Инкарнация: Страж Урсока").duration(30)
                .mod(Modifier.maxHealth(0.3), Modifier.cooldownPct("mangle", -0.5), Modifier.cooldownPct("thrash_bear", -0.5), Modifier.cost("ironfur", -0.5))
                .vfx("incarnation"));
        reg(debuff("thrash_bear", "Thrash", "Взбучка").duration(15).stacks(3).tag("bleed").dispel(DispelType.BLEED).periodic(3, damage(ap(0.12))));
        reg(buff("gore", "Gore", "Ранение").duration(10).hidden());
        reg(passive("guardian_basics", "Gore", "Ранение").mod(Modifier.stat(Stat.STAMINA, 0.1))
                .trigger(Trigger.on(TriggerType.CAST, all(resetCooldown("mangle"), energize(RAGE, 4))).filter(ModFilter.ability("thrash_bear")).chance(0.15))
                .trigger(Trigger.on(TriggerType.CAST, all(resetCooldown("mangle"), energize(RAGE, 4))).filter(ModFilter.ability("moonfire")).chance(0.15)));
        // Restoration
        reg(buff("lifebloom", "Lifebloom", "Жизнецвет").duration(15).school(School.NATURE).tag("hot").dispel(DispelType.MAGIC)
                .periodic(1, heal(sp(0.18))).onExpire(heal(sp(1.4))));
        reg(buff("wild_growth", "Wild Growth", "Буйный рост").duration(7).school(School.NATURE).tag("hot").dispel(DispelType.MAGIC)
                .periodic(1, heal(sp(0.16))));
        reg(buff("ironbark", "Ironbark", "Железная кора").duration(12).mod(Modifier.taken(-0.2)).vfx("ironbark"));
        reg(buff("innervate", "Innervate", "Озарение").duration(8).mod(Modifier.of(ModType.COST_PCT, ModFilter.ANY, -1.0)).vfx("innervate"));
        reg(buff("natures_swiftness", "Nature's Swiftness", "Природная стремительность").duration(30)
                .mod(Modifier.castTime("regrowth", -1.0), Modifier.abilityHealing("regrowth", 1.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("regrowth")).consumeStack()));
        reg(buff("incarnation_tree", "Incarnation: Tree of Life", "Инкарнация: Древо Жизни").duration(30).mod(Modifier.healing(0.15),
                Modifier.castTime("regrowth", -0.3)).vfx("tree_of_life"));
        reg(passive("restoration_druid_basics", "Harmony", "Гармония").mod(Modifier.healing(0.05)));

        // ------------------------------------------------------------ class abilities
        reg(ab("cat_form", "Cat Form", "Облик кошки").target(TargetType.SELF).gcd(1.0).keepStealth().effect(selfAura("cat_form")).tag("form"));
        reg(ab("bear_form", "Bear Form", "Облик медведя").target(TargetType.SELF).gcd(1.0).effect(selfAura("bear_form"), energize(RAGE, 15)).tag("form"));
        reg(ab("moonkin_form", "Moonkin Form", "Облик лунного совуха").spec(BALANCE).target(TargetType.SELF).gcd(1.0)
                .effect(selfAura("moonkin_form")).tag("form"));
        reg(ab("travel_form", "Travel Form", "Походный облик").target(TargetType.SELF).gcd(1.0).effect(selfAura("travel_form")).tag("form", "movement"));
        reg(ab("caster_form", "Cancel Form", "Отмена облика").target(TargetType.SELF).offGcd().cooldown(0.5)
                .effect(custom("Leaves your current shapeshift form", "Выход из облика", ctx -> {
                    AuraInstance f = ctx.caster.auras().formAura();
                    if (f != null) ctx.engine.removeAura(f, false);
                })).tag("form"));
        reg(ab("moonfire", "Moonfire", "Лунный огонь").school(School.ARCANE).ranged().cost(MANA, 0.6).gen(ASTRAL_POWER, 6)
                .effect(damage(sp(0.4)), aura("moonfire")).tag("dot").vfx("moonfire"));
        reg(ab("wrath", "Wrath", "Гнев").school(School.NATURE).ranged().cast(1.5).cost(MANA, 0.2).gen(ASTRAL_POWER, 8)
                .effect(projectile(35, damage(sp(0.95)))).vfx("wrath"));
        reg(ab("regrowth", "Regrowth", "Восстановление").school(School.NATURE).target(TargetType.FRIENDLY).cast(1.5).cost(MANA, 10)
                .effect(heal(sp(1.8)), aura("regrowth")).tag("heal").vfx("nature_heal"));
        reg(ab("rebirth", "Rebirth", "Возрождение").school(School.NATURE).target(TargetType.DEAD_FRIENDLY).cast(2.0).cooldown(600)
                .effect(resurrect(0.6)).tag("battle_res"));
        reg(ab("barkskin", "Barkskin", "Дубовая кожа").target(TargetType.SELF).cooldown(60).offGcd().usableWhileCc().keepStealth()
                .effect(selfAura("barkskin")).tag("defensive"));
        reg(ab("entangling_roots", "Entangling Roots", "Гнев деревьев").school(School.NATURE).ranged().cast(1.7).cost(MANA, 1)
                .effect(aura("entangling_roots")).tag("cc"));
        reg(ab("soothe", "Soothe", "Умиротворение").school(School.NATURE).ranged().cooldown(10).cost(MANA, 1)
                .effect(dispel(1, DispelType.ENRAGE)).tag("purge"));
        reg(ab("remove_corruption", "Remove Corruption", "Снятие порчи").school(School.NATURE).target(TargetType.FRIENDLY).cooldown(8)
                .cost(MANA, 1.3).effect(dispel(3, DispelType.CURSE, DispelType.POISON)).tag("dispel"));
        reg(ab("skull_bash", "Skull Bash", "Лобовая атака").range(13).cooldown(15).offGcd().effect(charge(), interrupt(3)).tag("interrupt"));
        reg(ab("stampeding_roar", "Stampeding Roar", "Тревожный рев").target(TargetType.SELF).cooldown(120).gcd(1.0)
                .effect(groupAura("stampeding_roar", 30)).tag("utility"));
        reg(ab("growl", "Growl", "Рык").range(30).cooldown(8).offGcd().form("bear_form").effect(taunt()).tag("taunt"));
        reg(ab("cyclone", "Cyclone", "Смерч").school(School.NATURE).ranged().cast(1.7).cost(MANA, 1).effect(aura("cyclone")).tag("cc"));
        reg(ab("ferocious_bite", "Ferocious Bite", "Свирепый укус").cost(ENERGY, 25).costRange(COMBO_POINTS, 1, 5).gcd(1.0).form("cat_form")
                .effect(spendExtra(ENERGY, 25, 1.0, damage(ap(0.65).perCombo()))).tag("finisher", "weapon").vfx("bite"));
        reg(ab("frenzied_regeneration", "Frenzied Regeneration", "Неистовое восстановление").target(TargetType.SELF).cost(RAGE, 10)
                .cooldown(36).charges(1).form("bear_form").effect(selfAura("frenzied_regeneration")).tag("defensive", "heal"));

        // ------------------------------------------------------------ Balance
        reg(ab("starsurge", "Starsurge", "Звездный поток").spec(BALANCE).school(School.ARCANE).ranged().cost(ASTRAL_POWER, 40)
                .effect(projectile(40, damage(sp(2.1)))).form("moonkin_form").vfx("starsurge"));
        reg(ab("starfall", "Starfall", "Звездопад").spec(BALANCE).school(School.ARCANE).target(TargetType.NONE).cost(ASTRAL_POWER, 50)
                .effect(area(GroundArea.Def.enemies("starfall", 15, 8, 1, damage(School.ARCANE, sp(0.32))).following().color(0xFF8080FF)))
                .form("moonkin_form").tag("aoe").vfx("starfall"));
        reg(ab("starfire", "Starfire", "Звездный огонь").spec(BALANCE).school(School.ARCANE).ranged().cast(2.25).cost(MANA, 0.3)
                .gen(ASTRAL_POWER, 10).effect(damage(sp(1.5)), aoe(6, damage(sp(0.4)))).form("moonkin_form").vfx("starfire"));
        reg(ab("sunfire", "Sunfire", "Солнечный огонь").spec(BALANCE).school(School.NATURE).ranged().cost(MANA, 0.5).gen(ASTRAL_POWER, 6)
                .effect(damage(sp(0.3)), aura("sunfire"), aoe(8, aura("sunfire"))).tag("dot"));
        reg(ab("celestial_alignment", "Celestial Alignment", "Парад планет").spec(BALANCE).target(TargetType.SELF).cooldown(180).offGcd()
                .effect(selfAura("celestial_alignment"), selfAura("eclipse_solar"), selfAura("eclipse_lunar"), energize(ASTRAL_POWER, 40)).tag("cooldown"));
        reg(ab("solar_beam", "Solar Beam", "Луч солнца").spec(BALANCE).school(School.NATURE).range(40).cooldown(60).offGcd()
                .effect(interrupt(4), area(GroundArea.Def.enemies("solar_beam", 5, 8, 0.5, aura("solar_beam")).color(0xFFFFE080))).tag("interrupt"));
        reg(ab("force_of_nature", "Force of Nature", "Сила природы").spec(BALANCE).school(School.NATURE).target(TargetType.SELF).cooldown(60)
                .gen(ASTRAL_POWER, 20).effect(summon("treant", 10, 3)).tag("cooldown", "pet"));
        reg(ab("fury_of_elune", "Fury of Elune", "Ярость Элуны").spec(BALANCE).school(School.ARCANE).range(40).cooldown(60)
                .effect(area(GroundArea.Def.enemies("fury_of_elune", 6, 8, 0.5, all(damage(School.ARCANE, sp(0.25)), energize(ASTRAL_POWER, 2))))).tag("aoe"));

        // ------------------------------------------------------------ Feral
        reg(ab("shred", "Shred", "Полоснуть").spec(FERAL).cost(ENERGY, 40).gen(COMBO_POINTS, 1).gcd(1.0).form("cat_form")
                .effect(damage(ap(1.2)), when(Cond.stealthed(), damage(ap(0.6)))).tag("builder", "weapon").vfx("claw"));
        reg(ab("rake", "Rake", "Глубокая рана").spec(FERAL).cost(ENERGY, 35).gen(COMBO_POINTS, 1).gcd(1.0).form("cat_form")
                .effect(damage(ap(0.6)), aura("rake"), when(Cond.stealthed(), aura("rake_stun"))).tag("builder", "bleed"));
        reg(ab("rip", "Rip", "Разорвать").spec(FERAL).cost(ENERGY, 20).costRange(COMBO_POINTS, 1, 5).gcd(1.0).form("cat_form")
                .effect(auraPerCombo("rip", 4, 4, false)).tag("finisher", "bleed"));
        reg(ab("thrash_cat", "Thrash", "Взбучка").spec(FERAL).target(TargetType.NONE).cost(ENERGY, 40).gen(COMBO_POINTS, 1).gcd(1.0).form("cat_form")
                .effect(aroundSelf(8, damage(ap(0.3)), aura("thrash_cat"))).tag("aoe", "bleed", "builder"));
        reg(ab("swipe_cat", "Swipe", "Размах").spec(FERAL).target(TargetType.NONE).cost(ENERGY, 35).gen(COMBO_POINTS, 1).gcd(1.0).form("cat_form")
                .effect(aroundSelf(8, damage(ap(0.5)))).tag("aoe", "builder").vfx("swipe"));
        reg(ab("tigers_fury", "Tiger's Fury", "Тигриное неистовство").spec(FERAL).target(TargetType.SELF).cooldown(30).offGcd().keepStealth()
                .effect(selfAura("tigers_fury"), energize(ENERGY, 50)).tag("cooldown"));
        reg(ab("berserk_cat", "Berserk", "Берсерк").spec(FERAL).target(TargetType.SELF).cooldown(180).offGcd().form("cat_form")
                .effect(selfAura("berserk_cat")).tag("cooldown"));
        reg(ab("prowl", "Prowl", "Крадущийся зверь").spec(FERAL).target(TargetType.SELF).cooldown(2).offGcd().keepStealth().form("cat_form")
                .requires(RogueContent.OUT_OF_COMBAT).effect(selfAura("prowl")).tag("utility"));
        reg(ab("maim", "Maim", "Калечение").spec(FERAL).cost(ENERGY, 30).costRange(COMBO_POINTS, 1, 5).cooldown(20).gcd(1.0).form("cat_form")
                .effect(damage(ap(0.3).perCombo()), auraPerCombo("maim", 0, 1, false)).tag("finisher", "cc"));
        reg(ab("primal_wrath", "Primal Wrath", "Первобытный гнев").spec(FERAL).target(TargetType.NONE).cost(ENERGY, 20).costRange(COMBO_POINTS, 1, 5)
                .gcd(1.0).form("cat_form").effect(aroundSelf(8, damage(ap(0.2).perCombo()), auraPerCombo("rip", 2, 2, false))).tag("finisher", "aoe", "bleed"));
        reg(ab("feral_frenzy", "Feral Frenzy", "Дикое бешенство").spec(FERAL).cost(ENERGY, 25).cooldown(45).gcd(1.0).form("cat_form")
                .gen(COMBO_POINTS, 5).effect(damage(ap(2.0)), aura("rake")).tag("cooldown", "builder"));

        // ------------------------------------------------------------ Guardian
        reg(ab("mangle", "Mangle", "Увечье").spec(GUARDIAN).cooldown(6).hastedCooldown().gen(RAGE, 15).form("bear_form")
                .effect(damage(ap(1.4))).tag("weapon").vfx("maul"));
        reg(ab("thrash_bear", "Thrash", "Взбучка").spec(GUARDIAN).target(TargetType.NONE).cooldown(6).hastedCooldown().gen(RAGE, 5).form("bear_form")
                .effect(aroundSelf(8, damage(ap(0.4)), aura("thrash_bear"))).tag("aoe", "bleed").vfx("thrash"));
        reg(ab("swipe_bear", "Swipe", "Размах").spec(GUARDIAN).target(TargetType.NONE).form("bear_form").effect(aroundSelf(8, damage(ap(0.3))))
                .tag("aoe").vfx("swipe"));
        reg(ab("maul", "Maul", "Трепка").spec(GUARDIAN).cost(RAGE, 40).form("bear_form").effect(damage(ap(1.8))).tag("weapon").vfx("maul"));
        reg(ab("ironfur", "Ironfur", "Железный мех").spec(GUARDIAN).target(TargetType.SELF).cost(RAGE, 40).offGcd().cooldown(0.5).form("bear_form")
                .effect(selfAura("ironfur")).tag("defensive", "active_mitigation"));
        reg(ab("survival_instincts", "Survival Instincts", "Инстинкты выживания").spec(GUARDIAN, FERAL).target(TargetType.SELF).cooldown(180)
                .charges(2).offGcd().effect(selfAura("survival_instincts")).tag("defensive", "major_defensive"));
        reg(ab("incarnation_guardian", "Incarnation: Guardian of Ursoc", "Инкарнация: Страж Урсока").spec(GUARDIAN).target(TargetType.SELF)
                .cooldown(180).offGcd().form("bear_form").effect(selfAura("incarnation_guardian")).tag("cooldown", "defensive"));

        // ------------------------------------------------------------ Restoration
        reg(ab("rejuvenation", "Rejuvenation", "Омоложение").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.FRIENDLY)
                .cost(MANA, 2.2).effect(aura("rejuvenation")).tag("heal", "hot").vfx("nature_heal"));
        reg(ab("swiftmend", "Swiftmend", "Быстрое восстановление").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.FRIENDLY)
                .cooldown(15).cost(MANA, 2.8).effect(heal(sp(2.7))).tag("heal").vfx("nature_burst"));
        reg(ab("lifebloom", "Lifebloom", "Жизнецвет").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.FRIENDLY).cost(MANA, 1.6)
                .effect(custom("Only one Lifebloom can be active", "Только один «Жизнецвет» может быть активен", ctx -> {
                    for (UnitState u : ctx.engine.units()) if (u != ctx.target) ctx.engine.removeAura(u, "lifebloom", ctx.caster);
                }), aura("lifebloom")).tag("heal", "hot"));
        reg(ab("wild_growth", "Wild Growth", "Буйный рост").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.FRIENDLY).cast(1.5)
                .cooldown(10).cost(MANA, 4.4).effect(onTargets(Selector.injuredAllies(30, 6), aura("wild_growth"))).tag("heal", "aoe_heal").vfx("wild_growth"));
        reg(ab("efflorescence", "Efflorescence", "Цветение").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.GROUND).range(40)
                .cost(MANA, 3.4).cooldown(1).effect(area(GroundArea.Def.allies("efflorescence", 10, 30, 2, heal(sp(0.14))).color(0xFF80FF80)))
                .tag("heal", "aoe_heal"));
        reg(ab("tranquility", "Tranquility", "Спокойствие").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.NONE).cooldown(180)
                .channel(5, 1).tick(onTargets(Selector.group(40), heal(sp(0.7)))).tag("heal", "raid_cd").vfx("tranquility"));
        reg(ab("ironbark", "Ironbark", "Железная кора").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.FRIENDLY).cooldown(90)
                .offGcd().effect(aura("ironbark")).tag("external"));
        reg(ab("innervate", "Innervate", "Озарение").spec(RESTORATION_DRUID, BALANCE).school(School.NATURE).target(TargetType.FRIENDLY)
                .cooldown(180).offGcd().effect(aura("innervate")).tag("cooldown"));
        reg(ab("natures_swiftness", "Nature's Swiftness", "Природная стремительность").spec(RESTORATION_DRUID).target(TargetType.SELF)
                .cooldown(60).offGcd().effect(selfAura("natures_swiftness")).tag("cooldown"));
        reg(ab("natures_cure", "Nature's Cure", "Природный целитель").spec(RESTORATION_DRUID).school(School.NATURE).target(TargetType.FRIENDLY)
                .cooldown(8).cost(MANA, 1.3).effect(dispel(3, DispelType.MAGIC, DispelType.CURSE, DispelType.POISON)).tag("dispel"));
        reg(ab("incarnation_tree", "Incarnation: Tree of Life", "Инкарнация: Древо Жизни").spec(RESTORATION_DRUID).target(TargetType.SELF)
                .cooldown(180).offGcd().effect(selfAura("incarnation_tree")).tag("cooldown"));

        // ------------------------------------------------------------ kit
        kit.extraResources.addAll(java.util.EnumSet.of(MANA, ENERGY, COMBO_POINTS, RAGE));
        kit.common("cat_form", "bear_form", "travel_form", "caster_form", "moonfire", "wrath", "regrowth", "rebirth", "barkskin",
                "entangling_roots", "soothe", "skull_bash", "stampeding_roar", "growl", "ferocious_bite", "frenzied_regeneration");
        kit.spec(BALANCE, "moonkin_form", "starsurge", "starfall", "starfire", "sunfire", "celestial_alignment", "solar_beam",
                "remove_corruption", "innervate");
        kit.spec(FERAL, "shred", "rake", "rip", "thrash_cat", "swipe_cat", "tigers_fury", "berserk_cat", "prowl", "maim",
                "survival_instincts", "remove_corruption");
        kit.spec(GUARDIAN, "mangle", "thrash_bear", "swipe_bear", "maul", "ironfur", "survival_instincts", "incarnation_guardian",
                "remove_corruption");
        kit.spec(RESTORATION_DRUID, "rejuvenation", "swiftmend", "lifebloom", "wild_growth", "efflorescence", "tranquility", "ironbark",
                "innervate", "natures_swiftness", "natures_cure");
        kit.passive(BALANCE, "balance_basics");
        kit.passive(FERAL, "feral_basics");
        kit.passive(GUARDIAN, "guardian_basics");
        kit.passive(RESTORATION_DRUID, "restoration_druid_basics");
        kit.bar(BALANCE, "wrath", "starfire", "moonfire", "sunfire", "starsurge", "starfall", "celestial_alignment", "solar_beam",
                "moonkin_form", "barkskin", "regrowth", "rebirth");
        kit.bar(FERAL, "shred", "rake", "rip", "ferocious_bite", "thrash_cat", "swipe_cat", "tigers_fury", "skull_bash", "prowl",
                "berserk_cat", "survival_instincts", "regrowth");
        kit.bar(GUARDIAN, "mangle", "thrash_bear", "swipe_bear", "maul", "ironfur", "frenzied_regeneration", "moonfire", "skull_bash",
                "growl", "barkskin", "survival_instincts", "incarnation_guardian");
        kit.bar(RESTORATION_DRUID, "rejuvenation", "regrowth", "lifebloom", "swiftmend", "wild_growth", "efflorescence", "natures_cure",
                "ironbark", "tranquility", "natures_swiftness", "innervate", "rebirth");

        kit.rotation(BALANCE, of(
                self("barkskin").when(selfHealthBelow(0.4)).urgent(),
                self("moonkin_form").when(Cond.hasAura("moonkin_form").not()),
                interrupt("solar_beam"),
                self("celestial_alignment"),
                use("moonfire").when(Cond.targetHasAura("moonfire").not()),
                use("sunfire").when(Cond.targetHasAura("sunfire").not()),
                use("starfall").when(enemiesAround(40, 3).and(resourceAtLeast(ASTRAL_POWER, 50))),
                use("starsurge").when(resourceAtLeast(ASTRAL_POWER, 70)),
                use("starfire").when(Cond.hasAura("eclipse_lunar").or(enemiesAround(40, 3))),
                use("wrath")));
        kit.rotation(FERAL, of(
                self("survival_instincts").when(selfHealthBelow(0.3)).urgent(),
                heal("regrowth").below(0.5).when(Cond.hasAura("predatory_swiftness")).on(com.wowcraft.core.bot.BotTarget.SELF).urgent(),
                self("cat_form").when(Cond.hasAura("cat_form").not()),
                interrupt("skull_bash"),
                self("tigers_fury").when(resourceBelow(ENERGY, 40)),
                self("berserk_cat"),
                use("rip").when(resourceAtLeast(COMBO_POINTS, 5).and(Cond.targetHasAura("rip").not())),
                use("ferocious_bite").when(resourceAtLeast(COMBO_POINTS, 5)),
                use("rake").when(Cond.targetHasAura("rake").not()),
                use("thrash_cat").when(enemiesAround(8, 3).and(Cond.targetHasAura("thrash_cat").not())),
                use("swipe_cat").when(enemiesAround(8, 3)),
                use("shred")));
        kit.rotation(GUARDIAN, of(
                self("survival_instincts").when(selfHealthBelow(0.3)).urgent(),
                self("frenzied_regeneration").when(selfHealthBelow(0.55)).urgent(),
                self("ironfur").when(resourceAtLeast(RAGE, 40)).urgent(),
                self("barkskin").when(selfHealthBelow(0.6)).urgent(),
                self("bear_form").when(Cond.hasAura("bear_form").not()),
                interrupt("skull_bash"),
                self("incarnation_guardian"),
                use("thrash_bear"),
                use("mangle"),
                use("moonfire").when(Cond.targetHasAura("moonfire").not()),
                use("maul").when(resourceAtLeast(RAGE, 90)),
                use("swipe_bear")));
        kit.rotation(RESTORATION_DRUID, of(
                heal("ironbark").below(0.3).urgent(),
                use("tranquility").on(com.wowcraft.core.bot.BotTarget.SELF).when(Cond.alliesBelow(0.45, 3)).urgent(),
                self("barkskin").when(selfHealthBelow(0.4)).urgent(),
                dispel("natures_cure"),
                heal("lifebloom").on(com.wowcraft.core.bot.BotTarget.TANK).when(Cond.targetHasAura("lifebloom").not()),
                heal("swiftmend").below(0.5),
                heal("wild_growth").below(0.8).when(Cond.alliesBelow(0.8, 3)),
                heal("rejuvenation").below(0.95).when(Cond.targetHasAura("rejuvenation").not()),
                heal("regrowth").below(0.6),
                use("efflorescence").on(com.wowcraft.core.bot.BotTarget.ALLY_GROUND).when(Cond.alliesBelow(0.9, 3)),
                use("moonfire").when(Cond.targetHasAura("moonfire").not()),
                use("wrath")));

        // ------------------------------------------------------------ talents
        classTree(
                t("feline_swiftness", 0, 0, "Feline Swiftness", "Кошачья стремительность").mod(Modifier.speed(0.08)),
                t("thick_hide", 0, 1, "Thick Hide", "Толстая шкура").mod(Modifier.taken(-0.04)),
                t("improved_barkskin", 0, 2, "Improved Barkskin", "Улучшенная дубовая кожа").mod(Modifier.duration("barkskin", 4)),
                t("cyclone_talent", 1, 0, "Cyclone", "Смерч").grant("cyclone"),
                t("natures_vigil", 1, 1, "Nature's Vigil", "Бдительность природы").mod(Modifier.healing(0.04), Modifier.damage(0.02)),
                t("lycaras_teachings", 1, 2, "Lycara's Teachings", "Учения Ликары").mod(Modifier.stat(Stat.AGILITY, 0.03), Modifier.stat(Stat.INTELLECT, 0.03)),
                t("heart_of_the_wild", 2, 0, "Heart of the Wild", "Сердце дикой природы").mod(Modifier.damage(0.04)),
                t("renewal", 2, 1, "Renewal", "Обновление").mod(Modifier.maxHealth(0.05)),
                t("wild_charge", 2, 2, "Wild Charge", "Дикий рывок").mod(Modifier.cooldown("skull_bash", -5), Modifier.cooldown("stampeding_roar", -60)));
        specTree(BALANCE,
                t("natures_balance", 0, 0, "Nature's Balance", "Баланс природы").mod(Modifier.regen(ASTRAL_POWER.name(), 0.0), Modifier.abilityDamage("wrath", 0.15)),
                t("shooting_stars", 0, 1, "Shooting Stars", "Падающие звезды").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("moonfire"), 0.2)),
                t("force_of_nature_talent", 0, 2, "Force of Nature", "Сила природы").grant("force_of_nature"),
                t("stellar_drift", 1, 0, "Stellar Drift", "Звездный дрейф").mod(Modifier.castWhileMoving("starfire"), Modifier.abilityDamage("starfall", 0.15)),
                t("twin_moons", 1, 1, "Twin Moons", "Близнецы-луны").mod(Modifier.abilityDamage("starfire", 0.15)),
                t("power_of_goldrinn", 1, 2, "Power of Goldrinn", "Сила Голдринна").mod(Modifier.abilityDamage("starsurge", 0.2)),
                t("fury_of_elune_talent", 2, 0, "Fury of Elune", "Ярость Элуны").grant("fury_of_elune"),
                t("incarnation_moonkin", 2, 1, "Incarnation: Chosen of Elune", "Инкарнация: Избранный Элуны").mod(Modifier.duration("celestial_alignment", 10)),
                t("orbital_strike", 2, 2, "Orbital Strike", "Орбитальный удар").mod(Modifier.cooldown("celestial_alignment", -60)));
        specTree(FERAL,
                t("sabertooth", 0, 0, "Sabertooth", "Саблезуб").mod(Modifier.abilityDamage("ferocious_bite", 0.2)),
                t("lunar_inspiration", 0, 1, "Lunar Inspiration", "Лунное вдохновение").mod(Modifier.gen("moonfire", 0)),
                t("feral_frenzy_talent", 0, 2, "Feral Frenzy", "Дикое бешенство").grant("feral_frenzy"),
                t("primal_wrath_talent", 1, 0, "Primal Wrath", "Первобытный гнев").grant("primal_wrath"),
                t("taste_for_blood", 1, 1, "Taste for Blood", "Вкус крови").mod(Modifier.tagDamage("bleed", 0.12)),
                t("predator", 1, 2, "Predator", "Хищник").mod(Modifier.cooldown("tigers_fury", -8)),
                t("incarnation_feral", 2, 0, "Incarnation: Avatar of Ashamane", "Инкарнация: Аватара Ашамане").mod(Modifier.duration("berserk_cat", 10)),
                t("soul_of_the_forest", 2, 1, "Soul of the Forest", "Душа леса").mod(Modifier.regen(ENERGY.name(), 0.15)),
                t("raging_fury", 2, 2, "Raging Fury", "Бушующая ярость").mod(Modifier.duration("tigers_fury", 5)));
        specTree(GUARDIAN,
                t("ursine_adept", 0, 0, "Ursine Adept", "Медвежья сноровка").mod(Modifier.abilityDamage("mangle", 0.2)),
                t("reinforced_fur", 0, 1, "Reinforced Fur", "Усиленный мех").mod(Modifier.duration("ironfur", 2)),
                t("galactic_guardian", 0, 2, "Galactic Guardian", "Галактический страж").mod(Modifier.abilityDamage("moonfire", 0.4)),
                t("brambles", 1, 0, "Brambles", "Колючки").mod(physicalTaken(-0.05)),
                t("tooth_and_claw", 1, 1, "Tooth and Claw", "Зуб и коготь").mod(Modifier.abilityDamage("maul", 0.3)),
                t("layered_mane", 1, 2, "Layered Mane", "Густая грива").mod(Modifier.cost("ironfur", -0.2)),
                t("rend_and_tear", 2, 0, "Rend and Tear", "Раздирание").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("thrash_bear"), 0.4)),
                t("survival_of_the_fittest", 2, 1, "Survival of the Fittest", "Выживает сильнейший").mod(Modifier.cooldown("survival_instincts", -60)),
                t("after_the_wildfire", 2, 2, "After the Wildfire", "После лесного пожара").mod(Modifier.abilityHealing("frenzied_regeneration", 0.3)));
        specTree(RESTORATION_DRUID,
                t("germination", 0, 0, "Germination", "Прорастание").mod(Modifier.duration("rejuvenation", 3)),
                t("prosperity", 0, 1, "Prosperity", "Процветание").mod(Modifier.charges("swiftmend", 1)),
                t("photosynthesis", 0, 2, "Photosynthesis", "Фотосинтез").mod(Modifier.of(ModType.TICK_RATE, ModFilter.aura("lifebloom"), 0.3)),
                t("nurturing_dormancy", 1, 0, "Nurturing Dormancy", "Питательный покой").mod(Modifier.abilityHealing("wild_growth", 0.2)),
                t("inner_peace_druid", 1, 1, "Inner Peace", "Внутренний покой").mod(Modifier.cooldown("tranquility", -60)),
                t("flourish", 1, 2, "Flourish", "Цветение жизни").mod(Modifier.tagDamage("hot", 0.0), Modifier.of(ModType.HEALING_DONE, ModFilter.tag("hot"), 0.12)),
                t("incarnation_tree_talent", 2, 0, "Incarnation: Tree of Life", "Инкарнация: Древо Жизни").grant("incarnation_tree"),
                t("stonebark", 2, 1, "Stonebark", "Каменная кора").mod(Modifier.cooldown("ironbark", -20)),
                t("verdant_infusion", 2, 2, "Verdant Infusion", "Зеленое вливание").mod(Modifier.abilityHealing("swiftmend", 0.3)));
        hero(t("hero_keeper_of_the_grove", 0, 0, "Keeper of the Grove", "Хранитель рощи").mod(Modifier.healing(0.06), Modifier.schoolDamage(School.NATURE, 0.06)),
                t("hero_druid_of_the_claw", 0, 1, "Druid of the Claw", "Друид Когтя").mod(Modifier.damage(0.05), Modifier.maxHealth(0.04)));
    }
}
