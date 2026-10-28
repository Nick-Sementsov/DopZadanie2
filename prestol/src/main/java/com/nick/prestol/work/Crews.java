package com.nick.prestol.work;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.nick.prestol.Prestol;

public final class Crews {
   private static boolean broken;

   private Crews() {
   }

   public static void tick() {
      if (Prestol.ECO_CREWS && !broken) {
         try {
            CrewTogether.tick();
         } catch (LinkageError | RuntimeException var1) {
            broken = true;
            Prestol.LOGGER.warn("Prestol: crew sharing switched off", var1);
         }
      }
   }

   public static boolean atSite(AbstractEntityCitizen worker) {
      if (Prestol.ECO_CREWS && !broken && worker != null) {
         try {
            return CrewTogether.atSite(worker);
         } catch (LinkageError | RuntimeException var2) {
            broken = true;
            Prestol.LOGGER.warn("Prestol: cannot tell crew builders apart; crews walk home as before", var2);
            return false;
         }
      } else {
         return false;
      }
   }
}
