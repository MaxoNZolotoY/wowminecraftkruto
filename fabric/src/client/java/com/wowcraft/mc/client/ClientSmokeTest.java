package com.wowcraft.mc.client;

import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.net.S2C;
import com.wowcraft.mc.WowCraftMod;
import com.wowcraft.mc.client.screen.ClassSelectScreen;
import com.wowcraft.mc.entity.WowNpcEntity;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Scripted client self-test (-Dwowcraft.clientSmoke=true), run in CI under a virtual display: creates a world through the
 * real menus, picks a class in the class screen, opens every WoW screen, fills the group with bots, fights in a dungeon by
 * pressing the real ability keys, starts a Mythic+ key and plays an arena, taking screenshots along the way.
 * The log line "WOWCRAFT CLIENT SMOKE TEST PASSED" marks success.
 */
public final class ClientSmokeTest {
    private ClientSmokeTest() {
    }

    private static final Set<String> seenScreens = new HashSet<>();
    private static int experimentalPrompts;

    public static boolean enabled() {
        return Boolean.getBoolean("wowcraft.clientSmoke");
    }

    public static void start() {
        Thread t = new Thread(ClientSmokeTest::main, "WoWCraft client smoke test");
        t.setDaemon(true);
        t.start();
    }

    private static void main() {
        String failure = null;
        try {
            script();
        } catch (Throwable t) {
            failure = String.valueOf(t);
            WowCraftMod.LOG.error("Client smoke test error", t);
        }
        log("messages received: " + ClientState.received);
        if (failure == null) WowCraftMod.LOG.info("WOWCRAFT CLIENT SMOKE TEST PASSED");
        else WowCraftMod.LOG.error("WOWCRAFT CLIENT SMOKE TEST FAILED: {}", failure);
        try {
            leaveWorld();
        } catch (Throwable t) {
            WowCraftMod.LOG.error("Client smoke test: disconnect failed", t);
        }
        run(MinecraftClient::scheduleStop);
    }

    // ------------------------------------------------------------------ the script

    private static void script() {
        waitFor("game loading", mc -> mc.getOverlay() == null, 600);
        if (call(mc -> mc.currentScreen instanceof AccessibilityOnboardingScreen)) pressKey("gui.continue");
        waitFor("title screen", mc -> mc.currentScreen instanceof TitleScreen, 120);
        screenshot("01_title");
        pressKey("menu.singleplayer");
        waitFor("world list or creation", mc -> mc.currentScreen instanceof CreateWorldScreen || mc.currentScreen instanceof SelectWorldScreen, 60);
        if (call(mc -> mc.currentScreen instanceof SelectWorldScreen)) pressKey("selectWorld.create");
        waitFor("create world screen", mc -> mc.currentScreen instanceof CreateWorldScreen, 60);
        pressKey("selectWorld.create");
        enterWorld();
        log("in world " + call(mc -> mc.world.getRegistryKey().getValue().toString()));
        sleep(3000);

        // ---- class selection
        waitFor("character data", mc -> ClientState.received.containsKey("Character"), 60);
        if (!call(mc -> ClientState.character.classChosen)) {
            waitFor("class select screen", mc -> mc.currentScreen instanceof ClassSelectScreen, 30);
            screenshot("02_class_select");
            press("Mage");
            sleep(500);
            screenshot("03_class_mage");
            press("Play Fire");
        }
        waitFor("fire mage", mc -> "fire".equals(ClientState.character.spec) && ClientState.self != null && ClientState.self.resolvedBar != null, 30);
        check(call(mc -> mc.player.getInventory().armor.stream().anyMatch(s -> !s.isEmpty())), "starter armor equipped");
        check(call(mc -> Controls.combatMode), "number keys cast abilities by default (no R needed)");
        sleep(1500);
        screenshot("04_hud");
        perspective(Perspective.THIRD_PERSON_FRONT);
        screenshot("05_hud_third_person");
        perspective(Perspective.FIRST_PERSON);

        // ---- abilities on a plain Minecraft mob in the open world (no WoW unit until it is targeted)
        final int[] huskId = {-1};
        server(sp -> {
            net.minecraft.entity.mob.HuskEntity h = net.minecraft.entity.EntityType.HUSK.create(sp.getServerWorld());
            Vec3d at = sp.getPos().add(Vec3d.fromPolar(0, sp.getYaw()).multiply(7));
            h.refreshPositionAndAngles(at.x, sp.getY(), at.z, sp.getYaw() + 180, 0);
            h.setAiDisabled(true);
            sp.getServerWorld().spawnEntity(h);
            huskId[0] = h.getId();
        });
        waitFor("husk on the client", mc -> mc.world.getEntityById(huskId[0]) != null, 10);
        int textBefore = ClientState.received.getOrDefault("CombatTextBatch", 0);
        for (int i = 0; i < 16; i++) {
            final int n = i;
            run(mc -> {
                aim(mc, huskId[0]);
                S2C.Self self = ClientState.self;
                int slot = self != null && self.suggested >= 0 ? self.suggested : n % 3;
                KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(mc.options.hotbarKeys[Math.min(8, slot)]));
            });
            sleep(500);
        }
        screenshot("05b_vanilla_mob_combat");
        int huskTexts = ClientState.received.getOrDefault("CombatTextBatch", 0) - textBefore;
        log("vanilla mob: combat text batches " + huskTexts + ", unit " + call(mc -> ClientState.units.containsKey(huskId[0])));
        check(huskTexts > 0, "abilities hit a plain Minecraft mob");
        server(sp -> {
            net.minecraft.entity.Entity h = sp.getServerWorld().getEntityById(huskId[0]);
            if (h != null) h.discard();
        });
        waitFor("out of combat after the husk", mc -> ClientState.self != null && !ClientState.self.inCombat, 30);

        // ---- screens
        openScreen("character", "06_character");
        openScreen("talents", "07_talents");
        openScreen("group_finder", "08_group_finder");
        run(mc -> {
            Controls.request("vault");
            Controls.request("leaderboard");
        });
        openScreen("vault", "09_vault");

        // ---- dungeon with a bot group
        command("wow bots");
        waitFor("bot group", mc -> ClientState.group != null && ClientState.group.members != null && ClientState.group.members.size() >= 5, 30);
        log("group: " + call(mc -> ClientState.group.members.size()) + " members");
        command("wow dungeon grimhold_depths normal");
        waitFor("dungeon entered", mc -> ClientState.instance != null && inInstanceWorld(mc), 120);
        waitFor("dungeon terrain", mc -> !(mc.currentScreen instanceof DownloadingTerrainScreen), 60);
        sleep(4000);
        screenshot("10_dungeon_entrance");
        fight("11_dungeon_combat", 30);
        perspective(Perspective.THIRD_PERSON_BACK);
        screenshot("12_dungeon_combat_third_person");
        perspective(Perspective.FIRST_PERSON);
        waitFor("out of combat", mc -> ClientState.self != null && !ClientState.self.inCombat, 60);
        command("wow leave");
        waitFor("left dungeon", mc -> ClientState.instance == null && !inInstanceWorld(mc), 60);
        sleep(2000);
        screenshot("13_back_home");

        // ---- Mythic+
        command("wow m+");
        waitFor("keystone dungeon entered", mc -> ClientState.instance != null && ClientState.instance.keyLevel > 0 && inInstanceWorld(mc), 120);
        sleep(5000);
        command("wow start");
        waitFor("keystone countdown", mc -> ClientState.instance != null && (ClientState.instance.countdown > 0 || ClientState.instance.running), 30);
        screenshot("14_mythic_plus_countdown");
        waitFor("keystone timer", mc -> ClientState.instance != null && ClientState.instance.running && ClientState.instance.countdown <= 0, 30);
        log("keystone +" + call(mc -> ClientState.instance.keyLevel) + " affixes " + call(mc -> ClientState.instance.affixes));
        fight("15_mythic_plus_combat", 20);
        command("wow leave");
        waitFor("left keystone", mc -> ClientState.instance == null && !inInstanceWorld(mc), 60);

        // ---- arena (solo queue, bots fill both teams)
        waitFor("out of combat", mc -> ClientState.self != null && !ClientState.self.inCombat, 60);
        command("wow party leave");
        sleep(2000);
        command("wow pvp 3v3");
        waitFor("arena entered", mc -> ClientState.pvp != null && inInstanceWorld(mc), 120);
        sleep(3000);
        screenshot("16_arena_preparation");
        waitFor("arena gates open", mc -> ClientState.pvp != null && ClientState.pvp.countdown <= 0, 60);
        sleep(8000);
        screenshot("17_arena_fight");
        waitFor("arena finished", mc -> ClientState.pvp == null || ClientState.pvp.result != null, 240);
        log("arena result: " + call(mc -> ClientState.pvp != null ? ClientState.pvp.result : "left"));
        screenshot("18_arena_result");

        // ---- save, quit to the title screen and load the world again
        leaveWorld();
        String folder;
        try (java.util.stream.Stream<java.nio.file.Path> saves = java.nio.file.Files.list(call(mc -> mc.runDirectory.toPath().resolve("saves")))) {
            folder = saves.filter(java.nio.file.Files::isDirectory).map(pth -> pth.getFileName().toString()).findFirst().orElseThrow();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        log("reloading world " + folder);
        run(mc -> mc.createIntegratedServerLoader().start(new TitleScreen(), folder));
        enterWorld();
        waitFor("character after reload", mc -> ClientState.character.classChosen, 60);
        check("fire".equals(call(mc -> ClientState.character.spec)), "class and spec persisted across a reload");
        waitFor("player sent home from the instance world after a reload", mc -> mc.world != null && !inInstanceWorld(mc), 20);
        sleep(2000);
        screenshot("19_reloaded");

        // ---- creative tabs: real WoW gear and creature eggs
        int[] tabs = call(mc -> {
            net.minecraft.item.ItemGroups.updateDisplayContext(mc.player.networkHandler.getEnabledFeatures(), true, mc.world.getRegistryManager());
            var gear = net.minecraft.registry.Registries.ITEM_GROUP.get(new net.minecraft.util.Identifier("wowcraft", "items"));
            var creatures = net.minecraft.registry.Registries.ITEM_GROUP.get(new net.minecraft.util.Identifier("wowcraft", "creatures"));
            int withStats = 0;
            for (var st : gear.getDisplayStacks()) if (com.wowcraft.mc.item.WowItems.read(st) != null) withStats++;
            return new int[]{gear.getDisplayStacks().size(), withStats, creatures.getDisplayStacks().size()};
        });
        log("creative tabs: gear " + tabs[0] + " (" + tabs[1] + " with WoW stats), creatures " + tabs[2]);
        check(tabs[0] >= 100 && tabs[1] == tabs[0], "gear tab lists real WoW items");
        check(tabs[2] >= 50, "creatures tab lists spawn eggs");

        // ---- spawn eggs: a trash mob, an elite and a boss, out of aggro range
        java.util.List<String> eggIds = new java.util.ArrayList<>();
        for (com.wowcraft.core.npc.NpcRank r : new com.wowcraft.core.npc.NpcRank[]{com.wowcraft.core.npc.NpcRank.NORMAL,
                com.wowcraft.core.npc.NpcRank.ELITE, com.wowcraft.core.npc.NpcRank.BOSS}) {
            for (com.wowcraft.core.npc.NpcTemplate t : com.wowcraft.core.npc.NpcRegistry.all()) {
                if (t.rank == r && !t.friendly && com.wowcraft.mc.item.WowSpawnEggItem.spawnable(t)) {
                    eggIds.add(t.id);
                    break;
                }
            }
        }
        server(sp -> {
            var w = sp.getServerWorld();
            Vec3d look = Vec3d.fromPolar(0, sp.getYaw());
            Vec3d side = look.rotateY((float) Math.toRadians(90));
            for (int i = 0; i < eggIds.size(); i++) {
                Vec3d p = sp.getPos().add(look.multiply(16)).add(side.multiply((i - 1) * 5));
                net.minecraft.util.math.BlockPos top = w.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        net.minecraft.util.math.BlockPos.ofFloored(p)).down();
                net.minecraft.item.ItemStack egg = com.wowcraft.mc.item.WowSpawnEggItem.of(com.wowcraft.core.npc.NpcRegistry.get(eggIds.get(i)));
                net.minecraft.item.ItemStack old = sp.getStackInHand(net.minecraft.util.Hand.OFF_HAND);
                sp.setStackInHand(net.minecraft.util.Hand.OFF_HAND, egg);
                egg.useOnBlock(new net.minecraft.item.ItemUsageContext(sp, net.minecraft.util.Hand.OFF_HAND,
                        new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(top), net.minecraft.util.math.Direction.UP, top, false)));
                sp.setStackInHand(net.minecraft.util.Hand.OFF_HAND, old);
            }
        });
        waitFor("summoned creatures", mc -> {
            int found = 0;
            for (Entity e : mc.world.getEntities()) if (e instanceof WowNpcEntity n && eggIds.contains(n.templateId())) found++;
            return found >= eggIds.size();
        }, 20);
        log("summoned with eggs: " + eggIds);
        sleep(1500);
        screenshot("20_spawn_eggs");
        check(experimentalPrompts == 0, "no experimental-settings prompts (seen " + experimentalPrompts + ")");
    }

    private static void leaveWorld() {
        run(mc -> {
            if (mc.world != null) {
                mc.world.disconnect();
                mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
            }
            mc.setScreen(new TitleScreen());
        });
        waitFor("title screen after disconnect", mc -> mc.world == null && mc.currentScreen instanceof TitleScreen, 120);
    }

    /** Teleports next to the nearest enemy, targets it and presses ability keys for a while. */
    private static void fight(String shot, int seconds) {
        Integer enemy = call(mc -> {
            WowNpcEntity best = null;
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof WowNpcEntity n && n.unitKind() == UnitKind.NPC && !n.isWowDead() && n.isAlive()
                        && (best == null || n.squaredDistanceTo(mc.player) < best.squaredDistanceTo(mc.player))) best = n;
            }
            return best == null ? null : best.getId();
        });
        check(enemy != null, "an enemy is loaded on the client");
        server(sp -> {
            Entity e = sp.getServerWorld().getEntityById(enemy);
            if (e == null) return;
            Vec3d dir = sp.getPos().subtract(e.getPos()).multiply(1, 0, 1);
            dir = dir.lengthSquared() < 0.01 ? new Vec3d(1, 0, 0) : dir.normalize();
            Vec3d at = e.getPos().add(dir.multiply(5));
            sp.teleport(sp.getServerWorld(), at.x, e.getY(), at.z, sp.getYaw(), 0);
        });
        sleep(1500);
        run(mc -> aim(mc, enemy));
        sleep(300);
        run(mc -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(Controls.TARGET)));
        sleep(500);
        double startHp = call(mc -> {
            S2C.Unit u = ClientState.unit(enemy);
            return u != null ? u.health : -1;
        });
        int startText = ClientState.received.getOrDefault("CombatTextBatch", 0);
        long end = System.currentTimeMillis() + seconds * 1000L;
        int i = 0;
        boolean shotTaken = false, sawCombat = false;
        while (System.currentTimeMillis() < end) {
            final int n = i++;
            run(mc -> {
                S2C.Unit target = ClientState.target();
                aim(mc, target != null ? target.entityId : enemy);
                S2C.Self self = ClientState.self;
                int slot = self != null && self.suggested >= 0 ? self.suggested : n % 4;
                KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(mc.options.hotbarKeys[Math.min(8, slot)]));
            });
            sawCombat |= call(mc -> ClientState.self != null && ClientState.self.inCombat);
            if (!shotTaken && System.currentTimeMillis() > end - seconds * 600L) {
                screenshot(shot);
                shotTaken = true;
            }
            if (call(mc -> {
                S2C.Unit t = ClientState.unit(enemy);
                return t != null && t.dead;
            })) {
                // pick the next enemy in range
                run(mc -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(Controls.TARGET)));
            }
            sleep(450);
        }
        double endHp = call(mc -> {
            S2C.Unit u = ClientState.unit(enemy);
            return u != null ? (u.dead ? 0 : u.health) : 0;
        });
        int texts = ClientState.received.getOrDefault("CombatTextBatch", 0) - startText;
        log("fight: enemy hp " + Math.round(startHp) + " -> " + Math.round(endHp) + ", combat text batches " + texts + ", in combat " + sawCombat);
        check(sawCombat, "player entered combat");
        check(texts > 0, "combat text received");
    }

    private static void aim(MinecraftClient mc, int entityId) {
        Entity e = mc.world.getEntityById(entityId);
        if (e != null && mc.player != null) mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, e.getPos().add(0, e.getHeight() * 0.6, 0));
    }

    private static boolean inInstanceWorld(MinecraftClient mc) {
        return mc.world != null && mc.world.getRegistryKey().getValue().toString().equals("wowcraft:instances");
    }

    private static void enterWorld() {
        long end = System.currentTimeMillis() + 600_000;
        while (System.currentTimeMillis() < end) {
            boolean in = call(mc -> mc.world != null && mc.player != null && !(mc.currentScreen instanceof LevelLoadingScreen)
                    && !(mc.currentScreen instanceof DownloadingTerrainScreen));
            if (in) return;
            call(mc -> {
                Screen s = mc.currentScreen;
                if (s == null || s instanceof LevelLoadingScreen || s instanceof MessageScreen || s instanceof CreateWorldScreen) return null;
                String name = s.getClass().getName() + " \"" + s.getTitle().getString() + "\"";
                if (seenScreens.add(name)) {
                    log("screen while entering the world: " + name);
                    if (s.getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains("experimental")) experimentalPrompts++;
                }
                for (String key : new String[]{"gui.yes", "gui.proceed", "gui.continue", "selectWorld.backupJoinSkipButton", "gui.ok"}) {
                    if (pressNow(s, Text.translatable(key).getString())) {
                        log("pressed " + key);
                        break;
                    }
                }
                return null;
            });
            sleep(1000);
        }
        throw new IllegalStateException("timed out entering the world");
    }

    // ------------------------------------------------------------------ helpers

    private static void openScreen(String id, String shot) {
        run(mc -> ClientState.open(id));
        sleep(800);
        screenshot(shot);
        run(mc -> mc.setScreen(null));
        sleep(300);
    }

    private static void command(String cmd) {
        log("/" + cmd);
        run(mc -> mc.player.networkHandler.sendChatCommand(cmd));
        sleep(500);
    }

    private static void perspective(Perspective p) {
        run(mc -> mc.options.setPerspective(p));
        sleep(300);
    }

    private static void server(java.util.function.Consumer<ServerPlayerEntity> action) {
        IntegratedServer srv = call(MinecraftClient::getServer);
        java.util.UUID uuid = call(mc -> mc.player.getUuid());
        srv.submit(() -> {
            ServerPlayerEntity sp = srv.getPlayerManager().getPlayer(uuid);
            if (sp != null) action.accept(sp);
        }).join();
    }

    private static void pressKey(String translationKey) {
        press(call(mc -> Text.translatable(translationKey).getString()));
    }

    private static void press(String label) {
        waitFor("button \"" + label + "\"", mc -> mc.currentScreen != null && pressNow(mc.currentScreen, label), 30);
    }

    private static boolean pressNow(Screen screen, String label) {
        for (Element e : screen.children()) {
            if (e instanceof ButtonWidget b && b.active && b.getMessage().getString().equals(label)) {
                b.onPress();
                return true;
            }
        }
        return false;
    }

    private static void screenshot(String name) {
        sleep(1000);
        run(mc -> ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(), msg -> {
        }));
        log("screenshot " + name);
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new IllegalStateException("check failed: " + what);
        log("ok: " + what);
    }

    private static void waitFor(String what, Predicate<MinecraftClient> condition, int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (true) {
            if (call(condition::test)) return;
            if (System.currentTimeMillis() > end) throw new IllegalStateException("timed out waiting for " + what);
            sleep(250);
        }
    }

    private static <T> T call(Function<MinecraftClient, T> f) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.submit(() -> f.apply(mc)).join();
    }

    private static void run(java.util.function.Consumer<MinecraftClient> f) {
        call(mc -> {
            f.accept(mc);
            return null;
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void log(String s) {
        WowCraftMod.LOG.info("[client-smoke] {}", s);
    }
}
