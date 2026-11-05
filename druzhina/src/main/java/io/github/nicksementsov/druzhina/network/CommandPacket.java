package io.github.nicksementsov.druzhina.network;

import io.github.nicksementsov.druzhina.entity.Formation;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * An order from the commander's view: which men, what to do, where (and how to stand), or whom to attack, and how
 * ({@link Army#QUEUE}, {@link Army#AGGRESSIVE}).
 */
public record CommandPacket(int[] ids, int command, Vec3 at, float yaw, float width, int formation, int target, int flags) {
    private static final int MAX = 256;

    public static void encode(CommandPacket p, FriendlyByteBuf buf) {
        buf.writeVarIntArray(p.ids);
        buf.writeVarInt(p.command);
        buf.writeDouble(p.at.x);
        buf.writeDouble(p.at.y);
        buf.writeDouble(p.at.z);
        buf.writeFloat(p.yaw);
        buf.writeFloat(p.width);
        buf.writeVarInt(p.formation);
        buf.writeVarInt(p.target);
        buf.writeVarInt(p.flags);
    }

    public static CommandPacket decode(FriendlyByteBuf buf) {
        return new CommandPacket(buf.readVarIntArray(MAX), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CommandPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && Double.isFinite(p.at.x) && Double.isFinite(p.at.y) && Double.isFinite(p.at.z) && Float.isFinite(p.yaw)
                && Float.isFinite(p.width)) {
            Army.command(player, p.ids, Army.Command.byId(p.command), p.at, p.yaw, Math.max(0, p.width), Formation.byId(p.formation), p.target,
                    p.flags);
        }
        ctx.get().setPacketHandled(true);
    }
}
