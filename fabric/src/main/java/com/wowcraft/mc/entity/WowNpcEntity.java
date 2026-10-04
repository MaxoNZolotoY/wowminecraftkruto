package com.wowcraft.mc.entity;

import com.wowcraft.core.combat.MoveIntent;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.mc.WowCraftMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Placeholder body for every WoW NPC, pet, totem and bot. The combat engine owns its state; this entity only moves,
 * animates and displays. Visual data (template, texture, tint, scale, model / animation ids) is synced to clients so
 * renderers can be swapped for real models later.
 */
public class WowNpcEntity extends PathAwareEntity {
    public static final TrackedData<String> TEMPLATE = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<String> TEXTURE = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<String> MODEL = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<String> ANIMATION = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<Integer> TINT = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Float> SCALE = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Boolean> DEAD = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Integer> KIND = DataTracker.registerData(WowNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Server only: the combat unit driving this body. */
    public UnitState unit;
    /** Being moved to another world: removal must not delete the unit. */
    public boolean transferring;
    private Vec3d lastNavTarget;
    private int navCooldown;
    private int orphanTicks;
    private boolean stationary;
    /** Template walking speed (movement speed attribute before buffs / slows). */
    private double baseSpeed = 0.3;

    public WowNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(TEMPLATE, "");
        this.dataTracker.startTracking(TEXTURE, "zombie");
        this.dataTracker.startTracking(MODEL, "");
        this.dataTracker.startTracking(ANIMATION, "");
        this.dataTracker.startTracking(TINT, 0xFFFFFFFF);
        this.dataTracker.startTracking(SCALE, 1.0f);
        this.dataTracker.startTracking(DEAD, false);
        this.dataTracker.startTracking(KIND, UnitKind.NPC.ordinal());
    }

    @Override
    protected void initGoals() {
        // no vanilla AI: the WoW brain decides, see mobTick
    }

    /** Applies template visuals (server side). */
    public void setup(NpcTemplate t, UnitKind kind, String displayName) {
        this.dataTracker.set(TEMPLATE, t.id);
        this.dataTracker.set(TEXTURE, t.texture == null ? "zombie" : t.texture);
        this.dataTracker.set(MODEL, t.modelId == null ? "" : t.modelId);
        this.dataTracker.set(ANIMATION, t.animationSet == null ? "" : t.animationSet);
        this.dataTracker.set(TINT, t.tint);
        this.dataTracker.set(SCALE, (float) t.scale);
        this.dataTracker.set(KIND, kind.ordinal());
        this.stationary = t.stationary || kind == UnitKind.TOTEM;
        this.baseSpeed = t.moveSpeed > 0 ? t.moveSpeed : 0.3;
        setCustomName(Text.literal(displayName));
        setCustomNameVisible(false);
        calculateDimensions();
    }

    public void copyVisuals(WowNpcEntity other) {
        for (TrackedData<String> d : java.util.List.of(TEMPLATE, TEXTURE, MODEL, ANIMATION)) this.dataTracker.set(d, other.dataTracker.get(d));
        this.dataTracker.set(TINT, other.dataTracker.get(TINT));
        this.dataTracker.set(SCALE, other.dataTracker.get(SCALE));
        this.dataTracker.set(KIND, other.dataTracker.get(KIND));
        this.dataTracker.set(DEAD, other.dataTracker.get(DEAD));
        this.stationary = other.stationary;
        this.baseSpeed = other.baseSpeed;
        setCustomName(other.getCustomName());
        calculateDimensions();
    }

    public String templateId() {
        return this.dataTracker.get(TEMPLATE);
    }

    public String texture() {
        return this.dataTracker.get(TEXTURE);
    }

    public String modelId() {
        return this.dataTracker.get(MODEL);
    }

    public String animationSet() {
        return this.dataTracker.get(ANIMATION);
    }

    public int tint() {
        return this.dataTracker.get(TINT);
    }

    public float wowScale() {
        return this.dataTracker.get(SCALE);
    }

    public boolean isWowDead() {
        return this.dataTracker.get(DEAD);
    }

    public void setWowDead(boolean dead) {
        this.dataTracker.set(DEAD, dead);
    }

    public UnitKind unitKind() {
        int k = this.dataTracker.get(KIND);
        UnitKind[] all = UnitKind.values();
        return k >= 0 && k < all.length ? all[k] : UnitKind.NPC;
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return super.getDimensions(pose).scaled(Math.max(0.2f, wowScale()));
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (SCALE.equals(data)) calculateDimensions();
    }

    // ------------------------------------------------------------------ server behaviour

    @Override
    protected void mobTick() {
        super.mobTick();
        if (unit == null) {
            if (++orphanTicks > 40) discard();
            return;
        }
        drive();
    }

    private void drive() {
        UnitState u = unit;
        if (u.isDead() || isWowDead()) {
            getNavigation().stop();
            return;
        }
        double base = stationary ? 0.0 : baseSpeed * Math.max(0.1, 1.0 + u.stats().speedPct / 100.0) * (u.canMove() ? 1.0 : 0.0);
        var speedAttr = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && Math.abs(speedAttr.getBaseValue() - base) > 1e-3) speedAttr.setBaseValue(base);
        if (base <= 0) {
            getNavigation().stop();
            faceTarget();
            return;
        }
        MoveIntent m = u.move;
        if (m == null) m = MoveIntent.STOP;
        double speed = m.speedMult() > 0 ? m.speedMult() : 1.0;
        navCooldown--;
        switch (m.kind()) {
            case CHASE, FOLLOW -> {
                Entity target = m.target() != null && m.target().platform instanceof Entity e ? e : null;
                if (target == null || target.getWorld() != getWorld()) {
                    getNavigation().stop();
                    break;
                }
                double reach = m.range() + (getWidth() + target.getWidth()) / 2.0;
                if (squaredDistanceTo(target) <= reach * reach) {
                    getNavigation().stop();
                    getLookControl().lookAt(target, 30f, 30f);
                } else if (navCooldown <= 0 || getNavigation().isIdle()) {
                    getNavigation().startMovingTo(target, speed);
                    navCooldown = 8;
                }
            }
            case MOVE_TO -> moveTo(new Vec3d(m.point().x(), m.point().y(), m.point().z()), speed);
            case FLEE -> {
                if (m.point() == null) break;
                Vec3d from = new Vec3d(m.point().x(), m.point().y(), m.point().z());
                Vec3d away = getPos().subtract(from);
                if (away.lengthSquared() < 1e-4) away = new Vec3d(1, 0, 0);
                moveTo(getPos().add(away.normalize().multiply(Math.max(3, m.range()))), speed);
            }
            default -> {
                getNavigation().stop();
                faceTarget();
            }
        }
    }

    private void moveTo(Vec3d p, double speed) {
        if (p.squaredDistanceTo(getX(), p.y, getZ()) < 0.36) {
            getNavigation().stop();
            lastNavTarget = null;
            return;
        }
        if (lastNavTarget == null || lastNavTarget.squaredDistanceTo(p) > 0.5 || navCooldown <= 0 || getNavigation().isIdle()) {
            getNavigation().startMovingTo(p.x, p.y, p.z, speed);
            lastNavTarget = p;
            navCooldown = 10;
        }
    }

    private void faceTarget() {
        UnitState t = unit.target();
        if (t != null && t.platform instanceof Entity e && e.getWorld() == getWorld()) getLookControl().lookAt(e, 30f, 30f);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (WowCraftMod.bridge() != null) WowCraftMod.bridge().onNpcDamaged(this, source, amount);
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!getWorld().isClient && !transferring && unit != null && WowCraftMod.bridge() != null) WowCraftMod.bridge().onNpcRemoved(this);
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean shouldDropXp() {
        return false;
    }

    @Override
    public void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
    }

    @Override
    public boolean isPushable() {
        return !stationary && !isWowDead();
    }

    @Override
    public boolean isCollidable() {
        return false;
    }
}
