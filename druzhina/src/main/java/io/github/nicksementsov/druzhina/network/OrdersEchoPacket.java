package io.github.nicksementsov.druzhina.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * From the server to the commander's view: where the men just ordered are going. For each man his place (or none,
 * when he does not go to one), and the places the march passes through, the present one first.
 */
public record OrdersEchoPacket(int[] ids, List<Vec3> posts, List<Vec3> route) {
    private static final int MAX = 256;

    public static void encode(OrdersEchoPacket p, FriendlyByteBuf buf) {
        buf.writeVarIntArray(p.ids);
        for (int i = 0; i < p.ids.length; i++) {
            Vec3 v = p.posts.get(i);
            buf.writeBoolean(v != null);
            if (v != null) {
                write(buf, v);
            }
        }
        buf.writeVarInt(p.route.size());
        p.route.forEach(v -> write(buf, v));
    }

    public static OrdersEchoPacket decode(FriendlyByteBuf buf) {
        int[] ids = buf.readVarIntArray(MAX);
        List<Vec3> posts = new ArrayList<>(ids.length);
        for (int i = 0; i < ids.length; i++) {
            posts.add(buf.readBoolean() ? read(buf) : null);
        }
        int n = Math.min(buf.readVarInt(), 64);
        List<Vec3> route = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            route.add(read(buf));
        }
        return new OrdersEchoPacket(ids, posts, route);
    }

    private static void write(FriendlyByteBuf buf, Vec3 v) {
        buf.writeFloat((float) v.x);
        buf.writeFloat((float) v.y);
        buf.writeFloat((float) v.z);
    }

    private static Vec3 read(FriendlyByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Nullable
    public Vec3 post(int i) {
        return this.posts.get(i);
    }

    public static void handle(OrdersEchoPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.nicksementsov.druzhina.client.CommandView.echo(p)));
        ctx.get().setPacketHandled(true);
    }
}
