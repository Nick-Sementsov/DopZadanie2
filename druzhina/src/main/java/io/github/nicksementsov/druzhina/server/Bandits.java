package io.github.nicksementsov.druzhina.server;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.DruzhinaConfig;
import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Bands of bandits: now and then a band comes for a player, a few cutthroats and their chieftain, and more of them the
 * larger his army. Their purses pay for more soldiers.
 */
@Mod.EventBusSubscriber(modid = Druzhina.MODID)
public final class Bandits {
    private static final int CHECK = 100;
    private static final String PLAYED = "druzhina.played";
    private static final String NEXT = "druzhina.next_raid";

    private Bandits() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK != 0 || !DruzhinaConfig.RAIDS.get()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            check(player);
        }
    }

    private static void check(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long played = data.getLong(PLAYED) + CHECK;
        data.putLong(PLAYED, played);
        long next = data.getLong(NEXT);
        if (next == 0) {
            next = DruzhinaConfig.FIRST_RAID_MINUTES.get() * 1200L;
            data.putLong(NEXT, next);
        }
        if (played < next) {
            return;
        }
        if (raid(player, false) > 0) {
            float spread = 0.75f + player.getRandom().nextFloat() * 0.5f;
            data.putLong(NEXT, played + Math.round(DruzhinaConfig.RAID_MINUTES.get() * 1200L * spread));
        } else {
            // not now (underground, at sea): try again in a minute
            data.putLong(NEXT, played + 1200);
        }
    }

    /** Sends a band at {@code player}. Returns how many came: 0 when there was no place for them to come from. */
    public static int raid(ServerPlayer player, boolean forced) {
        ServerLevel level = player.serverLevel();
        RandomSource rnd = level.random;
        if (!forced && (player.isInWater() || player.getY() < level.getSeaLevel() - 12 && !level.canSeeSky(player.blockPosition()))) {
            return 0;
        }
        BlockPos camp = null;
        for (int i = 0; i < 16 && camp == null; i++) {
            float angle = rnd.nextFloat() * Mth.TWO_PI;
            double dist = forced ? 14 + rnd.nextInt(6) : 28 + rnd.nextInt(10);
            camp = surface(level, player, player.getX() + Mth.cos(angle) * dist, player.getZ() + Mth.sin(angle) * dist);
        }
        if (camp == null) {
            return 0;
        }

        int army = Army.of(player).size();
        // a few cutthroats against a lone traveller, more against a war band: never quite as many as his men
        // and a lone traveller meets two of them without their chieftain
        int size = army == 0 ? 2 : Mth.clamp(2 + army / 3 + rnd.nextInt(2), 3, DruzhinaConfig.MAX_BAND.get());
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            Role role = i == 0 && army > 0 ? Role.CHIEFTAIN : rnd.nextFloat() < 0.3f ? Role.BANDIT_ARCHER : Role.BANDIT;
            BlockPos at = surface(level, player, camp.getX() + rnd.nextInt(7) - 3, camp.getZ() + rnd.nextInt(7) - 3);
            if (spawn(level, role, at != null ? at : camp, player) != null) {
                spawned++;
            }
        }

        double dx = camp.getX() - player.getX();
        double dz = camp.getZ() - player.getZ();
        String from = Math.abs(dx) > Math.abs(dz) ? dx > 0 ? "east" : "west" : dz > 0 ? "south" : "north";
        player.displayClientMessage(Component.translatable("druzhina.raid.coming", spawned, Component.translatable("druzhina.raid.from." + from))
                .withStyle(ChatFormatting.RED), false);
        player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2f, 1.0f);
        return spawned;
    }

    @Nullable
    public static Soldier spawn(ServerLevel level, Role role, BlockPos at, @Nullable ServerPlayer quarry) {
        Soldier s = DruzhinaRegistry.SOLDIER.get().create(level);
        if (s == null) {
            return null;
        }
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        s.enlist(role, null, level.random);
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        if (quarry != null) {
            s.setTarget(quarry);
        }
        level.addFreshEntity(s);
        return s;
    }

    /** Dry ground at the top of the world at x, z, not too far up or down from the player, or null. */
    @Nullable
    private static BlockPos surface(ServerLevel level, ServerPlayer player, double x, double z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
        if (Math.abs(top.getY() - player.getY()) > 16 || !level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()) {
            return null;
        }
        return Army.ground(level, top);
    }
}
