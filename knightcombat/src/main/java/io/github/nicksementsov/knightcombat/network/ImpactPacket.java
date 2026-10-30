package io.github.nicksementsov.knightcombat.network;

import io.github.nicksementsov.knightcombat.client.CombatFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to the players around a fight: something struck something. The clients shake their cameras, stop the
 * swing for an instant and make the one who was hit flinch.
 *
 * @param attacker entity id of the one who struck (or cried), -1 when unknown
 * @param victim   entity id of the one who was struck or who blocked, -1 for walls and cries
 * @param weight   how heavy the weapon is, 0 to 1 (see {@link io.github.nicksementsov.knightcombat.combat.WeaponStats#weight})
 */
public record ImpactPacket(Impact impact, int attacker, int victim, float x, float y, float z, float weight) {
    public static void encode(ImpactPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.impact.ordinal());
        buf.writeVarInt(packet.attacker + 1);
        buf.writeVarInt(packet.victim + 1);
        buf.writeFloat(packet.x);
        buf.writeFloat(packet.y);
        buf.writeFloat(packet.z);
        buf.writeByte(Math.round(packet.weight * 100));
    }

    public static ImpactPacket decode(FriendlyByteBuf buf) {
        return new ImpactPacket(Impact.byId(buf.readByte()), buf.readVarInt() - 1, buf.readVarInt() - 1,
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte() / 100f);
    }

    public static void handle(ImpactPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CombatFx.onImpact(packet));
        context.get().setPacketHandled(true);
    }
}
