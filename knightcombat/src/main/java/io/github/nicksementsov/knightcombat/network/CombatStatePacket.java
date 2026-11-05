package io.github.nicksementsov.knightcombat.network;

import io.github.nicksementsov.knightcombat.client.ClientCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to clients: the visible combat state of a player, for the animations.
 *
 * @param combo the finisher the attack is ({@link io.github.nicksementsov.knightcombat.combat.Combo} ordinal)
 */
public record CombatStatePacket(int entityId, int phase, int attack, int flags, int combo, int length, int elapsed) {
    public static void encode(CombatStatePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.phase);
        buf.writeByte(packet.attack);
        buf.writeVarInt(packet.flags);
        buf.writeByte(packet.combo);
        buf.writeVarInt(packet.length);
        buf.writeVarInt(packet.elapsed);
    }

    public static CombatStatePacket decode(FriendlyByteBuf buf) {
        return new CombatStatePacket(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readByte(),
                buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CombatStatePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCombat.onState(packet));
        context.get().setPacketHandled(true);
    }
}
