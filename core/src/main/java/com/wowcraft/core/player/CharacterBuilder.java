package com.wowcraft.core.player;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.Formulas;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.mod.ModType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.stat.StatBlock;
import com.wowcraft.core.talent.TalentNode;
import com.wowcraft.core.talent.TalentTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Turns class / spec / level / talents / gear into a fully configured combat unit. */
public final class CharacterBuilder {
    private CharacterBuilder() {
    }

    /** Base (naked) stats of a spec at a level. */
    public static StatBlock baseStats(Spec spec, int level) {
        StatBlock b = new StatBlock();
        double primary = Formulas.basePrimary(level);
        b.set(Stat.STRENGTH, primary * 0.4);
        b.set(Stat.AGILITY, primary * 0.4);
        b.set(Stat.INTELLECT, primary * 0.4);
        b.set(spec.primaryStat, primary);
        b.set(Stat.STAMINA, Formulas.baseStamina(level));
        return b;
    }

    /**
     * Applies class, spec, level and talents.
     *
     * @param talents  chosen talent node ids (class + spec + hero), invalid ones are ignored
     * @param gear     total stats from equipment (may be null)
     */
    public static void apply(CombatEngine engine, UnitState u, Spec spec, int level, Collection<String> talents, StatBlock gear) {
        applyInternal(engine, u, spec, level, talents, gear, null);
    }

    /** Applies class, spec, level, talents and real equipment (stats, weapons, set bonuses, item effects). */
    public static void apply(CombatEngine engine, UnitState u, Spec spec, int level, Collection<String> talents,
                             com.wowcraft.core.item.Equipment equipment) {
        StatBlock gear = equipment != null ? equipment.totalStats(spec) : null;
        applyInternal(engine, u, spec, level, talents, gear, equipment);
    }

    private static void applyInternal(CombatEngine engine, UnitState u, Spec spec, int level, Collection<String> talents, StatBlock gear,
                                      com.wowcraft.core.item.Equipment equipment) {
        ClassKit kit = Content.kit(spec.wowClass);
        u.wowClass = spec.wowClass;
        u.spec = spec;
        u.level = level;
        u.role = null;

        // stats
        u.baseStats.clear();
        u.baseStats.add(baseStats(spec, level));
        u.gearStats.clear();
        if (gear != null) u.gearStats.add(gear);

        // resources
        Set<ResourceType> res = EnumSet.noneOf(ResourceType.class);
        java.util.Collections.addAll(res, spec.resources);
        res.addAll(kit.extraResources);
        boolean resourcesChanged = !u.resources().active().equals(res);
        if (resourcesChanged) engine.configureResources(u, res);

        // talents -> modifiers, passive auras, granted abilities
        List<String> chosen = validTalents(kit, spec, level, talents);
        u.permanentMods.clear();
        List<String> passiveAuras = new ArrayList<>(kit.classPassives);
        passiveAuras.addAll(kit.specPassives.getOrDefault(spec, List.of()));
        List<String> granted = new ArrayList<>();
        for (String id : chosen) {
            TalentNode n = findNode(kit, spec, id);
            if (n == null) continue;
            u.permanentMods.addAll(n.mods);
            if (n.passiveAura != null) passiveAuras.add(n.passiveAura);
            if (n.grantAbility != null) granted.add(n.grantAbility);
        }

        // abilities
        u.knownAbilities.clear();
        u.knownAbilities.addAll(kit.abilitiesFor(spec, level));
        for (String g : granted) if (Registry.hasAbility(g)) u.knownAbilities.add(g);
        for (Modifier m : u.permanentMods) {
            if (m.type() == ModType.GRANT_ABILITY && Registry.hasAbility(m.ref())) u.knownAbilities.add(m.ref());
            if (m.type() == ModType.REPLACE_ABILITY) {
                String[] p = m.ref().split(">");
                if (p.length == 2 && Registry.hasAbility(p[1])) u.knownAbilities.add(p[1]);
            }
        }
        // equipment: weapons, tier sets, item effects
        java.util.Map<String, Integer> itemAuraStacks = new java.util.HashMap<>();
        if (equipment != null) {
            com.wowcraft.core.combat.WeaponInfo mh = equipment.mainHand();
            com.wowcraft.core.combat.WeaponInfo oh = equipment.offHand();
            u.mainHand = mh != null ? mh : com.wowcraft.core.combat.WeaponInfo.FISTS;
            u.offHand = oh != null && com.wowcraft.core.item.Proficiency.dualWields(spec) ? oh : null;
            for (var en : equipment.setCounts().entrySet()) {
                com.wowcraft.core.item.TierSet set = com.wowcraft.core.item.ItemRegistry.set(en.getKey());
                if (set == null || set.wowClass() != spec.wowClass) continue;
                if (en.getValue() >= 2) {
                    u.permanentMods.addAll(set.bonus2());
                    if (set.aura2() != null) passiveAuras.add(set.aura2());
                }
                if (en.getValue() >= 4) {
                    u.permanentMods.addAll(set.bonus4());
                    if (set.aura4() != null) passiveAuras.add(set.aura4());
                }
            }
            u.tags.remove("trinket_1");
            u.tags.remove("trinket_2");
            for (var en : equipment.all().entrySet()) {
                com.wowcraft.core.item.ItemEffect fx = com.wowcraft.core.item.ItemRegistry.effect(en.getValue().effectId);
                if (fx == null) continue;
                int mag = fx.magnitude(en.getValue().ilvl);
                if (fx.kind() == com.wowcraft.core.item.ItemEffect.Kind.ON_USE && fx.abilityId() != null) {
                    u.knownAbilities.add(fx.abilityId());
                    u.tags.put(fx.tagKey(), mag);
                    if (en.getKey() == com.wowcraft.core.item.EquipSlot.TRINKET_1) u.tags.put("trinket_1", fx.abilityId());
                    if (en.getKey() == com.wowcraft.core.item.EquipSlot.TRINKET_2) u.tags.put("trinket_2", fx.abilityId());
                } else if (fx.auraId() != null) {
                    itemAuraStacks.merge(fx.auraId(), mag, Integer::sum);
                }
            }
        }
        u.knownAbilities.add("gladiators_medallion");
        u.knownAbilities.add("healing_potion");
        u.knownAbilities.add("damage_potion");
        u.knownAbilities.add("healthstone");

        // passives: drop old ones, apply new ones
        for (AuraInstance a : new ArrayList<>(u.auras().all())) {
            if (!a.def.passive) continue;
            Integer stacks = itemAuraStacks.get(a.def.id);
            if (!passiveAuras.contains(a.def.id) && (stacks == null || stacks != a.stacks)) engine.removeAura(a, false);
        }
        EffectContext ctx = new EffectContext(engine, u, u, null, null, null);
        for (String id : passiveAuras) {
            if (Registry.hasAura(id) && !u.auras().has(id)) engine.applyAura(ctx, u, id, 1, -1);
        }
        for (var en : itemAuraStacks.entrySet()) {
            if (Registry.hasAura(en.getKey()) && !u.auras().has(en.getKey())) engine.applyAura(ctx, u, en.getKey(), en.getValue(), -1);
        }
        // forms / stances are dropped on respec
        AuraInstance form = u.auras().formAura();
        if (form != null && resourcesChanged) engine.removeAura(form, false);

        u.invalidateMods();
        u.stats();
    }

    public static List<String> validTalents(ClassKit kit, Spec spec, int level, Collection<String> talents) {
        List<String> out = new ArrayList<>();
        if (talents == null) return out;
        if (kit.classTree != null) out.addAll(kit.classTree.sanitize(talents, level));
        TalentTree st = kit.specTrees.get(spec);
        if (st != null) out.addAll(st.sanitize(talents, level));
        if (level >= TalentTree.HERO_LEVEL) {
            for (TalentNode h : kit.heroOptions) {
                if (talents.contains(h.id)) {
                    out.add(h.id);
                    break;
                }
            }
        }
        return out;
    }

    public static TalentNode findNode(ClassKit kit, Spec spec, String id) {
        if (kit.classTree != null && kit.classTree.node(id) != null) return kit.classTree.node(id);
        TalentTree st = kit.specTrees.get(spec);
        if (st != null && st.node(id) != null) return st.node(id);
        for (TalentNode h : kit.heroOptions) if (h.id.equals(id)) return h;
        return null;
    }

    /** A reasonable default talent build: fills each tree left to right respecting gates. */
    public static List<String> defaultTalents(Spec spec, int level) {
        ClassKit kit = Content.kit(spec.wowClass);
        List<String> out = new ArrayList<>();
        for (TalentTree tree : new TalentTree[]{kit.classTree, kit.specTrees.get(spec)}) {
            if (tree == null) continue;
            java.util.LinkedHashSet<String> chosen = new java.util.LinkedHashSet<>();
            int[][] order = {{0, 0}, {0, 1}, {1, 0}, {1, 1}, {2, 0}, {0, 2}, {1, 2}, {2, 1}, {2, 2}};
            for (int[] rc : order) {
                TalentNode n = tree.at(rc[0], rc[1]);
                if (n != null && tree.canLearn(chosen, n.id, level)) chosen.add(n.id);
            }
            out.addAll(chosen);
        }
        if (level >= TalentTree.HERO_LEVEL && !kit.heroOptions.isEmpty()) out.add(kit.heroOptions.get(0).id);
        return out;
    }
}
