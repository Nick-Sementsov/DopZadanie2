package com.nick.prestol.realm;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.map.Colors;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Conquest;
import com.nick.prestol.war.Sieges;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class RealmActions {
   private RealmActions() {
   }

   private static boolean atWar(RealmData data, Realm realm) {
      for (RealmData.War w : data.wars()) {
         if (w.involves(realm.id())) {
            return true;
         }
      }

      return false;
   }

   private static IColony own(ServerPlayer player, String key) {
      IColony c = Settlements.byKey(key);
      return c != null && Settlements.isOwner(player, c) ? c : null;
   }

   public static Realms.Result found(MinecraftServer server, ServerPlayer player, String key, String name) {
      RealmData data = RealmData.get(server);
      IColony colony = own(player, key);
      if (colony == null) {
         return Realms.Result.no(Msg.bad("err.not_your_settlement"));
      } else if (data.byRuler(player.getUUID()) != null) {
         return Realms.Result.no(Msg.bad("err.already_ruler"));
      } else {
         Realm already = data.byMember(key);
         if (already != null) {
            return Realms.Result.no(Msg.bad("err.already_member", colony.getName(), already.name()));
         } else {
            Component bad = Realms.validateName(data, name, null);
            if (bad != null) {
               return Realms.Result.no(bad);
            } else {
               Realm realm = new Realm(UUID.randomUUID(), name.trim(), player.getUUID(), key, Realms.now(server));

               try {
                  if (!Colors.blank(colony.getColonyFlag())) {
                     realm.setBanner(colony.getColonyFlag());
                  }
               } catch (RuntimeException var10) {
               }

               data.add(realm);
               Realms.tellAll(server, Msg.good("realm.founded", player.getGameProfile().getName(), realm.name(), colony.getName()));
               Realms.chronicle(server, "founded", List.of(realm.name(), player.getGameProfile().getName(), colony.getName()), realm);
               Realms.changed(server);
               if (!Settlements.canBeCapital(colony)) {
                  player.sendSystemMessage(Msg.info("realm.capital_small", colony.getName()));
               }

               return Realms.Result.yes(Msg.good("ok.founded", realm.name()));
            }
         }
      }
   }

   public static Realms.Result invite(MinecraftServer server, ServerPlayer player, String key) {
      RealmData data = RealmData.get(server);
      Realm realm = Court.servedAs(server, player, Court.Office.CHANCELLOR);
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else {
         IColony colony = Settlements.byKey(key);
         if (colony == null) {
            return Realms.Result.no(Msg.bad("err.no_settlement"));
         } else {
            Component why = Realms.checkJoin(data, realm, colony);
            if (why != null) {
               return Realms.Result.no(why);
            } else if (Settlements.owner(colony) != null && Settlements.owner(colony).equals(player.getUUID())) {
               Realms.attach(server, data, realm, colony);
               Realms.chronicle(server, "joined", List.of(colony.getName(), realm.name()), realm);
               return Realms.Result.yes(Msg.good("ok.joined", colony.getName(), realm.name()));
            } else {
               data.invites(realm.id()).put(key, Realms.now(server) + 24000L);
               data.setDirty();
               Realms.tellColony(server, colony, Msg.info("realm.invited", realm.name(), colony.getName()));
               return Realms.Result.yes(Msg.good("ok.invited", colony.getName()));
            }
         }
      }
   }

   public static Realms.Result accept(MinecraftServer server, ServerPlayer player, UUID realmId, String key) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byId(realmId);
      IColony colony = own(player, key);
      if (realm != null && colony != null) {
         Long until = data.invites(realm.id()).get(key);
         if (until != null && until >= Realms.now(server)) {
            Component why = Realms.checkJoin(data, realm, colony);
            if (why != null) {
               return Realms.Result.no(why);
            } else {
               Realms.attach(server, data, realm, colony);
               Realms.tell(server, realm, Msg.good("realm.joined", colony.getName(), realm.name()));
               Realms.chronicle(server, "joined", List.of(colony.getName(), realm.name()), realm);
               return Realms.Result.yes(Msg.good("ok.joined", colony.getName(), realm.name()));
            }
         } else {
            return Realms.Result.no(Msg.bad("err.no_invite"));
         }
      } else {
         return Realms.Result.no(Msg.bad("err.not_your_settlement"));
      }
   }

   public static Realms.Result apply(MinecraftServer server, ServerPlayer player, UUID realmId, String key) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byId(realmId);
      IColony colony = own(player, key);
      if (realm != null && colony != null) {
         Component why = Realms.checkJoin(data, realm, colony);
         if (why != null) {
            return Realms.Result.no(why);
         } else {
            data.applications(realm.id()).add(key);
            data.setDirty();
            Realms.tell(server, realm, Msg.info("realm.applied", colony.getName(), realm.name()));
            return Realms.Result.yes(Msg.good("ok.applied", realm.name()));
         }
      } else {
         return Realms.Result.no(Msg.bad("err.not_your_settlement"));
      }
   }

   public static Realms.Result approve(MinecraftServer server, ServerPlayer player, String key, boolean yes) {
      RealmData data = RealmData.get(server);
      Realm realm = Court.servedAs(server, player, Court.Office.CHANCELLOR);
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (!data.applications(realm.id()).remove(key)) {
         return Realms.Result.no(Msg.bad("err.no_application"));
      } else {
         data.setDirty();
         IColony colony = Settlements.byKey(key);
         if (colony == null) {
            return Realms.Result.no(Msg.bad("err.no_settlement"));
         } else if (!yes) {
            Realms.tellColony(server, colony, Msg.bad("realm.application_refused", realm.name()));
            return Realms.Result.yes(Msg.info("ok.refused", colony.getName()));
         } else {
            Component why = Realms.checkJoin(data, realm, colony);
            if (why != null) {
               return Realms.Result.no(why);
            } else {
               Realms.attach(server, data, realm, colony);
               Realms.tell(server, realm, Msg.good("realm.joined", colony.getName(), realm.name()));
               Realms.tellColony(server, colony, Msg.good("realm.joined", colony.getName(), realm.name()));
               Realms.chronicle(server, "joined", List.of(colony.getName(), realm.name()), realm);
               return Realms.Result.yes(Msg.good("ok.joined", colony.getName(), realm.name()));
            }
         }
      }
   }

   public static Realms.Result kick(MinecraftServer server, ServerPlayer player, String key) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (key.equals(realm.capital())) {
         return Realms.Result.no(Msg.bad("err.capital_cannot_leave"));
      } else if (Sieges.isBesieged(key) || Conquest.pending(key)) {
         // kicking a besieged settlement used to end the siege silently
         return Realms.Result.no(Msg.bad("err.besieged"));
      } else {
         IColony colony = Settlements.byKey(key);
         if (colony != null && realm.members().contains(key)) {
            Realms.detach(server, data, realm, colony);
            Realms.tellColony(server, colony, Msg.bad("realm.kicked", colony.getName(), realm.name()));
            Realms.chronicle(server, "kicked", List.of(colony.getName(), realm.name()), realm);
            return Realms.Result.yes(Msg.info("ok.kicked", colony.getName()));
         } else {
            return Realms.Result.no(Msg.bad("err.no_settlement"));
         }
      }
   }

   public static Realms.Result leave(MinecraftServer server, ServerPlayer player, String key) {
      RealmData data = RealmData.get(server);
      IColony colony = own(player, key);
      if (colony == null) {
         return Realms.Result.no(Msg.bad("err.not_your_settlement"));
      } else {
         Realm realm = data.byMember(key);
         if (realm == null) {
            return Realms.Result.no(Msg.bad("err.not_in_realm"));
         } else if (key.equals(realm.capital())) {
            return Realms.Result.no(Msg.bad("err.capital_cannot_leave"));
         } else if (Sieges.isBesieged(key)) {
            return Realms.Result.no(Msg.bad("err.besieged"));
         } else {
            Realms.detach(server, data, realm, colony);
            Realms.tell(server, realm, Msg.bad("realm.left", colony.getName(), realm.name()));
            Realms.chronicle(server, "left", List.of(colony.getName(), realm.name()), realm);
            return Realms.Result.yes(Msg.info("ok.left", realm.name()));
         }
      }
   }

   public static Realms.Result disband(MinecraftServer server, ServerPlayer player) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (atWar(data, realm)) {
         // disbanding used to end every war and siege at once, with no truce
         return Realms.Result.no(Msg.bad("err.at_war"));
      } else {
         Realms.chronicleAll(server, "disbanded", realm.name());
         Realms.removeRealm(server, data, realm, Msg.info("realm.disbanded", realm.name()));
         return Realms.Result.yes(Msg.info("ok.disbanded", realm.name()));
      }
   }

   public static Realms.Result capital(MinecraftServer server, ServerPlayer player, String key) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (atWar(data, realm)) {
         // moving the capital mid-war dodged the "capital falls last" rule
         return Realms.Result.no(Msg.bad("err.at_war"));
      } else {
         IColony colony = Settlements.byKey(key);
         if (colony == null || !realm.members().contains(key)) {
            return Realms.Result.no(Msg.bad("err.no_settlement"));
         } else if (!Settlements.canBeCapital(colony)) {
            return Realms.Result.no(Msg.bad("err.capital_small", colony.getName()));
         } else {
            realm.setCapital(key);
            data.setDirty();
            Realms.tell(server, realm, Msg.good("realm.new_capital", colony.getName(), realm.name()));
            Realms.chronicle(server, "new_capital", List.of(colony.getName(), realm.name()), realm);
            Realms.changed(server);
            return Realms.Result.yes(Msg.good("ok.capital", colony.getName()));
         }
      }
   }

   public static Realms.Result rename(MinecraftServer server, ServerPlayer player, String name) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else {
         Component bad = Realms.validateName(data, name, realm);
         if (bad != null) {
            return Realms.Result.no(bad);
         } else {
            String old = realm.name();
            realm.setName(name.trim());
            data.setDirty();
            Realms.tellAll(server, Msg.info("realm.renamed", old, realm.name()));
            Realms.chronicle(server, "renamed", List.of(old, realm.name()), realm);
            Realms.changed(server);
            return Realms.Result.yes(Msg.good("ok.renamed", realm.name()));
         }
      }
   }

   public static Realms.Result color(MinecraftServer server, ServerPlayer player, int rgb) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else {
         realm.setColor(rgb & 16777215);
         data.setDirty();
         Realms.changed(server);
         return Realms.Result.yes(Msg.good("ok.color"));
      }
   }
}
