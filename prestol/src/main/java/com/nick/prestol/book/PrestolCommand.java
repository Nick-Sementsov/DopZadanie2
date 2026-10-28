package com.nick.prestol.book;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nick.prestol.net.Net;
import com.nick.prestol.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class PrestolCommand {
   private PrestolCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("prestol").executes(ctx -> {
               ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
               if (!Net.hasMod(player)) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(Msg.t("err.no_client_mod"));
                  return 0;
               } else {
                  Book.open(player);
                  return 1;
               }
            }))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("admin").requires(s -> s.hasPermission(2)))
                  .then(Commands.argument("order", StringArgumentType.greedyString()).executes(ctx -> {
                     ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                     Book.order(player, "admin " + StringArgumentType.getString(ctx, "order"));
                     return 1;
                  }))
            )
      );
   }
}
