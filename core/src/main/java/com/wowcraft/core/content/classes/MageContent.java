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
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.Selector;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;

import static com.wowcraft.core.bot.Rotation.*;
import static com.wowcraft.core.resource.ResourceType.ARCANE_CHARGES;
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.MAGE;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class MageContent extends ClassContent {
    public MageContent() {
        super(MAGE);
    }

    /** Frost: target is frozen (rooted, Winter's Chill) or the mage has Fingers of Frost. */
    static final Cond FROZEN = Cond.of(c -> c.target != null && (c.target.hasCc(CcType.ROOT) || c.target.auras().has("winters_chill")
            || c.caster.auras().has("fingers_of_frost")), "target is frozen", "цель заморожена");

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(buff("ice_block", "Ice Block", "Ледяная глыба").duration(10).mod(Modifier.immuneDamage()).cc(CcType.STUN).noBreakAfter().vfx("ice_block"));
        reg(debuff("hypothermia", "Hypothermia", "Гипотермия").duration(30).perCaster(false));
        reg(debuff("polymorph", "Polymorph", "Превращение").duration(50).cc(CcType.INCAPACITATE).breakOnAnyDamage().dispel(DispelType.MAGIC)
                .noPandemic().vfx("polymorph"));
        reg(debuff("frost_nova", "Frost Nova", "Кольцо льда").duration(6).school(School.FROST).cc(CcType.ROOT).breakOnDamage(0.1).dispel(DispelType.MAGIC));
        reg(buff("arcane_intellect", "Arcane Intellect", "Чародейский интеллект").duration(3600).persistent().mod(Modifier.stat(Stat.INTELLECT, 0.05))
                .desc("Intellect increased by 5%.", "Интеллект увеличен на 5%."));
        reg(buff("time_warp", "Time Warp", "Искажение времени").duration(40).mod(Modifier.haste(0.3)).vfx("lust"));
        reg(buff("mirror_image", "Mirror Image", "Зеркальное изображение").duration(40).mod(Modifier.taken(-0.2)));
        reg(buff("alter_time", "Alter Time", "Перемещение во времени").duration(10)
                .onExpire(custom("Returns your health to its earlier value", "Возвращает здоровье к прежнему значению", ctx -> {
                    double saved = ctx.aura.getData("hp");
                    if (saved > ctx.target.health()) ctx.engine.setHealth(ctx.target, saved);
                })));
        reg(debuff("slow_mage", "Slow", "Замедление").duration(15).mod(Modifier.speed(-0.5)).cc(CcType.SLOW).dispel(DispelType.MAGIC));
        // Arcane
        reg(buff("clearcasting", "Clearcasting", "Ясность мысли").duration(15).stacks(3).mod(Modifier.cost("arcane_missiles", -1.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("arcane_missiles")).consumeStack()));
        reg(buff("arcane_surge", "Arcane Surge", "Чародейский прилив").duration(15).mod(Modifier.schoolDamage(School.ARCANE, 0.35),
                Modifier.regen(MANA.name(), 1.0)).vfx("arcane_surge"));
        reg(debuff("touch_of_the_magi", "Touch of the Magi", "Прикосновение мага").duration(12).school(School.ARCANE)
                .desc("Accumulates 25% of the mage's damage, then explodes.", "Накапливает 25% урона мага, затем взрывается.")
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, custom("Stores damage", "Накапливает урон", ctx -> {
                    if (ctx.aura != null && ctx.target == ctx.aura.caster) ctx.aura.setData("stored", ctx.aura.getData("stored") + ctx.triggerAmount * 0.25);
                })))
                .onExpire(custom("Explodes", "Взрывается", ctx -> {
                    double stored = ctx.aura.getData("stored");
                    if (stored <= 0) return;
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.target.position(), 8)) {
                        ctx.engine.dealRawDamage(ctx.withTarget(u), u, School.ARCANE, u == ctx.target ? stored : stored * 0.5);
                    }
                    ctx.engine.vfx("arcane_explosion", ctx.caster, ctx.target, null);
                })).vfx("touch_of_the_magi"));
        reg(buff("presence_of_mind", "Presence of Mind", "Присутствие разума").duration(15).stacks(2).stacksPerApply(2)
                .mod(Modifier.castTime("arcane_blast", -1.0)).flatMods()
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("arcane_blast")).consumeStack()));
        reg(passive("arcane_basics", "Clearcasting", "Ясность мысли").mod(Modifier.schoolDamage(School.ARCANE, 0.05))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("clearcasting")).filter(ModFilter.ability("arcane_blast")).chance(0.12))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("clearcasting")).filter(ModFilter.ability("arcane_explosion")).chance(0.12)));
        // Fire
        reg(buff("heating_up", "Heating Up", "Разогрев").duration(10).hidden());
        reg(buff("hot_streak", "Hot Streak!", "Огненная глыба!").duration(15)
                .mod(Modifier.castTime("pyroblast", -1.0), Modifier.castTime("flamestrike", -1.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("pyroblast")).consumeStack())
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("flamestrike")).consumeStack()).vfx("hot_streak"));
        reg(debuff("ignite", "Ignite", "Возгорание").duration(9).school(School.FIRE).tag("dot", "fire").noRefresh()
                .periodic(1, custom("Burns for stored damage", "Сжигает накопленный урон", ctx -> {
                    if (ctx.aura == null) return;
                    double pool = ctx.aura.getData("pool");
                    double tick = pool * 0.2;
                    if (tick < 1) return;
                    ctx.aura.setData("pool", pool - tick);
                    ctx.engine.dealRawDamage(ctx, ctx.target, School.FIRE, tick);
                })).unhastedTicks());
        reg(buff("combustion", "Combustion", "Возгорание").duration(10).mod(Modifier.crit(100), Modifier.of(ModType.CAST_WHILE_MOVING, ModFilter.ability("scorch"), 1))
                .vfx("combustion"));
        cc("dragons_breath", "Dragon's Breath", "Дыхание дракона", CcType.DISORIENT, 4, DispelType.MAGIC);
        reg(debuff("flamestrike_burn", "Flamestrike", "Огненный столб").duration(8).school(School.FIRE).tag("dot", "fire").periodic(2, damage(School.FIRE, sp(0.12))));
        reg(passive("fire_basics", "Hot Streak", "Огненная глыба").mod(Modifier.abilityCrit("fire_blast", 100), Modifier.schoolDamage(School.FIRE, 0.05),
                        Modifier.of(ModType.CAST_WHILE_MOVING, ModFilter.ability("scorch"), 1))
                .trigger(Trigger.on(TriggerType.CRIT_DEALT, hotStreak()).filter(ModFilter.tag("fire_spell")))
                .trigger(Trigger.on(TriggerType.CRIT_DEALT, igniteProc()).filter(ModFilter.tag("fire_spell"))));
        // Frost
        reg(buff("fingers_of_frost", "Fingers of Frost", "Ледяные пальцы").duration(15).stacks(2)
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("ice_lance")).consumeStack()));
        reg(buff("brain_freeze", "Brain Freeze", "Заморозка мозгов").duration(15).mod(Modifier.cost("flurry", -1.0), Modifier.castTime("flurry", -1.0),
                Modifier.abilityDamage("flurry", 0.5)).trigger(Trigger.on(TriggerType.CAST, ctx -> {
        }).filter(ModFilter.ability("flurry")).consumeStack()));
        reg(debuff("winters_chill", "Winter's Chill", "Зимняя стужа").duration(6).stacks(2).school(School.FROST)
                .desc("Treated as frozen.", "Цель считается замороженной.")
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, ctx -> {
                }).filter(ModFilter.ability("ice_lance")).consumeStack()));
        reg(debuff("chilled", "Chilled", "Окоченение").duration(8).school(School.FROST).mod(Modifier.speed(-0.5)).cc(CcType.SLOW).dispel(DispelType.MAGIC));
        reg(buff("icy_veins", "Icy Veins", "Стылая кровь").duration(25).mod(Modifier.haste(0.3)).vfx("icy_veins"));
        reg(buff("ice_barrier", "Ice Barrier", "Ледяная преграда").duration(60).absorb().noPandemic());
        reg(passive("frost_mage_basics", "Fingers of Frost", "Ледяные пальцы").mod(Modifier.schoolDamage(School.FROST, 0.15))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("fingers_of_frost")).filter(ModFilter.ability("frostbolt")).chance(0.18))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("brain_freeze")).filter(ModFilter.ability("frostbolt")).chance(0.25))
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, selfAura("fingers_of_frost")).filter(ModFilter.ability("frozen_orb")).chance(0.1)));

        // ------------------------------------------------------------ class abilities
        reg(ab("fire_blast", "Fire Blast", "Огненный взрыв").school(School.FIRE).ranged().cooldown(12).charges(1).hastedCooldown().offGcd()
                .cost(MANA, 1).effect(damage(sp(0.8))).tag("fire_spell").vfx("fire_blast"));
        reg(ab("arcane_explosion", "Arcane Explosion", "Чародейский взрыв").school(School.ARCANE).target(TargetType.NONE).cost(MANA, 10)
                .effect(aroundSelf(10, damage(sp(0.55))), when(Cond.custom(u -> u.spec == ARCANE, "Arcane", "Тайная магия"), energize(ARCANE_CHARGES, 1)))
                .tag("aoe").vfx("arcane_explosion"));
        reg(ab("blink", "Blink", "Скачок").school(School.ARCANE).target(TargetType.SELF).cooldown(15).offGcd().usableWhileCc().usableWhileCasting()
                .effect(custom("Removes stuns and roots", "Снимает оглушение и обездвиживание", ctx -> {
                    for (AuraInstance a : new java.util.ArrayList<>(ctx.caster.auras().all()))
                        if (a.def.harmful && (a.def.cc == CcType.STUN || a.def.cc == CcType.ROOT)) ctx.engine.removeAura(a, false);
                }), blink(15)).tag("movement").vfx("blink"));
        reg(ab("ice_block", "Ice Block", "Ледяная глыба").school(School.FROST).target(TargetType.SELF).cooldown(240).offGcd().usableWhileCc()
                .requires(Cond.custom(u -> !u.auras().has("hypothermia"), "not Hypothermic", "нет «Гипотермии»"))
                .effect(custom("Removes harmful effects", "Снимает отрицательные эффекты", ctx -> {
                    for (AuraInstance a : new java.util.ArrayList<>(ctx.caster.auras().all()))
                        if (a.def.harmful && !a.def.passive && !a.def.id.equals("hypothermia")) ctx.engine.removeAura(a, false);
                }), selfAura("ice_block"), selfAura("hypothermia")).tag("defensive", "major_defensive"));
        reg(ab("counterspell", "Counterspell", "Антимагия").school(School.ARCANE).range(40).cooldown(24).offGcd().effect(interrupt(6)).tag("interrupt"));
        reg(ab("polymorph", "Polymorph", "Превращение").school(School.ARCANE).ranged().cast(1.7).cost(MANA, 4).effect(aura("polymorph")).tag("cc"));
        reg(ab("frost_nova", "Frost Nova", "Кольцо льда").school(School.FROST).target(TargetType.NONE).cooldown(30).cost(MANA, 2)
                .effect(aroundSelf(12, damage(sp(0.1)), aura("frost_nova"))).tag("cc", "aoe").vfx("frost_nova"));
        reg(ab("arcane_intellect", "Arcane Intellect", "Чародейский интеллект").school(School.ARCANE).target(TargetType.SELF)
                .effect(groupAura("arcane_intellect", 40)).tag("buff"));
        reg(ab("time_warp", "Time Warp", "Искажение времени").school(School.ARCANE).target(TargetType.SELF).cooldown(300).offGcd()
                .effect(custom("Increases haste of all party members by 30% for 40 sec", "Увеличивает скорость всех членов группы на 30% на 40 сек.", ctx -> {
                    for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, 60)) {
                        if (u.auras().has("exhaustion")) continue;
                        ctx.engine.applyAura(ctx.withTarget(u), u, "time_warp", 1, -1);
                        ctx.engine.applyAura(ctx.withTarget(u), u, "exhaustion", 1, -1);
                    }
                })).tag("cooldown", "lust"));
        reg(ab("spellsteal", "Spellsteal", "Похищение заклинания").school(School.ARCANE).ranged().cost(MANA, 10)
                .effect(dispel(1, DispelType.MAGIC)).tag("purge"));
        reg(ab("remove_curse", "Remove Curse", "Снятие проклятия").school(School.ARCANE).target(TargetType.FRIENDLY).cooldown(8).cost(MANA, 1.3)
                .effect(dispel(3, DispelType.CURSE)).tag("dispel"));
        reg(ab("mirror_image", "Mirror Image", "Зеркальное изображение").school(School.ARCANE).target(TargetType.SELF).cooldown(120)
                .effect(selfAura("mirror_image"), dropThreat()).tag("defensive"));
        reg(ab("alter_time", "Alter Time", "Перемещение во времени").school(School.ARCANE).target(TargetType.SELF).cooldown(60).offGcd()
                .effect(custom("Saves your health; after 10 sec you return to it", "Запоминает здоровье; через 10 сек. вы возвращаетесь к нему", ctx -> {
                    AuraInstance a = ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, "alter_time", 1, -1);
                    if (a != null) a.setData("hp", ctx.caster.health());
                })).tag("defensive"));
        reg(ab("slow_mage", "Slow", "Замедление").school(School.ARCANE).ranged().cost(MANA, 1).effect(aura("slow_mage")).tag("slow"));
        reg(ab("ice_barrier", "Ice Barrier", "Ледяная преграда").school(School.FROST).target(TargetType.SELF).cooldown(25).cost(MANA, 3)
                .effect(selfAbsorb("ice_barrier", sp(2.2))).tag("defensive", "shield"));
        reg(ab("ice_lance", "Ice Lance", "Ледяное копье").school(School.FROST).ranged().cost(MANA, 0.5)
                .effect(projectile(40, when(FROZEN, damage(sp(2.1)), damage(sp(0.7))))).tag("frost_spell").vfx("ice_lance"));

        // ------------------------------------------------------------ Arcane
        reg(ab("arcane_blast", "Arcane Blast", "Чародейская вспышка").spec(ARCANE).school(School.ARCANE).ranged().cast(2.25).cost(MANA, 2.75)
                .gen(ARCANE_CHARGES, 1).effect(chargeScaled(sp(1.1))).vfx("arcane_blast"));
        reg(ab("arcane_missiles", "Arcane Missiles", "Чародейские стрелы").spec(ARCANE).school(School.ARCANE).ranged().cost(MANA, 15)
                .channel(2.5, 0.5).tick(damage(sp(0.38))).vfx("arcane_missiles"));
        reg(ab("arcane_barrage", "Arcane Barrage", "Чародейский обстрел").spec(ARCANE).school(School.ARCANE).ranged()
                .effect(custom("Deals more damage per Arcane Charge and consumes them, restoring 2% mana each",
                        "Наносит больше урона за каждый заряд тайной магии, расходует их и восполняет 2% маны за заряд", ctx -> {
                            int charges = (int) ctx.caster.resources().get(ARCANE_CHARGES);
                            EffectContext c = ctx.copy();
                            c.scale *= 1.0 + 0.4 * charges;
                            damage(sp(1.0)).apply(c);
                            for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.target.position(), 8)) {
                                if (u == ctx.target) continue;
                                EffectContext cc = c.withTarget(u);
                                cc.scale *= 0.4;
                                damage(sp(1.0)).apply(cc);
                            }
                            ctx.engine.energize(ctx.caster, MANA, 200.0 * charges);
                            ctx.engine.energize(ctx.caster, ARCANE_CHARGES, -charges);
                        })).vfx("arcane_barrage"));
        reg(ab("arcane_surge", "Arcane Surge", "Чародейский прилив").spec(ARCANE).school(School.ARCANE).ranged().cast(2.5).cooldown(90)
                .effect(damage(sp(2.5)), selfAura("arcane_surge"), energize(ARCANE_CHARGES, 4)).tag("cooldown").vfx("arcane_surge"));
        reg(ab("evocation", "Evocation", "Прилив сил").spec(ARCANE).target(TargetType.SELF).cooldown(90).channel(3, 0.5)
                .tick(energize(MANA, 1700)).tag("utility"));
        reg(ab("touch_of_the_magi", "Touch of the Magi", "Прикосновение мага").spec(ARCANE).school(School.ARCANE).ranged().cooldown(45)
                .effect(damage(sp(0.5)), aura("touch_of_the_magi"), energize(ARCANE_CHARGES, 4)).tag("cooldown"));
        reg(ab("presence_of_mind", "Presence of Mind", "Присутствие разума").spec(ARCANE).target(TargetType.SELF).cooldown(45).offGcd()
                .effect(selfAura("presence_of_mind")).tag("cooldown"));

        // ------------------------------------------------------------ Fire
        reg(ab("fireball", "Fireball", "Огненный шар").spec(FIRE).school(School.FIRE).ranged().cast(2.25).cost(MANA, 2)
                .effect(projectile(35, damage(sp(1.45)))).tag("fire_spell").vfx("fireball"));
        reg(ab("pyroblast", "Pyroblast", "Огненная глыба").spec(FIRE).school(School.FIRE).ranged().cast(4.5).cost(MANA, 2)
                .effect(projectile(30, damage(sp(3.4)))).tag("fire_spell").vfx("pyroblast"));
        reg(ab("phoenix_flames", "Phoenix Flames", "Пламя феникса").spec(FIRE).school(School.FIRE).ranged().cooldown(25).charges(2).hastedCooldown()
                .effect(projectile(35, damage(sp(1.0)), aoe(8, damage(sp(0.4))))).tag("fire_spell", "aoe").vfx("phoenix"));
        reg(ab("scorch", "Scorch", "Ожог").spec(FIRE).school(School.FIRE).ranged().cast(1.5).cost(MANA, 1)
                .effect(when(targetHealthBelow(0.3), damage(sp(1.2)), damage(sp(0.6)))).tag("fire_spell").vfx("scorch"));
        reg(ab("flamestrike", "Flamestrike", "Огненный столб").spec(FIRE).school(School.FIRE).target(TargetType.GROUND).range(40).cast(4.0)
                .cost(MANA, 2.5).effect(delayed(0.5, onTargets(Selector.groundEnemies(8), damage(sp(1.1)), aura("flamestrike_burn")))).tag("fire_spell", "aoe")
                .vfx("flamestrike"));
        reg(ab("combustion", "Combustion", "Возгорание").spec(FIRE).target(TargetType.SELF).cooldown(120).offGcd().usableWhileCasting()
                .effect(selfAura("combustion")).tag("cooldown"));
        reg(ab("dragons_breath", "Dragon's Breath", "Дыхание дракона").spec(FIRE).school(School.FIRE).target(TargetType.NONE).cooldown(45).cost(MANA, 4)
                .effect(cone(90, 12, damage(sp(0.6)), aura("dragons_breath"))).tag("cc", "aoe", "fire_spell").vfx("dragons_breath"));
        reg(ab("meteor", "Meteor", "Метеор").spec(FIRE).school(School.FIRE).target(TargetType.GROUND).range(40).cooldown(45).cost(MANA, 1)
                .effect(delayed(3, onTargets(Selector.groundEnemies(8), damage(sp(3.0)), aura("flamestrike_burn")))).tag("fire_spell", "aoe", "cooldown").vfx("meteor"));

        // ------------------------------------------------------------ Frost
        reg(ab("frostbolt", "Frostbolt", "Ледяная стрела").spec(FROST_MAGE).school(School.FROST).ranged().cast(2.0).cost(MANA, 2)
                .effect(projectile(35, damage(sp(1.75)), aura("chilled"))).tag("frost_spell").vfx("frostbolt"));
        reg(ab("flurry", "Flurry", "Шквал").spec(FROST_MAGE).school(School.FROST).ranged().cast(3.0).cost(MANA, 1)
                .effect(projectile(40, damage(sp(0.6)), damage(sp(0.6)), damage(sp(0.6)), aura("winters_chill", 2))).tag("frost_spell").vfx("flurry"));
        reg(ab("frozen_orb", "Frozen Orb", "Ледяной шар").spec(FROST_MAGE).school(School.FROST).target(TargetType.GROUND).range(40).cooldown(60)
                .cost(MANA, 1).effect(area(GroundArea.Def.enemies("frozen_orb", 8, 8, 0.5, all(damage(School.FROST, sp(0.3)), aura("chilled"))).color(0xFF80E0FF)),
                        selfAura("fingers_of_frost")).tag("aoe", "cooldown").vfx("frozen_orb"));
        reg(ab("blizzard", "Blizzard", "Снежная буря").spec(FROST_MAGE).school(School.FROST).target(TargetType.GROUND).range(40).cast(2.0)
                .cooldown(8).hastedCooldown().cost(MANA, 2.5)
                .effect(area(GroundArea.Def.enemies("blizzard", 8, 8, 1, all(damage(School.FROST, sp(0.3)), aura("chilled"))).color(0xFFA0E0FF)))
                .tag("aoe").vfx("blizzard"));
        reg(ab("icy_veins", "Icy Veins", "Стылая кровь").spec(FROST_MAGE).target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("icy_veins")).tag("cooldown"));
        reg(ab("cone_of_cold", "Cone of Cold", "Конус холода").spec(FROST_MAGE).school(School.FROST).target(TargetType.NONE).cooldown(12)
                .cost(MANA, 4).effect(cone(90, 12, damage(sp(0.5)), aura("chilled"))).tag("aoe", "slow").vfx("cone_of_cold"));
        reg(ab("glacial_spike", "Glacial Spike", "Ледяной шип").spec(FROST_MAGE).school(School.FROST).ranged().cast(3.0).cooldown(20).cost(MANA, 1)
                .effect(projectile(35, damage(sp(3.5)), aura("frost_nova"))).tag("frost_spell").vfx("glacial_spike"));
        reg(ab("comet_storm", "Comet Storm", "Кометная буря").spec(FROST_MAGE).school(School.FROST).range(40).cooldown(30).cost(MANA, 1)
                .effect(delayed(0.5, aoe(6, damage(sp(0.6)))), delayed(1.0, aoe(6, damage(sp(0.6)))), delayed(1.5, aoe(6, damage(sp(0.6)))),
                        delayed(2.0, aoe(6, damage(sp(0.6))))).tag("aoe"));

        // ------------------------------------------------------------ kit
        kit.common("fire_blast", "arcane_explosion", "blink", "ice_block", "counterspell", "polymorph", "frost_nova", "arcane_intellect",
                "time_warp", "spellsteal", "remove_curse", "mirror_image", "alter_time", "slow_mage", "ice_lance");
        kit.spec(ARCANE, "arcane_blast", "arcane_missiles", "arcane_barrage", "arcane_surge", "evocation", "touch_of_the_magi", "presence_of_mind");
        kit.spec(FIRE, "fireball", "pyroblast", "phoenix_flames", "scorch", "flamestrike", "combustion", "dragons_breath");
        kit.spec(FROST_MAGE, "frostbolt", "flurry", "frozen_orb", "blizzard", "icy_veins", "cone_of_cold", "ice_barrier");
        kit.passive(ARCANE, "arcane_basics");
        kit.passive(FIRE, "fire_basics");
        kit.passive(FROST_MAGE, "frost_mage_basics");
        kit.bar(ARCANE, "arcane_blast", "arcane_missiles", "arcane_barrage", "arcane_explosion", "touch_of_the_magi", "arcane_surge",
                "presence_of_mind", "counterspell", "evocation", "blink", "ice_block", "polymorph");
        kit.bar(FIRE, "fireball", "pyroblast", "fire_blast", "phoenix_flames", "scorch", "flamestrike", "combustion", "counterspell",
                "dragons_breath", "blink", "ice_block", "polymorph");
        kit.bar(FROST_MAGE, "frostbolt", "ice_lance", "flurry", "frozen_orb", "blizzard", "cone_of_cold", "icy_veins", "counterspell",
                "frost_nova", "blink", "ice_block", "ice_barrier");

        kit.rotation(ARCANE, of(
                self("ice_block").when(selfHealthBelow(0.15)).urgent(),
                self("mirror_image").when(selfHealthBelow(0.5)).urgent(),
                interrupt("counterspell"),
                use("touch_of_the_magi").when(resourceAtLeast(ARCANE_CHARGES, 4).not()),
                use("arcane_surge"),
                self("evocation").when(resourceBelow(MANA, 1500)),
                use("arcane_missiles").when(Cond.hasAura("clearcasting")),
                use("arcane_explosion").on(com.wowcraft.core.bot.BotTarget.SELF).when(enemiesAround(10, 3).and(resourceBelow(ARCANE_CHARGES, 4))),
                use("arcane_barrage").when(resourceAtLeast(ARCANE_CHARGES, 4).and(resourceBelow(MANA, 3000).or(enemiesAround(30, 3)))),
                use("arcane_blast")));
        kit.rotation(FIRE, of(
                self("ice_block").when(selfHealthBelow(0.15)).urgent(),
                self("mirror_image").when(selfHealthBelow(0.5)).urgent(),
                interrupt("counterspell"),
                self("combustion"),
                use("flamestrike").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND).when(Cond.hasAura("hot_streak").and(enemiesAround(40, 3))),
                use("pyroblast").when(Cond.hasAura("hot_streak")),
                use("fire_blast").when(Cond.hasAura("heating_up")),
                use("phoenix_flames"),
                use("scorch").when(targetHealthBelow(0.3)),
                use("fireball")));
        kit.rotation(FROST_MAGE, of(
                self("ice_block").when(selfHealthBelow(0.15)).urgent(),
                self("ice_barrier").when(Cond.hasAura("ice_barrier").not()).urgent(),
                interrupt("counterspell"),
                self("icy_veins"),
                use("frozen_orb").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND),
                use("blizzard").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND).when(enemiesAround(40, 3)),
                use("flurry").when(Cond.hasAura("brain_freeze")),
                use("ice_lance").when(FROZEN),
                use("frostbolt")));

        // ------------------------------------------------------------ talents
        classTree(
                t("shimmer", 0, 0, "Shimmer", "Мерцание").mod(Modifier.charges("blink", 1)),
                t("improved_frost_nova", 0, 1, "Improved Frost Nova", "Улучшенное кольцо льда").mod(Modifier.charges("frost_nova", 1)),
                t("quick_witted", 0, 2, "Quick Witted", "Сообразительность").mod(Modifier.cooldown("counterspell", -4)),
                t("winters_protection", 1, 0, "Winter's Protection", "Защита зимы").mod(Modifier.cooldown("ice_block", -60)),
                t("tome_of_rhonin", 1, 1, "Tome of Rhonin", "Фолиант Ронина").mod(Modifier.crit(3)),
                t("ice_barrier_talent", 1, 2, "Ice Barrier", "Ледяная преграда").grant("ice_barrier"),
                t("master_of_time", 2, 0, "Master of Time", "Повелитель времени").mod(Modifier.cooldown("alter_time", -10)),
                t("incanters_flow", 2, 1, "Incanter's Flow", "Поток заклинателя").mod(Modifier.damage(0.05)),
                t("time_manipulation", 2, 2, "Time Manipulation", "Манипуляция временем").mod(Modifier.of(ModType.COOLDOWN_RATE, 0.1)));
        specTree(ARCANE,
                t("arcane_tempo", 0, 0, "Arcane Tempo", "Чародейский темп").mod(Modifier.haste(0.05)),
                t("amplification", 0, 1, "Amplification", "Усиление").mod(Modifier.abilityDamage("arcane_missiles", 0.2)),
                t("reverberate", 0, 2, "Reverberate", "Отзвук").mod(Modifier.abilityDamage("arcane_explosion", 0.25)),
                t("arcane_echo", 1, 0, "Arcane Echo", "Чародейское эхо").mod(Modifier.duration("touch_of_the_magi", 2)),
                t("chrono_shift", 1, 1, "Chrono Shift", "Хроносдвиг").mod(Modifier.speed(0.1)),
                t("resonance", 1, 2, "Resonance", "Резонанс").mod(Modifier.abilityDamage("arcane_barrage", 0.2)),
                t("arcane_familiar", 2, 0, "Arcane Familiar", "Чародейский фамильяр").mod(Modifier.ref(ModType.RESOURCE_MAX, MANA.name(), 1000)),
                t("enlightened", 2, 1, "Enlightened", "Просвещенный").mod(Modifier.abilityDamage("arcane_blast", 0.12)),
                t("arcane_bombardment", 2, 2, "Arcane Bombardment", "Чародейская бомбардировка").mod(Modifier.of(ModType.EXECUTE_DAMAGE, ModFilter.ability("arcane_barrage"), 0.5)));
        specTree(FIRE,
                t("pyrotechnics", 0, 0, "Pyrotechnics", "Пиротехника").mod(Modifier.abilityCrit("fireball", 15)),
                t("flame_on", 0, 1, "Flame On", "Пламя").mod(Modifier.charges("fire_blast", 1)),
                t("meteor_talent", 0, 2, "Meteor", "Метеор").grant("meteor"),
                t("searing_touch", 1, 0, "Searing Touch", "Опаляющее прикосновение").mod(Modifier.abilityDamage("scorch", 0.3)),
                t("call_of_the_sun_king", 1, 1, "Call of the Sun King", "Зов Солнечного короля").mod(Modifier.charges("phoenix_flames", 1)),
                t("master_of_flame", 1, 2, "Master of Flame", "Мастер пламени").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("ignite"), 0.2)),
                t("kindling", 2, 0, "Kindling", "Растопка").mod(Modifier.cooldown("combustion", -30)),
                t("fevered_incantation", 2, 1, "Fevered Incantation", "Лихорадочное заклинание").mod(Modifier.crit(4)),
                t("sun_kings_blessing", 2, 2, "Sun King's Blessing", "Благословение Солнечного короля").mod(Modifier.abilityDamage("pyroblast", 0.2)));
        specTree(FROST_MAGE,
                t("lonely_winter", 0, 0, "Lonely Winter", "Одинокая зима").mod(Modifier.schoolDamage(School.FROST, 0.08)),
                t("perpetual_winter", 0, 1, "Perpetual Winter", "Вечная зима").mod(Modifier.charges("flurry", 1)),
                t("glacial_spike_talent", 0, 2, "Glacial Spike", "Ледяной шип").grant("glacial_spike"),
                t("bone_chilling", 1, 0, "Bone Chilling", "Леденящий кости").mod(Modifier.abilityDamage("frostbolt", 0.15)),
                t("piercing_cold", 1, 1, "Piercing Cold", "Пронизывающий холод").mod(Modifier.abilityDamage("ice_lance", 0.25)),
                t("freezing_rain", 1, 2, "Freezing Rain", "Ледяной дождь").mod(Modifier.abilityDamage("blizzard", 0.3)),
                t("comet_storm_talent", 2, 0, "Comet Storm", "Кометная буря").grant("comet_storm"),
                t("thermal_void", 2, 1, "Thermal Void", "Термальная пустота").mod(Modifier.duration("icy_veins", 5)),
                t("everlasting_frost", 2, 2, "Everlasting Frost", "Вечная мерзлота").mod(Modifier.cooldown("frozen_orb", -15)));
        hero(t("hero_spellslinger", 0, 0, "Spellslinger", "Заклинатель").mod(Modifier.damage(0.06), Modifier.haste(0.02)),
                t("hero_sunfury", 0, 1, "Sunfury", "Ярость солнца").mod(Modifier.crit(4), Modifier.schoolDamage(School.FIRE, 0.05)));
    }

    /** Arcane Blast damage increases by 40% per Arcane Charge. */
    private static Effect chargeScaled(com.wowcraft.core.spell.Scaling s) {
        return new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                EffectContext c = ctx.copy();
                c.scale *= 1.0 + 0.4 * ctx.caster.resources().get(ARCANE_CHARGES);
                damage(s).apply(c);
            }

            @Override
            public String describe(com.wowcraft.core.spell.DescribeContext d) {
                return damage(s).describe(d) + d.t(" (+40% per Arcane Charge)", " (+40% за каждый заряд тайной магии)");
            }
        };
    }

    private static Effect hotStreak() {
        return custom("Two critical strikes in a row make your next Pyroblast instant", "Два критических удара подряд делают следующую «Огненную глыбу» мгновенной", ctx -> {
            UnitState m = ctx.caster;
            if (m.auras().has("heating_up")) {
                ctx.engine.removeAura(m, "heating_up", null);
                ctx.engine.applyAura(ctx.withTarget(m), m, "hot_streak", 1, -1);
            } else if (!m.auras().has("hot_streak")) {
                ctx.engine.applyAura(ctx.withTarget(m), m, "heating_up", 1, -1);
            }
        });
    }

    private static Effect igniteProc() {
        return custom("Critical strikes ignite the target", "Критические удары поджигают цель", ctx -> {
            if (ctx.target == null) return;
            double add = ctx.triggerAmount * (0.15 + ctx.caster.stats().masteryPct / 100.0);
            AuraInstance a = ctx.target.auras().get("ignite", ctx.caster);
            if (a == null) a = ctx.engine.applyAura(ctx, ctx.target, "ignite", 1, -1);
            if (a != null) {
                a.setData("pool", a.getData("pool") + add);
                a.expiresAt = ctx.now() + 9;
            }
        });
    }
}
