package com.wowcraft.core.content.classes;

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
import static com.wowcraft.core.resource.ResourceType.MAELSTROM;
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.SHAMAN;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class ShamanContent extends ClassContent {
    public ShamanContent() {
        super(SHAMAN);
    }

    private static final Cond NOT_EXHAUSTED = Cond.custom(u -> !u.auras().has("exhaustion"), "not Exhausted", "нет «Изнеможения»");

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(debuff("flame_shock", "Flame Shock", "Огненный шок").duration(18).school(School.FIRE).tag("dot").dispel(DispelType.MAGIC)
                .periodic(2, damage(School.FIRE, sp(0.16))));
        reg(debuff("frost_shock", "Frost Shock", "Ледяной шок").duration(6).school(School.FROST).mod(Modifier.speed(-0.5)).cc(CcType.SLOW).dispel(DispelType.MAGIC));
        dr("astral_shift", "Astral Shift", "Астральный сдвиг", 0.4, 12);
        reg(buff("ghost_wolf", "Ghost Wolf", "Призрачный волк").form("ghost_wolf").mod(Modifier.speed(0.3)).vfx("ghost_wolf"));
        cc("capacitor_totem", "Static Charge", "Статический заряд", CcType.STUN, 3, DispelType.NONE);
        reg(debuff("hex", "Hex", "Сглаз").duration(60).cc(CcType.INCAPACITATE).breakOnDamage(0.05).dispel(DispelType.CURSE).noPandemic());
        reg(buff("spirit_walk", "Spiritwalker's Grace", "Благосклонность предков").duration(15).mod(Modifier.of(ModType.CAST_WHILE_MOVING, 1)));
        // Elemental
        reg(buff("lava_surge", "Lava Surge", "Волна лавы").duration(10).mod(Modifier.castTime("lava_burst", -1.0))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("lava_burst")).consumeStack()));
        reg(buff("stormkeeper", "Stormkeeper", "Хранитель бурь").duration(15).stacks(2).stacksPerApply(2)
                .mod(Modifier.castTime("lightning_bolt", -1.0), Modifier.castTime("chain_lightning", -1.0),
                        Modifier.abilityDamage("lightning_bolt", 1.0), Modifier.abilityDamage("chain_lightning", 1.0))
                .flatMods()
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("lightning_bolt")).consumeStack())
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("chain_lightning")).consumeStack()));
        reg(passive("elemental_basics", "Lava Surge", "Волна лавы").mod(Modifier.schoolDamage(School.FIRE, 0.05), Modifier.schoolDamage(School.NATURE, 0.05))
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, all(resetCooldown("lava_burst"), selfAura("lava_surge"))).filter(ModFilter.aura("flame_shock")).chance(0.15)));
        // Enhancement
        reg(buff("maelstrom_weapon", "Maelstrom Weapon", "Оружие водоворота").duration(30).stacks(10)
                .mod(Modifier.of(ModType.CAST_TIME_PCT, ModFilter.tag("maelstrom_spell"), -0.2), Modifier.of(ModType.DAMAGE_DONE, ModFilter.tag("maelstrom_spell"), 0.2),
                        Modifier.of(ModType.HEALING_DONE, ModFilter.tag("maelstrom_spell"), 0.2))
                .trigger(Trigger.on(TriggerType.CAST, consumeStacks("maelstrom_weapon", 5)).filter(ModFilter.tag("maelstrom_spell"))));
        reg(buff("crash_lightning", "Crash Lightning", "Сокрушающая молния").duration(12)
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, cleave(0.3, 8, 5)).filter(ModFilter.ability("stormstrike")).icd(0.1)));
        reg(buff("doom_winds", "Doom Winds", "Роковые ветра").duration(8).mod(Modifier.abilityDamage("stormstrike", 0.5), Modifier.haste(0.1)));
        reg(buff("ascendance_enh", "Ascendance", "Перерождение").duration(15).mod(Modifier.damage(0.2), Modifier.haste(0.1)).vfx("ascendance"));
        reg(passive("enhancement_basics", "Windfury Weapon", "Оружие неистовства ветра").mod(Modifier.damage(0.15))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, all(damage(School.PHYSICAL, ap(0.6)), damage(School.PHYSICAL, ap(0.6)))).chance(0.25))
                .trigger(Trigger.on(TriggerType.AUTO_ATTACK, selfAura("maelstrom_weapon")).chance(0.35))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("maelstrom_weapon", 2)).filter(ModFilter.ability("stormstrike"))));
        // Restoration
        reg(buff("riptide", "Riptide", "Быстрина").duration(18).school(School.NATURE).tag("hot").dispel(DispelType.MAGIC).periodic(3, heal(sp(0.32))));
        reg(buff("earth_shield", "Earth Shield", "Щит земли").duration(600).stacks(9).noRefresh().school(School.NATURE)
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, custom("Heals the protected target", "Исцеляет защищенную цель", ctx -> {
                    if (ctx.aura == null) return;
                    UnitState sham = ctx.aura.caster;
                    EffectContext hc = new EffectContext(ctx.engine, sham, ctx.caster, null, null, ctx.aura);
                    ctx.engine.heal(hc, ctx.caster, com.wowcraft.core.combat.Formulas.base(sham, ctx.caster, sp(0.6), 1));
                })).icd(2).consumeStack().self()));
        reg(buff("tidal_waves", "Tidal Waves", "Приливные волны").duration(15).stacks(2)
                .mod(Modifier.castTime("healing_wave", -0.3), Modifier.abilityCrit("healing_surge", 30))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("healing_wave")).consumeStack()));
        reg(passive("restoration_shaman_basics", "Tidal Waves", "Приливные волны")
                .trigger(Trigger.on(TriggerType.CAST, selfAura("tidal_waves", 2)).filter(ModFilter.ability("riptide")))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("tidal_waves", 1)).filter(ModFilter.ability("chain_heal"))));
        reg(buff("spirit_link", "Spirit Link Totem", "Тотем духовной связи").duration(1.5).mod(Modifier.taken(-0.1)).hidden());
        reg(buff("ancestral_guidance", "Ancestral Guidance", "Наставления предков").duration(10)
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, custom("Heals injured allies for 25% of damage", "Исцеляет раненых союзников на 25% от урона", ctx -> {
                    List<UnitState> l = Selector.injuredAllies(40, 3).select(ctx.withTarget(ctx.caster));
                    for (UnitState u : l) ctx.engine.rawHeal(ctx.caster, u, ctx.triggerAmount * 0.25 / Math.max(1, l.size()));
                }))));

        // ------------------------------------------------------------ class abilities
        reg(ab("lightning_bolt", "Lightning Bolt", "Молния").school(School.NATURE).ranged().cast(2.0).cost(MANA, 0.5).gen(MAELSTROM, 8)
                .effect(damage(sp(1.35))).tag("maelstrom_spell").vfx("lightning"));
        reg(ab("chain_lightning", "Chain Lightning", "Цепная молния").school(School.NATURE).ranged().cast(2.0).cost(MANA, 1).gen(MAELSTROM, 4)
                .effect(chain(3, 12, 0.85, false, damage(sp(0.95)))).tag("maelstrom_spell", "aoe").vfx("chain_lightning"));
        reg(ab("flame_shock", "Flame Shock", "Огненный шок").school(School.FIRE).ranged().cost(MANA, 0.75).cooldown(6)
                .effect(damage(sp(0.45)), aura("flame_shock")).tag("dot"));
        reg(ab("frost_shock", "Frost Shock", "Ледяной шок").school(School.FROST).ranged().cost(MANA, 0.5)
                .effect(damage(sp(0.6)), aura("frost_shock")).tag("slow"));
        reg(ab("healing_surge", "Healing Surge", "Исцеляющий всплеск").school(School.NATURE).target(TargetType.FRIENDLY).cast(1.5).cost(MANA, 10)
                .effect(heal(sp(2.4))).tag("heal", "maelstrom_spell").vfx("nature_heal"));
        reg(ab("wind_shear", "Wind Shear", "Пронизывающий ветер").school(School.NATURE).ranged().cooldown(12).offGcd()
                .effect(interrupt(3)).tag("interrupt"));
        reg(ab("astral_shift", "Astral Shift", "Астральный сдвиг").target(TargetType.SELF).cooldown(120).offGcd().usableWhileCc()
                .effect(selfAura("astral_shift")).tag("defensive", "major_defensive"));
        reg(ab("capacitor_totem", "Capacitor Totem", "Тотем конденсации").school(School.NATURE).target(TargetType.GROUND).range(30).cooldown(60)
                .effect(delayed(2, onTargets(Selector.groundEnemies(8), aura("capacitor_totem")))).tag("cc", "aoe").vfx("capacitor"));
        reg(ab("bloodlust", "Bloodlust", "Жажда крови").school(School.NATURE).target(TargetType.SELF).cooldown(300).offGcd()
                .effect(custom("Increases haste of all party members by 30% for 40 sec", "Увеличивает скорость всех членов группы на 30% на 40 сек.", ctx -> {
                    for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, 60)) {
                        if (u.auras().has("exhaustion")) continue;
                        ctx.engine.applyAura(ctx.withTarget(u), u, "bloodlust", 1, -1);
                        ctx.engine.applyAura(ctx.withTarget(u), u, "exhaustion", 1, -1);
                    }
                })).tag("cooldown", "lust").vfx("lust"));
        reg(ab("purge", "Purge", "Развеивание магии").school(School.NATURE).ranged().cost(MANA, 1).effect(dispel(1, DispelType.MAGIC)).tag("purge"));
        reg(ab("cleanse_spirit", "Cleanse Spirit", "Очищение духа").school(School.NATURE).target(TargetType.FRIENDLY).cooldown(8).cost(MANA, 6.5)
                .effect(dispel(3, DispelType.CURSE)).tag("dispel"));
        reg(ab("earth_elemental", "Earth Elemental", "Элементаль земли").school(School.NATURE).target(TargetType.SELF).cooldown(300)
                .effect(summon("earth_elemental", 60, 1)).tag("pet", "defensive"));
        reg(ab("ghost_wolf", "Ghost Wolf", "Призрачный волк").target(TargetType.SELF).cooldown(1).effect(selfAura("ghost_wolf")).tag("movement"));
        reg(ab("hex", "Hex", "Сглаз").school(School.NATURE).ranged().cast(1.7).cooldown(30).effect(aura("hex")).tag("cc"));
        reg(ab("spiritwalkers_grace", "Spiritwalker's Grace", "Благосклонность предков").target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("spirit_walk")).tag("utility"));
        reg(ab("ancestral_guidance", "Ancestral Guidance", "Наставления предков").target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("ancestral_guidance")).tag("raid_cd"));

        // ------------------------------------------------------------ Elemental
        reg(ab("lava_burst", "Lava Burst", "Выброс лавы").spec(ELEMENTAL).school(School.FIRE).ranged().cast(2.0).cooldown(8).charges(2)
                .hastedCooldown().cost(MANA, 0.5).gen(MAELSTROM, 10)
                .effect(projectile(35, when(Cond.targetHasAura("flame_shock"), damage(sp(2.4)), damage(sp(1.6))))).vfx("lava_burst"));
        reg(ab("earth_shock", "Earth Shock", "Земной шок").spec(ELEMENTAL).school(School.NATURE).ranged().cost(MAELSTROM, 60)
                .effect(damage(sp(2.3))).vfx("earth_shock"));
        reg(ab("earthquake", "Earthquake", "Землетрясение").spec(ELEMENTAL).school(School.NATURE).target(TargetType.GROUND).range(40)
                .cost(MAELSTROM, 60).effect(area(GroundArea.Def.enemies("earthquake", 8, 6, 1, all(damage(School.NATURE, sp(0.3)),
                        chance(0.1, aura("generic_knockdown")))).color(0xFF8B5A2B))).tag("aoe").vfx("earthquake"));
        reg(ab("stormkeeper", "Stormkeeper", "Хранитель бурь").spec(ELEMENTAL).target(TargetType.SELF).cast(1.5).cooldown(60)
                .effect(selfAura("stormkeeper")).tag("cooldown"));
        reg(ab("fire_elemental", "Fire Elemental", "Элементаль огня").spec(ELEMENTAL).school(School.FIRE).target(TargetType.SELF).cooldown(150)
                .effect(summon("fire_elemental", 30, 1)).tag("cooldown", "pet"));
        reg(ab("icefury", "Icefury", "Ярость льда").spec(ELEMENTAL).school(School.FROST).ranged().cast(2.0).cooldown(25).gen(MAELSTROM, 15)
                .effect(damage(sp(2.0))));

        // ------------------------------------------------------------ Enhancement
        reg(ab("stormstrike", "Stormstrike", "Удар бури").spec(ENHANCEMENT).school(School.NATURE).cooldown(7.5).hastedCooldown().cost(MANA, 0.5)
                .effect(damage(School.PHYSICAL, ap(1.6)), damage(School.NATURE, ap(1.1))).tag("weapon", "nature").vfx("stormstrike"));
        reg(ab("lava_lash", "Lava Lash", "Вскипание лавы").spec(ENHANCEMENT).school(School.FIRE).cooldown(12).hastedCooldown().cost(MANA, 0.4)
                .effect(damage(ap(2.1)), when(Cond.targetHasAura("flame_shock"), aoe(8, aura("flame_shock")))).tag("weapon").vfx("lava_lash"));
        reg(ab("crash_lightning", "Crash Lightning", "Сокрушающая молния").spec(ENHANCEMENT).school(School.NATURE).target(TargetType.NONE)
                .cooldown(12).hastedCooldown().cost(MANA, 0.6).effect(cone(120, 8, damage(ap(0.8))), selfAura("crash_lightning"))
                .tag("aoe", "nature").vfx("crash_lightning"));
        reg(ab("feral_spirit", "Feral Spirit", "Дух дикого зверя").spec(ENHANCEMENT).target(TargetType.SELF).cooldown(90)
                .effect(summon("spirit_wolf", 15, 2), selfAura("maelstrom_weapon", 3)).tag("cooldown", "pet"));
        reg(ab("doom_winds", "Doom Winds", "Роковые ветра").spec(ENHANCEMENT).target(TargetType.SELF).cooldown(90).offGcd()
                .effect(selfAura("doom_winds")).tag("cooldown"));
        reg(ab("ascendance_enh", "Ascendance", "Перерождение").spec(ENHANCEMENT).target(TargetType.NONE).cooldown(180)
                .effect(aroundSelf(10, damage(School.NATURE, ap(1.5))), selfAura("ascendance_enh")).tag("cooldown"));
        reg(ab("sundering", "Sundering", "Раскол").spec(ENHANCEMENT).target(TargetType.NONE).cooldown(40).cost(MANA, 0.6)
                .effect(onTargets(Selector.line(11, 4), damage(ap(1.1)), aura("generic_knockdown"))).tag("aoe"));

        // ------------------------------------------------------------ Restoration
        reg(ab("riptide", "Riptide", "Быстрина").spec(RESTORATION_SHAMAN).school(School.NATURE).target(TargetType.FRIENDLY).cooldown(6)
                .charges(2).cost(MANA, 3).effect(heal(sp(1.7)), aura("riptide")).tag("heal", "hot").vfx("water_heal"));
        reg(ab("healing_wave", "Healing Wave", "Волна исцеления").spec(RESTORATION_SHAMAN).school(School.NATURE).target(TargetType.FRIENDLY)
                .cast(2.5).cost(MANA, 3).effect(heal(sp(3.5))).tag("heal").vfx("water_heal"));
        reg(ab("chain_heal", "Chain Heal", "Цепное исцеление").spec(RESTORATION_SHAMAN).school(School.NATURE).target(TargetType.FRIENDLY)
                .cast(2.5).cost(MANA, 10).effect(chain(4, 15, 0.75, true, heal(sp(2.0)))).tag("heal", "aoe_heal").vfx("chain_heal"));
        reg(ab("healing_rain", "Healing Rain", "Целительный ливень").spec(RESTORATION_SHAMAN).school(School.NATURE).target(TargetType.GROUND)
                .range(40).cast(2.0).cooldown(10).cost(MANA, 8.5)
                .effect(area(GroundArea.Def.allies("healing_rain", 10, 10, 2, heal(sp(0.28))).color(0xFF40A0FF))).tag("heal", "aoe_heal").vfx("healing_rain"));
        reg(ab("healing_stream_totem", "Healing Stream Totem", "Тотем исцеляющего потока").spec(RESTORATION_SHAMAN).school(School.NATURE)
                .target(TargetType.NONE).cooldown(30).cost(MANA, 5)
                .effect(area(GroundArea.Def.scripted("healing_stream", 40, 15, 2, custom("Heals the most injured ally", "Исцеляет самого раненого союзника", ctx -> {
                    List<UnitState> l = Selector.injuredAllies(40, 1).select(ctx.withTarget(ctx.caster));
                    for (UnitState u : l) ctx.engine.heal(ctx.withTarget(u), u, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(0.7), 1));
                })).centeredOnCaster())).tag("heal", "totem"));
        reg(ab("spirit_link_totem", "Spirit Link Totem", "Тотем духовной связи").spec(RESTORATION_SHAMAN).school(School.NATURE)
                .target(TargetType.GROUND).range(40).cooldown(180)
                .effect(area(GroundArea.Def.scripted("spirit_link", 10, 6, 1, custom("Redistributes health among allies inside", "Перераспределяет здоровье союзников", ctx -> {
                    List<UnitState> allies = ctx.engine.alliesAround(ctx.caster, ctx.point, 10);
                    if (allies.size() < 2) return;
                    double avg = 0;
                    for (UnitState u : allies) avg += u.healthFraction();
                    avg /= allies.size();
                    for (UnitState u : allies) {
                        ctx.engine.applyAura(ctx.withTarget(u), u, "spirit_link", 1, -1);
                        ctx.engine.setHealth(u, u.maxHealth() * avg);
                    }
                })).color(0xFF60FFC0))).tag("raid_cd").vfx("spirit_link"));
        reg(ab("healing_tide_totem", "Healing Tide Totem", "Тотем целительного прилива").spec(RESTORATION_SHAMAN).school(School.NATURE)
                .target(TargetType.NONE).cooldown(180)
                .effect(area(GroundArea.Def.allies("healing_tide", 40, 10, 2, heal(sp(0.8))).centeredOnCaster().color(0xFF40FFFF)))
                .tag("raid_cd", "heal"));
        reg(ab("earth_shield", "Earth Shield", "Щит земли").spec(RESTORATION_SHAMAN).school(School.NATURE).target(TargetType.FRIENDLY)
                .cost(MANA, 2.5).effect(aura("earth_shield", 9)).tag("buff"));
        reg(ab("purify_spirit", "Purify Spirit", "Очищение духа").spec(RESTORATION_SHAMAN).school(School.NATURE).target(TargetType.FRIENDLY)
                .cooldown(8).cost(MANA, 6.5).effect(dispel(3, DispelType.MAGIC, DispelType.CURSE)).tag("dispel"));

        // ------------------------------------------------------------ kit
        kit.common("lightning_bolt", "chain_lightning", "flame_shock", "frost_shock", "healing_surge", "wind_shear", "astral_shift",
                "capacitor_totem", "bloodlust", "purge", "earth_elemental", "ghost_wolf", "hex", "spiritwalkers_grace");
        kit.spec(ELEMENTAL, "lava_burst", "earth_shock", "earthquake", "stormkeeper", "fire_elemental", "cleanse_spirit");
        kit.spec(ENHANCEMENT, "stormstrike", "lava_lash", "crash_lightning", "feral_spirit", "doom_winds", "cleanse_spirit");
        kit.spec(RESTORATION_SHAMAN, "riptide", "healing_wave", "chain_heal", "healing_rain", "healing_stream_totem", "spirit_link_totem",
                "healing_tide_totem", "earth_shield", "purify_spirit");
        kit.passive(ELEMENTAL, "elemental_basics");
        kit.passive(ENHANCEMENT, "enhancement_basics");
        kit.passive(RESTORATION_SHAMAN, "restoration_shaman_basics");
        kit.bar(ELEMENTAL, "lightning_bolt", "lava_burst", "flame_shock", "earth_shock", "chain_lightning", "earthquake", "frost_shock",
                "wind_shear", "stormkeeper", "fire_elemental", "astral_shift", "bloodlust");
        kit.bar(ENHANCEMENT, "stormstrike", "lava_lash", "lightning_bolt", "crash_lightning", "flame_shock", "frost_shock", "chain_lightning",
                "wind_shear", "feral_spirit", "doom_winds", "astral_shift", "bloodlust");
        kit.bar(RESTORATION_SHAMAN, "riptide", "healing_wave", "healing_surge", "chain_heal", "healing_rain", "healing_stream_totem",
                "earth_shield", "purify_spirit", "spirit_link_totem", "healing_tide_totem", "astral_shift", "bloodlust");

        kit.rotation(ELEMENTAL, of(
                self("astral_shift").when(selfHealthBelow(0.35)).urgent(),
                interrupt("wind_shear"),
                self("fire_elemental"),
                self("stormkeeper"),
                use("flame_shock").when(Cond.targetHasAura("flame_shock").not()),
                use("earthquake").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND).when(enemiesAround(40, 3).and(resourceAtLeast(MAELSTROM, 60))),
                use("earth_shock").when(resourceAtLeast(MAELSTROM, 60)),
                use("lava_burst"),
                use("chain_lightning").when(enemiesAround(40, 3)),
                use("lightning_bolt")));
        kit.rotation(ENHANCEMENT, of(
                self("astral_shift").when(selfHealthBelow(0.35)).urgent(),
                heal("healing_surge").below(0.35).when(Cond.auraStacks("maelstrom_weapon", 5)).on(com.wowcraft.core.bot.BotTarget.SELF).urgent(),
                interrupt("wind_shear"),
                self("feral_spirit"),
                self("doom_winds"),
                use("crash_lightning").when(enemiesAround(8, 2)),
                use("flame_shock").when(Cond.targetHasAura("flame_shock").not()),
                use("chain_lightning").when(enemiesAround(30, 3).and(Cond.auraStacks("maelstrom_weapon", 5))),
                use("lightning_bolt").when(Cond.auraStacks("maelstrom_weapon", 5)),
                use("stormstrike"),
                use("lava_lash"),
                use("frost_shock")));
        kit.rotation(RESTORATION_SHAMAN, of(
                self("astral_shift").when(selfHealthBelow(0.35)).urgent(),
                use("healing_tide_totem").on(com.wowcraft.core.bot.BotTarget.SELF).when(Cond.alliesBelow(0.5, 3)).urgent(),
                use("spirit_link_totem").on(com.wowcraft.core.bot.BotTarget.ALLY_GROUND).when(Cond.alliesBelow(0.35, 2)).urgent(),
                interrupt("wind_shear"),
                dispel("purify_spirit"),
                heal("earth_shield").on(com.wowcraft.core.bot.BotTarget.TANK).when(Cond.targetHasAnyAura("earth_shield").not()),
                heal("riptide").below(0.9),
                use("healing_stream_totem").on(com.wowcraft.core.bot.BotTarget.SELF),
                use("healing_rain").on(com.wowcraft.core.bot.BotTarget.ALLY_GROUND),
                heal("healing_surge").below(0.5),
                heal("chain_heal").below(0.75),
                heal("healing_wave").below(0.85),
                use("flame_shock").when(Cond.targetHasAura("flame_shock").not()),
                use("lightning_bolt")));

        // ------------------------------------------------------------ talents
        classTree(
                t("spirit_wolf", 0, 0, "Spirit Wolf", "Дух волка").mod(Modifier.speed(0.05), Modifier.taken(-0.02)),
                t("planes_traveler", 0, 1, "Planes Traveler", "Странник измерений").mod(Modifier.cooldown("astral_shift", -30)),
                t("spiritwalkers_aegis", 0, 2, "Spiritwalker's Aegis", "Эгида предков").mod(Modifier.cooldown("spiritwalkers_grace", -60)),
                t("ancestral_guidance_talent", 1, 0, "Ancestral Guidance", "Наставления предков").grant("ancestral_guidance"),
                t("static_charge", 1, 1, "Static Charge", "Статический заряд").mod(Modifier.cooldown("capacitor_totem", -15)),
                t("voodoo_mastery", 1, 2, "Voodoo Mastery", "Мастерство вуду").mod(Modifier.cooldown("hex", -15)),
                t("elemental_orbit", 2, 0, "Elemental Orbit", "Орбита стихий").mod(Modifier.stat(Stat.INTELLECT, 0.04), Modifier.stat(Stat.AGILITY, 0.04)),
                t("nature_guardian", 2, 1, "Nature's Guardian", "Страж природы").mod(Modifier.maxHealth(0.05)),
                t("totemic_surge", 2, 2, "Totemic Surge", "Тотемный всплеск").mod(Modifier.cooldown("capacitor_totem", -6), Modifier.cooldown("healing_stream_totem", -6)));
        specTree(ELEMENTAL,
                t("flux_melting", 0, 0, "Flux Melting", "Плавление").mod(Modifier.abilityDamage("lava_burst", 0.15)),
                t("call_of_thunder", 0, 1, "Call of Thunder", "Зов грома").mod(Modifier.ref(ModType.RESOURCE_MAX, MAELSTROM.name(), 50)),
                t("icefury_talent", 0, 2, "Icefury", "Ярость льда").grant("icefury"),
                t("echo_of_the_elements", 1, 0, "Echo of the Elements", "Эхо стихий").mod(Modifier.charges("lava_burst", 1)),
                t("searing_flames", 1, 1, "Searing Flames", "Опаляющее пламя").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("flame_shock"), 0.3)),
                t("echoes_of_great_sundering", 1, 2, "Echoes of Great Sundering", "Отголоски Великого Раскола").mod(Modifier.abilityDamage("earthquake", 0.3)),
                t("storm_elemental", 2, 0, "Primordial Wave", "Первородная волна").mod(Modifier.cooldown("fire_elemental", -30)),
                t("ascendance_ele", 2, 1, "Surge of Power", "Всплеск силы").mod(Modifier.abilityDamage("earth_shock", 0.2)),
                t("master_of_the_elements", 2, 2, "Master of the Elements", "Повелитель стихий").mod(Modifier.abilityDamage("lightning_bolt", 0.2)));
        specTree(ENHANCEMENT,
                t("hot_hand", 0, 0, "Hot Hand", "Горячая рука").mod(Modifier.abilityDamage("lava_lash", 0.3)),
                t("stormflurry", 0, 1, "Stormflurry", "Буревой шквал").mod(Modifier.abilityDamage("stormstrike", 0.15)),
                t("sundering_talent", 0, 2, "Sundering", "Раскол").grant("sundering"),
                t("elemental_spirits", 1, 0, "Elemental Spirits", "Духи стихий").mod(Modifier.cooldown("feral_spirit", -30)),
                t("swirling_maelstrom", 1, 1, "Swirling Maelstrom", "Вихрь водоворота").mod(Modifier.abilityDamage("lightning_bolt", 0.2)),
                t("forceful_winds", 1, 2, "Forceful Winds", "Сильные ветра").mod(Modifier.duration("doom_winds", 4)),
                t("ascendance_talent", 2, 0, "Ascendance", "Перерождение").grant("ascendance_enh"),
                t("elemental_assault", 2, 1, "Elemental Assault", "Натиск стихий").mod(Modifier.charges("stormstrike", 1)),
                t("crashing_storms", 2, 2, "Crashing Storms", "Сокрушающие бури").mod(Modifier.abilityDamage("crash_lightning", 0.4)));
        specTree(RESTORATION_SHAMAN,
                t("tidebringer", 0, 0, "Tidebringer", "Вестник прилива").mod(Modifier.abilityHealing("chain_heal", 0.15)),
                t("resurgence", 0, 1, "Resurgence", "Возрождение").mod(Modifier.regen(MANA.name(), 0.2)),
                t("echo_of_the_elements_resto", 0, 2, "Echo of the Elements", "Эхо стихий").mod(Modifier.charges("riptide", 1)),
                t("deluge", 1, 0, "Deluge", "Наводнение").mod(Modifier.abilityHealing("healing_rain", 0.3)),
                t("high_tide", 1, 1, "High Tide", "Высокий прилив").mod(Modifier.of(ModType.EXTRA_TARGETS, ModFilter.ability("chain_heal"), 1)),
                t("living_stream", 1, 2, "Living Stream", "Живой поток").mod(Modifier.cooldown("healing_stream_totem", -6)),
                t("ascendance_resto", 2, 0, "Ancestral Protection", "Защита предков").mod(Modifier.cooldown("spirit_link_totem", -60)),
                t("downpour", 2, 1, "Downpour", "Ливень").mod(Modifier.cooldown("healing_tide_totem", -60)),
                t("unleash_life", 2, 2, "Unleash Life", "Высвобождение жизни").mod(Modifier.abilityHealing("healing_wave", 0.2), Modifier.abilityHealing("riptide", 0.2)));
        hero(t("hero_stormbringer", 0, 0, "Stormbringer", "Повелитель бури").mod(Modifier.schoolDamage(School.NATURE, 0.1)),
                t("hero_farseer", 0, 1, "Farseer", "Провидец").mod(Modifier.healing(0.06), Modifier.damage(0.04)));
    }
}
