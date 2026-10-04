package com.wowcraft.mc.client.hud;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.mc.WowCraftMod;
import com.wowcraft.mc.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Ability / aura icons. Placeholder: a colored square with initials. Real icons can be added as
 * {@code assets/wowcraft/textures/gui/ability/<id>.png} (and {@code .../aura/<id>.png}) and are picked up automatically.
 */
public final class Icons {
    private Icons() {
    }

    private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();

    static boolean exists(Identifier id) {
        return EXISTS.computeIfAbsent(id, k -> MinecraftClient.getInstance().getResourceManager().getResource(k).isPresent());
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] w = name.trim().split("[\\s'-]+");
        if (w.length == 1) return w[0].substring(0, Math.min(2, w[0].length()));
        return ("" + w[0].charAt(0) + w[1].charAt(0)).toUpperCase();
    }

    public static int abilityColor(Ability a) {
        if (a == null) return 0xFF404040;
        if (a.iconColor != 0) return a.iconColor;
        if (a.wowClass != null) return a.wowClass.color;
        return a.school.color;
    }

    public static void ability(DrawContext ctx, String id, int x, int y, int size) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Identifier tex = new Identifier(WowCraftMod.ID, "textures/gui/ability/" + id + ".png");
        if (exists(tex)) {
            ctx.drawTexture(tex, x, y, 0, 0, size, size, size, size);
            return;
        }
        Ability a = Registry.ability(id);
        int c = abilityColor(a);
        ctx.fill(x, y, x + size, y + size, 0xFF101010);
        ctx.fill(x + 1, y + 1, x + size - 1, y + size - 1, darken(c, 0.55f));
        ctx.fill(x + 1, y + 1, x + size - 1, y + size / 2, darken(c, 0.8f));
        String s = initials(a != null ? ClientState.t(a.name) : id);
        ctx.drawText(tr, s, x + (size - tr.getWidth(s)) / 2, y + (size - 8) / 2, 0xFFFFFFFF, true);
    }

    public static void aura(DrawContext ctx, String id, int x, int y, int size, boolean harmful) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Identifier tex = new Identifier(WowCraftMod.ID, "textures/gui/aura/" + id + ".png");
        AuraDef def = Registry.aura(id);
        int border = harmful ? (def != null && def.dispel != null ? def.dispel.color : 0xFFC02020) : 0xFF20A020;
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
        if (exists(tex)) {
            ctx.drawTexture(tex, x, y, 0, 0, size, size, size, size);
            return;
        }
        Ability same = Registry.ability(id);
        int c = def != null && def.iconColor != 0 ? def.iconColor : same != null ? abilityColor(same) : (harmful ? 0xFF803030 : 0xFF306030);
        ctx.fill(x, y, x + size, y + size, darken(c, 0.6f));
        String s = initials(def != null ? ClientState.t(def.name) : id);
        if (size >= 12) ctx.drawText(tr, s, x + (size - tr.getWidth(s)) / 2, y + (size - 8) / 2, 0xFFFFFFFF, true);
    }

    public static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f), g = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
