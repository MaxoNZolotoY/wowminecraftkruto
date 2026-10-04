package com.wowcraft.mc.client;

import com.wowcraft.core.net.Protocol;
import com.wowcraft.mc.WowCraftMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Client side of the custom channel. */
public final class ClientNet {
    private ClientNet() {
    }

    public static final Identifier CHANNEL = new Identifier(Protocol.CHANNEL);

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, sender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            Object msg;
            try {
                msg = Protocol.decode(data);
            } catch (RuntimeException e) {
                WowCraftMod.LOG.warn("Bad WoWCraft packet: {}", e.toString());
                return;
            }
            if (msg != null) client.execute(() -> ClientState.handle(msg));
        });
    }

    public static boolean canSend() {
        return ClientPlayNetworking.canSend(CHANNEL);
    }

    public static void send(Object msg) {
        if (!ClientPlayNetworking.canSend(CHANNEL)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBytes(Protocol.encode(msg));
        ClientPlayNetworking.send(CHANNEL, buf);
    }
}
