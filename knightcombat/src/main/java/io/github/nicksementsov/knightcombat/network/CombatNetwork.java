package io.github.nicksementsov.knightcombat.network;

import io.github.nicksementsov.knightcombat.KnightCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The network channel of the mod. The mod has to be installed on both the server and the clients. */
public final class CombatNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            KnightCombat.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private CombatNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(CombatInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatInputPacket::encode).decoder(CombatInputPacket::decode)
                .consumerMainThread(CombatInputPacket::handle).add();
        CHANNEL.messageBuilder(CombatStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatStatePacket::encode).decoder(CombatStatePacket::decode)
                .consumerMainThread(CombatStatePacket::handle).add();
        CHANNEL.messageBuilder(StaminaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StaminaPacket::encode).decoder(StaminaPacket::decode)
                .consumerMainThread(StaminaPacket::handle).add();
        CHANNEL.messageBuilder(FeedbackPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FeedbackPacket::encode).decoder(FeedbackPacket::decode)
                .consumerMainThread(FeedbackPacket::handle).add();
    }

    public static void sendToServer(CombatAction action) {
        CHANNEL.sendToServer(new CombatInputPacket(action));
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToTrackingAndSelf(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
}
