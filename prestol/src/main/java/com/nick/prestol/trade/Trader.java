package com.nick.prestol.trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/**
 * A caravan or a merchant ship of a realm. It goes round its route stop after stop, doing the orders of each
 * stop, for as long as it is running. Far from players it travels in the books only; near them it is a real
 * merchant with his pack mules or his boat that can be seen, followed and robbed.
 */
public final class Trader {
   public static final int SLOTS_PER_ANIMAL = 15;
   public static final int SHIP_SLOTS = 27;
   public static final int MAX_ANIMALS = 4;
   public static final int MAX_GUARDS = 4;
   public static final int MAX_STOPS = 8;
   private static final int JOURNAL_SIZE = 16;
   final UUID id;
   String name;
   final UUID realm;
   final Trader.Kind kind;
   int animals;
   int guards;
   boolean running;
   final List<Stop> route = new ArrayList<>();
   final Cargo cargo;
   String dim = "minecraft:overworld";
   Vec3 pos = Vec3.ZERO;
   /** Index of the stop it is heading to (or standing at while {@link #dwellUntil} is ahead). */
   int target;
   /** Next way point on the road to the target stop. */
   int wp;
   long dwellUntil;
   /** Just finished at a stop: on the way out he pays the escort and the road is judged. */
   boolean leaving;
   boolean escorted;
   boolean risky;
   /** Bandits are waiting half way along this leg. */
   boolean robPending;
   double legLength;
   double legDone;
   String status = "idle";
   List<String> statusArgs = new ArrayList<>();
   final Deque<String> journal = new ArrayDeque<>();
   // the merchant and his animals or boat while near players; not saved
   transient UUID merchant;
   transient final List<UUID> beasts = new ArrayList<>();
   transient Vec3 lastPhysical;
   transient int stuck;

   Trader(UUID id, String name, UUID realm, Trader.Kind kind) {
      this.id = id;
      this.name = name;
      this.realm = realm;
      this.kind = kind;
      this.animals = kind == Trader.Kind.CARAVAN ? 1 : 0;
      this.cargo = new Cargo(this.capacity());
   }

   public UUID id() {
      return this.id;
   }

   public String name() {
      return this.name;
   }

   public UUID realm() {
      return this.realm;
   }

   public Trader.Kind kind() {
      return this.kind;
   }

   public List<Stop> route() {
      return this.route;
   }

   public Cargo cargo() {
      return this.cargo;
   }

   int capacity() {
      return this.kind == Trader.Kind.SHIP ? SHIP_SLOTS : Math.max(1, this.animals) * SLOTS_PER_ANIMAL;
   }

   boolean physical() {
      return this.merchant != null;
   }

   void status(String key, Object... args) {
      this.status = key;
      this.statusArgs = new ArrayList<>();

      for (Object a : args) {
         this.statusArgs.add(String.valueOf(a));
      }
   }

   void note(String json) {
      this.journal.addFirst(json);

      while (this.journal.size() > JOURNAL_SIZE) {
         this.journal.removeLast();
      }
   }

   CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putUUID("id", this.id);
      t.putString("name", this.name);
      t.putUUID("realm", this.realm);
      t.putString("kind", this.kind.id());
      t.putInt("animals", this.animals);
      t.putInt("guards", this.guards);
      t.putBoolean("running", this.running);
      ListTag r = new ListTag();

      for (Stop s : this.route) {
         r.add(s.save());
      }

      t.put("route", r);
      t.put("cargo", this.cargo.save());
      t.putString("dim", this.dim);
      t.putDouble("x", this.pos.x);
      t.putDouble("y", this.pos.y);
      t.putDouble("z", this.pos.z);
      t.putInt("target", this.target);
      t.putInt("wp", this.wp);
      t.putLong("dwellUntil", this.dwellUntil);
      t.putBoolean("risky", this.risky);
      t.putBoolean("leaving", this.leaving);
      t.putBoolean("escorted", this.escorted);
      t.putBoolean("robPending", this.robPending);
      t.putDouble("legLength", this.legLength);
      t.putDouble("legDone", this.legDone);
      t.putString("status", this.status);
      ListTag args = new ListTag();

      for (String a : this.statusArgs) {
         args.add(StringTag.valueOf(a));
      }

      t.put("statusArgs", args);
      ListTag j = new ListTag();

      for (String s : this.journal) {
         j.add(StringTag.valueOf(s));
      }

      t.put("journal", j);
      return t;
   }

   static Trader load(CompoundTag t) {
      Trader.Kind kind = Trader.Kind.byId(t.getString("kind"));
      Trader tr = new Trader(t.getUUID("id"), t.getString("name"), t.getUUID("realm"), kind == null ? Trader.Kind.CARAVAN : kind);
      tr.animals = t.getInt("animals");
      tr.guards = t.getInt("guards");
      tr.running = t.getBoolean("running");

      for (Tag x : t.getList("route", 10)) {
         tr.route.add(Stop.load((CompoundTag)x));
      }

      tr.cargo.size = tr.capacity();
      tr.cargo.load(t.getList("cargo", 10));
      tr.dim = t.getString("dim");
      tr.pos = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
      tr.target = t.getInt("target");
      tr.wp = t.getInt("wp");
      tr.dwellUntil = t.getLong("dwellUntil");
      tr.risky = t.getBoolean("risky");
      tr.leaving = t.getBoolean("leaving");
      tr.escorted = t.getBoolean("escorted");
      tr.robPending = t.getBoolean("robPending");
      tr.legLength = t.getDouble("legLength");
      tr.legDone = t.getDouble("legDone");
      tr.status = t.getString("status");

      for (Tag a : t.getList("statusArgs", 8)) {
         tr.statusArgs.add(a.getAsString());
      }

      for (Tag j : t.getList("journal", 8)) {
         tr.journal.addLast(j.getAsString());
      }

      return tr;
   }

   static UUID uuid(Tag t) {
      return NbtUtils.loadUUID(t);
   }

   public static enum Kind {
      CARAVAN,
      SHIP;

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public static Trader.Kind byId(String id) {
         for (Trader.Kind k : values()) {
            if (k.id().equals(id)) {
               return k;
            }
         }

         return null;
      }
   }
}
