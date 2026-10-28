package com.nick.prestol.colony;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry.ModuleProducer;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.creativetab.ModCreativeTabs;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import com.minecolonies.core.colony.jobs.views.CrafterJobView;
import com.nick.prestol.Prestol;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * MineColonies huts for the Let's Do drinks: the brewer (Let's Do Brewery) and the winemaker (Let's Do Vinery).
 * Each one exists only when its Let's Do mod is installed: then the hut block, the building, the job, the structure
 * pack (blueprints/prestol/brewery|winery) and the data pack with the worker's recipes (packs/letsdo_*) appear.
 */
public final class PrestolHuts {
   public static final boolean BREWERY = ModList.get().isLoaded("brewery");
   public static final boolean VINERY = ModList.get().isLoaded("vinery");
   private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Prestol.MODID);
   private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Prestol.MODID);
   private static final DeferredRegister<BuildingEntry> BUILDINGS = DeferredRegister.create(new ResourceLocation("minecolonies", "buildings"), Prestol.MODID);
   private static final DeferredRegister<JobEntry> JOBS = DeferredRegister.create(new ResourceLocation("minecolonies", "jobs"), Prestol.MODID);
   private static final List<RegistryObject<? extends Block>> HUT_BLOCKS = new ArrayList<>();
   private static final List<RegistryObject<Item>> HUT_ITEMS = new ArrayList<>();
   static RegistryObject<BuildingEntry> BREWER;
   static RegistryObject<BuildingEntry> WINEMAKER;

   private PrestolHuts() {
   }

   public static void register(IEventBus modBus) {
      if (BREWERY || VINERY) {
         // module producers are numbered in the order they are made, the same on the server and the client:
         // let MineColonies number its own first
         BuildingModules.STATS_MODULE.getRuntimeID();
         if (BREWERY) {
            BREWER = hut("brewer", HutBlocks.Brewer::new, Skill.Knowledge, Skill.Focus, SoundEvents.BREWING_STAND_BREW);
         }

         if (VINERY) {
            WINEMAKER = hut("winemaker", HutBlocks.Winemaker::new, Skill.Creativity, Skill.Focus, SoundEvents.BOTTLE_FILL);
         }

         BLOCKS.register(modBus);
         ITEMS.register(modBus);
         BUILDINGS.register(modBus);
         JOBS.register(modBus);
         modBus.addListener(PrestolHuts::onSetup);
         modBus.addListener(PrestolHuts::onTabs);
         modBus.addListener(PrestolHuts::onPacks);
      }
   }

   private static <B extends AbstractBlockHut<B>> RegistryObject<BuildingEntry> hut(
      String name, Supplier<B> block, Skill primary, Skill secondary, SoundEvent sound
   ) {
      ResourceLocation id = new ResourceLocation(Prestol.MODID, name);
      RegistryObject<B> hutBlock = BLOCKS.register("blockhut" + name, block);
      HUT_BLOCKS.add(hutBlock);
      HUT_ITEMS.add(ITEMS.register("blockhut" + name, () -> new ItemBlockHut(hutBlock.get(), new Item.Properties())));
      RegistryObject<JobEntry> job = JOBS.register(
         name,
         () -> new JobEntry.Builder().setJobProducer(citizen -> new CraftJob(citizen, sound)).setJobViewProducer(() -> CrafterJobView::new).setRegistryName(id).createJobEntry()
      );
      ModuleProducer<CraftingWorkerBuildingModule, WorkerBuildingModuleView> work = new ModuleProducer<>(
         "prestol_" + name + "_work", () -> new CraftingWorkerBuildingModule(job.get(), primary, secondary, false, b -> 1), () -> WorkerBuildingModuleView::new
      );
      ModuleProducer<CraftHut.CraftingModule, CraftingModuleView> craft = new ModuleProducer<>(
         "prestol_" + name + "_craft", () -> new CraftHut.CraftingModule(job.get()), () -> CraftingModuleView::new
      );
      return BUILDINGS.register(
         name,
         () -> new BuildingEntry.Builder()
               .setBuildingBlock(hutBlock.get())
               .setBuildingProducer((colony, pos) -> new CraftHut(colony, pos, name))
               .setBuildingViewProducer(() -> EmptyView::new)
               .setRegistryName(id)
               .addBuildingModuleProducer(work)
               .addBuildingModuleProducer(craft)
               .addBuildingModuleProducer(BuildingModules.SETTINGS_CRAFTER_RECIPE)
               .addBuildingModuleProducer(BuildingModules.CRAFT_TASK_VIEW)
               .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
               .createBuildingEntry()
      );
   }

   private static void onSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(PrestolHuts::allowHutEntities);
   }

   /** MineColonies lists the blocks of its building block entity once, from its own huts: add ours. */
   @SuppressWarnings("unchecked")
   private static void allowHutEntities() {
      try {
         BlockEntityType<?> type = MinecoloniesTileEntities.BUILDING.get();
         Field validBlocks = ObfuscationReflectionHelper.findField(BlockEntityType.class, "f_58915_");
         Set<Block> blocks = new HashSet<>((Set<Block>)validBlocks.get(type));

         for (RegistryObject<? extends Block> hut : HUT_BLOCKS) {
            blocks.add(hut.get());
         }

         validBlocks.set(type, blocks);
      } catch (ReflectiveOperationException | RuntimeException var4) {
         Prestol.LOGGER.error("Prestol: the brewer and winemaker huts could not be attached to MineColonies buildings", var4);
      }
   }

   private static void onTabs(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey().equals(ModCreativeTabs.HUTS.getKey())) {
         for (RegistryObject<Item> item : HUT_ITEMS) {
            event.accept(item);
         }
      }
   }

   private static void onPacks(AddPackFindersEvent event) {
      if (event.getPackType() == PackType.SERVER_DATA) {
         if (BREWERY) {
            addPack(event, "letsdo_brewery", "Prestol: brewer");
         }

         if (VINERY) {
            addPack(event, "letsdo_vinery", "Prestol: winemaker");
         }
      }
   }

   private static void addPack(AddPackFindersEvent event, String name, String title) {
      Path path = ModList.get().getModFileById(Prestol.MODID).getFile().findResource("packs", name);
      Pack pack = Pack.readMetaAndCreate(
         "prestol:" + name, Component.literal(title), true, id -> new PathPackResources(id, path, true), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN
      );
      if (pack != null) {
         event.addRepositorySource(packs -> packs.accept(pack));
      } else {
         Prestol.LOGGER.error("Prestol: the data pack {} is missing from the mod file", name);
      }
   }
}
