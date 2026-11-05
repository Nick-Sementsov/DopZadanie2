package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.DruzhinaConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * What siege engines do to walls: every blow cracks the blocks it strikes (the cracks show as when a block is being
 * dug), and a block cracked through falls. Harder blocks take more blows; very hard ones (obsidian and the like),
 * unbreakable ones and blocks holding something (chests, colony huts) stand. Nothing breaks when the server's settings
 * say so, or when mobs may not grief.
 */
public final class SiegeDamage {
    /** Cracks are forgotten after this many ticks without a blow. */
    private static final long FORGET = 20 * 120;
    private static final Map<ServerLevel, Map<BlockPos, Crack>> CRACKS = new WeakHashMap<>();

    private record Crack(float damage, long at) {
    }

    private SiegeDamage() {
    }

    /** Whether engines may break blocks here, now. */
    public static boolean allowed(ServerLevel level, @Nullable Entity source) {
        if (!DruzhinaConfig.SIEGE_BREAKS.get()) {
            return false;
        }
        return source == null || ForgeEventFactory.getMobGriefingEvent(level, source);
    }

    /** A blow of {@code power} (1 breaks a block of hardness 1 at once) on the block at {@code pos}. */
    public static void hit(ServerLevel level, BlockPos pos, float power, @Nullable Entity source) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
            return;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > DruzhinaConfig.SIEGE_MAX_HARDNESS.get() || level.getBlockEntity(pos) != null) {
            return;
        }
        Map<BlockPos, Crack> cracks = CRACKS.computeIfAbsent(level, k -> new HashMap<>());
        long now = level.getGameTime();
        Crack old = cracks.get(pos);
        float damage = (old == null || now - old.at() > FORGET ? 0 : old.damage()) + power / Math.max(0.4f, hardness);
        int breaker = crackId(pos);
        SoundType sound = state.getSoundType(level, pos, source);
        if (damage >= 1) {
            cracks.remove(pos);
            level.destroyBlockProgress(breaker, pos, -1);
            level.destroyBlock(pos, true, source);
        } else {
            cracks.put(pos, new Crack(damage, now));
            level.destroyBlockProgress(breaker, pos, Math.min(9, (int) (damage * 10)));
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 1.0f, sound.getPitch() * 0.6f);
        }
        if (cracks.size() > 4096) {
            cracks.entrySet().removeIf(e -> now - e.getValue().at() > FORGET);
        }
    }

    /** A blow spread over a ball of blocks: full at its middle, nothing at its edge. */
    public static void blast(ServerLevel level, Vec3 at, double radius, float power, @Nullable Entity source) {
        if (!allowed(level, source)) {
            return;
        }
        int r = (int) Math.ceil(radius);
        BlockPos centre = BlockPos.containing(at);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            double d = Vec3.atCenterOf(pos).distanceTo(at);
            if (d <= radius) {
                hit(level, pos.immutable(), (float) (power * (1 - d / (radius + 0.5))), source);
            }
        }
    }

    /** A fixed id for the cracks of one block (the client keeps one crack picture per id). */
    private static int crackId(BlockPos pos) {
        return -1_000_000 - (int) (pos.asLong() & 0x7FFFFFFL);
    }
}
