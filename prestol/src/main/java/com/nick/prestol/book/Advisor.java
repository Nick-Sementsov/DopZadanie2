package com.nick.prestol.book;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Rank;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.trade.Trading;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advisor at the front of the book: a few plain words on what to do next, worked out from the state of the
 * player's lands — the most pressing first — each with the page where it is done, and the realm's next rank as the
 * long goal.
 */
final class Advisor {
   private static final int SHOWN = 3;

   private Advisor() {
   }

   private record Tip(int prio, String key, List<String> args, String page, String icon) {
      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putString("k", this.key);
         ListTag a = new ListTag();

         for (String s : this.args) {
            a.add(StringTag.valueOf(s));
         }

         t.put("a", a);
         t.putString("p", this.page);
         t.putString("i", this.icon);
         return t;
      }
   }

   /** Advice for {@code player}, read off the book state {@code tag} that is being built for him. */
   static ListTag advise(MinecraftServer server, ServerPlayer player, Realm realm, CompoundTag tag) {
      List<Tip> tips = new ArrayList<>();
      List<CompoundTag> settlements = list(tag, "settlements");
      List<CompoundTag> mine = new ArrayList<>();

      for (CompoundTag s : settlements) {
         if (s.getBoolean("mine")) {
            mine.add(s);
         }
      }

      if (realm == null) {
         if (mine.isEmpty()) {
            tips.add(new Tip(0, "no_colony", List.of(), "GUIDE", "minecolonies:blockhuttownhall"));
         } else {
            boolean invited = false;

            for (CompoundTag f : list(tag, "free")) {
               if (f.getBoolean("mine") && !list(f, "invites").isEmpty()) {
                  tips.add(new Tip(1, "invited", List.of(list(f, "invites").get(0).getString("name")), "REALM", "minecraft:writable_book"));
                  invited = true;
                  break;
               }
            }

            tips.add(new Tip(invited ? 2 : 1, "found", List.of(), "REALM", "minecraft:golden_helmet"));
            grow(tips, mine);
         }
      } else {
         boolean ruler = tag.getCompound("realm").getBoolean("ruler");
         List<CompoundTag> ours = new ArrayList<>();

         for (CompoundTag s : settlements) {
            if (realm.name().equals(s.getString("realm"))) {
               ours.add(s);
            }
         }

         // under siege: nothing is more urgent
         for (CompoundTag s : ours) {
            if (s.getInt("siege") != 0) {
               tips.add(new Tip(0, "besieged", List.of(s.getString("name")), "WAR", "minecraft:iron_sword"));
            }
         }

         // the treasury cannot pay the soldiers
         CompoundTag tr = tag.getCompound("treasury");
         if (tr.getInt("unpaid") > 0 || tr.contains("pay") && tr.getLong("pay") > tr.getLong("coins") + tr.getLong("income")) {
            tips.add(new Tip(1, "unpaid", List.of(), "TRADE", "minecraft:gold_nugget"));
         }

         // garrisons
         List<CompoundTag> army = list(tag, "army");
         if (tag.getBoolean("armyOn")) {
            for (CompoundTag s : ours) {
               boolean tower = false;

               for (CompoundTag t : army) {
                  tower |= t.getString("settlement").equals(s.getString("name"));
               }

               if (!tower) {
                  tips.add(new Tip(4, "no_tower", List.of(s.getString("name")), "ARMY", "minecolonies:blockhutguardtower"));
               }
            }

            for (CompoundTag t : army) {
               ListTag lack = t.getList("lack", 8);
               if (!lack.isEmpty() && t.getInt("have") < t.getInt("slots")) {
                  tips.add(new Tip(3, "tower_lack", List.of(t.getString("settlement"), lackText(lack)), "ARMY", "minecraft:iron_sword"));
               }
            }
         }

         grow(tips, ours);
         // trade
         if (tag.contains("trade")) {
            CompoundTag trade = tag.getCompound("trade");
            if (list(trade, "traders").isEmpty() && trade.getBoolean("ruler")) {
               IColony cap = Settlements.byKey(realm.capital());
               boolean store = cap != null && Trading.hasWarehouse(cap);
               tips.add(new Tip(6, store ? "hire" : "warehouse", List.of(cap == null ? "?" : cap.getName()), "TRADE", store ? "minecraft:lead" : "minecolonies:blockhutwarehouse"));
            }
         }

         // land
         CompoundTag land = tag.getCompound("land");
         if (land.getInt("limit") > 0 && land.getInt("used") * 2 < land.getInt("limit")) {
            tips.add(new Tip(7, "land", List.of(String.valueOf(land.getInt("limit") - land.getInt("used"))), "LANDS", "minecraft:filled_map"));
         }

         // laws and edicts
         CompoundTag r = tag.getCompound("realm");
         int free = r.getInt("lawSlots") - r.getList("laws", 8).size();
         if (ruler && free > 0) {
            tips.add(new Tip(8, "law", List.of(String.valueOf(free)), "DECREES", "minecraft:writable_book"));
         }

         for (CompoundTag s : ours) {
            if (s.contains("decrees")) {
               boolean any = false;

               for (CompoundTag d : list(s, "decrees")) {
                  any |= d.contains("left") || d.contains("cooldown");
               }

               if (!any) {
                  tips.add(new Tip(9, "edict", List.of(s.getString("name")), "DECREES", "minecraft:paper"));
                  break;
               }
            }
         }

         // free settlements to call in
         if (r.getBoolean("chancellor")) {
            for (CompoundTag f : list(tag, "free")) {
               if (!f.getBoolean("mine") && !f.getBoolean("invited")) {
                  tips.add(new Tip(10, "invite", List.of(f.getString("name")), "SETTLEMENTS", "minecraft:bell"));
                  break;
               }
            }
         }

         // friends
         boolean ally = false;
         boolean others = false;

         for (CompoundTag o : list(tag, "realms")) {
            others = true;
            ally |= o.getString("stance").equals("ally") || o.getString("stance").equals("vassal") || o.getString("stance").equals("liege");
         }

         if (ruler && others && !ally) {
            tips.add(new Tip(11, "ally", List.of(), "WAR", "minecraft:emerald"));
         }
      }

      tips.sort(Comparator.comparingInt(Tip::prio));
      ListTag out = new ListTag();

      for (int i = 0; i < tips.size() && i < SHOWN; i++) {
         out.add(tips.get(i).save());
      }

      if (out.isEmpty()) {
         out.add(new Tip(99, "all_good", List.of(), "", "minecraft:cake").save());
      }

      return out;
   }

   /** The settlement nearest its next step up, and what it still lacks. */
   private static void grow(List<Tip> tips, List<CompoundTag> settlements) {
      CompoundTag best = null;
      double bestDone = -1.0;

      for (CompoundTag s : settlements) {
         if (s.contains("needCitizens")) {
            double done = part(s.getInt("citizens"), s.getInt("needCitizens")) + part(s.getInt("hall"), s.getInt("needHall")) + part(s.getInt("buildings"), s.getInt("needBuildings"));
            if (done > bestDone) {
               bestDone = done;
               best = s;
            }
         }
      }

      if (best != null) {
         String next = new String[]{"hamlet", "village", "town"}[Math.min(2, best.getInt("tier") + 1)];
         tips.add(
            new Tip(
               5,
               "grow",
               List.of(
                  best.getString("name"),
                  "prestol.tier." + next,
                  best.getInt("citizens") + "/" + best.getInt("needCitizens"),
                  best.getInt("hall") + "/" + best.getInt("needHall"),
                  best.getInt("buildings") + "/" + best.getInt("needBuildings")
               ),
               "SETTLEMENTS",
               "minecraft:oak_door"
            )
         );
      }
   }

   private static double part(int have, int need) {
      return need <= 0 ? 1.0 : Math.min(1.0, (double)have / need);
   }

   /** What a tower lacks first, as the key of its name (the book words it). */
   private static String lackText(ListTag lack) {
      return "prestol.army.need." + lack.getString(0).toLowerCase(java.util.Locale.ROOT);
   }

   /** The realm's next rank and how far it is: population and towns, each as have/need. */
   static CompoundTag goal(Realm realm) {
      CompoundTag g = new CompoundTag();
      Rank now = Realms.rank(realm);
      Rank next = now == Rank.PRINCIPALITY ? Rank.KINGDOM : (now == Rank.KINGDOM ? Rank.EMPIRE : null);
      if (next != null) {
         g.putString("rank", next.key());
         g.putInt("pop", Realms.population(realm));
         g.putInt("popNeed", next == Rank.KINGDOM ? PrestolConfig.KINGDOM_POPULATION.get() : PrestolConfig.EMPIRE_POPULATION.get());
         g.putInt("towns", Realms.towns(realm));
         g.putInt("townsNeed", next == Rank.KINGDOM ? PrestolConfig.KINGDOM_TOWNS.get() : PrestolConfig.EMPIRE_TOWNS.get());
         g.putInt("laws", next.lawSlots);
      }

      return g;
   }

   private static List<CompoundTag> list(CompoundTag tag, String key) {
      List<CompoundTag> out = new ArrayList<>();

      for (Tag t : tag.getList(key, 10)) {
         out.add((CompoundTag)t);
      }

      return out;
   }
}
