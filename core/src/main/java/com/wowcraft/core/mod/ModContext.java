package com.wowcraft.core.mod;

import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.School;

import java.util.Set;

/** What a modifier query is about. */
public record ModContext(String abilityId, Set<String> tags, School school, String auraId) {
    public static final ModContext NONE = new ModContext(null, Set.of(), null, null);

    public static ModContext of(Ability a) {
        return a == null ? NONE : new ModContext(a.id, a.tags, a.school, null);
    }

    public static ModContext of(Ability a, School overrideSchool) {
        return a == null ? new ModContext(null, Set.of(), overrideSchool, null)
                : new ModContext(a.id, a.tags, overrideSchool != null ? overrideSchool : a.school, null);
    }

    public static ModContext aura(String auraId, Set<String> tags, School school) {
        return new ModContext(null, tags, school, auraId);
    }

    public static ModContext school(School s) {
        return new ModContext(null, Set.of(), s, null);
    }

    public boolean matches(ModFilter f) {
        return f.matches(abilityId, tags, school, auraId);
    }
}
