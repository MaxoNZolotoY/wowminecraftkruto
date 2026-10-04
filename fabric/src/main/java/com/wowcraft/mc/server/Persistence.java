package com.wowcraft.mc.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wowcraft.core.game.Config;
import com.wowcraft.core.game.WorldData;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.mc.WowCraftMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** JSON storage: profiles and world data in the world folder, settings in config/wowcraft.json. */
public final class Persistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    private final Path dir;

    public Persistence(MinecraftServer server) {
        this.dir = server.getSavePath(WorldSavePath.ROOT).resolve("wowcraft");
    }

    public static Config loadConfig() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("wowcraft.json");
        Config c = null;
        try {
            if (Files.exists(p)) c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Config.class);
        } catch (IOException | RuntimeException e) {
            WowCraftMod.LOG.warn("Could not read {}: {}", p, e.toString());
        }
        if (c == null) c = new Config();
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(c), StandardCharsets.UTF_8);
        } catch (IOException e) {
            WowCraftMod.LOG.warn("Could not write {}", p);
        }
        return c;
    }

    public PlayerProfile loadProfile(UUID uuid) {
        Path p = dir.resolve("players").resolve(uuid + ".json");
        try {
            if (Files.exists(p)) {
                PlayerProfile prof = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), PlayerProfile.class);
                if (prof != null) return prof;
            }
        } catch (IOException | RuntimeException e) {
            WowCraftMod.LOG.error("Could not read profile {}: {}", p, e.toString());
        }
        PlayerProfile prof = new PlayerProfile();
        prof.uuid = uuid.toString();
        return prof;
    }

    public void saveProfile(PlayerProfile profile) {
        if (profile.uuid == null) return;
        write(dir.resolve("players").resolve(profile.uuid + ".json"), GSON.toJson(profile));
    }

    public WorldData loadWorld() {
        Path p = dir.resolve("world.json");
        try {
            if (Files.exists(p)) {
                WorldData w = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), WorldData.class);
                if (w != null) return w;
            }
        } catch (IOException | RuntimeException e) {
            WowCraftMod.LOG.error("Could not read {}: {}", p, e.toString());
        }
        return new WorldData();
    }

    public void saveWorld(WorldData data) {
        write(dir.resolve("world.json"), GSON.toJson(data));
    }

    private void write(Path p, String json) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.writeString(p, json, StandardCharsets.UTF_8);
            } catch (IOException e2) {
                WowCraftMod.LOG.error("Could not write {}: {}", p, e2.toString());
            }
        }
    }
}
