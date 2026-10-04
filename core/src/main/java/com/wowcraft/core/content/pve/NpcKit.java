package com.wowcraft.core.content.pve;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.instance.RoomDef;
import com.wowcraft.core.item.EquipType;
import com.wowcraft.core.item.ItemRegistry;
import com.wowcraft.core.item.ItemTemplate;
import com.wowcraft.core.item.WeaponType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.npc.BodyType;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcSpell;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Small DSL for PvE content: NPC abilities (strikes, interruptible casts, cleaves, heals), debuffs,
 * NPC templates, dungeon definitions and named loot.
 * Damage uses NPC attack power: ap(1.0) = one auto-attack swing of that NPC.
 */
public abstract class NpcKit {
    protected static final List<Difficulty> DUNGEON_DIFFS = List.of(Difficulty.NORMAL, Difficulty.HEROIC, Difficulty.MYTHIC, Difficulty.MYTHIC_PLUS);
    protected static final List<Difficulty> RAID_DIFFS = List.of(Difficulty.LFR, Difficulty.RAID_NORMAL, Difficulty.RAID_HEROIC, Difficulty.RAID_MYTHIC);

    // ------------------------------------------------------------------ abilities

    /** Melee special attack. */
    protected static void strike(String id, String en, String ru, School school, double ap, Effect... extra) {
        Ability.Builder b = Ability.builder(id, en, ru).school(school).melee().gcd(0).noFacing().uninterruptible()
                .effect(Effects.damage(school, Scaling.ap(ap)));
        if (extra.length > 0) b.effect(extra);
        Registry.register(b.vfx("npc_strike").build());
    }

    /** Interruptible single target spell (kick it!). */
    protected static void bolt(String id, String en, String ru, School school, double ap, double cast, Effect... extra) {
        Ability.Builder b = Ability.builder(id, en, ru).school(school).range(40).cast(cast).gcd(0).noFacing().tag("spell")
                .effect(Effects.damage(school, Scaling.ap(ap)));
        if (extra.length > 0) b.effect(extra);
        Registry.register(b.vfx(school.name().toLowerCase() + "_bolt").build());
    }

    /** Interruptible AoE around the caster that hits every player in range. */
    protected static void nova(String id, String en, String ru, School school, double ap, double radius, double cast, boolean interruptible,
                               Effect... extra) {
        List<Effect> fx = new ArrayList<>();
        fx.add(Effects.damage(school, Scaling.ap(ap)));
        for (Effect e : extra) fx.add(e);
        Ability.Builder b = Ability.builder(id, en, ru).school(school).target(TargetType.NONE).cast(cast).gcd(0).tag("spell", "aoe")
                .effect(Effects.aroundSelf(radius, fx.toArray(new Effect[0])));
        if (!interruptible) b.uninterruptible();
        Registry.register(b.vfx("nova_" + school.name().toLowerCase()).build());
    }

    /** Frontal cone (dodge it by moving behind / out). */
    protected static void cleave(String id, String en, String ru, School school, double ap, double angle, double range, double cast,
                                 Effect... extra) {
        List<Effect> fx = new ArrayList<>();
        fx.add(Effects.damage(school, Scaling.ap(ap)));
        for (Effect e : extra) fx.add(e);
        Registry.register(Ability.builder(id, en, ru).school(school).range(range).cast(cast).gcd(0).uninterruptible().noFacing()
                .effect(Effects.cone(angle, range, fx.toArray(new Effect[0]))).vfx("cleave").build());
    }

    /** Interruptible heal on the most injured ally. */
    protected static void mend(String id, String en, String ru, double fraction, double cast) {
        Registry.register(Ability.builder(id, en, ru).school(School.HOLY).target(TargetType.FRIENDLY).range(40).cast(cast).gcd(0).noFacing()
                .tag("spell", "heal").effect(Effects.custom("Heals an ally for " + Math.round(fraction * 100) + "% of its health",
                        "Исцеляет союзника на " + Math.round(fraction * 100) + "% здоровья",
                        ctx -> ctx.engine.rawHeal(ctx.caster, ctx.target, ctx.target.maxHealth() * fraction)))
                .vfx("heal").build());
    }

    /** Applies a debuff / buff to the target (with an optional cast). */
    protected static void afflict(String id, String en, String ru, School school, String auraId, double cast, double range, boolean interruptible) {
        Ability.Builder b = Ability.builder(id, en, ru).school(school).range(range).gcd(0).noFacing().tag("spell").effect(Effects.aura(auraId));
        if (cast > 0) b.cast(cast);
        if (!interruptible) b.uninterruptible();
        Registry.register(b.vfx("debuff").build());
    }

    /** Self buff (e.g. an enrage to soothe or a shield to purge). */
    protected static void selfBuff(String id, String en, String ru, String auraId, double cast) {
        Ability.Builder b = Ability.builder(id, en, ru).target(TargetType.SELF).gcd(0).effect(Effects.selfAura(auraId)).tag("spell");
        if (cast > 0) b.cast(cast);
        Registry.register(b.vfx("buff").build());
    }

    /** Buffs an ally (e.g. a shield, haste) — the purge / spellsteal target. */
    protected static void allyBuff(String id, String en, String ru, String auraId, double cast) {
        Ability.Builder b = Ability.builder(id, en, ru).target(TargetType.FRIENDLY).range(40).gcd(0).noFacing().effect(Effects.aura(auraId)).tag("spell");
        if (cast > 0) b.cast(cast);
        Registry.register(b.vfx("buff").build());
    }

    /** Ground effect at the target's position. */
    protected static void groundZone(String id, String en, String ru, School school, double apPerTick, double radius, double duration, double cast) {
        Registry.register(Ability.builder(id, en, ru).school(school).target(TargetType.GROUND).range(40).cast(cast).gcd(0).noFacing().tag("spell")
                .effect(Effects.area(com.wowcraft.core.combat.GroundArea.Def.enemies(id, radius, duration, 1.0,
                        Effects.damage(school, Scaling.ap(apPerTick)))))
                .vfx("ground_" + school.name().toLowerCase()).build());
    }

    /** Charge to a random player and hit. */
    protected static void charge(String id, String en, String ru, double ap, Effect... extra) {
        List<Effect> fx = new ArrayList<>();
        fx.add(Effects.charge());
        fx.add(Effects.damage(School.PHYSICAL, Scaling.ap(ap)));
        for (Effect e : extra) fx.add(e);
        Registry.register(Ability.builder(id, en, ru).range(30).gcd(0).noFacing().uninterruptible().effect(fx.toArray(new Effect[0])).vfx("charge").build());
    }

    // ------------------------------------------------------------------ auras

    protected static void dot(String id, String en, String ru, School school, DispelType dispel, double apPerTick, double duration, int maxStacks) {
        AuraDef.Builder b = AuraDef.debuff(id, en, ru).school(school).duration(duration).periodic(2, Effects.damage(school, Scaling.ap(apPerTick)))
                .unhastedTicks().perCaster(false);
        if (dispel != null) b.dispel(dispel);
        if (maxStacks > 1) b.stacks(maxStacks);
        b.desc("Taking " + school.name().toLowerCase() + " damage every 2 sec.", "Получает периодический урон каждые 2 сек.");
        Registry.register(b.build());
    }

    protected static void cc(String id, String en, String ru, CcType type, DispelType dispel, double duration) {
        AuraDef.Builder b = AuraDef.debuff(id, en, ru).duration(duration).cc(type).perCaster(false);
        if (dispel != null) b.dispel(dispel);
        Registry.register(b.build());
    }

    protected static void debuffMod(String id, String en, String ru, DispelType dispel, double duration, int stacks, String descEn, String descRu,
                                    Modifier... mods) {
        AuraDef.Builder b = AuraDef.debuff(id, en, ru).duration(duration).mod(mods).perCaster(false).desc(descEn, descRu);
        if (dispel != null) b.dispel(dispel);
        if (stacks > 1) b.stacks(stacks);
        Registry.register(b.build());
    }

    protected static void buffMod(String id, String en, String ru, DispelType dispel, double duration, String descEn, String descRu, Modifier... mods) {
        AuraDef.Builder b = AuraDef.buff(id, en, ru).duration(duration).mod(mods).desc(descEn, descRu);
        if (dispel != null) b.dispel(dispel);
        Registry.register(b.build());
    }

    protected static void shieldAura(String id, String en, String ru, DispelType dispel, double duration) {
        AuraDef.Builder b = AuraDef.buff(id, en, ru).duration(duration).absorb();
        if (dispel != null) b.dispel(dispel);
        Registry.register(b.build());
    }

    // ------------------------------------------------------------------ templates

    protected static NpcTemplate npc(String id, String en, String ru, NpcRank rank, BodyType body, String texture, int tint) {
        NpcTemplate t = new NpcTemplate(id, en, ru, rank).body(body).texture(texture).tint(tint);
        t.model("wowcraft:npc/" + id, "wowcraft:" + body.name().toLowerCase());
        NpcRegistry.register(t);
        return t;
    }

    protected static NpcTemplate boss(String id, String en, String ru, BodyType body, String texture, int tint, String script) {
        NpcTemplate t = npc(id, en, ru, NpcRank.BOSS, body, texture, tint).script(script).scale(1.6).aggro(14);
        return t;
    }

    protected static NpcSpell sp(String id, double cd, double delay) {
        return NpcSpell.of(id, cd, delay);
    }

    protected static NpcSpell sp(String id, double cd, double delay, NpcSpell.Target target) {
        return NpcSpell.of(id, cd, delay).on(target);
    }

    // ------------------------------------------------------------------ dungeons

    @SafeVarargs
    protected static List<List<String>> packs(List<String>... packs) {
        return List.of(packs);
    }

    protected static List<String> pack(String... ids) {
        return List.of(ids);
    }

    protected static DungeonDef dungeon(String id, String shortName, String en, String ru, String descEn, String descRu, String palette, double timer,
                                        RoomDef... rooms) {
        DungeonDef d = new DungeonDef(id, shortName, L10n.of(en, ru), L10n.of(descEn, descRu), DungeonDef.Type.DUNGEON, palette, 80, timer, id,
                List.of(rooms), DUNGEON_DIFFS, 1, 5);
        Dungeons.register(d);
        Dungeons.addToMythicPool(id);
        return d;
    }

    protected static DungeonDef raid(String id, String shortName, String en, String ru, String descEn, String descRu, String palette, RoomDef... rooms) {
        DungeonDef d = new DungeonDef(id, shortName, L10n.of(en, ru), L10n.of(descEn, descRu), DungeonDef.Type.RAID, palette, 80, 0, id,
                List.of(rooms), RAID_DIFFS, 1, 20);
        Dungeons.register(d);
        return d;
    }

    // ------------------------------------------------------------------ loot

    protected static final Set<Role> DPS = EnumSet.of(Role.MELEE_DPS, Role.RANGED_DPS);
    protected static final Set<Role> MELEE = EnumSet.of(Role.MELEE_DPS, Role.TANK);
    protected static final Set<Role> CASTER = EnumSet.of(Role.RANGED_DPS, Role.HEALER);
    protected static final Set<Role> HEAL = EnumSet.of(Role.HEALER);
    protected static final Set<Role> TANK = EnumSet.of(Role.TANK);
    protected static final Set<Role> ANY = EnumSet.noneOf(Role.class);

    protected static String item(String table, String id, String en, String ru, EquipType type, WeaponType weapon, String primary, String[] secondaries,
                                 String effect, Set<Role> roles, String flavorEn, String flavorRu) {
        String armor = type.slots.get(0).armorSlot ? ItemTemplate.ADAPTIVE : "NONE";
        ItemTemplate t = new ItemTemplate(id, L10n.of(en, ru), type, weapon, armor, primary, secondaries, effect,
                flavorEn != null ? L10n.of(flavorEn, flavorRu) : null, roles);
        ItemRegistry.register(t);
        List<String> list = new ArrayList<>(ItemRegistry.lootTable(table));
        list.add(id);
        ItemRegistry.lootTable(table, list);
        return id;
    }

    protected static String[] stats(String a, String b) {
        return new String[]{a, b};
    }
}
