package com.nick.prestol.client.ui;

import com.nick.prestol.client.FlagArt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The chapters of the guide in the realm book. Each paragraph is a translation key
 * ({@code prestol.guide.<chapter>.<n>}) whose numbers come from the server's own rules, and a chapter or a
 * paragraph about a switched-off feature is not shown.
 */
final class GuideChapters {
   static final List<Chapter> ALL = List.of(
      chapter("start", Items.KNOWLEDGE_BOOK, null, p("1", "@key"), p("2"), p("3"), p("4"), p("5"), p("6")),
      chapter(
         "settlements", Items.BELL, null,
         p("1", "villageCitizens", "villageHall", "villageBuildings", "townCitizens", "townHall", "townBuildings"), p("2"), p("3", "mutinyHappiness", "mutinyDays")
      ),
      chapter(
         "realm", Items.GOLDEN_HELMET, null,
         p("1", "capitalCitizens"), p("2", "baseSettlements", "citizensPerSettlement"), p("3", "kingdomPopulation", "kingdomTowns", "empirePopulation", "empireTowns"), p("4")
      ),
      chapter("lands", Items.FILLED_MAP, null, p("1", "landReach"), p("2", "landPerSettlement", "landPerHall", "landPerBuilding")),
      chapter("decrees", Items.PAPER, null, p("1", "capitalBonus"), p("2", "lawLock")),
      chapter(
         "army", Items.SHIELD, "garrisons",
         p("1", "unitsPerLevel"), pIf("conscription", "2"), p("3", "recruitSeconds"), p("4", "trainingMinutes"), p("5")
      ),
      chapter("militia", Items.STONE_SWORD, "militia|mercenaries", pIf("militia", "1", "militiaCitizens"), pIf("mercenaries", "2", "mercenaryGold", "mercenaryMinutes")),
      chapter("provisions", Items.BREAD, "provisions", p("1", "provisionDays"), p("2"), p("3", "desertDays")),
      chapter("morale", Items.GOAT_HORN, "morale", p("1"), p("2", "routBelow"), p("3"), pIf("economy", "4")),
      chapter(
         "war", Items.IRON_SWORD, null,
         p("1", "mobilizeMinutes"), p("2", "siegeRadius", "holdMinutes"), pIf("defenderOnline", "3"), pIf("capitalLast", "4"),
         p("5", "lootMinutes", "slaveDeath", "immunityDays"), p("6", "truceDays"), p("7", "revoltDays", "revoltHappiness", "revoltHoldMinutes"), pIf("hyw", "8"), pIf("weariness", "9", "wearinessHappiness")
      ),
      chapter("treaty", Items.WRITABLE_BOOK, null, p("1"), p("2"), pIf("economy", "3")),
      chapter("fealty", Items.GOLDEN_HELMET, "fealty", p("1"), p("2", "fealtyShare"), p("3")),
      chapter("camps", Items.CAMPFIRE, "camps", p("1", "campRange"), p("2", "campHoldMinutes"), p("3"), pIf("enginesAtWar", "4")),
      chapter("treasury", Items.GOLD_NUGGET, "economy", p("1"), p("2", "taxLow", "taxNormal", "taxHigh"), p("3", "soldierPay"), p("4")),
      chapter(
         "trade", Items.CHEST_MINECART, "trade",
         p("1"), p("2", "caravanCost", "muleCost", "shipCost", "maxTraders"), p("6", "autoRange"), p("3"), p("4", "dwellSeconds"), p("7", "markup"), p("5", "robberyChance", "escortCost")
      ),
      chapter("court", Items.NAME_TAG, "court", p("1"), p("2"), p("3", "outlawDays"), p("4")),
      chapter("banner", FlagArt.flagItem(), null, p("1", "standardWool"), pIf("paintings", "2"), pIf("paintings", "3")),
      chapter("drinks", Items.HONEY_BOTTLE, "brewery|vinery", p("1"), p("2"), p("3"))
   );

   private GuideChapters() {
   }

   private static Chapter chapter(String id, Item icon, String needs, Para... paras) {
      return new Chapter(id, new ItemStack(icon), needs, List.of(paras));
   }

   private static Para p(String n, String... args) {
      return new Para(n, null, args);
   }

   private static Para pIf(String needs, String n, String... args) {
      return new Para(n, needs, args);
   }

   /** {@code needs} is a rule name or several joined with '|' (any of them). Without rules (an old server) everything shows. */
   private static boolean allowed(String needs, CompoundTag rules) {
      if (needs == null || rules.isEmpty()) {
         return true;
      } else {
         for (String n : needs.split("\\|")) {
            if (rules.getBoolean(n)) {
               return true;
            }
         }

         return false;
      }
   }

   static List<Chapter> shown(CompoundTag rules) {
      List<Chapter> out = new ArrayList<>();

      for (Chapter c : ALL) {
         if (allowed(c.needs, rules)) {
            out.add(c);
         }
      }

      return out;
   }

   record Chapter(String id, ItemStack icon, String needs, List<Para> paras) {
      Component title() {
         return Component.translatable("prestol.guide." + this.id);
      }

      List<Component> text(CompoundTag rules) {
         List<Component> out = new ArrayList<>();

         for (Para p : this.paras) {
            if (allowed(p.needs, rules)) {
               Object[] args = new Object[p.args.length];

               for (int i = 0; i < args.length; i++) {
                  String a = p.args[i];
                  args[i] = a.equals("@key") ? Component.keybind("key.prestol.book") : (rules.contains(a) ? String.valueOf(rules.getInt(a)) : "?");
               }

               out.add(Component.translatable("prestol.guide." + this.id + "." + p.n, args));
            }
         }

         return out;
      }
   }

   record Para(String n, String needs, String[] args) {
   }
}
