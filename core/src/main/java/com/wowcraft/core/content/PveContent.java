package com.wowcraft.core.content;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.npc.BodyType;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcSpell;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;

/** Shared PvE content: boss enrage, Mythic+ affix auras and helper NPCs, common NPC abilities. */
public final class PveContent {
    private PveContent() {
    }

    public static void register() {
        Registry.register(AuraDef.buff("boss_enrage", "Enrage", "Бешенство").mod(Modifier.damage(2.0), Modifier.haste(0.5)).persistent()
                .desc("Damage dealt increased by 200%.", "Наносимый урон увеличен на 200%.").vfx("enrage").build());

        // ---- Mythic+ affix auras
        Registry.register(AuraDef.buff("bolstering", "Bolster", "Усиление").stacks(20).persistent().mod(Modifier.maxHealth(0.2), Modifier.damage(0.2))
                .desc("Health and damage increased by 20% per stack.", "Здоровье и урон увеличены на 20% за каждый заряд.").vfx("bolster").build());
        Registry.register(AuraDef.buff("raging", "Raging", "Бушующий").persistent().dispel(DispelType.ENRAGE)
                .mod(Modifier.damage(0.5), Modifier.immune("ALL")).desc("Damage dealt increased by 50%.", "Наносимый урон увеличен на 50%.").vfx("enrage").build());
        Registry.register(AuraDef.debuff("bursting", "Burst", "Взрыв").duration(4).stacks(99).perCaster(false).dispel(DispelType.MAGIC)
                .periodic(1, Effects.rawDamage(School.PHYSICAL, Scaling.targetHp(0.01))).unhastedTicks()
                .desc("Taking 1% maximum health damage per second per stack.", "Получает урон в размере 1% макс. здоровья в секунду за заряд.").build());
        Registry.register(AuraDef.debuff("necrotic_wound", "Necrotic Wound", "Некротическая рана").duration(9).stacks(99).perCaster(false)
                .mod(Modifier.healingTaken(-0.02)).desc("Healing received reduced by 2% per stack.", "Получаемое исцеление уменьшено на 2% за заряд.").build());
        Registry.register(AuraDef.debuff("grievous_wound", "Grievous Wound", "Мучительная рана").stacks(10).perCaster(false).persistent()
                .periodic(3, Effects.rawDamage(School.PHYSICAL, Scaling.targetHp(0.015))).unhastedTicks()
                .desc("Taking damage until healed above 90% health.", "Получает урон, пока здоровье не поднимется выше 90%.").build());
        Registry.register(AuraDef.debuff("entangled", "Entangled", "Опутан").duration(6).cc(CcType.ROOT).breakOnDamage(0.0).perCaster(false).build());
        Registry.register(AuraDef.debuff("afflicted_cry", "Cry of the Afflicted", "Крик страдающего").duration(8).perCaster(false)
                .dispel(DispelType.MAGIC).onExpire(Effects.aura("afflicted_stun"))
                .desc("Stuns when it expires unless dispelled.", "Оглушает по окончании действия, если не будет рассеян.").build());
        Registry.register(AuraDef.debuff("afflicted_stun", "Afflicted", "Страдание").duration(4).cc(CcType.STUN).perCaster(false).noPandemic().build());
        Registry.register(AuraDef.debuff("destabilized", "Destabilize", "Дестабилизация").duration(10).perCaster(false).mod(Modifier.taken(0.1)).build());
        Registry.register(AuraDef.buff("void_empowered", "Void Empowered", "Усиление Бездны").stacks(10).duration(60).perCaster(false)
                .mod(Modifier.damage(0.1)).build());
        Registry.register(AuraDef.buff("void_pulsar_power", "Pulsar Power", "Сила пульсара").duration(20).mod(Modifier.haste(0.15), Modifier.damage(0.05)).build());
        Registry.register(AuraDef.debuff("spiteful_fixate", "Fixate", "Преследование").duration(20).perCaster(false).build());

        Registry.register(Ability.builder("explosive_detonate", "Explosive Detonation", "Взрывная детонация").target(TargetType.NONE).school(School.FIRE)
                .cast(0.5).effect(Effects.custom("Deals 15% max health to all players", "Наносит урон в размере 15% макс. здоровья всем игрокам", ctx -> {
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 60)) {
                        ctx.engine.dealRawDamage(ctx.withTarget(u), u, School.FIRE, u.maxHealth() * 0.15);
                    }
                    ctx.engine.die(ctx.caster, null);
                })).vfx("explosion").build());
        Registry.register(Ability.builder("destabilize", "Destabilize", "Дестабилизация").school(School.SHADOW).ranged().cast(4)
                .effect(Effects.custom("Damages all players and increases their damage taken", "Наносит урон всем игрокам и увеличивает получаемый ими урон", ctx -> {
                    for (UnitState u : ctx.engine.enemiesAround(ctx.caster, ctx.caster.position(), 60)) {
                        ctx.engine.dealRawDamage(ctx.withTarget(u), u, School.SHADOW, u.maxHealth() * 0.12);
                        ctx.engine.applyAura(ctx.withTarget(u), u, "destabilized", 1, -1);
                    }
                })).vfx("void_burst").build());
        Registry.register(Ability.builder("shade_strike", "Spiteful Strike", "Злобный удар").school(School.SHADOW).melee().cooldown(1.5)
                .effect(Effects.rawDamage(School.SHADOW, Scaling.targetHp(0.04))).build());

        NpcRegistry.register(new NpcTemplate("explosive_orb", "Explosive Orb", "Взрывоопасная сфера", NpcRank.MINION).body(BodyType.ELEMENTAL)
                .texture("blaze").scale(0.6).hp(0.01).dmg(0).stationary().forces(0).spell(NpcSpell.of("explosive_detonate", 99, 6).on(NpcSpell.Target.SELF)));
        NpcRegistry.register(new NpcTemplate("spiteful_shade", "Spiteful Shade", "Злобная тень", NpcRank.MINION).texture("shade").tint(0xFF6020A0)
                .hp(0.3).dmg(0.8).speed(0.22).forces(0).spell(NpcSpell.of("shade_strike", 1.5, 0.5)));
        NpcRegistry.register(new NpcTemplate("incorporeal_being", "Incorporeal Being", "Бесплотное существо", NpcRank.NORMAL).body(BodyType.ELEMENTAL)
                .texture("vex").tint(0xFFB0B0FF).hp(0.6).dmg(0.5).ranged(20).forces(0).spell(NpcSpell.of("destabilize", 8, 2)));
        NpcRegistry.register(new NpcTemplate("void_emissary", "Void Emissary", "Эмиссар Бездны", NpcRank.MINION).body(BodyType.ELEMENTAL)
                .texture("vex").tint(0xFF6C3483).hp(0.25).dmg(0).speed(0.15).forces(0));
    }
}
