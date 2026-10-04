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
import static com.wowcraft.core.resource.ResourceType.FOCUS;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.HUNTER;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class HunterContent extends ClassContent {
    public HunterContent() {
        super(HUNTER);
    }

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(debuff("hunters_mark", "Hunter's Mark", "Метка охотника").duration(3600).persistent().perCaster(false).mod(Modifier.taken(0.05))
                .desc("Damage taken increased by 5%.", "Получаемый урон увеличен на 5%."));
        reg(buff("misdirection", "Misdirection", "Перенаправление").duration(8)
                .desc("Threat you cause is transferred to the target ally.", "Создаваемая вами угроза перенаправляется на союзника.")
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, custom("Transfers threat", "Перенаправляет угрозу", ctx -> {
                    AuraInstance a = ctx.caster.auras().get("misdirection");
                    if (a == null || ctx.target == null || ctx.target.threat() == null) return;
                    UnitState ally = ctx.engine.unit((int) a.getData("ally"));
                    if (ally == null || ally.isDead()) return;
                    ctx.target.threat().add(ally, ctx.triggerAmount * 1.5);
                    ctx.target.threat().scale(ctx.caster, 0.2);
                }))));
        reg(buff("feign_death", "Feign Death", "Притвориться мертвым").duration(6).mod(Modifier.ref(ModType.UNTARGETABLE, "ALL", 1))
                .breakOnAnyDamage().hidden());
        reg(buff("aspect_of_the_turtle", "Aspect of the Turtle", "Дух черепахи").duration(8).mod(Modifier.immuneDamage(), Modifier.damage(-1.0))
                .desc("Immune to damage, cannot attack.", "Невосприимчивость к урону, нельзя атаковать.").vfx("turtle"));
        reg(buff("aspect_of_the_cheetah", "Aspect of the Cheetah", "Дух гепарда").duration(9).mod(Modifier.speed(0.9)).breakOnAnyDamage());
        cc("freezing_trap", "Freezing Trap", "Замораживающая ловушка", CcType.INCAPACITATE, 30, DispelType.MAGIC);
        reg(debuff("tar_trap", "Tar Trap", "Смоляная ловушка").duration(2).mod(Modifier.speed(-0.5)).cc(CcType.SLOW).hidden());
        reg(debuff("concussive_shot", "Concussive Shot", "Контузящий выстрел").duration(6).mod(Modifier.speed(-0.5)).cc(CcType.SLOW));
        // BM
        reg(debuff("barbed_shot", "Barbed Shot", "Разрывающий выстрел").duration(8).tag("bleed").dispel(DispelType.BLEED)
                .periodic(2, all(damage(ap(0.22)), energize(FOCUS, 5))));
        reg(buff("frenzy", "Frenzy", "Бешенство").duration(8).stacks(3).mod(Modifier.of(ModType.PET_DAMAGE, 0.06))
                .desc("Pet damage increased by 6% per stack.", "Урон питомца увеличен на 6% за каждый заряд."));
        reg(buff("bestial_wrath", "Bestial Wrath", "Звериный гнев").duration(15)
                .mod(Modifier.damage(0.15), Modifier.of(ModType.PET_DAMAGE, 0.25)).vfx("bestial_wrath"));
        reg(buff("beast_cleave", "Beast Cleave", "Звериная рубка").duration(4)
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, cleave(0.35, 8, 4)).filter(ModFilter.ability("kill_command")).icd(0.1)));
        reg(passive("beast_mastery_basics", "Animal Companion", "Звериный спутник").mod(Modifier.of(ModType.PET_DAMAGE, 0.1))
                .trigger(Trigger.on(TriggerType.CAST, cooldown("kill_command", -1)).filter(ModFilter.ability("cobra_shot"))));
        // MM
        reg(buff("trueshot", "Trueshot", "Меткий выстрел").duration(15).mod(Modifier.haste(0.3), Modifier.crit(20),
                Modifier.of(ModType.COOLDOWN_RATE, ModFilter.ability("aimed_shot"), 1.5)).vfx("trueshot"));
        reg(buff("precise_shots", "Precise Shots", "Точные выстрелы").duration(15).stacks(2)
                .mod(Modifier.abilityDamage("arcane_shot", 0.75), Modifier.abilityDamage("multi_shot_mm", 0.75))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("arcane_shot")).consumeStack())
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("multi_shot_mm")).consumeStack()));
        reg(buff("trick_shots", "Trick Shots", "Хитрые выстрелы").duration(20)
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, cleave(0.5, 8, 4)).filter(ModFilter.ability("aimed_shot")).icd(0.1))
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, cleave(0.5, 8, 4)).filter(ModFilter.ability("rapid_fire")).icd(0.1)));
        reg(passive("marksmanship_basics", "Lone Wolf", "Одинокий волк").mod(Modifier.damage(0.18))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("precise_shots", 2)).filter(ModFilter.ability("aimed_shot"))));
        // SV
        reg(debuff("serpent_sting", "Serpent Sting", "Укус змеи").duration(12).school(School.NATURE).tag("dot", "poison").dispel(DispelType.POISON)
                .periodic(3, damage(School.NATURE, ap(0.25))));
        reg(debuff("wildfire_bomb", "Wildfire Bomb", "Бомба с дикопламенем").duration(6).school(School.FIRE).tag("dot")
                .periodic(1, damage(School.FIRE, ap(0.15))));
        cc("harpoon", "Harpoon", "Гарпун", CcType.ROOT, 3, DispelType.NONE);
        reg(buff("coordinated_assault", "Coordinated Assault", "Согласованная атака").duration(20)
                .mod(Modifier.damage(0.2), Modifier.of(ModType.PET_DAMAGE, 0.2)).vfx("coordinated_assault"));
        reg(buff("mongoose_fury", "Mongoose Fury", "Ярость мангуста").duration(14).stacks(5).mod(Modifier.abilityDamage("mongoose_bite", 0.15)));
        reg(passive("survival_basics", "Tip of the Spear", "Острие копья").mod(Modifier.damage(-0.05))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("tip_of_the_spear", 3)).filter(ModFilter.ability("kill_command_sv"))));
        reg(buff("tip_of_the_spear", "Tip of the Spear", "Острие копья").duration(10).stacks(3)
                .mod(Modifier.abilityDamage("raptor_strike", 0.15), Modifier.abilityDamage("mongoose_bite", 0.15))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("raptor_strike")).consumeStack()));

        // ------------------------------------------------------------ class abilities
        reg(ab("hunters_mark", "Hunter's Mark", "Метка охотника").school(School.ARCANE).range(50).offGcd().cooldown(1)
                .effect(aura("hunters_mark")).tag("utility").keepStealth());
        reg(ab("misdirection", "Misdirection", "Перенаправление").target(TargetType.FRIENDLY).cooldown(30).offGcd()
                .effect(custom("Your threat is redirected to the target ally for 8 sec", "Ваша угроза перенаправляется на союзника на 8 сек.", ctx -> {
                    AuraInstance a = ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, "misdirection", 1, -1);
                    if (a != null && ctx.target != null) a.setData("ally", ctx.target.id);
                })).tag("utility"));
        reg(ab("feign_death", "Feign Death", "Притвориться мертвым").target(TargetType.SELF).cooldown(30).offGcd().usableWhileCasting()
                .effect(dropThreat(), selfAura("feign_death")).tag("defensive"));
        reg(ab("disengage", "Disengage", "Отрыв").target(TargetType.SELF).cooldown(20).offGcd().effect(disengage(1.6)).tag("movement"));
        reg(ab("aspect_of_the_turtle", "Aspect of the Turtle", "Дух черепахи").target(TargetType.SELF).cooldown(180).offGcd()
                .effect(selfAura("aspect_of_the_turtle")).tag("defensive", "major_defensive"));
        reg(ab("exhilaration", "Exhilaration", "Живость").target(TargetType.SELF).cooldown(120).offGcd()
                .effect(healPct(0.3), onTargets(Selector.pets(), heal(hp(0.3)))).tag("defensive", "heal"));
        reg(ab("counter_shot", "Counter Shot", "Встречный выстрел").range(40).cooldown(24).offGcd().effect(interrupt(3)).tag("interrupt"));
        reg(ab("muzzle", "Muzzle", "Намордник").cooldown(15).offGcd().effect(interrupt(3)).tag("interrupt"));
        reg(ab("freezing_trap", "Freezing Trap", "Замораживающая ловушка").school(School.FROST).range(30).cooldown(30)
                .effect(projectile(25, aura("freezing_trap"))).tag("cc").vfx("trap"));
        reg(ab("tar_trap", "Tar Trap", "Смоляная ловушка").target(TargetType.GROUND).range(40).cooldown(25)
                .effect(area(GroundArea.Def.enemies("tar_trap", 8, 30, 1, aura("tar_trap")).color(0xFF202020))).tag("slow"));
        reg(ab("aspect_of_the_cheetah", "Aspect of the Cheetah", "Дух гепарда").target(TargetType.SELF).cooldown(180).offGcd()
                .effect(selfAura("aspect_of_the_cheetah")).tag("movement"));
        reg(ab("call_pet", "Call Pet", "Призыв питомца").target(TargetType.SELF).cast(1.0).requires(Cond.hasPet().not())
                .effect(summon("hunter_pet", 0, 1)).tag("pet"));
        reg(ab("mend_pet", "Mend Pet", "Лечение питомца").target(TargetType.SELF).cooldown(10).requires(Cond.hasPet())
                .effect(onTargets(Selector.pets(), heal(ap(2.0)))).tag("pet"));
        reg(ab("kill_shot", "Kill Shot", "Убийственный выстрел").range(40).cost(FOCUS, 10).cooldown(10)
                .requires(targetHealthBelow(0.2)).effect(projectile(60, damage(ap(2.2)))).tag("execute").vfx("kill_shot"));
        reg(ab("tranquilizing_shot", "Tranquilizing Shot", "Усмиряющий выстрел").school(School.NATURE).range(40).cooldown(10)
                .effect(dispel(1, DispelType.MAGIC, DispelType.ENRAGE)).tag("purge"));
        reg(ab("concussive_shot", "Concussive Shot", "Контузящий выстрел").range(40).cooldown(5).effect(aura("concussive_shot")).tag("slow"));
        reg(ab("arcane_shot", "Arcane Shot", "Чародейский выстрел").school(School.ARCANE).range(40).cost(FOCUS, 20)
                .effect(projectile(50, damage(ap(0.9)))).vfx("arcane_arrow"));

        // ------------------------------------------------------------ Beast Mastery
        reg(ab("kill_command", "Kill Command", "Команда «Взять!»").spec(BEAST_MASTERY).range(50).cost(FOCUS, 30).cooldown(7.5)
                .charges(2).hastedCooldown().requires(Cond.hasPet()).ignoreLos()
                .effect(petDamage(School.PHYSICAL, ap(2.0))).tag("pet").vfx("kill_command"));
        reg(ab("barbed_shot", "Barbed Shot", "Разрывающий выстрел").spec(BEAST_MASTERY).range(40).cooldown(12).charges(2).hastedCooldown()
                .effect(projectile(50, aura("barbed_shot")), selfAura("frenzy")).tag("bleed").vfx("barbed_shot"));
        reg(ab("cobra_shot", "Cobra Shot", "Выстрел кобры").spec(BEAST_MASTERY).school(School.NATURE).range(40).cost(FOCUS, 35)
                .effect(projectile(50, damage(ap(0.9)))).vfx("cobra_shot"));
        reg(ab("bestial_wrath", "Bestial Wrath", "Звериный гнев").spec(BEAST_MASTERY).target(TargetType.SELF).cooldown(90).offGcd()
                .effect(selfAura("bestial_wrath"), resetCooldown("barbed_shot")).tag("cooldown"));
        reg(ab("multi_shot", "Multi-Shot", "Залп").spec(BEAST_MASTERY).range(40).cost(FOCUS, 40)
                .effect(aoe(8, damage(ap(0.45))), selfAura("beast_cleave")).tag("aoe").vfx("multi_shot"));
        reg(ab("dire_beast", "Dire Beast", "Ужасный зверь").spec(BEAST_MASTERY).range(40).cooldown(20).gen(FOCUS, 20)
                .effect(summon("dire_beast", 8, 1)).tag("pet"));
        reg(ab("call_of_the_wild", "Call of the Wild", "Зов дикой природы").spec(BEAST_MASTERY).target(TargetType.SELF).cooldown(120)
                .effect(summon("dire_beast", 20, 2), resetCooldown("kill_command"), resetCooldown("barbed_shot")).tag("cooldown", "pet"));

        // ------------------------------------------------------------ Marksmanship
        reg(ab("aimed_shot", "Aimed Shot", "Прицельный выстрел").spec(MARKSMANSHIP).range(40).cast(2.5).cost(FOCUS, 35).cooldown(12)
                .charges(2).hastedCooldown().effect(projectile(60, damage(ap(3.5)))).vfx("aimed_shot"));
        reg(ab("rapid_fire", "Rapid Fire", "Беглый огонь").spec(MARKSMANSHIP).range(40).cooldown(20).hastedCooldown().channel(2, 0.28)
                .tick(damage(ap(0.4)), energize(FOCUS, 1)).moving().vfx("rapid_fire"));
        reg(ab("steady_shot", "Steady Shot", "Верный выстрел").spec(MARKSMANSHIP).range(40).cast(1.5).moving().gen(FOCUS, 10)
                .effect(projectile(50, damage(ap(0.55)))).vfx("arrow"));
        reg(ab("trueshot", "Trueshot", "Меткий выстрел").spec(MARKSMANSHIP).target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("trueshot")).tag("cooldown"));
        reg(ab("multi_shot_mm", "Multi-Shot", "Залп").spec(MARKSMANSHIP).range(40).cost(FOCUS, 20)
                .effect(aoe(8, damage(ap(0.45))), when(enemiesAround(40, 3), selfAura("trick_shots"))).tag("aoe").vfx("multi_shot"));
        reg(ab("volley", "Volley", "Град стрел").spec(MARKSMANSHIP).target(TargetType.GROUND).range(40).cooldown(45)
                .effect(area(GroundArea.Def.enemies("volley", 8, 6, 0.5, damage(ap(0.25)))), selfAura("trick_shots")).tag("aoe", "cooldown"));
        reg(ab("explosive_shot", "Explosive Shot", "Взрывной выстрел").school(School.FIRE).range(40).cost(FOCUS, 20).cooldown(30)
                .effect(projectile(40, delayed(3, aoe(8, damage(School.FIRE, ap(1.5)))))).tag("aoe"));

        // ------------------------------------------------------------ Survival
        reg(ab("raptor_strike", "Raptor Strike", "Удар ящера").spec(SURVIVAL).cost(FOCUS, 30).effect(damage(ap(1.1))).tag("weapon").vfx("slash"));
        reg(ab("mongoose_bite", "Mongoose Bite", "Укус мангуста").spec(SURVIVAL).cost(FOCUS, 30)
                .effect(damage(ap(1.4)), selfAura("mongoose_fury")).tag("weapon").vfx("slash"));
        reg(ab("kill_command_sv", "Kill Command", "Команда «Взять!»").spec(SURVIVAL).range(50).cooldown(6).charges(2).hastedCooldown()
                .gen(FOCUS, 15).requires(Cond.hasPet()).ignoreLos().effect(petDamage(School.PHYSICAL, ap(1.0))).tag("pet").vfx("kill_command"));
        reg(ab("wildfire_bomb", "Wildfire Bomb", "Бомба с дикопламенем").spec(SURVIVAL).school(School.FIRE).range(40).cooldown(18)
                .charges(2).hastedCooldown().effect(projectile(25, aoe(6, damage(ap(0.8)), aura("wildfire_bomb")))).tag("aoe").vfx("bomb"));
        reg(ab("serpent_sting", "Serpent Sting", "Укус змеи").spec(SURVIVAL).school(School.NATURE).range(40).cost(FOCUS, 10)
                .effect(projectile(50, damage(ap(0.2)), aura("serpent_sting"))).tag("dot"));
        reg(ab("harpoon", "Harpoon", "Гарпун").spec(SURVIVAL).range(30).minRange(5).cooldown(30).offGcd()
                .effect(charge(), aura("harpoon")).tag("movement", "gap_closer"));
        reg(ab("coordinated_assault", "Coordinated Assault", "Согласованная атака").spec(SURVIVAL).cooldown(120)
                .effect(charge(), damage(ap(1.0)), selfAura("coordinated_assault")).tag("cooldown"));
        reg(ab("butchery", "Butchery", "Разделка туши").spec(SURVIVAL).target(TargetType.NONE).cost(FOCUS, 30).cooldown(15).charges(3)
                .hastedCooldown().effect(aroundSelf(8, damage(ap(0.8)))).tag("aoe").vfx("cleave"));
        reg(ab("flanking_strike", "Flanking Strike", "Фланговый удар").spec(SURVIVAL).cooldown(30).gen(FOCUS, 30)
                .effect(damage(ap(1.6)), petDamage(School.PHYSICAL, ap(1.0))).tag("weapon"));

        // ------------------------------------------------------------ kit
        kit.common("hunters_mark", "misdirection", "feign_death", "disengage", "aspect_of_the_turtle", "exhilaration",
                "freezing_trap", "tar_trap", "aspect_of_the_cheetah", "kill_shot", "tranquilizing_shot", "concussive_shot", "arcane_shot");
        kit.spec(BEAST_MASTERY, "kill_command", "barbed_shot", "cobra_shot", "bestial_wrath", "multi_shot", "dire_beast",
                "counter_shot", "call_pet", "mend_pet");
        kit.spec(MARKSMANSHIP, "aimed_shot", "rapid_fire", "steady_shot", "trueshot", "multi_shot_mm", "counter_shot");
        kit.spec(SURVIVAL, "raptor_strike", "kill_command_sv", "wildfire_bomb", "serpent_sting", "harpoon", "coordinated_assault",
                "butchery", "muzzle", "call_pet", "mend_pet");
        kit.passive(BEAST_MASTERY, "beast_mastery_basics");
        kit.passive(MARKSMANSHIP, "marksmanship_basics");
        kit.passive(SURVIVAL, "survival_basics");
        kit.defaultPet.put(BEAST_MASTERY, "hunter_pet");
        kit.defaultPet.put(SURVIVAL, "hunter_pet");
        kit.bar(BEAST_MASTERY, "kill_command", "barbed_shot", "cobra_shot", "multi_shot", "kill_shot", "bestial_wrath", "dire_beast",
                "counter_shot", "misdirection", "exhilaration", "disengage", "aspect_of_the_turtle");
        kit.bar(MARKSMANSHIP, "aimed_shot", "rapid_fire", "arcane_shot", "steady_shot", "multi_shot_mm", "kill_shot", "trueshot",
                "counter_shot", "misdirection", "exhilaration", "disengage", "aspect_of_the_turtle");
        kit.bar(SURVIVAL, "raptor_strike", "kill_command_sv", "wildfire_bomb", "serpent_sting", "butchery", "kill_shot", "harpoon",
                "muzzle", "coordinated_assault", "exhilaration", "aspect_of_the_turtle", "misdirection");

        kit.rotation(BEAST_MASTERY, of(
                self("exhilaration").when(selfHealthBelow(0.35)).urgent(),
                self("aspect_of_the_turtle").when(selfHealthBelow(0.15)).urgent(),
                self("call_pet").when(Cond.hasPet().not()),
                interrupt("counter_shot"),
                use("hunters_mark").when(Cond.targetHasAnyAura("hunters_mark").not()),
                self("bestial_wrath"),
                use("barbed_shot"),
                use("multi_shot").when(enemiesAround(40, 3).and(Cond.hasAura("beast_cleave").not())),
                use("kill_shot"),
                use("kill_command"),
                use("dire_beast"),
                use("cobra_shot").when(resourceAtLeast(FOCUS, 50))));
        kit.rotation(MARKSMANSHIP, of(
                self("exhilaration").when(selfHealthBelow(0.35)).urgent(),
                self("aspect_of_the_turtle").when(selfHealthBelow(0.15)).urgent(),
                interrupt("counter_shot"),
                use("hunters_mark").when(Cond.targetHasAnyAura("hunters_mark").not()),
                self("trueshot"),
                use("multi_shot_mm").when(enemiesAround(40, 3).and(Cond.hasAura("trick_shots").not())),
                use("kill_shot"),
                use("rapid_fire"),
                use("arcane_shot").when(Cond.hasAura("precise_shots")),
                use("aimed_shot"),
                use("arcane_shot").when(resourceAtLeast(FOCUS, 70)),
                use("steady_shot")));
        kit.rotation(SURVIVAL, of(
                self("exhilaration").when(selfHealthBelow(0.35)).urgent(),
                self("call_pet").when(Cond.hasPet().not()),
                interrupt("muzzle"),
                use("coordinated_assault"),
                use("wildfire_bomb"),
                use("kill_shot"),
                use("butchery").when(enemiesAround(8, 3)),
                use("serpent_sting").when(Cond.targetHasAura("serpent_sting").not()),
                use("kill_command_sv").when(resourceBelow(FOCUS, 70)),
                use("raptor_strike"),
                use("kill_command_sv")));

        // ------------------------------------------------------------ talents
        classTree(
                t("natural_mending", 0, 0, "Natural Mending", "Естественное восстановление").mod(Modifier.cooldown("exhilaration", -30)),
                t("posthaste", 0, 1, "Posthaste", "Без промедления").mod(Modifier.cooldown("disengage", -5), Modifier.speed(0.05)),
                t("lone_survivor", 0, 2, "Lone Survivor", "Одиночка").mod(Modifier.cooldown("counter_shot", -2), Modifier.cooldown("muzzle", -2)),
                t("explosive_shot_talent", 1, 0, "Explosive Shot", "Взрывной выстрел").grant("explosive_shot"),
                t("born_to_be_wild", 1, 1, "Born To Be Wild", "Рожденный свободным").mod(Modifier.cooldownPct("aspect_of_the_turtle", -0.2),
                        Modifier.cooldownPct("aspect_of_the_cheetah", -0.2)),
                t("keen_eyesight", 1, 2, "Keen Eyesight", "Острое зрение").mod(Modifier.crit(3)),
                t("improved_traps", 2, 0, "Improved Traps", "Улучшенные ловушки").mod(Modifier.cooldown("freezing_trap", -5), Modifier.cooldown("tar_trap", -5)),
                t("killer_instinct", 2, 1, "Killer Instinct", "Инстинкт убийцы").mod(Modifier.of(ModType.EXECUTE_DAMAGE, 0.15)),
                t("master_marksman", 2, 2, "Master Marksman", "Мастер-стрелок").mod(Modifier.stat(Stat.AGILITY, 0.04)));
        specTree(BEAST_MASTERY,
                t("killer_command", 0, 0, "Killer Command", "Команда убийцы").mod(Modifier.abilityDamage("kill_command", 0.2)),
                t("training_expert", 0, 1, "Training Expert", "Опытный дрессировщик").mod(Modifier.of(ModType.PET_DAMAGE, 0.1)),
                t("cobra_senses", 0, 2, "Cobra Senses", "Чутье кобры").mod(Modifier.abilityDamage("cobra_shot", 0.25)),
                t("call_of_the_wild_talent", 1, 0, "Call of the Wild", "Зов дикой природы").grant("call_of_the_wild"),
                t("war_orders", 1, 1, "War Orders", "Боевые приказы").mod(Modifier.charges("barbed_shot", 1)),
                t("one_with_the_pack", 1, 2, "One with the Pack", "Единство со стаей").mod(Modifier.cooldown("bestial_wrath", -20)),
                t("killer_cobra", 2, 0, "Killer Cobra", "Убийственная кобра").aura("killer_cobra"),
                t("brutal_companion", 2, 1, "Brutal Companion", "Жестокий спутник").mod(Modifier.duration("frenzy", 2), Modifier.of(ModType.PET_DAMAGE, 0.08)),
                t("savagery", 2, 2, "Savagery", "Свирепость").mod(Modifier.duration("bestial_wrath", 5)));
        reg(passive("killer_cobra", "Killer Cobra", "Убийственная кобра").desc("During Bestial Wrath, Cobra Shot resets Kill Command.",
                        "Во время «Звериного гнева» «Выстрел кобры» сбрасывает «Команду «Взять!»».")
                .trigger(Trigger.on(TriggerType.CAST, when(Cond.hasAura("bestial_wrath"), resetCooldown("kill_command"))).filter(ModFilter.ability("cobra_shot"))));
        specTree(MARKSMANSHIP,
                t("careful_aim", 0, 0, "Careful Aim", "Тщательное прицеливание").mod(Modifier.abilityCrit("aimed_shot", 20)),
                t("streamline", 0, 1, "Streamline", "Оптимизация").mod(Modifier.abilityDamage("rapid_fire", 0.2)),
                t("serpentstalkers_trickery", 0, 2, "Serpentstalker's Trickery", "Уловка змеелова").mod(Modifier.abilityDamage("arcane_shot", 0.2)),
                t("volley_talent", 1, 0, "Volley", "Град стрел").grant("volley"),
                t("lock_and_load", 1, 1, "Lock and Load", "На изготовку").aura("lock_and_load"),
                t("surging_shots", 1, 2, "Surging Shots", "Мощные выстрелы").mod(Modifier.cooldown("rapid_fire", -5)),
                t("calling_the_shots", 2, 0, "Calling the Shots", "Командование выстрелами").mod(Modifier.cooldown("trueshot", -30)),
                t("salvo", 2, 1, "Salvo", "Залп орудий").mod(Modifier.abilityDamage("multi_shot_mm", 0.3)),
                t("unerring_vision", 2, 2, "Unerring Vision", "Безошибочное зрение").mod(Modifier.duration("trueshot", 5), Modifier.crit(2)));
        reg(passive("lock_and_load", "Lock and Load", "На изготовку").desc("Auto Shot has a 8% chance to reset Aimed Shot.",
                        "«Автоматическая стрельба» с вероятностью 8% сбрасывает «Прицельный выстрел».")
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, resetCooldown("aimed_shot")).chance(0.08)));
        specTree(SURVIVAL,
                t("mongoose_bite_talent", 0, 0, "Mongoose Bite", "Укус мангуста").mod(Modifier.replace("raptor_strike", "mongoose_bite")),
                t("guerrilla_tactics", 0, 1, "Guerrilla Tactics", "Партизанская тактика").mod(Modifier.abilityDamage("wildfire_bomb", 0.3)),
                t("terms_of_engagement", 0, 2, "Terms of Engagement", "Условия боя").mod(Modifier.cooldown("harpoon", -10)),
                t("flanking_strike_talent", 1, 0, "Flanking Strike", "Фланговый удар").grant("flanking_strike"),
                t("bloodseeker", 1, 1, "Bloodseeker", "Кровопийца").mod(Modifier.speed(0.05), Modifier.haste(0.03)),
                t("vipers_venom", 1, 2, "Viper's Venom", "Яд гадюки").mod(Modifier.abilityDamage("serpent_sting", 0.4)),
                t("bombardier", 2, 0, "Bombardier", "Бомбардир").mod(Modifier.charges("wildfire_bomb", 1)),
                t("ruthless_marauder", 2, 1, "Ruthless Marauder", "Безжалостный мародер").mod(Modifier.duration("coordinated_assault", 6)),
                t("frenzy_strikes", 2, 2, "Frenzy Strikes", "Неистовые удары").mod(Modifier.abilityDamage("butchery", 0.3)));
        hero(t("hero_pack_leader", 0, 0, "Pack Leader", "Вожак стаи").mod(Modifier.of(ModType.PET_DAMAGE, 0.15)),
                t("hero_sentinel", 0, 1, "Sentinel", "Часовой").mod(Modifier.damage(0.06), Modifier.crit(3)));
    }
}
