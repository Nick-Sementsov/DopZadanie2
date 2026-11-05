package io.github.nicksementsov.druzhina.item;

import io.github.nicksementsov.druzhina.entity.Mounts;
import io.github.nicksementsov.druzhina.entity.siege.SiegeEngine;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A siege engine taken apart, to be set up on the ground: there it is the player's, and waits for one of his men to
 * work it (Shift and right click on it with an empty hand sends the nearest one).
 */
public class EngineItem extends Item {
    private final Supplier<? extends EntityType<? extends SiegeEngine>> type;

    public EngineItem(Supplier<? extends EntityType<? extends SiegeEngine>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        EntityType<? extends SiegeEngine> type = this.type.get();
        BlockPos above = ctx.getClickedPos().relative(ctx.getClickedFace());
        Vec3 at = Mounts.spot(server, type, Vec3.atBottomCenterOf(above));
        SiegeEngine engine = type.create(server);
        if (engine == null || !server.noCollision(type.getAABB(at.x, at.y, at.z))) {
            return InteractionResult.FAIL;
        }
        float yaw = player != null ? player.getYRot() : 0;
        engine.moveTo(at.x, at.y, at.z, yaw, 0);
        engine.setYBodyRot(yaw);
        engine.setYHeadRot(yaw);
        engine.setOwnerUUID(player != null ? player.getUUID() : null);
        engine.finalizeSpawn(server, server.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.SPAWN_EGG, null, null);
        server.addFreshEntity(engine);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 1.0f, 0.7f);
        if (player == null || !player.getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.druzhina.engine.tip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.druzhina.engine.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
