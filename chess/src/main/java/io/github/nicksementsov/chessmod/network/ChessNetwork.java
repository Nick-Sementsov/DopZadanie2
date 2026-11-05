package io.github.nicksementsov.chessmod.network;

import io.github.nicksementsov.chessmod.ChessMod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ChessNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ChessMod.id("main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private ChessNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(ChessActionPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChessActionPacket::encode)
                .decoder(ChessActionPacket::decode)
                .consumerMainThread(ChessActionPacket::handle)
                .add();
    }
}
