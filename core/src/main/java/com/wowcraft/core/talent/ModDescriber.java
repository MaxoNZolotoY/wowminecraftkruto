package com.wowcraft.core.talent;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.mod.ModFilter;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.stat.Stat;
import com.wowcraft.core.util.L10n.Lang;

import java.util.Locale;

/** Generates tooltip text for modifiers (talents, set bonuses, item effects). */
public final class ModDescriber {
    private ModDescriber() {
    }

    public static String describe(Modifier m, Lang lang) {
        boolean ru = lang == Lang.RU;
        String what = target(m.filter(), lang);
        double v = m.value();
        String pct = signedPct(v);
        String secs = num(Math.abs(v));
        return switch (m.type()) {
            case DAMAGE_DONE -> ru ? "Урон" + what + " " + pct : pct + " damage" + what;
            case HEALING_DONE -> ru ? "Исцеление" + what + " " + pct : pct + " healing" + what;
            case DAMAGE_TAKEN -> ru ? "Получаемый урон " + pct : pct + " damage taken";
            case HEALING_TAKEN -> ru ? "Получаемое исцеление " + pct : pct + " healing taken";
            case CRIT_CHANCE -> ru ? "Шанс крит. удара" + what + " +" + num(v) + "%" : "+" + num(v) + "% critical strike chance" + what;
            case CRIT_DAMAGE -> ru ? "Урон крит. ударов" + what + " " + signedPct(v) : signedPct(v) + " critical strike damage" + what;
            case HASTE -> ru ? "Скорость " + pct : pct + " haste";
            case STAT_PCT -> ru ? statName(m.ref(), lang) + " " + pct : pct + " " + statName(m.ref(), lang);
            case STAT_FLAT -> ru ? statName(m.ref(), lang) + " +" + num(v) : "+" + num(v) + " " + statName(m.ref(), lang);
            case MAX_HEALTH_PCT -> ru ? "Максимальный запас здоровья " + pct : pct + " maximum health";
            case MOVE_SPEED -> ru ? "Скорость передвижения " + pct : pct + " movement speed";
            case COOLDOWN_PCT -> ru ? "Время восстановления" + what + " " + pct : pct + " cooldown" + what;
            case COOLDOWN_FLAT -> v < 0
                    ? (ru ? "Время восстановления" + what + " сокращено на " + secs + " сек." : "Cooldown" + what + " reduced by " + secs + " sec")
                    : (ru ? "Время восстановления" + what + " увеличено на " + secs + " сек." : "Cooldown" + what + " increased by " + secs + " sec");
            case COOLDOWN_RATE -> ru ? "Восстановление" + what + " быстрее на " + Math.round(v * 100) + "%" : "Cooldown" + what + " recovers " + Math.round(v * 100) + "% faster";
            case COST_PCT -> ru ? "Стоимость" + what + " " + pct : pct + " cost" + what;
            case COST_FLAT -> ru ? "Стоимость" + what + " " + (v > 0 ? "+" : "") + num(v) : (v > 0 ? "+" : "") + num(v) + " cost" + what;
            case CAST_TIME_PCT -> v <= -0.999 ? (ru ? "Мгновенное применение" + what : "Instant cast" + what)
                    : (ru ? "Время применения" + what + " " + pct : pct + " cast time" + what);
            case CHARGES -> ru ? "Заряды" + what + " +" + num(v) : "+" + num(v) + " charge(s)" + what;
            case RANGE -> ru ? "Дальность" + what + " +" + num(v) + " м" : "+" + num(v) + " yd range" + what;
            case DURATION_FLAT -> ru ? "Длительность" + what + " +" + num(v) + " сек." : "+" + num(v) + " sec duration" + what;
            case DURATION_PCT -> ru ? "Длительность" + what + " " + pct : pct + " duration" + what;
            case CAST_WHILE_MOVING -> ru ? "Можно применять в движении" + what : "Castable while moving" + what;
            case RESOURCE_REGEN_PCT -> ru ? "Восполнение ресурса (" + resName(m.ref(), lang) + ") " + pct : pct + " " + resName(m.ref(), lang) + " regeneration";
            case RESOURCE_MAX -> ru ? "Максимум ресурса (" + resName(m.ref(), lang) + ") +" + num(v) : "+" + num(v) + " maximum " + resName(m.ref(), lang);
            case RESOURCE_GEN_FLAT -> ru ? what.trim() + " создает дополнительно " + num(v) + " ед. ресурса" : what.trim() + " generates " + num(v) + " additional resource";
            case THREAT_PCT -> ru ? "Угроза " + pct : pct + " threat";
            case LEECH -> ru ? "Самоисцеление +" + num(v) + "%" : "+" + num(v) + "% Leech";
            case AVOIDANCE -> ru ? "Избегание +" + num(v) + "%" : "+" + num(v) + "% Avoidance";
            case ABSORB_DONE -> ru ? "Сила поглощающих щитов " + pct : pct + " absorb shields";
            case ARMOR_PCT -> ru ? "Броня " + pct : pct + " armor";
            case PET_DAMAGE -> ru ? "Урон питомцев " + pct : pct + " pet damage";
            case CC_IMMUNE -> ru ? "Невосприимчивость к контролю" : "Immune to crowd control";
            case DAMAGE_IMMUNE -> ru ? "Невосприимчивость к урону" : "Immune to damage";
            case UNTARGETABLE -> ru ? "Невозможно выбрать целью" : "Cannot be targeted";
            case GRANT_ABILITY -> ru ? "Открывает способность «" + abilityName(m.ref(), lang) + "»" : "Grants " + abilityName(m.ref(), lang);
            case REPLACE_ABILITY -> {
                String[] p = m.ref().split(">");
                yield ru ? "«" + abilityName(p[0], lang) + "» заменяется на «" + abilityName(p[1], lang) + "»"
                        : abilityName(p[0], lang) + " is replaced by " + abilityName(p[1], lang);
            }
            case EXTRA_TARGETS -> ru ? "Дополнительные цели" + what + " +" + num(v) : "+" + num(v) + " additional targets" + what;
            case TICK_RATE -> ru ? "Частота периодического эффекта" + what + " " + pct : pct + " periodic tick rate" + what;
            case RADIUS_PCT -> ru ? "Радиус" + what + " " + pct : pct + " radius" + what;
            case EXECUTE_DAMAGE -> ru ? "Урон по целям с менее 35% здоровья" + what + " " + pct : pct + " damage" + what + " against targets below 35% health";
            case PVP_DAMAGE -> ru ? "Урон в PvP " + pct : pct + " PvP damage";
        };
    }

    public static String describeAll(java.util.List<Modifier> mods, String auraId, String grant, Lang lang) {
        StringBuilder sb = new StringBuilder();
        for (Modifier m : mods) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(describe(m, lang));
        }
        if (grant != null) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(lang == Lang.RU ? "Открывает способность «" + abilityName(grant, lang) + "»" : "Grants " + abilityName(grant, lang));
            Ability a = Registry.ability(grant);
            if (a != null && a.description != null) sb.append(": ").append(a.description.get(lang));
        }
        if (auraId != null) {
            AuraDef a = Registry.aura(auraId);
            if (a != null && a.description != null) {
                if (sb.length() > 0) sb.append(". ");
                sb.append(a.description.get(lang));
            }
        }
        return sb.toString();
    }

    private static String target(ModFilter f, Lang lang) {
        boolean ru = lang == Lang.RU;
        return switch (f.kind) {
            case ANY -> "";
            case ABILITY -> ru ? " способности «" + abilityName(f.value, lang) + "»" : " of " + abilityName(f.value, lang);
            case TAG -> ru ? " (" + tagName(f.value, lang) + ")" : " of " + tagName(f.value, lang) + " abilities";
            case NOT_TAG -> "";
            case SCHOOL -> {
                School s = School.valueOf(f.value);
                yield ru ? " (" + s.name.ru() + ")" : " of " + s.name.en() + " spells";
            }
            case AURA -> {
                AuraDef a = Registry.aura(f.value);
                String n = a == null ? f.value : a.name.get(lang);
                yield ru ? " эффекта «" + n + "»" : " of " + n;
            }
        };
    }

    private static String tagName(String tag, Lang lang) {
        boolean ru = lang == Lang.RU;
        return switch (tag) {
            case "bleed" -> ru ? "кровотечения" : "bleed";
            case "dot" -> ru ? "периодический урон" : "periodic";
            case "poison" -> ru ? "яды" : "poison";
            case "finisher" -> ru ? "завершающие приемы" : "finishing move";
            case "fire" -> ru ? "огонь" : "Fire";
            case "frost" -> ru ? "лед" : "Frost";
            case "shadow" -> ru ? "тьма" : "Shadow";
            case "aoe" -> ru ? "по области" : "area";
            case "heal" -> ru ? "исцеление" : "healing";
            case "pet" -> ru ? "питомцы" : "pet";
            default -> tag.replace('_', ' ');
        };
    }

    public static String abilityName(String id, Lang lang) {
        Ability a = Registry.ability(id);
        return a == null ? id : a.name.get(lang);
    }

    private static String statName(String ref, Lang lang) {
        Stat s = Stat.byName(ref);
        return s == null ? ref : s.name.get(lang).toLowerCase(lang == Lang.RU ? new Locale("ru") : Locale.ROOT);
    }

    private static String resName(String ref, Lang lang) {
        ResourceType r = ResourceType.byName(ref);
        return r == null ? ref : r.name.get(lang);
    }

    private static String signedPct(double v) {
        long p = Math.round(v * 100);
        return (p >= 0 ? "+" : "") + p + "%";
    }

    private static String num(double v) {
        return Math.abs(v - Math.round(v)) < 1e-9 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
