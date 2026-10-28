package com.nick.prestol.compat;

import com.google.gson.JsonElement;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.util.Msg;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import ydmsama.hundred_years_war.main.entity.entities.BaseWorkerEntity;

/**
 * Hundred Years War without its worker class: farmers, miners, porters and their workstations belong to
 * MineColonies on this server. Workers cannot be recruited, their scrolls do nothing, their workstation
 * recipes are gone and a worker that shows up in the world (an old save, a spawn command) is sent away.
 */
public final class HywRules {
   private static final Set<String> WORKER_ITEMS = Set.of(
      "scroll_breeder", "scroll_crafter", "scroll_farmer", "scroll_fisher", "scroll_lumberjack", "scroll_miner", "scroll_porter",
      "breeding_workstation", "crafting_workstation", "farming_workstation", "fishing_workstation", "lumber_workstation", "mining_workstation",
      "transport_order", "transport_workstation", "warehouse_control", "warehouse_storage"
   );
   private static final Set<String> WORKER_RECIPES = Set.of(
      "breeding_workstation", "crafting_workstation", "farming_workstation", "fishing_workstation", "lumber_workstation", "mining_workstation",
      "transport_order", "transport_workstation", "warehouse_control", "warehouse_storage", "warehouse_storage_to_chest"
   );
   private static boolean told;

   private HywRules() {
   }

   /** Readable before the server config is loaded (recipes load first): workers are off unless the config says otherwise. */
   public static boolean noWorkers() {
      try {
         return PrestolConfig.NO_HYW_WORKERS.get();
      } catch (IllegalStateException var1) {
         return true;
      }
   }

   private static boolean workerItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
         return id != null && "hundred_years_war".equals(id.getNamespace()) && WORKER_ITEMS.contains(id.getPath());
      }
   }

   /** Called from the recipe manager before recipes are parsed. */
   public static void dropWorkerRecipes(Map<ResourceLocation, JsonElement> recipes) {
      if (noWorkers() && recipes.keySet().removeIf(id -> "hundred_years_war".equals(id.getNamespace()) && WORKER_RECIPES.contains(id.getPath()))) {
         Prestol.LOGGER.info("Prestol: Hundred Years War worker recipes are switched off");
      }
   }

   @SubscribeEvent
   public static void onJoin(EntityJoinLevelEvent event) {
      if (!event.getLevel().isClientSide() && event.getEntity() instanceof BaseWorkerEntity && noWorkers()) {
         event.setCanceled(true);
         if (!told) {
            told = true;
            Prestol.LOGGER.info("Prestol: Hundred Years War workers are removed on this server (army.removeHywWorkers)");
         }
      }
   }

   @SubscribeEvent
   public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
      block(event, event.getItemStack());
   }

   @SubscribeEvent
   public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
      block(event, event.getItemStack());
   }

   @SubscribeEvent
   public static void onUseOnEntity(PlayerInteractEvent.EntityInteract event) {
      block(event, event.getItemStack());
   }

   private static void block(PlayerInteractEvent event, ItemStack stack) {
      if (noWorkers() && workerItem(stack)) {
         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.FAIL);
         if (!event.getLevel().isClientSide()) {
            event.getEntity().displayClientMessage(Msg.bad("army.no_hyw_workers"), true);
         }
      }
   }

   /** Mod-bus listener: the worker items vanish from the creative tab. */
   public static void onTabs(BuildCreativeModeTabContentsEvent event) {
      if (noWorkers()) {
         for (String path : WORKER_ITEMS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("hundred_years_war", path));
            if (item != null) {
               event.getEntries().remove(new ItemStack(item));
            }
         }
      }
   }
}
