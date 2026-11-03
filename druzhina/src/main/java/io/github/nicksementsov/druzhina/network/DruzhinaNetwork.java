package io.github.nicksementsov.druzhina.network;

import io.github.nicksementsov.druzhina.Druzhina;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class DruzhinaNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Druzhina.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private DruzhinaNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(HirePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HirePacket::encode).decoder(HirePacket::decode)
                .consumerMainThread(HirePacket::handle).add();
    }
}
