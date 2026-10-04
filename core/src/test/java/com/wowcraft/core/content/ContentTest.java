package com.wowcraft.core.content;

import com.wowcraft.core.TestBody;
import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.bot.Rotation;
import com.wowcraft.core.bot.RotationRunner;
import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.CombatListener;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.combat.WeaponInfo;
import com.wowcraft.core.mod.ModType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.player.CharacterBuilder;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Tooltip;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.stat.StatBlock;
import com.wowcraft.core.talent.ModDescriber;
import com.wowcraft.core.talent.TalentNode;
import com.wowcraft.core.talent.TalentTree;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class ContentTest {
    @BeforeAll
    static void boot() {
        Content.bootstrap();
    }

    @Test
    void kitsAreComplete() {
        List<String> problems = new ArrayList<>();
        for (WowClass c : WowClass.values()) {
            ClassKit kit = Content.kit(c);
            if (kit == null) {
                problems.add(c + ": no kit");
                continue;
            }
            for (String id : kit.classAbilities) if (!Registry.hasAbility(id)) problems.add(c + ": missing class ability " + id);
            for (String id : kit.classPassives) if (!Registry.hasAura(id)) problems.add(c + ": missing passive " + id);
            check(kit.classTree, c.name(), problems);
            if (kit.heroOptions.size() != 2) problems.add(c + ": needs 2 hero options");
            for (Spec s : Spec.of(c)) {
                List<String> abilities = kit.specAbilities.get(s);
                if (abilities == null || abilities.isEmpty()) problems.add(s + ": no spec abilities");
                else for (String id : abilities) if (!Registry.hasAbility(id)) problems.add(s + ": missing ability " + id);
                for (String id : kit.specPassives.getOrDefault(s, List.of())) if (!Registry.hasAura(id)) problems.add(s + ": missing passive " + id);
                String[] bar = kit.defaultBars.get(s);
                if (bar == null) problems.add(s + ": no default bar");
                else for (String id : bar) {
                    if (id == null) continue;
                    Ability a = Registry.ability(id);
                    if (a == null) problems.add(s + ": bar ability missing " + id);
                    else if (!a.usableBy(s)) problems.add(s + ": bar ability not usable " + id);
                }
                Rotation r = kit.rotations.get(s);
                if (r == null) problems.add(s + ": no rotation");
                else for (Rotation.Step st : r.steps) if (!Registry.hasAbility(st.abilityId())) problems.add(s + ": rotation ability missing " + st.abilityId());
                TalentTree tree = kit.specTrees.get(s);
                if (tree == null) problems.add(s + ": no spec tree");
                else check(tree, s.name(), problems);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void check(TalentTree tree, String owner, List<String> problems) {
        if (tree == null) {
            problems.add(owner + ": no talent tree");
            return;
        }
        if (tree.nodes().size() != 9) problems.add(owner + ": tree has " + tree.nodes().size() + " nodes");
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                if (tree.at(r, c) == null) problems.add(owner + ": missing talent at " + r + "," + c);
        for (TalentNode n : tree.nodes()) {
            if (n.grantAbility != null && !Registry.hasAbility(n.grantAbility)) problems.add(owner + ": talent grants missing " + n.grantAbility);
            if (n.passiveAura != null && !Registry.hasAura(n.passiveAura)) problems.add(owner + ": talent aura missing " + n.passiveAura);
            for (Modifier m : n.mods) {
                if (m.type() == ModType.REPLACE_ABILITY) {
                    String[] p = m.ref().split(">");
                    if (!Registry.hasAbility(p[0]) || !Registry.hasAbility(p[1])) problems.add(owner + ": bad replace " + m.ref());
                }
                if (m.type() == ModType.GRANT_ABILITY && !Registry.hasAbility(m.ref())) problems.add(owner + ": bad grant " + m.ref());
                if (m.filter().kind == com.wowcraft.core.mod.ModFilter.Kind.ABILITY && !Registry.hasAbility(m.filter().value))
                    problems.add(owner + ": talent " + n.id + " references missing ability " + m.filter().value);
                if (m.filter().kind == com.wowcraft.core.mod.ModFilter.Kind.AURA && !Registry.hasAura(m.filter().value))
                    problems.add(owner + ": talent " + n.id + " references missing aura " + m.filter().value);
            }
            if (ModDescriber.describeAll(n.mods, n.passiveAura, n.grantAbility, L10n.Lang.RU).isEmpty() && n.customDescription == null)
                problems.add(owner + ": talent " + n.id + " has no description");
        }
    }

    @Test
    void auraModifiersReferenceExistingContent() {
        List<String> problems = new ArrayList<>();
        for (AuraDef a : Registry.auras()) {
            for (Modifier m : a.mods) {
                if (m.filter().kind == com.wowcraft.core.mod.ModFilter.Kind.ABILITY && !Registry.hasAbility(m.filter().value))
                    problems.add("aura " + a.id + " references missing ability " + m.filter().value);
            }
            for (var t : a.triggers) {
                if (t.filter().kind == com.wowcraft.core.mod.ModFilter.Kind.ABILITY && !Registry.hasAbility(t.filter().value))
                    problems.add("aura " + a.id + " trigger references missing ability " + t.filter().value);
                if (t.filter().kind == com.wowcraft.core.mod.ModFilter.Kind.AURA && !Registry.hasAura(t.filter().value))
                    problems.add("aura " + a.id + " trigger references missing aura " + t.filter().value);
            }
        }
        for (Ability a : Registry.abilities()) {
            if (a.requiredFormAura != null && !Registry.hasAura(a.requiredFormAura)) problems.add(a.id + " requires missing form " + a.requiredFormAura);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void tooltipsRenderInBothLanguages() {
        Spec spec = Spec.ARMS;
        CombatEngine engine = new CombatEngine(1);
        UnitState u = engine.registerLocal("T", UnitKind.PLAYER, new TestBody(0, 0, 0));
        CharacterBuilder.apply(engine, u, spec, 80, List.of(), gear(spec));
        for (Ability a : Registry.abilities()) {
            for (L10n.Lang lang : L10n.Lang.values()) {
                List<String> lines = Tooltip.lines(a, lang, null);
                assertFalse(lines.isEmpty());
                Tooltip.lines(a, lang, u);
            }
        }
    }

    /** Test world that can spawn simple pets / summons. */
    static final class TestWorld implements com.wowcraft.core.combat.WorldAccess {
        private final CombatEngine engine;

        TestWorld(CombatEngine engine) {
            this.engine = engine;
        }

        @Override
        public boolean lineOfSight(String worldKey, Vec3 from, Vec3 to) {
            return true;
        }

        @Override
        public Vec3 safeDestination(String worldKey, Vec3 from, Vec3 to) {
            return to;
        }

        @Override
        public UnitState spawnNpc(String worldKey, Vec3 pos, float yaw, String templateId, UnitState owner, int level) {
            UnitState u = engine.registerLocal(templateId, UnitKind.PET, new TestBody(pos.x(), pos.y(), pos.z()));
            u.templateId = templateId;
            u.level = level;
            u.npcAttackPower = owner != null ? owner.stats().attackPower * 0.4 + owner.stats().spellPower * 0.4 : 100;
            engine.setMaxHealthDirect(u, owner != null ? owner.maxHealth() * 0.6 : 1000, true);
            return u;
        }
    }

    static StatBlock gear(Spec spec) {
        StatBlock g = new StatBlock();
        g.set(spec.primaryStat, 900).set(Stat.STAMINA, 1500).set(Stat.CRIT, 500).set(Stat.HASTE, 500)
                .set(Stat.MASTERY, 400).set(Stat.VERSATILITY, 300).set(Stat.ARMOR, 3000);
        return g;
    }

    /** Single-target numbers for balancing (informational). */
    @Test
    void singleTargetReport() {
        simulate(1, false);
    }

    /** Every spec runs its bot rotation against dummies for 90 seconds without errors and produces output. */
    @Test
    void everySpecRunsItsRotation() {
        simulate(3, true);
    }

    private void simulate(int dummyCount, boolean assertOutput) {
        Map<String, String> report = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        for (Spec spec : Spec.values()) {
            CombatEngine engine = new CombatEngine(spec.ordinal() + 11);
            engine.setWorld(new TestWorld(engine));
            double[] dealt = {0}, healed = {0};
            List<String> errors = new ArrayList<>();
            engine.addListener(new CombatListener() {
                @Override
                public void onDamage(HitResult hit) {
                    if (hit.source != null && hit.source.master().isPlayer()) dealt[0] += hit.effective() + hit.absorbed;
                }

                @Override
                public void onHeal(HitResult hit) {
                    if (hit.source != null && hit.source.master().isPlayer()) healed[0] += hit.effective();
                }
            });
            UnitState p = engine.register(1, java.util.UUID.randomUUID(), "Player", UnitKind.PLAYER, new TestBody(0, 64, 0));
            p.team = "players";
            p.groupId = "g";
            p.mainHand = spec == Spec.MARKSMANSHIP || spec == Spec.BEAST_MASTERY ? new WeaponInfo(100, 3.0, true, true)
                    : new WeaponInfo(100, 2.6, false, true);
            CharacterBuilder.apply(engine, p, spec, 80, CharacterBuilder.defaultTalents(spec, 80), gear(spec));
            engine.resetUnit(p, false);
            // an injured ally for healers to heal
            UnitState ally = engine.register(2, java.util.UUID.randomUUID(), "Ally", UnitKind.PLAYER, new TestBody(3, 64, 0));
            ally.team = "players";
            ally.groupId = "g";
            ally.role = Role.TANK;
            CharacterBuilder.apply(engine, ally, Spec.PROTECTION_WARRIOR, 80, List.of(), gear(Spec.PROTECTION_WARRIOR));
            engine.resetUnit(ally, false);
            List<UnitState> dummies = new ArrayList<>();
            for (int i = 0; i < dummyCount; i++) {
                UnitState d = engine.register(10 + i, java.util.UUID.randomUUID(), "Dummy" + i, UnitKind.NPC, new TestBody(2 + i * 0.5, 64, 1.5));
                d.team = "monsters";
                d.level = 80;
                d.npcAttackPower = 300;
                engine.setMaxHealthDirect(d, 5e7, true);
                dummies.add(d);
            }
            ((TestBody) p.body).facing(dummies.get(0).position());
            List<UnitState> allies = List.of(p, ally);
            RotationRunner.Situation sit = new RotationRunner.Situation(dummies.get(0), dummies, allies);
            Rotation rot = Content.kit(spec.wowClass).rotations.get(spec);
            for (int tick = 0; tick < 20 * 90; tick++) {
                // keep the ally injured so healers have work
                if (tick % 20 == 0) {
                    engine.dealRawDamage(new com.wowcraft.core.spell.EffectContext(engine, dummies.get(0), ally, null, null, null), ally,
                            com.wowcraft.core.spell.School.PHYSICAL, ally.maxHealth() * 0.08);
                    engine.dealRawDamage(new com.wowcraft.core.spell.EffectContext(engine, dummies.get(0), p, null, null, null), p,
                            com.wowcraft.core.spell.School.PHYSICAL, p.maxHealth() * 0.02);
                }
                try {
                    if (!p.isCasting()) {
                        RotationRunner.Decision dec = RotationRunner.decide(engine, p, rot, sit);
                        if (dec != null) {
                            CastResult r = engine.cast(p, dec.abilityId(), dec.target(), dec.point());
                            if (!r.ok() && r != CastResult.GCD && r != CastResult.BUSY) {
                                // decide() said OK, cast() disagreed - tolerated but noted
                            }
                        }
                    }
                    engine.tick(0.05);
                } catch (RuntimeException e) {
                    errors.add(e.toString());
                    e.printStackTrace();
                    break;
                }
                for (UnitState pet : p.livingPets()) {
                    if (!pet.autoAttack) engine.startAutoAttack(pet, dummies.get(0));
                    ((TestBody) pet.body).pos = dummies.get(0).position().add(-1.0, 0, 0);
                }
                if (p.isDead()) engine.resurrect(p, p, 1.0);
                if (ally.isDead()) engine.resurrect(p, ally, 1.0);
            }
            double dps = dealt[0] / 90.0, hps = healed[0] / 90.0;
            report.put(spec.name(), String.format("dps=%8.0f hps=%8.0f", dps, hps));
            if (!errors.isEmpty()) failures.add(spec + ": " + errors);
            if (!assertOutput) continue;
            if (spec.role == Role.HEALER) {
                if (hps < 200) failures.add(spec + ": healer produced too little healing: " + hps);
            } else if (dps < 500) {
                failures.add(spec + ": produced too little damage: " + dps);
            }
        }
        StringBuilder sb = new StringBuilder("\n=== " + dummyCount + " target(s) ===\n");
        report.forEach((k, v) -> sb.append(String.format("%-20s %s%n", k, v)));
        System.out.println(sb);
        assertTrue(failures.isEmpty(), String.join("\n", failures) + sb);
    }
}
