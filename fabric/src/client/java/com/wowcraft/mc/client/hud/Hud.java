package com.wowcraft.mc.client.hud;

import com.wowcraft.core.content.Registry;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.mythic.Affix;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.util.Mth;
import com.wowcraft.mc.client.ClientState;
import com.wowcraft.mc.client.Controls;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** The WoW-style interface: unit frames, action bar, cast bars, auras, boss timers, Mythic+ / PvP trackers, meter, alerts. */
public final class Hud {
    private Hud() {
    }

    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "="};

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        ClientState.cleanup();
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        S2C.Self self = ClientState.self;
        S2C.Character ch = ClientState.character;
        if (self == null) return;
        if (!ch.classChosen) {
            String s = ClientState.t("Choose your class: press K or type /wow class", "Выберите класс: нажмите K или введите /wow class");
            ctx.drawCenteredTextWithShadow(tr, s, sw / 2, sh / 4, 0xFFFFD040);
        }
        playerFrame(ctx, tr, self, ch, 4, 4);
        S2C.Unit target = ClientState.target();
        if (target != null) {
            unitFrame(ctx, tr, target, 148, 4, 140, true);
            S2C.Unit tot = target.targetId >= 0 ? ClientState.unit(target.targetId) : null;
            if (tot != null) unitFrame(ctx, tr, tot, 294, 4, 80, false);
        }
        partyFrames(ctx, tr, 4, 48);
        bossFrames(ctx, tr, sw - 134, 4);
        playerAuras(ctx, tr, self, sw, sh);
        actionBar(ctx, tr, self, sw, sh);
        castBar(ctx, tr, self, sw, sh);
        int rightY = 4 + Math.max(1, ClientState.bosses.size()) * 26 + 6;
        rightY = bossTimers(ctx, tr, sw - 160, Math.max(rightY, 80));
        rightY = mythicTracker(ctx, tr, sw - 160, rightY + 4);
        pvpTracker(ctx, tr, sw, sh);
        meter(ctx, tr, sw, sh);
        alerts(ctx, tr, sw, sh, self);
        loot(ctx, tr, sh);
    }

    // ------------------------------------------------------------------ frames

    static void bar(DrawContext ctx, int x, int y, int w, int h, double frac, int color) {
        ctx.fill(x, y, x + w, y + h, 0xC0101010);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) ctx.fill(x, y, x + fw, y + h, color);
    }

    private static void playerFrame(DrawContext ctx, TextRenderer tr, S2C.Self s, S2C.Character ch, int x, int y) {
        int w = 140;
        WowClass wc = WowClass.byId(ch.wowClass);
        int classColor = wc != null ? wc.color : 0xFFFFFFFF;
        ctx.fill(x - 1, y - 1, x + w + 1, y + 40, 0x90000000);
        String name = MinecraftClient.getInstance().player.getName().getString();
        ctx.drawText(tr, name, x + 2, y + 1, classColor, true);
        String lvl = String.valueOf(ch.level);
        ctx.drawText(tr, lvl, x + w - tr.getWidth(lvl) - 2, y + 1, 0xFFFFD040, true);
        if (s.inCombat) ctx.fill(x + w - tr.getWidth(lvl) - 8, y + 2, x + w - tr.getWidth(lvl) - 4, y + 6, 0xFFFF3030);
        double frac = s.maxHealth <= 0 ? 0 : s.health / s.maxHealth;
        bar(ctx, x + 1, y + 11, w - 2, 10, frac, s.ghost ? 0xFF606060 : 0xFF20B020);
        if (s.absorb > 0 && s.maxHealth > 0) {
            int ax = x + 1 + (int) ((w - 2) * frac), aw = (int) ((w - 2) * Math.min(1 - frac, s.absorb / s.maxHealth));
            ctx.fill(ax, y + 11, ax + aw, y + 21, 0xC0E8E8E8);
        }
        String hp = s.ghost ? ClientState.t("Ghost", "Призрак") : Mth.shortNumber(s.health) + " / " + Mth.shortNumber(s.maxHealth);
        ctx.drawText(tr, hp, x + (w - tr.getWidth(hp)) / 2, y + 12, 0xFFFFFFFF, true);
        int ry = y + 23;
        for (var en : s.resources.entrySet()) {
            ResourceType rt = ResourceType.valueOf(en.getKey());
            double max = s.resourceMax.getOrDefault(en.getKey(), 100.0);
            if (rt == ResourceType.RUNES && s.runes != null) {
                int pw = (w - 2 - 5) / 6;
                for (int i = 0; i < s.runes.length && i < 6; i++) {
                    int px = x + 1 + i * (pw + 1);
                    bar(ctx, px, ry, pw, 6, s.runes[i], s.runes[i] >= 1 ? rt.color : Icons.darken(rt.color, 0.5f));
                }
                ry += 8;
                continue;
            }
            if (rt.pips) {
                int n = (int) Math.round(max);
                int pw = Math.max(4, (w - 2 - (n - 1)) / Math.max(1, n));
                for (int i = 0; i < n; i++) {
                    int px = x + 1 + i * (pw + 1);
                    ctx.fill(px, ry, px + pw, ry + 6, en.getValue() >= i + 1 ? rt.color : 0xC0202020);
                }
                ry += 8;
            } else {
                bar(ctx, x + 1, ry, w - 2, 6, max <= 0 ? 0 : en.getValue() / max, rt.color);
                ry += 8;
            }
        }
    }

    static void unitFrame(DrawContext ctx, TextRenderer tr, S2C.Unit u, int x, int y, int w, boolean full) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + (full ? 40 : 22), 0x90000000);
        String rank = u.boss ? ClientState.t("Boss ", "Босс ") : "ELITE".equals(u.rank) ? "+ " : "";
        String name = (u.level > 0 ? u.level + " " : "") + rank + u.name;
        int nameColor = u.wowClass != null ? (0xFF000000 | u.color) : u.hostile ? 0xFFFF6060 : 0xFF80FF80;
        ctx.drawText(tr, trim(tr, name, w - 4), x + 2, y + 1, nameColor, true);
        double frac = u.maxHealth <= 0 ? 0 : u.health / u.maxHealth;
        int hc = u.dead ? 0xFF505050 : u.hostile ? 0xFFC02020 : u.wowClass != null ? (0xFF000000 | u.color) : 0xFF20B020;
        bar(ctx, x + 1, y + 11, w - 2, 9, frac, hc);
        String pct = u.dead ? ClientState.t("Dead", "Мертв") : Math.round(frac * 100) + "%";
        ctx.drawText(tr, pct, x + (w - tr.getWidth(pct)) / 2, y + 12, 0xFFFFFFFF, true);
        if (u.threat >= 0 && full) {
            int tc = u.threat >= 0.99 ? 0xFFFF3030 : u.threat > 0.8 ? 0xFFFFA020 : 0xFF808080;
            String th = Math.round(u.threat * 100) + "%";
            ctx.drawText(tr, th, x + w - tr.getWidth(th), y - 10 < 0 ? y + 22 : y - 9, tc, true);
        }
        if (!full) return;
        if (u.power != null && u.powerMax > 0) {
            ResourceType rt = ResourceType.valueOf(u.power);
            bar(ctx, x + 1, y + 21, w - 2, 4, u.powerCur / u.powerMax, rt.color);
        }
        if (u.cast != null) {
            Ability a = Registry.ability(u.cast.ability);
            bar(ctx, x + 1, y + 27, w - 2, 9, u.cast.progress, u.cast.interruptible ? 0xFFE0B020 : 0xFF909090);
            String cn = a != null ? ClientState.t(a.name) : u.cast.ability;
            ctx.drawText(tr, trim(tr, cn, w - 4), x + 3, y + 28, 0xFFFFFFFF, true);
        } else {
            int ax = x;
            for (S2C.AuraInfo a : u.auras) {
                if (ax > x + w - 12) break;
                var def = Registry.aura(a.id);
                Icons.aura(ctx, a.id, ax + 1, y + 28, 10, def != null && def.harmful);
                if (a.stacks > 1) ctx.drawText(tr, String.valueOf(a.stacks), ax + 6, y + 32, 0xFFFFFFFF, true);
                ax += 12;
            }
        }
    }

    static String trim(TextRenderer tr, String s, int w) {
        if (tr.getWidth(s) <= w) return s;
        while (s.length() > 1 && tr.getWidth(s + "..") > w) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static void partyFrames(DrawContext ctx, TextRenderer tr, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int i = 0;
        for (int id : ClientState.party) {
            if (id == mc.player.getId()) continue;
            S2C.Unit u = ClientState.unit(id);
            if (u == null) continue;
            int fy = y + i * 24;
            if (fy > ctx.getScaledWindowHeight() - 80) break;
            unitFrame(ctx, tr, u, x, fy, 90, false);
            String role = u.role == null ? "" : u.role.equals("TANK") ? "T" : u.role.equals("HEALER") ? "H" : "D";
            ctx.drawText(tr, role, x + 82, fy + 1, 0xFFFFD040, true);
            int ax = x + 94;
            for (S2C.AuraInfo a : u.auras) {
                var def = Registry.aura(a.id);
                if (def == null || !def.harmful) continue;
                Icons.aura(ctx, a.id, ax, fy + 6, 9, true);
                ax += 11;
                if (ax > x + 140) break;
            }
            i++;
        }
    }

    private static void bossFrames(DrawContext ctx, TextRenderer tr, int x, int y) {
        int i = 0;
        for (int id : ClientState.bosses) {
            S2C.Unit u = ClientState.unit(id);
            if (u == null) continue;
            unitFrame(ctx, tr, u, x, y + i * 26, 130, false);
            i++;
        }
        S2C.PvpStatus pvp = ClientState.pvp;
        if (pvp != null && pvp.mode != null && (pvp.mode.equals("ARENA") || pvp.mode.equals("SHUFFLE"))) {
            for (S2C.Unit u : ClientState.units.values()) {
                if (!u.hostile || u.wowClass == null) continue;
                unitFrame(ctx, tr, u, x, y + i * 26, 130, false);
                i++;
            }
        }
    }

    private static void playerAuras(DrawContext ctx, TextRenderer tr, S2C.Self s, int sw, int sh) {
        int bx = sw - 150, dx = sw - 150;
        int by = ClientState.bosses.isEmpty() ? 4 : 4 + ClientState.bosses.size() * 26 + 2;
        bx = sw - 4;
        dx = sw - 4;
        int buffY = by, debuffY = by + 24;
        for (S2C.AuraInfo a : s.auras) {
            var def = Registry.aura(a.id);
            boolean harmful = def != null && def.harmful;
            int size = 14;
            if (harmful) {
                dx -= size + 4;
                if (dx < sw / 2) continue;
                drawAura(ctx, tr, a, dx, debuffY, size, true);
            } else {
                bx -= size + 4;
                if (bx < sw / 2) continue;
                drawAura(ctx, tr, a, bx, buffY, size, false);
            }
        }
    }

    private static void drawAura(DrawContext ctx, TextRenderer tr, S2C.AuraInfo a, int x, int y, int size, boolean harmful) {
        Icons.aura(ctx, a.id, x, y, size, harmful);
        if (a.stacks > 1) ctx.drawText(tr, String.valueOf(a.stacks), x + size - tr.getWidth(String.valueOf(a.stacks)), y + size - 7, 0xFFFFFFFF, true);
        if (a.remaining >= 0 && a.remaining < 600) {
            String t = a.remaining >= 60 ? (int) (a.remaining / 60) + "m" : String.valueOf((int) Math.ceil(a.remaining));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + size / 2f, y + size + 1, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            ctx.drawText(tr, t, -tr.getWidth(t) / 2, 0, 0xFFFFFF80, true);
            ctx.getMatrices().pop();
        }
    }

    // ------------------------------------------------------------------ action bar

    private static void actionBar(DrawContext ctx, TextRenderer tr, S2C.Self s, int sw, int sh) {
        String[] bar = s.resolvedBar;
        if (bar == null) return;
        int size = 20, gap = 2;
        int total = 12 * size + 11 * gap;
        int x0 = (sw - total) / 2, y0 = sh - 22 - size - 8;
        if (Controls.combatMode) ctx.fill(x0 - 3, y0 - 3, x0 + total + 3, y0 + size + 3, 0x80FFB000);
        ctx.fill(x0 - 2, y0 - 2, x0 + total + 2, y0 + size + 2, 0xA0000000);
        long now = System.currentTimeMillis();
        float since = (now - ClientState.selfAt) / 1000f;
        for (int i = 0; i < 12; i++) {
            int x = x0 + i * (size + gap);
            String id = i < bar.length ? bar[i] : null;
            if (id == null) {
                ctx.fill(x, y0, x + size, y0 + size, 0x60404040);
            } else {
                Icons.ability(ctx, id, x, y0, size);
                S2C.CooldownInfo cd = null;
                for (S2C.CooldownInfo c : s.cooldowns) if (c.id.equals(id)) cd = c;
                float remaining = cd != null ? Math.max(0, cd.remaining - since) : 0;
                boolean noCharges = cd == null || cd.charges <= 0;
                Ability a = Registry.ability(id);
                float gcd = a != null && a.gcd > 0 ? Math.max(0, s.gcdRemaining - since) : 0;
                if (remaining > 0 && noCharges && cd.duration > 0) {
                    int h = (int) (size * Math.min(1, remaining / cd.duration));
                    ctx.fill(x, y0 + size - h, x + size, y0 + size, 0xB0000000);
                    String t = remaining >= 60 ? (int) (remaining / 60) + "m" : remaining >= 10 ? String.valueOf((int) remaining)
                            : String.format("%.1f", remaining);
                    ctx.drawText(tr, t, x + (size - tr.getWidth(t)) / 2, y0 + 6, 0xFFFFFF60, true);
                } else if (gcd > 0 && s.gcdDuration > 0) {
                    int h = (int) (size * Math.min(1, gcd / s.gcdDuration));
                    ctx.fill(x, y0 + size - h, x + size, y0 + size, 0x80000000);
                }
                if (s.usable != null && i < s.usable.length && !s.usable[i]) ctx.fill(x, y0, x + size, y0 + size, 0x90000040);
                else if (s.inRange != null && i < s.inRange.length && !s.inRange[i]) ctx.fill(x, y0, x + size, y0 + size, 0x70A00000);
                if (cd != null && cd.maxCharges > 1) ctx.drawText(tr, String.valueOf(cd.charges), x + size - 6, y0 + size - 8, 0xFFFFFFFF, true);
                if (s.suggested == i) ctx.drawBorder(x - 1, y0 - 1, size + 2, size + 2, 0xFFFFD700);
            }
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + 1, y0 + 1, 0);
            ctx.getMatrices().scale(0.6f, 0.6f, 1);
            ctx.drawText(tr, KEYS[i], 0, 0, Controls.combatMode ? 0xFFFFD040 : 0xFFA0A0A0, true);
            ctx.getMatrices().pop();
        }
        if (!Controls.combatMode) {
            String hint = ClientState.t("[R] combat mode", "[R] боевой режим");
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x0 + total + 6, y0 + 6, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawText(tr, hint, 0, 0, 0xFF909090, true);
            ctx.getMatrices().pop();
        }
    }

    private static void castBar(DrawContext ctx, TextRenderer tr, S2C.Self s, int sw, int sh) {
        if (s.cast == null) return;
        int w = 160, x = (sw - w) / 2, y = sh - 22 - 20 - 8 - 16;
        float since = (System.currentTimeMillis() - ClientState.selfAt) / 1000f;
        float prog = s.cast.total <= 0 ? 1 : Math.min(1, s.cast.progress + since / s.cast.total);
        if (s.cast.channel) prog = 1 - prog;
        ctx.fill(x - 1, y - 1, x + w + 1, y + 11, 0xFF000000);
        bar(ctx, x, y, w, 10, prog, s.cast.empower ? 0xFF40E0C0 : s.cast.channel ? 0xFF40A0FF : 0xFFE0B020);
        if (s.cast.empower) {
            for (int i = 1; i <= 4; i++) ctx.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + 10, 0xFF000000);
        }
        Ability a = Registry.ability(s.cast.ability);
        String n = a != null ? ClientState.t(a.name) : s.cast.ability;
        ctx.drawText(tr, n, x + (w - tr.getWidth(n)) / 2, y + 1, 0xFFFFFFFF, true);
    }

    // ------------------------------------------------------------------ trackers

    private static int bossTimers(DrawContext ctx, TextRenderer tr, int x, int y) {
        S2C.BossTimers t = ClientState.timers;
        if (t == null || t.bars.isEmpty()) return y;
        float since = (System.currentTimeMillis() - ClientState.timersAt) / 1000f;
        List<S2C.TimerBar> bars = new java.util.ArrayList<>(t.bars);
        bars.sort((a, b) -> Float.compare(a.remaining, b.remaining));
        for (S2C.TimerBar b : bars) {
            float rem = b.remaining - since;
            if (rem < -0.5f) continue;
            bar(ctx, x, y, 156, 10, b.total <= 0 ? 0 : Math.max(0, rem) / b.total, rem < 5 ? 0xFFE03030 : (0xFF000000 | b.color));
            ctx.drawText(tr, trim(tr, ClientState.t(b.label), 120), x + 2, y + 1, 0xFFFFFFFF, true);
            String r = String.format("%.0f", Math.max(0, rem));
            ctx.drawText(tr, r, x + 154 - tr.getWidth(r), y + 1, 0xFFFFFFFF, true);
            y += 12;
            if (y > ctx.getScaledWindowHeight() / 2) break;
        }
        return y;
    }

    private static int mythicTracker(DrawContext ctx, TextRenderer tr, int x, int y) {
        S2C.InstanceStatus s = ClientState.instance;
        if (s == null) return y;
        int w = 156;
        int h = 14 + s.bossNames.size() * 10 + (s.keyLevel > 0 ? 27 + (s.deaths > 0 ? 10 : 0) : 0) + (s.battleRes >= 0 ? 10 : 0);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h, 0x90000000);
        String title = ClientState.t(s.name) + (s.keyLevel > 0 ? " +" + s.keyLevel : "");
        ctx.drawText(tr, trim(tr, title, w), x, y, 0xFFFFD040, true);
        y += 10;
        if (s.keyLevel > 0) {
            StringBuilder af = new StringBuilder();
            for (String a : s.affixes) {
                Affix affix = Affix.byName(a);
                if (affix != null) af.append(af.length() > 0 ? ", " : "").append(ClientState.t(affix.name));
            }
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            ctx.drawText(tr, trim(tr, af.toString(), (int) (w / 0.7f)), 0, 0, 0xFFB0B0FF, true);
            ctx.getMatrices().pop();
            y += 8;
            String timer;
            int tc;
            if (s.countdown > 0) {
                timer = ClientState.t("Starting in ", "Старт через ") + (int) Math.ceil(s.countdown);
                tc = 0xFFFFD040;
            } else {
                timer = Mth.formatTime(s.elapsed) + " / " + Mth.formatTime(s.timer);
                tc = s.elapsed > s.timer ? 0xFFFF4040 : 0xFFFFFFFF;
            }
            ctx.drawText(tr, timer, x, y, tc, true);
            String up = s.completed ? (s.upgrades > 0 ? "+" + s.upgrades : ClientState.t("depleted", "не в срок")) : "+3 " + Mth.formatTime(Math.max(0, s.timer * 0.6 - s.elapsed))
                    + "  +2 " + Mth.formatTime(Math.max(0, s.timer * 0.8 - s.elapsed));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + 70, y + 1, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            ctx.drawText(tr, up, 0, 0, 0xFFA0FFA0, true);
            ctx.getMatrices().pop();
            y += 10;
            bar(ctx, x, y, w, 7, s.forces / 100.0, s.forces >= 100 ? 0xFF40D040 : 0xFFD0A040);
            String fp = String.format("%.1f%%", s.forces);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + w / 2f - tr.getWidth(fp) * 0.35f, y, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            ctx.drawText(tr, fp, 0, 0, 0xFFFFFFFF, true);
            ctx.getMatrices().pop();
            y += 9;
            if (s.deaths > 0) {
                String d = ClientState.t("Deaths: ", "Смерти: ") + s.deaths + " (-" + (int) (s.deaths * s.deathPenalty) + "s)";
                ctx.drawText(tr, d, x, y, 0xFFFF8080, true);
                y += 10;
            }
        }
        for (int i = 0; i < s.bossNames.size(); i++) {
            boolean dead = i < s.bossKilled.size() && s.bossKilled.get(i);
            String b = (dead ? "✔ " : "• ") + ClientState.t(s.bossNames.get(i));
            ctx.drawText(tr, trim(tr, b, w), x, y + 2, dead ? 0xFF60FF60 : 0xFFE0E0E0, true);
            y += 10;
        }
        if (s.battleRes >= 0) {
            ctx.drawText(tr, ClientState.t("Battle res: ", "Боевые воскрешения: ") + s.battleRes, x, y + 2, 0xFFA0D0FF, true);
            y += 10;
        }
        return y + 6;
    }

    private static void pvpTracker(DrawContext ctx, TextRenderer tr, int sw, int sh) {
        S2C.PvpStatus p = ClientState.pvp;
        if (p == null) return;
        String title = ClientState.t(p.name);
        ctx.drawCenteredTextWithShadow(tr, title, sw / 2, 4, 0xFFFFD040);
        if (p.countdown > 0) {
            ctx.drawCenteredTextWithShadow(tr, ClientState.t("Starts in ", "Начало через ") + (int) Math.ceil(p.countdown), sw / 2, 14, 0xFFFFFFFF);
        } else if (p.teams.size() == 2 && !"SHUFFLE".equals(p.mode)) {
            S2C.PvpTeam a = p.teams.get(0), b = p.teams.get(1);
            String score = a.score + "  :  " + b.score;
            ctx.drawText(tr, ClientState.t("Blue", "Синие"), sw / 2 - 60, 14, a.color, true);
            ctx.drawCenteredTextWithShadow(tr, score, sw / 2, 14, 0xFFFFFFFF);
            ctx.drawText(tr, ClientState.t("Red", "Красные"), sw / 2 + 40, 14, b.color, true);
        }
        int y = 24;
        for (String o : p.objectives) {
            ctx.drawCenteredTextWithShadow(tr, objective(o), sw / 2, y, 0xFFE0E0E0);
            y += 9;
        }
        if (p.dampening > 0) ctx.drawCenteredTextWithShadow(tr, ClientState.t("Dampening ", "Ослабление ") + p.dampening + "%", sw / 2, y, 0xFFFF8080);
        if (p.result != null) {
            String r = switch (p.result) {
                case "win" -> ClientState.t("VICTORY", "ПОБЕДА");
                case "loss" -> ClientState.t("DEFEAT", "ПОРАЖЕНИЕ");
                case "draw" -> ClientState.t("DRAW", "НИЧЬЯ");
                default -> ClientState.t("MATCH OVER", "МАТЧ ОКОНЧЕН");
            };
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, sh / 3f, 0);
            ctx.getMatrices().scale(2.5f, 2.5f, 1);
            ctx.drawCenteredTextWithShadow(tr, r, 0, 0, p.result.equals("win") ? 0xFF40FF40 : 0xFFFF4040);
            ctx.getMatrices().pop();
            if (p.ratingChange != 0) {
                ctx.drawCenteredTextWithShadow(tr, (p.ratingChange > 0 ? "+" : "") + p.ratingChange + ClientState.t(" rating", " рейтинга"), sw / 2, sh / 3 + 28,
                        0xFFFFD040);
            }
        }
    }

    private static String objective(String o) {
        String[] p = o.split(":");
        if (p[0].endsWith("_flag") && p.length >= 2) {
            String who = p[0].startsWith("blue") ? ClientState.t("Blue flag", "Синий флаг") : ClientState.t("Red flag", "Красный флаг");
            String st = switch (p[1]) {
                case "carried" -> ClientState.t("carried by ", "у ") + (p.length > 2 ? p[2] : "?");
                case "dropped" -> ClientState.t("dropped", "брошен");
                default -> ClientState.t("at base", "на базе");
            };
            return who + ": " + st;
        }
        if (p[0].equals("node") && p.length >= 4) {
            String owner = p[2].equals("0") ? ClientState.t("Blue", "Синие") : p[2].equals("1") ? ClientState.t("Red", "Красные") : ClientState.t("neutral", "нейтральна");
            return p[1] + ": " + owner + (p[3].equals("0") ? "" : " (" + p[3] + "%)");
        }
        if (p[0].equals("round") && p.length >= 3) return ClientState.t("Round ", "Раунд ") + p[1] + "/6 — " + ClientState.t("won: ", "побед: ") + p[2];
        return o;
    }

    private static void meter(DrawContext ctx, TextRenderer tr, int sw, int sh) {
        S2C.Meter m = ClientState.meter;
        if (!Controls.showMeter || m == null || m.rows.isEmpty() || m.duration <= 0) return;
        int w = 150, x = sw - w - 4, y = sh - 22 - 10 - Math.min(8, m.rows.size()) * 10 - 12;
        ctx.fill(x - 2, y - 2, x + w + 2, y + 10 + Math.min(8, m.rows.size()) * 10, 0x90000000);
        ctx.drawText(tr, ClientState.t("Damage", "Урон") + " (" + Mth.formatTime(m.duration) + ")", x, y, 0xFFFFD040, true);
        double top = 1;
        for (S2C.MeterRow r : m.rows) top = Math.max(top, r.damage);
        int i = 0;
        for (S2C.MeterRow r : m.rows) {
            if (i >= 8) break;
            int ry = y + 10 + i * 10;
            bar(ctx, x, ry, w, 9, r.damage / top, 0xFF000000 | r.color);
            ctx.drawText(tr, trim(tr, r.name, 80), x + 2, ry + 1, 0xFFFFFFFF, true);
            String v = Mth.shortNumber(r.damage / m.duration);
            ctx.drawText(tr, v, x + w - tr.getWidth(v) - 2, ry + 1, 0xFFFFFFFF, true);
            i++;
        }
    }

    // ------------------------------------------------------------------ alerts

    private static void alerts(DrawContext ctx, TextRenderer tr, int sw, int sh, S2C.Self self) {
        int y = sh / 4;
        for (ClientState.Timed<S2C.Warning> w : ClientState.warnings) {
            float a = w.age() < 3 ? 1 : Math.max(0, 1 - (w.age() - 3));
            int alpha = Math.max(8, (int) (a * 255));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, y, 0);
            ctx.getMatrices().scale(1.5f, 1.5f, 1);
            ctx.drawCenteredTextWithShadow(tr, ClientState.t(w.value.text), 0, 0, (alpha << 24) | (w.value.color & 0xFFFFFF));
            ctx.getMatrices().pop();
            y += 16;
        }
        int ey = sh / 2 - 30;
        for (ClientState.Timed<S2C.Error> e : ClientState.errors) {
            String msg = errorText(e.value.code);
            if (msg.isEmpty()) continue;
            ctx.drawCenteredTextWithShadow(tr, msg, sw / 2, ey, 0xFFFF3030);
            ey -= 10;
        }
        if (self.ghost) {
            ctx.drawCenteredTextWithShadow(tr, ClientState.t("You are dead. Wait for a resurrection or press [Z] to release.",
                    "Вы мертвы. Дождитесь воскрешения или нажмите [Z], чтобы отпустить дух."), sw / 2, sh / 2 + 20, 0xFFFF6060);
        }
        if (ClientState.invite != null) {
            ctx.drawCenteredTextWithShadow(tr, ClientState.invite.fromName + ClientState.t(" invites you to a group. [Y] accept", " приглашает вас в группу. [Y] — принять"),
                    sw / 2, sh / 2 + 34, 0xFF80C0FF);
        }
        S2C.Group g = ClientState.group;
        if (g != null && g.readyCheck) {
            ctx.drawCenteredTextWithShadow(tr, ClientState.t("Ready check! /wow ready — or open the group finder [O]", "Проверка готовности! Откройте поиск группы [O]"),
                    sw / 2, sh / 2 + 46, 0xFFFFD040);
        }
    }

    private static String errorText(String code) {
        try {
            return ClientState.t(com.wowcraft.core.combat.CastResult.valueOf(code).message);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static void loot(DrawContext ctx, TextRenderer tr, int sh) {
        int y = sh - 80;
        for (ClientState.Timed<S2C.Loot> l : ClientState.loot) {
            for (ItemData d : l.value.items) {
                String s = ClientState.t("You receive: ", "Вы получаете: ") + d.displayName().get(ClientState.lang()) + " (" + d.ilvl + ")";
                ctx.drawText(tr, s, 4, y, d.quality().color, true);
                y -= 10;
            }
            for (var en : l.value.currencies.entrySet()) {
                var c = com.wowcraft.core.item.Currency.byName(en.getKey());
                if (c == null) continue;
                String amount = c == com.wowcraft.core.item.Currency.GOLD ? com.wowcraft.core.item.Currency.formatGold(en.getValue()) : String.valueOf(en.getValue());
                ctx.drawText(tr, "+" + amount + " " + ClientState.t(c.name), 4, y, c.color, true);
                y -= 10;
            }
        }
    }

    static DungeonDef dungeon(String id) {
        return id == null ? null : Dungeons.get(id);
    }
}
