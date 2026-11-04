package io.github.nicksementsov.druzhina.network;

import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** "Hire a man of this role at this muster table." */
public record HirePacket(BlockPos table, int role) {
    public static void encode(HirePacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.table);
        buf.writeVarInt(p.role);
    }

    public static HirePacket decode(FriendlyByteBuf buf) {
        return new HirePacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(HirePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        Role[] roles = Role.values();
        if (player != null && p.role >= 0 && p.role < roles.length && roles[p.role].hired()) {
            Army.hire(player, p.table, roles[p.role]);
        }
        ctx.get().setPacketHandled(true);
    }
}
