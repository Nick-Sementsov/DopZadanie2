package com.nick.prestol.colony;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.nick.prestol.Prestol;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** Hut blocks of the Prestol buildings. MineColonies names hut blocks after their hut name in its own namespace. */
final class HutBlocks {
   private HutBlocks() {
   }

   static final class Brewer extends AbstractBlockHut<Brewer> {
      @NotNull
      @Override
      public String getHutName() {
         return "blockhutbrewer";
      }

      @Override
      public BuildingEntry getBuildingEntry() {
         return PrestolHuts.BREWER.get();
      }

      @Override
      public ResourceLocation getRegistryName() {
         return new ResourceLocation(Prestol.MODID, this.getHutName());
      }
   }

   static final class Winemaker extends AbstractBlockHut<Winemaker> {
      @NotNull
      @Override
      public String getHutName() {
         return "blockhutwinemaker";
      }

      @Override
      public BuildingEntry getBuildingEntry() {
         return PrestolHuts.WINEMAKER.get();
      }

      @Override
      public ResourceLocation getRegistryName() {
         return new ResourceLocation(Prestol.MODID, this.getHutName());
      }
   }
}
