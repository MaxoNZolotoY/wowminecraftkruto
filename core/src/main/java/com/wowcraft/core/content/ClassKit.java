package com.wowcraft.core.content;

import com.wowcraft.core.bot.Rotation;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.talent.TalentNode;
import com.wowcraft.core.talent.TalentTree;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Everything a class offers: abilities per spec, passives, default action bars, talents, bot rotations. */
public final class ClassKit {
    public static final int BAR_SIZE = 12;

    public final WowClass wowClass;
    /** Abilities every spec of the class gets. */
    public final List<String> classAbilities = new ArrayList<>();
    public final Map<Spec, List<String>> specAbilities = new EnumMap<>(Spec.class);
    /** Permanent auras (passives) per spec. */
    public final Map<Spec, List<String>> specPassives = new EnumMap<>(Spec.class);
    public final List<String> classPassives = new ArrayList<>();
    public final Map<Spec, String[]> defaultBars = new EnumMap<>(Spec.class);
    public final Map<Spec, Rotation> rotations = new EnumMap<>(Spec.class);
    public TalentTree classTree;
    public final Map<Spec, TalentTree> specTrees = new EnumMap<>(Spec.class);
    /** Hero talents: two options, one is chosen at level 70. */
    public final List<TalentNode> heroOptions = new ArrayList<>();
    /** NPC template used as this class's default pet (hunter / warlock / unholy DK). */
    public final Map<Spec, String> defaultPet = new EnumMap<>(Spec.class);
    /** Resources every spec of the class has in addition to the spec's own (druid forms). */
    public final java.util.Set<com.wowcraft.core.resource.ResourceType> extraResources = java.util.EnumSet.noneOf(com.wowcraft.core.resource.ResourceType.class);

    public ClassKit(WowClass c) {
        this.wowClass = c;
    }

    public ClassKit common(String... ids) {
        java.util.Collections.addAll(classAbilities, ids);
        return this;
    }

    public ClassKit spec(Spec s, String... ids) {
        specAbilities.computeIfAbsent(s, k -> new ArrayList<>()).addAll(List.of(ids));
        return this;
    }

    public ClassKit passive(Spec s, String... auraIds) {
        specPassives.computeIfAbsent(s, k -> new ArrayList<>()).addAll(List.of(auraIds));
        return this;
    }

    public ClassKit bar(Spec s, String... ids) {
        String[] bar = new String[BAR_SIZE];
        for (int i = 0; i < Math.min(ids.length, BAR_SIZE); i++) bar[i] = ids[i];
        defaultBars.put(s, bar);
        return this;
    }

    public ClassKit rotation(Spec s, Rotation r) {
        rotations.put(s, r);
        return this;
    }

    /** All ability ids (class + spec) available to a spec at a level, plus talent grants handled elsewhere. */
    public List<String> abilitiesFor(Spec spec, int level) {
        List<String> out = new ArrayList<>();
        for (String id : classAbilities) add(out, id, spec, level);
        for (String id : specAbilities.getOrDefault(spec, List.of())) add(out, id, spec, level);
        return out;
    }

    private static void add(List<String> out, String id, Spec spec, int level) {
        Ability a = Registry.ability(id);
        if (a == null) return;
        if (a.unlockLevel > level) return;
        if (!a.specs.isEmpty() && !a.specs.contains(spec)) return;
        if (!out.contains(id)) out.add(id);
    }
}
