package com.wowcraft.mc.client.screen;

import com.wowcraft.core.content.Registry;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.Currency;
import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Tooltip;
import com.wowcraft.mc.client.ClientNet;
import com.wowcraft.mc.client.ClientState;
import com.wowcraft.mc.client.hud.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Character sheet: equipment (with upgrades), stats, currencies and the spellbook (drag abilities to the action bar). */
public class CharacterScreen extends WowScreen {
    private String picked;
    private int page;

    public CharacterScreen() {
        super("Character", "Персонаж");
        C2S.Request r = new C2S.Request();
        r.what = "stats";
        ClientNet.send(r);
        C2S.Request c = new C2S.Request();
        c.what = "character";
        ClientNet.send(c);
    }

    @Override
    protected void init() {
        layout(420, 250);
        button(left + 6, top + panelH - 24, 90, 20, t("Change class", "Сменить класс"), () -> MinecraftClient.getInstance().setScreen(new ClassSelectScreen()));
        button(left + 100, top + panelH - 24, 70, 20, t("Talents", "Таланты"), () -> MinecraftClient.getInstance().setScreen(new TalentScreen()));
        int y = top + 30;
        for (EquipSlot slot : EquipSlot.values()) {
            ItemData d = ClientState.character.equipment.get(slot.name());
            if (d != null && d.canUpgrade()) {
                button(left + 150, y - 1, 14, 10, "+", () -> {
                    C2S.Upgrade u = new C2S.Upgrade();
                    u.slot = slot.name();
                    ClientNet.send(u);
                });
            }
            if (d != null && !slot.isVanillaSlot()) {
                button(left + 166, y - 1, 14, 10, "x", () -> {
                    C2S.Unequip u = new C2S.Unequip();
                    u.slot = slot.name();
                    ClientNet.send(u);
                });
            }
            y += 11;
        }
        button(left + panelW - 46, top + 22, 18, 14, "<", () -> {
            page = Math.max(0, page - 1);
            refresh();
        });
        button(left + panelW - 24, top + 22, 18, 14, ">", () -> {
            page++;
            refresh();
        });
    }

    private List<String> spellbook() {
        List<String> out = new ArrayList<>();
        for (String id : ClientState.character.abilities) {
            Ability a = Registry.ability(id);
            if (a != null && !a.passive && !a.hidden) out.add(id);
        }
        return out;
    }

    @Override
    protected void drawContent(DrawContext ctx, int mx, int my, float delta) {
        S2C.Character ch = ClientState.character;
        Spec spec = ClientState.spec();
        String head = spec != null ? ch.level + " " + ClientState.t(spec.name) + " " + ClientState.t(spec.wowClass.name) : t("No class", "Без класса");
        ctx.drawText(textRenderer, head, left + 6, top + 16, spec != null ? spec.wowClass.color : 0xFFFFFFFF, true);
        // equipment
        int y = top + 30;
        for (EquipSlot slot : EquipSlot.values()) {
            ItemData d = ch.equipment.get(slot.name());
            String label = ClientState.t(slot.name) + ": ";
            ctx.drawText(textRenderer, label, left + 6, y, 0xFF909090, false);
            int lx = left + 6 + textRenderer.getWidth(label);
            if (d != null) {
                String n = d.displayName().get(ClientState.lang());
                String txt = n.length() > 18 ? n.substring(0, 18) + ".." : n;
                ctx.drawText(textRenderer, txt + " " + d.ilvl, lx, y, d.quality().color, false);
                if (inside(mx, my, left + 6, y, 140, 10)) {
                    List<Text> tip = new ArrayList<>();
                    tip.add(Text.literal(n).styled(s -> s.withColor(d.quality().color & 0xFFFFFF)));
                    com.wowcraft.mc.item.WowItems.tooltip(com.wowcraft.mc.item.WowItems.toStack(d), tip);
                    hoverTooltip = tip;
                }
            } else {
                ctx.drawText(textRenderer, "-", lx, y, 0xFF505050, false);
            }
            y += 11;
        }
        // stats & currencies
        int sx = left + 186, sy = top + 30;
        ctx.drawText(textRenderer, t("Item level ", "Ур. предметов ") + ch.itemLevel, sx, sy, 0xFFFFD040, true);
        sy += 11;
        ctx.drawText(textRenderer, t("M+ rating ", "Рейтинг M+ ") + ch.mythicRating, sx, sy, com.wowcraft.core.mythic.MythicScore.color(ch.mythicRating), true);
        sy += 11;
        if (ch.keystoneDungeon != null) {
            DungeonDef d = Dungeons.get(ch.keystoneDungeon);
            ctx.drawText(textRenderer, t("Key: ", "Ключ: ") + (d != null ? d.shortName : ch.keystoneDungeon) + " +" + ch.keystoneLevel, sx, sy, 0xFFA335EE, true);
            sy += 11;
        }
        S2C.Stats st = ClientState.stats;
        if (st != null) {
            String[][] rows = {{"health", "Health", "Здоровье"}, {"crit", "Crit %", "Крит. %"}, {"haste", "Haste %", "Скорость %"},
                    {"mastery", "Mastery %", "Искусность %"}, {"versatility", "Versatility %", "Универсальность %"}, {"armor", "Armor", "Броня"},
                    {"attack_power", "Attack power", "Сила атаки"}, {"spell_power", "Spell power", "Сила заклинаний"}};
            for (String[] r : rows) {
                Double v = st.values.get(r[0]);
                if (v == null) continue;
                ctx.drawText(textRenderer, t(r[1], r[2]) + ": " + (v == Math.floor(v) ? String.valueOf(v.longValue()) : String.valueOf(v)), sx, sy, 0xFFE0E0E0, false);
                sy += 10;
            }
        }
        sy += 3;
        for (var en : ch.currencies.entrySet()) {
            Currency c = Currency.byName(en.getKey());
            if (c == null || en.getValue() == 0) continue;
            String v = c == Currency.GOLD ? Currency.formatGold(en.getValue()) : String.valueOf(en.getValue());
            ctx.drawText(textRenderer, ClientState.t(c.name) + ": " + v, sx, sy, c.color, false);
            sy += 10;
            if (sy > top + panelH - 30) break;
        }
        // spellbook
        int bx = left + 300, by = top + 40;
        ctx.drawText(textRenderer, t("Spellbook", "Книга заклинаний"), bx, top + 24, 0xFFFFD040, true);
        List<String> book = spellbook();
        int perPage = 24;
        int from = Math.min(page * perPage, Math.max(0, book.size() - 1));
        for (int i = from; i < Math.min(book.size(), from + perPage); i++) {
            int idx = i - from;
            int x = bx + (idx % 4) * 28, yy = by + (idx / 4) * 26;
            String id = book.get(i);
            Icons.ability(ctx, id, x, yy, 22);
            if (id.equals(picked)) ctx.drawBorder(x - 1, yy - 1, 24, 24, 0xFFFFD040);
            if (inside(mx, my, x, yy, 22, 22)) hoverTooltip = lines(Tooltip.lines(Registry.ability(id), ClientState.lang(), null), 0xFFFFD040);
        }
        // action bar for drag & drop
        String[] bar = ch.bar;
        int barY = top + panelH - 50, barX = left + 186;
        ctx.drawText(textRenderer, picked != null ? t("Click a slot to place it", "Нажмите на ячейку панели") : t("Pick an ability, then a slot", "Выберите способность, затем ячейку"),
                barX, barY - 10, 0xFF909090, false);
        for (int i = 0; i < 12; i++) {
            int x = barX + i * 19;
            String id = bar != null && i < bar.length ? bar[i] : null;
            if (id != null) Icons.ability(ctx, id, x, barY, 17);
            else ctx.fill(x, barY, x + 17, barY + 17, 0x80404040);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        List<String> book = spellbook();
        int bx = left + 300, by = top + 40, perPage = 24;
        int from = Math.min(page * perPage, Math.max(0, book.size() - 1));
        for (int i = from; i < Math.min(book.size(), from + perPage); i++) {
            int idx = i - from;
            int x = bx + (idx % 4) * 28, yy = by + (idx / 4) * 26;
            if (inside(mx, my, x, yy, 22, 22)) {
                picked = book.get(i);
                return true;
            }
        }
        int barY = top + panelH - 50, barX = left + 186;
        for (int i = 0; i < 12; i++) {
            if (inside(mx, my, barX + i * 19, barY, 17, 17)) {
                C2S.SetBar sb = new C2S.SetBar();
                sb.slot = i;
                sb.ability = button == 1 ? null : picked;
                ClientNet.send(sb);
                if (ClientState.character.bar != null && i < ClientState.character.bar.length) ClientState.character.bar[i] = sb.ability;
                picked = null;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
