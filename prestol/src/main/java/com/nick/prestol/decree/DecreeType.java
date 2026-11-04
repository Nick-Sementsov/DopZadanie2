package com.nick.prestol.decree;

import com.minecolonies.api.research.util.ResearchConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public enum DecreeType {
   RUSH(
      "rush",
      10,
      40,
      new DecreeType.Boost(ResearchConstants.BLOCK_BREAK_SPEED, 0.5, "mining", true),
      new DecreeType.Boost(ResearchConstants.BLOCK_PLACE_SPEED, 0.5, "building", true),
      new DecreeType.Boost(ResearchConstants.WALKING, 0.15, "walking", true),
      new DecreeType.Boost(ResearchConstants.FARMING, 0.25, "farming", true),
      new DecreeType.Boost(ResearchConstants.RECIPES, 0.25, "crafting", true)
   ),
   BUILD(
      "build",
      10,
      40,
      new DecreeType.Boost(ResearchConstants.BLOCK_PLACE_SPEED, 1.0, "building", true),
      new DecreeType.Boost(ResearchConstants.BLOCK_BREAK_SPEED, 0.5, "mining", true),
      new DecreeType.Boost(ResearchConstants.WALKING, 0.1, "walking", true)
   ),
   HARVEST(
      "harvest",
      15,
      45,
      new DecreeType.Boost(ResearchConstants.FARMING, 0.75, "farming", true),
      new DecreeType.Boost(ResearchConstants.GROWTH, 0.5, "growth", true)
   ),
   FESTIVAL(
      "festival",
      10,
      30,
      new DecreeType.Boost(ResearchConstants.HAPPINESS, 0.2, "happiness", true),
      new DecreeType.Boost(ResearchConstants.GROWTH, 0.25, "growth", true),
      new DecreeType.Boost(ResearchConstants.LEVELING, 0.1, "leveling", true)
   ),
   ENLIGHTEN(
      "enlighten",
      20,
      60,
      new DecreeType.Boost(ResearchConstants.LEVELING, 0.5, "leveling", true),
      new DecreeType.Boost(ResearchConstants.TEACHING, 0.5, "teaching", true)
   ),
   MOBILIZE(
      "mobilize",
      15,
      60,
      new DecreeType.Boost(ResearchConstants.HEALTH_BOOST, 4.0, "guard_health", false),
      new DecreeType.Boost(ResearchConstants.MELEE_DAMAGE, 1.0, "melee", false),
      new DecreeType.Boost(ResearchConstants.ARCHER_DAMAGE, 1.0, "archery", false),
      new DecreeType.Boost(ResearchConstants.MELEE_ARMOR, 0.1, "armor", true),
      new DecreeType.Boost(ResearchConstants.REGENERATION, 0.25, "regeneration", true)
   ),
   MINING(
      "mining",
      10,
      45,
      new DecreeType.Boost(ResearchConstants.BLOCK_BREAK_SPEED, 1.0, "mining", true),
      new DecreeType.Boost(ResearchConstants.MORE_ORES, 0.25, "ores", true),
      new DecreeType.Boost(ResearchConstants.WALKING, 0.1, "walking", true)
   ),
   CRAFTS(
      "crafts",
      10,
      40,
      new DecreeType.Boost(ResearchConstants.RECIPES, 0.5, "crafting", true),
      new DecreeType.Boost(ResearchConstants.LEVELING, 0.25, "leveling", true)
   ),
   MEDIC(
      "medic",
      15,
      45,
      new DecreeType.Boost(ResearchConstants.REGENERATION, 0.5, "regeneration", true),
      new DecreeType.Boost(ResearchConstants.SATURATION, 0.1, "saturation", true),
      new DecreeType.Boost(ResearchConstants.HEALTH_BOOST, 2.0, "health", false)
   ),
   SETTLERS(
      "settlers",
      20,
      90,
      new DecreeType.Boost(ResearchConstants.CITIZEN_CAP, 10.0, "citizen_cap", false),
      new DecreeType.Boost(ResearchConstants.HAPPINESS, 0.05, "happiness", true)
   );

   public final String id;
   public final int defaultDuration;
   public final int defaultCooldown;
   public final List<DecreeType.Boost> boosts;

   private DecreeType(String id, int duration, int cooldown, DecreeType.Boost... boosts) {
      this.id = id;
      this.defaultDuration = duration;
      this.defaultCooldown = cooldown;
      this.boosts = List.of(boosts);
   }

   public String key() {
      return "prestol.decree." + this.id;
   }

   public static DecreeType byId(String id) {
      if (id == null) {
         return null;
      } else {
         String k = id.toLowerCase(Locale.ROOT);

         for (DecreeType t : values()) {
            if (t.id.equals(k)) {
               return t;
            }
         }

         return null;
      }
   }

   public static record Boost(ResourceLocation effect, double amount, String label, boolean percent) {
   }
}
