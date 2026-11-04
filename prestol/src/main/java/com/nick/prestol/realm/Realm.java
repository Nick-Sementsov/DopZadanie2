package com.nick.prestol.realm;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public final class Realm {
   private final UUID id;
   private String name;
   private UUID ruler;
   private String capital;
   private final Set<String> members = new LinkedHashSet<>();
   private final long founded;
   private int color = -1;
   private String flag = "";
   private ListTag banner = new ListTag();
   private final Set<UUID> allies = new LinkedHashSet<>();
   private boolean capitalWarned;

   public Realm(UUID id, String name, UUID ruler, String capital, long founded) {
      this.id = id;
      this.name = name;
      this.ruler = ruler;
      this.capital = capital;
      this.founded = founded;
      this.members.add(capital);
   }

   public UUID id() {
      return this.id;
   }

   public String name() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public UUID ruler() {
      return this.ruler;
   }

   public void setRuler(UUID ruler) {
      this.ruler = ruler;
   }

   public String capital() {
      return this.capital;
   }

   public void setCapital(String capital) {
      this.capital = capital;
      this.members.add(capital);
      this.capitalWarned = false;
   }

   public Set<String> members() {
      return this.members;
   }

   public long founded() {
      return this.founded;
   }

   public int color() {
      return this.color;
   }

   public void setColor(int color) {
      this.color = color;
   }

   public String flag() {
      return this.flag;
   }

   public void setFlag(String flag) {
      this.flag = flag == null ? "" : flag;
   }

   public ListTag banner() {
      return this.banner;
   }

   public void setBanner(ListTag banner) {
      this.banner = banner == null ? new ListTag() : banner;
   }

   public Set<UUID> allies() {
      return this.allies;
   }

   public boolean capitalWarned() {
      return this.capitalWarned;
   }

   public void setCapitalWarned(boolean warned) {
      this.capitalWarned = warned;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putString("name", this.name);
      tag.putUUID("ruler", this.ruler);
      tag.putString("capital", this.capital);
      tag.putLong("founded", this.founded);
      tag.putInt("color", this.color);
      tag.putString("flag", this.flag);
      tag.put("banner", this.banner.copy());
      tag.putBoolean("capitalWarned", this.capitalWarned);
      ListTag list = new ListTag();

      for (String m : this.members) {
         list.add(StringTag.valueOf(m));
      }

      tag.put("members", list);
      ListTag al = new ListTag();

      for (UUID a : this.allies) {
         al.add(StringTag.valueOf(a.toString()));
      }

      tag.put("allies", al);
      return tag;
   }

   public static Realm load(CompoundTag tag) {
      Realm r = new Realm(tag.getUUID("id"), tag.getString("name"), tag.getUUID("ruler"), tag.getString("capital"), tag.getLong("founded"));
      r.color = tag.contains("color") ? tag.getInt("color") : -1;
      r.flag = tag.getString("flag");
      r.banner = tag.getList("banner", 10);
      r.capitalWarned = tag.getBoolean("capitalWarned");
      ListTag list = tag.getList("members", 8);

      for (int i = 0; i < list.size(); i++) {
         r.members.add(list.getString(i));
      }

      ListTag al = tag.getList("allies", 8);

      for (int i = 0; i < al.size(); i++) {
         try {
            r.allies.add(UUID.fromString(al.getString(i)));
         } catch (IllegalArgumentException var6) {
         }
      }

      return r;
   }
}
