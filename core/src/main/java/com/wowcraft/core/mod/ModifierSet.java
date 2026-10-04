package com.wowcraft.core.mod;

import java.util.ArrayList;
import java.util.List;

/** Flattened list of modifiers with weights (aura stacks). */
public final class ModifierSet {
    private final List<Modifier> mods = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();

    public void clear() {
        mods.clear();
        weights.clear();
    }

    public void add(Modifier m) {
        add(m, 1.0);
    }

    public void add(Modifier m, double weight) {
        mods.add(m);
        weights.add(weight);
    }

    public void addAll(List<Modifier> list) {
        for (Modifier m : list) add(m);
    }

    public int size() {
        return mods.size();
    }

    public List<Modifier> list() {
        return mods;
    }

    /** Sum of ADD modifiers (or the sum of values for MUL types). */
    public double sum(ModType type, ModContext ctx) {
        double s = 0;
        for (int i = 0; i < mods.size(); i++) {
            Modifier m = mods.get(i);
            if (m.type() == type && ctx.matches(m.filter())) s += m.value() * weights.get(i);
        }
        return s;
    }

    /** Product of (1 + v) for MUL modifiers. */
    public double product(ModType type, ModContext ctx) {
        double p = 1.0;
        for (int i = 0; i < mods.size(); i++) {
            Modifier m = mods.get(i);
            if (m.type() == type && ctx.matches(m.filter())) p *= 1.0 + m.value() * weights.get(i);
        }
        return Math.max(0, p);
    }

    public double sumRef(ModType type, String ref) {
        double s = 0;
        for (int i = 0; i < mods.size(); i++) {
            Modifier m = mods.get(i);
            if (m.type() == type && ref.equals(m.ref())) s += m.value() * weights.get(i);
        }
        return s;
    }

    public double productRef(ModType type, String ref) {
        double p = 1.0;
        for (int i = 0; i < mods.size(); i++) {
            Modifier m = mods.get(i);
            if (m.type() == type && ref.equals(m.ref())) p *= 1.0 + m.value() * weights.get(i);
        }
        return Math.max(0, p);
    }

    public boolean hasRef(ModType type, String ref) {
        for (Modifier m : mods) if (m.type() == type && (ref.equals(m.ref()) || "ALL".equals(m.ref()))) return true;
        return false;
    }

    public boolean has(ModType type) {
        for (Modifier m : mods) if (m.type() == type) return true;
        return false;
    }

    public List<String> refs(ModType type) {
        List<String> out = new ArrayList<>();
        for (Modifier m : mods) if (m.type() == type && m.ref() != null) out.add(m.ref());
        return out;
    }
}
