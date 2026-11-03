package io.github.nicksementsov.knightcombat.network;

import io.github.nicksementsov.knightcombat.combat.CombatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the player pressed or released a combat key. */
public record CombatInputPacket(CombatAction action) {
    public static void encode(CombatInputPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action.ordinal());
    }

    public static CombatInputPacket decode(FriendlyByteBuf buf) {
        return new CombatInputPacket(CombatAction.byId(buf.readByte()));
    }

    public static void handle(CombatInputPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && packet.action != null) {
            CombatManager.handleInput(player, packet.action);
        }
        context.get().setPacketHandled(true);
    }
}
