package com.nick.prestol.decree;

import com.minecolonies.api.research.util.ResearchConstants;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public enum LawType {
   LEVY(
      "levy",
      new DecreeType.Boost(ResearchConstants.MELEE_DAMAGE, 0.5, "melee", false),
      new DecreeType.Boost(ResearchConstants.ARCHER_DAMAGE, 0.5, "archery", false),
      new DecreeType.Boost(ResearchConstants.HEALTH_BOOST, 2.0, "guard_health", false)
   ),
   GUILDS(
      "guilds",
      new DecreeType.Boost(ResearchConstants.RECIPES, 0.25, "crafting", true),
      new DecreeType.Boost(ResearchConstants.LEVELING, 0.1, "leveling", true)
   ),
   FIELDS("fields", new DecreeType.Boost(ResearchConstants.FARMING, 0.25, "farming", true), new DecreeType.Boost(ResearchConstants.GROWTH, 0.1, "growth", true)),
   ROADS(
      "roads",
      new DecreeType.Boost(ResearchConstants.WALKING, 0.1, "walking", true),
      new DecreeType.Boost(ResearchConstants.BLOCK_PLACE_SPEED, 0.1, "building", true)
   ),
   CHARTER(
      "charter",
      new DecreeType.Boost(ResearchConstants.HAPPINESS, 0.1, "happiness", true),
      new DecreeType.Boost(ResearchConstants.CITIZEN_CAP, 5.0, "citizen_cap", false)
   ),
   SCHOOLS(
      "schools",
      new DecreeType.Boost(ResearchConstants.TEACHING, 0.25, "teaching", true),
      new DecreeType.Boost(ResearchConstants.LEVELING, 0.1, "leveling", true)
   );

   public final String id;
   public final List<DecreeType.Boost> boosts;

   private LawType(String id, DecreeType.Boost... boosts) {
      this.id = id;
      this.boosts = List.of(boosts);
   }

   public String key() {
      return "prestol.law." + this.id;
   }

   public static LawType byId(String id) {
      if (id == null) {
         return null;
      } else {
         String k = id.toLowerCase(Locale.ROOT);

         for (LawType t : values()) {
            if (t.id.equals(k)) {
               return t;
            }
         }

         return null;
      }
   }

   public static List<LawType> effective(Collection<LawType> enacted, int slots) {
      List<LawType> out = new ArrayList<>();

      for (LawType law : enacted) {
         if (out.size() >= Math.max(0, slots)) {
            break;
         }

         out.add(law);
      }

      return out;
   }
}
