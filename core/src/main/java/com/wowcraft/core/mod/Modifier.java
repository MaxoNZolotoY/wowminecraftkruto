package com.wowcraft.core.mod;

import com.wowcraft.core.spell.School;
import com.wowcraft.core.stat.Stat;

/** A single modifier: type + filter + value (+ optional reference such as a stat or ability id). */
public record Modifier(ModType type, ModFilter filter, double value, String ref) {

    public static Modifier of(ModType type, double value) {
        return new Modifier(type, ModFilter.ANY, value, null);
    }

    public static Modifier of(ModType type, ModFilter filter, double value) {
        return new Modifier(type, filter, value, null);
    }

    public static Modifier ref(ModType type, String ref, double value) {
        return new Modifier(type, ModFilter.ANY, value, ref);
    }

    // ---- common helpers used by content ----

    public static Modifier damage(double pct) {
        return of(ModType.DAMAGE_DONE, pct);
    }

    public static Modifier damage(ModFilter f, double pct) {
        return of(ModType.DAMAGE_DONE, f, pct);
    }

    public static Modifier abilityDamage(String abilityId, double pct) {
        return of(ModType.DAMAGE_DONE, ModFilter.ability(abilityId), pct);
    }

    public static Modifier tagDamage(String tag, double pct) {
        return of(ModType.DAMAGE_DONE, ModFilter.tag(tag), pct);
    }

    public static Modifier schoolDamage(School s, double pct) {
        return of(ModType.DAMAGE_DONE, ModFilter.school(s), pct);
    }

    public static Modifier healing(double pct) {
        return of(ModType.HEALING_DONE, pct);
    }

    public static Modifier abilityHealing(String abilityId, double pct) {
        return of(ModType.HEALING_DONE, ModFilter.ability(abilityId), pct);
    }

    public static Modifier taken(double pct) {
        return of(ModType.DAMAGE_TAKEN, pct);
    }

    public static Modifier healingTaken(double pct) {
        return of(ModType.HEALING_TAKEN, pct);
    }

    public static Modifier crit(double points) {
        return of(ModType.CRIT_CHANCE, points);
    }

    public static Modifier abilityCrit(String abilityId, double points) {
        return of(ModType.CRIT_CHANCE, ModFilter.ability(abilityId), points);
    }

    public static Modifier haste(double pct) {
        return of(ModType.HASTE, pct);
    }

    public static Modifier stat(Stat s, double pct) {
        return ref(ModType.STAT_PCT, s.name(), pct);
    }

    public static Modifier statFlat(Stat s, double v) {
        return ref(ModType.STAT_FLAT, s.name(), v);
    }

    public static Modifier speed(double pct) {
        return of(ModType.MOVE_SPEED, pct);
    }

    public static Modifier cooldown(String abilityId, double seconds) {
        return of(ModType.COOLDOWN_FLAT, ModFilter.ability(abilityId), seconds);
    }

    public static Modifier cooldownPct(String abilityId, double pct) {
        return of(ModType.COOLDOWN_PCT, ModFilter.ability(abilityId), pct);
    }

    public static Modifier castTime(String abilityId, double pct) {
        return of(ModType.CAST_TIME_PCT, ModFilter.ability(abilityId), pct);
    }

    public static Modifier cost(String abilityId, double pct) {
        return of(ModType.COST_PCT, ModFilter.ability(abilityId), pct);
    }

    public static Modifier charges(String abilityId, int n) {
        return of(ModType.CHARGES, ModFilter.ability(abilityId), n);
    }

    public static Modifier range(String abilityId, double blocks) {
        return of(ModType.RANGE, ModFilter.ability(abilityId), blocks);
    }

    public static Modifier duration(String auraId, double seconds) {
        return of(ModType.DURATION_FLAT, ModFilter.aura(auraId), seconds);
    }

    public static Modifier castWhileMoving(String abilityId) {
        return of(ModType.CAST_WHILE_MOVING, ModFilter.ability(abilityId), 1);
    }

    public static Modifier grant(String abilityId) {
        return ref(ModType.GRANT_ABILITY, abilityId, 1);
    }

    public static Modifier replace(String from, String to) {
        return ref(ModType.REPLACE_ABILITY, from + ">" + to, 1);
    }

    public static Modifier maxHealth(double pct) {
        return of(ModType.MAX_HEALTH_PCT, pct);
    }

    public static Modifier leech(double points) {
        return of(ModType.LEECH, points);
    }

    public static Modifier immune(String ccOrAll) {
        return ref(ModType.CC_IMMUNE, ccOrAll, 1);
    }

    public static Modifier immuneDamage() {
        return ref(ModType.DAMAGE_IMMUNE, "ALL", 1);
    }

    public static Modifier threat(double pct) {
        return of(ModType.THREAT_PCT, pct);
    }

    public static Modifier regen(String resource, double pct) {
        return ref(ModType.RESOURCE_REGEN_PCT, resource, pct);
    }

    public static Modifier gen(String abilityId, double amount) {
        return of(ModType.RESOURCE_GEN_FLAT, ModFilter.ability(abilityId), amount);
    }
}
