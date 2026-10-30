package com.nick.prestol.book;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Court;
import com.nick.prestol.realm.Fealty;
import com.nick.prestol.army.Army;
import com.nick.prestol.decree.DecreeData;
import com.nick.prestol.decree.DecreeType;
import com.nick.prestol.decree.Decrees;
import com.nick.prestol.decree.LawType;
import com.nick.prestol.economy.Treasury;
import com.nick.prestol.trade.Trading;
import com.nick.prestol.flag.FlagActions;
import com.nick.prestol.flag.Flags;
import com.nick.prestol.land.Lands;
import com.nick.prestol.map.Colors;
import com.nick.prestol.map.MapSnapshot;
import com.nick.prestol.map.MapSync;
import com.nick.prestol.net.Net;
import com.nick.prestol.realm.Holding;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmActions;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.realm.Tier;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Camps;
import com.nick.prestol.war.Conquest;
import com.nick.prestol.war.Sieges;
import com.nick.prestol.war.Treaties;
import com.nick.prestol.war.Wars;
import com.nick.prestol.war.Weariness;
import com.nick.prestol.work.Seasons;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Book {
   private Book() {
   }

   public static void open(ServerPlayer player) {
      Net.sendBook(player, build(player, null), true);
   }

   public static void order(ServerPlayer player, String order) {
      MinecraftServer server = player.getServer();
      if (server != null) {
         Realms.Result result;
         try {
            result = run(server, player, order.trim());
         } catch (RuntimeException var5) {
            Prestol.LOGGER.error("Prestol: order '{}' of {} failed", new Object[]{order, player.getGameProfile().getName(), var5});
            result = Realms.Result.no(Msg.bad("err.internal"));
         }

         boolean open = order.startsWith("open");
         Net.sendBook(player, build(player, result), open);
      }
   }

   public static void flagUpload(ServerPlayer player, int w, int h, byte[] rgb) {
      MinecraftServer server = player.getServer();
      if (server != null) {
         Realms.Result result;
         try {
            result = FlagActions.upload(server, player, w, h, rgb);
         } catch (LinkageError | RuntimeException var7) {
            Prestol.LOGGER.error("Prestol: the flag of {} could not be raised", player.getGameProfile().getName(), var7);
            result = Realms.Result.no(Msg.bad("err.internal"));
         }

         Net.sendBook(player, build(player, result), false);
      }
   }

   private static UUID uuid(String s) {
      try {
         return UUID.fromString(s);
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   private static String rest(String[] w, int from) {
      StringBuilder b = new StringBuilder();

      for (int i = from; i < w.length; i++) {
         if (b.length() > 0) {
            b.append(' ');
         }

         b.append(w[i]);
      }

      return b.toString();
   }

   private static Realms.Result run(MinecraftServer server, ServerPlayer player, String order) {
      String[] w = order.split(" ");
      if (w.length == 0) {
         return null;
      } else {
         String var4 = w[0];
         switch (var4) {
            case "open":
            case "refresh":
               return null;
            case "found":
               return w.length >= 3 ? RealmActions.found(server, player, w[1], rest(w, 2)) : Realms.Result.no(Msg.bad("err.name_length", 3, 32));
            case "invite":
               return RealmActions.invite(server, player, w[1]);
            case "accept":
               return RealmActions.accept(server, player, uuid(w[1]), w[2]);
            case "apply":
               return RealmActions.apply(server, player, uuid(w[1]), w[2]);
            case "approve":
            case "reject":
               return RealmActions.approve(server, player, w[1], w[0].equals("approve"));
            case "kick":
               return RealmActions.kick(server, player, w[1]);
            case "leave":
               return RealmActions.leave(server, player, w[1]);
            case "disband":
               return RealmActions.disband(server, player);
            case "capital":
               return RealmActions.capital(server, player, w[1]);
            case "rename":
               return RealmActions.rename(server, player, rest(w, 1));
            case "color":
               try {
                  return RealmActions.color(server, player, Integer.parseInt(w[1], 16));
               } catch (NumberFormatException var7) {
                  return Realms.Result.no(Msg.bad("err.unknown"));
               }
            case "flag":
               return FlagActions.choose(server, player, w.length > 1 ? w[1] : "-");
            case "standard":
               return FlagActions.standard(server, player);
            case "event":
               return com.nick.prestol.events.RealmEvents.order(server, player, w);
            case "land":
               return Lands.rect(
                  server, player, w[1].equals("take"), Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4]), Integer.parseInt(w[5])
               );
            case "war":
            case "peace":
            case "ally":
            case "unally":
               return Wars.order(server, player, w[0], uuid(w[1]));
            case "conquest":
               return Conquest.choose(server, player, w[1], w[2]);
            case "free":
               return Conquest.free(server, player, w[1]);
            case "revolt":
               return Sieges.revolt(server, player, w[1]);
            case "decree":
            case "revoke":
               DecreeType t = DecreeType.byId(w[2]);
               if (t == null) {
                  return Realms.Result.no(Msg.bad("err.unknown"));
               }

               return w[0].equals("decree") ? Decrees.issue(server, player, w[1], t) : Decrees.revoke(server, player, w[1], t);
            case "law":
               LawType law = LawType.byId(w[2]);
               if (law == null) {
                  return Realms.Result.no(Msg.bad("err.unknown"));
               }

               return w[1].equals("enact") ? Decrees.enact(server, player, law) : Decrees.repeal(server, player, law);
            case "army":
               return w.length >= 3 ? Army.order(server, player, w[1], w[2]) : Realms.Result.no(Msg.bad("err.unknown"));
            case "court":
               return Court.order(server, player, w);
            case "treaty":
               return Treaties.offer(server, player, w);
            case "fealty":
               return Fealty.order(server, player, w);
            case "treasury":
               return Treasury.order(server, player, w);
            case "trade":
               return Trading.order(server, player, w);
            case "admin":
               return Admin.run(server, player, w);
            default:
               return Realms.Result.no(Msg.bad("err.unknown"));
         }
      }
   }

   public static CompoundTag build(ServerPlayer player, Realms.Result answer) {
      MinecraftServer server = player.getServer();
      RealmData data = RealmData.get(server);
      DecreeData decrees = DecreeData.get(server);
      long now = Realms.now(server);
      CompoundTag tag = new CompoundTag();
      tag.putLong("day", Realms.day(now));
      tag.putBoolean("op", player.hasPermissions(2));
      tag.putBoolean("betsy", Flags.betsy());
      tag.putBoolean("paintings", Flags.paintings());
      tag.putInt("standardWool", (Integer)PrestolConfig.STANDARD_WOOL.get());
      tag.put("rules", Guide.rules());
      if (answer != null && answer.message() != null) {
         tag.putString("answer", Serializer.toJson(answer.message()));
         tag.putBoolean("answerOk", answer.ok());
      }

      CompoundTag season = Seasons.describe(player.level());
      if (season != null) {
         tag.put("season", season);
      }

      Realm mine = Realms.realmOf(server, player);
      boolean ruler = Realms.isRuler(mine, player);
      tag.putBoolean("economy", Treasury.on());
      if (mine != null && Treasury.on()) {
         tag.put("treasury", Treasury.describe(server, mine, Court.may(server, mine, player, Court.Office.TREASURER)));
         if (Trading.on()) {
            tag.put("trade", Trading.describe(server, player, mine));
         }
      }

      Set<IColony> shown = new LinkedHashSet<>(Settlements.ownedBy(player.getUUID()));
      if (mine != null) {
         shown.addAll(Realms.members(mine));
      }

      for (IColony c : Settlements.all()) {
         Holding h = data.peek(Settlements.key(c));
         if (h != null && player.getUUID().equals(h.formerOwner)) {
            shown.add(c);
         }
      }

      ListTag settlements = new ListTag();

      for (IColony cx : shown) {
         settlements.add(settlement(server, data, decrees, player, cx, now));
      }

      tag.put("settlements", settlements);
      tag.putBoolean("armyOn", Army.garrisons());
      tag.putInt("armyArrows", (Integer)PrestolConfig.ARMY_ARROWS.get());
      tag.putInt("armyFood", (Integer)PrestolConfig.ARMY_FOOD.get());
      tag.putInt("armyTraining", (Integer)PrestolConfig.TRAINING_MINUTES.get());
      tag.put("army", Army.describe(server, player));
      if (mine != null) {
         CompoundTag r = realm(server, data, mine, now);
         r.putBoolean("ruler", ruler);
         boolean chancellor = Court.may(server, mine, player, Court.Office.CHANCELLOR);
         r.putBoolean("chancellor", chancellor);
         r.putInt("lawSlots", Decrees.lawSlots(mine));
         ListTag enacted = new ListTag();

         for (LawType l : decrees.lawsOf(mine.id())) {
            enacted.add(StringTag.valueOf(l.id));
         }

         r.put("laws", enacted);
         ListTag effective = new ListTag();

         for (LawType l : Decrees.activeLaws(decrees, mine)) {
            effective.add(StringTag.valueOf(l.id));
         }

         r.put("lawsActive", effective);
         r.putInt("lawLock", (int)Math.max(0L, (decrees.lawLockUntil(mine.id()) - now) / 20L));
         if (chancellor) {
            ListTag apps = new ListTag();

            for (String k : data.applications(mine.id())) {
               IColony cx = Settlements.byKey(k);
               if (cx != null) {
                  CompoundTag a = new CompoundTag();
                  a.putString("key", k);
                  a.putString("name", cx.getName());
                  a.putString("owner", Settlements.ownerName(cx));
                  apps.add(a);
               }
            }

            r.put("applications", apps);
         }

         tag.put("realm", r);
         tag.put("camps", Camps.describe(server, mine));
         if (Court.on()) {
            tag.put("court", Court.describe(server, player, mine));
         }
      }

      ListTag free = new ListTag();

      for (IColony cx : Settlements.all()) {
         String kx = Settlements.key(cx);
         if (data.byMember(kx) == null) {
            CompoundTag f = new CompoundTag();
            f.putString("key", kx);
            f.putString("name", cx.getName());
            f.putString("owner", Settlements.ownerName(cx));
            f.putInt("tier", Settlements.tierOf(cx).ordinal());
            f.putInt("citizens", Settlements.stats(cx).citizens());
            f.putBoolean("mine", Settlements.isOwner(player, cx));
            f.putBoolean("invited", mine != null && data.invites(mine.id()).containsKey(kx));
            ListTag inv = new ListTag();

            for (UUID r : data.invitesFor(kx, now)) {
               Realm by = data.byId(r);
               if (by != null) {
                  CompoundTag i = new CompoundTag();
                  i.putUUID("id", by.id());
                  i.putString("name", by.name());
                  inv.add(i);
               }
            }

            f.put("invites", inv);
            free.add(f);
         }
      }

      tag.put("free", free);
      ListTag realms = new ListTag();

      for (Realm rx : data.all()) {
         CompoundTag t = realm(server, data, rx, now);
         if (mine != null && rx != mine) {
            RealmData.War war = data.war(mine.id(), rx.id());
            t.putString("stance", war != null ? "war" : (Wars.allied(mine, rx) ? "ally" : "neutral"));
            if (war != null) {
               t.putInt("warIn", (int)Math.max(0L, (war.activeAt() - now) / 20L));
               t.putBoolean("weDeclared", war.attacker().equals(mine.id()));
            }

            t.putBoolean("allyFromThem", data.hasOffer("ally", rx.id(), mine.id()));
            t.putBoolean("allyFromUs", data.hasOffer("ally", mine.id(), rx.id()));
            t.putBoolean("peaceFromThem", data.hasOffer("peace", rx.id(), mine.id()));
            t.putBoolean("peaceFromUs", data.hasOffer("peace", mine.id(), rx.id()));
            if (Fealty.on()) {
               if (Fealty.suzerainOf(server, rx) == mine) {
                  t.putString("bond", "vassal");
               } else if (Fealty.suzerainOf(server, mine) == rx) {
                  t.putString("bond", "liege");
               }

               t.putBoolean("swearFromThem", data.hasOffer("swear", rx.id(), mine.id()));
               t.putBoolean("demandFromThem", data.hasOffer("demand", rx.id(), mine.id()));
               t.putBoolean("swearFromUs", data.hasOffer("swear", mine.id(), rx.id()));
               t.putBoolean("demandFromUs", data.hasOffer("demand", mine.id(), rx.id()));
               // what an oath between us would run into, from either side
               String asLord = Fealty.refusal(server, mine, rx);
               String asVassal = Fealty.refusal(server, rx, mine);
               t.putBoolean("canDemand", asLord == null);
               t.putBoolean("canSwear", asVassal == null);
               t.putInt("fealtyShare", PrestolConfig.FEALTY_SHARE.get());
            }

            if (war != null) {
               Treaties.describeFor(server, mine, rx, t);
               t.putInt("weariness", (int)Math.round(Weariness.of(rx.id())));
            }

            long truce = data.truceUntil(mine.id(), rx.id());
            if (truce > now) {
               t.putLong("truceDay", Realms.day(truce));
            }
         } else if (rx == mine) {
            t.putString("stance", "self");
            t.putInt("weariness", (int)Math.round(Weariness.of(rx.id())));
         }

         realms.add(t);
      }

      tag.put("realms", realms);
      ListTag sieges = new ListTag();

      for (Entry<String, Sieges.Siege> e : Sieges.active().entrySet()) {
         IColony cxx = Settlements.byKey(e.getKey());
         if (cxx != null) {
            Sieges.Siege s = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putString("key", e.getKey());
            t.putString("name", cxx.getName());
            t.putBoolean("revolt", s.kind() == Sieges.Kind.REVOLT);
            t.putInt("held", s.held());
            t.putInt("needed", s.needed());
            t.putBoolean("contested", s.contested());
            sieges.add(t);
         }
      }

      tag.put("sieges", sieges);
      ListTag chronicle = new ListTag();

      for (RealmData.Entry ex : data.chronicle(mine == null ? null : mine.id(), 120)) {
         CompoundTag t = new CompoundTag();
         t.putLong("day", Realms.day(ex.time()));
         t.putString("key", ex.key());
         ListTag a = new ListTag();

         for (String s : ex.args()) {
            a.add(StringTag.valueOf(s));
         }

         t.put("args", a);
         chronicle.add(t);
      }

      tag.put("chronicle", chronicle);
      List<IColony> landCols = Lands.colonies(server, player);
      CompoundTag land = new CompoundTag();
      land.putInt("used", Lands.used(server, landCols));
      land.putInt("limit", Lands.limit(landCols));
      land.putInt("reach", (Integer)PrestolConfig.LAND_REACH.get());
      tag.put("land", land);
      CompoundTag pending = Conquest.pendingFor(player, server);
      if (pending != null) {
         tag.put("pending", pending);
      }

      CompoundTag event = com.nick.prestol.events.RealmEvents.describe(server, mine, mine != null && Court.may(server, mine, player, Court.Office.CHANCELLOR));
      if (event != null) {
         tag.put("event", event);
      }

      try {
         tag.put("advice", Advisor.advise(server, player, mine, tag));
         if (mine != null) {
            tag.put("goal", Advisor.goal(mine));
         }
      } catch (RuntimeException e) {
         // advice is a help, never a reason for the book not to open
      }

      return tag;
   }

   private static CompoundTag realm(MinecraftServer server, RealmData data, Realm r, long now) {
      CompoundTag t = new CompoundTag();
      t.putUUID("id", r.id());
      t.putString("name", r.name());
      t.putString("rank", Realms.rank(r).key());
      t.putInt("color", Colors.of(r));
      t.putString("flag", r.flag());
      ListTag banner = new ListTag();

      for (MapSnapshot.Layer l : MapSync.banner(r)) {
         CompoundTag b = new CompoundTag();
         b.putString("p", l.pattern());
         b.putInt("c", l.color());
         banner.add(b);
      }

      t.put("banner", banner);
      IColony cap = Settlements.byKey(r.capital());
      t.putString("capital", cap == null ? "?" : cap.getName());
      t.putString("capitalKey", r.capital());
      t.putString("rulerName", Book.Owners.name(server, r.ruler()));
      t.putLong("founded", Realms.day(r.founded()));
      t.putInt("settlements", r.members().size());
      t.putInt("maxSettlements", Math.min(9999, Realms.maxSettlements(r)));
      t.putInt("population", Realms.population(r));
      t.putInt("towns", Realms.towns(r));
      return t;
   }

   private static CompoundTag settlement(MinecraftServer server, RealmData data, DecreeData decrees, ServerPlayer player, IColony c, long now) {
      String key = Settlements.key(c);
      Settlements.Stats s = Settlements.stats(c);
      Tier tier = Settlements.tierOf(s);
      Realm realm = data.byMember(key);
      Holding h = data.peek(key);
      CompoundTag t = new CompoundTag();
      t.putString("key", key);
      t.putString("name", c.getName());
      t.putString("owner", Settlements.ownerName(c));
      t.putBoolean("mine", Settlements.isOwner(player, c));
      t.putInt("tier", tier.ordinal());
      t.putInt("citizens", s.citizens());
      if (Army.militiaOn()) {
         t.putInt("militia", Army.militiaSize(c));
         t.putInt("militiaUp", Army.militiaUp(server, key));
      }

      t.putInt("hall", s.townHall());
      t.putInt("buildings", s.buildings());
      t.putDouble("happiness", Math.max(0.0, Math.min(10.0, (double)Math.round(Settlements.happiness(c) * 10.0) / 10.0)));
      Tier next = tier.next();
      if (next != null) {
         t.putInt("needCitizens", next.minCitizens());
         t.putInt("needHall", next.minTownHall());
         t.putInt("needBuildings", next.minBuildings());
      }

      t.putBoolean("canBeCapital", Settlements.canBeCapital(c));
      t.putInt("capitalCitizens", (Integer)PrestolConfig.CAPITAL_CITIZENS.get());
      if (realm != null) {
         t.putString("realm", realm.name());
         t.putBoolean("capital", key.equals(realm.capital()));
      }

      t.putInt("x", c.getCenter().getX());
      t.putInt("z", c.getCenter().getZ());
      if (h != null) {
         t.putBoolean("enslaved", h.enslaved);
         if (h.immuneUntil > now) {
            t.putLong("immuneDay", Realms.day(h.immuneUntil));
         }

         if (player.getUUID().equals(h.formerOwner)) {
            t.putBoolean("former", true);
            t.putLong("revoltDay", Realms.day(h.capturedAt + (long)((Integer)PrestolConfig.REVOLT_AFTER_DAYS.get()).intValue() * 24000L));
            t.putDouble("revoltHappiness", (Double)PrestolConfig.REVOLT_HAPPINESS.get());
         }

         if (h.unhappyDays > 0) {
            t.putInt("unhappyDays", h.unhappyDays);
            t.putInt("mutinyDays", (Integer)PrestolConfig.MUTINY_DAYS.get());
         }
      }

      t.putInt("siege", Sieges.mapState(key));
      if (realm != null && Decrees.mayIssue(server, player, c)) {
         ListTag list = new ListTag();

         for (DecreeType d : DecreeType.values()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", d.id);
            e.putInt("dur", (int)(PrestolConfig.durationTicks(d) / 1200L));
            e.putInt("cd", (int)(PrestolConfig.cooldownTicks(d) / 1200L));
            DecreeData.Active a = decrees.active(key, d);
            if (a != null) {
               e.putInt("left", (int)Math.max(0L, (a.end() - now) / 20L));
            }

            long ready = decrees.readyAt(key, d);
            if (ready > now) {
               e.putInt("cooldown", (int)((ready - now) / 20L));
            }

            list.add(e);
         }

         t.put("decrees", list);
      }

      return t;
   }

   static List<String> list(String... s) {
      return new ArrayList<>(List.of(s));
   }

   static final class Owners {
      static String name(MinecraftServer server, UUID id) {
         ServerPlayer p = server.getPlayerList().getPlayer(id);
         return p != null ? p.getGameProfile().getName() : server.getProfileCache().get(id).map(g -> g.getName()).orElse("?");
      }
   }
}
