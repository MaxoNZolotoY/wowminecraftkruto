package com.wowcraft.mc.client;

import com.wowcraft.core.content.Registry;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.CastType;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Key bindings: combat mode (number keys cast abilities), targeting, screens. */
public final class Controls {
    private Controls() {
    }

    static final String CATEGORY = "category.wowcraft";
    public static KeyBinding COMBAT_MODE, TARGET, SLOT_10, SLOT_11, SLOT_12, CHARACTER, TALENTS, GROUP_FINDER, VAULT, METER, RELEASE, ACCEPT,
            POTION, TRINKET;
    /** On by default: number keys cast abilities; R switches back to vanilla hotbar selection. */
    public static boolean combatMode = true;
    public static boolean showMeter = true;
    private static int heldSlot = -1;
    private static KeyBinding heldKey;
    private static int lastTargetIndex;

    public static void register() {
        COMBAT_MODE = key("combat_mode", GLFW.GLFW_KEY_R);
        TARGET = key("target", GLFW.GLFW_KEY_G);
        SLOT_10 = key("slot_10", GLFW.GLFW_KEY_0);
        SLOT_11 = key("slot_11", GLFW.GLFW_KEY_MINUS);
        SLOT_12 = key("slot_12", GLFW.GLFW_KEY_EQUAL);
        CHARACTER = key("character", GLFW.GLFW_KEY_K);
        TALENTS = key("talents", GLFW.GLFW_KEY_N);
        GROUP_FINDER = key("group_finder", GLFW.GLFW_KEY_O);
        VAULT = key("vault", GLFW.GLFW_KEY_J);
        METER = key("meter", GLFW.GLFW_KEY_M);
        RELEASE = key("release", GLFW.GLFW_KEY_Z);
        ACCEPT = key("accept", GLFW.GLFW_KEY_Y);
        POTION = key("potion", GLFW.GLFW_KEY_H);
        TRINKET = key("trinket", GLFW.GLFW_KEY_U);
    }

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.wowcraft." + name, InputUtil.Type.KEYSYM, code, CATEGORY));
    }

    /** START_CLIENT_TICK: runs before vanilla reads the hotbar keys. */
    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        while (COMBAT_MODE.wasPressed()) {
            combatMode = !combatMode;
            mc.inGameHud.setOverlayMessage(Text.literal(combatMode ? ClientState.t("Combat mode: ON (1-0, -, = cast abilities)", "Боевой режим: ВКЛ (1-0, -, = — способности)")
                    : ClientState.t("Combat mode: OFF", "Боевой режим: ВЫКЛ")).formatted(combatMode ? Formatting.GOLD : Formatting.GRAY), false);
        }
        if (mc.currentScreen == null) {
            if (combatMode) {
                for (int i = 0; i < 9; i++) {
                    KeyBinding k = mc.options.hotbarKeys[i];
                    while (k.wasPressed()) cast(mc, i, k);
                }
                while (SLOT_10.wasPressed()) cast(mc, 9, SLOT_10);
                while (SLOT_11.wasPressed()) cast(mc, 10, SLOT_11);
                while (SLOT_12.wasPressed()) cast(mc, 11, SLOT_12);
            } else {
                while (SLOT_10.wasPressed()) {
                }
                while (SLOT_11.wasPressed()) {
                }
                while (SLOT_12.wasPressed()) {
                }
            }
            while (TARGET.wasPressed()) cycleTarget(mc);
            while (CHARACTER.wasPressed()) ClientState.open("character");
            while (TALENTS.wasPressed()) ClientState.open("talents");
            while (GROUP_FINDER.wasPressed()) ClientState.open("group_finder");
            while (VAULT.wasPressed()) {
                request("vault");
                request("leaderboard");
                ClientState.open("vault");
            }
            while (METER.wasPressed()) showMeter = !showMeter;
            while (RELEASE.wasPressed()) {
                C2S.InstanceAction a = new C2S.InstanceAction();
                a.action = "release";
                ClientNet.send(a);
            }
            while (ACCEPT.wasPressed()) {
                if (ClientState.invite != null) {
                    C2S.GroupAction g = new C2S.GroupAction();
                    g.action = "accept";
                    ClientNet.send(g);
                    ClientState.invite = null;
                }
            }
            while (POTION.wasPressed()) useItem("potion");
            while (TRINKET.wasPressed()) useItem("trinket_1");
        }
        // empowered spells fire when the key is released
        if (heldSlot >= 0 && (heldKey == null || !heldKey.isPressed())) {
            C2S.Release r = new C2S.Release();
            ClientNet.send(r);
            heldSlot = -1;
            heldKey = null;
        }
        if (showMeter && mc.world != null && mc.world.getTime() % 20 == 0 && ClientState.self != null && ClientState.self.inCombat) request("meter");
    }

    static void request(String what) {
        C2S.Request r = new C2S.Request();
        r.what = what;
        ClientNet.send(r);
    }

    static void useItem(String which) {
        C2S.UseItem u = new C2S.UseItem();
        u.which = which;
        ClientNet.send(u);
    }

    /** Casts an action bar slot at the crosshair target / point. */
    public static void cast(MinecraftClient mc, int slot, KeyBinding key) {
        C2S.Cast c = new C2S.Cast();
        c.slot = slot;
        Entity target = crosshairEntity(mc, 45);
        if (target != null) c.targetId = target.getId();
        HitResult hit = mc.player.raycast(40, 1.0f, false);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            Vec3d p = bh.getPos();
            c.x = p.x;
            c.y = p.y;
            c.z = p.z;
            c.hasPoint = true;
        } else if (target != null) {
            c.x = target.getX();
            c.y = target.getY();
            c.z = target.getZ();
            c.hasPoint = true;
        }
        ClientNet.send(c);
        S2C.Self self = ClientState.self;
        if (self != null && self.resolvedBar != null && slot < self.resolvedBar.length && self.resolvedBar[slot] != null) {
            Ability a = Registry.ability(self.resolvedBar[slot]);
            if (a != null && a.castType == CastType.EMPOWER) {
                heldSlot = slot;
                heldKey = key;
            }
        }
    }

    /** Living entity under the crosshair up to a long range (vanilla only checks reach distance). */
    public static Entity crosshairEntity(MinecraftClient mc, double range) {
        if (mc.targetedEntity instanceof LivingEntity le) return le;
        Entity cam = mc.getCameraEntity();
        if (cam == null) return null;
        Vec3d start = cam.getCameraPosVec(1.0f);
        Vec3d dir = cam.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(range));
        HitResult block = cam.raycast(range, 1.0f, false);
        double max = range * range;
        if (block != null && block.getType() != HitResult.Type.MISS) max = block.getPos().squaredDistanceTo(start);
        Box box = cam.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
        EntityHitResult eh = ProjectileUtil.raycast(cam, start, end, box, e -> e instanceof LivingEntity && !e.isSpectator() && e != mc.player, max);
        return eh != null ? eh.getEntity() : null;
    }

    /** Tab-targeting: cycles through enemies in front of the player (WoW units and hostile Minecraft mobs), nearest first. */
    static void cycleTarget(MinecraftClient mc) {
        List<Entity> candidates = new ArrayList<>();
        Vec3d look = mc.player.getRotationVec(1.0f);
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive() || e.isSpectator()) continue;
            S2C.Unit u = ClientState.units.get(e.getId());
            boolean enemy = u != null ? u.hostile && !u.dead : e instanceof Monster;
            if (!enemy) continue;
            Vec3d to = e.getPos().subtract(mc.player.getPos());
            if (to.lengthSquared() > 45 * 45) continue;
            if (to.normalize().dotProduct(look) < 0.2 && to.lengthSquared() > 25) continue;
            candidates.add(e);
        }
        if (candidates.isEmpty()) return;
        candidates.sort((a, b) -> Double.compare(a.squaredDistanceTo(mc.player), b.squaredDistanceTo(mc.player)));
        int current = ClientState.self != null ? ClientState.self.targetId : -1;
        int index = -1;
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).getId() == current) index = i;
        lastTargetIndex = index < 0 ? 0 : (index + 1) % candidates.size();
        C2S.Target t = new C2S.Target();
        t.entityId = candidates.get(lastTargetIndex).getId();
        ClientNet.send(t);
    }
}
