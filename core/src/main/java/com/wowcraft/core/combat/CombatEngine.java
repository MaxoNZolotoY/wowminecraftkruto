package com.wowcraft.core.combat;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.aura.Trigger;
import com.wowcraft.core.aura.TriggerType;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.mod.ModContext;
import com.wowcraft.core.mod.ModType;
import com.wowcraft.core.mod.ModifierSet;
import com.wowcraft.core.resource.ResourcePool;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.MasteryKind;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.CastType;
import com.wowcraft.core.spell.Cond;
import com.wowcraft.core.spell.Cost;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;
import com.wowcraft.core.stat.Ratings;
import com.wowcraft.core.util.Mth;
import com.wowcraft.core.util.Rng;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The heart of the mod: a WoW-style combat simulation independent of Minecraft.
 * The platform registers units (with a {@link Body}), calls {@link #tick(double)} every server tick and
 * forwards player input to {@link #cast}.
 */
public final class CombatEngine {
    public static final double SPELL_QUEUE_WINDOW = 0.4;
    public static final double OOC_HEALTH_REGEN = 0.05;
    private static final double TANK_THREAT = 5.0;

    private double now;
    private final Rng rng;
    private final Map<Integer, UnitState> units = new LinkedHashMap<>();
    private final List<CombatListener> listeners = new ArrayList<>();
    private final List<GroundArea> areas = new ArrayList<>();
    private final PriorityQueue<Scheduled> scheduled = new PriorityQueue<>();
    private WorldAccess world = WorldAccess.NONE;
    private Hostility hostility = Hostility.TEAMS;
    private int procDepth;
    private int nextLocalId = -1;
    /** Optional game-rule check before casting (battle res charges, arena preparation...). */
    private java.util.function.BiFunction<UnitState, Ability, CastResult> castFilter;
    private long scheduleSeq;

    private record Scheduled(double at, long seq, Runnable task) implements Comparable<Scheduled> {
        @Override
        public int compareTo(Scheduled o) {
            int c = Double.compare(at, o.at);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }

    public CombatEngine(long seed) {
        this.rng = new Rng(seed);
    }

    // ================================================================== setup

    public void setWorld(WorldAccess world) {
        this.world = world;
    }

    public WorldAccess world() {
        return world;
    }

    public void setCastFilter(java.util.function.BiFunction<UnitState, Ability, CastResult> filter) {
        this.castFilter = filter;
    }

    public void setHostility(Hostility h) {
        this.hostility = h;
    }

    public Hostility hostility() {
        return hostility;
    }

    public void addListener(CombatListener l) {
        listeners.add(l);
    }

    public void removeListener(CombatListener l) {
        listeners.remove(l);
    }

    public double now() {
        return now;
    }

    public Rng rng() {
        return rng;
    }

    public void schedule(double delay, Runnable task) {
        scheduled.add(new Scheduled(now + Math.max(0, delay), scheduleSeq++, task));
    }

    // ================================================================== units

    public UnitState register(int id, UUID uuid, String name, UnitKind kind, Body body) {
        UnitState u = new UnitState(id, uuid, name, kind);
        u.body = body;
        units.put(id, u);
        return u;
    }

    /** Registers a unit without a platform id (tests, virtual units). */
    public UnitState registerLocal(String name, UnitKind kind, Body body) {
        int id = nextLocalId--;
        return register(id, UUID.randomUUID(), name, kind, body);
    }

    public void remove(UnitState u) {
        if (units.remove(u.id) == null) return;
        u.cast = null;
        for (UnitState o : units.values()) {
            if (o.threat != null) o.threat.remove(u);
            o.engaged.remove(u);
            if (o.target == u) o.target = null;
            o.pets.remove(u);
        }
        if (u.owner != null) u.owner.pets.remove(u);
        for (CombatListener l : listeners) l.onUnitRemoved(u);
    }

    public UnitState unit(int id) {
        return units.get(id);
    }

    public Collection<UnitState> units() {
        return units.values();
    }

    public List<UnitState> unitsSnapshot() {
        return new ArrayList<>(units.values());
    }

    /** Sets up a unit's resources from its spec. */
    public void configureResources(UnitState u, Set<ResourceType> types) {
        u.resources.configure(types);
        u.markDirty(UnitState.DIRTY_RESOURCES);
    }

    public void setHealth(UnitState u, double health) {
        double old = u.health;
        u.health = Mth.clamp(health, 0, u.maxHealth());
        u.markDirty(UnitState.DIRTY_HEALTH);
        if (u.body != null && Math.abs(old - u.health) > 1e-6) u.body.applyHealth(u, old, null);
    }

    public void setMaxHealthDirect(UnitState u, double max, boolean fill) {
        u.setBaseMaxHealth(max);
        u.stats();
        if (fill) setHealth(u, u.maxHealth());
    }

    /** Restores a unit to full health / resources and clears combat state (evade, instance reset). */
    public void resetUnit(UnitState u, boolean clearAuras) {
        u.dead = false;
        u.ghost = false;
        cancelCast(u, false);
        if (clearAuras) {
            for (AuraInstance a : new ArrayList<>(u.auras.all())) if (!a.def.passive) removeAura(a, false);
        }
        u.stats();
        setHealth(u, u.maxHealth());
        for (ResourceType t : u.resources.active()) {
            if (t == ResourceType.RUNES) continue;
            u.resources.set(t, t.startsEmpty() ? 0 : (t.oocTarget > 0 ? t.oocTarget : u.resources.max(t)));
        }
        u.resources.add(ResourceType.RUNES, ResourcePool.RUNE_COUNT);
        if (u.threat != null) u.threat.clear();
        u.engaged.clear();
        u.lastCombatAt = -1e9;
        u.autoAttack = false;
        u.markDirty(0xFFFF);
    }

    // ================================================================== relations & geometry

    public boolean isHostile(UnitState a, UnitState b) {
        return a != null && b != null && hostility.hostile(a, b);
    }

    public boolean isFriendly(UnitState a, UnitState b) {
        return a != null && b != null && (a == b || hostility.friendly(a, b));
    }

    /** Whether {@code a} may target {@code b} at all (stealth, untargetable). */
    public boolean canTarget(UnitState a, UnitState b) {
        if (b == null || b.isDead()) return false;
        if (!sameWorld(a, b)) return false;
        if (isHostile(a, b)) {
            if (b.mods().has(ModType.UNTARGETABLE)) return false;
            if (b.auras.isStealthed() && distance(a, b) > 2.5) return false;
        }
        return true;
    }

    public boolean sameWorld(UnitState a, UnitState b) {
        return a.body == null || b.body == null || a.worldKey().equals(b.worldKey());
    }

    /** Edge-to-edge distance (accounts for unit size, like WoW combat reach). */
    public double distance(UnitState a, UnitState b) {
        Vec3 pa = a.position(), pb = b.position();
        double horiz = pa.horizontalDistance(pb) - (a.width() + b.width()) / 2.0;
        double vert = 0;
        double aTop = pa.y() + a.height(), bTop = pb.y() + b.height();
        if (pa.y() > bTop) vert = pa.y() - bTop;
        else if (pb.y() > aTop) vert = pb.y() - aTop;
        horiz = Math.max(0, horiz);
        return Math.sqrt(horiz * horiz + vert * vert);
    }

    public double distanceTo(UnitState a, Vec3 p) {
        return Math.max(0, a.position().distance(p) - a.width() / 2.0);
    }

    public boolean isFacing(UnitState a, UnitState b) {
        if (a == b) return true;
        if (a.position().horizontalDistance(b.position()) < 1.2) return true;
        double diff = Math.abs(Mth.angleDiff(a.position().yawTo(b.position()), a.yaw()));
        return diff <= 95;
    }

    public boolean isBehind(UnitState attacker, UnitState target) {
        double diff = Math.abs(Mth.angleDiff(target.position().yawTo(attacker.position()), target.yaw()));
        return diff >= 100;
    }

    public boolean lineOfSight(UnitState a, UnitState b) {
        if (a == b) return true;
        return world.lineOfSight(a.worldKey(), a.eye(), b.center()) || world.lineOfSight(a.worldKey(), a.eye(), b.eye());
    }

    public List<UnitState> enemiesAround(UnitState caster, Vec3 center, double radius) {
        List<UnitState> out = new ArrayList<>();
        String wk = caster.worldKey();
        for (UnitState u : units.values()) {
            if (u.isDead() || u == caster) continue;
            if (caster.body != null && u.body != null && !wk.equals(u.worldKey())) continue;
            if (!isHostile(caster, u) || !canTarget(caster, u)) continue;
            if (u.kind == UnitKind.TOTEM && !u.tags.containsKey("targetable")) continue;
            if (u.position().distance(center) - u.width() / 2.0 <= radius) out.add(u);
        }
        return out;
    }

    public List<UnitState> alliesAround(UnitState caster, Vec3 center, double radius) {
        List<UnitState> out = new ArrayList<>();
        String wk = caster.worldKey();
        for (UnitState u : units.values()) {
            if (u.isDead()) continue;
            if (caster.body != null && u.body != null && !wk.equals(u.worldKey())) continue;
            if (u.kind == UnitKind.TOTEM || u.kind == UnitKind.VANILLA) continue;
            if (!isFriendly(caster, u)) continue;
            if (u.position().distance(center) - u.width() / 2.0 <= radius) out.add(u);
        }
        return out;
    }

    /** Party / raid members (or same-team allies for NPCs) near the caster. */
    public List<UnitState> groupMembersAround(UnitState caster, double radius) {
        UnitState m = caster.master();
        if (m.groupId == null) {
            List<UnitState> out = new ArrayList<>();
            out.add(caster);
            if (!caster.isPlayerLike()) return alliesAround(caster, caster.position(), radius);
            for (UnitState p : caster.livingPets()) if (distance(caster, p) <= radius) out.add(p);
            return out;
        }
        List<UnitState> out = new ArrayList<>();
        for (UnitState u : units.values()) {
            if (u.isDead() || u.kind == UnitKind.TOTEM) continue;
            UnitState um = u.master();
            if (!m.groupId.equals(um.groupId)) continue;
            if (!sameWorld(caster, u)) continue;
            if (distance(caster, u) <= radius) out.add(u);
        }
        return out;
    }

    public double radius(EffectContext ctx, double base) {
        return base * ctx.caster.mods().product(ModType.RADIUS_PCT, ModContext.of(ctx.ability));
    }

    // ================================================================== tick

    public void tick(double dt) {
        now += dt;
        while (!scheduled.isEmpty() && scheduled.peek().at <= now) {
            Scheduled s = scheduled.poll();
            try {
                s.task.run();
            } catch (RuntimeException e) {
                // a broken scripted effect must never take the server down
                e.printStackTrace();
            }
        }
        for (UnitState u : unitsSnapshot()) {
            if (!units.containsKey(u.id)) continue;
            tickUnit(u, dt);
        }
        tickAreas();
    }

    private void tickUnit(UnitState u, double dt) {
        if (u.body != null && u.body.isRemoved()) {
            remove(u);
            return;
        }
        if (now >= u.despawnAt) {
            if (u.body != null) u.body.despawn();
            remove(u);
            return;
        }
        if (u.statsDirty || u.modsDirty) u.recomputeStats();

        boolean moving = u.body != null && u.body.isMoving();
        if (moving) u.lastMovedAt = now;

        // ---- casting ----
        if (u.cast != null) tickCast(u, moving);
        if (u.cast == null && u.queued != null) {
            UnitState.QueuedCast q = u.queued;
            u.queued = null;
            if (now - q.requestedAt() <= 1.0) {
                CastResult r = cast(u, q.abilityId(), q.target(), q.point());
                if (r == CastResult.QUEUED && u.queued != null && u.queued.abilityId().equals(q.abilityId())) {
                    // keep the original request time so it can't loop forever
                    u.queued = q;
                }
            }
        }

        // ---- auras ----
        tickAuras(u);

        if (u.isDead()) return;

        // ---- combat state ----
        updateCombatState(u);

        // ---- resources ----
        tickResources(u, dt);

        // ---- out of combat regeneration ----
        if (!u.inCombat && u.isPlayerLike() && now - u.lastCombatAt > 2.0 && u.health < u.maxHealth) {
            double old = u.health;
            u.health = Math.min(u.maxHealth, u.health + u.maxHealth * OOC_HEALTH_REGEN * dt);
            u.markDirty(UnitState.DIRTY_HEALTH);
            if (u.body != null) u.body.applyHealth(u, old, null);
        }

        // ---- auto attack ----
        tickAutoAttack(u);
    }

    private void updateCombatState(UnitState u) {
        boolean was = u.inCombat;
        boolean in;
        if (u.threat != null && u.kind != UnitKind.VANILLA) {
            in = !u.threat.isEmpty() || now - u.lastCombatAt < 1.0;
        } else {
            in = now - u.lastCombatAt < Formulas.COMBAT_TIMEOUT;
            if (!in) {
                for (UnitState e : u.engaged) {
                    if (e.isAlive() && e.threat != null && e.threat.raw().containsKey(u)) {
                        in = true;
                        break;
                    }
                }
            }
        }
        if (in != was) {
            u.inCombat = in;
            if (!in) {
                u.engaged.removeIf(e -> !e.isAlive() || e.threat == null || !e.threat.raw().containsKey(u));
                for (AuraInstance a : new ArrayList<>(u.auras.all())) if (a.def.removeOutOfCombat) removeAura(a, false);
                if (u.isPlayerLike()) u.autoAttack = false;
            }
            u.markDirty(UnitState.DIRTY_HEALTH);
            for (CombatListener l : listeners) l.onCombatChanged(u, in);
        }
    }

    private void tickResources(UnitState u, double dt) {
        ResourcePool rp = u.resources;
        if (rp.active().isEmpty()) return;
        DerivedStats d = u.stats();
        ModifierSet m = u.mods();
        for (ResourceType t : rp.active()) {
            if (t == ResourceType.RUNES) {
                rp.tickRunes(dt, d.hasteMult() * m.productRef(ModType.RESOURCE_REGEN_PCT, t.name()));
                continue;
            }
            double rate = u.inCombat ? t.regenInCombat : t.regenOutOfCombat;
            if (t == ResourceType.MANA && u.role() == Role.HEALER) rate *= 1.6;
            if (t.hasted) rate *= d.hasteMult();
            rate *= m.productRef(ModType.RESOURCE_REGEN_PCT, t.name());
            double cur = rp.get(t);
            if (rate > 0) {
                double cap = (!u.inCombat && t.oocTarget > 0) ? Math.max(cur, t.oocTarget) : rp.max(t);
                if (cur < cap) rp.set(t, Math.min(cap, cur + rate * dt));
            }
            if (!u.inCombat && t.decayOutOfCombat > 0 && now - u.lastCombatAt > 3.0) {
                double target = Math.max(0, t.oocTarget);
                cur = rp.get(t);
                if (cur > target) rp.set(t, Math.max(target, cur - t.decayOutOfCombat * dt * (t.pips ? 1 : 1)));
            }
        }
    }

    // ================================================================== auto attacks

    private void tickAutoAttack(UnitState u) {
        if (!u.autoAttack) return;
        UnitState t = u.target;
        if (t == null || t.isDead() || !isHostile(u, t) || !canTarget(u, t)) {
            if (u.isPlayerLike() && (t == null || t.isDead())) u.autoAttack = false;
            return;
        }
        if (!u.canAttack() || u.cast != null && !u.mainHand.ranged()) return;
        if (u.auras.isStealthed()) return;
        if (u.auras.hasCc(CcType.DISARM) && !u.mainHand.ranged()) return;
        boolean ranged = u.mainHand != null && u.mainHand.ranged();
        double range = ranged ? Ability.RANGED : Ability.MELEE_RANGE;
        double dist = distance(u, t);
        if (dist > range) return;
        if (!isFacing(u, t) && u.isPlayerLike()) return;
        if (ranged && !lineOfSight(u, t)) return;
        if (now >= u.nextSwingMain) {
            swing(u, t, false);
        }
        if (u.offHand != null && !ranged && now >= u.nextSwingOff) {
            swing(u, t, true);
        }
    }

    /** Performs a single weapon swing (also used by the left-click attack). */
    public HitResult swing(UnitState u, UnitState t, boolean offhand) {
        WeaponInfo w = offhand ? u.offHand : u.mainHand;
        if (w == null) w = WeaponInfo.FISTS;
        double speed = w.speed() / u.stats().hasteMult();
        if (offhand) u.nextSwingOff = now + speed;
        else {
            u.nextSwingMain = now + speed;
            if (u.offHand != null && u.nextSwingOff < now) u.nextSwingOff = now + speed * 0.5;
        }
        double dmg;
        if (u.isNpcLike() || (u.kind == UnitKind.PET && u.npcAttackPower > 0)) {
            dmg = u.npcAttackPower * w.speed() / 2.0;
        } else {
            dmg = Formulas.weaponDamage(u, w, false);
        }
        if (offhand) dmg *= 0.5;
        Ability auto = Registry.ability(w.ranged() ? "auto_shot" : "auto_attack");
        EffectContext ctx = new EffectContext(this, u, t, null, auto, null);
        ctx.schoolOverride = School.PHYSICAL;
        HitResult hit = dealDamage(ctx, t, School.PHYSICAL, dmg);
        if (u.resources.has(ResourceType.RAGE)) {
            double rage = w.speed() * (offhand ? 1.75 : 3.5);
            energize(u, ResourceType.RAGE, rage);
        }
        if (u.body != null) u.body.playAnimation(w.ranged() ? "shoot" : "swing");
        if (hit != null) fireTriggers(u, TriggerType.AUTO_ATTACK, t, auto, null, hit.amount);
        return hit;
    }

    public boolean swingReady(UnitState u) {
        return now >= u.nextSwingMain;
    }

    public double nextSwing(UnitState u) {
        return u.nextSwingMain;
    }

    // ================================================================== casting

    /** Main entry point for "use ability" from players, bots and NPCs. */
    public CastResult cast(UnitState caster, String abilityId, UnitState target, Vec3 point) {
        String resolved = resolveAbility(caster, abilityId);
        Ability a = Registry.ability(resolved);
        if (a == null) return fail(caster, CastResult.UNKNOWN, null);
        if (caster.isPlayerLike() && !caster.knownAbilities.contains(a.id) && !caster.knownAbilities.contains(abilityId)) {
            return fail(caster, CastResult.UNKNOWN, a);
        }
        if (a.passive) return fail(caster, CastResult.PASSIVE, a);
        if (caster.isDead()) return fail(caster, CastResult.DEAD, a);

        // empower release: casting the same empowered ability again releases it
        if (caster.cast != null && caster.cast.type == CastType.EMPOWER && caster.cast.ability == a) {
            caster.cast.released = true;
            return CastResult.OK;
        }

        boolean onGcd = a.gcd > 0 && now < caster.gcdEnd;
        boolean busy = caster.cast != null && !a.usableWhileCasting;
        if (onGcd || busy) {
            double remaining = Math.max(onGcd ? caster.gcdEnd - now : 0, busy ? caster.cast.end - now : 0);
            if (busy && caster.cast.type == CastType.CHANNEL && a.gcd > 0) {
                // casting something new during a channel clips it (like WoW)
                if (!onGcd) {
                    cancelCast(caster, false);
                    busy = false;
                }
            }
            if (onGcd || busy) {
                if (remaining <= SPELL_QUEUE_WINDOW || caster.isNpcLike()) {
                    caster.queued = new UnitState.QueuedCast(abilityId, target, point, now);
                    return CastResult.QUEUED;
                }
                return busy ? fail(caster, CastResult.BUSY, a) : CastResult.GCD;
            }
        }

        CastAttempt at = validate(caster, a, target, point, true);
        if (at.result != CastResult.OK) return fail(caster, at.result, a);
        if (castFilter != null) {
            CastResult fr = castFilter.apply(caster, a);
            if (fr != null && fr != CastResult.OK) return fail(caster, fr, a);
        }
        return begin(caster, a, at);
    }

    /** Applies talent replacements and temporary (aura) replacements such as Metamorphosis. */
    public String resolveAbility(UnitState caster, String abilityId) {
        String id = caster.replacements.getOrDefault(abilityId, abilityId);
        for (String ref : caster.mods().refs(ModType.REPLACE_ABILITY)) {
            int i = ref.indexOf('>');
            if (i > 0 && ref.substring(0, i).equals(id)) return ref.substring(i + 1);
        }
        return id;
    }

    /** Checks whether an ability could be cast right now (used by UI and bots). */
    public CastResult check(UnitState caster, String abilityId, UnitState target) {
        String resolved = resolveAbility(caster, abilityId);
        Ability a = Registry.ability(resolved);
        if (a == null) return CastResult.UNKNOWN;
        if (caster.isDead()) return CastResult.DEAD;
        if (a.gcd > 0 && now < caster.gcdEnd) return CastResult.GCD;
        if (caster.cast != null && !a.usableWhileCasting) return CastResult.BUSY;
        return validate(caster, a, target, null, false).result;
    }

    private static final class CastAttempt {
        CastResult result = CastResult.OK;
        UnitState target;
        Vec3 point;
        int comboSpent;
    }

    private CastAttempt validate(UnitState caster, Ability a, UnitState target, Vec3 point, boolean forReal) {
        CastAttempt at = new CastAttempt();
        if (caster.isPlayerLike() && a.wowClass != null && caster.spec != null && !a.usableBy(caster.spec)
                && !caster.knownAbilities.contains(a.id)) {
            at.result = CastResult.WRONG_SPEC;
            return at;
        }
        // crowd control
        if (!a.usableWhileCc) {
            for (AuraInstance au : caster.auras.all()) {
                if (au.removed || au.def.cc == null) continue;
                CcType cc = au.def.cc;
                if (cc == CcType.SILENCE) {
                    if (a.school.isMagic() || a.hasTag("spell")) {
                        at.result = CastResult.SILENCED;
                        return at;
                    }
                } else if (cc.preventsCasting) {
                    at.result = CastResult.CROWD_CONTROLLED;
                    return at;
                } else if (cc == CcType.DISARM && a.hasTag("weapon")) {
                    at.result = CastResult.CROWD_CONTROLLED;
                    return at;
                }
            }
            if (!caster.canMove() && a.hasTag("movement") && !a.hasTag("breaks_root")) {
                at.result = CastResult.CROWD_CONTROLLED;
                return at;
            }
        }
        if (now < caster.lockoutUntil(a.school) && a.school.isMagic()) {
            at.result = CastResult.LOCKED_OUT;
            return at;
        }
        // cooldown
        if (a.cooldown > 0 || caster.cooldowns.get(a.id) != null) {
            int maxCharges = maxCharges(caster, a);
            if (caster.cooldowns.charges(a.id, maxCharges, now) <= 0) {
                at.result = CastResult.COOLDOWN;
                return at;
            }
        }
        // target
        switch (a.targetType) {
            case SELF, NONE -> at.target = caster;
            case ENEMY -> {
                UnitState t = target;
                if (t == null || !isHostile(caster, t)) t = caster.target != null && isHostile(caster, caster.target) ? caster.target : null;
                if (t == null) {
                    at.result = CastResult.NO_TARGET;
                    return at;
                }
                if (!canTarget(caster, t) || t.isDead()) {
                    at.result = CastResult.INVALID_TARGET;
                    return at;
                }
                at.target = t;
            }
            case FRIENDLY -> {
                UnitState t = target;
                if (t == null || !isFriendly(caster, t) || t.isDead()) {
                    t = caster.target != null && isFriendly(caster, caster.target) && caster.target.isAlive() ? caster.target : caster;
                }
                at.target = t;
            }
            case ANY -> at.target = target != null && target.isAlive() ? target : (caster.target != null ? caster.target : caster);
            case DEAD_FRIENDLY -> {
                UnitState t = target != null ? target : caster.target;
                if (t == null || !t.isDead() || !isFriendly(caster, t) && !(t.isPlayerLike() && hostility.sameSide(caster, t))) {
                    at.result = CastResult.INVALID_TARGET;
                    return at;
                }
                at.target = t;
            }
            case GROUND -> {
                at.target = target;
                at.point = point != null ? point : (target != null ? target.position() : caster.position());
            }
        }
        double range = a.range + caster.mods().sum(ModType.RANGE, ModContext.of(a));
        if (at.target != null && at.target != caster && a.targetType != TargetType.SELF && a.targetType != TargetType.NONE) {
            if (!sameWorld(caster, at.target)) {
                at.result = CastResult.OUT_OF_RANGE;
                return at;
            }
            double dist = distance(caster, at.target);
            if (dist > range + 0.3) {
                at.result = CastResult.OUT_OF_RANGE;
                return at;
            }
            if (a.minRange > 0 && dist < a.minRange) {
                at.result = CastResult.TOO_CLOSE;
                return at;
            }
            if (!a.ignoresLineOfSight && dist > 2.0 && !lineOfSight(caster, at.target)) {
                at.result = CastResult.NO_LINE_OF_SIGHT;
                return at;
            }
            if (a.requiresFacing && a.targetType == TargetType.ENEMY && caster.isPlayerLike() && !isFacing(caster, at.target)) {
                at.result = CastResult.NOT_FACING;
                return at;
            }
        }
        if (a.targetType == TargetType.GROUND && at.point != null && caster.position().distance(at.point) > range + 1.0) {
            at.result = CastResult.OUT_OF_RANGE;
            return at;
        }
        // requirements
        if (!a.requirements.isEmpty()) {
            EffectContext ctx = new EffectContext(this, caster, at.target, at.point, a, null);
            for (Cond c : a.requirements) {
                if (!c.test(ctx)) {
                    at.result = CastResult.REQUIREMENT;
                    return at;
                }
            }
        }
        // resources
        for (Cost c : a.costs) {
            double cost = effectiveCost(caster, a, c);
            if (!caster.resources.has(c.type()) && caster.isPlayerLike()) {
                continue;
            }
            if (!caster.resources.canAfford(c.type(), cost)) {
                at.result = CastResult.NO_RESOURCE;
                return at;
            }
            if (c.upTo() > 0) {
                at.comboSpent = (int) Math.min(c.upTo(), Math.floor(caster.resources.get(c.type()) + 1e-6));
            }
        }
        // movement for cast-time spells
        double castTime = castTime(caster, a);
        if ((a.castType == CastType.CAST && castTime > 0 || a.castType == CastType.CHANNEL || a.castType == CastType.EMPOWER)
                && !canCastWhileMoving(caster, a) && caster.body != null && caster.body.isMoving()) {
            at.result = CastResult.MOVING;
            return at;
        }
        return at;
    }

    private CastResult begin(UnitState caster, Ability a, CastAttempt at) {
        if (a.requiredFormAura != null && !caster.auras().has(a.requiredFormAura)) {
            applyAura(new EffectContext(this, caster, caster, null, null, null), caster, a.requiredFormAura, 1, -1);
        }
        double castTime = castTime(caster, a);
        boolean hasCastBar = a.castType == CastType.CHANNEL || a.castType == CastType.EMPOWER || (a.castType == CastType.CAST && castTime > 0.05);
        if (hasCastBar && caster.auras.isStealthed() && a.breaksStealth && !a.isHelpful()) {
            // stealth breaks when starting an offensive cast; instants break it after their effects (so "from stealth" bonuses apply)
            breakStealth(caster);
        }
        if (a.targetType == TargetType.ENEMY && at.target != null) {
            caster.target = at.target;
            caster.markDirty(UnitState.DIRTY_TARGET);
        }
        triggerGcd(caster, a);
        switch (a.castType) {
            case CAST -> {
                if (castTime <= 0.05) {
                    payCosts(caster, a);
                    execute(caster, a, at.target, at.point, at.comboSpent, 1);
                } else {
                    caster.cast = new CastState(a, at.target, at.point, now, now + castTime, CastType.CAST, a.interruptible);
                    caster.cast.comboSpent = at.comboSpent;
                    caster.markDirty(UnitState.DIRTY_CAST);
                    if (caster.body != null && at.target != null && at.target != caster) caster.body.lookAt(at.target.position());
                    for (CombatListener l : listeners) l.onCastStart(caster, a, at.target, castTime);
                    if (a.vfx != null) vfx(a.vfx + "_cast", caster, at.target, at.point);
                }
            }
            case CHANNEL -> {
                int spent = payCosts(caster, a);
                double hm = caster.stats().hasteMult();
                double duration = a.channelDuration / hm;
                CastState cs = new CastState(a, at.target, at.point, now, now + duration, CastType.CHANNEL, a.interruptible);
                cs.tickInterval = a.channelTickInterval / hm;
                cs.nextTick = now + cs.tickInterval;
                cs.comboSpent = spent;
                caster.cast = cs;
                caster.markDirty(UnitState.DIRTY_CAST);
                for (CombatListener l : listeners) l.onCastStart(caster, a, at.target, duration);
                // channels execute their "on start" effects immediately and trigger the cooldown
                EffectContext ctx = new EffectContext(this, caster, at.target, at.point, a, null);
                ctx.comboSpent = spent;
                for (Effect e : a.effects) e.apply(ctx);
                startCooldown(caster, a);
                if (at.target != null && isHostile(caster, at.target)) enterCombat(caster, at.target);
            }
            case EMPOWER -> {
                double hm = caster.stats().hasteMult();
                double stage = 0.8 / hm;
                double duration = stage * a.maxEmpowerStage + 1.0;
                CastState cs = new CastState(a, at.target, at.point, now, now + duration, CastType.EMPOWER, a.interruptible);
                cs.stageDuration = stage;
                caster.cast = cs;
                caster.markDirty(UnitState.DIRTY_CAST);
                for (CombatListener l : listeners) l.onCastStart(caster, a, at.target, duration);
            }
            default -> {
                int spent = payCosts(caster, a);
                execute(caster, a, at.target, at.point, Math.max(spent, at.comboSpent), 1);
            }
        }
        return CastResult.OK;
    }

    private void tickCast(UnitState u, boolean moving) {
        CastState c = u.cast;
        if (!u.isAlive()) {
            cancelCast(u, false);
            return;
        }
        if (moving && !canCastWhileMoving(u, c.ability) && (c.type != CastType.CAST || c.end - now > 0.05)) {
            cancelCast(u, false);
            return;
        }
        if (!u.canCast() && !c.ability.usableWhileCc) {
            cancelCast(u, true);
            return;
        }
        switch (c.type) {
            case CAST -> {
                if (now >= c.end) {
                    u.cast = null;
                    u.markDirty(UnitState.DIRTY_CAST);
                    UnitState t = c.target;
                    if (c.ability.targetType == TargetType.ENEMY && (t == null || t.isDead() || !canTarget(u, t))) {
                        for (CombatListener l : listeners) l.onCastFailed(u, c.ability, false);
                        return;
                    }
                    if (t != null && t != u && c.ability.targetType != TargetType.GROUND && distance(u, t) > c.ability.range + 3.0) {
                        fail(u, CastResult.OUT_OF_RANGE, c.ability);
                        for (CombatListener l : listeners) l.onCastFailed(u, c.ability, false);
                        return;
                    }
                    if (!canAffordAll(u, c.ability)) {
                        fail(u, CastResult.NO_RESOURCE, c.ability);
                        for (CombatListener l : listeners) l.onCastFailed(u, c.ability, false);
                        return;
                    }
                    int spent = payCosts(u, c.ability);
                    execute(u, c.ability, t, c.point, Math.max(spent, c.comboSpent), 1);
                }
            }
            case CHANNEL -> {
                while (u.cast == c && now >= c.nextTick && c.nextTick <= c.end + 1e-6) {
                    c.ticksDone++;
                    c.nextTick += c.tickInterval;
                    UnitState t = c.target;
                    if (c.ability.targetType == TargetType.ENEMY && (t == null || t.isDead())) {
                        cancelCast(u, false);
                        return;
                    }
                    EffectContext ctx = new EffectContext(this, u, t, c.point, c.ability, null);
                    ctx.comboSpent = c.comboSpent;
                    ctx.periodic = true;
                    for (Effect e : c.ability.channelEffects) e.apply(ctx);
                }
                if (u.cast == c && now >= c.end) {
                    u.cast = null;
                    u.markDirty(UnitState.DIRTY_CAST);
                    for (CombatListener l : listeners) l.onCastSuccess(u, c.ability, c.target);
                    fireTriggers(u, TriggerType.CAST, c.target, c.ability, null, 0);
                }
            }
            case EMPOWER -> {
                boolean full = now >= c.start + c.stageDuration * c.ability.maxEmpowerStage;
                if (c.released || now >= c.end || full && u.isNpcLike()) {
                    int stage = c.empowerStage(now);
                    u.cast = null;
                    u.markDirty(UnitState.DIRTY_CAST);
                    if (!canAffordAll(u, c.ability)) {
                        fail(u, CastResult.NO_RESOURCE, c.ability);
                        return;
                    }
                    payCosts(u, c.ability);
                    execute(u, c.ability, c.target, c.point, 0, stage);
                }
            }
            default -> {
            }
        }
    }

    /** Releases an empowered spell (key released). */
    public void releaseEmpower(UnitState u) {
        if (u.cast != null && u.cast.type == CastType.EMPOWER) u.cast.released = true;
    }

    public void cancelCast(UnitState u, boolean interrupted) {
        if (u.cast == null) return;
        Ability a = u.cast.ability;
        u.cast = null;
        u.queued = null;
        u.markDirty(UnitState.DIRTY_CAST);
        for (CombatListener l : listeners) l.onCastFailed(u, a, interrupted);
    }

    /** Executes an ability's effects (after costs were paid). */
    private void execute(UnitState caster, Ability a, UnitState target, Vec3 point, int comboSpent, int empowerStage) {
        EffectContext ctx = new EffectContext(this, caster, target, point, a, null);
        ctx.comboSpent = comboSpent;
        ctx.empowerStage = empowerStage;
        if (target != null && target != caster && caster.body != null && a.targetType == TargetType.ENEMY) {
            caster.body.lookAt(target.position());
        }
        for (Effect e : a.effects) {
            e.apply(ctx);
        }
        for (Cost g : a.generates) {
            double amount = g.amount() + caster.mods().sum(ModType.RESOURCE_GEN_FLAT, ModContext.of(a));
            energize(caster, g.type(), amount);
        }
        startCooldown(caster, a);
        if (a.breaksStealth && !a.isHelpful()) breakStealth(caster);
        if (target != null && target != caster && isHostile(caster, target)) {
            enterCombat(caster, target);
            if (a.startsAutoAttack && caster.isPlayerLike()) {
                caster.autoAttack = true;
            }
        }
        if (a.isHelpful() && target != null && target != caster && target.inCombat) {
            caster.lastCombatAt = Math.max(caster.lastCombatAt, now - 1);
        }
        if (caster.body != null && a.animation != null) caster.body.playAnimation(a.animation);
        if (a.vfx != null) vfx(a.vfx, caster, target, point);
        for (CombatListener l : listeners) l.onCastSuccess(caster, a, target);
        fireTriggers(caster, TriggerType.CAST, target, a, null, 0);
    }

    /** Executes an ability's effects without costs, cooldowns or GCD (procs). */
    public void triggerAbility(UnitState caster, String abilityId, UnitState target, Vec3 point) {
        Ability a = Registry.ability(abilityId);
        if (a == null) return;
        if (procDepth > 4) return;
        procDepth++;
        try {
            EffectContext ctx = new EffectContext(this, caster, target, point, a, null);
            for (Effect e : a.effects) e.apply(ctx);
            if (a.vfx != null) vfx(a.vfx, caster, target, point);
        } finally {
            procDepth--;
        }
    }

    private void triggerGcd(UnitState u, Ability a) {
        if (a.gcd <= 0) return;
        double gcd = a.gcd;
        if (gcd >= 1.0) gcd = Math.max(a.gcd >= 1.5 ? Formulas.MIN_GCD : 1.0, gcd / u.stats().hasteMult());
        u.gcdEnd = now + gcd;
        u.gcdDuration = gcd;
        u.markDirty(UnitState.DIRTY_COOLDOWNS);
    }

    public double castTime(UnitState u, Ability a) {
        if (a.castType != CastType.CAST) return 0;
        double t = a.castTime * u.mods().product(ModType.CAST_TIME_PCT, ModContext.of(a));
        return Math.max(0, t / u.stats().hasteMult());
    }

    public boolean canCastWhileMoving(UnitState u, Ability a) {
        if (a.castWhileMoving) return true;
        return u.mods().sum(ModType.CAST_WHILE_MOVING, ModContext.of(a)) > 0;
    }

    public int maxCharges(UnitState u, Ability a) {
        return Math.max(1, a.charges + (int) u.mods().sum(ModType.CHARGES, ModContext.of(a)));
    }

    public double cooldownDuration(UnitState u, Ability a) {
        ModContext mc = ModContext.of(a);
        double cd = a.cooldown * u.mods().product(ModType.COOLDOWN_PCT, mc) + u.mods().sum(ModType.COOLDOWN_FLAT, mc);
        if (a.hastedCooldown) cd /= u.stats().hasteMult();
        double rate = u.mods().sum(ModType.COOLDOWN_RATE, mc);
        if (rate > 0) cd /= 1.0 + rate;
        return Math.max(0, cd);
    }

    private void startCooldown(UnitState u, Ability a) {
        double cd = cooldownDuration(u, a);
        if (cd > 0) u.cooldowns.trigger(a.id, cd, maxCharges(u, a), now);
        for (String shared : a.sharedCooldown) {
            Ability o = Registry.ability(shared);
            if (o != null) u.cooldowns.lock(shared, Math.min(cd, Math.max(cd, 0)), now);
        }
    }

    public double effectiveCost(UnitState u, Ability a, Cost c) {
        ModContext mc = ModContext.of(a);
        double cost = c.amount() * u.mods().product(ModType.COST_PCT, mc) + u.mods().sum(ModType.COST_FLAT, mc);
        return Math.max(0, cost);
    }

    private boolean canAffordAll(UnitState u, Ability a) {
        for (Cost c : a.costs) {
            if (!u.resources.has(c.type())) continue;
            if (!u.resources.canAfford(c.type(), effectiveCost(u, a, c))) return false;
        }
        return true;
    }

    /** Pays the costs and returns the amount of "upTo" resource spent (combo points etc.). */
    private int payCosts(UnitState u, Ability a) {
        int spent = 0;
        for (Cost c : a.costs) {
            if (!u.resources.has(c.type())) continue;
            double cost = effectiveCost(u, a, c);
            if (c.upTo() > 0) {
                double avail = u.resources.get(c.type());
                double use = Math.min(c.upTo(), Math.floor(avail + 1e-6));
                use = Math.max(cost, use);
                u.resources.add(c.type(), -use);
                spent = (int) Math.round(use);
            } else {
                u.resources.add(c.type(), -cost);
                if (c.type().pips) spent = Math.max(spent, (int) Math.round(c.amount()));
            }
        }
        u.markDirty(UnitState.DIRTY_RESOURCES);
        return spent;
    }

    private CastResult fail(UnitState u, CastResult r, Ability a) {
        if (r != CastResult.GCD && r != CastResult.QUEUED) {
            for (CombatListener l : listeners) l.onError(u, r, a);
        }
        return r;
    }

    // ================================================================== damage

    /** Full damage pipeline: modifiers, crit, mitigation, absorbs, leech, threat, procs. */
    public HitResult dealDamage(EffectContext ctx, UnitState target, School school, double base) {
        return damageInternal(ctx, target, school, base, true);
    }

    /** Damage without the source's modifiers / crit (boss mechanics, environment). Target mitigation still applies. */
    public HitResult dealRawDamage(EffectContext ctx, UnitState target, School school, double base) {
        return damageInternal(ctx, target, school, base, false);
    }

    private HitResult damageInternal(EffectContext ctx, UnitState target, School school, double base, boolean withSourceMods) {
        if (target == null || target.isDead() || base <= 0) return null;
        UnitState src = ctx.caster;
        HitResult hit = new HitResult(src, target, false, school);
        hit.periodic = ctx.periodic;
        hit.abilityId = ctx.ability != null ? ctx.ability.id : null;
        hit.auraId = ctx.aura != null ? ctx.aura.def.id : null;
        ModContext mc = ctx.aura != null && ctx.ability == null
                ? ModContext.aura(ctx.aura.def.id, ctx.aura.def.tags, school)
                : ModContext.of(ctx.ability, school);

        // immunity
        ModifierSet tm = target.mods();
        if (tm.hasRef(ModType.DAMAGE_IMMUNE, school.name())) {
            hit.immune = true;
            for (CombatListener l : listeners) l.onDamage(hit);
            return hit;
        }

        double amount = base;
        if (withSourceMods && src != null) {
            ModifierSet sm = src.mods();
            amount *= sm.product(ModType.DAMAGE_DONE, mc);
            amount *= masteryDamageMult(src, mc);
            if (src.kind == UnitKind.PET && src.owner != null) {
                amount *= src.owner.mods().product(ModType.PET_DAMAGE, ModContext.NONE);
                if (src.owner.spec != null && src.owner.spec.masteryKind == MasteryKind.PET_DAMAGE) {
                    amount *= 1.0 + src.owner.stats().masteryPct / 100.0;
                }
            }
            amount *= 1.0 + src.stats().versPct / 100.0;
            if (target.healthFraction() < 0.35) amount *= sm.product(ModType.EXECUTE_DAMAGE, mc);
            amount *= src.damageMultiplier;
            // crit
            double critChance = 0;
            if (src.isPlayerLike() || src.kind == UnitKind.PET) {
                critChance = src.stats().critPct + sm.sum(ModType.CRIT_CHANCE, mc) - (mc.abilityId() == null ? 0 : 0);
                if (mc.abilityId() != null) critChance += 0;
            }
            if (critChance > 0 && rng.nextDouble() * 100.0 < critChance) {
                hit.crit = true;
                amount *= Formulas.CRIT_MULTIPLIER + sm.sum(ModType.CRIT_DAMAGE, mc);
            }
            if (src.master().isPlayerLike() && target.master().isPlayerLike()) {
                amount *= Formulas.PVP_DAMAGE_MULT * sm.product(ModType.PVP_DAMAGE, mc);
            }
        } else if (src != null) {
            amount *= src.damageMultiplier;
        }

        // target mitigation
        if (school == School.PHYSICAL && !(ctx.ability != null && ctx.ability.hasTag("ignore_armor")) && !hit.periodic) {
            if (target.isPlayerLike() || target.kind == UnitKind.PET) {
                amount *= 1.0 - Ratings.armorReduction(target.stats().armor, src != null ? src.level : target.level);
            } else {
                amount *= 1.0 - target.npcArmorReduction;
            }
        }
        amount *= tm.product(ModType.DAMAGE_TAKEN, ModContext.school(school));
        if (target.isPlayerLike() || target.kind == UnitKind.PET) {
            amount *= 1.0 - Math.min(0.5, target.stats().versDrPct / 100.0);
            if (target.spec != null && target.spec.masteryKind == MasteryKind.DEFENSIVE) {
                amount *= 1.0 - Math.min(0.3, target.stats().masteryPct / 300.0);
            }
            if (ctx.aoeTargets > 1 || ctx.ability != null && ctx.ability.hasTag("aoe")) {
                amount *= 1.0 - Math.min(0.5, target.stats().avoidancePct / 100.0);
            }
        }
        amount = Math.max(0, amount);
        hit.amount = amount;

        // absorbs
        double remaining = amount;
        for (AuraInstance a : new ArrayList<>(target.auras.all())) {
            if (remaining <= 0) break;
            if (a.removed || !a.def.absorb || a.absorbRemaining <= 0) continue;
            if (a.def.absorbMagicOnly && !school.isMagic()) continue;
            double soak = Math.min(remaining, a.absorbRemaining);
            a.absorbRemaining -= soak;
            remaining -= soak;
            hit.absorbed += soak;
            target.auras.markDirty();
            if (a.absorbRemaining <= 0.5) removeAura(a, false);
        }

        applyHealthLoss(target, remaining, hit, src);

        if (src != null) {
            // leech
            double leech = src.stats().leechPct;
            if (leech > 0 && hit.effective() > 0 && src.isAlive()) {
                rawHeal(src, src, hit.effective() * leech / 100.0);
            }
            // threat
            if (target.threat != null) {
                double threat = (hit.effective() + hit.absorbed) * threatMultiplier(src, mc);
                target.threat.add(src, threat);
            }
            ctx.lastCrit = hit.crit;
            ctx.lastAmount = hit.effective();
            enterCombat(src, target);
        }

        // break crowd control on damage
        if (hit.effective() + hit.absorbed > 0) breakCcOnDamage(target, hit.effective() + hit.absorbed);

        // rage from being hit
        if (target.resources.has(ResourceType.RAGE) && hit.effective() > 0 && target.isAlive()) {
            double rage = hit.effective() / Math.max(1, target.maxHealth) * (target.role() == Role.TANK ? 120 : 60);
            energize(target, ResourceType.RAGE, rage);
        }

        for (CombatListener l : listeners) l.onDamage(hit);

        // procs
        if (src != null && procDepth < 3) {
            procDepth++;
            try {
                fireTriggers(src, TriggerType.DAMAGE_DEALT, target, ctx.ability, ctx.aura, hit.amount);
                if (hit.crit) fireTriggers(src, TriggerType.CRIT_DEALT, target, ctx.ability, ctx.aura, hit.amount);
                if (src.kind == UnitKind.PET && src.owner != null) {
                    fireTriggers(src.owner, TriggerType.DAMAGE_DEALT, target, ctx.ability, ctx.aura, hit.amount);
                }
                if (target.isAlive()) {
                    fireTriggers(target, TriggerType.DAMAGE_TAKEN, src, ctx.ability, ctx.aura, hit.amount);
                }
                if (hit.killed) fireTriggers(src.master(), TriggerType.KILL, target, ctx.ability, ctx.aura, hit.amount);
            } finally {
                procDepth--;
            }
        }
        return hit;
    }

    private double masteryDamageMult(UnitState src, ModContext mc) {
        if (src.spec == null) return 1.0;
        double m = src.stats().masteryPct / 100.0;
        return switch (src.spec.masteryKind) {
            case ALL_DAMAGE -> 1.0 + m;
            case TAGGED_DAMAGE -> src.spec.masteryTag != null && mc.tags() != null && mc.tags().contains(src.spec.masteryTag) ? 1.0 + m : 1.0;
            default -> 1.0;
        };
    }

    private double threatMultiplier(UnitState src, ModContext mc) {
        double t = src.mods().product(ModType.THREAT_PCT, mc);
        if (src.role() == Role.TANK && (src.isPlayerLike())) t *= TANK_THREAT;
        if (src.kind == UnitKind.PET) t *= 1.0;
        return t;
    }

    /** Environmental / vanilla damage converted into the combat model (no source modifiers, no procs). */
    public HitResult environmentalDamage(UnitState target, double amount, UnitState attacker) {
        if (target == null || target.isDead() || amount <= 0) return null;
        HitResult hit = new HitResult(attacker, target, false, School.PHYSICAL);
        hit.environmental = true;
        hit.amount = amount;
        double remaining = amount;
        for (AuraInstance a : new ArrayList<>(target.auras.all())) {
            if (remaining <= 0) break;
            if (a.removed || !a.def.absorb || a.absorbRemaining <= 0) continue;
            double soak = Math.min(remaining, a.absorbRemaining);
            a.absorbRemaining -= soak;
            remaining -= soak;
            hit.absorbed += soak;
            if (a.absorbRemaining <= 0.5) removeAura(a, false);
        }
        applyHealthLoss(target, remaining, hit, attacker);
        if (attacker != null) enterCombat(attacker, target);
        for (CombatListener l : listeners) l.onDamage(hit);
        return hit;
    }

    private void applyHealthLoss(UnitState target, double loss, HitResult hit, UnitState src) {
        if (loss <= 0) return;
        double old = target.health;
        if (target.health - loss <= 0 && procDepth < 5) {
            // cheat death effects
            procDepth++;
            try {
                fireTriggers(target, TriggerType.LETHAL_DAMAGE, src, null, null, loss);
            } finally {
                procDepth--;
            }
            if (target.mods().hasRef(ModType.DAMAGE_IMMUNE, "ALL")) {
                return;
            }
        }
        target.health = Math.max(0, target.health - loss);
        target.recordDamageTaken(now, loss);
        target.markDirty(UnitState.DIRTY_HEALTH);
        double fracBefore = old / Math.max(1, target.maxHealth);
        if (target.health <= 0) {
            hit.killed = true;
            if (target.body != null) target.body.applyHealth(target, old, hit);
            die(target, src);
        } else {
            if (target.body != null) target.body.applyHealth(target, old, hit);
            for (AuraInstance a : new ArrayList<>(target.auras.all())) {
                for (int i = 0; i < a.def.triggers.size(); i++) {
                    Trigger t = a.def.triggers.get(i);
                    if (t.type() == TriggerType.HEALTH_BELOW && fracBefore >= t.threshold() && target.healthFraction() < t.threshold()) {
                        runTrigger(a, i, t, src);
                    }
                }
            }
        }
    }

    // ================================================================== healing

    public HitResult heal(EffectContext ctx, UnitState target, double base) {
        return heal(ctx, target, base, false);
    }

    public HitResult heal(EffectContext ctx, UnitState target, double base, boolean noCrit) {
        if (target == null || target.isDead() || base <= 0) return null;
        UnitState src = ctx.caster;
        School school = ctx.school();
        HitResult hit = new HitResult(src, target, true, school);
        hit.periodic = ctx.periodic;
        hit.abilityId = ctx.ability != null ? ctx.ability.id : null;
        hit.auraId = ctx.aura != null ? ctx.aura.def.id : null;
        ModContext mc = ctx.aura != null && ctx.ability == null
                ? ModContext.aura(ctx.aura.def.id, ctx.aura.def.tags, school) : ModContext.of(ctx.ability, school);
        double amount = base;
        if (src != null) {
            ModifierSet sm = src.mods();
            amount *= sm.product(ModType.HEALING_DONE, mc);
            amount *= 1.0 + src.stats().versPct / 100.0;
            if (src.spec != null) {
                double m = src.stats().masteryPct / 100.0;
                switch (src.spec.masteryKind) {
                    case ALL_HEALING -> amount *= 1.0 + m;
                    case LOW_HEALTH_HEALING -> amount *= 1.0 + m * (1.0 - target.healthFraction());
                    default -> {
                    }
                }
            }
            if (!noCrit && (src.isPlayerLike() || src.kind == UnitKind.PET)) {
                double cc = src.stats().critPct + sm.sum(ModType.CRIT_CHANCE, mc);
                if (rng.nextDouble() * 100 < cc) {
                    hit.crit = true;
                    amount *= Formulas.CRIT_MULTIPLIER + sm.sum(ModType.CRIT_DAMAGE, mc);
                }
            }
            amount *= src.damageMultiplier > 1 && src.isNpcLike() ? src.damageMultiplier : 1.0;
        }
        amount *= target.mods().product(ModType.HEALING_TAKEN, ModContext.NONE);
        amount = Math.max(0, amount);
        hit.amount = amount;
        double old = target.health;
        double missing = target.maxHealth - target.health;
        hit.overheal = Math.max(0, amount - missing);
        target.health = Math.min(target.maxHealth, target.health + amount);
        target.markDirty(UnitState.DIRTY_HEALTH);
        if (target.body != null && hit.effective() > 0) target.body.applyHealth(target, old, hit);

        if (src != null && hit.effective() > 0) {
            // healing threat: split among enemies engaged with the target
            if (target.inCombat) {
                List<UnitState> enemies = new ArrayList<>();
                for (UnitState e : target.engaged) if (e.isAlive() && e.threat != null && isHostile(e, src)) enemies.add(e);
                if (!enemies.isEmpty()) {
                    double t = hit.effective() * 0.5 / enemies.size() * threatMultiplier(src, mc) / (src.role() == Role.TANK ? TANK_THREAT : 1);
                    for (UnitState e : enemies) {
                        e.threat.add(src, t);
                        src.engaged.add(e);
                        e.engaged.add(src);
                    }
                    src.lastCombatAt = now;
                }
            }
        }
        ctx.lastCrit = hit.crit;
        ctx.lastAmount = hit.effective();
        for (CombatListener l : listeners) l.onHeal(hit);
        if (src != null && procDepth < 3) {
            procDepth++;
            try {
                fireTriggers(src, TriggerType.HEAL_DEALT, target, ctx.ability, ctx.aura, hit.amount);
            } finally {
                procDepth--;
            }
        }
        return hit;
    }

    /** Heal without modifiers, crits or events other than sync (leech, regen effects). */
    public void rawHeal(UnitState src, UnitState target, double amount) {
        if (target == null || target.isDead() || amount <= 0) return;
        double old = target.health;
        target.health = Math.min(target.maxHealth, target.health + amount);
        target.markDirty(UnitState.DIRTY_HEALTH);
        if (target.body != null && target.health != old) target.body.applyHealth(target, old, null);
    }

    public AuraInstance applyAbsorb(EffectContext ctx, UnitState target, String auraId, double base) {
        UnitState src = ctx.caster;
        double amount = base;
        if (src != null) {
            amount *= src.mods().product(ModType.ABSORB_DONE, ModContext.of(ctx.ability));
            amount *= 1.0 + src.stats().versPct / 100.0;
            if (src.spec != null && src.spec.masteryKind == MasteryKind.ABSORBS) {
                amount *= 1.0 + src.stats().masteryPct / 100.0;
            }
        }
        amount *= ctx.scale > 0 ? 1.0 : 0.0;
        AuraInstance a = applyAura(ctx, target, auraId, 1, -1);
        if (a != null) {
            a.absorbRemaining = Math.max(a.absorbRemaining, 0) + amount;
            a.absorbMax = Math.max(a.absorbMax, a.absorbRemaining);
            target.auras.markDirty();
        }
        return a;
    }

    // ================================================================== auras

    public AuraInstance applyAura(EffectContext ctx, UnitState target, String auraId, int stacks, double durationOverride) {
        AuraDef def = Registry.aura(auraId);
        if (def == null || target == null) return null;
        if (target.isDead() && !def.persistsThroughDeath) return null;
        UnitState caster = ctx.caster != null ? ctx.caster : target;
        boolean hostileApply = def.harmful && caster != target && isHostile(caster, target);

        if (hostileApply && target.mods().hasRef(ModType.DAMAGE_IMMUNE, "ALL") && def.cc != null) return null;

        double duration = durationOverride > 0 ? durationOverride : def.duration;
        if (duration > 0) {
            ModContext mc = ModContext.aura(def.id, def.tags, def.school);
            duration = duration * caster.mods().product(ModType.DURATION_PCT, mc) + caster.mods().sum(ModType.DURATION_FLAT, mc);
        }

        if (def.cc != null && hostileApply) {
            if (target.mods().hasRef(ModType.CC_IMMUNE, def.cc.name())) {
                vfx("immune", caster, target, null);
                return null;
            }
            if (target.boss && def.cc != CcType.SLOW) {
                vfx("immune", caster, target, null);
                return null;
            }
            if (target.isPlayerLike() && def.cc.drCategory != null) {
                double mult = target.dr.apply(def.cc.drCategory, now, duration);
                if (mult <= 0) {
                    vfx("immune", caster, target, null);
                    return null;
                }
                duration *= mult;
            }
            if (target.isPlayerLike() && caster.master().isPlayerLike() && def.cc.isHardCc()) {
                duration = Math.min(duration, 8.0);
            }
        }

        AuraInstance existing = def.perCaster ? target.auras.get(def.id, caster) : target.auras.get(def.id);
        if (existing != null) {
            if (def.refreshOnApply && duration > 0) {
                double rem = existing.remaining(now);
                double carry = def.pandemic ? Math.min(rem, duration * 0.3) : 0;
                existing.duration = duration + carry;
                existing.expiresAt = now + existing.duration;
            }
            int before = existing.stacks;
            existing.stacks = Math.min(def.maxStacks, existing.stacks + Math.max(1, stacks) * def.stacksPerApply);
            if (existing.stacks != before) target.invalidateMods();
            target.auras.markDirty();
            for (CombatListener l : listeners) l.onAuraApplied(target, existing);
            return existing;
        }

        AuraInstance a = new AuraInstance(def, target, caster);
        a.appliedAt = now;
        a.duration = duration;
        a.expiresAt = duration > 0 ? now + duration : Double.POSITIVE_INFINITY;
        a.stacks = Math.min(def.maxStacks, Math.max(1, stacks) * def.stacksPerApply);
        if (def.isPeriodic()) {
            double interval = def.tickInterval;
            if (def.hastedTicks) interval /= caster.stats().hasteMult();
            interval /= caster.mods().product(ModType.TICK_RATE, ModContext.aura(def.id, def.tags, def.school));
            a.tickInterval = Math.max(0.1, interval);
            a.nextTickAt = now + a.tickInterval;
        }
        if (def.form != null) {
            AuraInstance old = target.auras.formAura();
            if (old != null) removeAura(old, false);
        }
        target.auras.add(a);
        target.invalidateMods();
        if (def.stealth && target.body != null) target.body.setStealthed(true);
        if (def.cc != null) {
            if (def.cc.preventsCasting && target.cast != null) cancelCast(target, true);
            if (def.cc == CcType.SILENCE && target.cast != null && target.cast.ability.school.isMagic()) cancelCast(target, true);
            if (def.cc.preventsAttacks) target.queued = null;
            if (target.body != null) target.body.refreshMovement(target);
        } else if (target.body != null && !def.mods.isEmpty()) {
            target.body.refreshMovement(target);
        }
        if (hostileApply) enterCombat(caster, target);
        for (CombatListener l : listeners) l.onAuraApplied(target, a);
        if (def.onApply != null) {
            EffectContext c = new EffectContext(this, caster, target, null, null, a);
            def.onApply.apply(c);
        }
        if (def.tickOnApply && def.isPeriodic()) tickAura(a);
        if (def.vfx != null) vfx(def.vfx, caster, target, null);
        return a;
    }

    private void tickAuras(UnitState u) {
        List<AuraInstance> list = u.auras.all();
        if (list.isEmpty()) return;
        for (AuraInstance a : new ArrayList<>(list)) {
            if (a.removed) continue;
            if (a.def.isPeriodic()) {
                int guard = 0;
                while (!a.removed && now >= a.nextTickAt && a.nextTickAt <= a.expiresAt + 1e-6 && guard++ < 20) {
                    a.nextTickAt += a.tickInterval;
                    tickAura(a);
                }
            }
            if (!a.removed && now >= a.expiresAt) {
                removeAura(a, true);
            }
        }
    }

    private void tickAura(AuraInstance a) {
        if (a.holder.isDead() && !a.def.persistsThroughDeath) return;
        EffectContext c = new EffectContext(this, a.caster, a.holder, null, null, a);
        c.periodic = true;
        c.scale = a.def.modsPerStack ? a.stacks : 1;
        if (!a.def.modsPerStack) c.scale = 1;
        c.scale = a.stacks;
        try {
            a.def.tickEffect.apply(c);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void removeAura(AuraInstance a, boolean expired) {
        if (a.removed) return;
        a.removed = true;
        UnitState u = a.holder;
        u.auras.remove(a);
        u.invalidateMods();
        if (a.def.stealth && u.body != null && !u.auras.isStealthed()) u.body.setStealthed(false);
        if (a.def.cc != null) {
            if (a.def.cc.drCategory != null) u.dr.ccEnded(a.def.cc.drCategory, now);
            if (u.body != null) u.body.refreshMovement(u);
        } else if (u.body != null && !a.def.mods.isEmpty()) {
            u.body.refreshMovement(u);
        }
        for (CombatListener l : listeners) l.onAuraRemoved(u, a, expired);
        Effect e = expired ? a.def.onExpire : a.def.onRemove;
        if (e != null) {
            EffectContext c = new EffectContext(this, a.caster, u, null, null, a);
            c.scale = a.stacks;
            e.apply(c);
        }
    }

    /** Removes an aura by id (optionally only the one from a specific caster). */
    public boolean removeAura(UnitState u, String auraId, UnitState caster) {
        boolean any = false;
        for (AuraInstance a : new ArrayList<>(u.auras.all())) {
            if (!a.def.id.equals(auraId) || a.removed) continue;
            if (caster != null && a.caster != caster) continue;
            removeAura(a, false);
            any = true;
        }
        return any;
    }

    public void removeStacks(UnitState u, String auraId, int count) {
        AuraInstance a = u.auras.get(auraId);
        if (a == null) return;
        a.stacks -= count;
        if (a.stacks <= 0) removeAura(a, false);
        else {
            u.invalidateMods();
            u.auras.markDirty();
        }
    }

    public void breakStealth(UnitState u) {
        for (AuraInstance a : new ArrayList<>(u.auras.all())) if (a.def.stealth) removeAura(a, false);
    }

    private void breakCcOnDamage(UnitState target, double amount) {
        for (AuraInstance a : new ArrayList<>(target.auras.all())) {
            if (a.removed) continue;
            if (a.def.breakOnAnyDamage) {
                removeAura(a, false);
            } else if (a.def.breakOnDamageFraction > 0) {
                double acc = a.getData("dmg") + amount;
                a.setData("dmg", acc);
                if (acc >= a.def.breakOnDamageFraction * target.maxHealth) removeAura(a, false);
            }
        }
    }

    // ================================================================== procs

    void fireTriggers(UnitState holder, TriggerType type, UnitState other, Ability ability, AuraInstance sourceAura, double amount) {
        if (holder == null || holder.auras.size() == 0) return;
        for (AuraInstance a : new ArrayList<>(holder.auras.all())) {
            if (a.removed || a.def.triggers.isEmpty()) continue;
            for (int i = 0; i < a.def.triggers.size(); i++) {
                Trigger t = a.def.triggers.get(i);
                if (t.type() != type) continue;
                if (ability != null) {
                    if (!t.filter().matches(ability)) continue;
                } else if (sourceAura != null) {
                    if (!t.filter().matches(null, sourceAura.def.tags, sourceAura.def.school, sourceAura.def.id)) continue;
                } else if (t.filter().kind != com.wowcraft.core.mod.ModFilter.Kind.ANY) {
                    continue;
                }
                if (sourceAura == a) continue;
                if (now - a.triggerLastFired[i] < t.icd()) continue;
                if (t.chance() < 1.0 && !rng.chance(t.chance())) continue;
                a.triggerLastFired[i] = now;
                EffectContext c = new EffectContext(this, holder, t.onSelf() ? holder : (other != null ? other : holder), null, ability, a);
                c.triggerAmount = amount;
                c.scale = 1.0;
                try {
                    t.effect().apply(c);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                if (t.consume()) removeStacks(holder, a.def.id, 1);
                if (a.removed) break;
            }
        }
    }

    private void runTrigger(AuraInstance a, int idx, Trigger t, UnitState other) {
        if (now - a.triggerLastFired[idx] < t.icd()) return;
        a.triggerLastFired[idx] = now;
        EffectContext c = new EffectContext(this, a.holder, t.onSelf() || other == null ? a.holder : other, null, null, a);
        t.effect().apply(c);
        if (t.consume()) removeStacks(a.holder, a.def.id, 1);
    }

    // ================================================================== control & utility

    public void energize(UnitState u, ResourceType t, double amount) {
        if (u == null || !u.resources.has(t) || amount == 0) return;
        if (amount > 0) amount *= 1.0;
        u.resources.add(t, amount);
        u.markDirty(UnitState.DIRTY_RESOURCES);
    }

    public void adjustCooldown(UnitState u, String abilityId, double delta) {
        u.cooldowns.adjust(abilityId, delta, now);
    }

    public void resetCooldown(UnitState u, String abilityId) {
        u.cooldowns.reset(abilityId);
    }

    public boolean interrupt(UnitState by, UnitState target, double lockout) {
        if (target.cast == null || !target.cast.interruptible) return false;
        Ability a = target.cast.ability;
        if (target.mods().hasRef(ModType.CC_IMMUNE, "INTERRUPT")) return false;
        cancelCast(target, true);
        target.lockouts.put(a.school, now + lockout);
        if (a.school == School.PHYSICAL) target.lockouts.put(School.PHYSICAL, now + lockout * 0.5);
        target.markDirty(UnitState.DIRTY_CAST);
        for (CombatListener l : listeners) l.onInterrupt(by, target, a);
        vfx("interrupt", by, target, null);
        return true;
    }

    public int dispel(UnitState by, UnitState target, Set<DispelType> types, int count) {
        boolean friendlyTarget = isFriendly(by, target);
        int removed = 0;
        for (AuraInstance a : new ArrayList<>(target.auras.all())) {
            if (removed >= count) break;
            if (a.removed || a.def.passive || a.def.hidden) continue;
            if (!types.contains(a.def.dispel)) continue;
            if (friendlyTarget != a.def.harmful) continue;
            removeAura(a, false);
            removed++;
        }
        if (removed > 0) vfx("dispel", by, target, null);
        return removed;
    }

    public void taunt(UnitState by, UnitState target, double duration) {
        if (target.threat == null) return;
        target.threat.taunt(by, now, duration);
        enterCombat(by, target);
        vfx("taunt", by, target, null);
    }

    public void moveTo(UnitState u, Vec3 dest, boolean breakRoots) {
        if (u.body == null) return;
        if (u.boss) return;
        Vec3 from = u.position();
        Vec3 safe = world.safeDestination(u.worldKey(), from, dest);
        if (safe == null) return;
        u.body.teleport(safe);
        if (breakRoots) {
            for (AuraInstance a : new ArrayList<>(u.auras.all())) if (a.def.cc == CcType.ROOT) removeAura(a, false);
        }
        vfx("move", u, null, safe);
    }

    public void knockback(Vec3 origin, UnitState target, double strength, double up) {
        if (target.body == null || target.boss || target.mods().hasRef(ModType.CC_IMMUNE, "KNOCKBACK")) return;
        Vec3 dir = target.position().sub(origin).horizontal().normalize();
        if (dir.lengthSq() < 1e-6) dir = Vec3.fromYaw(target.yaw()).mul(-1);
        target.body.setVelocity(new Vec3(dir.x() * strength, up, dir.z() * strength));
    }

    public void pushUnit(UnitState u, Vec3 velocity) {
        if (u.body != null) u.body.setVelocity(velocity);
    }

    public UnitState summon(UnitState owner, String templateId, double duration, int index) {
        double angle = owner.yaw() + 140 + index * 40;
        Vec3 pos = owner.position().add(Vec3.fromYaw(angle).mul(1.8));
        UnitState s = world.spawnNpc(owner.worldKey(), pos, owner.yaw(), templateId, owner, owner.level);
        if (s == null) return null;
        s.owner = owner;
        s.team = owner.team;
        s.groupId = owner.groupId;
        s.instanceId = owner.instanceId;
        if (!owner.pets.contains(s)) owner.pets.add(s);
        if (duration > 0) s.despawnAt = now + duration;
        if (owner.target != null && isHostile(owner, owner.target)) s.target = owner.target;
        for (CombatListener l : listeners) l.onSummon(owner, s);
        return s;
    }

    public GroundArea addArea(EffectContext ctx, GroundArea.Def def) {
        Vec3 c = def.atCaster() ? ctx.caster.position() : ctx.targetPoint();
        double r = radius(ctx, def.radius());
        GroundArea area = new GroundArea(def, ctx.caster, ctx.ability, c, ctx.caster.worldKey(), now, r);
        areas.add(area);
        for (CombatListener l : listeners) l.onAreaCreated(area);
        return area;
    }

    public List<GroundArea> areas() {
        return areas;
    }

    private void tickAreas() {
        if (areas.isEmpty()) return;
        Iterator<GroundArea> it = areas.iterator();
        List<GroundArea> expired = new ArrayList<>();
        List<GroundArea> active = new ArrayList<>(areas);
        for (GroundArea a : active) {
            if (now >= a.expiresAt || (a.caster != null && !units.containsKey(a.caster.id) && a.caster.kind != UnitKind.NPC)) {
                expired.add(a);
                continue;
            }
            if (a.def.followsCaster() && a.caster != null) a.center = a.caster.position();
            if (now >= a.nextTick) {
                a.nextTick = now + a.def.tickInterval();
                if (a.def.tickEffect() == null) continue;
                EffectContext ctx = new EffectContext(this, a.caster, null, a.center, a.ability, null);
                ctx.periodic = true;
                if (a.def.oncePerTick()) {
                    a.def.tickEffect().apply(ctx);
                    continue;
                }
                List<UnitState> targets = new ArrayList<>();
                if (a.def.affectsEnemies()) targets.addAll(enemiesAround(a.caster, a.center, a.radius));
                if (a.def.affectsAllies()) targets.addAll(alliesAround(a.caster, a.center, a.radius));
                int n = targets.size();
                for (UnitState t : targets) {
                    EffectContext c = ctx.withTarget(t);
                    c.aoeTargets = n;
                    if (n > Formulas.AOE_SOFT_CAP) c.scale *= Math.sqrt(Formulas.AOE_SOFT_CAP / n);
                    a.def.tickEffect().apply(c);
                }
            }
        }
        while (it.hasNext()) {
            GroundArea a = it.next();
            if (expired.contains(a)) {
                it.remove();
                for (CombatListener l : listeners) l.onAreaRemoved(a);
            }
        }
    }

    public void removeAreasOf(UnitState caster) {
        Iterator<GroundArea> it = areas.iterator();
        while (it.hasNext()) {
            GroundArea a = it.next();
            if (a.caster == caster) {
                it.remove();
                for (CombatListener l : listeners) l.onAreaRemoved(a);
            }
        }
    }

    public void launchProjectile(EffectContext ctx, double speed, List<Effect> onHit) {
        UnitState t = ctx.target;
        double dist = t != null ? ctx.caster.position().distance(t.position()) : ctx.caster.position().distance(ctx.targetPoint());
        double delay = speed <= 0 ? 0 : dist / speed;
        vfx("projectile:" + (ctx.ability != null && ctx.ability.vfx != null ? ctx.ability.vfx : ctx.school().name().toLowerCase()),
                ctx.caster, t, ctx.point, speed);
        EffectContext c = ctx.copy();
        Runnable run = () -> {
            if (t != null && (t.isDead() || !units.containsKey(t.id))) return;
            for (Effect e : onHit) e.apply(c);
        };
        if (delay < 0.05) run.run();
        else schedule(delay, run);
    }

    public void resurrect(UnitState by, UnitState target, double healthFraction) {
        if (!target.isDead()) return;
        target.dead = false;
        target.ghost = false;
        target.stats();
        double old = target.health;
        target.health = Math.max(1, target.maxHealth * healthFraction);
        target.markDirty(UnitState.DIRTY_HEALTH);
        if (target.body != null) target.body.applyHealth(target, old, null);
        for (CombatListener l : listeners) l.onResurrect(target, by);
        vfx("resurrect", by, target, null);
    }

    public void vfx(String key, UnitState source, UnitState target, Vec3 point) {
        vfx(key, source, target, point, 0);
    }

    public void vfx(String key, UnitState source, UnitState target, Vec3 point, double param) {
        for (CombatListener l : listeners) l.onVfx(key, source, target, point, param);
    }

    // ================================================================== combat / death

    public void enterCombat(UnitState a, UnitState b) {
        if (a == null || b == null || a == b) return;
        if (!isHostile(a, b)) return;
        a.lastCombatAt = now;
        b.lastCombatAt = now;
        a.engaged.add(b);
        b.engaged.add(a);
        if (b.threat != null && !b.threat.raw().containsKey(a) && a.isAlive()) b.threat.add(a, 0.01);
        if (a.threat != null && !a.threat.raw().containsKey(b) && b.isAlive()) a.threat.add(b, 0.01);
        UnitState am = a.master();
        if (am != a) {
            am.lastCombatAt = now;
            am.engaged.add(b);
            b.engaged.add(am);
        }
    }

    /** Puts a unit on an NPC's threat list without damage (body pull, pack aggro). */
    public void aggro(UnitState npc, UnitState target, double amount) {
        if (npc.threat == null || target == null || target.isDead()) return;
        npc.threat.add(target, Math.max(0.01, amount));
        enterCombat(npc, target);
    }

    public void die(UnitState u, UnitState killer) {
        if (u.dead) return;
        u.dead = true;
        u.deathTime = now;
        u.health = 0;
        u.autoAttack = false;
        u.queued = null;
        if (u.cast != null) cancelCast(u, false);
        for (AuraInstance a : new ArrayList<>(u.auras.all())) if (!a.def.persistsThroughDeath) removeAura(a, false);
        if (u.threat != null) u.threat.clear();
        for (UnitState o : units.values()) {
            if (o.threat != null) o.threat.remove(u);
        }
        u.markDirty(0xFFFF);
        for (CombatListener l : listeners) l.onDeath(u, killer);
        if (u.body != null) u.body.kill(u, killer);
        removeAreasOf(u);
        for (UnitState p : new ArrayList<>(u.pets)) {
            if (p.kind == UnitKind.TOTEM || p.despawnAt < Double.POSITIVE_INFINITY && u.isNpcLike()) {
                if (p.body != null) p.body.despawn();
                remove(p);
            }
        }
    }

    /** Marks a dead player as a ghost (dead inside an instance, waiting for resurrection). */
    public void makeGhost(UnitState u) {
        u.dead = false;
        u.ghost = true;
        u.markDirty(UnitState.DIRTY_HEALTH);
    }

    // ================================================================== target helpers

    public void setTarget(UnitState u, UnitState target) {
        if (u.target != target) {
            u.target = target;
            u.markDirty(UnitState.DIRTY_TARGET);
            if (u.isPlayerLike() && u.autoAttack && (target == null || !isHostile(u, target))) u.autoAttack = false;
        }
    }

    public void startAutoAttack(UnitState u, UnitState target) {
        if (target == null || !isHostile(u, target)) return;
        setTarget(u, target);
        u.autoAttack = true;
        enterCombat(u, target);
    }

    public void forEachListener(Consumer<CombatListener> c) {
        for (CombatListener l : listeners) c.accept(l);
    }
}
