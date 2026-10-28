package com.nick.prestol.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.NotNull;

public class CraftJob extends AbstractJobCrafter<CraftAI, CraftJob> {
   private final SoundEvent sound;

   public CraftJob(ICitizenData citizen, SoundEvent sound) {
      super(citizen);
      this.sound = sound;
   }

   @NotNull
   @Override
   public CraftAI generateAI() {
      return new CraftAI(this);
   }

   @Override
   public void playSound(BlockPos pos, EntityCitizen worker) {
      worker.queueSound(this.sound, pos, 5, 1, 0.6F, 1.0F);
   }
}
