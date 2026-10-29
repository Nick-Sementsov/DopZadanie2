package com.nick.prestol;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.mojang.logging.LogUtils;
import com.nick.prestol.army.Army;
import com.nick.prestol.book.Guide;
import com.nick.prestol.book.PrestolCommand;
import com.nick.prestol.client.PrestolClient;
import com.nick.prestol.colony.PrestolHuts;
import com.nick.prestol.trade.TradeEntities;
import com.nick.prestol.trade.TradeWorld;
import com.nick.prestol.compat.HywRules;
import com.nick.prestol.decree.Decrees;
import com.nick.prestol.decree.EffectBoost;
import com.nick.prestol.economy.Treasury;
import com.nick.prestol.flag.BannerImages;
import com.nick.prestol.flag.TownFlags;
import com.nick.prestol.map.MapSync;
import com.nick.prestol.net.Net;
import com.nick.prestol.realm.DerzhavaImport;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.RealmTicker;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.war.Conquest;
import com.nick.prestol.war.Sieges;
import com.nick.prestol.war.Camps;
import com.nick.prestol.realm.Court;
import com.nick.prestol.war.WarBlocks;
import com.nick.prestol.war.Treaties;
import com.nick.prestol.war.WarData;
import com.nick.prestol.war.Wars;
import com.nick.prestol.work.CrewMaterials;
import com.nick.prestol.work.Crews;
import com.nick.prestol.work.OfflineWork;
import com.nick.prestol.work.WorkerFixes;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod("prestol")
public class Prestol {
   public static final String MODID = "prestol";
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final boolean ECO_CREWS = ModList.get().isLoaded("ecominecolonies");

   public Prestol() {
      ModLoadingContext.get().registerConfig(Type.SERVER, PrestolConfig.SPEC, "prestol-server.toml");
      Net.register();
      OfflineWork.registerChunkCallback();
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      modBus.addListener(this::onSetup);
      PrestolHuts.register(modBus);
      TradeEntities.register(modBus);
      WarBlocks.register(modBus);
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.register(new RealmTicker());
      MinecraftForge.EVENT_BUS.register(new MapSync.Events());
      MinecraftForge.EVENT_BUS.register(Conquest.class);
      MinecraftForge.EVENT_BUS.register(Army.class);
      MinecraftForge.EVENT_BUS.register(Guide.class);
      MinecraftForge.EVENT_BUS.register(TradeWorld.class);
      MinecraftForge.EVENT_BUS.register(Camps.class);
      MinecraftForge.EVENT_BUS.register(Court.class);
      if (ModList.get().isLoaded("hundred_years_war")) {
         MinecraftForge.EVENT_BUS.register(HywRules.class);
         modBus.addListener(HywRules::onTabs);
      }

      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PrestolClient.init(modBus));
   }

   private void onSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> {
         try {
            IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyDeletedModEvent.class, e -> {
               MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
               if (server != null && e.getColony() != null) {
                  server.execute(() -> {
                     MapSync.forgetColony(server, e.getColony());
                     Realms.onColonyDeleted(server, e.getColony());
                  });
               }
            });
            IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyCreatedModEvent.class, e -> {
               MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
               if (server != null && e.getColony() != null) {
                  server.execute(() -> Guide.onColonyFounded(server, e.getColony()));
               }
            });
         } catch (LinkageError | RuntimeException var1x) {
            LOGGER.warn("Prestol: could not listen to colony events", var1x);
         }
      });
   }

   @SubscribeEvent
   public void onCommands(RegisterCommandsEvent event) {
      PrestolCommand.register(event.getDispatcher());
   }

   @SubscribeEvent
   public void onServerStarted(ServerStartedEvent event) {
      MinecraftServer server = event.getServer();
      BannerImages.reset();

      try {
         DerzhavaImport.run(server);
      } catch (RuntimeException var4) {
         LOGGER.error("Prestol: could not carry over the old Derzhava data", var4);
      }

      Conquest.reload(RealmData.get(server));
      WarData.restore(server);
      Army.onStart(server);
      Wars.applyHostility(server, RealmData.get(server));
      Realms.changed(server);
      LOGGER.info("Prestol: ready, {} realms, decree effects {}", RealmData.get(server).all().size(), EffectBoost.isAvailable() ? "ok" : "UNAVAILABLE");
   }

   /** Merchants, mules and boats leave the world before it is saved. */
   @SubscribeEvent
   public void onServerStopping(ServerStoppingEvent event) {
      run("trade", () -> TradeWorld.despawnAll(event.getServer()));
   }

   @SubscribeEvent
   public void onServerStopped(ServerStoppedEvent event) {
      Sieges.clear();
      Camps.clear();
      Treaties.clear();
      Conquest.clear();
      Realms.forgetMissing();
      Decrees.clear();
      Army.clear();
      OfflineWork.forgetAll();
      if (ECO_CREWS) {
         CrewMaterials.forgetAll();
      }
   }

   @SubscribeEvent
   public void onServerTick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         MinecraftServer server = event.getServer();
         int t = server.getTickCount() % 20;
         switch (t) {
            case 0:
               run("sieges", () -> Sieges.tick(server));
               run("camps", () -> Camps.tick(server));
            case 1:
            case 3:
            case 5:
            case 7:
            case 9:
            case 11:
            case 12:
            case 13:
            case 14:
            default:
               break;
            case 2:
               run("conquest", () -> Conquest.tick(server));
               break;
            case 4:
               run("map", () -> MapSync.tick(server));
               break;
            case 6:
               run("flags", () -> TownFlags.tick(server));
               break;
            case 8:
               run("decrees", () -> Decrees.tick(server));
               break;
            case 10:
               if ((Boolean)PrestolConfig.AI_FIXES.get()) {
                  run("workers", () -> WorkerFixes.tick(server));
               }
               break;
            case 15:
               if (ECO_CREWS) {
                  run("crews", () -> CrewMaterials.tick(server));
                  if (server.getTickCount() > 1200) {
                     run("crew parts", Crews::tick);
                  }
               }
         }

         if (t == 1) {
            run("treasury", () -> Treasury.tick(server));
         }

         if (t == 7) {
            run("court", () -> Court.tick(server));
         }

         run("trade", () -> TradeWorld.tick(server));

         if (server.getTickCount() % 100 == 12) {
            run("army", () -> Army.tick(server));
         }

         if (server.getTickCount() % 10 == 3) {
            run("inspect", () -> Army.inspect(server));
         }

         if (server.getTickCount() % 200 == 100) {
            run("offline", () -> OfflineWork.tick(server));
         }
      }
   }

   private static void run(String what, Runnable job) {
      try {
         job.run();
      } catch (LinkageError | RuntimeException var3) {
         LOGGER.warn("Prestol: {} tick failed", what, var3);
      }
   }
}
