package com.wowcraft.mc.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.wowcraft.core.game.PlayerSession;
import com.wowcraft.core.util.L10n;
import com.wowcraft.mc.WowCraftMod;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/** /wow <anything>: forwarded to the core command handler. */
public final class WowCommand {
    private WowCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("wow")
                .executes(ctx -> run(ctx, ""))
                .then(CommandManager.argument("args", StringArgumentType.greedyString()).executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "args")))));
    }

    private static int run(CommandContext<ServerCommandSource> ctx, String args) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || WowCraftMod.game() == null) {
            src.sendError(Text.literal("/wow must be used by a player"));
            return 0;
        }
        boolean op = src.hasPermissionLevel(2);
        List<L10n> lines = WowCraftMod.game().command(player.getUuid(), args, op);
        PlayerSession s = WowCraftMod.game().session(player.getUuid());
        L10n.Lang lang = s != null ? s.lang : L10n.Lang.EN;
        for (L10n line : lines) player.sendMessage(Text.literal(line.get(lang)));
        return Command.SINGLE_SUCCESS;
    }
}
