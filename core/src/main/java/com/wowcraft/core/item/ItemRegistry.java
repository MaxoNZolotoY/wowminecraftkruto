package com.wowcraft.core.item;

import com.wowcraft.core.spec.WowClass;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Item effects, tier sets, templates and loot tables. Filled by content. */
public final class ItemRegistry {
    private static final Map<String, ItemEffect> EFFECTS = new LinkedHashMap<>();
    private static final Map<String, TierSet> SETS = new LinkedHashMap<>();
    private static final Map<WowClass, TierSet> SET_BY_CLASS = new EnumMap<>(WowClass.class);
    private static final Map<String, ItemTemplate> TEMPLATES = new LinkedHashMap<>();
    private static final Map<String, List<String>> LOOT_TABLES = new LinkedHashMap<>();

    private ItemRegistry() {
    }

    public static ItemEffect register(ItemEffect e) {
        EFFECTS.put(e.id(), e);
        return e;
    }

    public static TierSet register(TierSet s) {
        SETS.put(s.id(), s);
        SET_BY_CLASS.put(s.wowClass(), s);
        return s;
    }

    public static ItemTemplate register(ItemTemplate t) {
        if (TEMPLATES.put(t.id(), t) != null) throw new IllegalStateException("Duplicate item template " + t.id());
        return t;
    }

    public static void lootTable(String id, List<String> templateIds) {
        LOOT_TABLES.put(id, List.copyOf(templateIds));
    }

    public static ItemEffect effect(String id) {
        return id == null ? null : EFFECTS.get(id);
    }

    public static TierSet set(String id) {
        return id == null ? null : SETS.get(id);
    }

    public static TierSet setFor(WowClass c) {
        return SET_BY_CLASS.get(c);
    }

    public static ItemTemplate template(String id) {
        return TEMPLATES.get(id);
    }

    public static List<String> lootTable(String id) {
        return LOOT_TABLES.getOrDefault(id, List.of());
    }

    public static Collection<ItemEffect> effects() {
        return EFFECTS.values();
    }

    public static Collection<ItemTemplate> templates() {
        return TEMPLATES.values();
    }

    public static Collection<TierSet> sets() {
        return SETS.values();
    }

    public static Map<String, List<String>> lootTables() {
        return LOOT_TABLES;
    }
}
