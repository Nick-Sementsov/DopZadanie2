package com.nick.prestol.flag;

import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.util.Msg;
import java.util.List;
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
