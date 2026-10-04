package com.wowcraft.mc.client.screen;

import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.player.GreatVault;
import com.wowcraft.core.util.Mth;
import com.wowcraft.mc.client.ClientNet;
import com.wowcraft.mc.client.ClientState;
import com.wowcraft.mc.item.WowItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** The Great Vault (weekly rewards) and the Mythic+ leaderboard. */
public class VaultScreen extends WowScreen {
    public VaultScreen() {
        super("Great Vault", "Великое хранилище");
    }

    @Override
    protected void init() {
        layout(420, 240);
        S2C.Vault v = ClientState.vault;
        if (v != null && !v.claimed) {
            for (int i = 0; i < v.rewards.size() && i < 9; i++) {
                int idx = i;
                button(left + 6 + (i % 3) * 132, top + 130 + (i / 3) * 18, 40, 16, t("Take", "Взять"), () -> {
                    C2S.VaultClaim c = new C2S.VaultClaim();
                    c.index = idx;
                    ClientNet.send(c);
                });
            }
        }
    }

    @Override
    protected void drawContent(DrawContext ctx, int mx, int my, float delta) {
        S2C.Vault v = ClientState.vault;
        String[][] rows = {{"Mythic+ (1/4/8 dungeons)", "Эпохальный+ (1/4/8 подземелий)"}, {"Raid (2/4/6 bosses)", "Рейд (2/4/6 боссов)"},
                {"World & PvP (3/6/9)", "Мир и PvP (3/6/9)"}};
        int[][] thresholds = {GreatVault.MYTHIC_THRESHOLDS, GreatVault.RAID_THRESHOLDS, GreatVault.WORLD_THRESHOLDS};
        for (int r = 0; r < 3; r++) {
            int y = top + 22 + r * 34;
            ctx.drawText(textRenderer, t(rows[r][0], rows[r][1]), left + 6, y, 0xFFFFD040, true);
            int progress = v == null ? 0 : r == 0 ? v.mythicRuns.size() : r == 1 ? v.raidKills.size() : v.world;
            for (int i = 0; i < 3; i++) {
                int x = left + 6 + i * 132, sy = y + 11;
                boolean unlocked = progress >= thresholds[r][i];
                ctx.fill(x, sy, x + 126, sy + 18, unlocked ? 0xFF3A2A60 : 0xFF202024);
                int ilvl = v != null && v.slots != null && v.slots.length > r && v.slots[r].length > i ? v.slots[r][i] : 0;
                String s = unlocked ? t("Item level ", "Уровень предмета ") + ilvl : Math.min(progress, thresholds[r][i]) + "/" + thresholds[r][i];
                ctx.drawText(textRenderer, s, x + 4, sy + 5, unlocked ? 0xFFA335EE : 0xFF808080, false);
            }
        }
        int ry = top + 120;
        if (v != null && !v.rewards.isEmpty()) {
            ctx.drawText(textRenderer, v.claimed ? t("Reward claimed this week.", "Награда за эту неделю получена.")
                    : t("Choose one reward from last week:", "Выберите одну награду за прошлую неделю:"), left + 6, ry, 0xFF80FF80, true);
            for (int i = 0; i < v.rewards.size() && i < 9; i++) {
                ItemData d = v.rewards.get(i);
                int x = left + 50 + (i % 3) * 132, y = top + 134 + (i / 3) * 18;
                String n = d.displayName().get(ClientState.lang());
                ctx.drawText(textRenderer, (n.length() > 12 ? n.substring(0, 12) + ".." : n) + " " + d.ilvl, x, y, d.quality().color, false);
                if (inside(mx, my, x, y - 2, 84, 12)) {
                    List<Text> tip = new ArrayList<>();
                    tip.add(Text.literal(n).styled(s -> s.withColor(d.quality().color & 0xFFFFFF)));
                    WowItems.tooltip(WowItems.toStack(d), tip);
                    hoverTooltip = tip;
                }
            }
        } else {
            ctx.drawText(textRenderer, t("Rewards appear here after the weekly reset (Wednesday).", "Награды появятся после еженедельного сброса (среда)."),
                    left + 6, ry, 0xFF909090, false);
        }
        S2C.Leaderboard lb = ClientState.leaderboard;
        int ly = top + 190;
        ctx.drawText(textRenderer, t("Best keys on this server:", "Лучшие ключи сервера:"), left + 6, ly, 0xFFFFD040, true);
        if (lb != null) {
            for (int i = 0; i < lb.rows.size() && i < 4; i++) {
                S2C.LeaderboardRow r = lb.rows.get(i);
                DungeonDef d = Dungeons.get(r.dungeon);
                ctx.drawText(textRenderer, (d != null ? d.shortName : r.dungeon) + " +" + r.level + " " + Mth.formatTime(r.time) + (r.timed ? "" : "*") + "  "
                        + String.join(", ", r.party), left + 6, ly + 11 + i * 10, r.timed ? 0xFFE0E0E0 : 0xFF909090, false);
            }
            for (int i = 0; i < lb.topPlayers.size() && i < 4; i++) {
                ctx.drawText(textRenderer, (i + 1) + ". " + lb.topPlayers.get(i) + " " + lb.topRatings.get(i), left + 290, ly + 11 + i * 10,
                        com.wowcraft.core.mythic.MythicScore.color(lb.topRatings.get(i)), false);
            }
        }
    }
}
