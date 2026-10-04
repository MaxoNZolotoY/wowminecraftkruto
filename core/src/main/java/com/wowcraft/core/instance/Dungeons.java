package com.wowcraft.core.instance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of dungeons, raids, arenas and battlegrounds. */
public final class Dungeons {
    private static final Map<String, DungeonDef> DEFS = new LinkedHashMap<>();
    /** Dungeons in the current Mythic+ season pool. */
    private static final List<String> MYTHIC_POOL = new ArrayList<>();

    private Dungeons() {
    }

    public static DungeonDef register(DungeonDef d) {
        if (DEFS.put(d.id, d) != null) throw new IllegalStateException("Duplicate dungeon " + d.id);
        return d;
    }

    public static void addToMythicPool(String id) {
        MYTHIC_POOL.add(id);
    }

    public static DungeonDef get(String id) {
        return DEFS.get(id);
    }

    public static Collection<DungeonDef> all() {
        return DEFS.values();
    }

    public static List<String> mythicPool() {
        return MYTHIC_POOL;
    }

    public static List<DungeonDef> ofType(DungeonDef.Type t) {
        List<DungeonDef> out = new ArrayList<>();
        for (DungeonDef d : DEFS.values()) if (d.type == t) out.add(d);
        return out;
    }
}
