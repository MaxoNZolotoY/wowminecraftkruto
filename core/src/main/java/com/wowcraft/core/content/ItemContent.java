package com.wowcraft.core.content;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.aura.Trigger;
import com.wowcraft.core.aura.TriggerType;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.item.EquipType;
import com.wowcraft.core.item.ItemEffect;
import com.wowcraft.core.item.ItemRegistry;
import com.wowcraft.core.item.LootGenerator;
import com.wowcraft.core.item.TierSet;
import com.wowcraft.core.mod.ModFilter;
import com.wowcraft.core.mod.ModType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.util.L10n;

import java.util.List;

/** Trinket / item effects and class tier sets. */
public final class ItemContent {
    private ItemContent() {
    }

    private static final int BIG = 1_000_000;

    public static void register() {
        // ------------------------------------------------------------ stat buff auras (stacks = magnitude)
        Registry.register(AuraDef.buff("trinket_primary_buff", "Surge of Power", "Прилив силы").duration(20).stacks(BIG).noRefresh()
                .mod(Modifier.statFlat(Stat.STRENGTH, 1), Modifier.statFlat(Stat.AGILITY, 1), Modifier.statFlat(Stat.INTELLECT, 1)).vfx("trinket").build());
        Registry.register(AuraDef.buff("trinket_haste_buff", "Quickened Sands", "Ускоренные пески").duration(15).stacks(BIG).noRefresh()
                .mod(Modifier.statFlat(Stat.HASTE, 1)).vfx("trinket").build());
        Registry.register(AuraDef.buff("trinket_crit_buff", "Ember Rage", "Ярость углей").duration(10).stacks(BIG).noRefresh()
                .mod(Modifier.statFlat(Stat.CRIT, 1)).build());
        Registry.register(AuraDef.buff("trinket_pvp_primary", "Gladiator's Resolve", "Решимость гладиатора").duration(15).stacks(BIG).noRefresh()
                .mod(Modifier.statFlat(Stat.STRENGTH, 1), Modifier.statFlat(Stat.AGILITY, 1), Modifier.statFlat(Stat.INTELLECT, 1)).build());
        Registry.register(AuraDef.buff("trinket_mastery_buff", "Crystalline Focus", "Кристальная сосредоточенность").duration(12).stacks(BIG).noRefresh()
                .mod(Modifier.statFlat(Stat.MASTERY, 1)).build());
        Registry.register(AuraDef.buff("trinket_absorb", "Mountain's Ward", "Оберег горы").duration(15).absorb().build());

        // ------------------------------------------------------------ on-use effects
        onUse("idol_of_fury", "Idol of Fury", "Идол ярости", "Use: Increases your primary stat by {X} for 20 sec.",
                "Использование: увеличивает основную характеристику на {X} на 20 сек.", 120, 0.27, "trinket_primary_buff");
        onUse("hourglass_of_haste", "Hourglass of Haste", "Песочные часы скорости", "Use: Grants {X} Haste for 15 sec.",
                "Использование: увеличивает показатель скорости на {X} на 15 сек.", 90, 0.4, "trinket_haste_buff");
        onUse("gladiators_badge", "Gladiator's Badge", "Знак гладиатора", "Use: Increases your primary stat by {X} for 15 sec.",
                "Использование: увеличивает основную характеристику на {X} на 15 сек.", 120, 0.22, "trinket_pvp_primary");
        onUse("prism_of_focus", "Prism of Focus", "Призма сосредоточенности", "Use: Grants {X} Mastery for 12 sec.",
                "Использование: увеличивает показатель искусности на {X} на 12 сек.", 60, 0.35, "trinket_mastery_buff");
        // absorb on-use
        Registry.register(Ability.builder("item_heart_of_the_mountain", "Heart of the Mountain", "Сердце горы").target(TargetType.SELF)
                .offGcd().cooldown(90).hidden().tag("defensive", "item")
                .effect(Effects.custom("Absorbs damage", "Поглощает урон", ctx -> {
                    int mag = magnitude(ctx.caster, "heart_of_the_mountain");
                    ctx.engine.applyAbsorb(ctx.withTarget(ctx.caster), ctx.caster, "trinket_absorb", mag);
                })).vfx("trinket").build());
        ItemRegistry.register(new ItemEffect("heart_of_the_mountain", L10n.of("Heart of the Mountain", "Сердце горы"),
                L10n.of("Use: Absorbs {X} damage for 15 sec.", "Использование: поглощает {X} ед. урона в течение 15 сек."),
                ItemEffect.Kind.ON_USE, "item_heart_of_the_mountain", null, 5.0));
        // damage on-use
        Registry.register(Ability.builder("item_shard_of_annihilation", "Shard of Annihilation", "Осколок аннигиляции").range(40)
                .offGcd().cooldown(90).hidden().tag("cooldown", "item").school(School.SHADOW)
                .effect(Effects.custom("Deals Shadow damage", "Наносит урон от темной магии", ctx -> {
                    if (ctx.target == null) return;
                    ctx.engine.dealDamage(ctx, ctx.target, School.SHADOW, magnitude(ctx.caster, "shard_of_annihilation"));
                })).vfx("shadow_burst").build());
        ItemRegistry.register(new ItemEffect("shard_of_annihilation", L10n.of("Shard of Annihilation", "Осколок аннигиляции"),
                L10n.of("Use: Deals {X} Shadow damage to your target.", "Использование: наносит цели {X} ед. урона от темной магии."),
                ItemEffect.Kind.ON_USE, "item_shard_of_annihilation", null, 7.0));

        // ------------------------------------------------------------ passive effects
        passive("void_shard", "Shard of the Void", "Осколок Бездны",
                "Equip: Your attacks have a chance to deal {X} Shadow damage.", "Если на персонаже: ваши атаки могут нанести {X} ед. урона от темной магии.",
                1.1, Trigger.on(TriggerType.DAMAGE_DEALT, procDamage(School.SHADOW, false)).chance(0.15).icd(1.5));
        passive("storm_totem", "Totem of Storms", "Тотем бурь",
                "Equip: Your attacks have a chance to deal {X} Nature damage to all enemies near the target.",
                "Если на персонаже: ваши атаки могут нанести {X} ед. урона от сил природы всем противникам рядом с целью.",
                0.7, Trigger.on(TriggerType.DAMAGE_DEALT, procDamage(School.NATURE, true)).chance(0.1).icd(2));
        passive("ember_of_rage", "Ember of Rage", "Уголек гнева",
                "Equip: Your attacks have a chance to grant {X} critical strike for 10 sec.",
                "Если на персонаже: ваши атаки могут увеличить показатель крит. удара на {X} на 10 сек.",
                0.25, Trigger.on(TriggerType.DAMAGE_DEALT, procBuff("trinket_crit_buff")).chance(0.12).icd(20));
        passive("chalice_of_renewal", "Chalice of Renewal", "Чаша обновления",
                "Equip: Your heals have a chance to heal the target for an additional {X}.",
                "Если на персонаже: ваши исцеляющие заклинания могут восполнить цели еще {X} ед. здоровья.",
                1.4, Trigger.on(TriggerType.HEAL_DEALT, procHeal()).chance(0.15).icd(1.5));
        passive("gladiators_insignia", "Gladiator's Insignia", "Эмблема гладиатора",
                "Equip: Your attacks have a chance to increase your primary stat by {X} for 15 sec.",
                "Если на персонаже: ваши атаки могут увеличить основную характеристику на {X} на 15 сек.",
                0.18, Trigger.on(TriggerType.DAMAGE_DEALT, procBuff("trinket_pvp_primary")).chance(0.1).icd(45));
        // static stat trinkets
        Registry.register(AuraDef.passive("item_ward_of_ages", "Ward of Ages", "Оберег веков").stacks(BIG).mod(Modifier.statFlat(Stat.STAMINA, 1)).build());
        ItemRegistry.register(new ItemEffect("ward_of_ages", L10n.of("Ward of Ages", "Оберег веков"),
                L10n.of("Equip: Increases Stamina by {X}.", "Если на персонаже: выносливость увеличена на {X}."),
                ItemEffect.Kind.PASSIVE, null, "item_ward_of_ages", 0.25));
        Registry.register(AuraDef.passive("item_scroll_of_wisdom", "Scroll of Wisdom", "Свиток мудрости").stacks(BIG)
                .mod(Modifier.statFlat(Stat.INTELLECT, 1)).build());
        ItemRegistry.register(new ItemEffect("scroll_of_wisdom", L10n.of("Scroll of Wisdom", "Свиток мудрости"),
                L10n.of("Equip: Increases Intellect by {X}.", "Если на персонаже: интеллект увеличен на {X}."),
                ItemEffect.Kind.PASSIVE, null, "item_scroll_of_wisdom", 0.08));
        Registry.register(AuraDef.passive("item_fang_of_the_beast", "Fang of the Beast", "Клык зверя").stacks(BIG)
                .mod(Modifier.statFlat(Stat.AGILITY, 1), Modifier.statFlat(Stat.STRENGTH, 1)).build());
        ItemRegistry.register(new ItemEffect("fang_of_the_beast", L10n.of("Fang of the Beast", "Клык зверя"),
                L10n.of("Equip: Increases Strength and Agility by {X}.", "Если на персонаже: сила и ловкость увеличены на {X}."),
                ItemEffect.Kind.PASSIVE, null, "item_fang_of_the_beast", 0.08));

        registerTierSets();
    }

    static int magnitude(UnitState u, String effectId) {
        Object o = u.tags.get("item_effect:" + effectId);
        return o instanceof Number n ? n.intValue() : 1;
    }

    private static void onUse(String id, String en, String ru, String descEn, String descRu, double cd, double k, String auraId) {
        String abilityId = "item_" + id;
        Registry.register(Ability.builder(abilityId, en, ru).target(TargetType.SELF).offGcd().cooldown(cd).hidden().tag("cooldown", "item")
                .usableWhileCasting().effect(Effects.custom(descEn.replace("{X}", "X"), descRu.replace("{X}", "X"), ctx ->
                        ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, auraId, magnitude(ctx.caster, id), -1))).vfx("trinket").build());
        ItemRegistry.register(new ItemEffect(id, L10n.of(en, ru), L10n.of(descEn, descRu), ItemEffect.Kind.ON_USE, abilityId, null, k));
    }

    private static void passive(String id, String en, String ru, String descEn, String descRu, double k, Trigger trigger) {
        String auraId = "item_" + id;
        Registry.register(AuraDef.passive(auraId, en, ru).stacks(BIG).flatMods().trigger(trigger).build());
        ItemRegistry.register(new ItemEffect(id, L10n.of(en, ru), L10n.of(descEn, descRu), ItemEffect.Kind.PASSIVE, null, auraId, k));
    }

    /** Proc damage equal to the passive aura's stack count. */
    private static Effect procDamage(School school, boolean aoe) {
        return ctx -> {
            if (ctx.aura == null || ctx.target == null || ctx.target == ctx.caster) return;
            double amount = ctx.aura.stacks;
            if (aoe) {
                for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.target.position(), 8)) {
                    ctx.engine.dealDamage(withAura(ctx, u), u, school, amount);
                }
            } else {
                ctx.engine.dealDamage(withAura(ctx, ctx.target), ctx.target, school, amount);
            }
            ctx.engine.vfx("item_proc", ctx.caster, ctx.target, null);
        };
    }

    private static EffectContext withAura(EffectContext ctx, UnitState t) {
        EffectContext c = new EffectContext(ctx.engine, ctx.caster, t, null, null, ctx.aura);
        c.schoolOverride = null;
        return c;
    }

    private static Effect procHeal() {
        return ctx -> {
            if (ctx.aura == null || ctx.target == null) return;
            ctx.engine.heal(new EffectContext(ctx.engine, ctx.caster, ctx.target, null, null, ctx.aura), ctx.target, ctx.aura.stacks, true);
        };
    }

    private static Effect procBuff(String buffAura) {
        return ctx -> {
            if (ctx.aura == null) return;
            AuraInstance existing = ctx.caster.auras().get(buffAura);
            if (existing != null) ctx.engine.removeAura(existing, false);
            ctx.engine.applyAura(ctx.withTarget(ctx.caster), ctx.caster, buffAura, ctx.aura.stacks, -1);
        };
    }

    // ------------------------------------------------------------------ tier sets

    private static void registerTierSets() {
        set(WowClass.WARRIOR, "tier_warrior_s1", "Warplate of the Crimson Siege", "Латы Багровой осады", "of the Crimson Siege", "Багровой осады",
                List.of(Modifier.abilityDamage("mortal_strike", 0.15), Modifier.abilityDamage("bloodthirst", 0.15), Modifier.abilityDamage("shield_slam", 0.15)),
                List.of(Modifier.damage(0.06), Modifier.cooldownPct("avatar", -0.2), Modifier.taken(-0.03)));
        set(WowClass.PALADIN, "tier_paladin_s1", "Radiant Lightforged Regalia", "Регалии Сияющих Озаренных", "of the Radiant Dawn", "Сияющей зари",
                List.of(Modifier.abilityDamage("templars_verdict", 0.15), Modifier.abilityHealing("holy_shock", 0.15), Modifier.abilityDamage("avengers_shield", 0.15)),
                List.of(Modifier.damage(0.05), Modifier.healing(0.05), Modifier.cooldownPct("avenging_wrath", -0.2)));
        set(WowClass.HUNTER, "tier_hunter_s1", "Lightless Scavenger's Necessities", "Снаряжение Темного падальщика", "of the Lightless Hunt", "Безлунной охоты",
                List.of(Modifier.abilityDamage("kill_command", 0.15), Modifier.abilityDamage("aimed_shot", 0.15), Modifier.abilityDamage("raptor_strike", 0.15)),
                List.of(Modifier.damage(0.05), Modifier.of(ModType.PET_DAMAGE, 0.08)));
        set(WowClass.ROGUE, "tier_rogue_s1", "K'areshi Phantom's Bindings", "Обмотки К'арешского призрака", "of the Phantom", "Призрака",
                List.of(Modifier.tagDamage("finisher", 0.12)), List.of(Modifier.damage(0.06), Modifier.crit(3)));
        set(WowClass.PRIEST, "tier_priest_s1", "Living Luster's Vestments", "Облачение Живого сияния", "of Living Luster", "Живого сияния",
                List.of(Modifier.abilityHealing("flash_heal", 0.15), Modifier.abilityDamage("mind_blast", 0.15), Modifier.abilityHealing("penance", 0.15)),
                List.of(Modifier.healing(0.06), Modifier.damage(0.05)));
        set(WowClass.DEATH_KNIGHT, "tier_dk_s1", "Exhumed Centurion's Relics", "Реликвии Эксгумированного центуриона", "of the Exhumed Centurion",
                "Эксгумированного центуриона",
                List.of(Modifier.abilityDamage("obliterate", 0.15), Modifier.abilityDamage("scourge_strike", 0.15), Modifier.abilityHealing("death_strike", 0.15)),
                List.of(Modifier.damage(0.06), Modifier.taken(-0.03)));
        set(WowClass.SHAMAN, "tier_shaman_s1", "Waves of the Forgotten Reservoir", "Волны Забытого резервуара", "of the Forgotten Reservoir",
                "Забытого резервуара",
                List.of(Modifier.abilityDamage("lava_burst", 0.15), Modifier.abilityDamage("stormstrike", 0.15), Modifier.abilityHealing("riptide", 0.15)),
                List.of(Modifier.damage(0.05), Modifier.healing(0.05)));
        set(WowClass.MAGE, "tier_mage_s1", "Sparks of Violet Rebirth", "Искры Фиолетового возрождения", "of Violet Rebirth", "Фиолетового возрождения",
                List.of(Modifier.abilityDamage("arcane_blast", 0.12), Modifier.abilityDamage("fireball", 0.12), Modifier.abilityDamage("frostbolt", 0.12)),
                List.of(Modifier.damage(0.06), Modifier.crit(3)));
        set(WowClass.WARLOCK, "tier_warlock_s1", "Rites of the Hexflame Coven", "Обряды Ковена Порчепламени", "of the Hexflame Coven", "Ковена Порчепламени",
                List.of(Modifier.abilityDamage("malefic_rapture", 0.15), Modifier.abilityDamage("hand_of_guldan", 0.15), Modifier.abilityDamage("chaos_bolt", 0.15)),
                List.of(Modifier.damage(0.05), Modifier.of(ModType.PET_DAMAGE, 0.08)));
        set(WowClass.MONK, "tier_monk_s1", "Gatecrasher's Fortitude", "Стойкость Сокрушителя врат", "of the Gatecrasher", "Сокрушителя врат",
                List.of(Modifier.abilityDamage("rising_sun_kick", 0.15), Modifier.abilityDamage("keg_smash", 0.15), Modifier.abilityHealing("vivify", 0.15)),
                List.of(Modifier.damage(0.05), Modifier.healing(0.05), Modifier.taken(-0.03)));
        set(WowClass.DRUID, "tier_druid_s1", "Mane of the Greatlynx", "Грива Великой рыси", "of the Greatlynx", "Великой рыси",
                List.of(Modifier.abilityDamage("starsurge", 0.15), Modifier.abilityDamage("rip", 0.15), Modifier.abilityDamage("mangle", 0.15),
                        Modifier.abilityHealing("rejuvenation", 0.15)),
                List.of(Modifier.damage(0.05), Modifier.healing(0.05)));
        set(WowClass.DEMON_HUNTER, "tier_dh_s1", "Husk of the Hypogeal Nemesis", "Оболочка Подземного возмездия", "of the Hypogeal Nemesis",
                "Подземного возмездия",
                List.of(Modifier.abilityDamage("chaos_strike", 0.15), Modifier.abilityDamage("annihilation", 0.15), Modifier.abilityDamage("soul_cleave", 0.15)),
                List.of(Modifier.damage(0.06), Modifier.leech(2)));
        set(WowClass.EVOKER, "tier_evoker_s1", "Destroyer's Scarred Wards", "Изуродованные обереги Разрушителя", "of the Scarred Destroyer",
                "Изуродованного разрушителя",
                List.of(Modifier.abilityDamage("disintegrate", 0.15), Modifier.abilityHealing("echo", 0.15), Modifier.abilityDamage("eruption", 0.15)),
                List.of(Modifier.damage(0.05), Modifier.healing(0.05)));
    }

    private static void set(WowClass c, String id, String en, String ru, String suffixEn, String suffixRu, List<Modifier> two, List<Modifier> four) {
        L10n[] names = new L10n[TierSet.PIECES.length];
        for (int i = 0; i < names.length; i++) {
            EquipType p = TierSet.PIECES[i];
            L10n base = LootGenerator.baseName(p, c.armor, null);
            names[i] = L10n.of(base.en() + " " + suffixEn, base.ru() + " " + suffixRu);
        }
        ItemRegistry.register(new TierSet(id, c, L10n.of(en, ru), names, two, four, null, null,
                L10n.of(com.wowcraft.core.talent.ModDescriber.describeAll(two, null, null, L10n.Lang.EN),
                        com.wowcraft.core.talent.ModDescriber.describeAll(two, null, null, L10n.Lang.RU)),
                L10n.of(com.wowcraft.core.talent.ModDescriber.describeAll(four, null, null, L10n.Lang.EN),
                        com.wowcraft.core.talent.ModDescriber.describeAll(four, null, null, L10n.Lang.RU))));
    }
}
