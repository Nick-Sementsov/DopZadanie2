package com.nick.prestol.net;

import com.nick.prestol.book.Book;
import com.nick.prestol.client.ClientMap;
import com.nick.prestol.client.ui.BookScreen;
import com.nick.prestol.client.ui.ScrollScreen;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
   private static final String VERSION = "1";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("prestol", "main"), () -> "1", "1"::equals, "1"::equals);

   private Net() {
   }

   public static void register() {
      CHANNEL.registerMessage(
         0, Net.BookState.class, Net.BookState::write, Net.BookState::read, Net.BookState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)
      );
      CHANNEL.registerMessage(1, Net.Order.class, Net.Order::write, Net.Order::read, Net.Order::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      CHANNEL.registerMessage(2, Net.MapData.class, Net.MapData::write, Net.MapData::read, Net.MapData::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(3, Net.Choice.class, Net.Choice::write, Net.Choice::read, Net.Choice::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static boolean hasMod(ServerPlayer player) {
      return player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
   }

   public static void sendBook(ServerPlayer player, CompoundTag state, boolean open) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Net.BookState(state, open));
   }

   public static void sendMap(ServerPlayer player, byte[] bytes) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Net.MapData(bytes));
   }

   public static void openChoice(ServerPlayer player, String key, String name, int seconds) {
      if (hasMod(player)) {
         CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Net.Choice(key, name, seconds));
      }
   }

   public static void order(String order) {
      CHANNEL.sendToServer(new Net.Order(order));
   }

   public static record BookState(CompoundTag state, boolean open) {
      void write(FriendlyByteBuf buf) {
         buf.writeNbt(this.state);
         buf.writeBoolean(this.open);
      }

      static Net.BookState read(FriendlyByteBuf buf) {
         return new Net.BookState(buf.readNbt(), buf.readBoolean());
      }

      void handle(Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Net.ClientHandoff.book(this.state, this.open)));
         ctx.get().setPacketHandled(true);
      }
   }

   public static record Choice(String key, String name, int seconds) {
      void write(FriendlyByteBuf buf) {
         buf.writeUtf(this.key);
         buf.writeUtf(this.name);
         buf.writeVarInt(this.seconds);
      }

      static Net.Choice read(FriendlyByteBuf buf) {
         return new Net.Choice(buf.readUtf(), buf.readUtf(), buf.readVarInt());
      }

      void handle(Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Net.ClientHandoff.choice(this.key, this.name, this.seconds)));
         ctx.get().setPacketHandled(true);
      }
   }

   static final class ClientHandoff {
      private ClientHandoff() {
      }

      static void book(CompoundTag state, boolean open) {
         BookScreen.receive(state, open);
      }

      static void map(byte[] bytes) {
         ClientMap.accept(bytes);
      }

      static void choice(String key, String name, int seconds) {
         ScrollScreen.open(key, name, seconds);
      }
   }

   public static record MapData(byte[] bytes) {
      void write(FriendlyByteBuf buf) {
         buf.writeByteArray(this.bytes);
      }

      static Net.MapData read(FriendlyByteBuf buf) {
         return new Net.MapData(buf.readByteArray(8388608));
      }

      void handle(Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Net.ClientHandoff.map(this.bytes)));
         ctx.get().setPacketHandled(true);
      }
   }

   public static record Order(String order) {
      void write(FriendlyByteBuf buf) {
         buf.writeUtf(this.order, 512);
      }

      static Net.Order read(FriendlyByteBuf buf) {
         return new Net.Order(buf.readUtf(512));
      }

      void handle(Supplier<Context> ctx) {
         ServerPlayer player = ctx.get().getSender();
         ctx.get().enqueueWork(() -> {
            if (player != null) {
               Book.order(player, this.order);
            }
         });
         ctx.get().setPacketHandled(true);
      }
   }
}
