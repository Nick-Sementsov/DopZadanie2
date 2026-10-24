package io.github.nicksementsov.chessmod.network;

import io.github.nicksementsov.chessmod.board.BoardAction;
import io.github.nicksementsov.chessmod.board.ChessBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: a player at a chess board wants to move, start a new game, take back or resign. */
public record ChessActionPacket(BlockPos pos, BoardAction action, String data) {
    private static final int MAX_DATA_LENGTH = 64;
    /** Players must stand about as close as they need to be to open the board. */
    private static final double MAX_DISTANCE_SQR = 8 * 8;

    public static void encode(ChessActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeEnum(packet.action);
        buffer.writeUtf(packet.data, MAX_DATA_LENGTH);
    }

    public static ChessActionPacket decode(FriendlyByteBuf buffer) {
        return new ChessActionPacket(buffer.readBlockPos(), buffer.readEnum(BoardAction.class), buffer.readUtf(MAX_DATA_LENGTH));
    }

    public static void handle(ChessActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            Level level = player.level();
            if (level.isLoaded(packet.pos)
                    && player.distanceToSqr(Vec3.atCenterOf(packet.pos)) <= MAX_DISTANCE_SQR
                    && level.getBlockEntity(packet.pos) instanceof ChessBoardBlockEntity board) {
                board.handleAction(player, packet.action, packet.data);
            }
        }
        context.get().setPacketHandled(true);
    }

    public static void send(BlockPos pos, BoardAction action, String data) {
        ChessNetwork.CHANNEL.sendToServer(new ChessActionPacket(pos, action, data));
    }
}
