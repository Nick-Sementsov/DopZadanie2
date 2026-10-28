package com.nick.prestol.colony;

import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import org.jetbrains.annotations.NotNull;

public class CraftAI extends AbstractEntityAICrafting<CraftJob, CraftHut> {
   public CraftAI(@NotNull CraftJob job) {
      super(job);
   }

   @Override
   public Class<CraftHut> getExpectedBuildingClass() {
      return CraftHut.class;
   }
}
