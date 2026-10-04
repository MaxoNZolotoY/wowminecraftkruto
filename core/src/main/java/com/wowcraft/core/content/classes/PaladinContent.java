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
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.spell.Selector;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.HOLY_POWER;
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.PALADIN;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class PaladinContent extends ClassContent {
    public PaladinContent() {
        super(PALADIN);
    }

    private static final Cond NO_FORBEARANCE = Cond.of(c -> c.target == null || !c.target.auras().has("forbearance"),
            "target is not affected by Forbearance", "на цели нет «Воздержанности»");
    private static final Cond HOSTILE_TARGET = Cond.of(c -> c.target != null && c.engine.isHostile(c.caster, c.target),
            "target is an enemy", "цель — противник");

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(debuff("forbearance", "Forbearance", "Воздержанность").duration(30).perCaster(false)
                .desc("Cannot be affected by Divine Shield, Blessing of Protection or Lay on Hands.",
                        "Нельзя применить «Божественный щит», «Благословение защиты» или «Возложение рук»."));
        reg(buff("divine_shield", "Divine Shield", "Божественный щит").duration(8).mod(Modifier.immuneDamage(), Modifier.damage(-0.5))
                .desc("Immune to all damage.", "Невосприимчивость к любому урону.").vfx("divine_shield"));
        reg(buff("blessing_of_protection", "Blessing of Protection", "Благословение защиты").duration(10)
                .mod(Modifier.ref(ModType.DAMAGE_IMMUNE, School.PHYSICAL.name(), 1)).dispel(DispelType.MAGIC)
                .desc("Immune to physical damage.", "Невосприимчивость к физическому урону."));
        reg(buff("blessing_of_freedom", "Blessing of Freedom", "Благословение свободы").duration(8)
                .mod(Modifier.immune("ROOT"), Modifier.immune("SLOW")).dispel(DispelType.MAGIC));
        reg(buff("divine_steed", "Divine Steed", "Божественный скакун").duration(4).mod(Modifier.speed(1.0)));
        cc("hammer_of_justice", "Hammer of Justice", "Молот правосудия", CcType.STUN, 6, DispelType.MAGIC);
        reg(buff("avenging_wrath", "Avenging Wrath", "Гнев карателя").duration(20)
                .mod(Modifier.damage(0.2), Modifier.healing(0.2), Modifier.crit(20))
                .desc("Damage, healing and critical strike chance increased by 20%.", "Урон, исцеление и шанс крит. удара увеличены на 20%.").vfx("wings"));
        reg(debuff("judgment", "Judgment", "Правосудие").duration(15).mod(Modifier.taken(0.05)));
        reg(buff("devotion_aura", "Devotion Aura", "Аура благочестия").duration(3600).persistent().mod(Modifier.taken(-0.03))
                .desc("Damage taken reduced by 3%.", "Получаемый урон уменьшен на 3%."));
        // Holy
        reg(buff("beacon_of_light", "Beacon of Light", "Частица Света").duration(3600).persistent()
                .desc("Receives 40% of the Paladin's healing to other targets.", "Получает 40% исцеления, которое паладин оказывает другим целям."));
        reg(passive("beacon_caster", "Beacon of Light", "Частица Света").trigger(Trigger.on(TriggerType.HEAL_DEALT, beaconTransfer())));
        reg(buff("infusion_of_light", "Infusion of Light", "Прилив Света").duration(15)
                .mod(Modifier.castTime("flash_of_light", -1.0), Modifier.abilityHealing("holy_light", 0.3))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("flash_of_light")).consumeStack()));
        reg(passive("holy_basics", "Infusion of Light", "Прилив Света")
                .trigger(Trigger.on(TriggerType.CRIT_DEALT, selfAura("infusion_of_light")).filter(ModFilter.ability("holy_shock"))));
        reg(buff("aura_mastery", "Aura Mastery", "Мастер аур").duration(8).mod(Modifier.taken(-0.2)));
        dr("divine_protection", "Divine Protection", "Божественная защита", 0.2, 8);
        reg(buff("blessing_of_sacrifice", "Blessing of Sacrifice", "Жертвенное благословение").duration(12).mod(Modifier.taken(-0.3)));
        // Protection
        reg(buff("shield_of_the_righteous", "Shield of the Righteous", "Щит праведника").duration(4.5)
                .mod(Modifier.of(ModType.ARMOR_PCT, 0.8), Modifier.taken(-0.1)));
        reg(buff("ardent_defender", "Ardent Defender", "Ревностный защитник").duration(8).mod(Modifier.taken(-0.2))
                .trigger(Trigger.on(TriggerType.LETHAL_DAMAGE, all(custom("Prevents a killing blow and heals you for 20%",
                        "Предотвращает смертельный удар и восполняет 20% здоровья", ctx -> {
                            ctx.engine.removeAura(ctx.caster, "ardent_defender", null);
                            ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, "ardent_save", 1, -1);
                            ctx.engine.rawHeal(ctx.caster, ctx.caster, Math.max(0, ctx.caster.maxHealth() * 0.2 - ctx.caster.health()));
                        }))).self()));
        reg(buff("ardent_save", "Ardent Defender", "Ревностный защитник").duration(0.2).mod(Modifier.immuneDamage()).hidden());
        dr("guardian_of_ancient_kings", "Guardian of Ancient Kings", "Защитник древних королей", 0.5, 8);
        reg(debuff("avengers_shield_silence", "Avenger's Shield", "Щит мстителя").duration(3).cc(CcType.SILENCE).dispel(DispelType.MAGIC));
        reg(buff("grand_crusader", "Grand Crusader", "Великий крестоносец").duration(0.1).hidden());
        reg(passive("protection_paladin_basics", "Grand Crusader", "Великий крестоносец")
                .mod(Modifier.of(ModType.ARMOR_PCT, 0.6), Modifier.stat(Stat.STAMINA, 0.1))
                .trigger(Trigger.on(TriggerType.CAST, resetCooldown("avengers_shield")).filter(ModFilter.ability("hammer_of_the_righteous")).chance(0.15))
                .trigger(Trigger.on(TriggerType.CAST, resetCooldown("avengers_shield")).filter(ModFilter.ability("crusader_strike")).chance(0.15)));
        // Retribution
        reg(buff("shield_of_vengeance", "Shield of Vengeance", "Щит возмездия").duration(15).absorb()
                .onRemove(onTargets(Selector.enemiesAroundCaster(8), damage(School.HOLY, ap(0.5)))));
        reg(debuff("wake_of_ashes", "Wake of Ashes", "Испепеляющий след").duration(9).school(School.HOLY).periodic(3, damage(School.HOLY, ap(0.2))));
        reg(passive("retribution_basics", "Art of War", "Искусство войны")
                .mod(Modifier.damage(-0.05))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, resetCooldown("blade_of_justice")).chance(0.15)));
        reg(debuff("consecration_slow", "Consecration", "Освящение").duration(2).mod(Modifier.speed(-0.2)).cc(CcType.SLOW).hidden());

        // ------------------------------------------------------------ class abilities
        reg(ab("crusader_strike", "Crusader Strike", "Удар воина Света").cooldown(6).charges(2).hastedCooldown().gen(HOLY_POWER, 1)
                .effect(damage(ap(1.0))).tag("weapon", "builder").vfx("holy_strike"));
        reg(ab("judgment", "Judgment", "Правосудие").school(School.HOLY).ranged().cooldown(12).hastedCooldown().gen(HOLY_POWER, 1)
                .effect(damage(sp(1.1).plusFlat(0)), aura("judgment")).tag("builder").vfx("judgment"));
        reg(ab("word_of_glory", "Word of Glory", "Торжество").school(School.HOLY).target(TargetType.FRIENDLY).cost(HOLY_POWER, 3)
                .effect(heal(sp(3.2))).tag("heal", "holy_power_spender").vfx("holy_heal"));
        reg(ab("flash_of_light", "Flash of Light", "Вспышка Света").school(School.HOLY).target(TargetType.FRIENDLY).cast(1.5).cost(MANA, 10)
                .effect(heal(sp(2.0))).tag("heal").vfx("holy_heal"));
        reg(ab("hammer_of_justice", "Hammer of Justice", "Молот правосудия").school(School.HOLY).range(10).cooldown(60)
                .effect(projectile(30, aura("hammer_of_justice"))).tag("cc").vfx("hammer"));
        reg(ab("divine_shield", "Divine Shield", "Божественный щит").school(School.HOLY).target(TargetType.SELF).cooldown(300).offGcd()
                .usableWhileCc().requires(NO_FORBEARANCE).effect(selfAura("divine_shield"), selfAura("forbearance"),
                        custom("Removes harmful effects", "Снимает отрицательные эффекты", ctx -> {
                            for (AuraInstance a : new java.util.ArrayList<>(ctx.caster.auras().all()))
                                if (a.def.harmful && !a.def.passive && !a.def.id.equals("forbearance")) ctx.engine.removeAura(a, false);
                        })).tag("defensive", "major_defensive"));
        reg(ab("blessing_of_protection", "Blessing of Protection", "Благословение защиты").school(School.HOLY).target(TargetType.FRIENDLY)
                .cooldown(300).requires(NO_FORBEARANCE).effect(aura("blessing_of_protection"), aura("forbearance")).tag("external"));
        reg(ab("lay_on_hands", "Lay on Hands", "Возложение рук").school(School.HOLY).target(TargetType.FRIENDLY).cooldown(600).offGcd()
                .requires(NO_FORBEARANCE).effect(heal(hp(1.0)), aura("forbearance")).tag("heal", "external").vfx("holy_burst"));
        reg(ab("divine_steed", "Divine Steed", "Божественный скакун").target(TargetType.SELF).cooldown(45).offGcd()
                .effect(selfAura("divine_steed")).tag("movement"));
        reg(ab("blessing_of_freedom", "Blessing of Freedom", "Благословение свободы").school(School.HOLY).target(TargetType.FRIENDLY)
                .cooldown(25).effect(aura("blessing_of_freedom"), custom("Removes roots and slows", "Снимает обездвиживание и замедление", ctx -> {
                    UnitState t = ctx.target != null ? ctx.target : ctx.caster;
                    for (AuraInstance a : new java.util.ArrayList<>(t.auras().all()))
                        if (a.def.cc == CcType.ROOT || a.def.cc == CcType.SLOW) ctx.engine.removeAura(a, false);
                })).tag("utility"));
        reg(ab("rebuke", "Rebuke", "Укор").cooldown(15).offGcd().effect(interrupt(4)).tag("interrupt"));
        reg(ab("hand_of_reckoning", "Hand of Reckoning", "Длань расплаты").school(School.HOLY).range(30).cooldown(8).offGcd()
                .effect(taunt()).tag("taunt"));
        reg(ab("consecration", "Consecration", "Освящение").school(School.HOLY).target(TargetType.NONE).cooldown(9)
                .effect(area(GroundArea.Def.enemies("consecration", 8, 12, 1, all(damage(School.HOLY, ap(0.12)), aura("consecration_slow")))
                        .centeredOnCaster().color(0xFFFFE680))).tag("aoe").vfx("consecration"));
        reg(ab("cleanse_toxins", "Cleanse Toxins", "Очищение от токсинов").school(School.HOLY).target(TargetType.FRIENDLY).cooldown(8)
                .cost(MANA, 6.5).effect(dispel(3, DispelType.POISON, DispelType.DISEASE)).tag("dispel"));
        reg(ab("avenging_wrath", "Avenging Wrath", "Гнев карателя").target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("avenging_wrath")).tag("cooldown"));
        reg(ab("intercession", "Intercession", "Заступничество").school(School.HOLY).target(TargetType.DEAD_FRIENDLY).cast(2.0)
                .cost(HOLY_POWER, 3).cooldown(600).effect(resurrect(0.6)).tag("battle_res"));
        reg(ab("devotion_aura", "Devotion Aura", "Аура благочестия").target(TargetType.SELF).cooldown(1)
                .effect(groupAura("devotion_aura", 40)).tag("buff"));
        reg(ab("hammer_of_wrath", "Hammer of Wrath", "Молот гнева").school(School.HOLY).ranged().cooldown(7.5).hastedCooldown()
                .gen(HOLY_POWER, 1).requires(Cond.of(c -> c.target != null && (c.target.healthFraction() < 0.2 || c.caster.auras().has("avenging_wrath")),
                        "target below 20% health or Avenging Wrath active", "здоровье цели ниже 20% или активен «Гнев карателя»"))
                .effect(projectile(40, damage(ap(1.6)))).tag("execute").vfx("hammer"));

        // ------------------------------------------------------------ Holy
        reg(ab("holy_shock", "Holy Shock", "Шок небес").spec(HOLY_PALADIN).school(School.HOLY).target(TargetType.ANY).range(40)
                .cooldown(7.5).hastedCooldown().gen(HOLY_POWER, 1).cost(MANA, 2.8)
                .effect(when(HOSTILE_TARGET, damage(sp(1.0)), heal(sp(1.9)))).tag("heal").vfx("holy_shock"));
        reg(ab("holy_light", "Holy Light", "Свет небес").spec(HOLY_PALADIN).school(School.HOLY).target(TargetType.FRIENDLY)
                .cast(2.5).cost(MANA, 12).gen(HOLY_POWER, 1).effect(heal(sp(3.8))).tag("heal").vfx("holy_heal"));
        reg(ab("light_of_dawn", "Light of Dawn", "Свет зари").spec(HOLY_PALADIN).school(School.HOLY).target(TargetType.NONE)
                .cost(HOLY_POWER, 3).effect(onTargets(Selector.injuredAllies(15, 5), heal(sp(1.1)))).tag("heal", "aoe_heal").vfx("holy_wave"));
        reg(ab("beacon_of_light", "Beacon of Light", "Частица Света").spec(HOLY_PALADIN).school(School.HOLY).target(TargetType.FRIENDLY)
                .cooldown(1).effect(custom("Places a Beacon on the target", "Помещает Частицу Света на цель", ctx -> {
                    for (UnitState u : ctx.engine.units()) ctx.engine.removeAura(u, "beacon_of_light", ctx.caster);
                }), aura("beacon_of_light")).tag("buff"));
        reg(ab("aura_mastery", "Aura Mastery", "Мастер аур").spec(HOLY_PALADIN).target(TargetType.SELF).cooldown(180)
                .effect(groupAura("aura_mastery", 40)).tag("defensive", "raid_cd"));
        reg(ab("divine_protection", "Divine Protection", "Божественная защита").spec(HOLY_PALADIN, RETRIBUTION).target(TargetType.SELF)
                .cooldown(60).offGcd().effect(selfAura("divine_protection")).tag("defensive"));
        reg(ab("cleanse", "Cleanse", "Очищение").spec(HOLY_PALADIN).school(School.HOLY).target(TargetType.FRIENDLY).cooldown(8)
                .cost(MANA, 6.5).effect(dispel(3, DispelType.MAGIC, DispelType.POISON, DispelType.DISEASE)).tag("dispel"));
        reg(ab("blessing_of_sacrifice", "Blessing of Sacrifice", "Жертвенное благословение").spec(HOLY_PALADIN, PROTECTION_PALADIN)
                .school(School.HOLY).target(TargetType.FRIENDLY).cooldown(120).offGcd().effect(aura("blessing_of_sacrifice")).tag("external"));
        reg(ab("divine_toll", "Divine Toll", "Божественный благовест").school(School.ARCANE).target(TargetType.NONE).cooldown(60)
                .gen(HOLY_POWER, 0).effect(custom("Casts Holy Shock (Holy), Avenger's Shield (Protection) or Judgment (Retribution) on up to 5 targets",
                        "Применяет «Шок небес», «Щит мстителя» или «Правосудие» к 5 целям", ctx -> {
                            String id = ctx.caster.spec == HOLY_PALADIN ? "holy_shock" : ctx.caster.spec == PROTECTION_PALADIN ? "avengers_shield" : "judgment";
                            java.util.List<UnitState> targets = ctx.caster.spec == HOLY_PALADIN
                                    ? Selector.injuredAllies(30, 5).select(ctx)
                                    : ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 30);
                            int n = 0;
                            for (UnitState t : targets) {
                                if (n++ >= 5) break;
                                ctx.engine.triggerAbility(ctx.caster, id, t, null);
                                ctx.engine.energize(ctx.caster, HOLY_POWER, 1);
                            }
                        })).tag("cooldown"));

        // ------------------------------------------------------------ Protection
        reg(ab("avengers_shield", "Avenger's Shield", "Щит мстителя").spec(PROTECTION_PALADIN).school(School.HOLY).ranged()
                .cooldown(15).hastedCooldown().gen(HOLY_POWER, 1)
                .effect(projectile(40, aura("avengers_shield_silence"), interrupt(3)), chain(3, 10, 1.0, false, damage(ap(1.4))))
                .tag("interrupt").vfx("avengers_shield"));
        reg(ab("hammer_of_the_righteous", "Hammer of the Righteous", "Молот праведника").spec(PROTECTION_PALADIN).cooldown(6).charges(2)
                .hastedCooldown().gen(HOLY_POWER, 1).effect(damage(ap(1.0)), aoe(8, damage(School.HOLY, ap(0.3)))).tag("weapon", "builder"));
        reg(ab("shield_of_the_righteous", "Shield of the Righteous", "Щит праведника").spec(PROTECTION_PALADIN).school(School.HOLY)
                .target(TargetType.NONE).cost(HOLY_POWER, 3).offGcd().cooldown(1)
                .effect(cone(120, 6, damage(ap(0.6))), selfAura("shield_of_the_righteous")).tag("defensive", "active_mitigation", "holy_power_spender"));
        reg(ab("ardent_defender", "Ardent Defender", "Ревностный защитник").spec(PROTECTION_PALADIN).target(TargetType.SELF).cooldown(120)
                .offGcd().effect(selfAura("ardent_defender")).tag("defensive", "major_defensive"));
        reg(ab("guardian_of_ancient_kings", "Guardian of Ancient Kings", "Защитник древних королей").spec(PROTECTION_PALADIN)
                .target(TargetType.SELF).cooldown(300).offGcd().effect(selfAura("guardian_of_ancient_kings")).tag("defensive", "major_defensive"));

        // ------------------------------------------------------------ Retribution
        reg(ab("blade_of_justice", "Blade of Justice", "Клинок правосудия").spec(RETRIBUTION).school(School.HOLY).range(12)
                .cooldown(12).hastedCooldown().gen(HOLY_POWER, 1).effect(damage(ap(1.3))).tag("builder").vfx("holy_strike"));
        reg(ab("templars_verdict", "Templar's Verdict", "Вердикт храмовника").spec(RETRIBUTION).school(School.HOLY).cost(HOLY_POWER, 3)
                .effect(damage(ap(2.5))).tag("holy_power_spender").vfx("verdict"));
        reg(ab("divine_storm", "Divine Storm", "Божественная буря").spec(RETRIBUTION).school(School.HOLY).target(TargetType.NONE)
                .cost(HOLY_POWER, 3).effect(aroundSelf(8, damage(ap(1.4)))).tag("holy_power_spender", "aoe").vfx("divine_storm"));
        reg(ab("wake_of_ashes", "Wake of Ashes", "Испепеляющий след").spec(RETRIBUTION).school(School.HOLY).target(TargetType.NONE)
                .cooldown(30).gen(HOLY_POWER, 3).effect(cone(90, 12, damage(ap(1.8)), aura("wake_of_ashes"))).tag("aoe", "cooldown").vfx("wake_of_ashes"));
        reg(ab("shield_of_vengeance", "Shield of Vengeance", "Щит возмездия").spec(RETRIBUTION).target(TargetType.SELF).cooldown(90)
                .effect(selfAbsorb("shield_of_vengeance", hp(0.3))).tag("defensive"));
        reg(ab("final_reckoning", "Final Reckoning", "Последняя расплата").spec(RETRIBUTION).school(School.HOLY).target(TargetType.GROUND)
                .range(30).cooldown(60).effect(onTargets(Selector.groundEnemies(8), damage(ap(1.5)), aura("final_reckoning"))).tag("cooldown", "aoe"));
        reg(debuff("final_reckoning", "Final Reckoning", "Последняя расплата").duration(12).mod(Modifier.taken(0.1)));

        // ------------------------------------------------------------ kit
        kit.common("crusader_strike", "judgment", "word_of_glory", "flash_of_light", "hammer_of_justice", "divine_shield",
                "blessing_of_protection", "lay_on_hands", "divine_steed", "blessing_of_freedom", "rebuke", "hand_of_reckoning",
                "consecration", "avenging_wrath", "intercession", "devotion_aura", "hammer_of_wrath");
        kit.spec(HOLY_PALADIN, "holy_shock", "holy_light", "light_of_dawn", "beacon_of_light", "aura_mastery", "divine_protection",
                "cleanse", "blessing_of_sacrifice");
        kit.spec(PROTECTION_PALADIN, "avengers_shield", "hammer_of_the_righteous", "shield_of_the_righteous", "ardent_defender",
                "guardian_of_ancient_kings", "blessing_of_sacrifice", "cleanse_toxins");
        kit.spec(RETRIBUTION, "blade_of_justice", "templars_verdict", "divine_storm", "wake_of_ashes", "shield_of_vengeance",
                "divine_protection", "cleanse_toxins");
        kit.passive(HOLY_PALADIN, "beacon_caster", "holy_basics");
        kit.passive(PROTECTION_PALADIN, "protection_paladin_basics");
        kit.passive(RETRIBUTION, "retribution_basics");
        kit.bar(HOLY_PALADIN, "holy_shock", "flash_of_light", "holy_light", "word_of_glory", "light_of_dawn", "beacon_of_light",
                "cleanse", "judgment", "crusader_strike", "avenging_wrath", "aura_mastery", "lay_on_hands");
        kit.bar(PROTECTION_PALADIN, "avengers_shield", "judgment", "hammer_of_the_righteous", "shield_of_the_righteous", "consecration",
                "word_of_glory", "hammer_of_wrath", "rebuke", "hand_of_reckoning", "ardent_defender", "guardian_of_ancient_kings", "divine_shield");
        kit.bar(RETRIBUTION, "blade_of_justice", "judgment", "crusader_strike", "templars_verdict", "divine_storm", "wake_of_ashes",
                "hammer_of_wrath", "rebuke", "avenging_wrath", "shield_of_vengeance", "divine_shield", "word_of_glory");

        kit.rotation(HOLY_PALADIN, of(
                self("divine_protection").when(selfHealthBelow(0.4)).urgent(),
                heal("lay_on_hands").below(0.15).urgent(),
                dispel("cleanse"),
                heal("holy_shock").below(0.9),
                heal("word_of_glory").below(0.7).when(resourceAtLeast(HOLY_POWER, 3)),
                use("light_of_dawn").when(resourceAtLeast(HOLY_POWER, 3)).on(com.wowcraft.core.bot.BotTarget.SELF),
                heal("flash_of_light").below(0.55),
                heal("holy_light").below(0.8),
                use("judgment"),
                use("crusader_strike"),
                use("holy_shock")));
        kit.rotation(PROTECTION_PALADIN, of(
                self("guardian_of_ancient_kings").when(selfHealthBelow(0.3)).urgent(),
                self("ardent_defender").when(selfHealthBelow(0.45)).urgent(),
                self("shield_of_the_righteous").when(resourceAtLeast(HOLY_POWER, 3).and(Cond.hasAura("shield_of_the_righteous").not())).urgent(),
                self("word_of_glory").when(resourceAtLeast(HOLY_POWER, 3).and(selfHealthBelow(0.5))).urgent(),
                interrupt("rebuke"),
                use("consecration").when(Cond.enemiesAround(8, 1)),
                use("avengers_shield"),
                use("judgment"),
                use("hammer_of_wrath"),
                use("hammer_of_the_righteous")));
        kit.rotation(RETRIBUTION, of(
                self("shield_of_vengeance").when(selfHealthBelow(0.6)).urgent(),
                interrupt("rebuke"),
                self("avenging_wrath"),
                use("divine_storm").when(resourceAtLeast(HOLY_POWER, 3).and(enemiesAround(8, 3))),
                use("templars_verdict").when(resourceAtLeast(HOLY_POWER, 4)),
                use("wake_of_ashes").when(resourceBelow(HOLY_POWER, 3)),
                use("hammer_of_wrath"),
                use("blade_of_justice"),
                use("judgment"),
                use("crusader_strike"),
                use("templars_verdict").when(resourceAtLeast(HOLY_POWER, 3))));

        // ------------------------------------------------------------ talents
        classTree(
                t("cavalier", 0, 0, "Cavalier", "Кавалерист").mod(Modifier.charges("divine_steed", 1)),
                t("fist_of_justice", 0, 1, "Fist of Justice", "Кулак правосудия").mod(Modifier.cooldown("hammer_of_justice", -15)),
                t("holy_aegis", 0, 2, "Holy Aegis", "Священная эгида").mod(Modifier.crit(3)),
                t("divine_toll", 1, 0, "Divine Toll", "Божественный благовест").grant("divine_toll"),
                t("unbreakable_spirit", 1, 1, "Unbreakable Spirit", "Несокрушимый дух").mod(Modifier.cooldownPct("divine_shield", -0.3),
                        Modifier.cooldownPct("divine_protection", -0.3), Modifier.cooldownPct("lay_on_hands", -0.3)),
                t("seal_of_might", 1, 2, "Seal of Might", "Печать могущества").mod(Modifier.stat(Stat.STRENGTH, 0.04), Modifier.stat(Stat.INTELLECT, 0.04)),
                t("sanctified_wrath", 2, 0, "Sanctified Wrath", "Священный гнев").mod(Modifier.duration("avenging_wrath", 5)),
                t("blessed_calling", 2, 1, "Blessed Calling", "Благословенное призвание").mod(Modifier.cooldown("blessing_of_protection", -60),
                        Modifier.cooldown("blessing_of_sacrifice", -30)),
                t("lightforged_blessing", 2, 2, "Lightforged Blessing", "Благословение Озаренных").mod(Modifier.abilityHealing("word_of_glory", 0.3)));
        specTree(HOLY_PALADIN,
                t("crusaders_might", 0, 0, "Crusader's Might", "Мощь крестоносца").aura("crusaders_might"),
                t("divine_insight", 0, 1, "Divine Insight", "Божественное прозрение").mod(Modifier.charges("holy_shock", 1)),
                t("unending_light", 0, 2, "Unending Light", "Нескончаемый свет").mod(Modifier.abilityHealing("light_of_dawn", 0.25)),
                t("glimmer_of_light", 1, 0, "Glimmer of Light", "Частица света").mod(Modifier.abilityHealing("holy_shock", 0.2)),
                t("tower_of_radiance", 1, 1, "Tower of Radiance", "Башня сияния").mod(Modifier.gen("flash_of_light", 1)),
                t("awakening", 1, 2, "Awakening", "Пробуждение").mod(Modifier.cooldownPct("avenging_wrath", -0.25)),
                t("beacon_of_virtue", 2, 0, "Beacon of Virtue", "Частица добродетели").mod(Modifier.healing(0.05)),
                t("relentless_inquisitor", 2, 1, "Relentless Inquisitor", "Неутомимый инквизитор").mod(Modifier.haste(0.06)),
                t("divine_favor", 2, 2, "Divine Favor", "Божественное одобрение").mod(Modifier.abilityHealing("holy_light", 0.25), Modifier.cost("holy_light", -0.25)));
        reg(passive("crusaders_might", "Crusader's Might", "Мощь крестоносца").desc("Crusader Strike reduces the cooldown of Holy Shock by 2 sec.",
                        "«Удар воина Света» сокращает время восстановления «Шока небес» на 2 сек.")
                .trigger(Trigger.on(TriggerType.CAST, cooldown("holy_shock", -2)).filter(ModFilter.ability("crusader_strike"))));
        specTree(PROTECTION_PALADIN,
                t("redoubt", 0, 0, "Redoubt", "Оплот").mod(Modifier.stat(Stat.STRENGTH, 0.06)),
                t("crusaders_judgment", 0, 1, "Crusader's Judgment", "Правосудие крестоносца").mod(Modifier.charges("judgment", 1)),
                t("bulwark_of_order", 0, 2, "Bulwark of Order", "Бастион порядка").mod(Modifier.abilityDamage("avengers_shield", 0.25)),
                t("sanctuary", 1, 0, "Sanctuary", "Святилище").mod(Modifier.taken(-0.04)),
                t("improved_holy_shield", 1, 1, "Holy Shield", "Священный щит").mod(magicTaken(-0.08)),
                t("bastion_of_light", 1, 2, "Bastion of Light", "Бастион Света").mod(Modifier.duration("shield_of_the_righteous", 1.5)),
                t("moment_of_glory", 2, 0, "Moment of Glory", "Миг славы").mod(Modifier.cooldown("avengers_shield", -3), Modifier.abilityDamage("avengers_shield", 0.2)),
                t("eye_of_tyr", 2, 1, "Eye of Tyr", "Око Тира").mod(Modifier.cooldown("guardian_of_ancient_kings", -120)),
                t("final_stand", 2, 2, "Final Stand", "Последний рубеж").mod(Modifier.cooldown("ardent_defender", -30), Modifier.maxHealth(0.05)));
        specTree(RETRIBUTION,
                t("swift_justice", 0, 0, "Swift Justice", "Быстрое правосудие").mod(Modifier.cooldown("judgment", -2)),
                t("improved_blade_of_justice", 0, 1, "Improved Blade of Justice", "Улучшенный клинок правосудия").mod(Modifier.charges("blade_of_justice", 1)),
                t("blade_of_wrath", 0, 2, "Blade of Wrath", "Клинок гнева").mod(Modifier.abilityDamage("blade_of_justice", 0.25)),
                t("final_reckoning_talent", 1, 0, "Final Reckoning", "Последняя расплата").grant("final_reckoning"),
                t("jurisdiction", 1, 1, "Jurisdiction", "Юрисдикция").mod(Modifier.range("templars_verdict", 8), Modifier.range("blade_of_justice", 8)),
                t("tempest_of_the_lightbringer", 1, 2, "Tempest of the Lightbringer", "Буря Светоносного").mod(Modifier.abilityDamage("divine_storm", 0.2)),
                t("crusade", 2, 0, "Crusade", "Крестовый поход").mod(Modifier.duration("avenging_wrath", 7), Modifier.haste(0.03)),
                t("ashes_to_dust", 2, 1, "Ashes to Dust", "Прах к праху").mod(Modifier.abilityDamage("wake_of_ashes", 0.4)),
                t("final_verdict", 2, 2, "Final Verdict", "Окончательный приговор").mod(Modifier.abilityDamage("templars_verdict", 0.15)));
        hero(t("hero_templar", 0, 0, "Templar", "Храмовник").mod(Modifier.tagDamage("holy_power_spender", 0.1), Modifier.damage(0.04)),
                t("hero_herald_of_the_sun", 0, 1, "Herald of the Sun", "Вестник солнца").mod(Modifier.healing(0.08), Modifier.crit(3)));
    }

    /** 40% of heals on others are copied to the paladin's Beacon target. */
    private static Effect beaconTransfer() {
        return custom("Copies healing to the Beacon target", "Копирует исцеление на цель с Частицей Света", ctx -> {
            for (UnitState u : ctx.engine.units()) {
                AuraInstance b = u.auras().get("beacon_of_light", ctx.caster);
                if (b != null && u.isAlive() && u != ctx.target) {
                    ctx.engine.rawHeal(ctx.caster, u, ctx.triggerAmount * 0.4);
                    return;
                }
            }
        });
    }
}
