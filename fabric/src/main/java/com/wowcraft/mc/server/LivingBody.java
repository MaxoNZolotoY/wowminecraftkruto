package com.wowcraft.mc.server;

import com.wowcraft.core.combat.Body;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.util.Vec3;
import com.wowcraft.mc.entity.WowNpcEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/** Combat body backed by a Minecraft living entity (player, WoW NPC or vanilla mob). */
public final class LivingBody implements Body {
    public LivingEntity entity;
    private final McPlatform platform;
    private double lastX, lastZ;
    private boolean moving;

    public LivingBody(McPlatform platform, LivingEntity entity) {
        this.platform = platform;
        this.entity = entity;
        this.lastX = entity.getX();
        this.lastZ = entity.getZ();
    }

    /** Called once per server tick before the game tick. */
    void updateMoving() {
        double dx = entity.getX() - lastX, dz = entity.getZ() - lastZ;
        moving = dx * dx + dz * dz > 0.0016;
        lastX = entity.getX();
        lastZ = entity.getZ();
    }

    @Override
    public Vec3 position() {
        return new Vec3(entity.getX(), entity.getY(), entity.getZ());
    }

    @Override
    public float yaw() {
        return entity instanceof PlayerEntity ? entity.getHeadYaw() : entity.getYaw();
    }

    @Override
    public double width() {
        return entity.getWidth();
    }

    @Override
    public double height() {
        return entity.getHeight();
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    @Override
    public boolean isRemoved() {
        if (entity instanceof WowNpcEntity n && n.transferring) return false;
        return entity.isRemoved() && !(entity instanceof ServerPlayerEntity);
    }

    @Override
    public String worldKey() {
        return entity.getWorld().getRegistryKey().getValue().toString();
    }

    @Override
    public void applyHealth(UnitState unit, double oldHealth, HitResult cause) {
        platform.mirrorHealth(this, unit, cause);
    }

    @Override
    public void kill(UnitState unit, UnitState killer) {
        platform.onBodyKilled(this, unit, killer);
    }

    @Override
    public void teleport(Vec3 pos) {
        if (entity instanceof ServerPlayerEntity sp) {
            sp.networkHandler.requestTeleport(pos.x(), pos.y(), pos.z(), sp.getYaw(), sp.getPitch());
        } else {
            entity.requestTeleport(pos.x(), pos.y(), pos.z());
        }
        lastX = pos.x();
        lastZ = pos.z();
    }

    @Override
    public void setVelocity(Vec3 v) {
        entity.setVelocity(v.x(), v.y(), v.z());
        entity.velocityModified = true;
    }

    @Override
    public void lookAt(Vec3 point) {
        if (entity instanceof PlayerEntity) return;
        Vec3 p = position();
        if (p.horizontalDistance(point) < 0.05) return;
        float yaw = (float) p.yawTo(point);
        entity.setYaw(yaw);
        entity.setHeadYaw(yaw);
        entity.setBodyYaw(yaw);
    }

    @Override
    public void refreshMovement(UnitState unit) {
        if (entity instanceof ServerPlayerEntity sp) platform.updatePlayerSpeed(sp, unit);
    }

    @Override
    public void setStealthed(boolean stealthed) {
        entity.setInvisible(stealthed);
    }

    @Override
    public void despawn() {
        if (!(entity instanceof PlayerEntity)) {
            if (entity instanceof WowNpcEntity n) n.unit = null;
            entity.discard();
        }
    }

    @Override
    public void playAnimation(String key) {
        if (key == null) return;
        if (key.contains("attack") || key.contains("strike") || key.contains("melee") || key.contains("swing")) entity.swingHand(Hand.MAIN_HAND, true);
    }
}
