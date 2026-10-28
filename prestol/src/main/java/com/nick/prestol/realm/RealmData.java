package com.nick.prestol.realm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class RealmData extends SavedData {
   private static final String NAME = "prestol_realms";
   public static final int CHRONICLE_SIZE = 400;
   private final Map<UUID, Realm> realms = new LinkedHashMap<>();
   private final Map<String, Holding> holdings = new HashMap<>();
   private final List<RealmData.War> wars = new ArrayList<>();
   private final Map<String, Long> truces = new HashMap<>();
   private final Set<String> offers = new LinkedHashSet<>();
   private final Map<UUID, Set<String>> applications = new HashMap<>();
   private final Map<UUID, Map<String, Long>> invites = new HashMap<>();
   private final Deque<RealmData.Entry> chronicle = new ArrayDeque<>();
   private boolean imported;

   public static RealmData get(MinecraftServer server) {
      return (RealmData)server.overworld().getDataStorage().computeIfAbsent(RealmData::load, RealmData::new, "prestol_realms");
   }

   public Collection<Realm> all() {
      return this.realms.values();
   }

   public Realm byId(UUID id) {
      return id == null ? null : this.realms.get(id);
   }

   public Realm byName(String name) {
      String n = name.trim().toLowerCase(Locale.ROOT);

      for (Realm r : this.realms.values()) {
         if (r.name().toLowerCase(Locale.ROOT).equals(n)) {
            return r;
         }
      }

      return null;
   }

   public Realm byRuler(UUID player) {
      for (Realm r : this.realms.values()) {
         if (r.ruler().equals(player)) {
            return r;
         }
      }

      return null;
   }

   public Realm byMember(String settlement) {
      for (Realm r : this.realms.values()) {
         if (r.members().contains(settlement)) {
            return r;
         }
      }

      return null;
   }

   public void add(Realm r) {
      this.realms.put(r.id(), r);
      this.setDirty();
   }

   public void remove(Realm r) {
      this.realms.remove(r.id());

      for (Realm o : this.realms.values()) {
         o.allies().remove(r.id());
      }

      this.wars.removeIf(w -> w.involves(r.id()));
      String id = r.id().toString();
      this.offers.removeIf(ox -> ox.contains(id));
      this.truces.keySet().removeIf(k -> k.contains(id));
      this.applications.remove(r.id());
      this.invites.remove(r.id());
      this.setDirty();
   }

   public Holding holding(String settlement) {
      return this.holdings.computeIfAbsent(settlement, k -> new Holding());
   }

   public Holding peek(String settlement) {
      return this.holdings.get(settlement);
   }

   public Map<String, Holding> holdings() {
      return this.holdings;
   }

   public void forget(String settlement) {
      if (this.holdings.remove(settlement) != null) {
         this.setDirty();
      }

      for (Set<String> s : this.applications.values()) {
         s.remove(settlement);
      }

      for (Map<String, Long> m : this.invites.values()) {
         m.remove(settlement);
      }
   }

   public static String pair(UUID a, UUID b) {
      return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
   }

   public List<RealmData.War> wars() {
      return this.wars;
   }

   public RealmData.War war(UUID a, UUID b) {
      if (a != null && b != null) {
         for (RealmData.War w : this.wars) {
            if (w.involves(a) && w.involves(b) && !a.equals(b)) {
               return w;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public void addWar(RealmData.War w) {
      this.wars.add(w);
      this.setDirty();
   }

   public void endWar(RealmData.War w) {
      this.wars.remove(w);
      this.setDirty();
   }

   public long truceUntil(UUID a, UUID b) {
      return this.truces.getOrDefault(pair(a, b), -1L);
   }

   public void setTruce(UUID a, UUID b, long until) {
      this.truces.put(pair(a, b), until);
      this.setDirty();
   }

   public boolean hasOffer(String kind, UUID from, UUID to) {
      return this.offers.contains(kind + "|" + from + "|" + to);
   }

   public void setOffer(String kind, UUID from, UUID to, boolean on) {
      String k = kind + "|" + from + "|" + to;
      if (on ? this.offers.add(k) : this.offers.remove(k)) {
         this.setDirty();
      }
   }

   public List<UUID> offersTo(String kind, UUID to) {
      List<UUID> out = new ArrayList<>();
      String tail = "|" + to;

      for (String o : this.offers) {
         if (o.startsWith(kind + "|") && o.endsWith(tail)) {
            try {
               out.add(UUID.fromString(o.substring(kind.length() + 1, o.length() - tail.length())));
            } catch (IllegalArgumentException var8) {
            }
         }
      }

      return out;
   }

   public Set<String> applications(UUID realm) {
      return this.applications.computeIfAbsent(realm, k -> new LinkedHashSet<>());
   }

   public Map<String, Long> invites(UUID realm) {
      return this.invites.computeIfAbsent(realm, k -> new LinkedHashMap<>());
   }

   public List<UUID> invitesFor(String settlement, long now) {
      List<UUID> out = new ArrayList<>();

      for (Map.Entry<UUID, Map<String, Long>> e : this.invites.entrySet()) {
         Long until = e.getValue().get(settlement);
         if (until != null && until >= now) {
            out.add(e.getKey());
         }
      }

      return out;
   }

   public void write(long time, String key, List<String> args, List<UUID> realms) {
      this.chronicle.addLast(new RealmData.Entry(time, key, List.copyOf(args), List.copyOf(realms)));

      while (this.chronicle.size() > 400) {
         this.chronicle.removeFirst();
      }

      this.setDirty();
   }

   public List<RealmData.Entry> chronicle(UUID realm, int max) {
      List<RealmData.Entry> out = new ArrayList<>();
      Iterator<RealmData.Entry> it = this.chronicle.descendingIterator();

      while (it.hasNext() && out.size() < max) {
         RealmData.Entry e = it.next();
         if (e.realms().isEmpty() || realm != null && e.realms().contains(realm)) {
            out.add(e);
         }
      }

      return out;
   }

   public boolean imported() {
      return this.imported;
   }

   public void setImported() {
      this.imported = true;
      this.setDirty();
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag rl = new ListTag();

      for (Realm r : this.realms.values()) {
         rl.add(r.save());
      }

      tag.put("realms", rl);
      CompoundTag hs = new CompoundTag();
      this.holdings.forEach((k, h) -> {
         if (!h.isEmpty()) {
            hs.put(k, h.save());
         }
      });
      tag.put("holdings", hs);
      ListTag wl = new ListTag();

      for (RealmData.War w : this.wars) {
         CompoundTag t = new CompoundTag();
         t.putUUID("attacker", w.attacker());
         t.putUUID("defender", w.defender());
         t.putLong("declared", w.declaredAt());
         t.putLong("active", w.activeAt());
         wl.add(t);
      }

      tag.put("wars", wl);
      CompoundTag tr = new CompoundTag();
      this.truces.forEach(tr::putLong);
      tag.put("truces", tr);
      ListTag of = new ListTag();

      for (String o : this.offers) {
         of.add(StringTag.valueOf(o));
      }

      tag.put("offers", of);
      CompoundTag ap = new CompoundTag();
      this.applications.forEach((r, sx) -> {
         ListTag l = new ListTag();

         for (String k : sx) {
            l.add(StringTag.valueOf(k));
         }

         ap.put(r.toString(), l);
      });
      tag.put("applications", ap);
      CompoundTag inv = new CompoundTag();
      this.invites.forEach((r, m) -> {
         CompoundTag tx = new CompoundTag();
         m.forEach(tx::putLong);
         inv.put(r.toString(), tx);
      });
      tag.put("invites", inv);
      ListTag ch = new ListTag();

      for (RealmData.Entry e : this.chronicle) {
         CompoundTag t = new CompoundTag();
         t.putLong("t", e.time());
         t.putString("k", e.key());
         ListTag a = new ListTag();

         for (String s : e.args()) {
            a.add(StringTag.valueOf(s));
         }

         t.put("a", a);
         ListTag r = new ListTag();

         for (UUID u : e.realms()) {
            r.add(StringTag.valueOf(u.toString()));
         }

         t.put("r", r);
         ch.add(t);
      }

      tag.put("chronicle", ch);
      tag.putBoolean("imported", this.imported);
      return tag;
   }

   public static RealmData load(CompoundTag tag) {
      RealmData d = new RealmData();
      ListTag rl = tag.getList("realms", 10);

      for (int i = 0; i < rl.size(); i++) {
         Realm r = Realm.load(rl.getCompound(i));
         d.realms.put(r.id(), r);
      }

      CompoundTag hs = tag.getCompound("holdings");

      for (String k : hs.getAllKeys()) {
         d.holdings.put(k, Holding.load(hs.getCompound(k)));
      }

      ListTag wl = tag.getList("wars", 10);

      for (int i = 0; i < wl.size(); i++) {
         CompoundTag t = wl.getCompound(i);
         d.wars.add(new RealmData.War(t.getUUID("attacker"), t.getUUID("defender"), t.getLong("declared"), t.getLong("active")));
      }

      CompoundTag tr = tag.getCompound("truces");

      for (String k : tr.getAllKeys()) {
         d.truces.put(k, tr.getLong(k));
      }

      ListTag of = tag.getList("offers", 8);

      for (int i = 0; i < of.size(); i++) {
         d.offers.add(of.getString(i));
      }

      CompoundTag ap = tag.getCompound("applications");

      for (String r : ap.getAllKeys()) {
         try {
            Set<String> set = new LinkedHashSet<>();
            ListTag l = ap.getList(r, 8);

            for (int i = 0; i < l.size(); i++) {
               set.add(l.getString(i));
            }

            d.applications.put(UUID.fromString(r), set);
         } catch (IllegalArgumentException var20) {
         }
      }

      CompoundTag inv = tag.getCompound("invites");

      for (String r : inv.getAllKeys()) {
         try {
            Map<String, Long> m = new LinkedHashMap<>();
            CompoundTag t = inv.getCompound(r);

            for (String k : t.getAllKeys()) {
               m.put(k, t.getLong(k));
            }

            d.invites.put(UUID.fromString(r), m);
         } catch (IllegalArgumentException var19) {
         }
      }

      ListTag ch = tag.getList("chronicle", 10);

      for (int i = 0; i < ch.size(); i++) {
         CompoundTag t = ch.getCompound(i);
         List<String> args = new ArrayList<>();
         ListTag a = t.getList("a", 8);

         for (int j = 0; j < a.size(); j++) {
            args.add(a.getString(j));
         }

         List<UUID> rs = new ArrayList<>();
         ListTag r = t.getList("r", 8);

         for (int j = 0; j < r.size(); j++) {
            try {
               rs.add(UUID.fromString(r.getString(j)));
            } catch (IllegalArgumentException var18) {
            }
         }

         d.chronicle.addLast(new RealmData.Entry(t.getLong("t"), t.getString("k"), args, rs));
      }

      d.imported = tag.getBoolean("imported");
      return d;
   }

   public static record Entry(long time, String key, List<String> args, List<UUID> realms) {
   }

   public static record War(UUID attacker, UUID defender, long declaredAt, long activeAt) {
      public boolean involves(UUID realm) {
         return this.attacker.equals(realm) || this.defender.equals(realm);
      }

      public UUID other(UUID realm) {
         return this.attacker.equals(realm) ? this.defender : this.attacker;
      }
   }
}
