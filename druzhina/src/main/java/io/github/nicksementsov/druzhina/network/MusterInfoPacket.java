package io.github.nicksementsov.druzhina.network;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * From the server to the muster window: how many men the player leads, how many he may ({@code max} -1: no limit),
 * and what the limit comes from.
 */
public record MusterInfoPacket(int men, int max, @Nullable Component why) {
    public static void encode(MusterInfoPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.men);
        buf.writeVarInt(p.max);
        buf.writeBoolean(p.why != null);
        if (p.why != null) {
            buf.writeComponent(p.why);
        }
    }

    public static MusterInfoPacket decode(FriendlyByteBuf buf) {
        int men = buf.readVarInt();
        int max = buf.readVarInt();
        return new MusterInfoPacket(men, max, buf.readBoolean() ? buf.readComponent() : null);
    }

    public static void handle(MusterInfoPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.nicksementsov.druzhina.client.MusterScreen.info(p.men(), p.max(), p.why())));
        ctx.get().setPacketHandled(true);
    }
}
