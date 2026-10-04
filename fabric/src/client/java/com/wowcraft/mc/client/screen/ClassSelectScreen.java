package com.wowcraft.mc.client.screen;

import com.wowcraft.core.net.C2S;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.mc.client.ClientNet;
import com.wowcraft.mc.client.ClientState;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Choose a class and specialization. */
public class ClassSelectScreen extends WowScreen {
    private WowClass selected;

    public ClassSelectScreen() {
        super("Choose your class", "Выбор класса");
        Spec current = ClientState.spec();
        selected = current != null ? current.wowClass : WowClass.WARRIOR;
    }

    @Override
    protected void init() {
        layout(380, 240);
        WowClass[] all = WowClass.values();
        for (int i = 0; i < all.length; i++) {
            WowClass c = all[i];
            int col = i % 2, row = i / 2;
            button(left + 8 + col * 86, top + 20 + row * 22, 84, 20, ClientState.t(c.name), () -> {
                selected = c;
                refresh();
            });
        }
        List<Spec> specs = Spec.of(selected);
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            button(left + 190, top + 40 + i * 52, 180, 20, t("Play ", "Играть: ") + ClientState.t(s.name), () -> {
                C2S.ChooseClass c = new C2S.ChooseClass();
                c.wowClass = s.wowClass.id();
                c.spec = s.id();
                ClientNet.send(c);
                close();
            });
        }
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawText(textRenderer, ClientState.t(selected.name), left + 190, top + 22, selected.color, true);
        ctx.drawText(textRenderer, t("Armor: ", "Броня: ") + ClientState.t(selected.armor.name), left + 280, top + 22, 0xFFB0B0B0, false);
        List<Spec> specs = Spec.of(selected);
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            int y = top + 62 + i * 52;
            String role = switch (s.role) {
                case TANK -> t("Tank", "Танк");
                case HEALER -> t("Healer", "Лекарь");
                case MELEE_DPS -> t("Melee damage", "Урон в ближнем бою");
                case RANGED_DPS -> t("Ranged damage", "Урон на расстоянии");
            };
            int rc = s.role == Role.TANK ? 0xFF6FA8FF : s.role == Role.HEALER ? 0xFF60FF60 : 0xFFFF7070;
            ctx.drawText(textRenderer, role + " • " + ClientState.t(s.primaryStat.name), left + 192, y, rc, false);
            StringBuilder res = new StringBuilder();
            for (var r : s.resources) res.append(res.length() > 0 ? ", " : "").append(ClientState.t(r.name));
            ctx.drawText(textRenderer, res.toString(), left + 192, y + 10, 0xFFA0A0A0, false);
            ctx.drawText(textRenderer, t("Mastery: ", "Искусность: ") + ClientState.t(s.masteryName), left + 192, y + 20, 0xFFD0C080, false);
        }
        ctx.drawText(textRenderer, t("Change any time out of combat.", "Можно сменить в любой момент вне боя."), left + 8, top + panelH - 12, 0xFF808080, false);
    }
}
