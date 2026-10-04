package com.wowcraft.core.content;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.spell.Ability;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Global registries of game content. Filled once by {@link Content#bootstrap()}. */
public final class Registry {
    private static final Map<String, Ability> ABILITIES = new LinkedHashMap<>();
    private static final Map<String, AuraDef> AURAS = new LinkedHashMap<>();

    private Registry() {
    }

    public static Ability register(Ability a) {
        if (ABILITIES.put(a.id, a) != null) {
            throw new IllegalStateException("Duplicate ability id: " + a.id);
        }
        return a;
    }

    public static AuraDef register(AuraDef a) {
        if (AURAS.put(a.id, a) != null) {
            throw new IllegalStateException("Duplicate aura id: " + a.id);
        }
        return a;
    }

    public static Ability ability(String id) {
        return ABILITIES.get(id);
    }

    public static AuraDef aura(String id) {
        return AURAS.get(id);
    }

    public static Collection<Ability> abilities() {
        return ABILITIES.values();
    }

    public static Collection<AuraDef> auras() {
        return AURAS.values();
    }

    public static boolean hasAbility(String id) {
        return ABILITIES.containsKey(id);
    }

    public static boolean hasAura(String id) {
        return AURAS.containsKey(id);
    }
}
