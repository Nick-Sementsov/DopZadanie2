package com.nick.prestol.economy;

import com.nick.prestol.PrestolConfig;
import java.util.Locale;

/** Tax rate of a realm: the higher, the richer the treasury and the gloomier the people. */
public enum Tax {
   NONE(0.15),
   LOW(0.05),
   NORMAL(0.0),
   HIGH(-0.15);

   /** Change of the MineColonies happiness effect in every settlement of the realm. */
   public final double happiness;

   private Tax(double happiness) {
      this.happiness = happiness;
   }

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public int perCitizen() {
      return switch (this) {
         case NONE -> 0;
         case LOW -> PrestolConfig.TAX_LOW.get();
         case NORMAL -> PrestolConfig.TAX_NORMAL.get();
         case HIGH -> PrestolConfig.TAX_HIGH.get();
      };
   }

   public static Tax byId(String id) {
      for (Tax t : values()) {
         if (t.id().equals(id)) {
            return t;
         }
      }

      return null;
   }
}
