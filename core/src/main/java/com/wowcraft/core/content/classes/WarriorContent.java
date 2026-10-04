package com.wowcraft.core.content.classes;

import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.aura.Trigger;
import com.wowcraft.core.aura.TriggerType;
import com.wowcraft.core.combat.GroundArea;
import com.wowcraft.core.mod.ModFilter;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.spell.Cond;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.RAGE;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.WARRIOR;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class WarriorContent extends ClassContent {
    public WarriorContent() {
        super(WARRIOR);
    }

    private static final Cond EXECUTE_RANGE = Cond.of(c -> c.target != null && (c.caster.auras().has("sudden_death")
                    || c.target.healthFraction() < (c.caster.auras().has("massacre") ? 0.35 : 0.20)),
            "target below 20% health", "здоровье цели ниже 20%");
    private static final Cond IS_FURY = Cond.custom(u -> u.spec == FURY, "Fury", "Неистовство");

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(debuff("mortal_wounds", "Mortal Wounds", "Смертельные раны").duration(10).mod(Modifier.healingTaken(-0.25))
                .desc("Healing received reduced by 25%.", "Получаемое исцеление уменьшено на 25%."));
        reg(debuff("deep_wounds", "Deep Wounds", "Глубокие раны").duration(12).tag("bleed").dispel(DispelType.BLEED)
                .periodic(1.5, damage(ap(0.09))));
        reg(debuff("rend", "Rend", "Кровопускание").duration(15).tag("bleed").dispel(DispelType.BLEED).periodic(3, damage(ap(0.22))));
        reg(buff("overpower_buff", "Overpower", "Превосходство").duration(15).stacks(2)
                .mod(Modifier.abilityDamage("mortal_strike", 0.15)).trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("mortal_strike")).consumeStack()));
        reg(debuff("colossus_smash", "Colossus Smash", "Удар колосса").duration(10).mod(Modifier.taken(0.2))
                .desc("Damage taken increased by 20%.", "Получаемый урон увеличен на 20%.").vfx("smash"));
        reg(buff("bladestorm", "Bladestorm", "Вихрь клинков").duration(4).mod(Modifier.immune("ALL")).hidden());
        reg(buff("sweeping_strikes", "Sweeping Strikes", "Размашистые удары").duration(12)
                .desc("Your single-target attacks also hit a nearby enemy.", "Ваши атаки также поражают противника рядом.")
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, cleave(0.6, 8, 1)).filter(ModFilter.notTag("aoe")).icd(0.1)));
        dr("die_by_the_sword", "Die by the Sword", "Бой насмерть", 0.3, 8);
        reg(buff("avatar", "Avatar", "Аватара").duration(20).mod(Modifier.damage(0.2)).mod(Modifier.immune("ROOT"))
                .desc("Damage increased by 20%.", "Урон увеличен на 20%.").vfx("avatar"));
        reg(buff("battle_shout", "Battle Shout", "Боевой крик").duration(3600).persistent()
                .mod(Modifier.stat(Stat.STRENGTH, 0.05), Modifier.stat(Stat.AGILITY, 0.05))
                .desc("Strength and Agility increased by 5%.", "Сила и ловкость увеличены на 5%."));
        reg(buff("rallying_cry", "Rallying Cry", "Ободряющий клич").duration(10).mod(Modifier.maxHealth(0.10))
                .desc("Maximum health increased by 10%.", "Максимальный запас здоровья увеличен на 10%."));
        cc("intimidating_shout", "Intimidating Shout", "Устрашающий крик", CcType.FEAR, 8, DispelType.NONE);
        reg(debuff("hamstring", "Hamstring", "Подрезать сухожилия").duration(15).mod(Modifier.speed(-0.5)).cc(CcType.SLOW));
        reg(buff("spell_reflection", "Spell Reflection", "Отражение заклинаний").duration(5).mods(magicTaken(-0.6))
                .desc("Magic damage taken reduced by 60%.", "Получаемый магический урон уменьшен на 60%."));
        reg(buff("victorious", "Victorious", "Победитель").duration(20).hidden());
        reg(passive("warrior_kill_watch", "Victory Rush", "Победный раж")
                .trigger(Trigger.on(TriggerType.KILL, selfAura("victorious"))));
        cc("charge_root", "Charge", "Рывок", CcType.ROOT, 1, DispelType.NONE);
        cc("storm_bolt", "Storm Bolt", "Удар громовержца", CcType.STUN, 4, DispelType.NONE);
        cc("shockwave", "Shockwave", "Ударная волна", CcType.STUN, 2, DispelType.NONE);
        reg(buff("sudden_death", "Sudden Death", "Внезапная смерть").duration(12)
                .desc("Execute can be used on any target.", "«Казнь» можно применить к любой цели.")
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("execute")).consumeStack()));
        reg(passive("massacre", "Massacre", "Массовая резня").desc("Execute is usable below 35% health.", "«Казнь» можно применить к цели с менее чем 35% здоровья."));
        reg(debuff("thunderous_roar", "Thunderous Roar", "Громоподобный рык").duration(8).tag("bleed").periodic(2, damage(ap(0.25))));
        // Fury
        reg(buff("enrage", "Enrage", "Исступление").duration(4).dispel(DispelType.ENRAGE)
                .mod(Modifier.haste(0.15), Modifier.damage(0.10)).desc("Haste +15%, damage +10%.", "Скорость +15%, урон +10%.").vfx("enrage"));
        reg(buff("recklessness", "Recklessness", "Безрассудство").duration(12).mod(Modifier.crit(20), Modifier.damage(0.1))
                .desc("Critical strike chance +20%, damage +10%.", "Шанс крит. удара +20%, урон +10%.").vfx("recklessness"));
        reg(buff("enraged_regeneration", "Enraged Regeneration", "Безудержное восстановление").duration(8).mod(Modifier.taken(-0.3))
                .periodic(1, healPct(0.02)).unhastedTicks());
        reg(buff("meat_cleaver", "Meat Cleaver", "Мясорубка").duration(20).stacks(4).stacksPerApply(4)
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, cleave(0.5, 8, 4)).filter(ModFilter.notTag("aoe")).consumeStack().icd(0.2)));
        reg(passive("fury_basics", "Fury", "Неистовство").mod(Modifier.cost("execute", -1.0))
                .trigger(Trigger.on(TriggerType.CRIT_DEALT, selfAura("enrage")).filter(ModFilter.ability("bloodthirst"))));
        reg(debuff("odyns_fury", "Odyn's Fury", "Ярость Одина").duration(4).tag("bleed").periodic(1, damage(ap(0.15))));
        // Protection
        reg(buff("shield_block", "Shield Block", "Блок щитом").duration(6).mod(physicalTaken(-0.3), Modifier.abilityDamage("shield_slam", 0.3))
                .desc("Physical damage taken reduced by 30%.", "Получаемый физический урон уменьшен на 30%."));
        reg(buff("ignore_pain", "Ignore Pain", "Стойкость к боли").duration(12).absorb().noPandemic());
        dr("shield_wall", "Shield Wall", "Глухая оборона", 0.4, 8);
        reg(buff("last_stand", "Last Stand", "Ни шагу назад").duration(15).mod(Modifier.maxHealth(0.3)));
        reg(debuff("demoralizing_shout", "Demoralizing Shout", "Деморализующий крик").duration(8).mod(Modifier.damage(-0.2))
                .desc("Damage dealt reduced by 20%.", "Наносимый урон уменьшен на 20%."));
        reg(debuff("thunder_clap", "Thunder Clap", "Удар грома").duration(10).mod(Modifier.speed(-0.2)).cc(CcType.SLOW));
        reg(buff("revenge_free", "Revenge!", "Реванш!").duration(6).mod(Modifier.cost("revenge", -1.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("revenge")).consumeStack()));
        reg(passive("protection_basics", "Vanguard", "Авангард").mod(Modifier.of(com.wowcraft.core.mod.ModType.ARMOR_PCT, 0.5),
                        Modifier.stat(Stat.STAMINA, 0.1))
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, selfAura("revenge_free")).chance(0.25).icd(3)));
        reg(passive("arms_basics", "Seasoned Soldier", "Опытный солдат").mod(Modifier.damage(0.15))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, selfAura("sudden_death")).chance(0.08)));

        // ------------------------------------------------------------ class abilities
        reg(ab("charge", "Charge", "Рывок").range(25).minRange(8).cooldown(20).offGcd().gen(RAGE, 20)
                .effect(charge(), aura("charge_root"), damage(ap(0.2))).tag("movement", "gap_closer").vfx("charge"));
        reg(ab("pummel", "Pummel", "Зуботычина").cooldown(15).offGcd().effect(interrupt(4)).tag("interrupt").usableWhileCasting());
        reg(ab("heroic_leap", "Heroic Leap", "Героический прыжок").target(TargetType.GROUND).range(40).minRange(8).cooldown(45)
                .effect(leap(), delayed(0.4, onTargets(com.wowcraft.core.spell.Selector.groundEnemies(8), damage(ap(0.5)))))
                .tag("movement").vfx("leap"));
        reg(ab("battle_shout", "Battle Shout", "Боевой крик").target(TargetType.SELF).effect(groupAura("battle_shout", 40)).tag("buff"));
        reg(ab("victory_rush", "Victory Rush", "Победный раж").requires(Cond.hasAura("victorious"))
                .effect(damage(ap(0.6)), healPct(0.2), removeSelfAura("victorious")).tag("heal"));
        reg(ab("impending_victory", "Impending Victory", "Верная победа").cost(RAGE, 10).cooldown(25)
                .effect(damage(ap(0.5)), healPct(0.3)).tag("heal"));
        reg(ab("rallying_cry", "Rallying Cry", "Ободряющий клич").target(TargetType.SELF).cooldown(180).offGcd()
                .effect(groupAura("rallying_cry", 40)).tag("defensive", "raid_cd"));
        reg(ab("intimidating_shout", "Intimidating Shout", "Устрашающий крик").target(TargetType.NONE).cooldown(90)
                .effect(aroundSelf(8, aura("intimidating_shout"))).tag("cc", "aoe"));
        reg(ab("taunt", "Taunt", "Провокация").range(30).cooldown(8).offGcd().effect(taunt()).tag("taunt"));
        reg(ab("hamstring", "Hamstring", "Подрезать сухожилия").cost(RAGE, 10).effect(damage(ap(0.1)), aura("hamstring")).tag("slow"));
        reg(ab("execute", "Execute", "Казнь").cost(RAGE, 20).requires(EXECUTE_RANGE)
                .effect(when(IS_FURY, all(damage(ap(1.8)), energize(RAGE, 20)), spendExtra(RAGE, 20, 1.0, damage(ap(2.4)))),
                        when(Cond.custom(u -> u.spec == ARMS, "Arms", "Оружие"), aura("deep_wounds")))
                .tag("execute").vfx("execute"));
        reg(ab("spell_reflection", "Spell Reflection", "Отражение заклинаний").target(TargetType.SELF).cooldown(25).offGcd()
                .effect(selfAura("spell_reflection")).tag("defensive"));
        reg(ab("avatar", "Avatar", "Аватара").target(TargetType.SELF).cooldown(90).offGcd().effect(selfAura("avatar"), energize(RAGE, 10)).tag("cooldown"));
        reg(ab("storm_bolt", "Storm Bolt", "Удар громовержца").range(20).cooldown(30).effect(projectile(40, damage(ap(0.5)), aura("storm_bolt"))).tag("cc"));
        reg(ab("thunderous_roar", "Thunderous Roar", "Громоподобный рык").target(TargetType.NONE).cooldown(90)
                .effect(aroundSelf(12, damage(ap(1.0)), aura("thunderous_roar"))).tag("aoe", "cooldown").vfx("roar"));
        reg(ab("berserker_rage", "Berserker Rage", "Ярость берсерка").target(TargetType.SELF).cooldown(60).offGcd().usableWhileCc()
                .effect(custom("Breaks fear effects and enrages you", "Снимает эффекты страха и приводит вас в исступление", ctx -> {
                    for (var a : new java.util.ArrayList<>(ctx.caster.auras().all()))
                        if (a.def.cc == CcType.FEAR || a.def.cc == CcType.INCAPACITATE) ctx.engine.removeAura(a, false);
                }), selfAura("enrage")).tag("defensive"));

        // ------------------------------------------------------------ Arms
        reg(ab("mortal_strike", "Mortal Strike", "Смертельный удар").spec(ARMS).cost(RAGE, 30).cooldown(6).hastedCooldown()
                .effect(damage(ap(2.8)), aura("mortal_wounds"), aura("deep_wounds")).tag("weapon").vfx("slash_red"));
        reg(ab("overpower", "Overpower", "Превосходство").spec(ARMS).cooldown(12).charges(2).hastedCooldown()
                .effect(damage(ap(1.5)), selfAura("overpower_buff")).tag("weapon").vfx("slash"));
        reg(ab("slam", "Slam", "Мощный удар").spec(ARMS).cost(RAGE, 20).effect(damage(ap(1.4))).tag("weapon").vfx("slam"));
        reg(ab("colossus_smash", "Colossus Smash", "Удар колосса").spec(ARMS).cooldown(45)
                .effect(damage(ap(2.0)), aura("colossus_smash")).tag("weapon", "cooldown").vfx("smash"));
        reg(ab("warbreaker", "Warbreaker", "Боевой разрушитель").spec(ARMS).target(TargetType.NONE).cooldown(45)
                .effect(aroundSelf(8, damage(ap(1.2)), aura("colossus_smash"))).tag("aoe", "cooldown").vfx("smash"));
        reg(ab("bladestorm", "Bladestorm", "Вихрь клинков").spec(ARMS).target(TargetType.NONE).cooldown(90).channel(4, 1).moving()
                .effect(selfAura("bladestorm")).tick(aroundSelf(8, damage(ap(0.9)))).tag("aoe", "cooldown").vfx("bladestorm"));
        reg(ab("sweeping_strikes", "Sweeping Strikes", "Размашистые удары").spec(ARMS).target(TargetType.SELF).cooldown(30).offGcd()
                .effect(selfAura("sweeping_strikes")).tag("aoe"));
        reg(ab("whirlwind", "Whirlwind", "Вихрь").spec(ARMS).target(TargetType.NONE).cost(RAGE, 30)
                .effect(aroundSelf(8, damage(ap(0.75)))).tag("aoe").vfx("whirlwind"));
        reg(ab("rend", "Rend", "Кровопускание").spec(ARMS).cost(RAGE, 20).effect(damage(ap(0.2)), aura("rend")).tag("bleed"));
        reg(ab("die_by_the_sword", "Die by the Sword", "Бой насмерть").spec(ARMS).target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("die_by_the_sword")).tag("defensive"));

        // ------------------------------------------------------------ Fury
        reg(ab("bloodthirst", "Bloodthirst", "Кровожадность").spec(FURY).cooldown(4.5).hastedCooldown().gen(RAGE, 8)
                .effect(damage(ap(0.9)), healPct(0.03), chance(0.3, selfAura("enrage"))).tag("weapon").vfx("slash_red"));
        reg(ab("raging_blow", "Raging Blow", "Яростный выпад").spec(FURY).cooldown(8).charges(2).hastedCooldown().gen(RAGE, 12)
                .effect(damage(ap(1.05))).tag("weapon").vfx("slash"));
        reg(ab("rampage", "Rampage", "Буйство").spec(FURY).cost(RAGE, 80)
                .effect(damage(ap(2.0)), selfAura("enrage")).tag("weapon").vfx("rampage"));
        reg(ab("whirlwind_fury", "Whirlwind", "Вихрь").spec(FURY).target(TargetType.NONE).gen(RAGE, 5)
                .effect(aroundSelf(8, damage(ap(0.45))), selfAura("meat_cleaver")).tag("aoe").vfx("whirlwind"));
        reg(ab("recklessness", "Recklessness", "Безрассудство").spec(FURY).target(TargetType.SELF).cooldown(90).offGcd()
                .effect(selfAura("recklessness")).tag("cooldown"));
        reg(ab("enraged_regeneration", "Enraged Regeneration", "Безудержное восстановление").spec(FURY).target(TargetType.SELF)
                .cooldown(120).offGcd().effect(selfAura("enraged_regeneration"), healPct(0.15)).tag("defensive"));
        reg(ab("onslaught", "Onslaught", "Натиск").spec(FURY).cooldown(18).gen(RAGE, 10).effect(damage(ap(1.8)), selfAura("enrage")).tag("weapon"));
        reg(ab("odyns_fury", "Odyn's Fury", "Ярость Одина").spec(FURY).target(TargetType.NONE).cooldown(45).gen(RAGE, 15)
                .effect(aroundSelf(12, damage(ap(1.6)), aura("odyns_fury")), selfAura("enrage")).tag("aoe", "cooldown").vfx("odyns_fury"));

        // ------------------------------------------------------------ Protection
        reg(ab("shield_slam", "Shield Slam", "Мощный удар щитом").spec(PROTECTION_WARRIOR).cooldown(9).hastedCooldown().gen(RAGE, 15)
                .effect(damage(ap(1.5))).tag("weapon").vfx("shield_slam"));
        reg(ab("thunder_clap", "Thunder Clap", "Удар грома").spec(PROTECTION_WARRIOR).target(TargetType.NONE).cooldown(6).hastedCooldown()
                .gen(RAGE, 5).effect(aroundSelf(8, damage(ap(0.55)), aura("thunder_clap"))).tag("aoe").vfx("thunder_clap"));
        reg(ab("revenge", "Revenge", "Реванш").spec(PROTECTION_WARRIOR).cost(RAGE, 20).target(TargetType.NONE)
                .effect(cone(140, 8, damage(ap(0.8)))).tag("aoe").vfx("cleave"));
        reg(ab("devastate", "Devastate", "Сокрушение").spec(PROTECTION_WARRIOR).effect(damage(ap(0.6)), chance(0.3, selfAura("revenge_free"))).tag("weapon"));
        reg(ab("ignore_pain", "Ignore Pain", "Стойкость к боли").spec(PROTECTION_WARRIOR).target(TargetType.SELF).cost(RAGE, 35)
                .cooldown(1).offGcd().effect(selfAbsorb("ignore_pain", ap(2.2)), custom("Absorb is capped at 30% of maximum health",
                        "Поглощение не превышает 30% макс. здоровья", ctx -> {
                            var a = ctx.caster.auras().get("ignore_pain");
                            if (a != null) a.absorbRemaining = Math.min(a.absorbRemaining, ctx.caster.maxHealth() * 0.3);
                        })).tag("defensive", "active_mitigation"));
        reg(ab("shield_block", "Shield Block", "Блок щитом").spec(PROTECTION_WARRIOR).target(TargetType.SELF).cost(RAGE, 30)
                .cooldown(16).charges(2).offGcd().effect(selfAura("shield_block")).tag("defensive", "active_mitigation"));
        reg(ab("shield_wall", "Shield Wall", "Глухая оборона").spec(PROTECTION_WARRIOR).target(TargetType.SELF).cooldown(180).offGcd()
                .effect(selfAura("shield_wall")).tag("defensive", "major_defensive"));
        reg(ab("last_stand", "Last Stand", "Ни шагу назад").spec(PROTECTION_WARRIOR).target(TargetType.SELF).cooldown(180).offGcd()
                .effect(selfAura("last_stand"), healPct(0.3)).tag("defensive", "major_defensive"));
        reg(ab("demoralizing_shout", "Demoralizing Shout", "Деморализующий крик").spec(PROTECTION_WARRIOR).target(TargetType.NONE)
                .cooldown(45).gen(RAGE, 0).effect(aroundSelf(10, aura("demoralizing_shout"))).tag("defensive", "aoe"));
        reg(ab("shockwave", "Shockwave", "Ударная волна").spec(PROTECTION_WARRIOR).target(TargetType.NONE).cooldown(40)
                .effect(cone(90, 10, damage(ap(0.3)), aura("shockwave"))).tag("cc", "aoe").vfx("shockwave"));
        reg(ab("ravager", "Ravager", "Опустошитель").spec(PROTECTION_WARRIOR, ARMS).target(TargetType.GROUND).range(40).cooldown(90)
                .effect(area(GroundArea.Def.enemies("ravager", 8, 10, 1, all(damage(ap(0.35)), energize(RAGE, 2))))).tag("aoe", "cooldown"));

        // ------------------------------------------------------------ kit
        kit.common("charge", "pummel", "heroic_leap", "battle_shout", "victory_rush", "rallying_cry", "intimidating_shout",
                "taunt", "hamstring", "execute", "spell_reflection", "avatar", "berserker_rage");
        kit.classPassives.add("warrior_kill_watch");
        kit.spec(ARMS, "mortal_strike", "overpower", "slam", "colossus_smash", "bladestorm", "sweeping_strikes", "whirlwind", "die_by_the_sword");
        kit.spec(FURY, "bloodthirst", "raging_blow", "rampage", "whirlwind_fury", "recklessness", "enraged_regeneration");
        kit.spec(PROTECTION_WARRIOR, "shield_slam", "thunder_clap", "revenge", "devastate", "ignore_pain", "shield_block", "shield_wall",
                "last_stand", "demoralizing_shout", "shockwave");
        kit.passive(ARMS, "arms_basics");
        kit.passive(FURY, "fury_basics");
        kit.passive(PROTECTION_WARRIOR, "protection_basics");
        kit.bar(ARMS, "mortal_strike", "overpower", "slam", "execute", "colossus_smash", "whirlwind", "charge", "pummel",
                "sweeping_strikes", "bladestorm", "die_by_the_sword", "victory_rush");
        kit.bar(FURY, "bloodthirst", "raging_blow", "rampage", "execute", "whirlwind_fury", "recklessness", "charge", "pummel",
                "avatar", "enraged_regeneration", "heroic_leap", "victory_rush");
        kit.bar(PROTECTION_WARRIOR, "shield_slam", "thunder_clap", "revenge", "devastate", "ignore_pain", "shield_block", "charge",
                "pummel", "taunt", "shield_wall", "last_stand", "demoralizing_shout");

        kit.rotation(ARMS, of(
                self("die_by_the_sword").when(selfHealthBelow(0.35)).urgent(),
                use("victory_rush").when(selfHealthBelow(0.8)).urgent(),
                interrupt("pummel"),
                self("avatar"),
                use("colossus_smash"),
                self("sweeping_strikes").when(enemiesAround(8, 2)),
                use("bladestorm").when(enemiesAround(8, 3)),
                use("execute").when(resourceAtLeast(RAGE, 20)),
                use("mortal_strike"),
                use("overpower"),
                use("whirlwind").when(enemiesAround(8, 3)),
                use("slam").when(resourceAtLeast(RAGE, 50)),
                self("battle_shout").when(Cond.hasAura("battle_shout").not())));
        kit.rotation(FURY, of(
                self("enraged_regeneration").when(selfHealthBelow(0.35)).urgent(),
                use("victory_rush").when(selfHealthBelow(0.8)).urgent(),
                interrupt("pummel"),
                self("recklessness"),
                self("avatar"),
                use("rampage").when(resourceAtLeast(RAGE, 80)),
                use("whirlwind_fury").when(enemiesAround(8, 2).and(Cond.hasAura("meat_cleaver").not())),
                use("execute"),
                use("bloodthirst"),
                use("raging_blow"),
                use("whirlwind_fury"),
                self("battle_shout").when(Cond.hasAura("battle_shout").not())));
        kit.rotation(PROTECTION_WARRIOR, of(
                self("last_stand").when(selfHealthBelow(0.25)).urgent(),
                self("shield_wall").when(selfHealthBelow(0.35)).urgent(),
                self("shield_block").when(Cond.hasAura("shield_block").not()).urgent(),
                self("ignore_pain").when(resourceAtLeast(RAGE, 60).and(selfHealthBelow(0.9))).urgent(),
                interrupt("pummel"),
                use("demoralizing_shout").when(enemiesAround(10, 2)),
                use("shield_slam"),
                use("thunder_clap"),
                use("revenge").when(resourceAtLeast(RAGE, 40).or(Cond.hasAura("revenge_free"))),
                use("execute").when(resourceAtLeast(RAGE, 40)),
                use("devastate"),
                self("battle_shout").when(Cond.hasAura("battle_shout").not())));

        // ------------------------------------------------------------ talents
        classTree(
                t("war_machine", 0, 0, "War Machine", "Боевая машина").mod(Modifier.speed(0.1), Modifier.damage(0.02)),
                t("double_time", 0, 1, "Double Time", "Двойной рывок").mod(Modifier.charges("charge", 1), Modifier.cooldown("charge", -3)),
                t("impending_victory", 0, 2, "Impending Victory", "Верная победа").mod(Modifier.replace("victory_rush", "impending_victory")),
                t("storm_bolt", 1, 0, "Storm Bolt", "Удар громовержца").grant("storm_bolt"),
                t("bounding_stride", 1, 1, "Bounding Stride", "Широкий шаг").mod(Modifier.cooldown("heroic_leap", -15)),
                t("second_wind", 1, 2, "Second Wind", "Второе дыхание").aura("second_wind"),
                t("rumbling_earth", 2, 0, "Rumbling Earth", "Грохочущая земля").mod(Modifier.duration("avatar", 5), Modifier.cooldown("shockwave", -15)),
                t("thunderous_roar", 2, 1, "Thunderous Roar", "Громоподобный рык").grant("thunderous_roar"),
                t("unbreakable_will", 2, 2, "Unbreakable Will", "Несокрушимая воля").mod(Modifier.cooldown("die_by_the_sword", -30),
                        Modifier.cooldown("shield_wall", -60), Modifier.cooldown("enraged_regeneration", -30)));
        reg(passive("second_wind", "Second Wind", "Второе дыхание").periodic(1, when(selfHealthBelow(0.35), healPct(0.02))).unhastedTicks()
                .desc("Regenerate 2% health per second while below 35% health.", "Восполняет 2% здоровья в секунду, пока здоровье ниже 35%."));
        specTree(ARMS,
                t("massacre", 0, 0, "Massacre", "Массовая резня").aura("massacre"),
                t("improved_overpower", 0, 1, "Improved Overpower", "Улучшенное превосходство").mod(Modifier.charges("overpower", 1)),
                t("rend_talent", 0, 2, "Rend", "Кровопускание").grant("rend"),
                t("tactician", 1, 0, "Tactician", "Тактик").aura("tactician"),
                t("fervor_of_battle", 1, 1, "Fervor of Battle", "Боевое рвение").mod(Modifier.abilityDamage("whirlwind", 0.3)),
                t("bloodletting", 1, 2, "Bloodletting", "Кровопускание").mod(Modifier.tagDamage("bleed", 0.2)),
                t("warbreaker", 2, 0, "Warbreaker", "Боевой разрушитель").mod(Modifier.replace("colossus_smash", "warbreaker")),
                t("sharpened_blades", 2, 1, "Sharpened Blades", "Заточенные клинки").mod(
                        Modifier.of(com.wowcraft.core.mod.ModType.CRIT_DAMAGE, ModFilter.ability("mortal_strike"), 0.3),
                        Modifier.abilityCrit("execute", 10)),
                t("juggernaut", 2, 2, "Juggernaut", "Неудержимый").mod(Modifier.abilityDamage("execute", 0.25), Modifier.abilityDamage("mortal_strike", 0.1)));
        reg(passive("tactician", "Tactician", "Тактик").desc("Mortal Strike has a 30% chance to reset Overpower.", "«Смертельный удар» с вероятностью 30% сбрасывает «Превосходство».")
                .trigger(Trigger.on(TriggerType.CAST, resetCooldown("overpower")).filter(ModFilter.ability("mortal_strike")).chance(0.3)));
        specTree(FURY,
                t("improved_raging_blow", 0, 0, "Improved Raging Blow", "Улучшенный яростный выпад").mod(Modifier.abilityDamage("raging_blow", 0.2)),
                t("cruelty", 0, 1, "Cruelty", "Жестокость").mod(Modifier.abilityCrit("raging_blow", 15)),
                t("onslaught_talent", 0, 2, "Onslaught", "Натиск").grant("onslaught"),
                t("massacre_fury", 1, 0, "Massacre", "Массовая резня").aura("massacre"),
                t("fresh_meat", 1, 1, "Fresh Meat", "Свежее мясо").mod(Modifier.abilityCrit("bloodthirst", 15)),
                t("improved_whirlwind", 1, 2, "Improved Whirlwind", "Улучшенный вихрь").mod(Modifier.abilityDamage("whirlwind_fury", 0.5)),
                t("odyns_fury_talent", 2, 0, "Odyn's Fury", "Ярость Одина").grant("odyns_fury"),
                t("anger_management", 2, 1, "Anger Management", "Управление гневом").aura("anger_management_fury"),
                t("titanic_rage", 2, 2, "Titanic Rage", "Титаническая ярость").mod(Modifier.duration("recklessness", 4), Modifier.abilityDamage("rampage", 0.15)));
        reg(passive("anger_management_fury", "Anger Management", "Управление гневом").desc("Rampage reduces the cooldown of Recklessness by 4 sec.", "«Буйство» сокращает время восстановления «Безрассудства» на 4 сек.")
                .trigger(Trigger.on(TriggerType.CAST, cooldown("recklessness", -4)).filter(ModFilter.ability("rampage"))));
        specTree(PROTECTION_WARRIOR,
                t("brace_for_impact", 0, 0, "Brace for Impact", "Готовность к удару").mod(Modifier.abilityDamage("shield_slam", 0.15)),
                t("best_served_cold", 0, 1, "Best Served Cold", "Холодная месть").mod(Modifier.abilityDamage("revenge", 0.25)),
                t("into_the_fray", 0, 2, "Into the Fray", "В гущу боя").mod(Modifier.haste(0.05)),
                t("booming_voice", 1, 0, "Booming Voice", "Громогласный голос").mod(Modifier.gen("demoralizing_shout", 30)),
                t("indomitable", 1, 1, "Indomitable", "Несгибаемость").mod(Modifier.maxHealth(0.1)),
                t("heavy_repercussions", 1, 2, "Heavy Repercussions", "Тяжелые последствия").mod(Modifier.duration("shield_block", 2)),
                t("anger_management_prot", 2, 0, "Anger Management", "Управление гневом").aura("anger_management_prot"),
                t("ravager_talent", 2, 1, "Ravager", "Опустошитель").grant("ravager"),
                t("bolster", 2, 2, "Bolster", "Подкрепление").mod(Modifier.cooldown("last_stand", -60), Modifier.duration("last_stand", 5)));
        reg(passive("anger_management_prot", "Anger Management", "Управление гневом").desc("Ignore Pain reduces the cooldown of Shield Wall by 3 sec.", "«Стойкость к боли» сокращает время восстановления «Глухой обороны» на 3 сек.")
                .trigger(Trigger.on(TriggerType.CAST, cooldown("shield_wall", -3)).filter(ModFilter.ability("ignore_pain"))));
        hero(t("hero_colossus", 0, 0, "Colossus", "Колосс").mod(Modifier.damage(0.06), Modifier.maxHealth(0.1)),
                t("hero_slayer", 0, 1, "Slayer", "Убийца").mod(Modifier.abilityDamage("execute", 0.15), Modifier.crit(5)));
    }
}
