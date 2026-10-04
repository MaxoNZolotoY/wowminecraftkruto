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
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.util.Vec3;

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.COMBO_POINTS;
import static com.wowcraft.core.resource.ResourceType.ENERGY;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.ROGUE;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class RogueContent extends ClassContent {
    public RogueContent() {
        super(ROGUE);
    }

    static final Cond STEALTH_LIKE = Cond.custom(u -> u.auras().isStealthed() || u.auras().has("shadow_dance") || u.auras().has("subterfuge"),
            "requires stealth or Shadow Dance", "требуется незаметность или «Танец теней»");
    static final Cond OUT_OF_COMBAT = Cond.custom(u -> !u.inCombat(), "not in combat", "вне боя");
    static final Cond TARGET_NOT_IN_COMBAT = Cond.of(c -> c.target != null && !c.target.inCombat(), "target not in combat", "цель вне боя");
    private static final String[] ROLL_BUFFS = {"rtb_broadside", "rtb_ruthless_precision", "rtb_grand_melee", "rtb_skull_and_crossbones",
            "rtb_buried_treasure", "rtb_true_bearing"};

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(buff("stealth", "Stealth", "Незаметность").stealth().mod(Modifier.speed(-0.1)).desc("Stealthed.", "Вы незаметны.").vfx("stealth"));
        reg(buff("vanish", "Vanish", "Исчезновение").duration(3).stealth().mod(Modifier.speed(0.3)).hidden());
        reg(buff("subterfuge", "Subterfuge", "Увертка").duration(3).hidden());
        cc("cheap_shot", "Cheap Shot", "Подлый трюк", CcType.STUN, 4, DispelType.NONE);
        cc("kidney_shot", "Kidney Shot", "Удар по почкам", CcType.STUN, 1, DispelType.NONE);
        reg(debuff("sap", "Sap", "Ошеломление").duration(60).cc(CcType.INCAPACITATE).breakOnAnyDamage().noPandemic());
        reg(debuff("blind", "Blind", "Ослепление").duration(60).cc(CcType.DISORIENT).breakOnDamage(0.05).dispel(DispelType.POISON).noPandemic());
        reg(debuff("gouge", "Gouge", "Парализующий удар").duration(4).cc(CcType.INCAPACITATE).breakOnAnyDamage().noPandemic());
        reg(buff("cloak_of_shadows", "Cloak of Shadows", "Плащ теней").duration(5).mod(magicImmune()).vfx("cloak"));
        reg(buff("evasion", "Evasion", "Ускользание").duration(10).mod(physicalTaken(-0.5)));
        reg(buff("feint", "Feint", "Ложный выпад").duration(6).mod(Modifier.of(ModType.AVOIDANCE, 40)));
        reg(buff("crimson_vial", "Crimson Vial", "Алый фиал").duration(4).periodic(1, healPct(0.05)).unhastedTicks());
        reg(buff("sprint", "Sprint", "Спринт").duration(8).mod(Modifier.speed(0.7)));
        reg(buff("slice_and_dice", "Slice and Dice", "Мясорубка").mod(Modifier.haste(0.12)).desc("Attack speed increased.", "Скорость атаки увеличена."));
        reg(debuff("shiv", "Shiv", "Отравляющий укол").duration(8).mod(Modifier.taken(0.1)).tag("poison"));
        reg(passive("instant_poison", "Instant Poison", "Быстродействующий яд").desc("Your attacks have a 20% chance to deal Nature damage.",
                        "Ваши атаки с вероятностью 20% наносят дополнительный урон от сил природы.")
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, damage(School.NATURE, ap(0.18))).filter(ModFilter.notTag("poison")).chance(0.2).icd(0.2)));
        // Assassination
        reg(debuff("garrote", "Garrote", "Гаррота").duration(18).tag("bleed").dispel(DispelType.BLEED).periodic(2, damage(ap(0.25))));
        reg(debuff("garrote_silence", "Garrote - Silence", "Гаррота — немота").duration(3).cc(CcType.SILENCE));
        reg(debuff("rupture", "Rupture", "Рваная рана").tag("bleed").dispel(DispelType.BLEED).periodic(2, damage(ap(0.32))));
        reg(debuff("deadly_poison", "Deadly Poison", "Смертельный яд").duration(12).school(School.NATURE).tag("poison", "dot")
                .dispel(DispelType.POISON).periodic(2, damage(School.NATURE, ap(0.12))));
        reg(buff("envenom", "Envenom", "Отравление").duration(4).mod(Modifier.tagDamage("poison", 0.25)));
        reg(debuff("deathmark", "Deathmark", "Метка смерти").duration(16).mod(Modifier.taken(0.2)).tag("bleed")
                .periodic(2, damage(ap(0.4))).vfx("deathmark"));
        reg(debuff("crimson_tempest", "Crimson Tempest", "Багровая буря").tag("bleed").periodic(2, damage(ap(0.18))));
        reg(passive("assassination_basics", "Deadly Poison", "Смертельный яд").mod(Modifier.tagDamage("poison", 0.1))
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, aura("deadly_poison")).filter(ModFilter.notTag("poison")).chance(0.35).icd(0.3)));
        // Outlaw
        reg(buff("blade_flurry", "Blade Flurry", "Шквал клинков").duration(10)
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, cleave(0.5, 8, 4)).filter(ModFilter.notTag("aoe")).icd(0.1)));
        reg(buff("adrenaline_rush", "Adrenaline Rush", "Выброс адреналина").duration(20)
                .mod(Modifier.regen(ENERGY.name(), 0.6), Modifier.haste(0.2)).vfx("adrenaline"));
        reg(buff("opportunity", "Opportunity", "Возможность").duration(10)
                .mod(Modifier.cost("pistol_shot", -1.0), Modifier.abilityDamage("pistol_shot", 0.5))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("pistol_shot")).consumeStack()));
        reg(buff("rtb_broadside", "Broadside", "Бортовой залп").duration(30).mod(Modifier.of(ModType.RESOURCE_GEN_FLAT, ModFilter.tag("builder"), 1)));
        reg(buff("rtb_ruthless_precision", "Ruthless Precision", "Безжалостная точность").duration(30).mod(Modifier.crit(20)));
        reg(buff("rtb_grand_melee", "Grand Melee", "Великая битва").duration(30).mod(Modifier.leech(10), Modifier.haste(0.1)));
        reg(buff("rtb_skull_and_crossbones", "Skull and Crossbones", "Череп и кости").duration(30).mod(Modifier.abilityDamage("sinister_strike", 0.3)));
        reg(buff("rtb_buried_treasure", "Buried Treasure", "Зарытое сокровище").duration(30).mod(Modifier.regen(ENERGY.name(), 0.25)));
        reg(buff("rtb_true_bearing", "True Bearing", "Верный курс").duration(30).mod(Modifier.of(ModType.COOLDOWN_RATE, 0.3)));
        reg(debuff("between_the_eyes", "Between the Eyes", "Промеж глаз").duration(2).cc(CcType.STUN).noPandemic());
        reg(passive("outlaw_basics", "Combat Potency", "Боевой потенциал").mod(Modifier.damage(-0.05))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("opportunity")).filter(ModFilter.ability("sinister_strike")).chance(0.35))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, energize(ENERGY, 8)).chance(0.3)));
        // Subtlety
        reg(buff("shadow_dance", "Shadow Dance", "Танец теней").duration(8).mod(Modifier.damage(0.15)).vfx("shadow_dance"));
        reg(buff("symbols_of_death", "Symbols of Death", "Символы смерти").duration(10).mod(Modifier.damage(0.15)).vfx("symbols"));
        reg(buff("shadow_blades", "Shadow Blades", "Клинки Тьмы").duration(16).mod(Modifier.of(ModType.RESOURCE_GEN_FLAT, ModFilter.tag("builder"), 1),
                Modifier.damage(0.1)).vfx("shadow_blades"));
        reg(passive("subtlety_basics", "Shadow Techniques", "Техники Тьмы").mod(Modifier.schoolDamage(School.SHADOW, 0.1))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, energize(COMBO_POINTS, 1)).chance(0.25)));

        // ------------------------------------------------------------ class abilities
        reg(ab("stealth", "Stealth", "Незаметность").target(TargetType.SELF).cooldown(2).offGcd().keepStealth().requires(OUT_OF_COMBAT)
                .effect(selfAura("stealth")).tag("utility"));
        reg(ab("vanish", "Vanish", "Исчезновение").target(TargetType.SELF).cooldown(120).offGcd().keepStealth().usableWhileCasting()
                .effect(dropThreat(), selfAura("vanish"), selfAura("subterfuge")).tag("defensive", "stealth"));
        reg(ab("kick", "Kick", "Пинок").cooldown(15).offGcd().effect(interrupt(5)).tag("interrupt"));
        reg(ab("cheap_shot", "Cheap Shot", "Подлый трюк").cost(ENERGY, 40).gen(COMBO_POINTS, 1).requires(STEALTH_LIKE)
                .effect(aura("cheap_shot")).tag("cc", "builder"));
        reg(ab("sap", "Sap", "Ошеломление").range(10).cost(ENERGY, 35).requires(STEALTH_LIKE).requires(TARGET_NOT_IN_COMBAT).keepStealth()
                .effect(aura("sap")).tag("cc", "utility"));
        reg(ab("kidney_shot", "Kidney Shot", "Удар по почкам").cost(ENERGY, 25).costRange(COMBO_POINTS, 1, 5).cooldown(20)
                .effect(auraPerCombo("kidney_shot", 0, 1, false)).tag("cc", "finisher"));
        reg(ab("blind", "Blind", "Ослепление").range(15).cost(ENERGY, 15).cooldown(120).effect(aura("blind")).tag("cc"));
        reg(ab("cloak_of_shadows", "Cloak of Shadows", "Плащ теней").target(TargetType.SELF).cooldown(120).offGcd().usableWhileCc()
                .effect(dispel(10, DispelType.MAGIC), selfAura("cloak_of_shadows")).tag("defensive"));
        reg(ab("evasion", "Evasion", "Ускользание").target(TargetType.SELF).cooldown(120).offGcd().effect(selfAura("evasion")).tag("defensive"));
        reg(ab("feint", "Feint", "Ложный выпад").target(TargetType.SELF).cost(ENERGY, 35).cooldown(15).offGcd().keepStealth()
                .effect(selfAura("feint")).tag("defensive"));
        reg(ab("crimson_vial", "Crimson Vial", "Алый фиал").target(TargetType.SELF).cost(ENERGY, 20).cooldown(30).gcd(1.0).keepStealth()
                .effect(selfAura("crimson_vial")).tag("defensive", "heal"));
        reg(ab("sprint", "Sprint", "Спринт").target(TargetType.SELF).cooldown(120).offGcd().keepStealth().effect(selfAura("sprint")).tag("movement"));
        reg(ab("shiv", "Shiv", "Отравляющий укол").school(School.NATURE).cost(ENERGY, 20).cooldown(25).gcd(1.0).gen(COMBO_POINTS, 1)
                .effect(damage(ap(0.3)), aura("shiv"), dispel(1, DispelType.ENRAGE)).tag("purge", "builder"));
        reg(ab("slice_and_dice", "Slice and Dice", "Мясорубка").target(TargetType.SELF).cost(ENERGY, 25).costRange(COMBO_POINTS, 1, 5)
                .gcd(1.0).effect(auraPerCombo("slice_and_dice", 6, 6, true)).tag("finisher"));
        reg(ab("shadowstep", "Shadowstep", "Шаг сквозь тень").range(25).cooldown(30).charges(1).offGcd().keepStealth()
                .effect(custom("Teleports behind the target", "Переносит вас за спину цели", ctx -> {
                    if (ctx.target == null) return;
                    Vec3 behind = ctx.target.position().add(Vec3.fromYaw(ctx.target.yaw()).mul(-1.5));
                    ctx.engine.moveTo(ctx.caster, behind, true);
                    if (ctx.caster.body != null) ctx.caster.body.lookAt(ctx.target.position());
                })).tag("movement", "gap_closer"));
        reg(ab("gouge", "Gouge", "Парализующий удар").cost(ENERGY, 25).cooldown(15).gcd(1.0).gen(COMBO_POINTS, 1)
                .effect(aura("gouge")).tag("cc"));

        // ------------------------------------------------------------ Assassination
        reg(ab("mutilate", "Mutilate", "Расправа").spec(ASSASSINATION).cost(ENERGY, 50).gen(COMBO_POINTS, 2).gcd(1.0)
                .effect(damage(ap(1.5))).tag("builder", "weapon").vfx("dual_stab"));
        reg(ab("garrote", "Garrote", "Гаррота").spec(ASSASSINATION).cost(ENERGY, 45).cooldown(6).gen(COMBO_POINTS, 1).gcd(1.0)
                .effect(aura("garrote"), when(STEALTH_LIKE, aura("garrote_silence"))).tag("builder", "bleed"));
        reg(ab("rupture", "Rupture", "Рваная рана").spec(ASSASSINATION, SUBTLETY).cost(ENERGY, 25).costRange(COMBO_POINTS, 1, 5).gcd(1.0)
                .effect(auraPerCombo("rupture", 4, 4, false)).tag("finisher", "bleed"));
        reg(ab("envenom", "Envenom", "Отравление").spec(ASSASSINATION).school(School.NATURE).cost(ENERGY, 35).costRange(COMBO_POINTS, 1, 5)
                .gcd(1.0).effect(damage(ap(0.75).perCombo()), selfAura("envenom")).tag("finisher", "poison").vfx("poison_burst"));
        reg(ab("fan_of_knives", "Fan of Knives", "Веер клинков").spec(ASSASSINATION).target(TargetType.NONE).cost(ENERGY, 35)
                .gen(COMBO_POINTS, 1).gcd(1.0).effect(aroundSelf(10, damage(ap(0.4)), aura("deadly_poison"))).tag("aoe", "builder").vfx("fan_of_knives"));
        reg(ab("deathmark", "Deathmark", "Метка смерти").spec(ASSASSINATION).cooldown(120).gcd(1.0).effect(aura("deathmark")).tag("cooldown", "bleed"));
        reg(ab("crimson_tempest", "Crimson Tempest", "Багровая буря").spec(ASSASSINATION).target(TargetType.NONE).cost(ENERGY, 30)
                .costRange(COMBO_POINTS, 1, 5).gcd(1.0).effect(aroundSelf(10, damage(ap(0.2)), auraPerCombo("crimson_tempest", 2, 2, false)))
                .tag("finisher", "aoe", "bleed"));
        reg(ab("kingsbane", "Kingsbane", "Погибель королей").spec(ASSASSINATION).school(School.NATURE).cost(ENERGY, 35).cooldown(60)
                .gen(COMBO_POINTS, 1).gcd(1.0).effect(damage(ap(1.4)), aura("kingsbane")).tag("cooldown", "poison"));
        reg(debuff("kingsbane", "Kingsbane", "Погибель королей").duration(14).school(School.NATURE).tag("poison", "dot")
                .periodic(2, damage(School.NATURE, ap(0.4))));

        // ------------------------------------------------------------ Outlaw
        reg(ab("sinister_strike", "Sinister Strike", "Коварный удар").spec(OUTLAW).cost(ENERGY, 45).gen(COMBO_POINTS, 1).gcd(1.0)
                .effect(damage(ap(1.1)), chance(0.35, all(damage(ap(0.5)), energize(COMBO_POINTS, 1)))).tag("builder", "weapon").vfx("slash"));
        reg(ab("pistol_shot", "Pistol Shot", "Пистолетный выстрел").spec(OUTLAW).range(20).cost(ENERGY, 40).gen(COMBO_POINTS, 1).gcd(1.0)
                .effect(projectile(60, damage(ap(1.0)))).tag("builder").vfx("pistol"));
        reg(ab("dispatch", "Dispatch", "Перехват").spec(OUTLAW).cost(ENERGY, 35).costRange(COMBO_POINTS, 1, 5).gcd(1.0)
                .effect(damage(ap(0.65).perCombo())).tag("finisher", "weapon").vfx("slash_red"));
        reg(ab("between_the_eyes", "Between the Eyes", "Промеж глаз").spec(OUTLAW).range(20).cost(ENERGY, 25)
                .costRange(COMBO_POINTS, 1, 5).cooldown(45).gcd(1.0)
                .effect(projectile(60, damage(ap(0.55).perCombo()), when(Cond.targetIsPlayer().not(), aura("between_the_eyes"))))
                .tag("finisher").vfx("pistol"));
        reg(ab("roll_the_bones", "Roll the Bones", "Бросок костей").spec(OUTLAW).target(TargetType.SELF).cost(ENERGY, 25).cooldown(45).gcd(1.0)
                .effect(custom("Grants 1-2 random combat enhancements for 30 sec", "Дает 1-2 случайных усиления на 30 сек.", ctx -> {
                    for (String id : ROLL_BUFFS) ctx.engine.removeAura(ctx.caster, id, null);
                    int n = ctx.engine.rng().chance(0.3) ? 2 : 1;
                    java.util.List<String> pool = new java.util.ArrayList<>(java.util.List.of(ROLL_BUFFS));
                    for (int i = 0; i < n; i++) {
                        String pick = pool.remove(ctx.engine.rng().nextInt(pool.size()));
                        ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, pick, 1, -1);
                    }
                })).tag("buff").vfx("dice"));
        reg(ab("blade_flurry", "Blade Flurry", "Шквал клинков").spec(OUTLAW).target(TargetType.NONE).cost(ENERGY, 15).cooldown(30).gcd(1.0)
                .effect(aroundSelf(8, damage(ap(0.35))), selfAura("blade_flurry")).tag("aoe"));
        reg(ab("adrenaline_rush", "Adrenaline Rush", "Выброс адреналина").spec(OUTLAW).target(TargetType.SELF).cooldown(180).offGcd()
                .effect(selfAura("adrenaline_rush"), energize(COMBO_POINTS, 5)).tag("cooldown"));
        reg(ab("grappling_hook", "Grappling Hook", "Абордажный крюк").spec(OUTLAW).target(TargetType.GROUND).range(40).cooldown(45).offGcd()
                .effect(leap()).tag("movement"));
        reg(ab("killing_spree", "Killing Spree", "Череда убийств").spec(OUTLAW).range(10).cost(ENERGY, 25).costRange(COMBO_POINTS, 1, 5)
                .cooldown(90).channel(2, 0.4).effect(charge()).tick(damage(ap(0.45))).tag("finisher", "cooldown").vfx("killing_spree"));
        reg(ab("ambush", "Ambush", "Внезапный удар").cost(ENERGY, 50).gen(COMBO_POINTS, 2).gcd(1.0).requires(STEALTH_LIKE)
                .effect(damage(ap(1.8))).tag("builder", "weapon"));

        // ------------------------------------------------------------ Subtlety
        reg(ab("backstab", "Backstab", "Удар в спину").spec(SUBTLETY).cost(ENERGY, 35).gen(COMBO_POINTS, 1).gcd(1.0)
                .effect(damage(ap(1.2)), when(behindTarget(), damage(ap(0.4)))).tag("builder", "weapon").vfx("stab"));
        reg(ab("shadowstrike", "Shadowstrike", "Удар Тьмы").spec(SUBTLETY).school(School.SHADOW).range(15).cost(ENERGY, 40)
                .gen(COMBO_POINTS, 2).gcd(1.0).requires(STEALTH_LIKE)
                .effect(custom("Teleports behind the target", "Переносит вас за спину цели", ctx -> {
                    if (ctx.target == null) return;
                    ctx.engine.moveTo(ctx.caster, ctx.target.position().add(Vec3.fromYaw(ctx.target.yaw()).mul(-1.4)), true);
                }), damage(ap(2.0))).tag("builder").vfx("shadowstrike"));
        reg(ab("eviscerate", "Eviscerate", "Потрошение").spec(SUBTLETY).school(School.SHADOW).cost(ENERGY, 35).costRange(COMBO_POINTS, 1, 5)
                .gcd(1.0).effect(damage(ap(0.8).perCombo())).tag("finisher").vfx("shadow_slash"));
        reg(ab("shadow_dance", "Shadow Dance", "Танец теней").spec(SUBTLETY).target(TargetType.SELF).cooldown(60).charges(2).offGcd().keepStealth()
                .effect(selfAura("shadow_dance")).tag("cooldown"));
        reg(ab("symbols_of_death", "Symbols of Death", "Символы смерти").spec(SUBTLETY).target(TargetType.SELF).cooldown(30).offGcd()
                .keepStealth().effect(selfAura("symbols_of_death"), energize(ENERGY, 40)).tag("cooldown"));
        reg(ab("black_powder", "Black Powder", "Черный порох").spec(SUBTLETY).school(School.SHADOW).target(TargetType.NONE).cost(ENERGY, 35)
                .costRange(COMBO_POINTS, 1, 5).gcd(1.0).effect(aroundSelf(10, damage(ap(0.3).perCombo()))).tag("finisher", "aoe"));
        reg(ab("shuriken_storm", "Shuriken Storm", "Бурный вихрь сюрикенов").spec(SUBTLETY).target(TargetType.NONE).cost(ENERGY, 35).gcd(1.0)
                .effect(aroundSelf(10, damage(ap(0.38))), custom("Generates 1 combo point per enemy hit", "Создает 1 прием серии за каждого пораженного противника", ctx ->
                        ctx.engine.energize(ctx.caster, COMBO_POINTS, Math.min(5, Math.max(1, ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 10).size())))))
                .tag("aoe", "builder").vfx("shuriken_storm"));
        reg(ab("shuriken_toss", "Shuriken Toss", "Бросок сюрикена").spec(SUBTLETY).range(30).cost(ENERGY, 40).gen(COMBO_POINTS, 1).gcd(1.0)
                .effect(projectile(40, damage(ap(0.6)))).tag("builder"));
        reg(ab("shadow_blades", "Shadow Blades", "Клинки Тьмы").spec(SUBTLETY).target(TargetType.SELF).cooldown(90).offGcd()
                .effect(selfAura("shadow_blades")).tag("cooldown"));
        reg(ab("secret_technique", "Secret Technique", "Секретная техника").spec(SUBTLETY).school(School.SHADOW).target(TargetType.NONE)
                .cost(ENERGY, 30).costRange(COMBO_POINTS, 1, 5).cooldown(60).gcd(1.0)
                .effect(aroundSelf(8, damage(ap(0.6).perCombo()))).tag("finisher", "aoe", "cooldown"));

        // ------------------------------------------------------------ kit
        kit.common("stealth", "vanish", "kick", "cheap_shot", "sap", "kidney_shot", "blind", "cloak_of_shadows", "evasion", "feint",
                "crimson_vial", "sprint", "shiv", "slice_and_dice", "ambush");
        kit.classPassives.add("instant_poison");
        kit.spec(ASSASSINATION, "mutilate", "garrote", "rupture", "envenom", "fan_of_knives", "deathmark", "shadowstep");
        kit.spec(OUTLAW, "sinister_strike", "pistol_shot", "dispatch", "between_the_eyes", "roll_the_bones", "blade_flurry",
                "adrenaline_rush", "grappling_hook", "gouge");
        kit.spec(SUBTLETY, "backstab", "shadowstrike", "eviscerate", "rupture", "shadow_dance", "symbols_of_death", "black_powder",
                "shuriken_storm", "shuriken_toss", "shadow_blades", "shadowstep");
        kit.passive(ASSASSINATION, "assassination_basics");
        kit.passive(OUTLAW, "outlaw_basics");
        kit.passive(SUBTLETY, "subtlety_basics");
        kit.bar(ASSASSINATION, "mutilate", "garrote", "rupture", "envenom", "fan_of_knives", "slice_and_dice", "deathmark", "kick",
                "stealth", "vanish", "kidney_shot", "feint");
        kit.bar(OUTLAW, "sinister_strike", "pistol_shot", "dispatch", "between_the_eyes", "roll_the_bones", "blade_flurry",
                "adrenaline_rush", "kick", "stealth", "vanish", "kidney_shot", "feint");
        kit.bar(SUBTLETY, "backstab", "shadowstrike", "eviscerate", "rupture", "shadow_dance", "symbols_of_death", "shuriken_storm",
                "kick", "stealth", "vanish", "black_powder", "shadow_blades");

        Cond finisherReady = resourceAtLeast(COMBO_POINTS, 5);
        kit.rotation(ASSASSINATION, of(
                self("crimson_vial").when(selfHealthBelow(0.5)).urgent(),
                self("feint").when(selfHealthBelow(0.4)).urgent(),
                interrupt("kick"),
                use("deathmark"),
                use("garrote").when(Cond.targetHasAura("garrote").not()),
                use("rupture").when(finisherReady.and(Cond.targetHasAura("rupture").not())),
                self("slice_and_dice").when(Cond.hasAura("slice_and_dice").not().and(resourceAtLeast(COMBO_POINTS, 2))),
                use("envenom").when(finisherReady),
                use("fan_of_knives").when(enemiesAround(10, 3)),
                use("mutilate")));
        kit.rotation(OUTLAW, of(
                self("crimson_vial").when(selfHealthBelow(0.5)).urgent(),
                self("feint").when(selfHealthBelow(0.4)).urgent(),
                interrupt("kick"),
                self("adrenaline_rush"),
                use("blade_flurry").when(enemiesAround(8, 2).and(Cond.hasAura("blade_flurry").not())),
                self("roll_the_bones").when(Cond.custom(u -> !u.auras().any(a -> a.def.id.startsWith("rtb_")), "no Roll the Bones buff", "нет бонуса «Броска костей»")),
                use("between_the_eyes").when(finisherReady),
                use("dispatch").when(finisherReady),
                use("pistol_shot").when(Cond.hasAura("opportunity")),
                use("sinister_strike")));
        kit.rotation(SUBTLETY, of(
                self("crimson_vial").when(selfHealthBelow(0.5)).urgent(),
                self("feint").when(selfHealthBelow(0.4)).urgent(),
                interrupt("kick"),
                self("symbols_of_death"),
                self("shadow_blades"),
                self("shadow_dance").when(Cond.hasAura("shadow_dance").not().and(resourceBelow(COMBO_POINTS, 3))),
                use("rupture").when(finisherReady.and(Cond.targetHasAura("rupture").not())),
                use("black_powder").when(finisherReady.and(enemiesAround(10, 3))),
                use("eviscerate").when(finisherReady),
                use("shadowstrike"),
                use("shuriken_storm").when(enemiesAround(10, 3)),
                use("backstab")));

        // ------------------------------------------------------------ talents
        classTree(
                t("thiefs_versatility", 0, 0, "Thief's Versatility", "Воровская универсальность").mod(Modifier.statFlat(Stat.VERSATILITY, 120)),
                t("improved_sprint", 0, 1, "Improved Sprint", "Улучшенный спринт").mod(Modifier.cooldown("sprint", -60)),
                t("shadowstep_talent", 0, 2, "Shadowstep", "Шаг сквозь тень").grant("shadowstep"),
                t("elusiveness", 1, 0, "Elusiveness", "Неуловимость").mod(Modifier.taken(-0.04)),
                t("cheat_death", 1, 1, "Cheat Death", "Обман смерти").aura("cheat_death"),
                t("deadened_nerves", 1, 2, "Deadened Nerves", "Онемевшие нервы").mod(physicalTaken(-0.05)),
                t("vigor", 2, 0, "Vigor", "Бодрость").mod(Modifier.ref(ModType.RESOURCE_MAX, ENERGY.name(), 50), Modifier.regen(ENERGY.name(), 0.1)),
                t("deeper_stratagem", 2, 1, "Deeper Stratagem", "Глубокий замысел").mod(Modifier.tagDamage("finisher", 0.1)),
                t("thistle_tea", 2, 2, "Thistle Tea", "Чай из чертополоха").mod(Modifier.cooldown("vanish", -30), Modifier.cooldown("cloak_of_shadows", -30)));
        reg(passive("cheat_death", "Cheat Death", "Обман смерти").desc("Fatal damage instead leaves you at 10% health and reduces damage taken by 85% for 3 sec (6 min cooldown).",
                        "Смертельный урон вместо этого оставляет вам 10% здоровья и снижает урон на 85% на 3 сек. (раз в 6 мин.)")
                .trigger(Trigger.on(TriggerType.LETHAL_DAMAGE, custom("Prevents death", "Предотвращает смерть", ctx -> {
                    ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, "cheating_death", 1, -1);
                    ctx.engine.rawHeal(ctx.caster, ctx.caster, Math.max(0, ctx.caster.maxHealth() * 0.1 - ctx.caster.health()));
                })).icd(360).self()));
        reg(buff("cheating_death", "Cheating Death", "Обман смерти").duration(3).mod(Modifier.immuneDamage()));
        specTree(ASSASSINATION,
                t("improved_garrote", 0, 0, "Improved Garrote", "Улучшенная гаррота").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("garrote"), 0.3)),
                t("venomous_wounds", 0, 1, "Venomous Wounds", "Ядовитые раны").mod(Modifier.regen(ENERGY.name(), 0.15)),
                t("crimson_tempest_talent", 0, 2, "Crimson Tempest", "Багровая буря").grant("crimson_tempest"),
                t("master_assassin", 1, 0, "Master Assassin", "Мастер-убийца").mod(Modifier.crit(5)),
                t("kingsbane_talent", 1, 1, "Kingsbane", "Погибель королей").grant("kingsbane"),
                t("lethal_poisons", 1, 2, "Lethal Poisons", "Смертоносные яды").mod(Modifier.tagDamage("poison", 0.15)),
                t("scent_of_blood", 2, 0, "Scent of Blood", "Запах крови").mod(Modifier.tagDamage("bleed", 0.15)),
                t("doomblade", 2, 1, "Doomblade", "Клинок рока").mod(Modifier.abilityDamage("mutilate", 0.25)),
                t("arterial_precision", 2, 2, "Arterial Precision", "Артериальная точность").mod(Modifier.duration("deathmark", 4), Modifier.cooldown("deathmark", -30)));
        specTree(OUTLAW,
                t("improved_main_gauche", 0, 0, "Improved Main Gauche", "Улучшенный мен-гош").mod(Modifier.damage(0.04)),
                t("quick_draw", 0, 1, "Quick Draw", "Быстрое выхватывание").mod(Modifier.abilityDamage("pistol_shot", 0.4)),
                t("killing_spree_talent", 0, 2, "Killing Spree", "Череда убийств").grant("killing_spree"),
                t("loaded_dice", 1, 0, "Loaded Dice", "Шулерские кости").mod(Modifier.cooldown("roll_the_bones", -15)),
                t("ruthlessness", 1, 1, "Ruthlessness", "Безжалостность").mod(Modifier.abilityDamage("dispatch", 0.15)),
                t("ace_up_your_sleeve", 1, 2, "Ace Up Your Sleeve", "Туз в рукаве").mod(Modifier.cooldown("between_the_eyes", -15)),
                t("dancing_steel", 2, 0, "Dancing Steel", "Танцующая сталь").mod(Modifier.duration("blade_flurry", 3), Modifier.abilityDamage("blade_flurry", 0.4)),
                t("improved_adrenaline_rush", 2, 1, "Improved Adrenaline Rush", "Улучшенный выброс адреналина").mod(Modifier.cooldown("adrenaline_rush", -60)),
                t("greenskins_wickers", 2, 2, "Greenskin's Wickers", "Фитили Зеленокожего").mod(Modifier.abilityDamage("between_the_eyes", 0.3)));
        specTree(SUBTLETY,
                t("improved_backstab", 0, 0, "Improved Backstab", "Улучшенный удар в спину").mod(Modifier.abilityDamage("backstab", 0.2)),
                t("premeditation", 0, 1, "Premeditation", "Умысел").mod(Modifier.gen("shadowstrike", 1)),
                t("secret_technique_talent", 0, 2, "Secret Technique", "Секретная техника").grant("secret_technique"),
                t("dark_shadow", 1, 0, "Dark Shadow", "Темная тень").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("shadow_dance"), 0.0), Modifier.damage(0.05)),
                t("deepening_shadows", 1, 1, "Deepening Shadows", "Сгущающиеся тени").mod(Modifier.charges("shadow_dance", 1)),
                t("weaponmaster", 1, 2, "Weaponmaster", "Мастер оружия").mod(Modifier.abilityDamage("shadowstrike", 0.2)),
                t("the_first_dance", 2, 0, "The First Dance", "Первый танец").mod(Modifier.duration("shadow_dance", 2)),
                t("finality", 2, 1, "Finality", "Окончательность").mod(Modifier.abilityDamage("eviscerate", 0.2), Modifier.abilityDamage("black_powder", 0.2)),
                t("flagellation", 2, 2, "Flagellation", "Бичевание").mod(Modifier.cooldown("shadow_blades", -30), Modifier.duration("shadow_blades", 4)));
        hero(t("hero_deathstalker", 0, 0, "Deathstalker", "Ловчий смерти").mod(Modifier.tagDamage("finisher", 0.12)),
                t("hero_trickster", 0, 1, "Trickster", "Ловкач").mod(Modifier.damage(0.05), Modifier.haste(0.03)));
    }

    /** True for units under any of the rogue's stealth-like effects. */
    static boolean stealthLike(UnitState u) {
        for (AuraInstance a : u.auras().all()) if (a.def.stealth || a.def.id.equals("shadow_dance")) return true;
        return false;
    }
}
