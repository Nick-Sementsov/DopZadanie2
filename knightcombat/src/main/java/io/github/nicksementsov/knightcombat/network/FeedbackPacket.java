package io.github.nicksementsov.knightcombat.network;

import io.github.nicksementsov.knightcombat.client.ClientCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to one client: something happened in their fight. */
public record FeedbackPacket(Feedback feedback) {
    public static void encode(FeedbackPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.feedback.ordinal());
    }

    public static FeedbackPacket decode(FriendlyByteBuf buf) {
        return new FeedbackPacket(Feedback.byId(buf.readByte()));
    }

    public static void handle(FeedbackPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCombat.onFeedback(packet.feedback));
        context.get().setPacketHandled(true);
    }
}
