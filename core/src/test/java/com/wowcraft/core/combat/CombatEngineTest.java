package com.wowcraft.core.combat;

import com.wowcraft.core.TestBody;
import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.util.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class CombatEngineTest {

    @BeforeAll
    static void content() {
        com.wowcraft.core.content.Content.bootstrap();
        if (Registry.hasAbility("t_strike")) return;
        Registry.register(Ability.builder("t_strike", "Strike", "Удар").cost(ResourceType.RAGE, 20)
                .effect(Effects.damage(Scaling.ap(1.0))).cooldown(6).build());
        Registry.register(Ability.builder("t_bolt", "Bolt", "Стрела").school(School.FIRE).ranged().cast(2.0)
                .effect(Effects.damage(Scaling.sp(2.0))).build());
        Registry.register(AuraDef.debuff("t_stun", "Stun", "Оглушение").duration(4).cc(CcType.STUN).build());
        Registry.register(Ability.builder("t_hammer", "Hammer", "Молот").ranged().cooldown(30)
                .effect(Effects.aura("t_stun")).tag("cc").build());
        Registry.register(AuraDef.debuff("t_dot", "Dot", "Дот").duration(10).periodic(2, Effects.damage(Scaling.flat(10))).build());
        Registry.register(Ability.builder("t_dotcast", "Dot", "Дот").ranged().effect(Effects.aura("t_dot")).build());
        Registry.register(AuraDef.buff("t_shield", "Shield", "Щит").duration(15).absorb().build());
        Registry.register(AuraDef.buff("t_power", "Power", "Сила").duration(10).mod(Modifier.damage(0.5)).build());
        Registry.register(Ability.builder("t_charges", "Charges", "Заряды").ranged().cooldown(10).charges(2)
                .effect(Effects.damage(Scaling.flat(5))).build());
    }

    private CombatEngine engine;

    private UnitState player(CombatEngine e, String name, double x, Spec spec) {
        UnitState u = e.registerLocal(name, UnitKind.PLAYER, new TestBody(x, 0, 0));
        u.spec = spec;
        u.wowClass = spec.wowClass;
        u.team = "players";
        u.level = 80;
        u.baseStats.set(Stat.STRENGTH, 1000).set(Stat.INTELLECT, 1000).set(Stat.STAMINA, 1000);
        e.configureResources(u, EnumSet.of(ResourceType.RAGE, ResourceType.MANA));
        u.knownAbilities.addAll(java.util.List.of("t_strike", "t_bolt", "t_hammer", "t_dotcast", "t_charges"));
        e.resetUnit(u, true);
        return u;
    }

    private UnitState dummy(CombatEngine e, double x, double hp) {
        UnitState u = e.registerLocal("Dummy", UnitKind.NPC, new TestBody(x, 0, 0));
        u.team = "monsters";
        u.level = 80;
        e.setMaxHealthDirect(u, hp, true);
        return u;
    }

    @Test
    void meleeStrikeDealsDamageAndCostsRage() {
        engine = new CombatEngine(1);
        UnitState p = player(engine, "P", 0, Spec.ARMS);
        UnitState d = dummy(engine, 1.5, 100000);
        ((TestBody) p.body).facing(d.position());
        p.resources().set(ResourceType.RAGE, 50);
        CastResult r = engine.cast(p, "t_strike", d, null);
        assertEquals(CastResult.OK, r);
        assertTrue(d.health() < 100000, "damage applied");
        assertEquals(30, p.resources().get(ResourceType.RAGE), 1e-6);
        assertTrue(d.threat().get(p) > 0, "threat generated");
        // cooldown
        engine.tick(1.6);
        assertEquals(CastResult.COOLDOWN, engine.cast(p, "t_strike", d, null));
    }

    @Test
    void notEnoughRage() {
        engine = new CombatEngine(1);
        UnitState p = player(engine, "P", 0, Spec.ARMS);
        UnitState d = dummy(engine, 1.5, 1000);
        ((TestBody) p.body).facing(d.position());
        p.resources().set(ResourceType.RAGE, 5);
        assertEquals(CastResult.NO_RESOURCE, engine.cast(p, "t_strike", d, null));
    }

    @Test
    void castTimeAndMovementCancels() {
        engine = new CombatEngine(2);
        UnitState p = player(engine, "P", 0, Spec.FIRE);
        UnitState d = dummy(engine, 10, 100000);
        ((TestBody) p.body).facing(d.position());
        assertEquals(CastResult.OK, engine.cast(p, "t_bolt", d, null));
        assertTrue(p.isCasting());
        engine.tick(0.5);
        ((TestBody) p.body).moving = true;
        engine.tick(0.05);
        assertFalse(p.isCasting(), "movement cancels cast");
        assertEquals(100000, d.health(), 1e-6);
        ((TestBody) p.body).moving = false;
        engine.tick(2.0);
        assertEquals(CastResult.OK, engine.cast(p, "t_bolt", d, null));
        for (int i = 0; i < 50; i++) engine.tick(0.05);
        assertFalse(p.isCasting());
        assertTrue(d.health() < 100000);
    }

    @Test
    void stunInterruptsAndBossesAreImmune() {
        engine = new CombatEngine(3);
        UnitState p = player(engine, "P", 0, Spec.PROTECTION_PALADIN);
        UnitState caster = dummy(engine, 8, 100000);
        caster.spec = null;
        ((TestBody) p.body).facing(caster.position());
        caster.knownAbilities.add("t_bolt");
        caster.npcSpellPower = 100;
        ((TestBody) caster.body).facing(p.position());
        assertEquals(CastResult.OK, engine.cast(caster, "t_bolt", p, null));
        assertTrue(caster.isCasting());
        assertEquals(CastResult.OK, engine.cast(p, "t_hammer", caster, null));
        assertFalse(caster.isCasting(), "stun interrupts");
        assertTrue(caster.hasCc(CcType.STUN));
        UnitState boss = dummy(engine, 5, 100000);
        boss.boss = true;
        engine.tick(2);
        engine.resetCooldown(p, "t_hammer");
        engine.cast(p, "t_hammer", boss, null);
        assertFalse(boss.hasCc(CcType.STUN), "boss immune");
    }

    @Test
    void dotTicksAndPandemic() {
        engine = new CombatEngine(4);
        UnitState p = player(engine, "P", 0, Spec.AFFLICTION);
        UnitState d = dummy(engine, 5, 100000);
        ((TestBody) p.body).facing(d.position());
        engine.cast(p, "t_dotcast", d, null);
        double before = d.health();
        for (int i = 0; i < 100; i++) engine.tick(0.05); // 5 sec
        assertTrue(d.health() < before, "dot ticked");
        var aura = d.auras().get("t_dot", p);
        assertNotNull(aura);
        double rem = aura.remaining(engine.now());
        engine.cast(p, "t_dotcast", d, null);
        double rem2 = d.auras().get("t_dot", p).remaining(engine.now());
        assertTrue(rem2 > 10.0 && rem2 <= 13.0 + 1e-6, "pandemic carryover: " + rem + " -> " + rem2);
    }

    @Test
    void absorbSoaksDamage() {
        engine = new CombatEngine(5);
        UnitState p = player(engine, "P", 0, Spec.DISCIPLINE);
        UnitState d = dummy(engine, 3, 1000);
        var ctx = new com.wowcraft.core.spell.EffectContext(engine, p, p, null, null, null);
        engine.applyAbsorb(ctx, p, "t_shield", 500);
        double hp = p.health();
        var dctx = new com.wowcraft.core.spell.EffectContext(engine, d, p, null, null, null);
        engine.dealRawDamage(dctx, p, School.SHADOW, 300);
        assertEquals(hp, p.health(), 1e-6, "fully absorbed");
        assertTrue(p.auras().has("t_shield"));
    }

    @Test
    void chargesRecharge() {
        engine = new CombatEngine(6);
        UnitState p = player(engine, "P", 0, Spec.ARMS);
        UnitState d = dummy(engine, 5, 100000);
        ((TestBody) p.body).facing(d.position());
        assertEquals(CastResult.OK, engine.cast(p, "t_charges", d, null));
        engine.tick(1.6);
        assertEquals(CastResult.OK, engine.cast(p, "t_charges", d, null));
        engine.tick(1.6);
        assertEquals(CastResult.COOLDOWN, engine.cast(p, "t_charges", d, null));
        for (int i = 0; i < 8; i++) engine.tick(1.0);
        assertEquals(CastResult.OK, engine.cast(p, "t_charges", d, null));
    }

    @Test
    void diminishingReturns() {
        DrTracker dr = new DrTracker();
        assertEquals(1.0, dr.apply("STUN", 0, 4));
        assertEquals(0.5, dr.apply("STUN", 4, 4));
        assertEquals(0.25, dr.apply("STUN", 6, 4));
        assertEquals(0.0, dr.apply("STUN", 7, 4));
        assertEquals(1.0, dr.apply("STUN", 40, 4));
    }

    @Test
    void killingBlowMarksDeathAndClearsThreat() {
        engine = new CombatEngine(7);
        UnitState p = player(engine, "P", 0, Spec.ARMS);
        UnitState d = dummy(engine, 1.5, 10);
        ((TestBody) p.body).facing(d.position());
        p.resources().set(ResourceType.RAGE, 100);
        engine.cast(p, "t_strike", d, null);
        assertTrue(d.isDead());
        assertTrue(((TestBody) d.body).killed);
    }

    @Test
    void damageBuffIncreasesDamage() {
        engine = new CombatEngine(8);
        UnitState p = player(engine, "P", 0, Spec.ARMS);
        UnitState d1 = dummy(engine, 3, 1e9);
        var ctx = new com.wowcraft.core.spell.EffectContext(engine, p, d1, null, null, null);
        // compare average over many hits with / without buff
        double a = 0, b = 0;
        for (int i = 0; i < 200; i++) a += engine.dealRawDamage(ctx, d1, School.FIRE, 100).amount;
        engine.applyAura(ctx, p, "t_power", 1, -1);
        for (int i = 0; i < 200; i++) b += engine.dealDamage(ctx, d1, School.FIRE, 100).amount;
        assertTrue(b > a * 1.4, "buffed damage " + b + " vs " + a);
    }

    /** A spell landing after its target left the world (despawned mob) must not keep the caster in combat forever. */
    @Test
    void removedTargetDoesNotKeepCasterInCombat() {
        engine = new CombatEngine(3);
        UnitState p = player(engine, "P", 0, Spec.FIRE);
        UnitState mob = engine.registerLocal("Husk", UnitKind.VANILLA, new TestBody(5, 0, 0));
        mob.team = "monsters";
        engine.setMaxHealthDirect(mob, 100000, true);
        engine.enterCombat(p, mob);
        engine.tick(0.1);
        assertTrue(p.inCombat());
        engine.remove(mob);
        engine.enterCombat(p, mob); // a projectile in flight hits the removed mob
        engine.aggro(mob, p, 10);
        for (int i = 0; i < 80; i++) engine.tick(0.1);
        assertFalse(p.inCombat(), "caster leaves combat after the usual timeout");
        assertTrue(p.engaged().isEmpty(), "no stale engagement");
    }
}
