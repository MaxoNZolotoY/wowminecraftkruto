package com.wowcraft.core.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Registry of boss scripts by id. */
public final class BossScripts {
    private static final Map<String, Supplier<BossScript>> SCRIPTS = new HashMap<>();

    private BossScripts() {
    }

    public static void register(String id, Supplier<BossScript> s) {
        SCRIPTS.put(id, s);
    }

    public static BossScript create(String id) {
        Supplier<BossScript> s = id == null ? null : SCRIPTS.get(id);
        return s == null ? null : s.get();
    }

    public static boolean has(String id) {
        return SCRIPTS.containsKey(id);
    }
}
