package com.wowcraft.core.content;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.TargetType;

/** Abilities and auras shared by every class (auto attacks, PvP trinket, potions, generic debuffs). */
public final class CommonContent {
    private CommonContent() {
    }

    public static void register() {
        Registry.register(Ability.builder("auto_attack", "Auto Attack", "Автоатака").passive().hidden().build());
        Registry.register(Ability.builder("auto_shot", "Auto Shot", "Автоматическая стрельба").passive().hidden().ranged().build());

        // PvP trinket: Gladiator's Medallion
        Registry.register(AuraDef.buff("medallion_immunity", "Gladiator's Medallion", "Медальон гладиатора")
                .duration(0.5).mod(Modifier.immune("ALL")).hidden().build());
        Registry.register(Ability.builder("gladiators_medallion", "Gladiator's Medallion", "Медальон гладиатора")
                .target(TargetType.SELF).offGcd().cooldown(120).usableWhileCc().usableWhileCasting()
                .desc("Removes all movement impairing and loss of control effects.", "Снимает все эффекты контроля и замедления.")
                .effect(Effects.custom("Removes crowd control", "Снимает контроль", ctx -> {
                    for (var a : new java.util.ArrayList<>(ctx.caster.auras().all())) {
                        if (a.def.cc != null && a.def.harmful) ctx.engine.removeAura(a, false);
                    }
                }), Effects.selfAura("medallion_immunity"))
                .tag("pvp_trinket", "defensive").vfx("holy_burst").build());

        // Potions & healthstones (used by items)
        Registry.register(Ability.builder("healing_potion", "Healing Potion", "Лечебное зелье")
                .target(TargetType.SELF).offGcd().cooldown(300).usableWhileCasting()
                .effect(Effects.healPct(0.35)).tag("potion", "defensive").vfx("heal_burst").hidden().build());
        Registry.register(AuraDef.buff("power_potion", "Tempered Potion", "Закаленное зелье").duration(30)
                .mod(Modifier.damage(0.08), Modifier.healing(0.08)).build());
        Registry.register(Ability.builder("damage_potion", "Tempered Potion", "Закаленное зелье")
                .target(TargetType.SELF).offGcd().cooldown(300).usableWhileCasting()
                .effect(Effects.selfAura("power_potion")).tag("potion", "cooldown").hidden().build());
        Registry.register(Ability.builder("healthstone", "Healthstone", "Камень здоровья")
                .target(TargetType.SELF).offGcd().cooldown(60).usableWhileCasting()
                .effect(Effects.healPct(0.25)).tag("defensive").hidden().build());

        // Bloodlust family exhaustion
        Registry.register(AuraDef.debuff("exhaustion", "Exhaustion", "Изнеможение").duration(600).persistent()
                .desc("Cannot benefit from Bloodlust-like effects.", "Невосприимчивость к эффектам «Жажды крови».")
                .perCaster(false).build());
        Registry.register(AuraDef.buff("bloodlust", "Bloodlust", "Жажда крови").duration(40)
                .mod(Modifier.haste(0.30)).desc("Haste increased by 30%.", "Скорость увеличена на 30%.").vfx("lust").build());

        // Generic debuffs used by many specs and bosses
        Registry.register(AuraDef.debuff("generic_stun", "Stunned", "Оглушение").duration(3).cc(CcType.STUN).build());
        Registry.register(AuraDef.debuff("generic_root", "Rooted", "Обездвиживание").duration(4).cc(CcType.ROOT).dispel(DispelType.MAGIC).build());
        Registry.register(AuraDef.debuff("generic_silence", "Silenced", "Немота").duration(3).cc(CcType.SILENCE).dispel(DispelType.MAGIC).build());
        Registry.register(AuraDef.debuff("generic_slow", "Slowed", "Замедление").duration(6).mod(Modifier.speed(-0.5)).cc(CcType.SLOW).build());
        Registry.register(AuraDef.debuff("generic_knockdown", "Knocked Down", "Сбит с ног").duration(1.5).cc(CcType.STUN).noPandemic().build());
        Registry.register(AuraDef.buff("immune_cc", "Unstoppable", "Неудержимость").mod(Modifier.immune("ALL")).hidden().build());
        Registry.register(AuraDef.debuff("dampening", "Dampening", "Ослабление").stacks(100).persistent().perCaster(false)
                .mod(Modifier.healingTaken(-0.01)).desc("Healing received reduced by 1% per stack.", "Получаемое исцеление уменьшено на 1% за каждый заряд.").build());
        Registry.register(AuraDef.debuff("resurrection_sickness", "Resurrection Sickness", "Слабость после воскрешения")
                .duration(30).mod(Modifier.damage(-0.5), Modifier.healing(-0.5)).build());
        Registry.register(AuraDef.debuff("deserter", "Deserter", "Дезертир").duration(900).persistent().build());
        // Food / drink style out-of-combat restore (vendor food)
        Registry.register(AuraDef.buff("well_fed", "Well Fed", "Сытость").duration(3600).persistent()
                .mod(Modifier.statFlat(com.wowcraft.core.stat.Stat.STAMINA, 40), Modifier.statFlat(com.wowcraft.core.stat.Stat.VERSATILITY, 40)).build());
    }
}
