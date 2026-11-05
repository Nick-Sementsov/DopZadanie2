package io.github.nicksementsov.druzhina.network;

import io.github.nicksementsov.druzhina.api.Relations;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * The commander's view asks who around is an enemy: bandits, and whoever fights for a side at war with the player's
 * (soldiers, players, a colony's guards). The answer is a {@link HostilesPacket}.
 */
public record ScoutPacket() {
    /** How far around the commander the scouts look, and how many foes they name at most. */
    private static final double RANGE = 112;
    private static final int MAX = 512;

    public static void encode(ScoutPacket p, FriendlyByteBuf buf) {
    }

    public static ScoutPacket decode(FriendlyByteBuf buf) {
        return new ScoutPacket();
    }

    public static void handle(ScoutPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            int[] ids = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                    e -> e != player && e.isAlive() && Relations.hostile(player.getUUID(), e))
                .stream().limit(MAX).mapToInt(LivingEntity::getId).toArray();
            DruzhinaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HostilesPacket(ids));
        }
        ctx.get().setPacketHandled(true);
    }
}
