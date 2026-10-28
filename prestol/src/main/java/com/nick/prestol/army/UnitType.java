package com.nick.prestol.army;

import java.util.List;
import java.util.Locale;

public enum UnitType {
   WARRIOR("warrior", List.of(Supplies.Need.SWORD)),
   SPEARMAN("spear_man", List.of(Supplies.Need.POLEARM)),
   SHIELDMAN("shieldman", List.of(Supplies.Need.SHIELD, Supplies.Need.SWORD)),
   ARCHER("archer", List.of(Supplies.Need.BOW, Supplies.Need.ARROWS)),
   CROSSBOWMAN("crossbowman", List.of(Supplies.Need.CROSSBOW, Supplies.Need.ARROWS)),
   MILITIA("militia", List.of());

   public static final List<UnitType> GARRISON = List.of(WARRIOR, SPEARMAN, SHIELDMAN, ARCHER, CROSSBOWMAN);
   public final String entity;
   public final List<Supplies.Need> weapons;

   private UnitType(String entity, List<Supplies.Need> weapons) {
      this.entity = entity;
      this.weapons = weapons;
   }

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public String langKey() {
      return "prestol.army.type." + this.id();
   }

   public static UnitType byId(String id) {
      for (UnitType t : values()) {
         if (t.id().equals(id)) {
            return t;
         }
      }

      return null;
   }
}
