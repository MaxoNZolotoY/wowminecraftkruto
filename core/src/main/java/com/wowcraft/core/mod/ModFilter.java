package com.wowcraft.core.mod;

import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.School;

import java.util.Set;

/** Restricts which abilities / schools a modifier applies to. */
public final class ModFilter {
    public enum Kind {ANY, ABILITY, TAG, SCHOOL, AURA, NOT_TAG}

    public static final ModFilter ANY = new ModFilter(Kind.ANY, null);

    public final Kind kind;
    public final String value;

    private ModFilter(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public static ModFilter ability(String id) {
        return new ModFilter(Kind.ABILITY, id);
    }

    public static ModFilter tag(String tag) {
        return new ModFilter(Kind.TAG, tag);
    }

    public static ModFilter notTag(String tag) {
        return new ModFilter(Kind.NOT_TAG, tag);
    }

    public static ModFilter school(School s) {
        return new ModFilter(Kind.SCHOOL, s.name());
    }

    public static ModFilter aura(String id) {
        return new ModFilter(Kind.AURA, id);
    }

    public boolean matches(String abilityId, Set<String> tags, School school, String auraId) {
        return switch (kind) {
            case ANY -> true;
            case ABILITY -> value.equals(abilityId);
            case TAG -> tags != null && tags.contains(value);
            case NOT_TAG -> tags == null || !tags.contains(value);
            case SCHOOL -> school != null && school.name().equals(value);
            case AURA -> value.equals(auraId);
        };
    }

    public boolean matches(Ability ability) {
        if (ability == null) return kind == Kind.ANY || kind == Kind.NOT_TAG;
        return matches(ability.id, ability.tags, ability.school, null);
    }

    @Override
    public String toString() {
        return kind == Kind.ANY ? "any" : kind.name().toLowerCase() + ":" + value;
    }
}
