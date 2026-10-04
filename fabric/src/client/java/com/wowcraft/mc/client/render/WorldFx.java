package com.wowcraft.mc.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.util.Mth;
import com.wowcraft.mc.client.ClientState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** In-world rendering: boss telegraphs, ground effects, nameplates with health bars, floating combat text, spell particles. */
public final class WorldFx {
    private WorldFx() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldFx::render);
    }

    private static Vec3d cam;
    private static BufferBuilder buf;
    private static int quads;

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        cam = ctx.camera().getPos();
        MatrixStack ms = ctx.matrixStack();
        float td = ctx.tickDelta();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        quads = 0;
        Matrix4f m = ms.peek().getPositionMatrix();
        for (ClientState.Timed<S2C.Telegraph> t : ClientState.telegraphs.values()) telegraph(mc, m, t, td);
        for (ClientState.Timed<S2C.Area> a : ClientState.areas.values()) area(mc, m, a, td);
        nameplateBars(mc, ms, td);
        if (quads > 0) BufferRenderer.drawWithGlobalProgram(buf.end());
        else buf.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        nameplateText(mc, ms, imm, td);
        combatText(mc, ms, imm, td);
        imm.draw();
    }

    // ------------------------------------------------------------------ geometry

    private static void v(Matrix4f m, double x, double y, double z, int argb) {
        buf.vertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).next();
    }

    private static int alpha(int rgb, float a) {
        return (Math.max(0, Math.min(255, (int) (a * 255))) << 24) | (rgb & 0xFFFFFF);
    }

    private static void disc(Matrix4f m, double x, double y, double z, double r, int color) {
        int seg = Math.max(16, (int) (r * 6));
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            v(m, x, y, z, color);
            v(m, x + Math.cos(a0) * r, y, z + Math.sin(a0) * r, color);
            v(m, x + Math.cos(a1) * r, y, z + Math.sin(a1) * r, color);
            v(m, x, y, z, color);
            quads++;
        }
    }

    private static void ring(Matrix4f m, double x, double y, double z, double r0, double r1, int color) {
        int seg = Math.max(16, (int) (r1 * 6));
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            v(m, x + Math.cos(a0) * r0, y, z + Math.sin(a0) * r0, color);
            v(m, x + Math.cos(a0) * r1, y, z + Math.sin(a0) * r1, color);
            v(m, x + Math.cos(a1) * r1, y, z + Math.sin(a1) * r1, color);
            v(m, x + Math.cos(a1) * r0, y, z + Math.sin(a1) * r0, color);
            quads++;
        }
    }

    /** Minecraft yaw -> direction (0 = +Z). */
    private static double dx(double yawDeg) {
        return -Math.sin(Math.toRadians(yawDeg));
    }

    private static double dz(double yawDeg) {
        return Math.cos(Math.toRadians(yawDeg));
    }

    private static void sector(Matrix4f m, double x, double y, double z, float yaw, double angle, double range, int color) {
        int seg = Math.max(8, (int) (angle / 6));
        for (int i = 0; i < seg; i++) {
            double a0 = yaw - angle / 2 + angle * i / seg, a1 = yaw - angle / 2 + angle * (i + 1) / seg;
            v(m, x, y, z, color);
            v(m, x + dx(a0) * range, y, z + dz(a0) * range, color);
            v(m, x + dx(a1) * range, y, z + dz(a1) * range, color);
            v(m, x, y, z, color);
            quads++;
        }
    }

    private static void rect(Matrix4f m, double x, double y, double z, float yaw, double length, double width, int color) {
        double fx = dx(yaw), fz = dz(yaw), sx = -fz, sz = fx;
        double hw = width / 2;
        v(m, x + sx * hw, y, z + sz * hw, color);
        v(m, x - sx * hw, y, z - sz * hw, color);
        v(m, x - sx * hw + fx * length, y, z - sz * hw + fz * length, color);
        v(m, x + sx * hw + fx * length, y, z + sz * hw + fz * length, color);
        quads++;
    }

    private static Vec3d follow(MinecraftClient mc, int id, float td) {
        if (id < 0) return null;
        Entity e = mc.world.getEntityById(id);
        return e == null ? null : e.getLerpedPos(td);
    }

    private static void telegraph(MinecraftClient mc, Matrix4f m, ClientState.Timed<S2C.Telegraph> timed, float td) {
        S2C.Telegraph t = timed.value;
        double x = t.x, y = t.y, z = t.z;
        Vec3d f = follow(mc, t.followId, td);
        if (f != null && timed.age() < t.duration * 0.85f) {
            x = f.x;
            y = f.y;
            z = f.z;
        }
        y += 0.03;
        float progress = t.duration <= 0 ? 1 : Math.min(1f, timed.age() / t.duration);
        int rgb = t.color & 0xFFFFFF;
        int base = alpha(rgb, t.soak ? 0.18f : 0.22f), fill = alpha(rgb, 0.38f), edge = alpha(rgb, 0.85f);
        switch (t.shape) {
            case "CIRCLE" -> {
                disc(m, x, y, z, t.radius, base);
                disc(m, x, y + 0.005, z, t.radius * progress, fill);
                ring(m, x, y + 0.01, z, Math.max(0, t.radius - 0.12), t.radius, edge);
            }
            case "RING" -> {
                ring(m, x, y, z, t.inner, t.radius, base);
                ring(m, x, y + 0.005, z, t.inner, t.inner + (t.radius - t.inner) * progress, fill);
                ring(m, x, y + 0.01, z, Math.max(0, t.inner - 0.1), t.inner, edge);
                ring(m, x, y + 0.01, z, t.radius - 0.1, t.radius, edge);
            }
            case "CONE" -> {
                sector(m, x, y, z, t.yaw, t.angle, t.length > 0 ? t.length : t.radius, base);
                sector(m, x, y + 0.005, z, t.yaw, t.angle, (t.length > 0 ? t.length : t.radius) * progress, fill);
            }
            case "LINE" -> {
                rect(m, x, y, z, t.yaw, t.length, t.width, base);
                rect(m, x, y + 0.005, z, t.yaw, t.length * progress, t.width, fill);
            }
            default -> {
            }
        }
    }

    private static void area(MinecraftClient mc, Matrix4f m, ClientState.Timed<S2C.Area> timed, float td) {
        S2C.Area a = timed.value;
        double x = a.x, y = a.y + 0.02, z = a.z;
        Vec3d f = follow(mc, a.followId, td);
        if (f != null) {
            x = f.x;
            y = f.y + 0.02;
            z = f.z;
        }
        int rgb = a.color & 0xFFFFFF;
        disc(m, x, y, z, a.radius, alpha(rgb, a.hostile ? 0.2f : 0.12f));
        ring(m, x, y + 0.01, z, Math.max(0, a.radius - 0.1), a.radius, alpha(rgb, 0.6f));
    }

    // ------------------------------------------------------------------ nameplates

    private static boolean showPlate(MinecraftClient mc, S2C.Unit u) {
        return u.entityId != mc.player.getId() && !(u.dead && !u.boss);
    }

    private static void nameplateBars(MinecraftClient mc, MatrixStack ms, float td) {
        for (S2C.Unit u : ClientState.units.values()) {
            if (!showPlate(mc, u)) continue;
            Entity e = mc.world.getEntityById(u.entityId);
            if (e == null || e.squaredDistanceTo(mc.player) > 40 * 40 || e.isInvisibleTo(mc.player)) continue;
            Vec3d p = e.getLerpedPos(td);
            ms.push();
            ms.translate(p.x - cam.x, p.y + e.getHeight() + 0.45 - cam.y, p.z - cam.z);
            ms.multiply(mc.gameRenderer.getCamera().getRotation());
            ms.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f m = ms.peek().getPositionMatrix();
            float w = u.boss ? 60 : 40, h = 4;
            float frac = u.maxHealth <= 0 ? 0 : (float) (u.health / u.maxHealth);
            int hpColor = u.hostile ? 0xFFD03030 : u.wowClass != null ? (0xFF000000 | u.color) : 0xFF30C030;
            quad2d(m, -w / 2 - 1, -1, w / 2 + 1, h + 1, 0xC0000000);
            quad2d(m, -w / 2, 0, -w / 2 + w * frac, h, hpColor);
            if (u.absorb > 0 && u.maxHealth > 0) {
                float af = (float) Math.min(1 - frac, u.absorb / u.maxHealth);
                quad2d(m, -w / 2 + w * frac, 0, -w / 2 + w * (frac + af), h, 0xC0E0E0E0);
            }
            if (u.cast != null) {
                float cp = u.cast.total <= 0 ? 0 : Math.min(1, u.cast.progress);
                quad2d(m, -w / 2 - 1, h + 2, w / 2 + 1, h + 6, 0xC0000000);
                quad2d(m, -w / 2, h + 3, -w / 2 + w * cp, h + 5, u.cast.interruptible ? 0xFFE0B020 : 0xFF909090);
            }
            ms.pop();
        }
    }

    private static void quad2d(Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        buf.vertex(m, x0, y0, 0).color(r, g, b, a).next();
        buf.vertex(m, x0, y1, 0).color(r, g, b, a).next();
        buf.vertex(m, x1, y1, 0).color(r, g, b, a).next();
        buf.vertex(m, x1, y0, 0).color(r, g, b, a).next();
        quads++;
    }

    private static void nameplateText(MinecraftClient mc, MatrixStack ms, VertexConsumerProvider imm, float td) {
        TextRenderer tr = mc.textRenderer;
        S2C.Unit target = ClientState.target();
        for (S2C.Unit u : ClientState.units.values()) {
            if (!showPlate(mc, u)) continue;
            Entity e = mc.world.getEntityById(u.entityId);
            if (e == null || e.squaredDistanceTo(mc.player) > 40 * 40 || e.isInvisibleTo(mc.player)) continue;
            Vec3d p = e.getLerpedPos(td);
            ms.push();
            ms.translate(p.x - cam.x, p.y + e.getHeight() + 0.45 - cam.y, p.z - cam.z);
            ms.multiply(mc.gameRenderer.getCamera().getRotation());
            ms.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f m = ms.peek().getPositionMatrix();
            String name = (u.level > 0 && u.wowClass == null ? u.level + " " : "") + u.name;
            if (u.cast != null && u.cast.ability != null) {
                var a = com.wowcraft.core.content.Registry.ability(u.cast.ability);
                String cn = a != null ? ClientState.t(a.name) : u.cast.ability;
                tr.draw(cn, -tr.getWidth(cn) / 2f, 11, 0xFFFFFFFF, false, m, imm, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            }
            int color = u == target ? 0xFFFFFF60 : (u.hostile ? 0xFFFF7070 : 0xFFFFFFFF);
            tr.draw(name, -tr.getWidth(name) / 2f, -10, color, false, m, imm, TextRenderer.TextLayerType.NORMAL, 0x40000000, 0xF000F0);
            ms.pop();
        }
    }

    // ------------------------------------------------------------------ combat text

    private static void combatText(MinecraftClient mc, MatrixStack ms, VertexConsumerProvider imm, float td) {
        TextRenderer tr = mc.textRenderer;
        int me = mc.player.getId();
        for (ClientState.FloatingText f : ClientState.combatText) {
            S2C.CombatText c = f.e;
            float age = f.age();
            if (age > 1.5f) continue;
            double x = c.x + f.jitterX, y = c.y + 0.3 + age * 1.2, z = c.z + f.jitterZ;
            String text;
            int color;
            if (c.immune) {
                text = ClientState.t("Immune", "Невосприимчив");
                color = 0xFFC0C0C0;
            } else if (c.amount <= 0 && c.absorbed > 0) {
                text = ClientState.t("Absorb ", "Поглощено ") + Mth.shortNumber(c.absorbed);
                color = 0xFFC0C0C0;
            } else {
                text = (c.heal ? "+" : "") + Mth.shortNumber(c.amount) + (c.crit ? "!" : "");
                if (c.heal) color = 0xFF40FF40;
                else if (c.targetId == me) color = 0xFFFF4040;
                else color = c.periodic ? 0xFFE0E060 : 0xFFFFFF40;
            }
            float alpha = age < 1.0f ? 1f : 1f - (age - 1.0f) / 0.5f;
            int a = Math.max(8, (int) (alpha * 255));
            color = (a << 24) | (color & 0xFFFFFF);
            ms.push();
            ms.translate(x - cam.x, y - cam.y, z - cam.z);
            ms.multiply(mc.gameRenderer.getCamera().getRotation());
            float s = c.crit ? 0.04f : 0.028f;
            ms.scale(-s, -s, s);
            tr.draw(text, -tr.getWidth(text) / 2f, 0, color, true, ms.peek().getPositionMatrix(), imm, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            ms.pop();
        }
    }

    // ------------------------------------------------------------------ vfx

    private static ParticleEffect particleFor(String key) {
        String k = key == null ? "" : key.toLowerCase();
        if (k.contains("heal") || k.contains("renew") || k.contains("mend")) return ParticleTypes.HAPPY_VILLAGER;
        if (k.contains("holy") || k.contains("light") || k.contains("divine") || k.contains("sun")) return ParticleTypes.END_ROD;
        if (k.contains("fire") || k.contains("flame") || k.contains("burn") || k.contains("meteor") || k.contains("pyro") || k.contains("lava")) return ParticleTypes.FLAME;
        if (k.contains("frost") || k.contains("ice") || k.contains("snow") || k.contains("blizzard") || k.contains("cold")) return ParticleTypes.SNOWFLAKE;
        if (k.contains("arcane") || k.contains("star") || k.contains("moon")) return ParticleTypes.ENCHANT;
        if (k.contains("shadow") || k.contains("void") || k.contains("dark")) return ParticleTypes.SQUID_INK;
        if (k.contains("fel") || k.contains("chaos") || k.contains("soul")) return ParticleTypes.SOUL_FIRE_FLAME;
        if (k.contains("nature") || k.contains("poison") || k.contains("plague") || k.contains("rot")) return ParticleTypes.SNEEZE;
        if (k.contains("lightning") || k.contains("storm") || k.contains("spark") || k.contains("shock")) return ParticleTypes.ELECTRIC_SPARK;
        if (k.contains("explos")) return ParticleTypes.EXPLOSION;
        if (k.contains("resurrect")) return ParticleTypes.TOTEM_OF_UNDYING;
        if (k.contains("charge") || k.contains("leap") || k.contains("dash") || k.contains("blink")) return ParticleTypes.CLOUD;
        if (k.contains("cleave") || k.contains("sweep") || k.contains("whirl")) return ParticleTypes.SWEEP_ATTACK;
        if (k.contains("strike") || k.contains("slash") || k.contains("attack") || k.contains("bite")) return ParticleTypes.CRIT;
        return ParticleTypes.ENCHANTED_HIT;
    }

    public static void vfx(S2C.Vfx v) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        ParticleEffect type = particleFor(v.key);
        Entity src = v.sourceId >= 0 ? mc.world.getEntityById(v.sourceId) : null;
        Entity tgt = v.targetId >= 0 ? mc.world.getEntityById(v.targetId) : null;
        Vec3d to = v.hasPoint ? new Vec3d(v.x, v.y, v.z) : tgt != null ? tgt.getPos().add(0, tgt.getHeight() * 0.6, 0) : null;
        if (src != null && to != null && src != tgt && src.getPos().squaredDistanceTo(to) > 9) {
            Vec3d from = src.getPos().add(0, src.getHeight() * 0.7, 0);
            int n = (int) Math.min(30, from.distanceTo(to) * 2);
            for (int i = 0; i <= n; i++) {
                Vec3d p = from.lerp(to, (double) i / n);
                mc.world.addParticle(type, p.x, p.y, p.z, 0, 0.01, 0);
            }
        }
        Vec3d at = to != null ? to : src != null ? src.getPos().add(0, src.getHeight() * 0.6, 0) : null;
        if (at == null) return;
        int burst = v.param > 3 ? 24 : 10;
        double spread = Math.min(4, Math.max(0.4, v.param > 3 ? v.param * 0.5 : 0.6));
        for (int i = 0; i < burst; i++) {
            double ox = (Math.random() - 0.5) * spread, oy = Math.random() * 0.8, oz = (Math.random() - 0.5) * spread;
            mc.world.addParticle(type, at.x + ox, at.y + oy, at.z + oz, ox * 0.05, 0.03, oz * 0.05);
        }
    }
}
