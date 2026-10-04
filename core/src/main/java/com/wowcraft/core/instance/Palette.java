package com.wowcraft.core.instance;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Block ids (Minecraft resource locations) used for each material in a theme. */
public final class Palette {
    private static final Map<String, Palette> PALETTES = new HashMap<>();

    public final String id;
    private final EnumMap<Material, String> blocks = new EnumMap<>(Material.class);

    public Palette(String id) {
        this.id = id;
        blocks.put(Material.AIR, "minecraft:air");
        blocks.put(Material.BARRIER, "minecraft:barrier");
        blocks.put(Material.GLASS, "minecraft:tinted_glass");
    }

    public Palette set(Material m, String block) {
        blocks.put(m, block);
        return this;
    }

    public String block(Material m) {
        String b = blocks.get(m);
        if (b == null) b = blocks.getOrDefault(Material.WALL, "minecraft:stone_bricks");
        return b;
    }

    public static Palette register(Palette p) {
        PALETTES.put(p.id, p);
        return p;
    }

    public static Palette get(String id) {
        return PALETTES.getOrDefault(id, PALETTES.get("stone"));
    }

    static {
        register(new Palette("stone").set(Material.FLOOR, "minecraft:polished_andesite").set(Material.FLOOR_ACCENT, "minecraft:chiseled_stone_bricks")
                .set(Material.WALL, "minecraft:stone_bricks").set(Material.WALL_ACCENT, "minecraft:cracked_stone_bricks")
                .set(Material.CEILING, "minecraft:stone_bricks").set(Material.PILLAR, "minecraft:polished_basalt")
                .set(Material.LIGHT, "minecraft:glowstone").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:cobweb")
                .set(Material.PATH, "minecraft:smooth_stone").set(Material.RUBBLE, "minecraft:cobblestone").set(Material.PLATFORM, "minecraft:polished_andesite"));
        register(new Palette("deep").set(Material.FLOOR, "minecraft:deepslate_tiles").set(Material.FLOOR_ACCENT, "minecraft:chiseled_deepslate")
                .set(Material.WALL, "minecraft:deepslate_bricks").set(Material.WALL_ACCENT, "minecraft:cracked_deepslate_bricks")
                .set(Material.CEILING, "minecraft:cobbled_deepslate").set(Material.PILLAR, "minecraft:polished_deepslate")
                .set(Material.LIGHT, "minecraft:verdant_froglight").set(Material.LIQUID, "minecraft:lava").set(Material.DECOR, "minecraft:chain")
                .set(Material.PATH, "minecraft:polished_deepslate").set(Material.RUBBLE, "minecraft:cobbled_deepslate").set(Material.PLATFORM, "minecraft:reinforced_deepslate"));
        register(new Palette("tide").set(Material.FLOOR, "minecraft:prismarine_bricks").set(Material.FLOOR_ACCENT, "minecraft:dark_prismarine")
                .set(Material.WALL, "minecraft:prismarine").set(Material.WALL_ACCENT, "minecraft:dark_prismarine")
                .set(Material.CEILING, "minecraft:prismarine_bricks").set(Material.PILLAR, "minecraft:dark_prismarine")
                .set(Material.LIGHT, "minecraft:sea_lantern").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:tube_coral_block")
                .set(Material.PATH, "minecraft:sandstone").set(Material.RUBBLE, "minecraft:gravel").set(Material.PLATFORM, "minecraft:prismarine_bricks"));
        register(new Palette("ember").set(Material.FLOOR, "minecraft:polished_blackstone_bricks").set(Material.FLOOR_ACCENT, "minecraft:gilded_blackstone")
                .set(Material.WALL, "minecraft:blackstone").set(Material.WALL_ACCENT, "minecraft:magma_block")
                .set(Material.CEILING, "minecraft:basalt").set(Material.PILLAR, "minecraft:polished_basalt")
                .set(Material.LIGHT, "minecraft:shroomlight").set(Material.LIQUID, "minecraft:lava").set(Material.DECOR, "minecraft:chain")
                .set(Material.PATH, "minecraft:polished_blackstone").set(Material.RUBBLE, "minecraft:netherrack").set(Material.PLATFORM, "minecraft:obsidian"));
        register(new Palette("frost").set(Material.FLOOR, "minecraft:packed_ice").set(Material.FLOOR_ACCENT, "minecraft:blue_ice")
                .set(Material.WALL, "minecraft:snow_block").set(Material.WALL_ACCENT, "minecraft:bone_block")
                .set(Material.CEILING, "minecraft:packed_ice").set(Material.PILLAR, "minecraft:bone_block")
                .set(Material.LIGHT, "minecraft:sea_lantern").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:snow_block")
                .set(Material.PATH, "minecraft:snow_block").set(Material.RUBBLE, "minecraft:ice").set(Material.PLATFORM, "minecraft:blue_ice"));
        register(new Palette("grove").set(Material.FLOOR, "minecraft:moss_block").set(Material.FLOOR_ACCENT, "minecraft:rooted_dirt")
                .set(Material.WALL, "minecraft:mossy_stone_bricks").set(Material.WALL_ACCENT, "minecraft:dark_oak_log")
                .set(Material.CEILING, "minecraft:dark_oak_planks").set(Material.PILLAR, "minecraft:dark_oak_log")
                .set(Material.LIGHT, "minecraft:ochre_froglight").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:moss_block")
                .set(Material.PATH, "minecraft:dirt_path").set(Material.RUBBLE, "minecraft:mossy_cobblestone").set(Material.PLATFORM, "minecraft:mud_bricks"));
        register(new Palette("void").set(Material.FLOOR, "minecraft:purpur_block").set(Material.FLOOR_ACCENT, "minecraft:crying_obsidian")
                .set(Material.WALL, "minecraft:end_stone_bricks").set(Material.WALL_ACCENT, "minecraft:purpur_pillar")
                .set(Material.CEILING, "minecraft:obsidian").set(Material.PILLAR, "minecraft:purpur_pillar")
                .set(Material.LIGHT, "minecraft:pearlescent_froglight").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:amethyst_block")
                .set(Material.PATH, "minecraft:end_stone").set(Material.RUBBLE, "minecraft:end_stone").set(Material.PLATFORM, "minecraft:purpur_block"));
        register(new Palette("sand").set(Material.FLOOR, "minecraft:smooth_sandstone").set(Material.FLOOR_ACCENT, "minecraft:chiseled_sandstone")
                .set(Material.WALL, "minecraft:cut_sandstone").set(Material.WALL_ACCENT, "minecraft:chiseled_red_sandstone")
                .set(Material.CEILING, "minecraft:sandstone").set(Material.PILLAR, "minecraft:cut_red_sandstone")
                .set(Material.LIGHT, "minecraft:glowstone").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:chiseled_sandstone")
                .set(Material.PATH, "minecraft:smooth_red_sandstone").set(Material.RUBBLE, "minecraft:sand").set(Material.PLATFORM, "minecraft:cut_sandstone"));
        register(new Palette("harbor").set(Material.FLOOR, "minecraft:spruce_planks").set(Material.FLOOR_ACCENT, "minecraft:stripped_spruce_wood")
                .set(Material.WALL, "minecraft:stone_bricks").set(Material.WALL_ACCENT, "minecraft:spruce_log")
                .set(Material.CEILING, "minecraft:dark_oak_planks").set(Material.PILLAR, "minecraft:spruce_log")
                .set(Material.LIGHT, "minecraft:glowstone").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:barrel")
                .set(Material.PATH, "minecraft:oak_planks").set(Material.RUBBLE, "minecraft:gravel").set(Material.PLATFORM, "minecraft:spruce_planks"));
        register(new Palette("arena").set(Material.FLOOR, "minecraft:smooth_stone").set(Material.FLOOR_ACCENT, "minecraft:red_terracotta")
                .set(Material.WALL, "minecraft:stone_bricks").set(Material.WALL_ACCENT, "minecraft:chiseled_stone_bricks")
                .set(Material.CEILING, "minecraft:barrier").set(Material.PILLAR, "minecraft:polished_andesite")
                .set(Material.LIGHT, "minecraft:sea_lantern").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:chiseled_stone_bricks")
                .set(Material.PATH, "minecraft:gravel").set(Material.RUBBLE, "minecraft:cobblestone").set(Material.PLATFORM, "minecraft:smooth_stone"));
        register(new Palette("titan").set(Material.FLOOR, "minecraft:polished_diorite").set(Material.FLOOR_ACCENT, "minecraft:gold_block")
                .set(Material.WALL, "minecraft:quartz_bricks").set(Material.WALL_ACCENT, "minecraft:chiseled_quartz_block")
                .set(Material.CEILING, "minecraft:quartz_block").set(Material.PILLAR, "minecraft:quartz_pillar")
                .set(Material.LIGHT, "minecraft:pearlescent_froglight").set(Material.LIQUID, "minecraft:water").set(Material.DECOR, "minecraft:amethyst_block")
                .set(Material.PATH, "minecraft:smooth_quartz").set(Material.RUBBLE, "minecraft:calcite").set(Material.PLATFORM, "minecraft:crying_obsidian"));
    }
}
