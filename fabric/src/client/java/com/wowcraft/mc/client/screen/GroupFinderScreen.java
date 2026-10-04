package com.wowcraft.mc.client.screen;

import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.mythic.Affix;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.mc.client.ClientNet;
import com.wowcraft.mc.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Group finder: dungeons, raids, Mythic+ listings, PvP queues and the current group. */
public class GroupFinderScreen extends WowScreen {
    private static int tab;
    private static String role = "dps";
    private static boolean followers = true;
    private static boolean rated;
    private static String selectedDungeon;
    private TextFieldWidget inviteField;

    public GroupFinderScreen() {
        super("Group Finder", "Поиск группы");
        C2S.LfgAction r = new C2S.LfgAction();
        r.action = "refresh";
        ClientNet.send(r);
        C2S.Request g = new C2S.Request();
        g.what = "group";
        ClientNet.send(g);
    }

    private static final String[][] TABS = {{"Dungeons", "Подземелья"}, {"Raids", "Рейды"}, {"Mythic+", "Эпохальный+"}, {"PvP", "PvP"}, {"Group", "Группа"}};

    @Override
    protected void init() {
        layout(420, 250);
        for (int i = 0; i < TABS.length; i++) {
            int idx = i;
            button(left + 6 + i * 82, top + 16, 80, 16, (i == tab ? "» " : "") + t(TABS[i][0], TABS[i][1]), () -> {
                tab = idx;
                refresh();
            });
        }
        int y = top + 38;
        switch (tab) {
            case 0 -> initDungeons(y, DungeonDef.Type.DUNGEON);
            case 1 -> initDungeons(y, DungeonDef.Type.RAID);
            case 2 -> initMythic(y);
            case 3 -> initPvp(y);
            default -> initGroup(y);
        }
        if (ClientState.lfg != null && ClientState.lfg.queue != null) {
            button(left + panelW - 96, top + panelH - 22, 90, 18, t("Leave queue", "Покинуть очередь"), () -> {
                lfg("leave");
                C2S.PvpAction p = new C2S.PvpAction();
                p.action = "leave";
                ClientNet.send(p);
            });
        }
    }

    private void lfg(String action) {
        C2S.LfgAction a = new C2S.LfgAction();
        a.action = action;
        a.role = role;
        ClientNet.send(a);
    }

    private void cycleRole() {
        role = switch (role) {
            case "tank" -> "healer";
            case "healer" -> "dps";
            default -> "tank";
        };
        refresh();
    }

    private String roleName() {
        return switch (role) {
            case "tank" -> t("Tank", "Танк");
            case "healer" -> t("Healer", "Лекарь");
            default -> t("Damage", "Урон");
        };
    }

    private void initDungeons(int y, DungeonDef.Type type) {
        List<DungeonDef> defs = Dungeons.ofType(type);
        for (int i = 0; i < defs.size(); i++) {
            DungeonDef d = defs.get(i);
            button(left + 6, y + i * 19, 150, 18, (d.id.equals(selectedDungeon) ? "» " : "") + ClientState.t(d.name), () -> {
                selectedDungeon = d.id;
                refresh();
            });
        }
        int rx = left + 166;
        button(rx, y, 120, 18, t("Role: ", "Роль: ") + roleName(), this::cycleRole);
        button(rx + 124, y, 120, 18, (followers ? "☑ " : "☐ ") + t("Fill with bots", "Боты-соратники"), () -> {
            followers = !followers;
            refresh();
        });
        DungeonDef sel = selectedDungeon != null ? Dungeons.get(selectedDungeon) : null;
        if (sel != null && sel.type != type) sel = null;
        if (type == DungeonDef.Type.DUNGEON) {
            String[] diffs = {"normal", "heroic"};
            for (int i = 0; i < diffs.length; i++) {
                String df = diffs[i];
                String label = t("Queue ", "Очередь: ") + (df.equals("normal") ? t("Normal", "Обычный") : t("Heroic", "Героический"));
                DungeonDef target = sel;
                button(rx, y + 24 + i * 20, 244, 18, label + (target != null ? "" : t(" (random)", " (случайное)")), () -> queue(target, df));
            }
        } else {
            DungeonDef target = sel;
            button(rx, y + 24, 244, 18, t("Raid Finder queue (LFR)", "Поиск рейда (LFR)"), () -> queue(target, "lfr"));
        }
        if (sel != null) {
            int by = y + 70;
            List<Difficulty> diffs = sel.difficulties;
            int col = 0;
            for (Difficulty df : diffs) {
                if (df == Difficulty.MYTHIC_PLUS || df == Difficulty.LFR) continue;
                DungeonDef target = sel;
                button(rx + (col % 2) * 124, by + (col / 2) * 20, 120, 18, t("Enter: ", "Войти: ") + ClientState.t(df.name), () -> {
                    C2S.InstanceAction a = new C2S.InstanceAction();
                    a.action = "enter";
                    a.dungeon = target.id;
                    a.difficulty = df.name();
                    ClientNet.send(a);
                    close();
                });
                col++;
            }
        }
    }

    private void queue(DungeonDef d, String difficulty) {
        C2S.LfgAction a = new C2S.LfgAction();
        a.action = "queue";
        a.dungeon = d != null ? d.id : "random";
        a.difficulty = difficulty;
        a.role = role;
        a.followers = followers;
        ClientNet.send(a);
    }

    private void initMythic(int y) {
        S2C.Character ch = ClientState.character;
        if (ch.keystoneDungeon != null) {
            button(left + 6, y, 160, 18, t("Start my keystone", "Запустить мой ключ"), () -> {
                C2S.InstanceAction a = new C2S.InstanceAction();
                a.action = "enter";
                a.dungeon = ch.keystoneDungeon;
                a.difficulty = Difficulty.MYTHIC_PLUS.name();
                ClientNet.send(a);
                close();
            });
            button(left + 170, y, 120, 18, t("List my group", "Выставить группу"), () -> {
                C2S.LfgAction a = new C2S.LfgAction();
                a.action = "list_key";
                ClientNet.send(a);
            });
            button(left + 294, y, 120, 18, t("Fill with bots", "Добавить ботов"), () -> lfg("fill_bots"));
        }
        button(left + 294, y + 20, 120, 18, t("Role: ", "Роль: ") + roleName(), this::cycleRole);
        S2C.Lfg lfg = ClientState.lfg;
        int ly = y + 66;
        if (lfg != null) {
            for (int i = 0; i < lfg.listings.size() && i < 6; i++) {
                S2C.Listing l = lfg.listings.get(i);
                if (!l.applied) {
                    button(left + panelW - 70, ly + i * 14 - 2, 62, 12, t("Apply", "Заявка"), () -> {
                        C2S.LfgAction a = new C2S.LfgAction();
                        a.action = "apply";
                        a.listing = l.id;
                        a.role = role;
                        ClientNet.send(a);
                    });
                }
            }
            int ay = ly + 96;
            for (int i = 0; i < lfg.applicants.size() && i < 4; i++) {
                S2C.Applicant ap = lfg.applicants.get(i);
                button(left + panelW - 130, ay + i * 14 - 2, 60, 12, t("Accept", "Принять"), () -> {
                    C2S.LfgAction a = new C2S.LfgAction();
                    a.action = "accept";
                    a.applicant = ap.uuid;
                    ClientNet.send(a);
                });
                button(left + panelW - 66, ay + i * 14 - 2, 60, 12, t("Decline", "Отказать"), () -> {
                    C2S.LfgAction a = new C2S.LfgAction();
                    a.action = "decline";
                    a.applicant = ap.uuid;
                    ClientNet.send(a);
                });
            }
        }
    }

    private void initPvp(int y) {
        button(left + 6, y, 120, 18, (rated ? "☑ " : "☐ ") + t("Rated", "Рейтинговые"), () -> {
            rated = !rated;
            refresh();
        });
        String[][] brackets = {{"2v2", "Arena 2v2", "Арена 2х2"}, {"3v3", "Arena 3v3", "Арена 3х3"}, {"5v5", "Arena 5v5", "Арена 5х5"},
                {"shuffle", "Solo Shuffle", "Потасовка"}, {"ctf", "Capture the Flag", "Захват флага"}, {"domination", "Domination", "Господство"},
                {"bg", "Random Battleground", "Случайное поле боя"}};
        for (int i = 0; i < brackets.length; i++) {
            String[] b = brackets[i];
            button(left + 6 + (i % 2) * 150, y + 24 + (i / 2) * 22, 146, 20, t(b[1], b[2]), () -> {
                C2S.PvpAction a = new C2S.PvpAction();
                a.action = "queue";
                a.bracket = b[0];
                a.rated = rated;
                ClientNet.send(a);
            });
        }
    }

    private void initGroup(int y) {
        inviteField = new TextFieldWidget(textRenderer, left + 6, y, 120, 16, Text.literal("name"));
        addDrawableChild(inviteField);
        button(left + 130, y, 70, 16, t("Invite", "Пригласить"), () -> {
            if (inviteField.getText().isBlank()) return;
            group("invite", inviteField.getText().trim(), null);
            inviteField.setText("");
        });
        button(left + 204, y, 70, 16, t("Leave", "Покинуть"), () -> group("leave", null, null));
        button(left + 278, y, 70, 16, t("Ready?", "Готовы?"), () -> group("ready_check", null, null));
        button(left + 352, y, 62, 16, t("Raid", "Рейд"), () -> group("raid", null, null));
        String[][] bots = {{"tank", "+Tank bot", "+Бот-танк"}, {"healer", "+Healer bot", "+Бот-лекарь"}, {"dps", "+DPS bot", "+Бот-боец"}};
        for (int i = 0; i < bots.length; i++) {
            String[] b = bots[i];
            button(left + 6 + i * 104, y + 20, 100, 16, t(b[1], b[2]), () -> group("add_bot", null, b[0]));
        }
        button(left + 318, y + 20, 96, 16, t("Fill group", "Заполнить"), () -> group("fill_bots", null, null));
        S2C.Group g = ClientState.group;
        if (g != null && g.readyCheck) {
            button(left + 6, y + 40, 100, 16, t("I'm ready", "Я готов"), () -> group("ready", null, null));
            button(left + 110, y + 40, 100, 16, t("Not ready", "Не готов"), () -> group("not_ready", null, null));
        }
        if (g != null) {
            for (int i = 0; i < g.members.size() && i < 10; i++) {
                S2C.GroupMember m = g.members.get(i);
                if (m.leader) continue;
                int my = y + 66 + i * 14;
                button(left + panelW - 60, my - 2, 54, 12, t("Kick", "Исключить"), () -> group("kick", m.name, null));
                if (!m.bot) button(left + panelW - 120, my - 2, 56, 12, t("Promote", "Лидер"), () -> group("promote", m.name, null));
            }
        }
    }

    private void group(String action, String name, String value) {
        C2S.GroupAction a = new C2S.GroupAction();
        a.action = action;
        a.name = name;
        a.value = value;
        ClientNet.send(a);
    }

    @Override
    protected void drawContent(DrawContext ctx, int mx, int my, float delta) {
        S2C.Lfg lfg = ClientState.lfg;
        int y = top + 38;
        if (lfg != null && lfg.queue != null) {
            ctx.drawText(textRenderer, t("In queue: ", "В очереди: ") + lfg.queue + " (" + (int) lfg.queueTime + "s)", left + 6, top + panelH - 16, 0xFF80FF80, false);
        }
        switch (tab) {
            case 0, 1 -> {
                DungeonDef sel = selectedDungeon != null ? Dungeons.get(selectedDungeon) : null;
                if (sel != null) {
                    int ty = y + 116;
                    for (String line : wrap(ClientState.t(sel.description), 40)) {
                        ctx.drawText(textRenderer, line, left + 166, ty, 0xFFC0C0C0, false);
                        ty += 10;
                    }
                }
            }
            case 2 -> drawMythic(ctx, y);
            case 3 -> drawPvp(ctx, y);
            default -> drawGroup(ctx, y);
        }
    }

    private void drawMythic(DrawContext ctx, int y) {
        S2C.Character ch = ClientState.character;
        S2C.Lfg lfg = ClientState.lfg;
        String key = ch.keystoneDungeon != null ? Dungeons.get(ch.keystoneDungeon).name.get(ClientState.lang()) + " +" + ch.keystoneLevel
                : t("No keystone", "Нет ключа");
        ctx.drawText(textRenderer, t("Your keystone: ", "Ваш ключ: ") + key, left + 6, y + 24, 0xFFA335EE, true);
        if (lfg != null) {
            StringBuilder af = new StringBuilder();
            for (String a : lfg.weeklyAffixes) {
                Affix x = Affix.byName(a);
                if (x != null) af.append(af.length() > 0 ? ", " : "").append(ClientState.t(x.name));
            }
            ctx.drawText(textRenderer, t("This week: ", "На этой неделе: ") + af, left + 6, y + 36, 0xFFB0B0FF, false);
            int ly = y + 66;
            ctx.drawText(textRenderer, t("Listed groups:", "Группы:"), left + 6, ly - 12, 0xFFFFD040, true);
            for (int i = 0; i < lfg.listings.size() && i < 6; i++) {
                S2C.Listing l = lfg.listings.get(i);
                StringBuilder roles = new StringBuilder();
                for (String r : l.roles) roles.append(r.equals("TANK") ? "T" : r.equals("HEALER") ? "H" : "D");
                String line = l.title + " — " + l.leader + " [" + roles + "] " + l.members + "/5" + (l.minRating > 0 ? " ≥" + (int) l.minRating : "")
                        + (l.applied ? t(" (applied)", " (заявка)") : "");
                ctx.drawText(textRenderer, line, left + 6, ly + i * 14, 0xFFE0E0E0, false);
            }
            int ay = ly + 96;
            if (!lfg.applicants.isEmpty()) ctx.drawText(textRenderer, t("Applicants:", "Заявки:"), left + 6, ay - 12, 0xFFFFD040, true);
            for (int i = 0; i < lfg.applicants.size() && i < 4; i++) {
                S2C.Applicant a = lfg.applicants.get(i);
                WowClass c = WowClass.byId(a.wowClass);
                ctx.drawText(textRenderer, a.name + " " + a.role + " ilvl " + (int) a.itemLevel + " • " + a.rating, left + 6, ay + i * 14,
                        c != null ? c.color : 0xFFFFFFFF, false);
            }
        }
    }

    private void drawPvp(DrawContext ctx, int y) {
        S2C.Character ch = ClientState.character;
        int ry = y + 120;
        ctx.drawText(textRenderer, t("Honor level ", "Уровень чести ") + ch.honorLevel, left + 6, ry, 0xFFE06666, true);
        ry += 12;
        for (var en : ch.pvpRatings.entrySet()) {
            ctx.drawText(textRenderer, en.getKey() + ": " + en.getValue(), left + 6, ry, 0xFFFFD040, false);
            ry += 10;
        }
        ctx.drawText(textRenderer, t("Empty spots are filled with bots after 10 seconds.", "Свободные места заполняются ботами через 10 секунд."), left + 160,
                y + 120, 0xFF909090, false);
    }

    private void drawGroup(DrawContext ctx, int y) {
        S2C.Group g = ClientState.group;
        if (g == null || !g.inGroup) {
            ctx.drawText(textRenderer, t("You are not in a group.", "Вы не в группе."), left + 6, y + 66, 0xFF909090, false);
            return;
        }
        for (int i = 0; i < g.members.size() && i < 10; i++) {
            S2C.GroupMember m = g.members.get(i);
            WowClass c = WowClass.byId(m.wowClass);
            String role = m.role == null ? "?" : m.role.equals("TANK") ? t("Tank", "Танк") : m.role.equals("HEALER") ? t("Healer", "Лекарь") : t("DPS", "Урон");
            String ready = m.ready == 1 ? " ✔" : m.ready == 2 ? " ✘" : "";
            String line = (m.leader ? "★ " : "") + m.name + (m.bot ? " [bot]" : "") + " — " + role + " • ilvl " + (int) m.itemLevel
                    + (m.rating > 0 ? " • " + m.rating : "") + (m.online ? "" : t(" (offline)", " (не в сети)")) + ready;
            ctx.drawText(textRenderer, line, left + 6, y + 66 + i * 14, c != null ? c.color : 0xFFFFFFFF, false);
        }
    }
}
