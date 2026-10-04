package com.wowcraft.mc.client.render;

import com.wowcraft.mc.WowCraftMod;
import com.wowcraft.mc.entity.WowNpcEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.BlazeEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.Map;

/**
 * Placeholder renderers for WoW NPCs: vanilla biped / spider / blaze models with a tint and scale.
 * To replace a placeholder texture drop a PNG into {@code assets/wowcraft/textures/entity/npc/<template_id>.png};
 * for real models / animations see docs (GeckoLib hook via the synced model and animation ids).
 */
public final class NpcRenderers {
    private NpcRenderers() {
    }

    private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();
    private static final Map<String, Identifier> VANILLA = new HashMap<>();
    private static final Identifier ZOMBIE = new Identifier("minecraft", "textures/entity/zombie/zombie.png");

    static {
        VANILLA.put("zombie", ZOMBIE);
        VANILLA.put("husk", new Identifier("minecraft", "textures/entity/zombie/husk.png"));
        VANILLA.put("drowned", new Identifier("minecraft", "textures/entity/zombie/drowned.png"));
        VANILLA.put("skeleton", new Identifier("minecraft", "textures/entity/skeleton/skeleton.png"));
        VANILLA.put("stray", new Identifier("minecraft", "textures/entity/skeleton/stray.png"));
        VANILLA.put("wither_skeleton", new Identifier("minecraft", "textures/entity/skeleton/wither_skeleton.png"));
        VANILLA.put("steve", new Identifier("minecraft", "textures/entity/player/wide/steve.png"));
        VANILLA.put("alex", new Identifier("minecraft", "textures/entity/player/slim/alex.png"));
        VANILLA.put("spider", new Identifier("minecraft", "textures/entity/spider/spider.png"));
        VANILLA.put("cave_spider", new Identifier("minecraft", "textures/entity/spider/cave_spider.png"));
        VANILLA.put("blaze", new Identifier("minecraft", "textures/entity/blaze.png"));
    }

    static boolean exists(Identifier id) {
        return EXISTS.computeIfAbsent(id, k -> MinecraftClient.getInstance().getResourceManager().getResource(k).isPresent());
    }

    /** Custom texture for the template, then for the texture key, then the vanilla placeholder. */
    static Identifier texture(WowNpcEntity e, Identifier fallback) {
        Identifier custom = new Identifier(WowCraftMod.ID, "textures/entity/npc/" + e.templateId() + ".png");
        if (!e.templateId().isEmpty() && exists(custom)) return custom;
        String key = e.texture();
        Identifier byKey = new Identifier(WowCraftMod.ID, "textures/entity/" + key + ".png");
        if (exists(byKey)) return byKey;
        if (key.startsWith("bot_")) return VANILLA.get("steve");
        return VANILLA.getOrDefault(key, fallback);
    }

    static boolean skeletal(String key) {
        return key.equals("skeleton") || key.equals("stray") || key.equals("wither_skeleton");
    }

    static boolean playerSkin(String key) {
        return key.startsWith("bot_") || key.equals("steve") || key.equals("alex");
    }

    static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }

    /** Lays dead bots on the ground. */
    static void deadPose(WowNpcEntity e, MatrixStack matrices) {
        if (e.isWowDead()) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
            matrices.translate(0.3, -0.2, 0);
        }
    }

    // ------------------------------------------------------------------ tinted models

    public static final class TintedBiped extends BipedEntityModel<WowNpcEntity> {
        private float r = 1, g = 1, b = 1;

        public TintedBiped(ModelPart root) {
            super(root);
        }

        @Override
        public void setAngles(WowNpcEntity e, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            float[] c = rgb(e.tint());
            r = c[0];
            g = c[1];
            b = c[2];
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
            super.render(matrices, vertices, light, overlay, red * r, green * g, blue * b, alpha);
        }
    }

    public static final class TintedSpider extends SpiderEntityModel<WowNpcEntity> {
        private float r = 1, g = 1, b = 1;

        public TintedSpider(ModelPart root) {
            super(root);
        }

        @Override
        public void setAngles(WowNpcEntity e, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            float[] c = rgb(e.tint());
            r = c[0];
            g = c[1];
            b = c[2];
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
            super.render(matrices, vertices, light, overlay, red * r, green * g, blue * b, alpha);
        }
    }

    public static final class TintedBlaze extends BlazeEntityModel<WowNpcEntity> {
        private float r = 1, g = 1, b = 1;

        public TintedBlaze(ModelPart root) {
            super(root);
        }

        @Override
        public void setAngles(WowNpcEntity e, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            float[] c = rgb(e.tint());
            r = c[0];
            g = c[1];
            b = c[2];
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
            super.render(matrices, vertices, light, overlay, red * r, green * g, blue * b, alpha);
        }
    }

    // ------------------------------------------------------------------ renderers

    /** One biped renderer per model shape (zombie 64x64, skeleton 64x32, player skin). */
    static final class Biped extends BipedEntityRenderer<WowNpcEntity, TintedBiped> {
        private final Identifier fallback;

        Biped(EntityRendererFactory.Context ctx, TintedBiped model, Identifier fallback, boolean armor) {
            super(ctx, model, 0.5f);
            this.fallback = fallback;
            if (armor) {
                addFeature(new ArmorFeatureRenderer<>(this, new BipedEntityModel<WowNpcEntity>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                        new BipedEntityModel<WowNpcEntity>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
            }
        }

        @Override
        public Identifier getTexture(WowNpcEntity e) {
            return texture(e, fallback);
        }

        @Override
        public void scale(WowNpcEntity e, MatrixStack matrices, float amount) {
            float s = Math.max(0.2f, e.wowScale());
            matrices.scale(s, s, s);
        }

        @Override
        public void setupTransforms(WowNpcEntity e, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta) {
            super.setupTransforms(e, matrices, animationProgress, bodyYaw, tickDelta);
            deadPose(e, matrices);
        }

        @Override
        public boolean hasLabel(WowNpcEntity e) {
            return false;
        }
    }

    /** Humanoid NPCs: chooses the biped variant matching the texture layout. */
    public static final class Humanoid extends EntityRenderer<WowNpcEntity> {
        private final Biped zombie, skeleton, player;

        public Humanoid(EntityRendererFactory.Context ctx) {
            super(ctx);
            zombie = new Biped(ctx, new TintedBiped(ctx.getPart(EntityModelLayers.ZOMBIE)), ZOMBIE, false);
            skeleton = new Biped(ctx, new TintedBiped(ctx.getPart(EntityModelLayers.SKELETON)), VANILLA.get("skeleton"), false);
            player = new Biped(ctx, new TintedBiped(ctx.getPart(EntityModelLayers.PLAYER)), VANILLA.get("steve"), true);
        }

        private Biped pick(WowNpcEntity e) {
            String key = e.texture();
            Identifier custom = new Identifier(WowCraftMod.ID, "textures/entity/npc/" + e.templateId() + ".png");
            if (!e.templateId().isEmpty() && exists(custom)) return player;
            if (skeletal(key)) return skeleton;
            if (playerSkin(key)) return player;
            return zombie;
        }

        @Override
        public void render(WowNpcEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
            pick(e).render(e, yaw, tickDelta, matrices, consumers, light);
        }

        @Override
        public Identifier getTexture(WowNpcEntity e) {
            return pick(e).getTexture(e);
        }
    }

    /** Four-legged NPCs (spider model placeholder). */
    public static final class Beast extends MobEntityRenderer<WowNpcEntity, TintedSpider> {
        public Beast(EntityRendererFactory.Context ctx) {
            super(ctx, new TintedSpider(ctx.getPart(EntityModelLayers.SPIDER)), 0.8f);
        }

        @Override
        public Identifier getTexture(WowNpcEntity e) {
            return texture(e, VANILLA.get("spider"));
        }

        @Override
        public void scale(WowNpcEntity e, MatrixStack matrices, float amount) {
            float s = Math.max(0.2f, e.wowScale()) * 0.85f;
            matrices.scale(s, s, s);
        }

        @Override
        public void setupTransforms(WowNpcEntity e, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta) {
            super.setupTransforms(e, matrices, animationProgress, bodyYaw, tickDelta);
            deadPose(e, matrices);
        }

        @Override
        public boolean hasLabel(WowNpcEntity e) {
            return false;
        }
    }

    /** Floating elementals and totems (blaze model placeholder). */
    public static final class Elemental extends MobEntityRenderer<WowNpcEntity, TintedBlaze> {
        public Elemental(EntityRendererFactory.Context ctx) {
            super(ctx, new TintedBlaze(ctx.getPart(EntityModelLayers.BLAZE)), 0.5f);
        }

        @Override
        public Identifier getTexture(WowNpcEntity e) {
            return texture(e, VANILLA.get("blaze"));
        }

        @Override
        public void scale(WowNpcEntity e, MatrixStack matrices, float amount) {
            float s = Math.max(0.2f, e.wowScale());
            matrices.scale(s, s, s);
        }

        @Override
        public boolean hasLabel(WowNpcEntity e) {
            return false;
        }
    }
}
