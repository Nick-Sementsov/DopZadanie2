package com.nick.prestol.map;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.network.FriendlyByteBuf;

public final class MapSnapshot {
   public static final MapSnapshot EMPTY = new MapSnapshot(List.of(), List.of(), Map.of());
   public final List<MapSnapshot.RealmInfo> realms;
   public final List<MapSnapshot.SettlementInfo> settlements;
   public final Map<String, Long2IntOpenHashMap> territory;

   public MapSnapshot(List<MapSnapshot.RealmInfo> realms, List<MapSnapshot.SettlementInfo> settlements, Map<String, Long2IntOpenHashMap> territory) {
      this.realms = realms;
      this.settlements = settlements;
      this.territory = territory;
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeVarInt(this.realms.size());

      for (MapSnapshot.RealmInfo r : this.realms) {
         buf.writeUUID(r.id());
         buf.writeUtf(r.name());
         buf.writeInt(r.color());
         buf.writeUtf(r.rankKey());
         buf.writeUtf(r.flag());
         buf.writeVarInt(r.banner().size());

         for (MapSnapshot.Layer l : r.banner()) {
            buf.writeUtf(l.pattern());
            buf.writeVarInt(l.color());
         }
      }

      buf.writeVarInt(this.settlements.size());

      for (MapSnapshot.SettlementInfo s : this.settlements) {
         buf.writeUtf(s.key());
         buf.writeUtf(s.dim());
         buf.writeVarInt(s.id());
         buf.writeUtf(s.name());
         buf.writeInt(s.x());
         buf.writeInt(s.y());
         buf.writeInt(s.z());
         buf.writeVarInt(s.tier());
         buf.writeBoolean(s.capital());
         buf.writeBoolean(s.realm() != null);
         if (s.realm() != null) {
            buf.writeUUID(s.realm());
         }

         buf.writeVarInt(s.siege());
         buf.writeUtf(s.owner());
      }

      buf.writeVarInt(this.territory.size());

      for (Entry<String, Long2IntOpenHashMap> e : this.territory.entrySet()) {
         buf.writeUtf(e.getKey());
         buf.writeVarInt(e.getValue().size());
         ObjectIterator var10 = e.getValue().long2IntEntrySet().iterator();

         while (var10.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2IntMap.Entry c = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var10.next();
            buf.writeLong(c.getLongKey());
            buf.writeVarInt(c.getIntValue());
         }
      }
   }

   public static MapSnapshot read(FriendlyByteBuf buf) {
      int n = buf.readVarInt();
      List<MapSnapshot.RealmInfo> realms = new ArrayList<>(n);

      for (int i = 0; i < n; i++) {
         UUID id = buf.readUUID();
         String name = buf.readUtf();
         int color = buf.readInt();
         String rank = buf.readUtf();
         String flag = buf.readUtf();
         int layers = buf.readVarInt();
         List<MapSnapshot.Layer> banner = new ArrayList<>(layers);

         for (int j = 0; j < layers; j++) {
            banner.add(new MapSnapshot.Layer(buf.readUtf(), buf.readVarInt()));
         }

         realms.add(new MapSnapshot.RealmInfo(id, name, color, rank, flag, banner));
      }

      int m = buf.readVarInt();
      List<MapSnapshot.SettlementInfo> settlements = new ArrayList<>(m);

      for (int i = 0; i < m; i++) {
         String key = buf.readUtf();
         String dim = buf.readUtf();
         int id = buf.readVarInt();
         String name = buf.readUtf();
         int x = buf.readInt();
         int y = buf.readInt();
         int z = buf.readInt();
         int tier = buf.readVarInt();
         boolean capital = buf.readBoolean();
         UUID realm = buf.readBoolean() ? buf.readUUID() : null;
         int siege = buf.readVarInt();
         String owner = buf.readUtf();
         settlements.add(new MapSnapshot.SettlementInfo(key, dim, id, name, x, y, z, tier, capital, realm, siege, owner));
      }

      int d = buf.readVarInt();
      Map<String, Long2IntOpenHashMap> territory = new HashMap<>();

      for (int i = 0; i < d; i++) {
         String dim = buf.readUtf();
         int c = buf.readVarInt();
         Long2IntOpenHashMap map = new Long2IntOpenHashMap(c);

         for (int j = 0; j < c; j++) {
            map.put(buf.readLong(), buf.readVarInt());
         }

         territory.put(dim, map);
      }

      return new MapSnapshot(realms, settlements, territory);
   }

   public static record Layer(String pattern, int color) {
   }

   public static record RealmInfo(UUID id, String name, int color, String rankKey, String flag, List<MapSnapshot.Layer> banner) {
   }

   public static record SettlementInfo(
      String key, String dim, int id, String name, int x, int y, int z, int tier, boolean capital, UUID realm, int siege, String owner
   ) {
   }
}
