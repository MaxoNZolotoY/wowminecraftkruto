package com.wowcraft.core.game;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.bot.RotationRunner;
import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.CastState;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.Cooldowns;
import com.wowcraft.core.combat.GroundArea;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.mythic.MythicScore;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.BossTimer;
import com.wowcraft.core.npc.Encounter;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.npc.Telegraph;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.resource.ResourcePool;
import com.wowcraft.core.resource.ResourceType;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.stat.Stat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds the state messages sent to clients. */
final class Sync {
    private Sync() {
    }

    static final double SELF_INTERVAL = 0.1, UNITS_INTERVAL = 0.2, SUGGEST_INTERVAL = 0.4;

    static void syncPlayer(GameServer server, PlayerSession s, double now) {
        if (s.characterDirty && now - s.lastCharacterSync > 0.5) {
            s.characterDirty = false;
            s.lastCharacterSync = now;
            server.send(s, character(server, s));
        }
        if (now - s.lastSelfSync >= SELF_INTERVAL) {
            s.lastSelfSync = now;
            server.send(s, self(server, s, now));
        }
        if (now - s.lastUnitsSync >= UNITS_INTERVAL) {
            s.lastUnitsSync = now;
            server.send(s, units(server, s, now));
        }
    }

    // ------------------------------------------------------------------ character

    static S2C.Character character(GameServer server, PlayerSession s) {
        PlayerProfile p = s.profile;
        UnitState u = s.unit;
        S2C.Character c = new S2C.Character();
        c.classChosen = p.classChosen && p.spec() != null;
        c.wowClass = p.wowClass;
        c.spec = p.spec;
        c.level = p.level;
        c.xp = p.xp;
        c.xpNext = Rewards.xpToNext(p.level);
        Spec spec = p.spec();
        if (spec != null && u != null) {
            for (String id : u.knownAbilities) {
                Ability a = Registry.ability(id);
                if (a == null || a.passive || a.hidden) continue;
                c.abilities.add(id);
            }
            c.bar = p.barFor(spec).clone();
            c.talents.addAll(p.talentsFor(spec));
        }
        c.currencies.putAll(p.currencies);
        if (p.keystone != null) {
            c.keystoneDungeon = p.keystone.dungeonId;
            c.keystoneLevel = p.keystone.level;
        }
        c.mythicRating = MythicScore.totalRating(p);
        Equipment eq = server.equipment(s);
        c.itemLevel = eq != null ? Math.round(eq.averageItemLevel() * 10) / 10.0 : 0;
        if (eq != null) for (var en : eq.all().entrySet()) c.equipment.put(en.getKey().name(), en.getValue());
        c.ghost = s.ghost;
        c.honorLevel = p.honorLevel;
        if (u != null) {
            Object t1 = u.tags.get("trinket_1"), t2 = u.tags.get("trinket_2");
            c.trinket1 = t1 instanceof String str ? str : null;
            c.trinket2 = t2 instanceof String str ? str : null;
        }
        for (var en : p.pvp.entrySet()) c.pvpRatings.put(en.getKey(), en.getValue().rating);
        return c;
    }

    // ------------------------------------------------------------------ self

    static S2C.Self self(GameServer server, PlayerSession s, double now) {
        CombatEngine engine = server.engine;
        UnitState u = s.unit;
        S2C.Self m = new S2C.Self();
        m.entityId = u.id;
        m.health = Math.round(u.health());
        m.maxHealth = Math.round(u.maxHealth());
        m.absorb = Math.round(absorb(u));
        ResourcePool pool = u.resources();
        for (ResourceType t : pool.active()) {
            if (t == ResourceType.RUNES) {
                m.runes = new float[ResourcePool.RUNE_COUNT];
                for (int i = 0; i < ResourcePool.RUNE_COUNT; i++) m.runes[i] = (float) pool.runeProgress(i);
            }
            m.resources.put(t.name(), Math.floor(pool.get(t) * 10) / 10.0);
            m.resourceMax.put(t.name(), pool.max(t));
        }
        m.inCombat = u.inCombat();
        m.gcdRemaining = (float) Math.max(0, u.gcdEnd() - now);
        m.gcdDuration = (float) u.gcdDuration();
        m.cast = cast(u.cast(), now);
        for (AuraInstance a : u.auras().all()) {
            if (a.removed || a.def.hidden) continue;
            if (a.def.passive && !a.def.absorb && a.stacks <= 1 && a.def.maxStacks <= 1) continue;
            m.auras.add(aura(a, now, u));
            if (m.auras.size() >= 40) break;
        }
        Spec spec = u.spec;
        if (spec != null) {
            String[] bar = s.profile.barFor(spec);
            m.resolvedBar = new String[bar.length];
            m.usable = new boolean[bar.length];
            m.inRange = new boolean[bar.length];
            UnitState target = u.target();
            Set<String> sent = new LinkedHashSet<>();
            for (int i = 0; i < bar.length; i++) {
                String id = bar[i];
                if (id == null) continue;
                String resolved = engine.resolveAbility(u, id);
                m.resolvedBar[i] = resolved;
                CastResult r = engine.check(u, resolved, target);
                m.usable[i] = r == CastResult.OK || r == CastResult.GCD || r == CastResult.BUSY || r == CastResult.OUT_OF_RANGE
                        || r == CastResult.NO_TARGET || r == CastResult.NOT_FACING || r == CastResult.NO_LINE_OF_SIGHT;
                m.inRange[i] = r != CastResult.OUT_OF_RANGE && r != CastResult.TOO_CLOSE;
                sent.add(resolved);
            }
            for (String extra : new String[]{"healing_potion", "damage_potion", "healthstone", "gladiators_medallion"}) sent.add(extra);
            Object t1 = u.tags.get("trinket_1"), t2 = u.tags.get("trinket_2");
            if (t1 instanceof String str) sent.add(str);
            if (t2 instanceof String str) sent.add(str);
            for (String id : sent) {
                Ability a = Registry.ability(id);
                if (a == null) continue;
                Cooldowns.Entry e = u.cooldowns().get(id);
                if (e == null) continue;
                double rem = u.cooldowns().remaining(id, now);
                int max = engine.maxCharges(u, a);
                int charges = u.cooldowns().charges(id, max, now);
                if (rem <= 0 && charges >= max) continue;
                S2C.CooldownInfo ci = new S2C.CooldownInfo();
                ci.id = id;
                ci.remaining = (float) rem;
                ci.duration = (float) e.duration;
                ci.charges = charges;
                ci.maxCharges = max;
                m.cooldowns.add(ci);
            }
            if (now - s.lastSuggest >= SUGGEST_INTERVAL) {
                s.lastSuggest = now;
                s.suggested = suggest(server, s, bar);
            }
            m.suggested = s.suggested;
        }
        m.targetId = u.target() != null ? u.target().id : -1;
        m.ghost = s.ghost;
        m.speedPct = u.stats().speedPct;
        m.form = u.auras().currentForm();
        return m;
    }

    /** Assisted highlight: the ability the spec's bot rotation would use now. */
    private static int suggest(GameServer server, PlayerSession s, String[] bar) {
        UnitState u = s.unit;
        if (u.spec == null || u.isDead()) return -1;
        ClassKit kit = Content.kit(u.spec.wowClass);
        var rotation = kit.rotations.get(u.spec);
        if (rotation == null) return -1;
        UnitState t = u.target();
        CombatEngine engine = server.engine;
        boolean hostileTarget = t != null && t.isAlive() && engine.isHostile(u, t);
        List<UnitState> enemies = engine.enemiesAround(u, hostileTarget ? t.position() : u.position(), 10);
        if (!hostileTarget && u.role() != com.wowcraft.core.spec.Role.HEALER) return -1;
        List<UnitState> allies = server.bots.alliesOf(u);
        try {
            RotationRunner.Decision d = RotationRunner.decide(engine, u, rotation, new RotationRunner.Situation(hostileTarget ? t : null, enemies, allies));
            if (d == null) return -1;
            for (int i = 0; i < bar.length; i++) {
                if (bar[i] == null) continue;
                if (bar[i].equals(d.abilityId()) || engine.resolveAbility(u, bar[i]).equals(d.abilityId())) return i;
            }
        } catch (RuntimeException ignored) {
        }
        return -1;
    }

    static double absorb(UnitState u) {
        double sum = 0;
        for (AuraInstance a : u.auras().all()) if (a.def.absorb && !a.removed) sum += a.absorbRemaining;
        return sum;
    }

    static S2C.CastInfo cast(CastState c, double now) {
        if (c == null) return null;
        S2C.CastInfo ci = new S2C.CastInfo();
        ci.ability = c.ability.id;
        ci.progress = (float) c.progress(now);
        ci.total = (float) (c.end - c.start);
        ci.channel = c.type == com.wowcraft.core.spell.CastType.CHANNEL;
        ci.empower = c.type == com.wowcraft.core.spell.CastType.EMPOWER;
        ci.stage = ci.empower ? c.empowerStage(now) : 0;
        ci.interruptible = c.interruptible;
        return ci;
    }

    static S2C.AuraInfo aura(AuraInstance a, double now, UnitState viewer) {
        S2C.AuraInfo ai = new S2C.AuraInfo();
        ai.id = a.def.id;
        ai.stacks = a.stacks;
        ai.remaining = a.isPermanent() ? -1 : (float) a.remaining(now);
        ai.duration = (float) a.duration;
        ai.mine = a.caster != null && viewer != null && a.caster.master() == viewer.master();
        ai.absorb = (int) Math.round(a.absorbRemaining);
        return ai;
    }

    // ------------------------------------------------------------------ units

    static S2C.Units units(GameServer server, PlayerSession s, double now) {
        CombatEngine engine = server.engine;
        UnitState me = s.unit;
        S2C.Units m = new S2C.Units();
        Set<UnitState> include = new LinkedHashSet<>();
        if (me.target() != null) include.add(me.target());
        if (me.target() != null && me.target().target() != null) include.add(me.target().target());
        // party / raid
        for (UnitState ally : server.groups.unitsOfGroup(me)) {
            include.add(ally);
            m.party.add(ally.id);
            for (UnitState pet : ally.livingPets()) {
                if (pet.kind == UnitKind.PET && pet.despawnAt == Double.POSITIVE_INFINITY && ally == me) {
                    include.add(pet);
                    m.party.add(pet.id);
                }
            }
        }
        // bosses of the active encounter
        Encounter enc = server.encounterFor(me);
        if (enc != null) {
            for (UnitState b : enc.bosses) {
                include.add(b);
                m.bosses.add(b.id);
            }
        }
        // PvP: enemy team frames
        for (UnitState enemy : server.pvp.enemyFrames(me)) include.add(enemy);
        // nameplates: nearby units in combat or hostile
        int plates = 0;
        double r2 = 32 * 32;
        for (UnitState u : engine.units()) {
            if (plates >= 24) break;
            if (u == me || include.contains(u)) continue;
            if (u.kind == UnitKind.TOTEM) continue;
            if (!engine.sameWorld(me, u)) continue;
            if (u.position().distanceSq(me.position()) > r2) continue;
            if (u.kind == UnitKind.VANILLA && !u.inCombat()) continue;
            if (u.auras().isStealthed() && engine.isHostile(me, u)) continue;
            include.add(u);
            plates++;
        }
        for (UnitState u : include) {
            if (u.isDead() && u.kind != UnitKind.PLAYER && u.kind != UnitKind.BOT && !m.bosses.contains(u.id)) continue;
            m.units.add(unit(engine, u, me, now));
        }
        return m;
    }

    static S2C.Unit unit(CombatEngine engine, UnitState u, UnitState viewer, double now) {
        S2C.Unit f = new S2C.Unit();
        f.entityId = u.id;
        f.uuid = u.uuid != null && u.isPlayer() ? u.uuid.toString() : null;
        NpcTemplate t = u.templateId != null ? NpcRegistry.get(u.templateId) : null;
        f.name = u.name;
        f.level = u.level;
        boolean hostile = engine.isHostile(viewer, u);
        f.hostile = hostile;
        if (u.wowClass != null) f.color = u.wowClass.color;
        else if (hostile) f.color = u.inCombat() ? 0xFFFF3030 : 0xFFFF6060;
        else if ("friendly".equals(u.master().team)) f.color = 0xFF40FF40;
        else f.color = 0xFF60A0FF;
        f.rank = t != null ? t.rank.name() : (u.boss ? "BOSS" : u.elite ? "ELITE" : null);
        f.role = u.role() != null ? u.role().name() : null;
        f.wowClass = u.wowClass != null ? u.wowClass.id() : null;
        f.health = Math.round(u.health());
        f.maxHealth = Math.round(u.maxHealth());
        f.absorb = Math.round(absorb(u));
        if (u.spec != null) {
            ResourceType rt = u.spec.primaryResource();
            if (rt != null && u.resources().has(rt)) {
                f.power = rt.name();
                f.powerCur = Math.floor(u.resources().get(rt));
                f.powerMax = u.resources().max(rt);
            }
        }
        f.cast = cast(u.cast(), now);
        int n = 0;
        for (AuraInstance a : u.auras().all()) {
            if (a.removed || a.def.hidden || a.def.passive) continue;
            // show debuffs from the viewer, all buffs on friendly, CC / dispellable on everything
            boolean mine = a.caster != null && a.caster.master() == viewer.master();
            boolean show = mine || a.def.cc != null || (!hostile && a.def.harmful) || (hostile && !a.def.harmful && a.def.dispel != null)
                    || u.boss && a.def.harmful == false || !a.def.harmful && !hostile;
            if (!show) continue;
            f.auras.add(aura(a, now, viewer));
            if (++n >= 16) break;
        }
        f.dead = u.isDead();
        f.inCombat = u.inCombat();
        if (u.threat() != null && !u.threat().isEmpty()) {
            double top = u.threat().topThreat();
            f.threat = top <= 0 ? 0 : (float) Math.min(1.0, u.threat().get(viewer) / top);
        }
        f.targetId = u.target() != null ? u.target().id : -1;
        f.boss = u.boss;
        return f;
    }

    // ------------------------------------------------------------------ world effects

    static S2C.Telegraph telegraph(Telegraph t, boolean added, double now) {
        S2C.Telegraph m = new S2C.Telegraph();
        m.id = t.id;
        m.remove = !added;
        m.shape = t.shape.name();
        m.x = t.center.x();
        m.y = t.center.y();
        m.z = t.center.z();
        m.radius = t.radius;
        m.inner = t.innerRadius;
        m.angle = t.angle;
        m.length = t.length;
        m.width = t.width;
        m.yaw = t.yaw;
        m.duration = (float) Math.max(0, t.resolveAt - now);
        m.color = t.color;
        m.followId = t.follow != null && now < t.followUntil ? t.follow.id : -1;
        m.soak = t.soakRequired > 0 || t.split;
        return m;
    }

    static S2C.Area area(GroundArea a, boolean removed, double now) {
        S2C.Area m = new S2C.Area();
        m.id = a.id;
        m.remove = removed;
        m.x = a.center.x();
        m.y = a.center.y();
        m.z = a.center.z();
        m.radius = a.radius;
        m.duration = (float) Math.max(0, a.expiresAt - now);
        m.color = a.def.color();
        m.key = a.def.vfx();
        m.hostile = a.def.affectsEnemies() || a.def.oncePerTick();
        m.followId = a.def.followsCaster() && a.caster != null ? a.caster.id : -1;
        return m;
    }

    static S2C.BossTimers timers(Encounter e, double now) {
        S2C.BossTimers m = new S2C.BossTimers();
        if (e.finished) return m;
        for (BossTimer b : e.timers) {
            S2C.TimerBar bar = new S2C.TimerBar();
            bar.id = b.id;
            bar.label = new S2C.Text(b.label.en(), b.label.ru());
            bar.remaining = (float) Math.max(0, b.end - now);
            bar.total = (float) (b.end - b.start);
            bar.color = b.color;
            m.bars.add(bar);
        }
        return m;
    }

    static S2C.Stats stats(UnitState u) {
        S2C.Stats m = new S2C.Stats();
        var d = u.stats();
        m.values.put("health", Math.floor(u.maxHealth()));
        m.values.put("strength", Math.floor(d.strength));
        m.values.put("agility", Math.floor(d.agility));
        m.values.put("intellect", Math.floor(d.intellect));
        m.values.put("stamina", Math.floor(d.stamina));
        m.values.put("attack_power", Math.floor(d.attackPower));
        m.values.put("spell_power", Math.floor(d.spellPower));
        m.values.put("crit", round1(d.critPct));
        m.values.put("haste", round1(d.hastePct));
        m.values.put("mastery", round1(d.masteryPct));
        m.values.put("versatility", round1(d.versPct));
        m.values.put("leech", round1(d.leechPct));
        m.values.put("avoidance", round1(d.avoidancePct));
        m.values.put("speed", round1(d.speedPct));
        m.values.put("armor", Math.floor(d.armor));
        m.values.put("crit_rating", Math.floor(u.baseStats.get(Stat.CRIT) + u.gearStats.get(Stat.CRIT)));
        m.values.put("haste_rating", Math.floor(u.baseStats.get(Stat.HASTE) + u.gearStats.get(Stat.HASTE)));
        m.values.put("mastery_rating", Math.floor(u.baseStats.get(Stat.MASTERY) + u.gearStats.get(Stat.MASTERY)));
        m.values.put("versatility_rating", Math.floor(u.baseStats.get(Stat.VERSATILITY) + u.gearStats.get(Stat.VERSATILITY)));
        return m;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static List<S2C.Text> texts(List<com.wowcraft.core.util.L10n> l) {
        List<S2C.Text> out = new ArrayList<>();
        for (var t : l) out.add(new S2C.Text(t.en(), t.ru()));
        return out;
    }
}
