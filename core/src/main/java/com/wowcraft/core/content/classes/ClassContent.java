package com.wowcraft.core.content.classes;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.talent.TalentNode;
import com.wowcraft.core.talent.TalentTree;

import java.util.List;

/** Base class with short helpers for writing class content. */
public abstract class ClassContent {
    protected final WowClass cls;
    protected final ClassKit kit;

    protected ClassContent(WowClass cls) {
        this.cls = cls;
        this.kit = new ClassKit(cls);
    }

    public abstract void register();

    public ClassKit kit() {
        return kit;
    }

    // ---- abilities ----

    protected Ability.Builder ab(String id, String en, String ru) {
        return Ability.builder(id, en, ru).cls(cls);
    }

    protected Ability reg(Ability.Builder b) {
        return Registry.register(b.build());
    }

    protected AuraDef reg(AuraDef.Builder b) {
        return Registry.register(b.build());
    }

    protected AuraDef.Builder buff(String id, String en, String ru) {
        return AuraDef.buff(id, en, ru);
    }

    protected AuraDef.Builder debuff(String id, String en, String ru) {
        return AuraDef.debuff(id, en, ru);
    }

    protected AuraDef.Builder passive(String id, String en, String ru) {
        return AuraDef.passive(id, en, ru);
    }

    /** Standard CC debuff. */
    protected AuraDef cc(String id, String en, String ru, CcType type, double duration, DispelType dispel) {
        return reg(debuff(id, en, ru).duration(duration).cc(type).dispel(dispel).noPandemic());
    }

    /** Standard slow debuff. */
    protected AuraDef slow(String id, String en, String ru, double pct, double duration) {
        return reg(debuff(id, en, ru).duration(duration).mod(Modifier.speed(-pct)).cc(CcType.SLOW).dispel(DispelType.MAGIC));
    }

    /** Damage reduction buff. */
    protected AuraDef dr(String id, String en, String ru, double pct, double duration) {
        return reg(buff(id, en, ru).duration(duration).mod(Modifier.taken(-pct)).tag("defensive"));
    }

    /** Modifiers that reduce magic damage taken by a percentage. */
    protected static Modifier[] magicTaken(double pct) {
        School[] magic = {School.HOLY, School.FIRE, School.NATURE, School.FROST, School.SHADOW, School.ARCANE, School.CHAOS};
        Modifier[] out = new Modifier[magic.length];
        for (int i = 0; i < magic.length; i++) {
            out[i] = Modifier.of(com.wowcraft.core.mod.ModType.DAMAGE_TAKEN, com.wowcraft.core.mod.ModFilter.school(magic[i]), pct);
        }
        return out;
    }

    protected static Modifier physicalTaken(double pct) {
        return Modifier.of(com.wowcraft.core.mod.ModType.DAMAGE_TAKEN, com.wowcraft.core.mod.ModFilter.school(School.PHYSICAL), pct);
    }

    protected static Modifier[] magicImmune() {
        School[] magic = {School.HOLY, School.FIRE, School.NATURE, School.FROST, School.SHADOW, School.ARCANE, School.CHAOS};
        Modifier[] out = new Modifier[magic.length];
        for (int i = 0; i < magic.length; i++) out[i] = Modifier.ref(com.wowcraft.core.mod.ModType.DAMAGE_IMMUNE, magic[i].name(), 1);
        return out;
    }

    // ---- talents ----

    protected TalentNode.Builder t(String id, int row, int col, String en, String ru) {
        return TalentNode.at(id, row, col, en, ru);
    }

    protected void classTree(TalentNode.Builder... nodes) {
        kit.classTree = new TalentTree(cls.id(), true, build(nodes));
    }

    protected void specTree(Spec spec, TalentNode.Builder... nodes) {
        kit.specTrees.put(spec, new TalentTree(spec.id(), false, build(nodes)));
    }

    protected void hero(TalentNode.Builder a, TalentNode.Builder b) {
        kit.heroOptions.add(a.build());
        kit.heroOptions.add(b.build());
    }

    private static List<TalentNode> build(TalentNode.Builder... nodes) {
        java.util.ArrayList<TalentNode> out = new java.util.ArrayList<>();
        for (TalentNode.Builder b : nodes) out.add(b.build());
        return out;
    }
}
