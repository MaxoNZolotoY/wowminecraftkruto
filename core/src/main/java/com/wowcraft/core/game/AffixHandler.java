package com.wowcraft.core.game;

import com.wowcraft.core.aura.AuraInstance;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.GroundArea;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.mythic.Affix;
import com.wowcraft.core.mythic.MythicRun;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.npc.Telegraph;
import com.wowcraft.core.spell.Effect;
import com.wowcraft.core.spell.EffectContext;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Implements Mythic+ affixes for one run. */
public final class AffixHandler {
    private final GameServer server;
    private final InstanceRun run;
    private final MythicRun m;
    private final CombatEngine engine;
    private final List<Telegraph> telegraphs = new ArrayList<>();
    private double nextVolcanic, nextStorming, nextQuaking, nextExplosive, nextEntangling, nextAfflicted, nextIncorporeal, nextPulsar,
            nextAscendant, nextGrievous;

    AffixHandler(GameServer server, InstanceRun run) {
        this.server = server;
        this.run = run;
        this.m = run.mythic;
        this.engine = server.engine();
        double now = engine.now();
        nextVolcanic = now + 10;
        nextStorming = now + 15;
        nextQuaking = now + 20;
        nextExplosive = now + 12;
        nextEntangling = now + 18;
        nextAfflicted = now + 25;
        nextIncorporeal = now + 30;
        nextPulsar = now + 30;
        nextAscendant = now + 40;
        nextGrievous = now + 3;
    }

    /** Spawn-time multipliers (health, damage) for an NPC. */
    double[] multipliers(NpcTemplate t) {
        double hp = m.scaling(), dmg = m.scaling();
        boolean boss = t.rank == NpcRank.BOSS;
        if (m.has(Affix.FORTIFIED) && !boss) {
            hp *= 1.2;
            dmg *= 1.3;
        }
        if (m.has(Affix.TYRANNICAL) && boss) {
            hp *= 1.3;
            dmg *= 1.15;
        }
        return new double[]{hp, dmg};
    }

    private boolean inCombat() {
        for (UnitState n : run.npcs) if (n.isAlive() && n.inCombat() && n.threat() != null && !n.threat().isEmpty()) return true;
        return false;
    }

    private List<UnitState> players() {
        List<UnitState> out = new ArrayList<>();
        for (UnitState u : server.participants(run)) if (u.isAlive()) out.add(u);
        return out;
    }

    private UnitState anyCombatNpc() {
        for (UnitState n : run.npcs) if (n.isAlive() && n.inCombat()) return n;
        return null;
    }

    void tick() {
        if (!m.started() || m.completed) return;
        double now = engine.now();
        boolean combat = inCombat();
        UnitState src = anyCombatNpc();
        if (combat && src != null) {
            if (m.has(Affix.VOLCANIC) && now >= nextVolcanic) {
                nextVolcanic = now + 8;
                for (UnitState p : players()) {
                    if (engine.distance(src, p) < 10 || engine.rng().chance(0.5)) continue;
                    add(Telegraph.circle(src, p.position(), 2.5, now, 1.5, 0xFFFF6020, Effects.rawDamage(School.FIRE, Scaling.targetHp(0.25))));
                }
            }
            if (m.has(Affix.STORMING) && now >= nextStorming) {
                nextStorming = now + 20;
                UnitState p = random(players());
                if (p != null) {
                    Vec3 c = p.position().add(engine.rng().range(-4, 4), 0, engine.rng().range(-4, 4));
                    for (int i = 0; i < 4; i++) {
                        Vec3 pos = c.add(Vec3.fromYaw(i * 90).mul(i * 1.5));
                        add(Telegraph.circle(src, pos, 2.5, now + i * 0.8, 2.0 + i * 0.8, 0xFF80C0FF,
                                all(Effects.rawDamage(School.NATURE, Scaling.targetHp(0.15)), Effects.knockback(0.8))));
                    }
                }
            }
            if (m.has(Affix.EXPLOSIVE) && now >= nextExplosive) {
                nextExplosive = now + 10;
                Vec3 pos = src.position().add(engine.rng().range(-5, 5), 0.5, engine.rng().range(-5, 5));
                UnitState orb = server.spawnInRun(run, "explosive_orb", pos, 0);
                if (orb != null) {
                    UnitState p = random(players());
                    if (p != null) engine.aggro(orb, p, 1);
                }
            }
            if (m.has(Affix.ENTANGLING) && now >= nextEntangling) {
                nextEntangling = now + 25;
                UnitState p = random(players());
                if (p != null) add(Telegraph.circle(src, p.position(), 3, now, 2.5, 0xFF30A030, Effects.aura("entangled")));
            }
            if (m.has(Affix.AFFLICTED) && now >= nextAfflicted) {
                nextAfflicted = now + 30;
                List<UnitState> ps = players();
                for (int i = 0; i < Math.min(2, ps.size()); i++) {
                    UnitState p = ps.get(engine.rng().nextInt(ps.size()));
                    engine.applyAura(new EffectContext(engine, src, p, null, null, null), p, "afflicted_cry", 1, -1);
                }
                server.warnRun(run, L10n.of("Dispel the Afflicted!", "Рассейте страдание!"), 0xFFA569BD);
            }
            if (m.has(Affix.INCORPOREAL) && now >= nextIncorporeal) {
                nextIncorporeal = now + 45;
                for (int i = 0; i < 2; i++) {
                    Vec3 pos = src.position().add(engine.rng().range(-6, 6), 0.5, engine.rng().range(-6, 6));
                    UnitState being = server.spawnInRun(run, "incorporeal_being", pos, 0);
                    if (being != null) {
                        being.despawnAt = now + 20;
                        UnitState p = random(players());
                        if (p != null) engine.aggro(being, p, 1);
                    }
                }
                server.warnRun(run, L10n.of("Incorporeal Beings: interrupt or crowd control!", "Бесплотные существа: прерывайте или контролируйте!"), 0xFF85929E);
            }
            if (m.has(Affix.VOID_PULSAR) && now >= nextPulsar) {
                nextPulsar = now + 30;
                UnitState p = random(players());
                if (p != null) {
                    Vec3 c = p.position().add(engine.rng().range(-6, 6), 0, engine.rng().range(-6, 6));
                    Telegraph t = Telegraph.circle(src, c, 4, now, 6, 0xFF9B59B6, Effects.aura("void_pulsar_power"));
                    t.soak(1, Effects.rawDamage(School.SHADOW, Scaling.targetHp(0.2)));
                    add(t);
                    server.warnRun(run, L10n.of("Absorb the Void Pulsar!", "Поглотите пульсар Бездны!"), 0xFF9B59B6);
                }
            }
            if (m.has(Affix.VOID_ASCENDANT) && now >= nextAscendant) {
                nextAscendant = now + 40;
                for (int i = 0; i < 3; i++) {
                    Vec3 pos = src.position().add(engine.rng().range(-8, 8), 0.5, engine.rng().range(-8, 8));
                    UnitState em = server.spawnInRun(run, "void_emissary", pos, 0);
                    if (em != null) {
                        em.despawnAt = now + 12;
                        em.tags.put("ascendant", Boolean.TRUE);
                    }
                }
                server.warnRun(run, L10n.of("Destroy the Void Emissaries!", "Уничтожьте эмиссаров Бездны!"), 0xFF4A235A);
            }
        }
        if (m.has(Affix.QUAKING) && combat && now >= nextQuaking) {
            nextQuaking = now + 20;
            for (UnitState p : players()) {
                add(Telegraph.circle(p, p.position(), 5, now, 2.5, 0xFF935116, ctx -> {
                    if (ctx.target == null || ctx.target == ctx.caster) return;
                    ctx.engine.dealRawDamage(ctx, ctx.target, School.PHYSICAL, ctx.target.maxHealth() * 0.08);
                    ctx.engine.interrupt(ctx.caster, ctx.target, 2);
                }).following(p, now + 2.0));
            }
        }
        if (m.has(Affix.GRIEVOUS) && now >= nextGrievous) {
            nextGrievous = now + 3;
            for (UnitState p : players()) {
                if (p.healthFraction() < 0.9 && combat) {
                    engine.applyAura(new EffectContext(engine, p, p, null, null, null), p, "grievous_wound", 1, -1);
                } else if (p.healthFraction() >= 0.9) {
                    engine.removeAura(p, "grievous_wound", null);
                }
            }
        }
        // void emissaries that reach enemies empower them
        if (m.has(Affix.VOID_ASCENDANT)) {
            for (UnitState n : new ArrayList<>(run.npcs)) {
                if (!n.isAlive() || !n.tags.containsKey("ascendant")) continue;
                if (now >= n.despawnAt - 0.2) {
                    for (UnitState e : run.npcs) {
                        if (e.isAlive() && !e.tags.containsKey("ascendant") && engine.distance(n, e) < 15)
                            engine.applyAura(new EffectContext(engine, n, e, null, null, null), e, "void_empowered", 1, -1);
                    }
                }
            }
        }
        // telegraphs
        for (Telegraph t : new ArrayList<>(telegraphs)) {
            if (t.follow != null && now < t.followUntil && t.follow.isAlive()) t.center = t.follow.position();
            if (now >= t.resolveAt) {
                telegraphs.remove(t);
                server.sendTelegraph(run, t, false);
                List<UnitState> inside = new ArrayList<>();
                for (UnitState p : server.participants(run)) if (p.isAlive() && t.contains(p)) inside.add(p);
                if (t.soakRequired > 0 && inside.size() < t.soakRequired && t.onFail != null) {
                    for (UnitState p : players()) t.onFail.apply(new EffectContext(engine, t.source, p, t.center, null, null));
                }
                if (t.onHit != null) for (UnitState p : inside) t.onHit.apply(new EffectContext(engine, t.source, p, t.center, null, null));
            }
        }
    }

    void onNpcDeath(UnitState dead) {
        if (!m.started() || m.completed || dead.boss) return;
        NpcTemplate t = NpcRegistry.get(dead.templateId);
        if (t == null || t.rank == NpcRank.MINION) return;
        double now = engine.now();
        if (m.has(Affix.BOLSTERING)) {
            for (UnitState n : run.npcs) {
                if (n == dead || n.isDead() || n.boss || engine.distance(dead, n) > 30) continue;
                engine.applyAura(new EffectContext(engine, n, n, null, null, null), n, "bolstering", 1, -1);
                engine.vfx("bolster", dead, n, null);
            }
        }
        if (m.has(Affix.SANGUINE)) {
            EffectContext c = new EffectContext(engine, dead, null, dead.position(), null, null);
            engine.addArea(c, GroundArea.Def.scripted("sanguine", 4, 20, 1, ctx -> {
                for (UnitState n : run.npcs) {
                    if (n.isAlive() && n.position().distance(ctx.point) <= 4.5) ctx.engine.rawHeal(dead, n, n.maxHealth() * 0.05);
                }
                for (UnitState p : players()) {
                    if (p.position().distance(ctx.point) <= 4.5)
                        ctx.engine.dealRawDamage(new EffectContext(ctx.engine, dead, p, ctx.point, null, null), p, School.SHADOW, p.maxHealth() * 0.02);
                }
            }).color(0xFF8B0000));
        }
        if (m.has(Affix.BURSTING)) {
            for (UnitState p : players()) engine.applyAura(new EffectContext(engine, dead, p, null, null, null), p, "bursting", 1, -1);
        }
        if (m.has(Affix.SPITEFUL)) {
            UnitState shade = server.spawnInRun(run, "spiteful_shade", dead.position(), 0);
            if (shade != null) {
                shade.despawnAt = now + 15;
                UnitState p = random(players());
                if (p != null && shade.threat() != null) {
                    engine.aggro(shade, p, 1);
                    shade.threat().fixate(p, now, 15);
                }
            }
        }
    }

    /** Raging (enrage at 30%) and Necrotic (melee hits) react to damage. */
    void onDamage(HitResult hit) {
        if (!m.started() || m.completed) return;
        UnitState t = hit.target;
        if (m.has(Affix.RAGING) && t != null && t.isNpcLike() && !t.boss && t.isAlive() && t.healthFraction() < 0.3 && !t.auras().has("raging")
                && run.npcs.contains(t)) {
            engine.applyAura(new EffectContext(engine, t, t, null, null, null), t, "raging", 1, -1);
        }
        if (m.has(Affix.NECROTIC) && hit.source != null && hit.source.isNpcLike() && t != null && t.isPlayerLike()
                && "auto_attack".equals(hit.abilityId) && t.isAlive()) {
            engine.applyAura(new EffectContext(engine, hit.source, t, null, null, null), t, "necrotic_wound", 1, -1);
        }
    }

    void clear() {
        for (Telegraph t : telegraphs) server.sendTelegraph(run, t, false);
        telegraphs.clear();
    }

    private void add(Telegraph t) {
        telegraphs.add(t);
        server.sendTelegraph(run, t, true);
    }

    private UnitState random(List<UnitState> l) {
        return l.isEmpty() ? null : l.get(engine.rng().nextInt(l.size()));
    }

    private static Effect all(Effect... e) {
        return Effects.all(e);
    }

    public List<Telegraph> telegraphs() {
        return telegraphs;
    }

    AuraInstance dummy() {
        return null;
    }
}
