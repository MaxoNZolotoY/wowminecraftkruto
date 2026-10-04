package com.wowcraft.mc.server;

import com.wowcraft.core.combat.CombatListener;
import com.wowcraft.core.combat.HitResult;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.game.GameServer;
import com.wowcraft.core.game.InstanceRun;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.Vec3;
import com.wowcraft.mc.WowCraftMod;
import net.minecraft.server.MinecraftServer;

/**
 * Headless self-test (-Dwowcraft.smoke=true): builds a dungeon in the instance dimension, spawns its enemies and a few bots,
 * lets them fight, builds an arena map, then stops the server. CI checks the log for the result line.
 */
public final class SmokeTest {
    private SmokeTest() {
    }

    private static int tick;
    private static InstanceRun dungeon, arena;
    private static int damageEvents, deaths;
    private static int phase;
    private static String failure;

    public static boolean enabled() {
        return Boolean.getBoolean("wowcraft.smoke");
    }

    public static void tick(MinecraftServer server) {
        GameServer game = WowCraftMod.game();
        if (game == null) return;
        tick++;
        try {
            step(server, game);
        } catch (RuntimeException e) {
            WowCraftMod.LOG.error("Smoke test exception", e);
            finish(server, "exception: " + e);
        }
    }

    private static void step(MinecraftServer server, GameServer game) {
        switch (phase) {
            case 0 -> {
                if (tick < 40) return;
                WowCraftMod.LOG.info("[smoke] creating dungeon instance");
                game.engine().addListener(new CombatListener() {
                    @Override
                    public void onDamage(HitResult hit) {
                        damageEvents++;
                    }

                    @Override
                    public void onDeath(UnitState unit, UnitState killer) {
                        deaths++;
                    }
                });
                dungeon = game.instances().createStandalone(Dungeons.get("grimhold_depths"), Difficulty.NORMAL);
                phase = 1;
            }
            case 1 -> {
                if (dungeon.state == InstanceRun.State.BUILDING) {
                    if (tick > 2400) finish(server, "dungeon build timed out");
                    return;
                }
                WowCraftMod.LOG.info("[smoke] dungeon ready: {} enemies spawned", dungeon.npcs.size());
                if (dungeon.npcs.isEmpty()) {
                    finish(server, "no enemies spawned");
                    return;
                }
                UnitState first = dungeon.npcs.get(0);
                Vec3 at = first.position().add(3, 0, 3);
                Spec[] specs = {Spec.PROTECTION_PALADIN, Spec.RESTORATION_SHAMAN, Spec.FIRE, Spec.HAVOC};
                for (Spec spec : specs) {
                    UnitState bot = game.bots().spawn(spec, 140, dungeon.worldKey, at, 0, "players", "smoke", dungeon.id);
                    if (bot != null) dungeon.bots.add(bot);
                }
                WowCraftMod.LOG.info("[smoke] spawned {} bots", dungeon.bots.size());
                phase = 2;
                tick = 0;
            }
            case 2 -> {
                if (tick < 600) return;
                WowCraftMod.LOG.info("[smoke] fight: {} damage events, {} deaths", damageEvents, deaths);
                if (damageEvents == 0) {
                    finish(server, "no combat happened");
                    return;
                }
                arena = game.instances().createStandaloneMap(Dungeons.get("ring_of_trials"));
                phase = 3;
                tick = 0;
            }
            case 3 -> {
                if (arena.state == InstanceRun.State.BUILDING) {
                    if (tick > 1200) finish(server, "arena build timed out");
                    return;
                }
                WowCraftMod.LOG.info("[smoke] arena map ready");
                game.instances().close(dungeon);
                game.instances().close(arena);
                phase = 4;
                tick = 0;
            }
            case 4 -> {
                if (tick < 200) return;
                finish(server, null);
            }
            default -> {
            }
        }
    }

    private static void finish(MinecraftServer server, String error) {
        if (phase == 99) return;
        phase = 99;
        failure = error;
        if (error == null) WowCraftMod.LOG.info("WOWCRAFT SMOKE TEST PASSED");
        else WowCraftMod.LOG.error("WOWCRAFT SMOKE TEST FAILED: {}", error);
        server.stop(false);
    }
}
