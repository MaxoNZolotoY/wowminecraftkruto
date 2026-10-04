package com.wowcraft.mc.client.screen;

import com.wowcraft.mc.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Shared look and helpers for the WoW windows. */
public abstract class WowScreen extends Screen {
    protected int left, top, panelW, panelH;
    /** Tooltip lines for the area under the mouse, collected while rendering. */
    protected List<Text> hoverTooltip;

    protected WowScreen(String en, String ru) {
        super(Text.literal(ClientState.t(en, ru)));
    }

    protected void layout(int w, int h) {
        panelW = Math.min(w, width - 10);
        panelH = Math.min(h, height - 10);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    protected ButtonWidget button(int x, int y, int w, int h, String label, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> action.run()).dimensions(x, y, w, h).build();
        addDrawableChild(b);
        return b;
    }

    protected static String t(String en, String ru) {
        return ClientState.t(en, ru);
    }

    /** Rebuilds widgets (after state changes). */
    protected void refresh() {
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xFF8B6914);
        ctx.fill(left, top, left + panelW, top + panelH, 0xF0141418);
        ctx.drawCenteredTextWithShadow(textRenderer, title, left + panelW / 2, top + 5, 0xFFFFD040);
        hoverTooltip = null;
        drawContent(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        if (hoverTooltip != null && !hoverTooltip.isEmpty()) ctx.drawTooltip(textRenderer, hoverTooltip, mouseX, mouseY);
    }

    protected abstract void drawContent(DrawContext ctx, int mouseX, int mouseY, float delta);

    protected static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    protected static List<Text> lines(List<String> raw, int firstColor) {
        List<Text> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int c = i == 0 ? firstColor : 0xFFFFFFFF;
            for (String part : wrap(raw.get(i), 48)) out.add(Text.literal(part).styled(s -> s.withColor(c & 0xFFFFFF)));
        }
        return out;
    }

    protected static List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            if (line.length() + w.length() + 1 > width && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
