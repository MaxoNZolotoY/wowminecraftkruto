package com.wowcraft.mc.client.screen;

import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.talent.ModDescriber;
import com.wowcraft.core.talent.TalentNode;
import com.wowcraft.core.talent.TalentTree;
import com.wowcraft.mc.client.ClientNet;
import com.wowcraft.mc.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Class tree, spec tree and hero talents (left click learn, right click unlearn). */
public class TalentScreen extends WowScreen {
    private static final int NODE = 26, GAP = 14;

    public TalentScreen() {
        super("Talents", "Таланты");
    }

    @Override
    protected void init() {
        layout(400, 220);
        button(left + panelW - 70, top + panelH - 24, 62, 20, t("Reset", "Сброс"), () -> {
            C2S.Talent tl = new C2S.Talent();
            tl.reset = true;
            ClientNet.send(tl);
        });
    }

    private Set<String> chosen() {
        return new LinkedHashSet<>(ClientState.character.talents);
    }

    private int treeX(int i) {
        return left + 14 + i * 140;
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Spec spec = ClientState.spec();
        if (spec == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, t("Choose a class first (K).", "Сначала выберите класс (K)."), left + panelW / 2, top + 60, 0xFFFFFFFF);
            return;
        }
        ClassKit kit = Content.kit(spec.wowClass);
        int level = ClientState.character.level;
        Set<String> chosen = chosen();
        drawTree(ctx, mouseX, mouseY, kit.classTree, ClientState.t(spec.wowClass.name), treeX(0), chosen, level);
        drawTree(ctx, mouseX, mouseY, kit.specTrees.get(spec), ClientState.t(spec.name), treeX(1), chosen, level);
        // hero talents
        int hx = treeX(2), hy = top + 22;
        ctx.drawText(textRenderer, t("Hero (level 70)", "Героические (ур. 70)"), hx, hy, 0xFFFFD040, true);
        for (int i = 0; i < kit.heroOptions.size(); i++) {
            TalentNode n = kit.heroOptions.get(i);
            int x = hx, y = hy + 16 + i * 40;
            boolean has = chosen.contains(n.id);
            boolean avail = level >= TalentTree.HERO_LEVEL;
            drawNode(ctx, n, x, y, has, avail, mouseX, mouseY, 100);
        }
        ctx.drawText(textRenderer, t("Left click: learn • Right click: remove", "ЛКМ: изучить • ПКМ: убрать"), left + 10, top + panelH - 18, 0xFF909090, false);
    }

    private void drawTree(DrawContext ctx, int mx, int my, TalentTree tree, String title, int x0, Set<String> chosen, int level) {
        if (tree == null) return;
        int spent = tree.spent(chosen), total = tree.pointsAt(level);
        ctx.drawText(textRenderer, title + "  " + spent + "/" + total, x0, top + 22, 0xFFFFD040, true);
        for (TalentNode n : tree.nodes()) {
            int x = x0 + n.col * (NODE + GAP), y = top + 40 + n.row * (NODE + GAP + 6);
            boolean has = chosen.contains(n.id);
            boolean avail = !has && tree.canLearn(chosen, n.id, level);
            drawNode(ctx, n, x, y, has, avail, mx, my, NODE);
        }
    }

    private void drawNode(DrawContext ctx, TalentNode n, int x, int y, boolean has, boolean avail, int mx, int my, int w) {
        int border = has ? 0xFFFFD040 : avail ? 0xFF40FF40 : 0xFF505050;
        ctx.fill(x - 1, y - 1, x + w + 1, y + NODE + 1, border);
        ctx.fill(x, y, x + w, y + NODE, has ? 0xFF4A3A10 : 0xFF202024);
        String name = ClientState.t(n.name);
        if (w > NODE) {
            ctx.drawText(textRenderer, name.length() > 16 ? name.substring(0, 16) : name, x + 3, y + 9, has ? 0xFFFFFFFF : 0xFFB0B0B0, false);
        } else {
            String ini = com.wowcraft.mc.client.hud.Icons.initials(name);
            ctx.drawText(textRenderer, ini, x + (w - textRenderer.getWidth(ini)) / 2, y + 9, has ? 0xFFFFFFFF : 0xFF909090, true);
        }
        if (inside(mx, my, x, y, w, NODE)) {
            List<String> raw = new ArrayList<>();
            raw.add(name);
            String desc = n.customDescription != null ? ClientState.t(n.customDescription)
                    : ModDescriber.describeAll(n.mods, n.passiveAura, n.grantAbility, ClientState.lang());
            raw.add(desc);
            raw.add(has ? t("Learned", "Изучено") : avail ? t("Click to learn", "Нажмите, чтобы изучить") : t("Locked", "Недоступно"));
            hoverTooltip = lines(raw, 0xFFFFD040);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Spec spec = ClientState.spec();
        if (spec != null) {
            ClassKit kit = Content.kit(spec.wowClass);
            List<TalentNode> nodes = new ArrayList<>();
            List<int[]> rects = new ArrayList<>();
            TalentTree[] trees = {kit.classTree, kit.specTrees.get(spec)};
            for (int ti = 0; ti < 2; ti++) {
                if (trees[ti] == null) continue;
                for (TalentNode n : trees[ti].nodes()) {
                    nodes.add(n);
                    rects.add(new int[]{treeX(ti) + n.col * (NODE + GAP), top + 40 + n.row * (NODE + GAP + 6), NODE});
                }
            }
            for (int i = 0; i < kit.heroOptions.size(); i++) {
                nodes.add(kit.heroOptions.get(i));
                rects.add(new int[]{treeX(2), top + 38 + i * 40, 100});
            }
            for (int i = 0; i < nodes.size(); i++) {
                int[] r = rects.get(i);
                if (inside((int) mouseX, (int) mouseY, r[0], r[1], r[2], NODE)) {
                    C2S.Talent tl = new C2S.Talent();
                    tl.node = nodes.get(i).id;
                    tl.learn = button == 0;
                    ClientNet.send(tl);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    static Text txt(String s) {
        return Text.literal(s);
    }
}
