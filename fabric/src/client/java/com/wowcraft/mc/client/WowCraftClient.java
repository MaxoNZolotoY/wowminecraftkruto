package com.wowcraft.mc.client;

import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.Protocol;
import com.wowcraft.core.npc.BodyType;
import com.wowcraft.mc.client.hud.Hud;
import com.wowcraft.mc.client.render.NpcRenderers;
import com.wowcraft.mc.client.render.WorldFx;
import com.wowcraft.mc.entity.WowEntities;
import com.wowcraft.mc.item.WowDyeableArmorItem;
import com.wowcraft.mc.item.WowItems;
import com.wowcraft.mc.item.WowSpawnEggItem;
import com.wowcraft.core.npc.NpcTemplate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

/** Client entry point: renderers, HUD, input and networking. */
public final class WowCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(WowEntities.TYPES.get(BodyType.HUMANOID), NpcRenderers.Humanoid::new);
        EntityRendererRegistry.register(WowEntities.TYPES.get(BodyType.BEAST), NpcRenderers.Beast::new);
        EntityRendererRegistry.register(WowEntities.TYPES.get(BodyType.ELEMENTAL), NpcRenderers.Elemental::new);
        EntityRendererRegistry.register(WowEntities.TYPES.get(BodyType.TOTEM), NpcRenderers.Elemental::new);

        List<Item> dyeable = new ArrayList<>();
        for (Item item : WowItems.ALL.values()) if (item instanceof WowDyeableArmorItem) dyeable.add(item);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex > 0 ? -1 : ((DyeableItem) stack.getItem()).getColor(stack),
                dyeable.toArray(new Item[0]));

        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            NpcTemplate t = WowSpawnEggItem.template(stack);
            if (t == null) return -1;
            if (tintIndex > 0) return WowSpawnEggItem.rankColor(t);
            int c = t.tint & 0xFFFFFF;
            return c == 0xFFFFFF ? NpcRenderers.placeholderColor(t.body) : 0xFF000000 | c;
        }, WowItems.NPC_EGG);

        WowItems.tooltipSpec = ClientState::spec;
        WowItems.language = ClientState::lang;

        ClientNet.register();
        Controls.register();
        WorldFx.register();
        HudRenderCallback.EVENT.register(Hud::render);
        ClientTickEvents.START_CLIENT_TICK.register(Controls::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(ClientState::reset));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !ClientState.helloSent && ClientNet.canSend()) {
                C2S.Hello h = new C2S.Hello();
                h.protocol = Protocol.VERSION;
                h.language = client.getLanguageManager().getLanguage();
                ClientNet.send(h);
                ClientState.helloSent = true;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
        if (ClientSmokeTest.enabled()) ClientSmokeTest.start();
    }
}
