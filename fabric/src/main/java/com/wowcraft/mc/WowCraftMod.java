package com.wowcraft.mc;

import com.wowcraft.core.content.Content;
import com.wowcraft.core.game.Config;
import com.wowcraft.core.game.GameServer;
import com.wowcraft.core.game.PlayerSession;
import com.wowcraft.mc.block.WowBlocks;
import com.wowcraft.mc.entity.WowEntities;
import com.wowcraft.mc.item.WowItems;
import com.wowcraft.mc.server.McPlatform;
import com.wowcraft.mc.server.Net;
import com.wowcraft.mc.server.Persistence;
import com.wowcraft.mc.server.ServerHooks;
import com.wowcraft.mc.server.WowCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod entry point: registers content and runs the WoW game server next to the Minecraft server. */
public final class WowCraftMod implements ModInitializer {
    public static final String ID = "wowcraft";
    public static final Logger LOG = LoggerFactory.getLogger("WoWCraft");

    private static GameServer game;
    private static McPlatform platform;
    private static Persistence persistence;
    private static long ticks;

    public static GameServer game() {
        return game;
    }

    public static McPlatform bridge() {
        return platform;
    }

    public static Persistence persistence() {
        return persistence;
    }

    @Override
    public void onInitialize() {
        Content.bootstrap();
        WowItems.register();
        WowBlocks.register();
        WowEntities.register();
        Net.registerServer();
        ServerHooks.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WowCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(WowCraftMod::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(WowCraftMod::stop);
        ServerTickEvents.END_SERVER_TICK.register(WowCraftMod::tick);
        LOG.info("WoWCraft initialized: {} abilities ready", com.wowcraft.core.content.Registry.abilities().size());
    }

    private static void start(MinecraftServer server) {
        Config config = Persistence.loadConfig();
        persistence = new Persistence(server);
        platform = new McPlatform(server);
        game = new GameServer(platform, config, persistence.loadWorld());
        platform.attach(game);
        LOG.info("WoWCraft game server started");
    }

    private static void stop(MinecraftServer server) {
        if (game == null) return;
        saveAll();
        game.shutdown();
        platform.finishPendingWork();
        game = null;
        platform = null;
    }

    private static void saveAll() {
        for (PlayerSession s : game.sessions()) persistence.saveProfile(s.profile);
        persistence.saveWorld(game.worldData());
    }

    private static void tick(MinecraftServer server) {
        if (game == null) return;
        try {
            com.wowcraft.mc.server.ServerHooks.flushJoins(server);
            platform.tick();
        } catch (RuntimeException e) {
            LOG.error("WoWCraft tick failed", e);
        }
        if (++ticks % (20 * 300) == 0) saveAll();
        if (com.wowcraft.mc.server.SmokeTest.enabled()) com.wowcraft.mc.server.SmokeTest.tick(server);
    }
}
