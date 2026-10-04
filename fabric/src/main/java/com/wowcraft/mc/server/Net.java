package com.wowcraft.mc.server;

import com.wowcraft.core.net.Protocol;
import com.wowcraft.mc.WowCraftMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** The mod's single custom payload channel (type id + JSON, see {@link Protocol}). */
public final class Net {
    private Net() {
    }

    public static final Identifier CHANNEL = new Identifier(Protocol.CHANNEL);

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            Object msg;
            try {
                msg = Protocol.decode(data);
            } catch (RuntimeException e) {
                WowCraftMod.LOG.warn("Bad packet from {}: {}", player.getName().getString(), e.toString());
                return;
            }
            if (msg == null) return;
            server.execute(() -> {
                ServerHooks.ensureJoined(player);
                if (WowCraftMod.game() != null) WowCraftMod.game().handle(player.getUuid(), msg);
            });
        });
    }

    public static void send(ServerPlayerEntity player, Object msg) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBytes(Protocol.encode(msg));
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}
