package com.wowcraft.core.item;

import com.wowcraft.core.spec.ArmorType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Rng;

import java.util.ArrayList;
import java.util.List;

/** Creates items: personal loot rolls, named templates, procedural items, tier pieces. */
public final class LootGenerator {
    private LootGenerator() {
    }

    private static final EquipType[] ARMOR_TYPES = {EquipType.HEAD, EquipType.SHOULDER, EquipType.CHEST, EquipType.WRIST, EquipType.HANDS,
            EquipType.WAIST, EquipType.LEGS, EquipType.FEET};

    /** Builds an item from a template for a looter. */
    public static ItemData fromTemplate(ItemTemplate t, Spec looter, int ilvl, Rng rng, String source) {
        ItemData d = new ItemData();
        d.id = t.id();
        d.name = t.name().en();
        d.nameRu = t.name().ru();
        d.equipType = t.equipType().name();
        d.weaponType = t.weaponType() != null ? t.weaponType().name() : null;
        if (t.equipType().slots.get(0).armorSlot) {
            d.armorType = ItemTemplate.ADAPTIVE.equals(t.armor()) ? looter.wowClass.armor.name() : t.armor();
        } else {
            d.armorType = ArmorType.NONE.name();
        }
        d.primary = t.primary();
        if (t.secondaries() != null && t.secondaries().length > 0) {
            for (String s : t.secondaries()) d.secondaries.add(s);
        } else {
            randomSecondaries(d, rng);
        }
        d.effectId = t.effectId();
        if (t.flavor() != null) {
            d.flavor = t.flavor().en();
            d.flavorRu = t.flavor().ru();
        }
        finish(d, ilvl, rng, source);
        return d;
    }

    /** A procedurally named item of the given type. */
    public static ItemData generate(Spec looter, EquipType type, int ilvl, Rng rng, String source, String theme) {
        ItemData d = new ItemData();
        d.equipType = type.name();
        ArmorType at = type.slots.get(0).armorSlot ? looter.wowClass.armor : ArmorType.NONE;
        d.armorType = at.name();
        WeaponType wt = null;
        if (type.isWeapon() || type == EquipType.OFF_HAND || type == EquipType.SHIELD) {
            List<WeaponType> options = new ArrayList<>();
            for (WeaponType w : Proficiency.preferred(looter)) {
                if (type == EquipType.TWO_HAND && w.equipType == EquipType.TWO_HAND) options.add(w);
                else if (type == EquipType.RANGED && w.equipType == EquipType.RANGED) options.add(w);
                else if (type == EquipType.ONE_HAND && w.equipType == EquipType.ONE_HAND) options.add(w);
                else if ((type == EquipType.OFF_HAND || type == EquipType.SHIELD) && (w == WeaponType.SHIELD || w == WeaponType.OFF_HAND_HELD))
                    options.add(w);
            }
            if (options.isEmpty()) options.addAll(Proficiency.preferred(looter));
            wt = rng.pick(options);
            if (wt != null) {
                d.weaponType = wt.name();
                d.equipType = wt.equipType.name();
            }
            d.primary = looter.primaryStat.name();
        } else if (type == EquipType.NECK || type == EquipType.FINGER) {
            d.primary = null;
        } else if (type == EquipType.TRINKET) {
            d.primary = ItemData.ADAPTIVE;
        } else {
            d.primary = ItemData.ADAPTIVE;
        }
        randomSecondaries(d, rng);
        L10n base = baseName(EquipType.byName(d.equipType), at, wt);
        L10n suffix = suffix(theme, rng);
        d.name = base.en() + " " + suffix.en();
        d.nameRu = base.ru() + " " + suffix.ru();
        if (rng.chance(0.08)) d.tertiary = rng.pick(new String[]{"LEECH", "AVOIDANCE", "SPEED"});
        if ((type == EquipType.NECK || type == EquipType.FINGER) && rng.chance(0.25)) d.sockets = 1;
        finish(d, ilvl, rng, source);
        return d;
    }

    /** A tier set piece for the looter's class. */
    public static ItemData tierPiece(Spec looter, EquipType piece, int ilvl, Rng rng, String source) {
        TierSet set = ItemRegistry.setFor(looter.wowClass);
        ItemData d = generate(looter, piece, ilvl, rng, source, "tier");
        if (set != null) {
            d.setId = set.id();
            int idx = 0;
            for (int i = 0; i < TierSet.PIECES.length; i++) if (TierSet.PIECES[i] == piece) idx = i;
            d.name = set.pieceNames()[idx].en();
            d.nameRu = set.pieceNames()[idx].ru();
            d.id = set.id() + "_" + piece.name().toLowerCase();
        }
        return d;
    }

    /** Personal loot from a loot table: a template the looter can use, or a generated item. */
    public static ItemData personalLoot(Spec looter, String lootTableId, int ilvl, Rng rng, String source, String theme, double tierChance) {
        if (tierChance > 0 && rng.chance(tierChance)) {
            return tierPiece(looter, rng.pick(TierSet.PIECES), ilvl, rng, source);
        }
        List<ItemTemplate> usable = new ArrayList<>();
        for (String id : ItemRegistry.lootTable(lootTableId)) {
            ItemTemplate t = ItemRegistry.template(id);
            if (t != null && usableBy(t, looter)) usable.add(t);
        }
        if (!usable.isEmpty() && rng.chance(0.7)) return fromTemplate(rng.pick(usable), looter, ilvl, rng, source);
        EquipType type = randomSlotFor(looter, rng);
        return generate(looter, type, ilvl, rng, source, theme);
    }

    public static boolean usableBy(ItemTemplate t, Spec s) {
        if (!t.suitsRole(s.role)) return false;
        if (t.weaponType() != null) {
            if (!Proficiency.preferred(s).contains(t.weaponType())) return false;
        }
        if (t.primary() != null && !ItemTemplate.ADAPTIVE.equals(t.primary()) && t.equipType() != EquipType.TRINKET) {
            if (!t.primary().equals(s.primaryStat.name())) return false;
        }
        if (t.equipType().slots.get(0).armorSlot && !ItemTemplate.ADAPTIVE.equals(t.armor())) {
            return t.armor().equals(s.wowClass.armor.name());
        }
        return true;
    }

    public static EquipType randomSlotFor(Spec s, Rng rng) {
        double r = rng.nextDouble();
        if (r < 0.55) return ARMOR_TYPES[rng.nextInt(ARMOR_TYPES.length)];
        if (r < 0.65) return EquipType.BACK;
        if (r < 0.72) return EquipType.NECK;
        if (r < 0.80) return EquipType.FINGER;
        if (r < 0.87) return EquipType.TRINKET;
        // weapon
        boolean twoHanded = false, ranged = false;
        for (WeaponType w : Proficiency.preferred(s)) {
            if (w.equipType == EquipType.TWO_HAND) twoHanded = true;
            if (w.equipType == EquipType.RANGED) ranged = true;
        }
        if (ranged) return EquipType.RANGED;
        if (twoHanded && rng.chance(0.7)) return EquipType.TWO_HAND;
        if (rng.chance(0.3)) {
            for (WeaponType w : Proficiency.preferred(s)) if (w == WeaponType.SHIELD) return EquipType.SHIELD;
            for (WeaponType w : Proficiency.preferred(s)) if (w == WeaponType.OFF_HAND_HELD) return EquipType.OFF_HAND;
        }
        return twoHanded && !hasOneHand(s) ? EquipType.TWO_HAND : EquipType.ONE_HAND;
    }

    private static boolean hasOneHand(Spec s) {
        for (WeaponType w : Proficiency.preferred(s)) if (w.equipType == EquipType.ONE_HAND) return true;
        return false;
    }

    private static void randomSecondaries(ItemData d, Rng rng) {
        List<Stat> pool = new ArrayList<>(List.of(Stat.SECONDARIES));
        Stat a = pool.remove(rng.nextInt(pool.size()));
        Stat b = pool.remove(rng.nextInt(pool.size()));
        d.secondaries.clear();
        d.secondaries.add(a.name());
        d.secondaries.add(b.name());
        d.split = 0.5 + (rng.nextDouble() - 0.5) * 0.3;
    }

    private static void finish(ItemData d, int ilvl, Rng rng, String source) {
        UpgradeTrack track = UpgradeTrack.forItemLevel(ilvl);
        if (ilvl >= UpgradeTrack.EXPLORER.baseItemLevel) {
            d.track = track.name();
            d.rank = track.rankFor(ilvl);
            d.ilvl = track.itemLevel(d.rank);
        } else {
            d.ilvl = Math.max(1, ilvl);
        }
        d.quality = ItemQuality.forItemLevel(d.ilvl).name();
        d.requiredLevel = Math.max(1, Math.min(80, d.ilvl < 100 ? d.ilvl - 20 : 80));
        d.source = source;
        d.seed = rng.nextLong();
    }

    // ------------------------------------------------------------------ naming

    public static L10n baseName(EquipType t, ArmorType a, WeaponType w) {
        if (w != null) return switch (w) {
            case SWORD_1H -> L10n.of("Blade", "Клинок");
            case SWORD_2H -> L10n.of("Greatsword", "Двуручный меч");
            case AXE_1H -> L10n.of("Hatchet", "Топорик");
            case AXE_2H -> L10n.of("Greataxe", "Секира");
            case MACE_1H -> L10n.of("Mace", "Булава");
            case MACE_2H -> L10n.of("Maul", "Молот");
            case DAGGER -> L10n.of("Dagger", "Кинжал");
            case FIST -> L10n.of("Claws", "Когти");
            case WARGLAIVE -> L10n.of("Warglaive", "Боевой клинок");
            case POLEARM -> L10n.of("Halberd", "Алебарда");
            case STAFF -> L10n.of("Staff", "Посох");
            case BOW -> L10n.of("Longbow", "Длинный лук");
            case GUN -> L10n.of("Rifle", "Ружье");
            case WAND -> L10n.of("Wand", "Жезл");
            case SHIELD -> L10n.of("Bulwark", "Щит");
            case OFF_HAND_HELD -> L10n.of("Tome", "Фолиант");
        };
        if (t == null) return L10n.of("Item", "Предмет");
        return switch (t) {
            case HEAD -> pick(a, "Hood", "Капюшон", "Mask", "Маска", "Coif", "Койф", "Greathelm", "Большой шлем");
            case SHOULDER -> pick(a, "Mantle", "Мантия", "Shoulderpads", "Наплечники", "Spaulders", "Наплечье", "Pauldrons", "Наплечники");
            case CHEST -> pick(a, "Robe", "Одеяние", "Tunic", "Туника", "Hauberk", "Хауберк", "Breastplate", "Кираса");
            case WRIST -> pick(a, "Cuffs", "Манжеты", "Bracers", "Наручи", "Wristguards", "Напульсники", "Vambraces", "Наручи");
            case HANDS -> pick(a, "Gloves", "Перчатки", "Grips", "Рукавицы", "Gauntlets", "Рукавицы", "Gauntlets", "Латные рукавицы");
            case WAIST -> pick(a, "Sash", "Кушак", "Belt", "Ремень", "Girdle", "Пояс", "Waistplate", "Латный пояс");
            case LEGS -> pick(a, "Leggings", "Штаны", "Breeches", "Бриджи", "Legguards", "Поножи", "Legplates", "Латные поножи");
            case FEET -> pick(a, "Slippers", "Туфли", "Boots", "Сапоги", "Sabatons", "Сабатоны", "Greaves", "Латные сапоги");
            case NECK -> L10n.of("Pendant", "Подвеска");
            case BACK -> L10n.of("Cloak", "Плащ");
            case FINGER -> L10n.of("Band", "Кольцо");
            case TRINKET -> L10n.of("Talisman", "Талисман");
            case OFF_HAND -> L10n.of("Tome", "Фолиант");
            case SHIELD -> L10n.of("Bulwark", "Щит");
            default -> L10n.of("Weapon", "Оружие");
        };
    }

    private static L10n pick(ArmorType a, String clothEn, String clothRu, String leatherEn, String leatherRu, String mailEn, String mailRu,
                             String plateEn, String plateRu) {
        return switch (a) {
            case CLOTH -> L10n.of(clothEn, clothRu);
            case LEATHER -> L10n.of(leatherEn, leatherRu);
            case MAIL -> L10n.of(mailEn, mailRu);
            default -> L10n.of(plateEn, plateRu);
        };
    }

    private static final String[][] SUFFIXES = {
            {"of the Void", "Бездны"}, {"of the Fallen King", "Павшего короля"}, {"of Ashes", "Пепла"},
            {"of the Deeps", "Глубин"}, {"of Storms", "Бурь"}, {"of the Wild", "Дикой природы"},
            {"of the Eternal Night", "Вечной ночи"}, {"of the Ascendant", "Вознесенного"}, {"of Ruin", "Разрушения"},
            {"of the Sunwell", "Солнечного Колодца"}, {"of the Earthen", "Земельников"}, {"of Titanforge", "Кузни титанов"},
            {"of the Crimson Dawn", "Багровой зари"}, {"of Frozen Wastes", "Ледяных пустошей"}, {"of the Shattered Isles", "Расколотых островов"}
    };

    public static L10n suffix(String theme, Rng rng) {
        String[] s = SUFFIXES[rng.nextInt(SUFFIXES.length)];
        return L10n.of(s[0], s[1]);
    }
}
