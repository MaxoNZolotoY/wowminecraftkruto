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
import static com.wowcraft.core.resource.ResourceType.INSANITY;
import static com.wowcraft.core.resource.ResourceType.MANA;
import static com.wowcraft.core.spec.Spec.*;
import static com.wowcraft.core.spec.WowClass.PRIEST;
import static com.wowcraft.core.spell.Cond.*;
import static com.wowcraft.core.spell.Effects.*;
import static com.wowcraft.core.spell.Scaling.*;

public final class PriestContent extends ClassContent {
    public PriestContent() {
        super(PRIEST);
    }

    private static final Cond HOSTILE_TARGET = Cond.of(c -> c.target != null && c.engine.isHostile(c.caster, c.target),
            "target is an enemy", "цель — противник");

    @Override
    public void register() {
        // ------------------------------------------------------------ auras
        reg(buff("power_word_shield", "Power Word: Shield", "Слово силы: Щит").duration(15).absorb().noPandemic().dispel(DispelType.MAGIC).vfx("shield"));
        reg(debuff("shadow_word_pain", "Shadow Word: Pain", "Слово Тьмы: Боль").duration(16).school(School.SHADOW).tag("dot", "shadow_dot")
                .dispel(DispelType.MAGIC).periodic(2, damage(School.SHADOW, sp(0.22))));
        cc("psychic_scream", "Psychic Scream", "Ментальный крик", CcType.FEAR, 8, DispelType.MAGIC);
        reg(buff("fade", "Fade", "Уход в тень").duration(10).mod(Modifier.threat(-0.9), Modifier.taken(-0.1)));
        reg(buff("power_infusion", "Power Infusion", "Придание сил").duration(15).mod(Modifier.haste(0.2)).dispel(DispelType.MAGIC).vfx("power_infusion"));
        reg(buff("power_word_fortitude", "Power Word: Fortitude", "Слово силы: Стойкость").duration(3600).persistent()
                .mod(Modifier.stat(Stat.STAMINA, 0.05)).desc("Stamina increased by 5%.", "Выносливость увеличена на 5%."));
        reg(debuff("holy_fire", "Holy Fire", "Священный огонь").duration(7).school(School.HOLY).tag("dot").periodic(1, damage(School.HOLY, sp(0.08))));
        // Discipline
        reg(buff("atonement", "Atonement", "Искупление вины").duration(15).desc("Healed when the Priest deals damage.", "Получает исцеление, когда жрец наносит урон.")
                .vfx("atonement"));
        reg(passive("atonement_caster", "Atonement", "Искупление вины").trigger(Trigger.on(TriggerType.DAMAGE_DEALT, atonementHeal())));
        reg(buff("pain_suppression", "Pain Suppression", "Подавление боли").duration(8).mod(Modifier.taken(-0.4)).vfx("pain_suppression"));
        reg(buff("power_word_barrier", "Power Word: Barrier", "Слово силы: Барьер").duration(1.5).mod(Modifier.taken(-0.2)).hidden());
        reg(buff("rapture", "Rapture", "Восторг").duration(8).mod(Modifier.of(ModType.ABSORB_DONE, 0.4), Modifier.cooldownPct("power_word_shield", -1.0)));
        // Holy
        reg(buff("renew", "Renew", "Обновление").duration(15).school(School.HOLY).tag("hot").dispel(DispelType.MAGIC).periodic(3, heal(sp(0.45))));
        reg(buff("prayer_of_mending", "Prayer of Mending", "Молитва восстановления").duration(30).stacks(5).noRefresh()
                .trigger(Trigger.on(TriggerType.DAMAGE_TAKEN, custom("Heals and jumps", "Исцеляет и перескакивает", ctx -> {
                    AuraInstance a = ctx.aura;
                    if (a == null) return;
                    UnitState priest = a.caster;
                    EffectContext hc = new EffectContext(ctx.engine, priest, ctx.caster, null, null, a);
                    ctx.engine.heal(hc, ctx.caster, com.wowcraft.core.combat.Formulas.base(priest, ctx.caster, sp(1.2), 1));
                    int left = a.stacks - 1;
                    ctx.engine.removeAura(a, false);
                    if (left <= 0) return;
                    UnitState next = null;
                    for (UnitState u : ctx.engine.alliesAround(ctx.caster, ctx.caster.position(), 20)) {
                        if (u == ctx.caster || u.auras().has("prayer_of_mending")) continue;
                        if (next == null || u.healthFraction() < next.healthFraction()) next = u;
                    }
                    if (next == null) next = ctx.caster;
                    ctx.engine.applyAura(new EffectContext(ctx.engine, priest, next, null, null, null), next, "prayer_of_mending", left, -1);
                    ctx.engine.vfx("chain_heal", ctx.caster, next, null);
                })).icd(1).self()));
        reg(buff("guardian_spirit", "Guardian Spirit", "Оберегающий дух").duration(10).mod(Modifier.healingTaken(0.6))
                .trigger(Trigger.on(TriggerType.LETHAL_DAMAGE, custom("Prevents death and heals for 40%", "Предотвращает смерть и восполняет 40% здоровья", ctx -> {
                    ctx.engine.removeAura(ctx.caster, "guardian_spirit", null);
                    ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, "guardian_save", 1, -1);
                    ctx.engine.rawHeal(ctx.caster, ctx.caster, Math.max(0, ctx.caster.maxHealth() * 0.4 - ctx.caster.health()));
                })).self()).vfx("guardian_spirit"));
        reg(buff("guardian_save", "Guardian Spirit", "Оберегающий дух").duration(0.2).mod(Modifier.immuneDamage()).hidden());
        reg(passive("holy_priest_basics", "Holy Words", "Слова Света")
                .trigger(Trigger.on(TriggerType.CAST, cooldown("holy_word_serenity", -6)).filter(ModFilter.ability("heal")))
                .trigger(Trigger.on(TriggerType.CAST, cooldown("holy_word_serenity", -6)).filter(ModFilter.ability("flash_heal")))
                .trigger(Trigger.on(TriggerType.CAST, cooldown("holy_word_sanctify", -6)).filter(ModFilter.ability("prayer_of_healing"))));
        // Shadow
        reg(buff("shadowform", "Shadowform", "Облик Тьмы").form("shadowform").persistent().mod(Modifier.schoolDamage(School.SHADOW, 0.1), Modifier.taken(-0.1))
                .vfx("shadowform"));
        reg(debuff("vampiric_touch", "Vampiric Touch", "Прикосновение вампира").duration(21).school(School.SHADOW).tag("dot", "shadow_dot")
                .dispel(DispelType.MAGIC).periodic(3, all(damage(School.SHADOW, sp(0.36)), drainLast(0.2))));
        reg(debuff("devouring_plague", "Devouring Plague", "Всепожирающая чума").duration(6).school(School.SHADOW).tag("dot", "shadow_dot")
                .dispel(DispelType.DISEASE).periodic(1.5, all(damage(School.SHADOW, sp(0.45)), drainLast(0.5))));
        reg(buff("voidform", "Voidform", "Облик Бездны").duration(20).mod(Modifier.damage(0.2), Modifier.haste(0.1),
                Modifier.charges("mind_blast", 1)).vfx("voidform"));
        reg(buff("dispersion", "Dispersion", "Слияние с Тьмой").duration(6).mod(Modifier.taken(-0.75), Modifier.speed(0.5))
                .periodic(1, healPct(0.05)).unhastedTicks().vfx("dispersion"));
        reg(buff("vampiric_embrace", "Vampiric Embrace", "Объятия вампира").duration(12)
                .trigger(Trigger.on(TriggerType.DAMAGE_DEALT, custom("Heals your party for 20% of Shadow damage dealt", "Исцеляет группу на 20% от урона от темной магии", ctx -> {
                    for (UnitState u : ctx.engine.groupMembersAround(ctx.caster, 40)) ctx.engine.rawHeal(ctx.caster, u, ctx.triggerAmount * 0.2 / 3);
                }))));
        cc("silence", "Silence", "Безмолвие", CcType.SILENCE, 4, DispelType.MAGIC);
        reg(passive("shadow_basics", "Shadowy Apparitions", "Призрачные явления")
                .trigger(Trigger.on(TriggerType.CRIT_DEALT, damage(School.SHADOW, sp(0.4))).filter(ModFilter.aura("shadow_word_pain")).chance(1.0)));

        // ------------------------------------------------------------ class abilities
        reg(ab("power_word_shield", "Power Word: Shield", "Слово силы: Щит").school(School.HOLY).target(TargetType.FRIENDLY).cost(MANA, 3)
                .cooldown(7.5).hastedCooldown().effect(absorb("power_word_shield", sp(3.0)),
                        when(Cond.custom(u -> u.spec == DISCIPLINE, "Discipline", "Послушание"), aura("atonement"))).tag("shield", "heal"));
        reg(ab("shadow_word_pain", "Shadow Word: Pain", "Слово Тьмы: Боль").school(School.SHADOW).ranged().cost(MANA, 1)
                .gen(INSANITY, 3).effect(damage(sp(0.15)), aura("shadow_word_pain")).tag("dot", "shadow_dot"));
        reg(ab("smite", "Smite", "Кара").school(School.HOLY).ranged().cast(1.5).cost(MANA, 0.5).effect(damage(sp(0.85))).vfx("holy_bolt"));
        reg(ab("flash_heal", "Flash Heal", "Быстрое исцеление").school(School.HOLY).target(TargetType.FRIENDLY).cast(1.5).cost(MANA, 10)
                .effect(heal(sp(2.2)), when(Cond.custom(u -> u.spec == DISCIPLINE, "Discipline", "Послушание"), aura("atonement")))
                .tag("heal").vfx("holy_heal"));
        reg(ab("psychic_scream", "Psychic Scream", "Ментальный крик").school(School.SHADOW).target(TargetType.NONE).cooldown(45)
                .effect(aroundSelf(8, aura("psychic_scream"))).tag("cc", "aoe"));
        reg(ab("fade", "Fade", "Уход в тень").target(TargetType.SELF).cooldown(30).offGcd().effect(selfAura("fade")).tag("defensive"));
        reg(ab("desperate_prayer", "Desperate Prayer", "Молитва отчаяния").school(School.HOLY).target(TargetType.SELF).cooldown(90).offGcd()
                .usableWhileCc().effect(healPct(0.25)).tag("defensive", "heal"));
        reg(ab("purify", "Purify", "Очищение").school(School.HOLY).target(TargetType.FRIENDLY).cost(MANA, 1.3).cooldown(8)
                .effect(dispel(3, DispelType.MAGIC, DispelType.DISEASE)).tag("dispel"));
        reg(ab("purify_disease", "Purify Disease", "Очищение от болезни").school(School.HOLY).target(TargetType.FRIENDLY).cost(MANA, 1.3)
                .cooldown(8).effect(dispel(3, DispelType.DISEASE)).tag("dispel"));
        reg(ab("dispel_magic", "Dispel Magic", "Рассеивание заклинаний").school(School.HOLY).ranged().cost(MANA, 2)
                .effect(dispel(1, DispelType.MAGIC)).tag("purge"));
        reg(ab("power_infusion", "Power Infusion", "Придание сил").school(School.HOLY).target(TargetType.FRIENDLY).cooldown(120).offGcd()
                .effect(aura("power_infusion")).tag("external", "cooldown"));
        reg(ab("shadow_word_death", "Shadow Word: Death", "Слово Тьмы: Смерть").school(School.SHADOW).ranged().cooldown(10).cost(MANA, 0.5)
                .gen(INSANITY, 4).requires(targetHealthBelow(0.2)).effect(damage(sp(1.8))).tag("execute").vfx("shadow_burst"));
        reg(ab("mind_blast", "Mind Blast", "Взрыв разума").school(School.SHADOW).ranged().cast(1.5).cooldown(9).hastedCooldown()
                .cost(MANA, 0.3).gen(INSANITY, 6).effect(damage(sp(2.0))).vfx("mind_blast"));
        reg(ab("leap_of_faith", "Leap of Faith", "Дружеская рука").school(School.HOLY).target(TargetType.FRIENDLY).range(40).cooldown(90).offGcd()
                .effect(custom("Pulls the ally to you", "Притягивает союзника к вам", ctx -> {
                    if (ctx.target != null && ctx.target != ctx.caster) ctx.engine.moveTo(ctx.target, ctx.caster.position(), true);
                })).tag("utility"));
        reg(ab("power_word_fortitude", "Power Word: Fortitude", "Слово силы: Стойкость").school(School.HOLY).target(TargetType.SELF)
                .effect(groupAura("power_word_fortitude", 40)).tag("buff"));
        reg(ab("holy_fire", "Holy Fire", "Священный огонь").school(School.HOLY).ranged().cast(1.5).cooldown(10).cost(MANA, 1)
                .effect(damage(sp(1.4)), aura("holy_fire")).vfx("holy_fire"));
        reg(ab("mass_resurrection", "Mass Dispel", "Массовое рассеивание").school(School.HOLY).target(TargetType.GROUND).range(30).cooldown(60)
                .cost(MANA, 8).cast(1.5).effect(onTargets(Selector.groundAllies(15), dispel(1, DispelType.MAGIC)),
                        onTargets(Selector.groundEnemies(15), dispel(1, DispelType.MAGIC))).tag("dispel", "purge"));

        // ------------------------------------------------------------ Discipline
        reg(ab("penance", "Penance", "Исповедь").spec(DISCIPLINE).school(School.HOLY).target(TargetType.ANY).ranged().cooldown(9).cost(MANA, 1.6)
                .channel(2, 0.66).tick(when(HOSTILE_TARGET, damage(sp(0.5)), heal(sp(1.1)))).tag("heal").vfx("penance"));
        reg(ab("power_word_radiance", "Power Word: Radiance", "Сияние Слова силы").spec(DISCIPLINE).school(School.HOLY).target(TargetType.FRIENDLY)
                .cast(2.0).cooldown(18).charges(2).cost(MANA, 6)
                .effect(heal(sp(1.1)), aura("atonement"), onTargets(Selector.injuredAllies(30, 4), heal(sp(1.1)), aura("atonement"))).tag("heal", "aoe_heal"));
        reg(ab("pain_suppression", "Pain Suppression", "Подавление боли").spec(DISCIPLINE).school(School.HOLY).target(TargetType.FRIENDLY)
                .cooldown(180).offGcd().usableWhileCc().effect(aura("pain_suppression")).tag("external"));
        reg(ab("power_word_barrier", "Power Word: Barrier", "Слово силы: Барьер").spec(DISCIPLINE).school(School.HOLY).target(TargetType.GROUND)
                .range(40).cooldown(180).effect(area(GroundArea.Def.allies("power_word_barrier", 8, 10, 1, aura("power_word_barrier")).color(0xFFFFFF80)))
                .tag("raid_cd", "defensive"));
        reg(ab("rapture", "Rapture", "Восторг").spec(DISCIPLINE).target(TargetType.SELF).cooldown(90).effect(selfAura("rapture")).tag("cooldown"));
        reg(ab("mind_blast_disc", "Mind Blast", "Взрыв разума").spec(DISCIPLINE).school(School.SHADOW).ranged().cast(1.5).cooldown(15)
                .cost(MANA, 2).effect(damage(sp(1.3)), absorb("power_word_shield", sp(1.0))).hidden());

        // ------------------------------------------------------------ Holy
        reg(ab("heal", "Heal", "Исцеление").spec(HOLY_PRIEST).school(School.HOLY).target(TargetType.FRIENDLY).cast(2.5).cost(MANA, 6)
                .effect(heal(sp(3.8))).tag("heal").vfx("holy_heal"));
        reg(ab("prayer_of_healing", "Prayer of Healing", "Молитва исцеления").spec(HOLY_PRIEST).school(School.HOLY).target(TargetType.FRIENDLY)
                .cast(2.0).cost(MANA, 12).effect(onTargets(Selector.injuredAllies(30, 5), heal(sp(1.5)))).tag("heal", "aoe_heal"));
        reg(ab("holy_word_serenity", "Holy Word: Serenity", "Слово Света: Безмятежность").spec(HOLY_PRIEST).school(School.HOLY)
                .target(TargetType.FRIENDLY).cooldown(60).cost(MANA, 2.5).effect(heal(sp(7.0))).tag("heal").vfx("holy_burst"));
        reg(ab("holy_word_sanctify", "Holy Word: Sanctify", "Слово Света: Освящение").spec(HOLY_PRIEST).school(School.HOLY)
                .target(TargetType.GROUND).range(40).cooldown(60).cost(MANA, 3.5).effect(onTargets(Selector.groundAllies(10), heal(sp(2.6))))
                .tag("heal", "aoe_heal").vfx("sanctify"));
        reg(ab("renew", "Renew", "Обновление").spec(HOLY_PRIEST).school(School.HOLY).target(TargetType.FRIENDLY).cost(MANA, 1.8)
                .effect(aura("renew")).tag("heal", "hot"));
        reg(ab("prayer_of_mending", "Prayer of Mending", "Молитва восстановления").spec(HOLY_PRIEST).school(School.HOLY).target(TargetType.FRIENDLY)
                .cooldown(12).hastedCooldown().cost(MANA, 2).effect(aura("prayer_of_mending", 5)).tag("heal"));
        reg(ab("guardian_spirit", "Guardian Spirit", "Оберегающий дух").spec(HOLY_PRIEST).school(School.HOLY).target(TargetType.FRIENDLY)
                .cooldown(180).offGcd().effect(aura("guardian_spirit")).tag("external"));
        reg(ab("divine_hymn", "Divine Hymn", "Божественный гимн").spec(HOLY_PRIEST).school(School.HOLY).target(TargetType.NONE).cooldown(180)
                .channel(5, 1).tick(onTargets(Selector.group(40), heal(sp(1.0)))).tag("heal", "raid_cd").vfx("divine_hymn"));
        reg(ab("halo", "Halo", "Сияние").school(School.HOLY).target(TargetType.NONE).cast(1.5).cooldown(60).cost(MANA, 2.7)
                .effect(onTargets(Selector.alliesAroundCaster(30), heal(sp(1.2))), aroundSelf(30, damage(sp(0.9)))).tag("heal", "aoe_heal").vfx("halo"));

        // ------------------------------------------------------------ Shadow
        reg(ab("shadowform", "Shadowform", "Облик Тьмы").spec(SHADOW).target(TargetType.SELF).requires(Cond.hasAura("shadowform").not())
                .effect(selfAura("shadowform")).tag("buff"));
        reg(ab("vampiric_touch", "Vampiric Touch", "Прикосновение вампира").spec(SHADOW).school(School.SHADOW).ranged().cast(1.5)
                .gen(INSANITY, 4).effect(aura("vampiric_touch")).tag("dot", "shadow_dot").form("shadowform"));
        reg(ab("mind_flay", "Mind Flay", "Пытка разума").spec(SHADOW).school(School.SHADOW).ranged().channel(4.5, 0.75)
                .tick(damage(sp(0.45)), energize(INSANITY, 2)).form("shadowform").vfx("mind_flay"));
        reg(ab("devouring_plague", "Devouring Plague", "Всепожирающая чума").spec(SHADOW).school(School.SHADOW).ranged().cost(INSANITY, 50)
                .effect(damage(sp(1.6)), aura("devouring_plague")).tag("dot", "shadow_dot").form("shadowform").vfx("plague"));
        reg(ab("void_eruption", "Void Eruption", "Извержение Бездны").spec(SHADOW).school(School.SHADOW).ranged().cast(1.5).cooldown(120)
                .effect(aoe(8, damage(sp(1.6))), selfAura("voidform"), resetCooldown("mind_blast")).tag("cooldown").form("shadowform").vfx("void_eruption"));
        reg(ab("dispersion", "Dispersion", "Слияние с Тьмой").spec(SHADOW).target(TargetType.SELF).cooldown(120).usableWhileCc()
                .effect(selfAura("dispersion")).tag("defensive", "major_defensive"));
        reg(ab("vampiric_embrace", "Vampiric Embrace", "Объятия вампира").spec(SHADOW).target(TargetType.SELF).cooldown(120).offGcd()
                .effect(selfAura("vampiric_embrace")).tag("raid_cd"));
        reg(ab("silence", "Silence", "Безмолвие").spec(SHADOW).school(School.SHADOW).range(30).cooldown(45).offGcd()
                .effect(interrupt(4), aura("silence")).tag("interrupt", "cc"));
        reg(ab("shadow_crash", "Shadow Crash", "Темное столкновение").spec(SHADOW).school(School.SHADOW).target(TargetType.GROUND).range(40)
                .cooldown(15).charges(2).gen(INSANITY, 6).effect(delayed(1, onTargets(Selector.groundEnemies(8), damage(sp(0.9)), aura("vampiric_touch"))))
                .tag("aoe").vfx("shadow_crash"));

        // ------------------------------------------------------------ kit
        kit.common("power_word_shield", "shadow_word_pain", "smite", "flash_heal", "psychic_scream", "fade", "desperate_prayer",
                "dispel_magic", "power_infusion", "shadow_word_death", "mind_blast", "leap_of_faith", "power_word_fortitude");
        kit.spec(DISCIPLINE, "penance", "power_word_radiance", "pain_suppression", "power_word_barrier", "rapture", "purify");
        kit.spec(HOLY_PRIEST, "heal", "prayer_of_healing", "holy_word_serenity", "holy_word_sanctify", "renew", "prayer_of_mending",
                "guardian_spirit", "divine_hymn", "purify", "holy_fire");
        kit.spec(SHADOW, "shadowform", "vampiric_touch", "mind_flay", "devouring_plague", "void_eruption", "dispersion",
                "vampiric_embrace", "silence", "shadow_crash", "purify_disease");
        kit.passive(DISCIPLINE, "atonement_caster");
        kit.passive(HOLY_PRIEST, "holy_priest_basics");
        kit.passive(SHADOW, "shadow_basics");
        kit.bar(DISCIPLINE, "power_word_shield", "penance", "flash_heal", "power_word_radiance", "smite", "shadow_word_pain", "mind_blast",
                "purify", "pain_suppression", "power_word_barrier", "rapture", "desperate_prayer");
        kit.bar(HOLY_PRIEST, "flash_heal", "heal", "renew", "prayer_of_mending", "prayer_of_healing", "holy_word_serenity", "holy_word_sanctify",
                "purify", "guardian_spirit", "divine_hymn", "power_word_shield", "smite");
        kit.bar(SHADOW, "vampiric_touch", "shadow_word_pain", "mind_blast", "mind_flay", "devouring_plague", "shadow_word_death",
                "void_eruption", "silence", "shadow_crash", "dispersion", "power_word_shield", "vampiric_embrace");

        kit.rotation(DISCIPLINE, of(
                self("desperate_prayer").when(selfHealthBelow(0.4)).urgent(),
                heal("pain_suppression").below(0.25).urgent(),
                dispel("purify"),
                heal("power_word_shield").below(0.9),
                heal("penance").below(0.5),
                heal("power_word_radiance").below(0.75),
                heal("flash_heal").below(0.6),
                use("shadow_word_pain").when(Cond.targetHasAura("shadow_word_pain").not()),
                use("mind_blast"),
                use("penance"),
                use("smite")));
        kit.rotation(HOLY_PRIEST, of(
                self("desperate_prayer").when(selfHealthBelow(0.4)).urgent(),
                heal("guardian_spirit").below(0.2).urgent(),
                dispel("purify"),
                heal("holy_word_serenity").below(0.45),
                heal("prayer_of_mending").below(0.95),
                heal("flash_heal").below(0.5),
                heal("prayer_of_healing").below(0.75),
                heal("renew").below(0.85),
                heal("heal").below(0.8),
                use("holy_fire"),
                use("smite")));
        kit.rotation(SHADOW, of(
                self("dispersion").when(selfHealthBelow(0.25)).urgent(),
                self("desperate_prayer").when(selfHealthBelow(0.45)).urgent(),
                self("shadowform").when(Cond.hasAura("shadowform").not()),
                interrupt("silence"),
                use("void_eruption"),
                use("vampiric_touch").when(Cond.targetHasAura("vampiric_touch").not()),
                use("shadow_word_pain").when(Cond.targetHasAura("shadow_word_pain").not()),
                use("devouring_plague").when(resourceAtLeast(INSANITY, 50)),
                use("shadow_word_death"),
                use("mind_blast"),
                use("shadow_crash").on(com.wowcraft.core.bot.BotTarget.ENEMY_GROUND),
                use("mind_flay")));

        // ------------------------------------------------------------ talents
        classTree(
                t("improved_flash_heal", 0, 0, "Improved Flash Heal", "Улучшенное быстрое исцеление").mod(Modifier.abilityHealing("flash_heal", 0.15)),
                t("body_and_soul", 0, 1, "Body and Soul", "Тело и душа").aura("body_and_soul"),
                t("angelic_bulwark", 0, 2, "Angelic Bulwark", "Ангельский бастион").mod(Modifier.maxHealth(0.05)),
                t("halo_talent", 1, 0, "Halo", "Сияние").grant("halo"),
                t("twist_of_fate", 1, 1, "Twist of Fate", "Превратность судьбы").mod(Modifier.of(ModType.EXECUTE_DAMAGE, 0.1), Modifier.healing(0.03)),
                t("mass_dispel", 1, 2, "Mass Dispel", "Массовое рассеивание").grant("mass_resurrection"),
                t("translucent_image", 2, 0, "Translucent Image", "Призрачный образ").mod(Modifier.cooldown("fade", -10)),
                t("crystalline_reflection", 2, 1, "Crystalline Reflection", "Кристаллическое отражение").mod(Modifier.of(ModType.ABSORB_DONE, 0.2)),
                t("power_infusion_mastery", 2, 2, "Twins of the Sun Priestess", "Близнецы жрицы солнца").mod(Modifier.cooldown("power_infusion", -30)));
        reg(passive("body_and_soul", "Body and Soul", "Тело и душа").desc("Power Word: Shield increases your movement speed by 40% for 3 sec.",
                        "«Слово силы: Щит» увеличивает скорость передвижения на 40% на 3 сек.")
                .trigger(Trigger.on(TriggerType.CAST, aura("body_and_soul_speed")).filter(ModFilter.ability("power_word_shield"))));
        reg(buff("body_and_soul_speed", "Body and Soul", "Тело и душа").duration(3).mod(Modifier.speed(0.4)));
        specTree(DISCIPLINE,
                t("castigation", 0, 0, "Castigation", "Наказание").mod(Modifier.abilityDamage("penance", 0.2), Modifier.abilityHealing("penance", 0.2)),
                t("shield_discipline", 0, 1, "Shield Discipline", "Дисциплина щита").mod(Modifier.of(ModType.ABSORB_DONE, 0.15)),
                t("bright_pupil", 0, 2, "Bright Pupil", "Прилежный ученик").mod(Modifier.cooldown("power_word_radiance", -4)),
                t("abyssal_reverie", 1, 0, "Abyssal Reverie", "Грезы бездны").mod(Modifier.schoolDamage(School.SHADOW, 0.15)),
                t("pain_and_suffering", 1, 1, "Pain and Suffering", "Боль и страдания").mod(Modifier.of(ModType.DAMAGE_DONE, ModFilter.aura("shadow_word_pain"), 0.3)),
                t("protector_of_the_frail", 1, 2, "Protector of the Frail", "Защитник слабых").mod(Modifier.cooldown("pain_suppression", -60)),
                t("evangelism", 2, 0, "Evangelism", "Благовестие").mod(Modifier.duration("atonement", 5)),
                t("aegis_of_wrath", 2, 1, "Aegis of Wrath", "Эгида гнева").mod(Modifier.of(ModType.ABSORB_DONE, 0.1), Modifier.cost("power_word_shield", -0.3)),
                t("ultimate_penitence", 2, 2, "Ultimate Penitence", "Абсолютное покаяние").mod(Modifier.charges("penance", 1)));
        specTree(HOLY_PRIEST,
                t("trail_of_light", 0, 0, "Trail of Light", "След света").mod(Modifier.abilityHealing("flash_heal", 0.1), Modifier.abilityHealing("heal", 0.1)),
                t("benediction", 0, 1, "Benediction", "Благословение").mod(Modifier.duration("renew", 6)),
                t("empowered_renew", 0, 2, "Empowered Renew", "Усиленное обновление").mod(Modifier.of(ModType.HEALING_DONE, ModFilter.aura("renew"), 0.3)),
                t("prayerful_litany", 1, 0, "Prayerful Litany", "Молитвенная литания").mod(Modifier.abilityHealing("prayer_of_healing", 0.2)),
                t("light_of_the_naaru", 1, 1, "Light of the Naaru", "Свет наару").mod(Modifier.cooldown("holy_word_serenity", -15), Modifier.cooldown("holy_word_sanctify", -15)),
                t("guardian_angel", 1, 2, "Guardian Angel", "Ангел-хранитель").mod(Modifier.cooldown("guardian_spirit", -60)),
                t("apotheosis", 2, 0, "Apotheosis", "Апофеоз").mod(Modifier.healing(0.06)),
                t("divine_word", 2, 1, "Divine Word", "Божественное слово").mod(Modifier.abilityHealing("holy_word_serenity", 0.3)),
                t("symbol_of_hope", 2, 2, "Symbol of Hope", "Символ надежды").mod(Modifier.regen(MANA.name(), 0.25)));
        specTree(SHADOW,
                t("misery", 0, 0, "Misery", "Мучение").mod(Modifier.duration("vampiric_touch", 3), Modifier.duration("shadow_word_pain", 3)),
                t("intangibility", 0, 1, "Intangibility", "Бесплотность").mod(Modifier.cooldown("dispersion", -30)),
                t("auspicious_spirits", 0, 2, "Auspicious Spirits", "Благие духи").mod(Modifier.tagDamage("shadow_dot", 0.1)),
                t("mind_melt", 1, 0, "Mind Melt", "Расплавление разума").mod(Modifier.abilityCrit("mind_blast", 15)),
                t("shadowy_insight", 1, 1, "Shadowy Insight", "Прозрение Тьмы").mod(Modifier.charges("mind_blast", 1)),
                t("psychic_link", 1, 2, "Psychic Link", "Психическая связь").mod(Modifier.abilityDamage("mind_flay", 0.2)),
                t("mindbender", 2, 0, "Mindbender", "Подчинитель разума").mod(Modifier.regen(INSANITY.name(), 0.0), Modifier.abilityDamage("devouring_plague", 0.2)),
                t("void_torrent", 2, 1, "Void Torrent", "Поток Бездны").mod(Modifier.duration("voidform", 5)),
                t("deathspeaker", 2, 2, "Deathspeaker", "Глашатай смерти").mod(Modifier.abilityDamage("shadow_word_death", 0.4), Modifier.cooldown("shadow_word_death", -3)));
        hero(t("hero_oracle", 0, 0, "Oracle", "Оракул").mod(Modifier.healing(0.08), Modifier.haste(0.03)),
                t("hero_voidweaver", 0, 1, "Voidweaver", "Ткач Бездны").mod(Modifier.schoolDamage(School.SHADOW, 0.1)));
    }

    private static com.wowcraft.core.spell.Effect atonementHeal() {
        return custom("Damage heals allies with Atonement for 40%", "Урон исцеляет союзников с «Искуплением вины» на 40%", ctx -> {
            if (ctx.triggerAmount <= 0) return;
            for (UnitState u : ctx.engine.units()) {
                if (!u.isAlive() || u.auras().get("atonement", ctx.caster) == null) continue;
                EffectContext hc = new EffectContext(ctx.engine, ctx.caster, u, null, null, ctx.aura);
                ctx.engine.heal(hc, u, ctx.triggerAmount * 0.4, true);
            }
        });
    }
}
