package com.wowcraft.mc.item;

import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.item.EquipType;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.item.ItemEffect;
import com.wowcraft.core.item.ItemQuality;
import com.wowcraft.core.item.ItemRegistry;
import com.wowcraft.core.item.ItemStats;
import com.wowcraft.core.item.TierSet;
import com.wowcraft.core.item.UpgradeTrack;
import com.wowcraft.core.item.WeaponType;
import com.wowcraft.core.net.Protocol;
import com.wowcraft.core.spec.ArmorType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.stat.StatBlock;
import com.wowcraft.core.util.L10n;
import com.wowcraft.mc.WowCraftMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Registration of WoW items and conversion between {@link ItemData} and Minecraft item stacks. */
public final class WowItems {
    private WowItems() {
    }

    public static final String NBT_KEY = "wow";
    /** Armor items per armor type and vanilla armor slot. */
    private static final Map<ArmorType, Map<EquipSlot, Item>> ARMOR = new EnumMap<>(ArmorType.class);
    private static final Map<WeaponType, Item> WEAPONS = new EnumMap<>(WeaponType.class);
    private static final Map<EquipType, Item> ACCESSORIES = new EnumMap<>(EquipType.class);
    public static final Map<String, Item> ALL = new LinkedHashMap<>();
    public static Item TOKEN;

    /** Client hook: the local player's spec for tooltips (adaptive primary stats). */
    public static Supplier<Spec> tooltipSpec = () -> null;
    /** Client hook: language for item names. */
    public static Supplier<L10n.Lang> language = () -> L10n.Lang.EN;

    public static void register() {
        for (ArmorType at : new ArmorType[]{ArmorType.CLOTH, ArmorType.LEATHER, ArmorType.MAIL, ArmorType.PLATE}) {
            WowArmorMaterial mat = WowArmorMaterial.valueOf(at.name());
            Map<EquipSlot, Item> m = new EnumMap<>(EquipSlot.class);
            m.put(EquipSlot.HEAD, reg(at.name().toLowerCase() + "_head", armor(mat, ArmorItem.Type.HELMET)));
            m.put(EquipSlot.CHEST, reg(at.name().toLowerCase() + "_chest", armor(mat, ArmorItem.Type.CHESTPLATE)));
            m.put(EquipSlot.LEGS, reg(at.name().toLowerCase() + "_legs", armor(mat, ArmorItem.Type.LEGGINGS)));
            m.put(EquipSlot.FEET, reg(at.name().toLowerCase() + "_feet", armor(mat, ArmorItem.Type.BOOTS)));
            ARMOR.put(at, m);
        }
        for (WeaponType w : WeaponType.values()) WEAPONS.put(w, reg(w.name().toLowerCase(), new WowWeaponItem(new Item.Settings().maxCount(1))));
        for (EquipType t : new EquipType[]{EquipType.NECK, EquipType.SHOULDER, EquipType.BACK, EquipType.WRIST, EquipType.HANDS, EquipType.WAIST,
                EquipType.FINGER, EquipType.TRINKET}) {
            ACCESSORIES.put(t, reg(t.name().toLowerCase(), new WowAccessoryItem(new Item.Settings().maxCount(1))));
        }
        TOKEN = reg("keystone", new Item(new Item.Settings().maxCount(1)));
        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(WEAPONS.get(WeaponType.SWORD_1H)))
                .displayName(Text.translatable("itemGroup.wowcraft"))
                .entries((ctx, entries) -> {
                    for (Item item : ALL.values()) entries.add(item);
                })
                .build();
        Registry.register(Registries.ITEM_GROUP, new Identifier(WowCraftMod.ID, "items"), group);
    }

    private static Item armor(WowArmorMaterial mat, ArmorItem.Type type) {
        Item.Settings s = new Item.Settings().maxCount(1);
        if (mat == WowArmorMaterial.CLOTH || mat == WowArmorMaterial.LEATHER) return new WowDyeableArmorItem(mat, type, s);
        return new WowArmorItem(mat, type, s);
    }

    private static Item reg(String name, Item item) {
        Registry.register(Registries.ITEM, new Identifier(WowCraftMod.ID, name), item);
        ALL.put(name, item);
        return item;
    }

    // ------------------------------------------------------------------ conversion

    /** The Minecraft item that represents a WoW item. */
    public static Item itemFor(ItemData d) {
        EquipType t = d.equipType();
        if (t == null) return TOKEN;
        WeaponType w = d.weaponType();
        if (w != null) return WEAPONS.get(w);
        EquipSlot slot = t.slots.get(0);
        if (slot == EquipSlot.HEAD || slot == EquipSlot.CHEST || slot == EquipSlot.LEGS || slot == EquipSlot.FEET) {
            ArmorType at = d.armorType();
            if (at == null || at == ArmorType.NONE) at = ArmorType.CLOTH;
            return ARMOR.get(at).get(slot);
        }
        Item acc = ACCESSORIES.get(t);
        return acc != null ? acc : TOKEN;
    }

    public static ItemStack toStack(ItemData d) {
        ItemStack stack = new ItemStack(itemFor(d));
        write(stack, d);
        return stack;
    }

    public static void write(ItemStack stack, ItemData d) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, Protocol.gson().toJson(d));
        if (stack.getItem() instanceof WowDyeableArmorItem dye) {
            dye.setColor(stack, armorColor(d));
        }
        nbt.putBoolean("Unbreakable", true);
    }

    public static ItemData read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_KEY)) return null;
        try {
            return Protocol.gson().fromJson(nbt.getString(NBT_KEY), ItemData.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static int armorColor(ItemData d) {
        ArmorType at = d.armorType();
        if (at == ArmorType.LEATHER) return 0x6B4A2B;
        ItemQuality q = d.quality();
        return switch (q) {
            case UNCOMMON -> 0x3C7A3C;
            case RARE -> 0x2E4C8C;
            case EPIC -> 0x5E2E8C;
            case LEGENDARY -> 0xA05A10;
            default -> 0xC8C8C8;
        };
    }

    // ------------------------------------------------------------------ display

    public static Text name(ItemStack stack, Text fallback) {
        ItemData d = read(stack);
        if (d == null) return fallback;
        return Text.literal(d.displayName().get(language.get())).styled(s -> s.withColor(d.quality().color & 0xFFFFFF));
    }

    private static String t(String en, String ru) {
        return language.get() == L10n.Lang.RU ? ru : en;
    }

    public static void tooltip(ItemStack stack, List<Text> tooltip) {
        ItemData d = read(stack);
        if (d == null) {
            tooltip.add(Text.literal(t("Placeholder WoW item", "Заготовка предмета WoW")).formatted(Formatting.GRAY));
            return;
        }
        L10n.Lang lang = language.get();
        tooltip.add(Text.literal(t("Item Level ", "Уровень предмета ") + d.ilvl).formatted(Formatting.YELLOW));
        UpgradeTrack track = d.track();
        if (track != null) {
            tooltip.add(Text.literal(t("Upgrade Level: ", "Уровень улучшения: ") + track.name.get(lang) + " " + d.rank + "/" + track.ranks)
                    .formatted(Formatting.GRAY));
        }
        EquipType type = d.equipType();
        if (type != null) {
            String right = d.weaponType() != null ? weaponName(d.weaponType(), lang) : d.armorType() != null && d.armorType() != ArmorType.NONE
                    ? d.armorType().name.get(lang) : "";
            tooltip.add(Text.literal(type.name.get(lang) + (right.isEmpty() ? "" : "   " + right)).formatted(Formatting.WHITE));
        }
        if (d.weaponType() != null) {
            double dps = ItemStats.weaponDps(d);
            tooltip.add(Text.literal(t("(", "(") + String.format("%.1f", dps) + t(" damage per second)", " ед. урона в секунду)")).formatted(Formatting.WHITE));
        }
        Spec spec = tooltipSpec.get();
        Spec shown = spec != null ? spec : Spec.ARMS;
        StatBlock stats = ItemStats.compute(d, shown);
        double armor = stats.get(Stat.ARMOR);
        if (armor > 0) tooltip.add(Text.literal(Math.round(armor) + " " + Stat.ARMOR.name.get(lang)).formatted(Formatting.WHITE));
        for (Stat s : new Stat[]{Stat.STRENGTH, Stat.AGILITY, Stat.INTELLECT, Stat.STAMINA}) {
            double v = stats.get(s);
            if (v > 0) tooltip.add(Text.literal("+" + Math.round(v) + " " + s.name.get(lang)).formatted(Formatting.WHITE));
        }
        for (Stat s : Stat.SECONDARIES) {
            double v = stats.get(s);
            if (v > 0) tooltip.add(Text.literal("+" + Math.round(v) + " " + s.name.get(lang)).formatted(Formatting.GREEN));
        }
        for (Stat s : new Stat[]{Stat.LEECH, Stat.AVOIDANCE, Stat.SPEED}) {
            double v = stats.get(s);
            if (v > 0) tooltip.add(Text.literal("+" + Math.round(v) + " " + s.name.get(lang)).formatted(Formatting.GREEN));
        }
        if (d.sockets > 0) tooltip.add(Text.literal(t("Prismatic Socket", "Призматическое гнездо")).formatted(Formatting.GRAY));
        if (d.effectId != null) {
            ItemEffect fx = ItemRegistry.effect(d.effectId);
            if (fx != null) tooltip.add(Text.literal(fx.describe(lang, d.ilvl)).formatted(Formatting.GREEN));
        }
        if (d.setId != null) {
            TierSet set = ItemRegistry.set(d.setId);
            if (set != null) {
                tooltip.add(Text.literal(set.name().get(lang)).formatted(Formatting.GOLD));
            }
        }
        if (d.flavor != null) tooltip.add(Text.literal("\"" + (lang == L10n.Lang.RU && d.flavorRu != null ? d.flavorRu : d.flavor) + "\"")
                .formatted(Formatting.GOLD));
        if (d.requiredLevel > 1) tooltip.add(Text.literal(t("Requires Level ", "Требуется уровень: ") + d.requiredLevel).formatted(Formatting.GRAY));
        if (type != null && !type.slots.get(0).isVanillaSlot() && d.weaponType() == null) {
            tooltip.add(Text.literal(t("Right-click to equip", "ПКМ — надеть")).formatted(Formatting.DARK_GRAY));
        }
        MutableText src = d.source != null ? Text.literal(t("Source: ", "Источник: ") + d.source).formatted(Formatting.DARK_GRAY) : null;
        if (src != null) tooltip.add(src);
    }

    static String weaponName(WeaponType w, L10n.Lang lang) {
        return w.name.get(lang);
    }
}
