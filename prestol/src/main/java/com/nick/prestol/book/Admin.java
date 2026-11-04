package com.nick.prestol.book;

import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Sieges;
import com.nick.prestol.war.Wars;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class Admin {
   private Admin() {
   }

   static Realms.Result run(MinecraftServer server, ServerPlayer player, String[] w) {
      if (!player.hasPermissions(2)) {
         return Realms.Result.no(Msg.bad("err.not_op"));
      } else {
         RealmData data = RealmData.get(server);
         String var4 = w.length > 1 ? w[1] : "";
         switch (var4) {
            case "peace":
               RealmData.War war = data.war(UUID.fromString(w[2]), UUID.fromString(w[3]));
               if (war == null) {
                  return Realms.Result.no(Msg.bad("err.unknown"));
               }

               Wars.makePeace(server, data, war);
               return Realms.Result.yes(Msg.good("admin.done"));
            case "lift":
               return Sieges.lift(server, w[2]) ? Realms.Result.yes(Msg.good("admin.done")) : Realms.Result.no(Msg.bad("err.unknown"));
            case "delete":
               Realm r = data.byId(UUID.fromString(w[2]));
               if (r == null) {
                  return Realms.Result.no(Msg.bad("err.no_realm"));
               }

               Realms.chronicleAll(server, "disbanded", r.name());
               Realms.removeRealm(server, data, r, Msg.info("realm.disbanded", r.name()));
               return Realms.Result.yes(Msg.good("admin.done"));
            case "event": {
               // an event staged by an operator: /prestol admin event <realm> [kind]
               Realm target = null;

               for (Realm x : data.all()) {
                  if (w.length > 2 && (x.name().equalsIgnoreCase(w[2]) || x.id().toString().equals(w[2]))) {
                     target = x;
                  }
               }

               if (target == null) {
                  return Realms.Result.no(Msg.bad("err.no_realm"));
               }

               return com.nick.prestol.events.RealmEvents.stage(server, target, w.length > 3 ? w[3] : "")
                  ? Realms.Result.yes(Msg.good("admin.done"))
                  : Realms.Result.no(Msg.bad("err.unknown"));
            }
            case "redraw":
               Realms.changed(server);
               return Realms.Result.yes(Msg.good("admin.done"));
            default:
               return Realms.Result.no(Msg.bad("err.unknown"));
         }
      }
   }
}
