package com.wowcraft.core.spell;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.util.L10n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds WoW-style tooltip lines for abilities. */
public final class Tooltip {
    private Tooltip() {
    }

    public static List<String> lines(Ability a, L10n.Lang lang, UnitState unit) {
        DescribeContext d = new DescribeContext(lang, unit, a);
        List<String> out = new ArrayList<>();
        out.add(a.name.get(lang));
        StringBuilder costs = new StringBuilder();
        for (Cost c : a.costs) {
            if (costs.length() > 0) costs.append(", ");
            if (c.type() == ResourceType.MANA) {
                costs.append(fmt(c.amount() / 100.0)).append(d.t("% of base mana", "% базовой маны"));
            } else {
                costs.append(fmt(c.amount()));
                if (c.upTo() > 0) costs.append("-").append(fmt(c.upTo()));
                costs.append(" ").append(c.type().name.get(lang));
            }
        }
        String range = a.range <= 0 ? "" : a.range <= Ability.MELEE_RANGE + 0.01 ? d.t("Melee Range", "Ближний бой") : fmt(a.range) + d.t(" yd range", " м");
        if (costs.length() > 0 || !range.isEmpty()) out.add(costs + (costs.length() > 0 && !range.isEmpty() ? "   " : "") + range);
        String cast = switch (a.castType) {
            case INSTANT -> d.t("Instant", "Мгновенное действие");
            case CAST -> fmt(a.castTime) + d.t(" sec cast", " сек. применение");
            case CHANNEL -> d.t("Channeled", "Поддерживаемое");
            case EMPOWER -> d.t("Empowered (" + a.maxEmpowerStage + " levels)", "Усиливаемое (" + a.maxEmpowerStage + " уровня)");
        };
        String cd = "";
        if (a.cooldown > 0) {
            cd = formatCd(a.cooldown, d);
            if (a.charges > 1) cd += d.t(" (" + a.charges + " charges)", " (" + a.charges + " заряда)");
        }
        out.add(cast + (cd.isEmpty() ? "" : "   " + cd));
        StringBuilder text = new StringBuilder();
        if (a.description != null) text.append(a.description.get(lang));
        String eff = describeEffects(d, a.effects);
        if (!eff.isEmpty()) {
            if (text.length() > 0) text.append(" ");
            text.append(eff).append(".");
        }
        String tick = describeEffects(d, a.channelEffects);
        if (!tick.isEmpty()) {
            if (text.length() > 0) text.append(" ");
            text.append(d.t("Every " + fmt(a.channelTickInterval) + " sec while channeling: ", "Каждые " + fmt(a.channelTickInterval) + " сек. поддержания: "))
                    .append(lowerFirst(tick)).append(".");
        }
        for (Cost g : a.generates) {
            if (g.amount() <= 0) continue;
            if (text.length() > 0) text.append(" ");
            text.append(d.t("Generates " + fmt(g.amount()) + " " + g.type().name.en() + ".",
                    "Создает " + fmt(g.amount()) + " ед. (" + g.type().name.ru() + ")."));
        }
        for (Cond c : a.requirements) {
            if (text.length() > 0) text.append(" ");
            text.append(d.t("Requires: ", "Требуется: ")).append(c.text.get(lang)).append(".");
        }
        if (text.length() > 0) out.add(text.toString());
        return out;
    }

    public static String describeEffects(DescribeContext d, List<Effect> effects) {
        StringBuilder sb = new StringBuilder();
        for (Effect e : effects) {
            String s;
            try {
                s = e.describe(d);
            } catch (RuntimeException ex) {
                s = "";
            }
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(". ");
            sb.append(s);
        }
        return sb.toString();
    }

    private static String formatCd(double seconds, DescribeContext d) {
        if (seconds >= 60 && seconds % 60 == 0) return (int) (seconds / 60) + d.t(" min cooldown", " мин. восстановление");
        return fmt(seconds) + d.t(" sec cooldown", " сек. восстановление");
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
