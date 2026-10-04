package com.wowcraft.core.spec;

import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.List;

import static com.wowcraft.core.resource.ResourceType.*;
import static com.wowcraft.core.spec.MasteryKind.*;
import static com.wowcraft.core.spec.Role.*;
import static com.wowcraft.core.spec.WowClass.*;

/** All 39 specializations. */
public enum Spec {
    // Warrior
    ARMS(WARRIOR, "Arms", "Оружие", MELEE_DPS, Stat.STRENGTH, new ResourceType[]{RAGE},
            "Deep Wounds", "Глубокие раны", TAGGED_DAMAGE, "bleed", 1.4),
    FURY(WARRIOR, "Fury", "Неистовство", MELEE_DPS, Stat.STRENGTH, new ResourceType[]{RAGE},
            "Unshackled Fury", "Неудержимое неистовство", ALL_DAMAGE, null, 1.4),
    PROTECTION_WARRIOR(WARRIOR, "Protection", "Защита", TANK, Stat.STRENGTH, new ResourceType[]{RAGE},
            "Critical Block", "Критический блок", DEFENSIVE, null, 0.9),
    // Paladin
    HOLY_PALADIN(PALADIN, "Holy", "Свет", HEALER, Stat.INTELLECT, new ResourceType[]{MANA, HOLY_POWER},
            "Lightbringer", "Светоносец", LOW_HEALTH_HEALING, null, 1.5),
    PROTECTION_PALADIN(PALADIN, "Protection", "Защита", TANK, Stat.STRENGTH, new ResourceType[]{MANA, HOLY_POWER},
            "Divine Bulwark", "Божественный оплот", DEFENSIVE, null, 0.9),
    RETRIBUTION(PALADIN, "Retribution", "Воздаяние", MELEE_DPS, Stat.STRENGTH, new ResourceType[]{MANA, HOLY_POWER},
            "Hand of Light", "Длань Света", TAGGED_DAMAGE, "holy_power_spender", 1.6),
    // Hunter
    BEAST_MASTERY(HUNTER, "Beast Mastery", "Повелитель зверей", RANGED_DPS, Stat.AGILITY, new ResourceType[]{FOCUS},
            "Master of Beasts", "Укротитель", PET_DAMAGE, null, 2.0),
    MARKSMANSHIP(HUNTER, "Marksmanship", "Стрельба", RANGED_DPS, Stat.AGILITY, new ResourceType[]{FOCUS},
            "Sniper Training", "Снайперская подготовка", ALL_DAMAGE, null, 1.2),
    SURVIVAL(HUNTER, "Survival", "Выживание", MELEE_DPS, Stat.AGILITY, new ResourceType[]{FOCUS},
            "Spirit Bond", "Духовная связь", ALL_DAMAGE, null, 1.2),
    // Rogue
    ASSASSINATION(ROGUE, "Assassination", "Ликвидация", MELEE_DPS, Stat.AGILITY, new ResourceType[]{ENERGY, COMBO_POINTS},
            "Potent Assassin", "Сильнодействующие яды", TAGGED_DAMAGE, "poison", 2.0),
    OUTLAW(ROGUE, "Outlaw", "Головорез", MELEE_DPS, Stat.AGILITY, new ResourceType[]{ENERGY, COMBO_POINTS},
            "Main Gauche", "Мен-гош", ALL_DAMAGE, null, 1.3),
    SUBTLETY(ROGUE, "Subtlety", "Скрытность", MELEE_DPS, Stat.AGILITY, new ResourceType[]{ENERGY, COMBO_POINTS},
            "Executioner", "Палач", TAGGED_DAMAGE, "finisher", 1.8),
    // Priest
    DISCIPLINE(PRIEST, "Discipline", "Послушание", HEALER, Stat.INTELLECT, new ResourceType[]{MANA},
            "Grace", "Благодать", ABSORBS, null, 1.4),
    HOLY_PRIEST(PRIEST, "Holy", "Свет", HEALER, Stat.INTELLECT, new ResourceType[]{MANA},
            "Echo of Light", "Эхо Света", ALL_HEALING, null, 1.25),
    SHADOW(PRIEST, "Shadow", "Тьма", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{INSANITY, MANA},
            "Shadow Weaving", "Плетение теней", TAGGED_DAMAGE, "shadow_dot", 1.6),
    // Death Knight
    BLOOD(DEATH_KNIGHT, "Blood", "Кровь", TANK, Stat.STRENGTH, new ResourceType[]{RUNIC_POWER, RUNES},
            "Blood Shield", "Щит крови", DEFENSIVE, null, 1.0),
    FROST_DK(DEATH_KNIGHT, "Frost", "Лед", MELEE_DPS, Stat.STRENGTH, new ResourceType[]{RUNIC_POWER, RUNES},
            "Frozen Heart", "Ледяное сердце", TAGGED_DAMAGE, "frost", 1.8),
    UNHOLY(DEATH_KNIGHT, "Unholy", "Нечестивость", MELEE_DPS, Stat.STRENGTH, new ResourceType[]{RUNIC_POWER, RUNES},
            "Dreadblade", "Клинок ужаса", PET_DAMAGE, null, 1.8),
    // Shaman
    ELEMENTAL(SHAMAN, "Elemental", "Стихии", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MAELSTROM, MANA},
            "Elemental Overload", "Перегрузка стихий", ALL_DAMAGE, null, 1.4),
    ENHANCEMENT(SHAMAN, "Enhancement", "Совершенствование", MELEE_DPS, Stat.AGILITY, new ResourceType[]{MANA},
            "Enhanced Elements", "Улучшенные стихии", TAGGED_DAMAGE, "nature", 1.6),
    RESTORATION_SHAMAN(SHAMAN, "Restoration", "Исцеление", HEALER, Stat.INTELLECT, new ResourceType[]{MANA},
            "Deep Healing", "Глубокое исцеление", LOW_HEALTH_HEALING, null, 1.6),
    // Mage
    ARCANE(MAGE, "Arcane", "Тайная магия", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA, ARCANE_CHARGES},
            "Savant", "Ученый", TAGGED_DAMAGE, "arcane", 1.3),
    FIRE(MAGE, "Fire", "Огонь", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA},
            "Ignite", "Возгорание", TAGGED_DAMAGE, "fire", 1.5),
    FROST_MAGE(MAGE, "Frost", "Лед", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA},
            "Icicles", "Сосульки", TAGGED_DAMAGE, "frost", 1.5),
    // Warlock
    AFFLICTION(WARLOCK, "Affliction", "Колдовство", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA, SOUL_SHARDS},
            "Potent Afflictions", "Мощные чары", TAGGED_DAMAGE, "dot", 2.0),
    DEMONOLOGY(WARLOCK, "Demonology", "Демонология", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA, SOUL_SHARDS},
            "Master Demonologist", "Мастер-демонолог", PET_DAMAGE, null, 1.6),
    DESTRUCTION(WARLOCK, "Destruction", "Разрушение", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA, SOUL_SHARDS},
            "Chaotic Energies", "Хаотическая энергия", ALL_DAMAGE, null, 1.3),
    // Monk
    BREWMASTER(MONK, "Brewmaster", "Хмелевар", TANK, Stat.AGILITY, new ResourceType[]{ENERGY},
            "Elusive Brawler", "Неуловимый боец", DEFENSIVE, null, 1.0),
    MISTWEAVER(MONK, "Mistweaver", "Ткач туманов", HEALER, Stat.INTELLECT, new ResourceType[]{MANA},
            "Gust of Mists", "Порыв туманов", ALL_HEALING, null, 1.3),
    WINDWALKER(MONK, "Windwalker", "Танцующий с ветром", MELEE_DPS, Stat.AGILITY, new ResourceType[]{ENERGY, CHI},
            "Combo Strikes", "Серия ударов", ALL_DAMAGE, null, 1.2),
    // Druid
    BALANCE(DRUID, "Balance", "Баланс", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{ASTRAL_POWER, MANA},
            "Astral Invocation", "Астральное воззвание", TAGGED_DAMAGE, "dot", 1.4),
    FERAL(DRUID, "Feral", "Сила зверя", MELEE_DPS, Stat.AGILITY, new ResourceType[]{ENERGY, COMBO_POINTS},
            "Razor Claws", "Острые когти", TAGGED_DAMAGE, "bleed", 1.8),
    GUARDIAN(DRUID, "Guardian", "Страж", TANK, Stat.AGILITY, new ResourceType[]{RAGE},
            "Nature's Guardian", "Страж природы", DEFENSIVE, null, 1.0),
    RESTORATION_DRUID(DRUID, "Restoration", "Исцеление", HEALER, Stat.INTELLECT, new ResourceType[]{MANA},
            "Harmony", "Гармония", ALL_HEALING, null, 1.3),
    // Demon Hunter
    HAVOC(DEMON_HUNTER, "Havoc", "Истребление", MELEE_DPS, Stat.AGILITY, new ResourceType[]{ResourceType.FURY},
            "Demonic Presence", "Демоническое присутствие", ALL_DAMAGE, null, 1.4),
    VENGEANCE(DEMON_HUNTER, "Vengeance", "Месть", TANK, Stat.AGILITY, new ResourceType[]{ResourceType.FURY},
            "Fel Blood", "Кровь Скверны", DEFENSIVE, null, 1.0),
    // Evoker
    DEVASTATION(EVOKER, "Devastation", "Опустошение", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA, ESSENCE},
            "Giantkiller", "Убийца великанов", ALL_DAMAGE, null, 1.3),
    PRESERVATION(EVOKER, "Preservation", "Сохранение", HEALER, Stat.INTELLECT, new ResourceType[]{MANA, ESSENCE},
            "Life-Binder", "Хранитель жизни", ALL_HEALING, null, 1.3),
    AUGMENTATION(EVOKER, "Augmentation", "Насыщение", RANGED_DPS, Stat.INTELLECT, new ResourceType[]{MANA, ESSENCE},
            "Timewalker", "Странник во времени", ALL_DAMAGE, null, 1.2);

    public final WowClass wowClass;
    public final L10n name;
    public final Role role;
    public final Stat primaryStat;
    public final ResourceType[] resources;
    public final L10n masteryName;
    public final MasteryKind masteryKind;
    /** Ability tag affected by TAGGED_DAMAGE masteries. */
    public final String masteryTag;
    /** Mastery percent per mastery point. */
    public final double masteryCoef;

    Spec(WowClass c, String en, String ru, Role role, Stat primary, ResourceType[] resources,
         String mEn, String mRu, MasteryKind mk, String mTag, double mCoef) {
        this.wowClass = c;
        this.name = L10n.of(en, ru);
        this.role = role;
        this.primaryStat = primary;
        this.resources = resources;
        this.masteryName = L10n.of(mEn, mRu);
        this.masteryKind = mk;
        this.masteryTag = mTag;
        this.masteryCoef = mCoef;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public ResourceType primaryResource() {
        return resources[0];
    }

    public boolean isMelee() {
        return role == MELEE_DPS || role == TANK;
    }

    public static List<Spec> of(WowClass c) {
        List<Spec> list = new ArrayList<>();
        for (Spec s : values()) if (s.wowClass == c) list.add(s);
        return list;
    }

    public static Spec byId(String id) {
        if (id == null) return null;
        for (Spec s : values()) if (s.id().equalsIgnoreCase(id)) return s;
        return null;
    }
}
