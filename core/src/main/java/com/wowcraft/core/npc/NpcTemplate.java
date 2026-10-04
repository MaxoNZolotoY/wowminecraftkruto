package com.wowcraft.core.npc;

import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of an NPC (mob, boss, pet, bot body). Visuals are placeholders: {@link #texture} picks a vanilla skin,
 * {@link #modelId} / {@link #animationSet} are reserved hooks for custom models (e.g. GeckoLib) added later.
 */
public final class NpcTemplate {
    public final String id;
    public final L10n name;
    public final NpcRank rank;
    public BodyType body = BodyType.HUMANOID;
    /** Placeholder texture key (see the client NpcTextures table). */
    public String texture = "zombie";
    public double scale = 1.0;
    public int tint = 0xFFFFFFFF;
    public double healthMult = 1.0;
    public double damageMult = 1.0;
    public double meleeSpeed = 2.0;
    /** Preferred combat distance; > 6 means the NPC is a caster/ranged and keeps distance. */
    public double preferredRange = 2.5;
    public Role role = Role.MELEE_DPS;
    public final List<NpcSpell> spells = new ArrayList<>();
    public double forces = -1;
    public String bossScript;
    public double aggroRadius = 9;
    public double armorReduction = 0.25;
    public double moveSpeed = 0.28;
    public boolean immuneCc;
    public String modelId;
    public String animationSet;
    /** Pets: fraction of the owner's attack / spell power used as attack power. */
    public double ownerPowerScale = 0.0;
    /** Pets: fraction of the owner's max health. */
    public double ownerHealthScale = 0.0;
    /** Player-like bots use this spec for abilities. */
    public Spec botSpec;
    public boolean stationary;
    public String vfx;
    public L10n title;
    /** Friendly NPC (vendor, quest giver, dungeon portal keeper). */
    public boolean friendly;
    public String interaction;

    public NpcTemplate(String id, String en, String ru, NpcRank rank) {
        this.id = id;
        this.name = L10n.of(en, ru);
        this.rank = rank;
        this.immuneCc = rank == NpcRank.BOSS;
    }

    // fluent setters for content
    public NpcTemplate body(BodyType b) {
        this.body = b;
        return this;
    }

    public NpcTemplate texture(String t) {
        this.texture = t;
        return this;
    }

    public NpcTemplate scale(double s) {
        this.scale = s;
        return this;
    }

    public NpcTemplate tint(int argb) {
        this.tint = argb;
        return this;
    }

    public NpcTemplate hp(double m) {
        this.healthMult = m;
        return this;
    }

    public NpcTemplate dmg(double m) {
        this.damageMult = m;
        return this;
    }

    public NpcTemplate swing(double s) {
        this.meleeSpeed = s;
        return this;
    }

    public NpcTemplate ranged(double distance) {
        this.preferredRange = distance;
        this.role = Role.RANGED_DPS;
        return this;
    }

    public NpcTemplate healer() {
        this.role = Role.HEALER;
        this.preferredRange = 18;
        return this;
    }

    public NpcTemplate spell(NpcSpell s) {
        this.spells.add(s);
        return this;
    }

    public NpcTemplate spell(String abilityId, double cd, double delay) {
        this.spells.add(NpcSpell.of(abilityId, cd, delay));
        return this;
    }

    public NpcTemplate forces(double f) {
        this.forces = f;
        return this;
    }

    public NpcTemplate script(String scriptId) {
        this.bossScript = scriptId;
        return this;
    }

    public NpcTemplate aggro(double r) {
        this.aggroRadius = r;
        return this;
    }

    public NpcTemplate armor(double a) {
        this.armorReduction = a;
        return this;
    }

    public NpcTemplate speed(double s) {
        this.moveSpeed = s;
        return this;
    }

    public NpcTemplate ccImmune() {
        this.immuneCc = true;
        return this;
    }

    public NpcTemplate model(String modelId, String animationSet) {
        this.modelId = modelId;
        this.animationSet = animationSet;
        return this;
    }

    public NpcTemplate pet(double powerScale, double healthScale) {
        this.ownerPowerScale = powerScale;
        this.ownerHealthScale = healthScale;
        return this;
    }

    public NpcTemplate stationary() {
        this.stationary = true;
        return this;
    }

    public NpcTemplate title(String en, String ru) {
        this.title = L10n.of(en, ru);
        return this;
    }

    public NpcTemplate friendly(String interaction) {
        this.friendly = true;
        this.interaction = interaction;
        return this;
    }

    public NpcTemplate bot(Spec spec) {
        this.botSpec = spec;
        this.role = spec.role;
        return this;
    }

    public double forcesValue() {
        return forces >= 0 ? forces : rank.forces;
    }
}
