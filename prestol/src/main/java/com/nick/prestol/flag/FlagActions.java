package com.nick.prestol.flag;

import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.util.Msg;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FlagActions {
   private FlagActions() {
   }

   public static Realms.Result choose(MinecraftServer server, ServerPlayer player, String id) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else {
         if (!id.isEmpty() && !"-".equals(id)) {
            if (Flags.imageSize(id) == null) {
               return Realms.Result.no(Msg.bad("flag.no_image"));
            }

            realm.setFlag(id);
         } else {
            realm.setFlag("");
         }

         data.setDirty();
         Realms.changed(server);
         Realms.tell(server, realm, Msg.good("flag.changed", realm.name()));
         Realms.chronicle(server, "flag", List.of(realm.name()), realm);
         return Realms.Result.yes(Msg.good("flag.changed", realm.name()));
      }
   }

   public static final int DRAW_W = 48;
   public static final int DRAW_H = 32;
   private static final long UPLOAD_PAUSE_MS = 30000L;
   private static final Map<UUID, Long> LAST_UPLOAD = new HashMap<>();

   /** Raises a flag the ruler drew (or imported) in the flag editor over the whole realm. */
   public static Realms.Result upload(MinecraftServer server, ServerPlayer player, int w, int h, byte[] rgb) {
      RealmData data = RealmData.get(server);
      Realm realm = data.byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (!Flags.paintings()) {
         return Realms.Result.no(Msg.bad("flag.no_paintings"));
      } else if (w != DRAW_W || h != DRAW_H || rgb == null || rgb.length != w * h * 3) {
         return Realms.Result.no(Msg.bad("err.unknown"));
      } else {
         long now = System.currentTimeMillis();
         Long last = LAST_UPLOAD.get(realm.id());
         if (last != null && now - last < UPLOAD_PAUSE_MS) {
            return Realms.Result.no(Msg.bad("flag.too_often", (UPLOAD_PAUSE_MS - (now - last)) / 1000L + 1L));
         } else {
            LAST_UPLOAD.put(realm.id(), now);
            String old = realm.flag();
            String id = RealmFlagImage.register(
               server, realm.id().toString().substring(0, 8), realm.name(), player.getGameProfile().getName(), w, h, rgb
            ).toString();
            realm.setFlag(id);
            data.setDirty();
            if (!old.equals(id)) {
               RealmFlagImage.forget(old);
            }

            Realms.changed(server);
            Realms.tell(server, realm, Msg.good("flag.changed", realm.name()));
            Realms.chronicle(server, "flag", List.of(realm.name()), realm);
            return Realms.Result.yes(Msg.good("flag.drawn", realm.name()));
         }
      }
   }

   public static Realms.Result standard(MinecraftServer server, ServerPlayer player) {
      Realm realm = Realms.realmOf(server, player);
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_in_realm"));
      } else {
         int wool = (Integer)PrestolConfig.STANDARD_WOOL.get();
         Inventory inv = player.getInventory();
         if (!player.isCreative()) {
            if (count(inv, true) < wool || count(inv, false) < 1) {
               return Realms.Result.no(Msg.bad("flag.standard_cost", wool));
            }

            take(inv, true, wool);
            take(inv, false, 1);
         }

         ItemStack st = Flags.standard(server, realm);
         if (!inv.add(st)) {
            player.drop(st, false);
         }

         return Realms.Result.yes(Msg.good("flag.standard_given", realm.name()));
      }
   }

   private static boolean matches(ItemStack s, boolean wool) {
      return wool ? s.is(ItemTags.WOOL) : s.is(Items.STICK);
   }

   private static int count(Inventory inv, boolean wool) {
      int n = 0;

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (matches(s, wool)) {
            n += s.getCount();
         }
      }

      return n;
   }

   private static void take(Inventory inv, boolean wool, int amount) {
      for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
         ItemStack s = inv.getItem(i);
         if (matches(s, wool)) {
            int t = Math.min(amount, s.getCount());
            s.shrink(t);
            amount -= t;
         }
      }

      inv.setChanged();
   }
}
