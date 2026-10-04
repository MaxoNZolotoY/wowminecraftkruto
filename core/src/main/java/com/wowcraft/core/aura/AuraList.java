package com.wowcraft.core.aura;

import com.wowcraft.core.combat.UnitState;

import java.util.ArrayList;
import java.util.List;

/** The auras present on a unit. Mutated only by the combat engine. */
public final class AuraList {
    private final List<AuraInstance> auras = new ArrayList<>();
    private boolean dirty;

    public List<AuraInstance> all() {
        return auras;
    }

    public void add(AuraInstance a) {
        auras.add(a);
        dirty = true;
    }

    public boolean remove(AuraInstance a) {
        boolean r = auras.remove(a);
        if (r) dirty = true;
        return r;
    }

    public AuraInstance get(String id) {
        for (AuraInstance a : auras) if (a.def.id.equals(id) && !a.removed) return a;
        return null;
    }

    public AuraInstance get(String id, UnitState caster) {
        for (AuraInstance a : auras)
            if (a.def.id.equals(id) && !a.removed && (!a.def.perCaster || a.caster == caster)) return a;
        return null;
    }

    public boolean has(String id) {
        return get(id) != null;
    }

    public boolean hasFrom(String id, UnitState caster) {
        for (AuraInstance a : auras) if (a.def.id.equals(id) && !a.removed && a.caster == caster) return true;
        return false;
    }

    public int stacks(String id) {
        AuraInstance a = get(id);
        return a == null ? 0 : a.stacks;
    }

    public String currentForm() {
        for (AuraInstance a : auras) if (a.def.form != null && !a.removed) return a.def.form;
        return null;
    }

    public AuraInstance formAura() {
        for (AuraInstance a : auras) if (a.def.form != null && !a.removed) return a;
        return null;
    }

    public boolean isStealthed() {
        for (AuraInstance a : auras) if (a.def.stealth && !a.removed) return true;
        return false;
    }

    public boolean hasCc(CcType type) {
        for (AuraInstance a : auras) if (a.def.cc == type && !a.removed) return true;
        return false;
    }

    public boolean any(java.util.function.Predicate<AuraInstance> p) {
        for (AuraInstance a : auras) if (!a.removed && p.test(a)) return true;
        return false;
    }

    public List<AuraInstance> matching(java.util.function.Predicate<AuraInstance> p) {
        List<AuraInstance> out = new ArrayList<>();
        for (AuraInstance a : auras) if (!a.removed && p.test(a)) out.add(a);
        return out;
    }

    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void markDirty() {
        dirty = true;
    }

    public int size() {
        return auras.size();
    }
}
