package com.nick.prestol.trade;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.resolver.IQueuedRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.nick.prestol.army.Supplies;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Settlements;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;

/**
 * The markets of the settlements. Besides what its owner writes up himself, a settlement of a realm trades on its
 * own: it sells what lies in its warehouse beyond its own needs, cheaper the more of it there is, and buys dear
 * what its people ask for and nobody in the colony can give them (the requests MineColonies hands to the player)
 * and food when the larder runs low.
 */
public final class Markets {
   /** How long a worked-out market holds, in ticks. */
   private static final long FRESH = 1200L;
   /** Lines of each kind a settlement offers on its own. */
   private static final int AUTO_LINES = 12;
   private static final double NEED_MARKUP = 1.5;
   private static final double FOOD_MARKUP = 1.25;
   /** Food a settlement keeps per citizen before it sells any. */
   private static final int FOOD_PER_CITIZEN = 12;
   private static final int KEEP = 32;
   private static final Item[] FOODS = {
      Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN,
      Items.COOKED_COD, Items.COOKED_SALMON, Items.CARROT, Items.APPLE, Items.POTATO, Items.WHEAT
   };
   private static final Map<String, Market> CACHE = new HashMap<>();

   private Markets() {
   }

   /**
    * What a settlement has and wants just now: {@code needs} are the goods its people ask for by name, {@code
    * hunger} the food (any food) its larder lacks.
    */
   public record Market(List<Offer> offers, Map<Item, Integer> stock, Map<Item, Integer> surplus, Map<Item, Integer> needs, int hunger, long at) {
      public Offer find(Item item, boolean sell) {
         String id = Cargo.id(item);

         for (Offer o : this.offers) {
            if (o.sell() == sell && o.item().equals(id)) {
               return o;
            }
         }

         return null;
      }

      public int stockOf(Item item) {
         return this.stock.getOrDefault(item, 0);
      }
   }

   public static void clear() {
      CACHE.clear();
   }

   /** Forgets what is known of a settlement after goods came or went. */
   static void touched(String key) {
      CACHE.remove(key);
   }

   public static Market of(MinecraftServer server, String key) {
      long now = server.overworld().getGameTime();
      Market m = CACHE.get(key);
      if (m == null || now - m.at() > FRESH) {
         m = work(server, key, now);
         CACHE.put(key, m);
      }

      return m;
   }

   /** The offers a foreign trader meets in a settlement: its owner's own lines first, then what it trades by itself. */
   public static List<Offer> offers(MinecraftServer server, String key) {
      return of(server, key).offers();
   }

   private static Market work(MinecraftServer server, String key, long now) {
      IColony colony = Settlements.byKey(key);
      TradeData data = TradeData.get(server);
      List<Offer> manual = data.market(key);
      Realm realm = RealmData.get(server).byMember(key);
      if (colony == null || realm == null) {
         return new Market(List.copyOf(manual), Map.of(), Map.of(), Map.of(), 0, now);
      } else {
         List<IItemHandler> stores = Supplies.stores(colony, null);
         Map<Item, Integer> stock = stock(stores);
         Map<Item, Integer> needs = needs(colony, stock);
         int hunger = hunger(colony, stock);
         Map<Item, Integer> surplus = surplus(colony, stock, needs);
         List<Offer> offers = new ArrayList<>(manual);
         if (!data.noAuto.contains(key)) {
            // what the owner wrote about an item, he decides alone: no line of its own either way
            Set<String> written = new HashSet<>();

            for (Offer o : manual) {
               written.add("s" + o.item());
               written.add("b" + o.item());
            }

            // sells: the richest surpluses first
            surplus.entrySet()
               .stream()
               .filter(e -> !written.contains("s" + Cargo.id(e.getKey())))
               .sorted(Comparator.comparingDouble((Map.Entry<Item, Integer> e) -> -e.getValue() * Prices.of(e.getKey())))
               .limit(AUTO_LINES)
               .forEach(e -> {
                  int have = e.getValue();
                  double glut = (double)have / (have + 64.0);
                  double price = Prices.of(e.getKey()) * (1.25 - 0.5 * glut);
                  offers.add(new Offer(Cargo.id(e.getKey()), true, price, stock.getOrDefault(e.getKey(), 0) - have, true));
               });
            // buys: what is wanted most
            needs.entrySet()
               .stream()
               .filter(e -> !written.contains("b" + Cargo.id(e.getKey())))
               .sorted(Comparator.comparingDouble((Map.Entry<Item, Integer> e) -> -e.getValue() * Prices.of(e.getKey())))
               .limit(AUTO_LINES)
               .forEach(e -> {
                  boolean food = e.getKey().isEdible();
                  double price = Prices.of(e.getKey()) * (food ? FOOD_MARKUP : NEED_MARKUP);
                  offers.add(new Offer(Cargo.id(e.getKey()), false, price, stock.getOrDefault(e.getKey(), 0) + e.getValue(), true));
               });
            // a hungry larder takes any of the common foods
            if (hunger > 0) {
               for (int i = 0; i < 4; i++) {
                  Item food = FOODS[i];
                  if (!written.contains("b" + Cargo.id(food)) && !needs.containsKey(food)) {
                     offers.add(new Offer(Cargo.id(food), false, Prices.of(food) * FOOD_MARKUP, stock.getOrDefault(food, 0) + hunger, true));
                  }
               }
            }
         }

         return new Market(offers, stock, surplus, needs, hunger, now);
      }
   }

   /** Plain goods in the stores (worn tools, written books and the like are not for sale). */
   private static Map<Item, Integer> stock(List<IItemHandler> stores) {
      Map<Item, Integer> out = new LinkedHashMap<>();

      for (IItemHandler h : stores) {
         for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && !s.hasTag()) {
               out.merge(s.getItem(), s.getCount(), Integer::sum);
            }
         }
      }

      return out;
   }

   /** What the colony asks for and cannot find itself, item by item, less what already lies in its stores. */
   static Map<Item, Integer> needs(IColony colony, Map<Item, Integer> stock) {
      Map<Item, Integer> raw = new LinkedHashMap<>();
      List<Food> foodAsked = new ArrayList<>();
      try {
         IRequestManager rm = colony.getRequestManager();
         List<IQueuedRequestResolver<?>> resolvers = new ArrayList<>();
         resolvers.add(rm.getPlayerResolver());
         resolvers.add(rm.getRetryingRequestResolver());

         for (IQueuedRequestResolver<?> resolver : resolvers) {
            for (IToken<?> token : resolver.getAllAssignedRequests()) {
               IRequest<?> r;
               try {
                  r = rm.getRequestForToken(token);
               } catch (IllegalArgumentException e) {
                  continue;
               }

               if (r == null) {
                  continue;
               }

               Object req = r.getRequest();
               if (req instanceof Food f) {
                  foodAsked.add(f);
               } else if (req instanceof Stack s) {
                  want(raw, s.getStack(), s.getCount());
               } else if (req instanceof StackList l) {
                  ItemStack best = null;

                  for (ItemStack opt : l.getStacks()) {
                     if (best == null || Prices.of(opt.getItem()) < Prices.of(best.getItem())) {
                        best = opt;
                     }
                  }

                  want(raw, best, l.getCount());
               } else {
                  // tools, armour and other kinds: the first thing that would do
                  List<ItemStack> shown = r.getDisplayStacks();
                  if (!shown.isEmpty()) {
                     want(raw, shown.get(0), Math.max(1, shown.get(0).getCount()));
                  }
               }
            }
         }
      } catch (RuntimeException e) {
         // a colony half loaded or a request of an odd kind: what was read so far will do
      }

      // food the cooks ask for and the stores have nothing of
      for (Food f : foodAsked) {
         boolean inStore = false;
         Item first = null;

         for (Item food : FOODS) {
            if (f.matches(new ItemStack(food))) {
               first = first == null ? food : first;
               inStore |= stock.getOrDefault(food, 0) >= f.getCount();
            }
         }

         if (!inStore && first != null) {
            raw.merge(first, f.getCount(), Integer::sum);
         }
      }

      Map<Item, Integer> out = new LinkedHashMap<>();
      raw.forEach((item, n) -> {
         int missing = n - stock.getOrDefault(item, 0);
         if (missing > 0 && Prices.of(item) > 0.0) {
            out.put(item, missing);
         }
      });
      return out;
   }

   /** How much food a thin larder lacks (at least a few meals' worth, or nothing). */
   static int hunger(IColony colony, Map<Item, Integer> stock) {
      int larder = 0;

      for (Map.Entry<Item, Integer> e : stock.entrySet()) {
         if (e.getKey().isEdible()) {
            larder += e.getValue();
         }
      }

      int citizens = Math.max(1, colony.getCitizenManager().getCurrentCitizenCount());
      int shortage = citizens * FOOD_PER_CITIZEN / 3 - larder;
      return shortage >= 8 ? shortage : 0;
   }

   private static void want(Map<Item, Integer> raw, ItemStack s, int n) {
      if (s != null && !s.isEmpty() && n > 0 && !Prices.money(s.getItem())) {
         raw.merge(s.getItem(), n, Integer::sum);
      }
   }

   /** What the colony can spare: its stock beyond a reserve and beyond what it asks for itself. */
   static Map<Item, Integer> surplus(IColony colony, Map<Item, Integer> stock, Map<Item, Integer> needs) {
      Map<Item, Integer> out = new LinkedHashMap<>();
      int citizens = Math.max(1, colony.getCitizenManager().getCurrentCitizenCount());
      int larder = 0;

      for (Map.Entry<Item, Integer> e : stock.entrySet()) {
         if (e.getKey().isEdible()) {
            larder += e.getValue();
         }
      }

      boolean foodToSpare = larder > citizens * FOOD_PER_CITIZEN;

      for (Map.Entry<Item, Integer> e : stock.entrySet()) {
         Item item = e.getKey();
         if (Prices.of(item) > 0.0 && !needs.containsKey(item) && !item.canBeDepleted() && !hut(item) && (!item.isEdible() || foodToSpare)) {
            int keep = Math.max(KEEP, item.getMaxStackSize() / 2);
            int spare = e.getValue() - keep;
            if (spare >= 4) {
               out.put(item, spare);
            }
         }
      }

      return out;
   }

   /** MineColonies huts and their tools are the colony's, not merchandise. */
   private static boolean hut(Item item) {
      var id = Prices.id(item);
      return id != null && id.getNamespace().equals("minecolonies") && (id.getPath().startsWith("blockhut") || id.getPath().contains("scepter") || id.getPath().contains("supply"));
   }
}
