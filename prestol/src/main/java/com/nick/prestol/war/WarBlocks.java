package com.nick.prestol.war;

import com.nick.prestol.Prestol;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The blocks of war: the war tent of a siege camp. */
public final class WarBlocks {
   private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Prestol.MODID);
   private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Prestol.MODID);
   public static final RegistryObject<Block> CAMP = BLOCKS.register(
      "siege_camp",
      () -> new CampBlock(
            BlockBehaviour.Properties.of()
               .mapColor(MapColor.COLOR_RED)
               .strength(1.0F)
               .sound(SoundType.WOOL)
               .noOcclusion()
               .ignitedByLava()
               .pushReaction(PushReaction.DESTROY)
         )
   );
   public static final RegistryObject<Item> CAMP_ITEM = ITEMS.register("siege_camp", () -> new BlockItem(CAMP.get(), new Item.Properties().stacksTo(1)));

   private WarBlocks() {
   }

   public static void register(IEventBus modBus) {
      BLOCKS.register(modBus);
      ITEMS.register(modBus);
      modBus.addListener(WarBlocks::onTabs);
   }

   private static void onTabs(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
         event.accept(CAMP_ITEM);
      }
   }
}
