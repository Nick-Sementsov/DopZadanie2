package io.github.nicksementsov.druzhina.item;

import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The commander's baton. Right click: «За мной!»; with Shift: «В атаку!»; on the ground: «Стоять здесь!», the
 * soldiers form a line there, facing the way the commander looks.
 */
public class BatonItem extends Item {
    private static final int COOLDOWN = 10;

    public BatonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Army.order(sp, player.isShiftKeyDown() ? Orders.CHARGE : Orders.FOLLOW);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            // Shift: the charge, wherever the baton points
            return this.use(ctx.getLevel(), player, ctx.getHand()).getResult();
        }
        if (!ctx.getLevel().isClientSide && player instanceof ServerPlayer sp) {
            BlockPos at = ctx.getClickedFace() == Direction.UP ? ctx.getClickedPos().above() : ctx.getClickedPos().relative(ctx.getClickedFace());
            Army.formLine(sp, at, player.getYRot());
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.druzhina.commander_baton.follow").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.druzhina.commander_baton.hold").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.druzhina.commander_baton.charge").withStyle(ChatFormatting.GRAY));
    }
}
