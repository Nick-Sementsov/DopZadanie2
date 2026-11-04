package io.github.nicksementsov.druzhina.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicksementsov.druzhina.entity.Mounts;
import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

/**
 * {@code /druzhina army}: the player's soldiers and how they are; {@code /druzhina order follow|hold|charge}: the
 * baton's orders by word. For operators: {@code /druzhina hire <role> [n]}
 * gives soldiers for free, {@code /druzhina bandits [n]} and {@code /druzhina raid} bring bandits.
 */
public final class DruzhinaCommands {
    private DruzhinaCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("druzhina")
                .then(Commands.literal("army").executes(DruzhinaCommands::army))
                .then(Commands.literal("order")
                        .then(Commands.argument("orders", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"follow", "hold", "charge"}, b))
                                .executes(DruzhinaCommands::order)))
                .then(Commands.literal("hire").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Role.values()).map(Role::id), b))
                                .executes(c -> hire(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                        .executes(c -> hire(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("raid").requires(s -> s.hasPermission(2))
                        .executes(c -> Bandits.raid(c.getSource().getPlayerOrException(), true))));
    }

    private static int army(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        List<Soldier> army = Army.of(player);
        c.getSource().sendSuccess(() -> Component.translatable("druzhina.cmd.army", army.size()).withStyle(ChatFormatting.GOLD), false);
        for (Soldier s : army) {
            c.getSource().sendSuccess(s::status, false);
        }
        return army.size();
    }

    /** «За мной!», «Стоять!» or «В атаку!» to the soldiers within earshot, as with the baton. */
    private static int order(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "orders");
        for (Orders o : Orders.values()) {
            if (o.id().equals(id) && o != Orders.PATROL) {
                if (o == Orders.HOLD) {
                    return Army.formLine(player, player.blockPosition().relative(player.getDirection(), 3), player.getYRot());
                }
                return Army.order(player, o);
            }
        }
        c.getSource().sendFailure(Component.literal("follow | hold | charge"));
        return 0;
    }

    /** Soldiers of a role next to the player: his own, or a band of bandits when the role is a bandit one. */
    private static int hire(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Role role = Role.byName(StringArgumentType.getString(c, "role"));
        if (role == null) {
            c.getSource().sendFailure(Component.literal("?"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < count; i++) {
            BlockPos at = player.blockPosition().offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
            if (role.bandit) {
                Bandits.spawn(level, role, at, null);
                continue;
            }
            Soldier s = DruzhinaRegistry.SOLDIER.get().create(level);
            if (s != null) {
                s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0);
                s.enlist(role, player.getUUID(), level.random);
                s.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.COMMAND, null, null);
                s.order(Orders.FOLLOW, null, 0);
                level.addFreshEntity(s);
                Mounts.mount(level, s, level.random);
            }
        }
        return count;
    }
}
