package com.nick.prestol.realm;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.authlib.GameProfile;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.economy.Coins;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.economy.Treasury;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Sieges;
import com.nick.prestol.war.Wars;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The court of a realm. The ruler appoints a treasurer (treasury and trade), a marshal (every garrison of the realm)
 * and a chancellor (invitations, applications and the law). The lords of the realm carry titles by what they hold, a
 * knight's hamlet up to a duke's three settlements, and wear them before their names. The ruler or the chancellor can
 * outlaw a player: the realm's guards and soldiers fall upon him in its lands, and a price set on his head is paid
 * from the treasury to whoever brings him down. Robbing a realm's caravan outlaws the robber there.
 */
public final class Court {
   private static final Map<UUID, String> SHOWN = new HashMap<>();
   private static int ticks;

   private Court() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.COURT.get();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   /** Whether {@code player} may act as {@code office} of {@code realm}: the ruler holds every office himself. */
   public static boolean may(MinecraftServer server, Realm realm, UUID player, Court.Office office) {
      if (realm == null || player == null) {
         return false;
      } else if (realm.ruler().equals(player)) {
         return true;
      } else {
         Map<Court.Office, UUID> held = CourtData.get(server).offices.get(realm.id());
         return on() && held != null && player.equals(held.get(office));
      }
   }

   /** Whether {@code player} is outlawed in {@code realm}. */
   public static boolean outlawed(MinecraftServer server, Realm realm, UUID player) {
      return on() && realm != null && player != null && CourtData.get(server).outlaws(realm.id()).containsKey(player);
   }

   public static boolean may(MinecraftServer server, Realm realm, ServerPlayer player, Court.Office office) {
      return may(server, realm, player.getUUID(), office);
   }

   /** The realm {@code player} serves as {@code office}: his own, if he rules it or holds the office. */
   public static Realm servedAs(MinecraftServer server, ServerPlayer player, Court.Office office) {
      Realm realm = Realms.realmOf(server, player);
      return may(server, realm, player, office) ? realm : null;
   }

   public static Realms.Result order(MinecraftServer server, ServerPlayer player, String[] w) {
      if (!on()) {
         return Realms.Result.no(Msg.bad("err.court_off"));
      } else {
         Realm realm = Realms.realmOf(server, player);
         if (realm == null) {
            return Realms.Result.no(Msg.bad("err.need_realm"));
         } else {
            CourtData court = CourtData.get(server);
            switch (w.length > 1 ? w[1] : "") {
               case "appoint": {
                  Court.Office office = w.length > 3 ? Court.Office.of(w[2]) : null;
                  UUID who = w.length > 3 ? uuid(w[3]) : null;
                  if (!Realms.isRuler(realm, player)) {
                     return Realms.Result.no(Msg.bad("err.not_ruler"));
                  } else if (office == null || who == null) {
                     return Realms.Result.no(Msg.bad("err.unknown"));
                  } else if (who.equals(realm.ruler()) || Sieges.realmOfPlayer(server, RealmData.get(server), who) != realm) {
                     return Realms.Result.no(Msg.bad("err.not_subject"));
                  } else {
                     court.offices(realm.id()).put(office, who);
                     court.setDirty();
                     String name = nameOf(server, who);
                     Realms.tell(server, realm, Msg.info("court.appointed", name, office.title()));
                     refreshTitles(server, true);
                     return Realms.Result.yes(Msg.good("ok.appointed", name, office.title()));
                  }
               }
               case "dismiss": {
                  Court.Office office = w.length > 2 ? Court.Office.of(w[2]) : null;
                  UUID holder = office == null ? null : court.offices(realm.id()).get(office);
                  if (holder == null) {
                     return Realms.Result.no(Msg.bad("err.unknown"));
                  } else if (!Realms.isRuler(realm, player) && !holder.equals(player.getUUID())) {
                     return Realms.Result.no(Msg.bad("err.not_ruler"));
                  } else {
                     court.offices(realm.id()).remove(office);
                     court.setDirty();
                     Realms.tell(server, realm, Msg.info("court.dismissed", nameOf(server, holder), office.title()));
                     return Realms.Result.yes(Msg.good("ok.dismissed", office.title()));
                  }
               }
               case "outlaw": {
                  if (!may(server, realm, player, Court.Office.CHANCELLOR)) {
                     return Realms.Result.no(Msg.bad("err.not_chancellor"));
                  } else if (w.length < 3) {
                     return Realms.Result.no(Msg.bad("err.unknown"));
                  } else {
                     GameProfile target = profile(server, w[2]);
                     long bounty = w.length > 3 ? parse(w[3]) : 0L;
                     if (target == null) {
                        return Realms.Result.no(Msg.bad("err.no_player", w[2]));
                     } else if (bounty < 0L) {
                        return Realms.Result.no(Msg.bad("err.unknown"));
                     } else if (target.getId().equals(player.getUUID()) || target.getId().equals(realm.ruler()) || lordOf(realm, target.getId())) {
                        return Realms.Result.no(Msg.bad("err.cannot_outlaw", target.getName()));
                     } else if (bounty > 0L && (!Treasury.on() || !EconomyData.get(server).take(realm.id(), bounty))) {
                        return Realms.Result.no(Msg.bad("err.treasury_short", Coins.fmt(Treasury.on() ? EconomyData.get(server).balance(realm.id()) : 0L)));
                     } else {
                        if (bounty > 0L) {
                           EconomyData.get(server).log(realm.id(), Realms.day(Realms.now(server)), "bounty_set", -bounty, target.getName());
                        }

                        declare(server, realm, target.getId(), target.getName(), "decree", bounty);
                        return Realms.Result.yes(Msg.good("ok.outlawed", target.getName()));
                     }
                  }
               }
               case "pardon": {
                  UUID who = w.length > 2 ? uuid(w[2]) : null;
                  CourtData.Outlaw o = who == null ? null : court.outlaws(realm.id()).get(who);
                  if (!may(server, realm, player, Court.Office.CHANCELLOR)) {
                     return Realms.Result.no(Msg.bad("err.not_chancellor"));
                  } else if (o == null) {
                     return Realms.Result.no(Msg.bad("err.unknown"));
                  } else {
                     lift(server, realm, o, "court.pardoned");
                     return Realms.Result.yes(Msg.good("ok.pardoned", o.name));
                  }
               }
               default:
                  return Realms.Result.no(Msg.bad("err.unknown"));
            }
         }
      }
   }

   /** Outlaws a player in a realm, or raises the price on his head and lengthens his exile if he already is. */
   private static void declare(MinecraftServer server, Realm realm, UUID player, String name, String reason, long bounty) {
      CourtData court = CourtData.get(server);
      long until = Realms.now(server) + PrestolConfig.OUTLAW_DAYS.get() * 24000L;
      CourtData.Outlaw o = court.outlaws(realm.id()).get(player);
      if (o == null) {
         o = new CourtData.Outlaw(player, name, reason, bounty, until);
         court.outlaws(realm.id()).put(player, o);
      } else {
         o.bounty += bounty;
         o.until = Math.max(o.until, until);
      }

      // an outlaw holds no office of the realm that outlawed him
      if (court.offices(realm.id()).values().removeIf(player::equals)) {
         Realms.tell(server, realm, Msg.info("court.office_lost", name));
      }

      court.setDirty();
      Component why = Component.translatable("prestol.court.reason." + reason);
      Component msg = o.bounty > 0L
         ? Msg.war("court.outlawed", name, realm.name(), Coins.fmt(o.bounty), why)
         : Msg.war("court.outlawed_free", name, realm.name(), why);
      Realms.tellAll(server, msg);
      Realms.chronicle(server, "outlaw", List.of(name, realm.name()), realm);
      Wars.applyHostility(server, RealmData.get(server));
      refreshTitles(server, true);
   }

   /** A robber who kills a realm's merchant in peace is outlawed there. */
   public static void robbed(MinecraftServer server, UUID realmId, ServerPlayer robber) {
      RealmData realms = RealmData.get(server);
      Realm realm = realms.byId(realmId);
      Realm theirs = Realms.realmOf(server, robber);
      if (on() && realm != null && theirs != realm && !Wars.atWar(realms, realm, theirs)) {
         declare(server, realm, robber.getUUID(), robber.getGameProfile().getName(), "robbery", 0L);
      }
   }

   private static void lift(MinecraftServer server, Realm realm, CourtData.Outlaw o, String why) {
      CourtData court = CourtData.get(server);
      court.outlaws(realm.id()).remove(o.player);
      court.setDirty();
      if (o.bounty > 0L && Treasury.on()) {
         EconomyData eco = EconomyData.get(server);
         eco.give(realm.id(), o.bounty);
         eco.log(realm.id(), Realms.day(Realms.now(server)), "bounty_back", o.bounty, o.name);
      }

      Realms.tellAll(server, Msg.info(why, o.name, realm.name()));
      Wars.applyHostility(server, RealmData.get(server));
      refreshTitles(server, true);
   }

   /** An outlaw brought down by a man, a soldier or a guard has met justice; whoever claims the price gets it. */
   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (on() && event.getEntity() instanceof ServerPlayer victim) {
         MinecraftServer server = victim.getServer();
         Entity killer = event.getSource().getEntity();
         ServerPlayer claimant = null;
         if (killer instanceof ServerPlayer p && p != victim) {
            claimant = p;
         } else if (killer instanceof OwnableEntity own && own.getOwnerUUID() != null && !own.getOwnerUUID().equals(victim.getUUID())) {
            claimant = server.getPlayerList().getPlayer(own.getOwnerUUID());
         }

         boolean justice = claimant != null
            || killer instanceof OwnableEntity own2 && !victim.getUUID().equals(own2.getOwnerUUID())
            || killer instanceof AbstractEntityCitizen;
         if (justice) {
            CourtData court = CourtData.get(server);
            RealmData realms = RealmData.get(server);

            for (Map.Entry<UUID, Map<UUID, CourtData.Outlaw>> e : new HashMap<>(court.outlaws).entrySet()) {
               CourtData.Outlaw o = e.getValue().get(victim.getUUID());
               Realm realm = realms.byId(e.getKey());
               if (o != null && realm != null) {
                  if (claimant != null && o.bounty > 0L) {
                     Coins.give(claimant, o.bounty);
                     EconomyData.get(server).log(realm.id(), Realms.day(Realms.now(server)), "bounty_paid", 0L, o.name, claimant.getGameProfile().getName());
                     Realms.tellAll(server, Msg.war("court.bounty_paid", o.name, claimant.getGameProfile().getName(), Coins.fmt(o.bounty), realm.name()));
                     Realms.chronicle(server, "bounty", List.of(o.name, claimant.getGameProfile().getName()), realm);
                     o.bounty = 0L;
                     lift(server, realm, o, "court.justice");
                  } else {
                     lift(server, realm, o, "court.justice");
                  }
               }
            }
         }
      }
   }

   /** Every second: exiles run out; now and then the titles before the names are brought up to date. */
   public static void tick(MinecraftServer server) {
      if (on()) {
         long now = Realms.now(server);
         CourtData court = CourtData.get(server);
         RealmData realms = RealmData.get(server);

         for (Map.Entry<UUID, Map<UUID, CourtData.Outlaw>> e : new HashMap<>(court.outlaws).entrySet()) {
            Realm realm = realms.byId(e.getKey());
            if (realm == null) {
               court.forget(e.getKey());
            } else {
               for (CourtData.Outlaw o : new ArrayList<>(e.getValue().values())) {
                  if (o.until <= now) {
                     lift(server, realm, o, "court.exile_over");
                  }
               }
            }
         }

         // offices of men who left the realm fall vacant
         for (Map.Entry<UUID, Map<Court.Office, UUID>> e : court.offices.entrySet()) {
            Realm realm = realms.byId(e.getKey());
            Iterator<Map.Entry<Court.Office, UUID>> it = e.getValue().entrySet().iterator();

            while (it.hasNext()) {
               UUID who = it.next().getValue();
               if (realm == null || Sieges.realmOfPlayer(server, realms, who) != realm) {
                  it.remove();
                  court.setDirty();
               }
            }
         }

         if (++ticks % 10 == 0) {
            refreshTitles(server, false);
         }
      }
   }

   /** The realm is gone: the prices on heads go back to its treasury before the treasury goes to the ruler. */
   public static void onRealmRemoved(MinecraftServer server, Realm realm) {
      CourtData court = CourtData.get(server);
      if (Treasury.on()) {
         long back = 0L;

         for (CourtData.Outlaw o : court.outlaws(realm.id()).values()) {
            back += o.bounty;
         }

         if (back > 0L) {
            EconomyData.get(server).give(realm.id(), back);
         }
      }

      court.forget(realm.id());
   }

   // ---- titles

   /** The title a player wears: outlaw, ruler, or the lord of what he holds in a realm; null for a commoner. */
   public static MutableComponent title(MinecraftServer server, UUID player) {
      CourtData court = CourtData.get(server);

      for (Map<UUID, CourtData.Outlaw> m : court.outlaws.values()) {
         if (m.containsKey(player)) {
            return Component.translatable("prestol.title.outlaw").withStyle(ChatFormatting.DARK_RED);
         }
      }

      RealmData realms = RealmData.get(server);
      Realm ruled = realms.byRuler(player);
      if (ruled != null) {
         return Component.translatable("prestol.title.ruler." + Realms.rank(ruled).name().toLowerCase(Locale.ROOT)).withStyle(s -> s.withColor(colorOf(ruled)));
      } else {
         Realm realm = null;
         Tier best = null;
         int held = 0;

         for (IColony c : Settlements.ownedBy(player)) {
            Realm r = realms.byMember(Settlements.key(c));
            if (r != null) {
               realm = r;
               held++;
               Tier t = Settlements.tierOf(c);
               if (best == null || t.ordinal() > best.ordinal()) {
                  best = t;
               }
            }
         }

         if (realm == null) {
            return null;
         } else {
            String key = held >= 3 ? "duke" : (best == Tier.TOWN ? "count" : (best == Tier.VILLAGE ? "baron" : "knight"));
            Realm r = realm;
            return Component.translatable("prestol.title." + key).withStyle(s -> s.withColor(colorOf(r)));
         }
      }
   }

   private static TextColor colorOf(Realm realm) {
      return TextColor.fromRgb(realm.color() < 0 ? 0xD8A23C : realm.color() & 0xFFFFFF);
   }

   private static MutableComponent styled(MinecraftServer server, UUID player, Component name) {
      MutableComponent t = PrestolConfig.TITLES_IN_NAMES.get() ? title(server, player) : null;
      return t == null ? null : Component.empty().append(t).append(" ").append(name);
   }

   @SubscribeEvent
   public static void onNameFormat(PlayerEvent.NameFormat event) {
      if (event.getEntity() instanceof ServerPlayer p && on()) {
         MutableComponent named = styled(p.getServer(), p.getUUID(), event.getDisplayname());
         if (named != null) {
            event.setDisplayname(named);
         }
      }
   }

   @SubscribeEvent
   public static void onTabName(PlayerEvent.TabListNameFormat event) {
      if (event.getEntity() instanceof ServerPlayer p && on()) {
         Component base = event.getDisplayName() != null ? event.getDisplayName() : p.getName();
         MutableComponent named = styled(p.getServer(), p.getUUID(), base);
         if (named != null) {
            event.setDisplayName(named);
         }
      }
   }

   /** Re-reads the names of players whose title changed, so chat and the player list show it. */
   private static void refreshTitles(MinecraftServer server, boolean now) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         MutableComponent t = title(server, p.getUUID());
         String shown = t == null ? "" : t.getString() + "|" + t.getStyle().getColor();
         if (now || !shown.equals(SHOWN.get(p.getUUID()))) {
            SHOWN.put(p.getUUID(), shown);
            p.refreshDisplayName();
            p.refreshTabListName();
         }
      }
   }

   // ---- book

   public static CompoundTag describe(MinecraftServer server, ServerPlayer player, Realm realm) {
      CompoundTag out = new CompoundTag();
      CourtData court = CourtData.get(server);
      RealmData realms = RealmData.get(server);
      out.putBoolean("appoint", Realms.isRuler(realm, player));
      out.putBoolean("law", may(server, realm, player, Court.Office.CHANCELLOR));
      out.putInt("days", PrestolConfig.OUTLAW_DAYS.get());
      ListTag offices = new ListTag();

      for (Court.Office office : Court.Office.values()) {
         CompoundTag t = new CompoundTag();
         t.putString("id", office.id());
         UUID holder = court.offices(realm.id()).get(office);
         if (holder != null) {
            t.putUUID("holder", holder);
            t.putString("name", nameOf(server, holder));
            t.putBoolean("me", holder.equals(player.getUUID()));
         }

         offices.add(t);
      }

      out.put("offices", offices);
      // the lords of the realm, who are the candidates for office as well
      ListTag lords = new ListTag();
      List<UUID> seen = new ArrayList<>();

      for (IColony c : Realms.members(realm)) {
         UUID owner = Settlements.owner(c);
         if (owner != null && !seen.contains(owner)) {
            seen.add(owner);
            CompoundTag t = new CompoundTag();
            t.putUUID("id", owner);
            t.putString("name", nameOf(server, owner));
            MutableComponent title = title(server, owner);
            if (title != null) {
               t.putString("title", Component.Serializer.toJson(title));
            }

            t.putBoolean("ruler", owner.equals(realm.ruler()));
            lords.add(t);
         }
      }

      out.put("lords", lords);
      ListTag outlaws = new ListTag();
      long now = Realms.now(server);

      for (CourtData.Outlaw o : court.outlaws(realm.id()).values()) {
         CompoundTag t = new CompoundTag();
         t.putUUID("id", o.player);
         t.putString("name", o.name);
         t.putString("reason", o.reason);
         t.putLong("bounty", o.bounty);
         t.putInt("days", (int)Math.max(1L, (o.until - now + 23999L) / 24000L));
         outlaws.add(t);
      }

      out.put("outlaws", outlaws);
      // prices on heads elsewhere: a bounty hunter's list
      ListTag wanted = new ListTag();

      for (Map.Entry<UUID, Map<UUID, CourtData.Outlaw>> e : court.outlaws.entrySet()) {
         Realm r = realms.byId(e.getKey());
         if (r != null && r != realm) {
            for (CourtData.Outlaw o : e.getValue().values()) {
               if (o.bounty > 0L) {
                  CompoundTag t = new CompoundTag();
                  t.putString("name", o.name);
                  t.putString("realm", r.name());
                  t.putLong("bounty", o.bounty);
                  wanted.add(t);
               }
            }
         }
      }

      out.put("wanted", wanted);
      return out;
   }

   // ---- helpers

   /** A lord of the realm (an owner of one of its settlements) answers to the realm, not to its law. */
   private static boolean lordOf(Realm realm, UUID player) {
      for (IColony c : Realms.members(realm)) {
         if (player.equals(Settlements.owner(c))) {
            return true;
         }
      }

      return false;
   }

   /** A player who has been on this server, by name. */
   private static GameProfile profile(MinecraftServer server, String name) {
      ServerPlayer online = server.getPlayerList().getPlayerByName(name);
      if (online != null) {
         return online.getGameProfile();
      } else {
         Optional<GameProfile> cached = server.getProfileCache().get(name);
         return cached.filter(p -> Files.exists(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getId() + ".dat"))).orElse(null);
      }
   }

   static String nameOf(MinecraftServer server, UUID id) {
      ServerPlayer online = server.getPlayerList().getPlayer(id);
      if (online != null) {
         return online.getGameProfile().getName();
      } else {
         Optional<String> cached = server.getProfileCache().get(id).map(GameProfile::getName);
         if (cached.isPresent()) {
            return cached.get();
         } else {
            // MineColonies remembers the names of the owners of its colonies
            List<IColony> owned = Settlements.ownedBy(id);
            return owned.isEmpty() ? "?" : Settlements.ownerName(owned.get(0));
         }
      }
   }

   private static UUID uuid(String s) {
      try {
         return UUID.fromString(s);
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   private static long parse(String s) {
      try {
         return Long.parseLong(s);
      } catch (NumberFormatException var2) {
         return -1L;
      }
   }

   public static enum Office {
      TREASURER,
      MARSHAL,
      CHANCELLOR;

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      String key() {
         return "prestol.court.office." + this.id();
      }

      Component title() {
         return Component.translatable(this.key());
      }

      static Court.Office of(String id) {
         for (Court.Office o : values()) {
            if (o.id().equals(id)) {
               return o;
            }
         }

         return null;
      }
   }
}
