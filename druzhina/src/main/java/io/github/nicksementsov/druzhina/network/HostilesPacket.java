package io.github.nicksementsov.druzhina.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** From the server to the commander's view: the entity ids of the enemies around the player. */
public record HostilesPacket(int[] ids) {
    private static final int MAX = 512;

    public static void encode(HostilesPacket p, FriendlyByteBuf buf) {
        buf.writeVarIntArray(p.ids);
    }

    public static HostilesPacket decode(FriendlyByteBuf buf) {
        return new HostilesPacket(buf.readVarIntArray(MAX));
    }

    public static void handle(HostilesPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.nicksementsov.druzhina.client.CommandView.hostiles(p.ids())));
        ctx.get().setPacketHandled(true);
    }
}
