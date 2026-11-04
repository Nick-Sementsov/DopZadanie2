package com.nick.prestol.client;

import com.nick.prestol.Prestol;
import com.nick.prestol.map.MapSnapshot;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class ClientMap {
   private static volatile MapSnapshot snapshot = MapSnapshot.EMPTY;
   private static volatile int version;
   private static Map<String, MapSnapshot.SettlementInfo> settlements = new HashMap<>();
   private static Map<UUID, MapSnapshot.RealmInfo> realms = new HashMap<>();
   private static Map<String, Map<Long, Integer>> regionHashes = new HashMap<>();

   private ClientMap() {
   }

   public static void accept(byte[] bytes) {
      try {
         MapSnapshot snap = MapSnapshot.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
         Map<String, MapSnapshot.SettlementInfo> byKey = new HashMap<>();

         for (MapSnapshot.SettlementInfo s : snap.settlements) {
            byKey.put(s.dim() + "#" + s.id(), s);
         }

         Map<UUID, MapSnapshot.RealmInfo> byId = new HashMap<>();

         for (MapSnapshot.RealmInfo r : snap.realms) {
            byId.put(r.id(), r);
         }

         Map<String, Map<Long, Integer>> hashes = new HashMap<>();

         for (Entry<String, Long2IntOpenHashMap> e : snap.territory.entrySet()) {
            Map<Long, Integer> m = new HashMap<>();
            ObjectIterator var8 = e.getValue().long2IntEntrySet().iterator();

            while (var8.hasNext()) {
               it.unimi.dsi.fastutil.longs.Long2IntMap.Entry c = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var8.next();
               int cx = (int)c.getLongKey();
               int cz = (int)(c.getLongKey() >> 32);
               MapSnapshot.SettlementInfo s = byKey.get(e.getKey() + "#" + c.getIntValue());
               int h = (int)(c.getLongKey() * 31L + (long)c.getIntValue()) * 17;
               if (s != null) {
                  MapSnapshot.RealmInfo r = s.realm() == null ? null : byId.get(s.realm());
                  h += (r == null ? 10395294 : r.color()) * 7 + (s.realm() == null ? 0 : s.realm().hashCode()) + s.siege() * 131;
               }

               m.merge(regionKey(cx >> 5, cz >> 5), h, (a, b) -> a * 31 + b);
            }

            hashes.put(e.getKey(), m);
         }

         settlements = byKey;
         realms = byId;
         regionHashes = hashes;
         snapshot = snap;
         version++;
      } catch (RuntimeException var15) {
         Prestol.LOGGER.warn("Prestol: bad map data from the server", var15);
      }
   }

   public static void clear() {
      snapshot = MapSnapshot.EMPTY;
      settlements = new HashMap<>();
      realms = new HashMap<>();
      regionHashes = new HashMap<>();
      version++;
   }

   public static int version() {
      return version;
   }

   public static long regionKey(int x, int z) {
      return (long)x << 32 | (long)z & 4294967295L;
   }

   public static boolean regionHas(String dim, int rx, int rz) {
      Map<Long, Integer> m = regionHashes.get(dim);
      return m != null && m.containsKey(regionKey(rx, rz));
   }

   public static int regionHash(String dim, int rx, int rz) {
      Map<Long, Integer> m = regionHashes.get(dim);
      return m == null ? 0 : m.getOrDefault(regionKey(rx, rz), 0);
   }

   public static MapSnapshot.SettlementInfo at(String dim, int cx, int cz) {
      Long2IntOpenHashMap m = snapshot.territory.get(dim);
      if (m == null) {
         return null;
      } else {
         int id = m.getOrDefault((long)cz << 32 | (long)cx & 4294967295L, 0);
         return id == 0 ? null : settlements.get(dim + "#" + id);
      }
   }

   public static MapSnapshot.RealmInfo realm(UUID id) {
      return id == null ? null : realms.get(id);
   }

   public static List<MapSnapshot.SettlementInfo> settlements() {
      return snapshot.settlements;
   }

   public static List<MapSnapshot.RealmInfo> realms() {
      return snapshot.realms;
   }

   public static int colorOf(MapSnapshot.SettlementInfo s) {
      MapSnapshot.RealmInfo r = realm(s.realm());
      return r == null ? 10395294 : r.color();
   }

   public static Component describe(MapSnapshot.SettlementInfo s) {
      MutableComponent role = s.capital()
         ? Component.translatable("prestol.role.capital")
         : Component.translatable("prestol.tier." + new String[]{"hamlet", "village", "town"}[Math.max(0, Math.min(2, s.tier()))]);
      MutableComponent line = Component.empty()
         .append(role)
         .append(" «" + s.name() + "»")
         .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colorOf(s))));
      MapSnapshot.RealmInfo r = realm(s.realm());
      if (r != null) {
         line.append(Component.literal(" · ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable(r.rankKey()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" «" + r.name() + "»").withStyle(ChatFormatting.WHITE));
      } else {
         line.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
            .append(Component.translatable("prestol.map.independent").withStyle(ChatFormatting.GRAY));
      }

      if (s.siege() == 1) {
         line.append(Component.literal(" ⚔ ").withStyle(ChatFormatting.RED))
            .append(Component.translatable("prestol.map.besieged").withStyle(ChatFormatting.RED));
      } else if (s.siege() == 2) {
         line.append(Component.literal(" ⚔ ").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.translatable("prestol.map.revolt").withStyle(ChatFormatting.LIGHT_PURPLE));
      }

      return line;
   }

   public static int xaero(int rgb, int opacityPercent) {
      int a = Math.max(0, Math.min(255, opacityPercent * 255 / 100));
      return (rgb & 0xFF) << 24 | (rgb >> 8 & 0xFF) << 16 | (rgb >> 16 & 0xFF) << 8 | a;
   }

   public static boolean chunkColors(String dim, int cx, int cz, int[] out) {
      MapSnapshot.SettlementInfo s = at(dim, cx, cz);
      if (s == null) {
         return false;
      } else {
         int c = colorOf(s);
         boolean siege = s.siege() != 0;
         int fill = xaero(siege ? 12591130 : c, siege ? 42 : 28);
         out[0] = fill;
         out[1] = side(s, at(dim, cx, cz - 1), c, fill);
         out[2] = side(s, at(dim, cx + 1, cz), c, fill);
         out[3] = side(s, at(dim, cx, cz + 1), c, fill);
         out[4] = side(s, at(dim, cx - 1, cz), c, fill);
         return true;
      }
   }

   private static int side(MapSnapshot.SettlementInfo s, MapSnapshot.SettlementInfo other, int c, int fill) {
      if (other == s) {
         return fill;
      } else {
         boolean sameRealm = other != null && s.realm() != null && s.realm().equals(other.realm());
         return xaero(c, sameRealm ? 45 : 90);
      }
   }
}
