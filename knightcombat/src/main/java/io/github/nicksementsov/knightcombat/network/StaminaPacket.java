package io.github.nicksementsov.knightcombat.network;

import io.github.nicksementsov.knightcombat.client.ClientCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to one client: their stamina. */
public record StaminaPacket(float stamina, float max) {
    public static void encode(StaminaPacket packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.stamina);
        buf.writeFloat(packet.max);
    }

    public static StaminaPacket decode(FriendlyByteBuf buf) {
        return new StaminaPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(StaminaPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCombat.onStamina(packet));
        context.get().setPacketHandled(true);
    }
}
