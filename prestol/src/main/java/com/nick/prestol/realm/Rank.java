package com.nick.prestol.realm;

import com.nick.prestol.PrestolConfig;
import java.util.Locale;

public enum Rank {
   PRINCIPALITY(1),
   KINGDOM(2),
   EMPIRE(3);

   public final int lawSlots;

   private Rank(int lawSlots) {
      this.lawSlots = lawSlots;
   }

   public String key() {
      return "prestol.rank." + this.name().toLowerCase(Locale.ROOT);
   }

   public static Rank of(int population, int towns) {
      if (population >= (Integer)PrestolConfig.EMPIRE_POPULATION.get() && towns >= (Integer)PrestolConfig.EMPIRE_TOWNS.get()) {
         return EMPIRE;
      } else {
         return population >= PrestolConfig.KINGDOM_POPULATION.get() && towns >= PrestolConfig.KINGDOM_TOWNS.get() ? KINGDOM : PRINCIPALITY;
      }
   }
}
