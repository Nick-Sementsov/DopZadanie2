package com.nick.prestol.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.nick.prestol.Prestol;
import com.nick.prestol.client.ui.BookScreen;
import com.nick.prestol.net.Net;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

public final class PrestolClient {
   public static final KeyMapping OPEN_BOOK = new KeyMapping("key.prestol.book", KeyConflictContext.IN_GAME, Type.KEYSYM, 296, "key.categories.prestol");
   private static Runnable worldMap;
   private static Runnable minimap;
   private static int ticks;

   private PrestolClient() {
   }

   public static void init(IEventBus modBus) {
      modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_BOOK));
      worldMap = hook("xaeroworldmap", "com.nick.prestol.client.xaero.WorldMapHook");
      minimap = hook("xaerominimap", "com.nick.prestol.client.xaero.MinimapHook");
      MinecraftForge.EVENT_BUS.addListener(PrestolClient::onTick);
      MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientMap.clear());
   }

   private static Runnable hook(String mod, String cls) {
      if (!ModList.get().isLoaded(mod)) {
         return null;
      } else {
         try {
            Method tick = Class.forName(cls).getMethod("tick");
            Prestol.LOGGER.info("Prestol: {} integration on", mod);
            return () -> {
               try {
                  tick.invoke(null);
               } catch (RuntimeException | ReflectiveOperationException var2x) {
               }
            };
         } catch (LinkageError | ReflectiveOperationException var3) {
            Prestol.LOGGER.error("Prestol: could not hook into {}", mod, var3);
            return null;
         }
      }
   }

   private static void onTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         while (OPEN_BOOK.consumeClick()) {
            if (Minecraft.getInstance().screen == null) {
               openBook("");
            }
         }

         if (++ticks % 10 == 0) {
            if (worldMap != null) {
               worldMap.run();
            }

            if (minimap != null) {
               minimap.run();
            }
         }
      }
   }

   public static boolean hasMapMod() {
      return worldMap != null;
   }

   public static void openBook(String hint) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.getConnection() != null) {
         if (!Net.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) {
            mc.player.displayClientMessage(Component.translatable("prestol.err.no_server_mod"), true);
         } else {
            BookScreen.hint(hint);
            Net.order("open");
         }
      }
   }
}
