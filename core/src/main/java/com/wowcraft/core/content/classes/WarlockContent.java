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
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.resource.ResourceType.SOUL_SHARDS;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.WARLOCK;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class WarlockContent extends ClassContent {
    public WarlockContent() {
        super(WARLOCK);
    }

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(debuff("corruption", "Corruption", "Порча").duration(14).school(School.SHADOW).tag("dot").dispel(DispelType.MAGIC)
                .periodic(2, damage(School.SHADOW, sp(0.2))));
        reg(debuff("fear_warlock", "Fear", "Страх").duration(20).cc(CcType.FEAR).breakOnDamage(0.1).dispel(DispelType.MAGIC).noPandemic());
        dr("unending_resolve", "Unending Resolve", "Твердая решимость", 0.4, 8);
        reg(debuff("mortal_coil", "Mortal Coil", "Лик тлена").duration(3).cc(CcType.DISORIENT).noPandemic());
        reg(debuff("curse_of_tongues", "Curse of Tongues", "Проклятие косноязычия").duration(60).dispel(DispelType.CURSE)
                .mod(Modifier.of(ModType.CAST_TIME_PCT, 0.3)).desc("Casting time increased by 30%.", "Время применения заклинаний увеличено на 30%."));
        reg(debuff("curse_of_weakness", "Curse of Weakness", "Проклятие слабости").duration(120).dispel(DispelType.CURSE).mod(Modifier.damage(-0.1)));
        reg(buff("dark_pact", "Dark Pact", "Темный пакт").duration(20).absorb().noPandemic());
        // Affliction
        reg(debuff("agony", "Agony", "Агония").duration(18).school(School.SHADOW).tag("dot").stacks(10).dispel(DispelType.CURSE)
                .periodic(2, all(damage(School.SHADOW, sp(0.05)), custom("Agony grows stronger", "Агония усиливается", ctx -> {
                    if (ctx.aura != null && ctx.aura.stacks < 10) {
                        ctx.aura.stacks++;
                        ctx.target.auras().markDirty();
                    }
                }), chance(0.2, custom("Generates a Soul Shard", "Создает осколок души", ctx -> ctx.engine.energize(ctx.caster, SOUL_SHARDS, 1))))));
        reg(debuff("unstable_affliction", "Unstable Affliction", "Нестабильное колдовство").duration(16).school(School.SHADOW).tag("dot")
                .dispel(DispelType.MAGIC).periodic(2, damage(School.SHADOW, sp(0.32))).mod(Modifier.taken(0.05)));
        reg(debuff("seed_of_corruption", "Seed of Corruption", "Семя порчи").duration(12).school(School.SHADOW).tag("dot")
                .onExpire(seedExplode()).onRemove(seedExplode()).breakOnDamage(0.0)
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, custom("Detonates after enough damage", "Взрывается после получения урона", ctx -> {
                    if (ctx.aura == null) return;
                    double acc = ctx.aura.getData("acc") + ctx.triggerAmount;
                    ctx.aura.setData("acc", acc);
                    if (acc > ctx.caster.maxHealth() * 0.15) ctx.engine.removeAura(ctx.aura, true);
                })).self()));
        reg(debuff("haunt", "Haunt", "Блуждающий дух").duration(18).mod(Modifier.taken(0.1)).vfx("haunt"));
        reg(buff("nightfall", "Nightfall", "Сумерки").duration(12).stacks(2).mod(Modifier.castTime("shadow_bolt", -1.0), Modifier.abilityDamage("shadow_bolt", 0.25))
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("shadow_bolt")).consumeStack()));
        reg(passive("affliction_basics", "Nightfall", "Сумерки").mod(Modifier.damage(-0.1))
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, selfAura("nightfall")).filter(ModFilter.aura("corruption")).chance(0.13)));
        // Demonology
        reg(buff("demonic_core", "Demonic Core", "Демоническое ядро").duration(20).stacks(4).mod(Modifier.castTime("demonbolt", -1.0))
                .flatMods().trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("demonbolt")).consumeStack()));
        reg(buff("demonic_power", "Demonic Power", "Демоническая сила").duration(15).mod(Modifier.of(ModType.PET_DAMAGE, 0.15)).vfx("demonic_power"));
        reg(passive("demonology_basics", "Demonic Core", "Демоническое ядро").mod(Modifier.of(ModType.PET_DAMAGE, 0.1))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("demonic_core")).filter(ModFilter.ability("hand_of_guldan")).chance(0.5))
                .trigger(Trigger.on(TriggerType.CAST, selfAura("demonic_core", 2)).filter(ModFilter.ability("call_dreadstalkers"))));
        // Destruction
        reg(debuff("immolate", "Immolate", "Жертвенный огонь").duration(18).school(School.FIRE).tag("dot").dispel(DispelType.MAGIC)
                .periodic(3, all(damage(School.FIRE, sp(0.28)), energize(SOUL_SHARDS, 0.1))));
        reg(buff("backdraft", "Backdraft", "Обратная тяга").duration(10).stacks(2)
                .mod(Modifier.castTime("incinerate", -0.3), Modifier.castTime("chaos_bolt", -0.3)).flatMods()
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("incinerate")).consumeStack())
                .trigger(Trigger.on(TriggerType.CAST, ctx -> {
                }).filter(ModFilter.ability("chaos_bolt")).consumeStack()));
        reg(debuff("havoc", "Havoc", "Хаос").duration(12).desc("Your single-target spells are duplicated on this target.",
                "Ваши заклинания по одной цели дублируются на эту цель.").vfx("havoc"));
        reg(passive("destruction_basics", "Havoc", "Хаос").mod(Modifier.abilityCrit("chaos_bolt", 100), Modifier.schoolDamage(School.FIRE, 0.05))
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, custom("Duplicates damage to the Havoc target", "Дублирует урон на цель «Хаоса»", ctx -> {
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 40)) {
                        if (u != ctx.target && u.auras().get("havoc", ctx.caster) != null) {
                            ctx.engine.dealRawDamage(ctx.withTarget(u), u, School.FIRE, ctx.triggerAmount * 0.8);
                            return;
                        }
                    }
                })).filter(ModFilter.notTag("aoe")).icd(0.1)));
        reg(debuff("rain_of_fire_burn", "Rain of Fire", "Огненный ливень").duration(1.5).hidden());

        // ------------------------------------------------------------ class abilities
        reg(ab("corruption", "Corruption", "Порча").school(School.SHADOW).ranged().cost(MANA, 1).effect(aura("corruption")).tag("dot"));
        reg(ab("shadow_bolt", "Shadow Bolt", "Стрела Тьмы").school(School.SHADOW).ranged().cast(2.0).cost(MANA, 1.5)
                .effect(projectile(35, damage(sp(1.2))), when(Cond.custom(u -> u.spec == DEMONOLOGY, "Demonology", "Демонология"), energize(SOUL_SHARDS, 1)))
                .vfx("shadow_bolt"));
        reg(ab("fear_warlock", "Fear", "Страх").school(School.SHADOW).ranged().cast(1.7).cost(MANA, 1.5).effect(aura("fear_warlock")).tag("cc"));
        reg(ab("unending_resolve", "Unending Resolve", "Твердая решимость").target(TargetType.SELF).cooldown(180).offGcd().usableWhileCc()
                .effect(selfAura("unending_resolve"), selfAura("immune_cc_short")).tag("defensive", "major_defensive"));
        reg(buff("immune_cc_short", "Unending Resolve", "Твердая решимость").duration(8).mod(Modifier.immune("INTERRUPT")).hidden());
        reg(ab("drain_life", "Drain Life", "Похищение жизни").school(School.SHADOW).ranged().cost(MANA, 2).channel(5, 1)
                .tick(damage(sp(0.3)), drainLast(1.5)).tag("heal").vfx("drain_life"));
        reg(ab("summon_imp", "Summon Imp", "Призыв беса").target(TargetType.SELF).cast(2.5).requires(Cond.hasPet().not())
                .effect(summon("imp", 0, 1)).tag("pet"));
        reg(ab("summon_voidwalker", "Summon Voidwalker", "Призыв демона Бездны").target(TargetType.SELF).cast(2.5).requires(Cond.hasPet().not())
                .effect(summon("voidwalker", 0, 1)).tag("pet"));
        reg(ab("summon_felhunter", "Summon Felhunter", "Призыв охотника Скверны").target(TargetType.SELF).cast(2.5).requires(Cond.hasPet().not())
                .effect(summon("felhunter", 0, 1)).tag("pet"));
        reg(ab("spell_lock", "Spell Lock", "Запрет чар").school(School.SHADOW).range(40).cooldown(24).offGcd().requires(Cond.hasPet())
                .effect(interrupt(5)).tag("interrupt"));
        reg(ab("mortal_coil", "Mortal Coil", "Лик тлена").school(School.SHADOW).ranged().cooldown(45)
                .effect(projectile(30, aura("mortal_coil")), healPct(0.2)).tag("cc", "heal"));
        reg(ab("soulstone", "Soulstone", "Камень души").school(School.SHADOW).target(TargetType.DEAD_FRIENDLY).cast(3).cooldown(600)
                .effect(resurrect(0.6)).tag("battle_res"));
        reg(ab("curse_of_tongues", "Curse of Tongues", "Проклятие косноязычия").school(School.SHADOW).ranged().cost(MANA, 1)
                .effect(aura("curse_of_tongues")).tag("utility"));
        reg(ab("curse_of_weakness", "Curse of Weakness", "Проклятие слабости").school(School.SHADOW).ranged().cost(MANA, 1)
                .effect(aura("curse_of_weakness")).tag("utility"));
        reg(ab("dark_pact", "Dark Pact", "Темный пакт").target(TargetType.SELF).cooldown(60).offGcd()
                .effect(selfAbsorb("dark_pact", hp(0.25))).tag("defensive"));
        reg(ab("healthstone_create", "Create Healthstone", "Создание камня здоровья").target(TargetType.SELF).cast(3).cooldown(60)
                .effect(custom("Your party is healed for 20%", "Группа исцелена на 20%", ctx -> {
                    for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, 40)) ctx.engine.rawHeal(ctx.caster, u, u.maxHealth() * 0.2);
                })).tag("heal"));

        // ------------------------------------------------------------ Affliction
        reg(ab("agony", "Agony", "Агония").spec(AFFLICTION).school(School.SHADOW).ranged().cost(MANA, 1).effect(aura("agony")).tag("dot"));
        reg(ab("unstable_affliction", "Unstable Affliction", "Нестабильное колдовство").spec(AFFLICTION).school(School.SHADOW).ranged()
                .cast(1.5).cost(SOUL_SHARDS, 1).effect(aura("unstable_affliction")).tag("dot").vfx("shadow_cast"));
        reg(ab("malefic_rapture", "Malefic Rapture", "Злобный захват").spec(AFFLICTION).school(School.SHADOW).ranged().cast(1.5)
                .cost(SOUL_SHARDS, 1).effect(custom("Damages all enemies affected by your DoTs, more per effect", "Наносит урон всем противникам с вашими эффектами",
                        ctx -> {
                            for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 40)) {
                                int dots = 0;
                                for (AuraInstance a : u.auras().all())
                                    if (a.caster == ctx.caster && a.def.harmful && a.def.isPeriodic()) dots++;
                                if (dots == 0) continue;
                                ctx.engine.dealDamage(ctx.withTarget(u), u, School.SHADOW,
                                        com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(0.55), 1) * dots);
                            }
                        })).vfx("malefic_rapture"));
        reg(ab("seed_of_corruption", "Seed of Corruption", "Семя порчи").spec(AFFLICTION).school(School.SHADOW).ranged().cast(2.0)
                .cost(SOUL_SHARDS, 1).effect(aura("seed_of_corruption")).tag("dot", "aoe"));
        reg(ab("haunt", "Haunt", "Блуждающий дух").spec(AFFLICTION).school(School.SHADOW).ranged().cast(1.5).cooldown(15)
                .effect(projectile(25, damage(sp(1.5)), aura("haunt"))).vfx("haunt"));
        reg(ab("summon_darkglare", "Summon Darkglare", "Призыв Темного взора").spec(AFFLICTION).school(School.SHADOW).ranged().cooldown(120)
                .effect(summon("darkglare", 20, 1), custom("Extends your DoTs on the target by 8 sec", "Продлевает ваши эффекты на цели на 8 сек.", ctx -> {
                    if (ctx.target == null) return;
                    for (AuraInstance a : ctx.target.auras().all()) if (a.caster == ctx.caster && a.def.isPeriodic()) a.expiresAt += 8;
                })).tag("cooldown", "pet"));
        reg(ab("soul_rot", "Soul Rot", "Гниение души").spec(AFFLICTION).school(School.NATURE).ranged().cast(1.5).cooldown(60)
                .effect(aura("soul_rot"), aoe(10, aura("soul_rot"))).tag("cooldown", "dot"));
        reg(debuff("soul_rot", "Soul Rot", "Гниение души").duration(8).school(School.NATURE).tag("dot").periodic(1, damage(School.NATURE, sp(0.25))));

        // ------------------------------------------------------------ Demonology
        reg(ab("hand_of_guldan", "Hand of Gul'dan", "Рука Гул'дана").spec(DEMONOLOGY).school(School.SHADOW).ranged().cast(1.5)
                .costRange(SOUL_SHARDS, 1, 3).effect(aoe(8, damage(sp(0.4).perCombo())), custom("Summons a Wild Imp per Soul Shard spent",
                        "Призывает дикого беса за каждый потраченный осколок души", ctx -> {
                            for (int i = 0; i < Math.max(1, ctx.comboSpent); i++) ctx.engine.summon(ctx.caster, "wild_imp", 15, i);
                        })).tag("aoe").vfx("hand_of_guldan"));
        reg(ab("call_dreadstalkers", "Call Dreadstalkers", "Призыв зловещих охотников").spec(DEMONOLOGY).school(School.SHADOW).ranged()
                .cast(2.0).cooldown(20).cost(SOUL_SHARDS, 2).effect(summon("dreadstalker", 12, 2)).tag("pet"));
        reg(ab("demonbolt", "Demonbolt", "Демоническая стрела").spec(DEMONOLOGY).school(School.SHADOW).ranged().cast(4.5).cost(MANA, 2)
                .gen(SOUL_SHARDS, 2).effect(projectile(35, damage(sp(2.0)))).vfx("demonbolt"));
        reg(ab("implosion", "Implosion", "Имплозия").spec(DEMONOLOGY).school(School.SHADOW).ranged().cost(MANA, 2)
                .effect(custom("Your Wild Imps explode at the target", "Ваши дикие бесы взрываются у цели", ctx -> {
                    int n = 0;
                    for (UnitState p : new java.util.ArrayList<>(ctx.caster.livingPets())) {
                        if (!"wild_imp".equals(p.templateId)) continue;
                        n++;
                        if (p.body != null) p.body.despawn();
                        ctx.engine.remove(p);
                    }
                    if (n == 0 || ctx.target == null) return;
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.target.position(), 8)) {
                        ctx.engine.dealDamage(ctx.withTarget(u), u, School.SHADOW, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(0.6), 1) * n);
                    }
                })).tag("aoe").vfx("implosion"));
        reg(ab("summon_demonic_tyrant", "Summon Demonic Tyrant", "Призыв демонического тирана").spec(DEMONOLOGY).target(TargetType.SELF)
                .cast(2.0).cooldown(90).effect(summon("demonic_tyrant", 15, 1), selfAura("demonic_power"),
                        custom("Extends your demons by 15 sec", "Продлевает время действия ваших демонов на 15 сек.", ctx -> {
                            for (UnitState p : ctx.caster.livingPets()) if (p.despawnAt < Double.POSITIVE_INFINITY) p.despawnAt += 15;
                        })).tag("cooldown", "pet"));
        reg(ab("summon_felguard", "Summon Felguard", "Призыв стража Скверны").spec(DEMONOLOGY).target(TargetType.SELF).cast(2.5)
                .requires(Cond.hasPet().not()).effect(summon("felguard", 0, 1)).tag("pet"));
        reg(ab("felstorm", "Felstorm", "Буря Скверны").spec(DEMONOLOGY).school(School.PHYSICAL).target(TargetType.SELF).cooldown(30).offGcd()
                .requires(Cond.hasPet()).effect(custom("Your Felguard whirls, hitting nearby enemies", "Ваш страж Скверны вращается, поражая противников", ctx -> {
                    for (UnitState p : ctx.caster.livingPets()) {
                        if (!"felguard".equals(p.templateId)) continue;
                        for (int i = 0; i < 5; i++) {
                            int tick = i;
                            ctx.engine.schedule(i * 0.6, () -> {
                                for (UnitState u : ctx.engine.enemiesAround(p, p.position(), 8)) {
                                    ctx.engine.dealDamage(ctx.withCaster(p, u), u, School.PHYSICAL, com.wowcraft.core.combat.Formulas.base(ctx.caster, u, sp(0.25), 1));
                                }
                            });
                        }
                    }
                })).tag("aoe", "pet"));
        reg(ab("grimoire_felguard", "Grimoire: Felguard", "Гримуар: страж Скверны").spec(DEMONOLOGY).target(TargetType.SELF).cooldown(120)
                .effect(summon("felguard", 17, 1)).tag("cooldown", "pet"));

        // ------------------------------------------------------------ Destruction
        reg(ab("immolate", "Immolate", "Жертвенный огонь").spec(DESTRUCTION).school(School.FIRE).ranged().cast(1.5).cost(MANA, 1.5)
                .effect(damage(sp(0.6)), aura("immolate")).tag("dot").vfx("immolate"));
        reg(ab("incinerate", "Incinerate", "Испепеление").spec(DESTRUCTION).school(School.FIRE).ranged().cast(2.0).cost(MANA, 2)
                .gen(SOUL_SHARDS, 0.2).effect(projectile(35, damage(sp(1.3)))).vfx("incinerate"));
        reg(ab("conflagrate", "Conflagrate", "Поджигание").spec(DESTRUCTION).school(School.FIRE).ranged().cooldown(12).charges(2).hastedCooldown()
                .cost(MANA, 1).gen(SOUL_SHARDS, 0.5).effect(damage(sp(1.1)), selfAura("backdraft", 2)).vfx("conflagrate"));
        reg(ab("chaos_bolt", "Chaos Bolt", "Стрела Хаоса").spec(DESTRUCTION).school(School.CHAOS).ranged().cast(3.0).cost(SOUL_SHARDS, 2)
                .effect(projectile(25, damage(sp(3.3)))).vfx("chaos_bolt"));
        reg(ab("rain_of_fire", "Rain of Fire", "Огненный ливень").spec(DESTRUCTION).school(School.FIRE).target(TargetType.GROUND).range(40)
                .cost(SOUL_SHARDS, 3).effect(area(GroundArea.Def.enemies("rain_of_fire", 8, 8, 1, damage(School.FIRE, sp(0.3))).color(0xFFFF6020)))
                .tag("aoe").vfx("rain_of_fire"));
        reg(ab("havoc", "Havoc", "Хаос").spec(DESTRUCTION).school(School.SHADOW).ranged().cooldown(30).effect(aura("havoc")).tag("utility"));
        reg(ab("summon_infernal", "Summon Infernal", "Призыв инфернала").spec(DESTRUCTION).school(School.FIRE).target(TargetType.GROUND).range(30)
                .cooldown(120).effect(onTargets(Selector.groundEnemies(8), damage(sp(1.0)), aura("generic_stun")), summon("infernal", 30, 1))
                .tag("cooldown", "pet").vfx("infernal"));
        reg(ab("shadowburn", "Shadowburn", "Ожог Тьмы").spec(DESTRUCTION).school(School.SHADOW).ranged().cost(SOUL_SHARDS, 1).cooldown(12)
                .charges(2).effect(damage(sp(1.5)), when(targetHealthBelow(0.2), energize(SOUL_SHARDS, 1))).tag("execute"));
        reg(ab("cataclysm", "Cataclysm", "Катаклизм").spec(DESTRUCTION).school(School.FIRE).target(TargetType.GROUND).range(40).cast(2.0)
                .cooldown(30).effect(onTargets(Selector.groundEnemies(8), damage(sp(1.5)), aura("immolate"))).tag("aoe"));

        // ------------------------------------------------------------ kit
        kit.common("corruption", "shadow_bolt", "fear_warlock", "unending_resolve", "drain_life", "spell_lock", "mortal_coil", "soulstone",
                "curse_of_tongues", "curse_of_weakness", "healthstone_create");
        kit.spec(AFFLICTION, "agony", "unstable_affliction", "malefic_rapture", "seed_of_corruption", "haunt", "summon_darkglare",
                "summon_imp", "summon_felhunter");
        kit.spec(DEMONOLOGY, "hand_of_guldan", "call_dreadstalkers", "demonbolt", "implosion", "summon_demonic_tyrant", "summon_felguard", "felstorm");
        kit.spec(DESTRUCTION, "immolate", "incinerate", "conflagrate", "chaos_bolt", "rain_of_fire", "havoc", "summon_infernal",
                "summon_imp", "summon_voidwalker");
        kit.passive(AFFLICTION, "affliction_basics");
        kit.passive(DEMONOLOGY, "demonology_basics");
        kit.passive(DESTRUCTION, "destruction_basics");
        kit.defaultPet.put(AFFLICTION, "felhunter");
        kit.defaultPet.put(DEMONOLOGY, "felguard");
        kit.defaultPet.put(DESTRUCTION, "imp");
        kit.bar(AFFLICTION, "agony", "corruption", "unstable_affliction", "malefic_rapture", "shadow_bolt", "haunt", "seed_of_corruption",
                "spell_lock", "summon_darkglare", "drain_life", "unending_resolve", "fear_warlock");
        kit.bar(DEMONOLOGY, "shadow_bolt", "hand_of_guldan", "demonbolt", "call_dreadstalkers", "implosion", "summon_demonic_tyrant",
                "felstorm", "spell_lock", "corruption", "drain_life", "unending_resolve", "fear_warlock");
        kit.bar(DESTRUCTION, "immolate", "incinerate", "conflagrate", "chaos_bolt", "rain_of_fire", "havoc", "shadowburn", "spell_lock",
                "summon_infernal", "drain_life", "unending_resolve", "fear_warlock");

        kit.rotation(AFFLICTION, of(
                self("unending_resolve").when(selfHealthBelow(0.3)).urgent(),
                self("summon_felhunter").when(Cond.hasPet().not()),
                interrupt("spell_lock"),
                use("agony").when(Cond.targetHasAura("agony").not()),
                use("corruption").when(Cond.targetHasAura("corruption").not()),
                use("unstable_affliction").when(Cond.targetHasAura("unstable_affliction").not()),
                use("seed_of_corruption").when(enemiesAround(40, 3).and(Cond.targetHasAura("seed_of_corruption").not())),
                use("summon_darkglare"),
                use("haunt"),
                use("malefic_rapture").when(resourceAtLeast(SOUL_SHARDS, 4)),
                use("drain_life").when(selfHealthBelow(0.5)),
                use("shadow_bolt")));
        kit.rotation(DEMONOLOGY, of(
                self("unending_resolve").when(selfHealthBelow(0.3)).urgent(),
                self("summon_felguard").when(Cond.hasPet().not()),
                interrupt("spell_lock"),
                use("call_dreadstalkers"),
                self("summon_demonic_tyrant"),
                self("felstorm").when(enemiesAround(30, 2)),
                use("hand_of_guldan").when(resourceAtLeast(SOUL_SHARDS, 3)),
                use("demonbolt").when(Cond.hasAura("demonic_core").and(resourceBelow(SOUL_SHARDS, 4))),
                use("implosion").when(enemiesAround(30, 3)),
                use("shadow_bolt")));
        kit.rotation(DESTRUCTION, of(
                self("unending_resolve").when(selfHealthBelow(0.3)).urgent(),
                self("summon_imp").when(Cond.hasPet().not()),
                interrupt("spell_lock"),
                use("summon_infernal").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND),
                use("immolate").when(Cond.targetHasAura("immolate").not()),
                use("rain_of_fire").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND).when(enemiesAround(40, 3).and(resourceAtLeast(SOUL_SHARDS, 3))),
                use("chaos_bolt").when(resourceAtLeast(SOUL_SHARDS, 4)),
                use("shadowburn").when(targetHealthBelow(0.2)),
                use("conflagrate"),
                use("chaos_bolt").when(resourceAtLeast(SOUL_SHARDS, 2)),
                use("incinerate")));

        // ------------------------------------------------------------ talents
        classTree(
                t("dark_pact_talent", 0, 0, "Dark Pact", "Темный пакт").grant("dark_pact"),
                t("demonic_embrace", 0, 1, "Demonic Embrace", "Демонические объятия").mod(Modifier.stat(Stat.STAMINA, 0.1)),
                t("fiendish_stride", 0, 2, "Fiendish Stride", "Дьявольский шаг").mod(Modifier.speed(0.1)),
                t("soul_conduit", 1, 0, "Soul Conduit", "Проводник душ").mod(Modifier.regen(SOUL_SHARDS.name(), 0.5)),
                t("demonic_resilience", 1, 1, "Demonic Resilience", "Демоническая стойкость").mod(Modifier.cooldown("unending_resolve", -45)),
                t("howl_of_terror", 1, 2, "Howl of Terror", "Вой ужаса").mod(Modifier.castTime("fear_warlock", -1.0)),
                t("grimoire_of_synergy", 2, 0, "Grimoire of Synergy", "Гримуар синергии").mod(Modifier.of(ModType.PET_DAMAGE, 0.1), Modifier.damage(0.03)),
                t("dark_accord", 2, 1, "Dark Accord", "Темное соглашение").mod(Modifier.cooldown("mortal_coil", -15)),
                t("summoners_embrace", 2, 2, "Summoner's Embrace", "Объятия призывателя").mod(Modifier.stat(Stat.INTELLECT, 0.04)));
        specTree(AFFLICTION,
                t("writhe_in_agony", 0, 0, "Writhe in Agony", "Корчи агонии").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("agony"), 0.2)),
                t("absolute_corruption", 0, 1, "Absolute Corruption", "Абсолютная порча").mod(Modifier.duration("corruption", 10)),
                t("soul_rot_talent", 0, 2, "Soul Rot", "Гниение души").grant("soul_rot"),
                t("creeping_death", 1, 0, "Creeping Death", "Ползучая смерть").mod(Modifier.of(ModType.TICK_RATE, ModFilter.tag("dot"), 0.15)),
                t("withering_bolt", 1, 1, "Withering Bolt", "Иссушающая стрела").mod(Modifier.abilityDamage("shadow_bolt", 0.2)),
                t("dread_touch", 1, 2, "Dread Touch", "Касание ужаса").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("unstable_affliction"), 0.25)),
                t("malefic_affliction", 2, 0, "Malefic Affliction", "Злобное колдовство").mod(Modifier.abilityDamage("malefic_rapture", 0.2)),
                t("dark_harvest", 2, 1, "Dark Harvest", "Темная жатва").mod(Modifier.cooldown("summon_darkglare", -30)),
                t("sow_the_seeds", 2, 2, "Sow the Seeds", "Посев").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("seed_of_corruption"), 0.4)));
        specTree(DEMONOLOGY,
                t("dreadlash", 0, 0, "Dreadlash", "Удар ужаса").mod(Modifier.of(ModType.PET_DAMAGE, 0.08)),
                t("demonic_strength", 0, 1, "Demonic Strength", "Демоническая сила").mod(Modifier.cooldown("felstorm", -10)),
                t("grimoire_felguard_talent", 0, 2, "Grimoire: Felguard", "Гримуар: страж Скверны").grant("grimoire_felguard"),
                t("sacrificed_souls", 1, 0, "Sacrificed Souls", "Принесенные в жертву души").mod(Modifier.damage(0.04)),
                t("power_siphon", 1, 1, "Power Siphon", "Похищение силы").mod(Modifier.abilityDamage("demonbolt", 0.2)),
                t("fel_might", 1, 2, "Fel Might", "Мощь Скверны").mod(Modifier.cooldown("call_dreadstalkers", -4)),
                t("reign_of_tyranny", 2, 0, "Reign of Tyranny", "Правление тирании").mod(Modifier.duration("demonic_power", 5)),
                t("guldans_ambition", 2, 1, "Gul'dan's Ambition", "Амбиции Гул'дана").mod(Modifier.abilityDamage("hand_of_guldan", 0.25)),
                t("the_expendables", 2, 2, "The Expendables", "Расходный материал").mod(Modifier.abilityDamage("implosion", 0.3)));
        specTree(DESTRUCTION,
                t("roaring_blaze", 0, 0, "Roaring Blaze", "Ревущее пламя").mod(Modifier.abilityDamage("conflagrate", 0.25)),
                t("improved_conflagrate", 0, 1, "Improved Conflagrate", "Улучшенное поджигание").mod(Modifier.charges("conflagrate", 1)),
                t("cataclysm_talent", 0, 2, "Cataclysm", "Катаклизм").grant("cataclysm"),
                t("eradication", 1, 0, "Eradication", "Искоренение").mod(Modifier.abilityDamage("chaos_bolt", 0.15)),
                t("inferno", 1, 1, "Inferno", "Инферно").mod(Modifier.abilityDamage("rain_of_fire", 0.3)),
                t("mayhem", 1, 2, "Mayhem", "Безумие").mod(Modifier.cooldown("havoc", -10)),
                t("crashing_chaos", 2, 0, "Crashing Chaos", "Сокрушительный хаос").mod(Modifier.cooldown("summon_infernal", -30)),
                t("burn_to_ashes", 2, 1, "Burn to Ashes", "Обращение в пепел").mod(Modifier.abilityDamage("incinerate", 0.2)),
                t("ruin", 2, 2, "Ruin", "Разорение").mod(Modifier.of(ModType.CRIT_DAMAGE, 0.2)));
        hero(t("hero_hellcaller", 0, 0, "Hellcaller", "Призыватель ада").mod(Modifier.tagDamage("dot", 0.1), Modifier.damage(0.03)),
                t("hero_diabolist", 0, 1, "Diabolist", "Демонолог").mod(Modifier.of(ModType.PET_DAMAGE, 0.15)));
    }

    private static com.wowcraft.core.spell.Effect seedExplode() {
        return custom("Explodes, damaging nearby enemies and applying Corruption", "Взрывается, нанося урон противникам рядом и накладывая «Порчу»", ctx -> {
            if (ctx.aura != null && ctx.aura.getData("exploded") > 0) return;
            if (ctx.aura != null) ctx.aura.setData("exploded", 1);
            UnitState lock = ctx.caster;
            for (UnitState u : ctx.engine.enemiesAround(lock, ctx.target.position(), 10)) {
                EffectContext c = new EffectContext(ctx.engine, lock, u, null, null, ctx.aura);
                ctx.engine.dealDamage(c, u, School.SHADOW, com.wowcraft.core.combat.Formulas.base(lock, u, sp(1.2), 1));
                ctx.engine.applyAura(c, u, "corruption", 1, -1);
            }
            ctx.engine.vfx("seed_explosion", lock, ctx.target, null);
        });
    }
}
