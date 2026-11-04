package com.nick.prestol.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * A MineColonies crafter hut of Prestol (brewer, winemaker). The worker knows only the recipes that Prestol ships
 * for him (the crafter recipes in packs/letsdo_*); the finer drinks open up as the hut is upgraded.
 */
public class CraftHut extends AbstractBuilding {
   static final int MAX_LEVEL = 3;
   private final String schematic;

   public CraftHut(IColony colony, BlockPos pos, String schematic) {
      super(colony, pos);
      this.schematic = schematic;
   }

   @NotNull
   @Override
   public String getSchematicName() {
      return this.schematic;
   }

   @Override
   public int getMaxBuildingLevel() {
      return MAX_LEVEL;
   }

   /** Recipes come only from the crafter recipe files: the worker cannot be taught new ones. */
   public static class CraftingModule extends AbstractCraftingBuildingModule.Custom {
      public CraftingModule(JobEntry job) {
         super(job);
      }
   }
}
