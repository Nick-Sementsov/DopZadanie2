package com.nick.prestol.events;

import com.nick.prestol.realm.Rank;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.util.Msg;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** A realm rising in rank is a feast for all its people: a title on their screens, fanfare and a line in the chronicle. */
public final class Celebrations {
   /** The rank each realm was last seen at; the first look at a realm only remembers it. */
   private static final Map<UUID, Rank> SEEN = new HashMap<>();

   private Celebrations() {
   }

   public static void clear() {
      SEEN.clear();
   }

   /** Every few seconds: a realm that grew into a higher rank is hailed. */
   public static void tick(MinecraftServer server) {
      RealmData data = RealmData.get(server);

      for (Realm r : data.all()) {
         Rank now = Realms.rank(r);
         Rank was = SEEN.put(r.id(), now);
         if (was != null && now.ordinal() > was.ordinal()) {
            hail(server, r, now);
         }
      }

      SEEN.keySet().removeIf(id -> data.byId(id) == null);
   }

   private static void hail(MinecraftServer server, Realm r, Rank rank) {
      Component title = Component.literal(r.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
      Component sub = Msg.t("realm.rank_up_sub", Component.translatable(rank.key()), rank.lawSlots).withStyle(ChatFormatting.YELLOW);
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (Realms.realmOf(server, p) == r) {
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
         }
      }

      Realms.tellAll(server, Msg.good("realm.rank_up", r.name(), Component.translatable(rank.key())));
      Realms.chronicle(server, "rank_up", List.of(r.name(), Component.translatable(rank.key()).getString()), r);
   }
}
