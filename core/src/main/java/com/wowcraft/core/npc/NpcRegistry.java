package com.wowcraft.core.npc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcRegistry {
    private static final Map<String, NpcTemplate> TEMPLATES = new LinkedHashMap<>();

    private NpcRegistry() {
    }

    public static NpcTemplate register(NpcTemplate t) {
        if (TEMPLATES.put(t.id, t) != null) throw new IllegalStateException("Duplicate NPC template " + t.id);
        return t;
    }

    public static NpcTemplate get(String id) {
        return TEMPLATES.get(id);
    }

    public static boolean has(String id) {
        return TEMPLATES.containsKey(id);
    }

    public static Collection<NpcTemplate> all() {
        return TEMPLATES.values();
    }
}
