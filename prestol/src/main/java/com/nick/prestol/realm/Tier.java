package com.nick.prestol.realm;

import com.nick.prestol.PrestolConfig;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum Tier {
   HAMLET(ChatFormatting.GRAY),
   VILLAGE(ChatFormatting.GREEN),
   TOWN(ChatFormatting.AQUA);

   public final ChatFormatting color;

   private Tier(ChatFormatting color) {
      this.color = color;
   }

   public String key() {
      return "prestol.tier." + this.name().toLowerCase(Locale.ROOT);
   }

   public MutableComponent title() {
      return Component.translatable(this.key());
   }

   public Tier next() {
      return this == TOWN ? null : values()[this.ordinal() + 1];
   }

   public int minCitizens() {
      return switch (this) {
         case HAMLET -> 0;
         case VILLAGE -> PrestolConfig.VILLAGE_CITIZENS.get();
         case TOWN -> PrestolConfig.TOWN_CITIZENS.get();
      };
   }

   public int minTownHall() {
      return switch (this) {
         case HAMLET -> 0;
         case VILLAGE -> PrestolConfig.VILLAGE_HALL.get();
         case TOWN -> PrestolConfig.TOWN_HALL.get();
      };
   }

   public int minBuildings() {
      return switch (this) {
         case HAMLET -> 0;
         case VILLAGE -> PrestolConfig.VILLAGE_BUILDINGS.get();
         case TOWN -> PrestolConfig.TOWN_BUILDINGS.get();
      };
   }

   public static Tier byOrdinal(int i) {
      Tier[] all = values();
      return i >= 0 && i < all.length ? all[i] : HAMLET;
   }
}
