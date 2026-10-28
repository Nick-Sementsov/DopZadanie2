package com.nick.prestol.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nick.prestol.client.FlagArt;
import com.nick.prestol.client.PrestolClient;
import com.nick.prestol.map.MapSnapshot;
import com.nick.prestol.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BookScreen extends Screen {
   private static final int W = 400;
   private static final int H = 250;
   private static final int LX = 28;
   private static final int RX = 214;
   private static final int PW = 158;
   private static final int TY = 24;
   private static final int BY = 224;
   private static CompoundTag state = new CompoundTag();
   private static String pendingHint = "";
   private static BookScreen.Page lastPage = BookScreen.Page.REALM;
   private static long answerAt;
   private static Component lastAnswer;
   private static boolean lastAnswerOk;
   private BookScreen.Page page = lastPage;
   private float k = 1.0F;
   private int ox;
   private int oy;
   private int mx;
   private int my;
   private int ticks;
   private final List<BookScreen.Hit> hits = new ArrayList<>();
   private final List<BookScreen.Hit> tips = new ArrayList<>();
   private final Map<String, int[]> scrollAreas = new HashMap<>();
   private final Map<String, Integer> scroll = new HashMap<>();
   private final Map<String, StringBuilder> fields = new HashMap<>();
   private String focus;
   private String confirm;
   private String selSettlement;
   private String selPost;
   private String selRealm;
   private String selFlag;
   private int foundIndex;
   private int decreeIndex;
   private int[] clip;
   private static final List<String> KINDS = List.of("warrior", "spearman", "shieldman", "archer", "crossbowman");

   protected BookScreen() {
      super(Component.translatable("prestol.book.title"));
   }

   public static void hint(String h) {
      pendingHint = h == null ? "" : h;
   }

   public static void receive(CompoundTag s, boolean open) {
      state = s == null ? new CompoundTag() : s;
      if (state.contains("answer")) {
         answerAt = System.currentTimeMillis();
         lastAnswer = answer();
         lastAnswerOk = state.getBoolean("answerOk");
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof BookScreen book) {
         book.afterState();
      } else {
         if (open) {
            BookScreen book = new BookScreen();
            book.applyHint(pendingHint);
            pendingHint = "";
            mc.setScreen(book);
            book.afterState();
         } else if (state.contains("answer") && mc.player != null) {
            Component a = answer();
            if (a != null) {
               mc.player.displayClientMessage(a, true);
            }
         }
      }
   }

   private static Component answer() {
      try {
         return Serializer.fromJson(state.getString("answer"));
      } catch (RuntimeException var1) {
         return null;
      }
   }

   private void applyHint(String h) {
      if (h.startsWith("settlement ")) {
         this.page = BookScreen.Page.SETTLEMENTS;
         this.selSettlement = h.substring("settlement ".length());
      } else if (h.equals("lands")) {
         this.page = BookScreen.Page.LANDS;
      }
   }

   private void afterState() {
      if (state.contains("pending") && (this.minecraft == null || !(this.minecraft.screen instanceof ScrollScreen))) {
         CompoundTag p = state.getCompound("pending");
         ScrollScreen.open(p.getString("key"), p.getString("name"), p.getInt("seconds"));
      }

      if (this.page == BookScreen.Page.ADMIN && !state.getBoolean("op")) {
         this.page = BookScreen.Page.REALM;
      }
   }

   private static void order(String o) {
      Net.order(o);
   }

   private static boolean hasRealm() {
      return state.contains("realm");
   }

   private static CompoundTag realm() {
      return state.getCompound("realm");
   }

   private static boolean ruler() {
      return hasRealm() && realm().getBoolean("ruler");
   }

   private static List<CompoundTag> list(CompoundTag tag, String key) {
      List<CompoundTag> out = new ArrayList<>();
      ListTag l = tag.getList(key, 10);

      for (int i = 0; i < l.size(); i++) {
         out.add(l.getCompound(i));
      }

      return out;
   }

   private static List<MapSnapshot.Layer> banner(CompoundTag realm) {
      List<MapSnapshot.Layer> out = new ArrayList<>();

      for (CompoundTag l : list(realm, "banner")) {
         out.add(new MapSnapshot.Layer(l.getString("p"), l.getInt("c")));
      }

      return out;
   }

   private static Component tier(int t) {
      return Component.translatable("prestol.tier." + new String[]{"hamlet", "village", "town"}[Math.max(0, Math.min(2, t))]);
   }

   private static Object arg(String s) {
      return s.startsWith("@") ? Component.translatable(s.substring(1)) : s;
   }

   protected void init() {
      this.k = Math.min(1.0F, Math.min((float)(this.width - 12) / 440.0F, (float)(this.height - 8) / 250.0F));
      this.ox = (int)(((float)this.width - 440.0F * this.k) / 2.0F);
      this.oy = (int)(((float)this.height - 250.0F * this.k) / 2.0F);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void tick() {
      if (++this.ticks % 40 == 0) {
         order("refresh");
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.renderBackground(g);
      this.mx = (int)((float)(mouseX - this.ox) / this.k);
      this.my = (int)((float)(mouseY - this.oy) / this.k);
      this.hits.clear();
      this.tips.clear();
      this.scrollAreas.clear();
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate((float)this.ox, (float)this.oy, 0.0F);
      pose.scale(this.k, this.k, 1.0F);
      this.ribbons(g, true);
      Ink.book(g, 0, 0);
      this.ribbons(g, false);
      switch (this.page) {
         case REALM:
            this.realmPage(g);
            break;
         case SETTLEMENTS:
            this.settlementsPage(g);
            break;
         case LANDS:
            this.landsPage(g);
            break;
         case WAR:
            this.warPage(g);
            break;
         case DECREES:
            this.decreesPage(g);
            break;
         case CHRONICLE:
            this.chroniclePage(g);
            break;
         case BANNER:
            this.bannerPage(g);
            break;
         case ADMIN:
            this.adminPage(g);
            break;
         case ARMY:
            this.armyPage(g);
      }

      this.answerStrip(g);
      this.closeSeal(g);
      pose.popPose();

      for (BookScreen.Hit t : this.tips) {
         if (t.in(this.mx, this.my) && t.tip() != null) {
            g.renderTooltip(this.font, this.font.split(t.tip(), 220), mouseX, mouseY);
            break;
         }
      }
   }

   private List<BookScreen.Page> pages() {
      List<BookScreen.Page> out = new ArrayList<>();

      for (BookScreen.Page p : BookScreen.Page.values()) {
         if (p != BookScreen.Page.ADMIN || state.getBoolean("op")) {
            out.add(p);
         }
      }

      return out;
   }

   private void ribbons(GuiGraphics g, boolean cloth) {
      List<BookScreen.Page> list = this.pages();

      for (int i = 0; i < list.size(); i++) {
         BookScreen.Page p = list.get(i);
         int y = 28 + i * 24;
         boolean on = p == this.page;
         boolean hover = this.mx >= 396 && this.mx < 436 && this.my >= y && this.my < y + 20;
         int length = on ? 40 : (hover ? 34 : 30);
         if (cloth) {
            Ink.ribbon(g, 388, y, length, 20, p.color);
         } else {
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(401.0F, (float)(y + 3), 0.0F);
            pose.scale(0.8F, 0.8F, 1.0F);
            g.renderItem(p.icon, 0, 0);
            pose.popPose();
            this.hits.add(new BookScreen.Hit(396, y, length - 8, 20, () -> {
               this.page = p;
               lastPage = p;
               this.confirm = null;
               this.focus = null;
            }, p.title()));
            this.tips.add(new BookScreen.Hit(396, y, length - 8, 20, null, p.title()));
         }
      }
   }

   private void closeSeal(GuiGraphics g) {
      boolean hover = this.mx >= 380 && this.mx < 398 && this.my >= -2 && this.my < 16;
      Ink.seal(g, 380, -2, 18, hover ? 13646378 : 11019802);
      this.hits.add(new BookScreen.Hit(380, -2, 18, 18, this::onClose, Ink.tr("close")));
      this.tips.add(new BookScreen.Hit(380, -2, 18, 18, null, Ink.tr("close")));
   }

   private void answerStrip(GuiGraphics g) {
      Component a = lastAnswer;
      if (a != null && System.currentTimeMillis() - answerAt <= 9000L) {
         Ink.blit(g, Ink.STRIP, 50, 234, 300, 13, 720, 28);
         boolean ok = lastAnswerOk;
         Component line = Ink.clip(Component.literal(a.getString()), Ink.F_SMALL, 290);
         Ink.centered(g, line, Ink.F_SMALL, 200, 236, ok ? -13673956 : -7464176);
      }
   }

   private int heading(GuiGraphics g, Component text, int x, int y, int w) {
      String s = text.getString();
      if (s.isEmpty()) {
         return y;
      } else {
         int dx = Ink.initial(g, s.substring(0, 1), x, y - 2);
         Component rest = Ink.clip(Component.literal(s.substring(1)), Ink.F_TITLE, w - dx);
         Ink.text(g, rest, Ink.F_TITLE, x + dx, y + 4, -7464176);
         Ink.divider(g, x, y + 20, w);
         return y + 30;
      }
   }

   private int sub(GuiGraphics g, Component text, int x, int y) {
      Ink.text(g, text, Ink.F_BOLD, x, y, -7464176);
      return y + 12;
   }

   private int line(GuiGraphics g, Component text, int x, int y, int w, int color) {
      return y + Ink.para(g, text, Ink.F_BODY, x, y, w, color);
   }

   private int small(GuiGraphics g, Component text, int x, int y, int w, int color) {
      return y + Ink.para(g, text, Ink.F_SMALL, x, y, w, color);
   }

   private int button(GuiGraphics g, Component label, int x, int y, int w, boolean enabled, Runnable action) {
      boolean hover = enabled && this.mx >= x && this.my >= y && this.mx < x + w && this.my < y + 15;
      Ink.strap(g, x, y, w, 15, enabled ? (hover ? 1 : 0) : 2);
      Component l = Ink.clip(label, Ink.F_SMALL, w - 10);
      Ink.centered(g, l, Ink.F_SMALL, x + w / 2, y + 4, enabled ? -994166 : -5199720);
      if (enabled) {
         this.hits.add(new BookScreen.Hit(x, y, w, 15, action, null));
      }

      return x + w;
   }

   private void dangerous(GuiGraphics g, String id, Component label, int x, int y, int w, boolean enabled, Runnable action) {
      boolean armed = id.equals(this.confirm);
      this.button(g, (Component)(armed ? Ink.tr("confirm") : label), x, y, w, enabled, () -> {
         if (armed) {
            this.confirm = null;
            action.run();
         } else {
            this.confirm = id;
         }
      });
   }

   private void sealButton(GuiGraphics g, Component label, int x, int y, int rgb, boolean enabled, Runnable action) {
      boolean hover = enabled && this.mx >= x && this.my >= y && this.mx < x + 20 + Ink.width(label, Ink.F_BOLD) && this.my < y + 18;
      Ink.seal(g, x, y, 18, enabled ? (hover ? brighter(rgb) : rgb) : 9079434);
      Ink.text(g, label, Ink.F_BOLD, x + 21, y + 5, enabled ? (hover ? -7464176 : -13951730) : -9544893);
      if (enabled) {
         this.hits.add(new BookScreen.Hit(x, y, 21 + Ink.width(label, Ink.F_BOLD), 18, action, null));
      }
   }

   private static int brighter(int rgb) {
      int r = Math.min(255, (rgb >> 16 & 0xFF) + 40);
      int gr = Math.min(255, (rgb >> 8 & 0xFF) + 40);
      int b = Math.min(255, (rgb & 0xFF) + 40);
      return r << 16 | gr << 8 | b;
   }

   private String field(GuiGraphics g, String id, int x, int y, int w, Component placeholder) {
      StringBuilder sb = this.fields.computeIfAbsent(id, k -> new StringBuilder());
      boolean focused = id.equals(this.focus);
      g.fill(x, y + 11, x + w, y + 12, focused ? -7464176 : -9544893);
      if (sb.length() == 0 && !focused) {
         Ink.text(g, placeholder, Ink.F_BODY, x + 2, y + 1, -6518162);
      } else {
         String shown = sb.toString();
         Component c = Component.literal(shown + (focused && this.ticks / 10 % 2 == 0 ? "|" : ""));
         Ink.text(g, Ink.clip(c, Ink.F_BODY, w - 2), Ink.F_BODY, x + 2, y + 1, -13951730);
      }

      this.hits.add(new BookScreen.Hit(x, y - 2, w, 15, () -> this.focus = id, null));
      return sb.toString().trim();
   }

   private boolean row(GuiGraphics g, int x, int y, int w, int h, boolean selected, Runnable action) {
      boolean hover = this.mx >= x && this.my >= y && this.mx < x + w && this.my < y + h && this.inScroll(x, y);
      if (selected) {
         g.fill(x - 2, y - 1, x + w, y + h - 1, 814619408);
      } else if (hover) {
         g.fill(x - 2, y - 1, x + w, y + h - 1, 405478670);
      }

      this.hits.add(new BookScreen.Hit(x, y, w, h, action, null));
      return hover;
   }

   private boolean inScroll(int x, int y) {
      return this.clip == null || this.my >= this.clip[1] && this.my < this.clip[1] + this.clip[3];
   }

   private int beginScroll(GuiGraphics g, String id, int x, int y, int w, int h) {
      this.clip = new int[]{x, y, w, h};
      this.scrollAreas.put(id, new int[]{x, y, w, h});
      g.enableScissor(
         (int)((float)this.ox + (float)(x - 3) * this.k),
         (int)((float)this.oy + (float)y * this.k),
         (int)((float)this.ox + (float)(x + w) * this.k),
         (int)((float)this.oy + (float)(y + h) * this.k)
      );
      return this.scroll.getOrDefault(id, 0);
   }

   private void endScroll(GuiGraphics g, String id, int contentHeight) {
      g.disableScissor();
      int[] a = this.clip;
      this.clip = null;
      int max = Math.max(0, contentHeight - a[3]);
      int s = Math.min(this.scroll.getOrDefault(id, 0), max);
      this.scroll.put(id, s);
      this.scrollAreas.get(id)[3] = a[3];
      if (max > 0) {
         int barH = Math.max(10, a[3] * a[3] / contentHeight);
         int barY = a[1] + (a[3] - barH) * s / max;
         g.fill(a[0] + a[2] + 1, barY, a[0] + a[2] + 3, barY + barH, -2140251325);
      }

      this.hits.removeIf(hh -> hh.y() >= a[1] - 1 && hh.x() >= a[0] - 3 && hh.x() < a[0] + a[2] && (hh.y() + hh.h() <= a[1] || hh.y() >= a[1] + a[3]));
   }

   private void tip(int x, int y, int w, int h, Component tip) {
      this.tips.add(new BookScreen.Hit(x, y, w, h, null, tip));
   }

   private void realmPage(GuiGraphics g) {
      if (!hasRealm()) {
         this.noRealmPage(g);
      } else {
         CompoundTag r = realm();
         int y = this.heading(g, Component.literal(r.getString("name")), 28, 24, 158);
         y = this.line(g, Component.translatable(r.getString("rank")).append(" · ").append(Ink.tr("founded", r.getLong("founded"))), 28, y, 158, -9544893);
         Ink.frame(g, 52, y + 2, 110, 70);
         FlagArt.draw(g, r.getString("flag"), banner(r), 58, y + 8, 98, 58, true);
         y += 78;
         y = this.line(g, Ink.tr("ruler", r.getString("rulerName")), 28, y, 158, -13951730);
         y = this.line(g, Ink.tr("capital", r.getString("capital")), 28, y, 158, -13951730);
         y = this.line(g, Ink.tr("day", state.getLong("day")), 28, y + 2, 158, -9544893);
         if (state.contains("season")) {
            CompoundTag s = state.getCompound("season");
            y = this.small(
               g,
               Component.translatable("prestol.season." + s.getInt("sub"), new Object[]{s.getInt("day"), s.getInt("of"), s.getInt("toNext")}),
               28,
               y,
               158,
               -13673956
            );
         }

         int ry = this.heading(g, Ink.tr("might"), 214, 24, 158);
         ry = this.line(g, Ink.tr("population", r.getInt("population")), 214, ry, 158, -13951730);
         ry = this.line(g, Ink.tr("towns", r.getInt("towns")), 214, ry, 158, -13951730);
         ry = this.line(g, Ink.tr("settlements_count", r.getInt("settlements"), r.getInt("maxSettlements")), 214, ry, 158, -13951730);
         CompoundTag land = state.getCompound("land");
         ry = this.line(g, Ink.tr("land_count", land.getInt("used"), land.getInt("limit")), 214, ry, 158, -13951730);
         ry += 4;
         ry = this.sub(g, Ink.tr("laws_in_force"), 214, ry);
         ListTag laws = r.getList("lawsActive", 8);
         if (laws.isEmpty()) {
            ry = this.small(g, Ink.tr("none"), 214, ry, 158, -9544893);
         }

         for (int i = 0; i < laws.size(); i++) {
            ry = this.small(g, Component.literal("• ").append(Component.translatable("prestol.law." + laws.getString(i))), 214, ry, 158, -13951730);
         }

         ry += 4;
         ry = this.sub(g, Ink.tr("relations"), 214, ry);
         int shown = 0;

         for (CompoundTag o : list(state, "realms")) {
            String st = o.getString("stance");
            if (st.equals("war") || st.equals("ally")) {
               ry = this.small(g, Ink.tr("stance." + st).append(": " + o.getString("name")), 214, ry, 158, st.equals("war") ? -7464176 : -13673956);
               shown++;
            }
         }

         if (shown == 0) {
            ry = this.small(g, Ink.tr("at_peace"), 214, ry, 158, -9544893);
         }

         if (state.contains("pending")) {
            CompoundTag p = state.getCompound("pending");
            this.sealButton(
               g,
               Ink.tr("decide_fate", p.getString("name")),
               214,
               184,
               12591128,
               true,
               () -> ScrollScreen.open(p.getString("key"), p.getString("name"), p.getInt("seconds"))
            );
         }

         if (ruler()) {
            String name = this.field(g, "rename", 214, 208, 88, Ink.tr("new_name"));
            this.button(g, Ink.tr("rename"), 306, 206, 66, name.length() >= 3, () -> order("rename " + name));
            this.dangerous(g, "disband", Ink.tr("disband"), 28, 208, 100, true, () -> order("disband"));
         } else {
            this.small(g, Ink.tr("not_ruler_note"), 28, 206, 158, -9544893);
         }
      }
   }

   private List<CompoundTag> ownFree() {
      List<CompoundTag> out = new ArrayList<>();

      for (CompoundTag f : list(state, "free")) {
         if (f.getBoolean("mine")) {
            out.add(f);
         }
      }

      return out;
   }

   private void noRealmPage(GuiGraphics g) {
      int y = this.heading(g, Ink.tr("free_lord"), 28, 24, 158);
      y = this.line(g, Ink.tr("free_lord_text"), 28, y, 158, -13951730) + 6;
      List<CompoundTag> mine = this.ownFree();
      y = this.sub(g, Ink.tr("invitations"), 28, y);
      int count = 0;

      for (CompoundTag f : mine) {
         for (CompoundTag inv : list(f, "invites")) {
            this.small(g, Ink.tr("invited_to", f.getString("name"), inv.getString("name")), 28, y, 108, -13951730);
            String key = f.getString("key");
            String id = inv.getUUID("id").toString();
            this.button(g, Ink.tr("accept"), 140, y - 2, 46, true, () -> order("accept " + id + " " + key));
            y += 18;
            count++;
         }
      }

      if (count == 0) {
         y = this.small(g, Ink.tr("no_invitations"), 28, y, 158, -9544893);
      }

      int ry = this.heading(g, Ink.tr("found_realm"), 214, 24, 158);
      if (mine.isEmpty()) {
         this.line(g, Ink.tr("need_settlement"), 214, ry, 158, -9544893);
      } else {
         this.foundIndex = Math.floorMod(this.foundIndex, mine.size());
         CompoundTag pick = mine.get(this.foundIndex);
         ry = this.small(g, Ink.tr("capital_will_be"), 214, ry, 158, -9544893);
         this.button(g, Component.literal("«"), 214, ry, 16, mine.size() > 1, () -> this.foundIndex--);
         Ink.centered(g, Ink.clip(Component.literal(pick.getString("name")), Ink.F_BOLD, 118), Ink.F_BOLD, 293, ry + 4, -13951730);
         this.button(g, Component.literal("»"), 356, ry, 16, mine.size() > 1, () -> this.foundIndex++);
         ry += 22;
         String name = this.field(g, "found", 214, ry, 158, Ink.tr("realm_name"));
         ry += 18;
         String key = pick.getString("key");
         this.sealButton(g, Ink.tr("found"), 214, ry, 12069404, name.length() >= 3, () -> order("found " + key + " " + name));
         ry += 26;
         ry = this.sub(g, Ink.tr("ask_to_join"), 214, ry);
         int off = this.beginScroll(g, "join", 214, ry, 158, 224 - ry);
         int yy = ry - off;

         for (CompoundTag r : list(state, "realms")) {
            this.small(g, Ink.clip(Component.literal(r.getString("name")), Ink.F_SMALL, 94), 214, yy + 3, 158, -13951730);
            String id = r.getUUID("id").toString();
            this.button(g, Ink.tr("apply"), 312, yy, 60, true, () -> order("apply " + id + " " + key));
            yy += 17;
         }

         this.endScroll(g, "join", yy + off - ry);
      }
   }

   private CompoundTag settlement(String key) {
      for (CompoundTag s : list(state, "settlements")) {
         if (s.getString("key").equals(key)) {
            return s;
         }
      }

      return null;
   }

   private void settlementsPage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.SETTLEMENTS.title(), 28, 24, 158);
      List<CompoundTag> mine = list(state, "settlements");
      if (this.selSettlement == null && !mine.isEmpty()) {
         this.selSettlement = mine.get(0).getString("key");
      }

      List<CompoundTag> free = new ArrayList<>();

      for (CompoundTag f : list(state, "free")) {
         if (!f.getBoolean("mine")) {
            free.add(f);
         }
      }

      int off = this.beginScroll(g, "settlements", 28, y, 158, 224 - y);
      int yy = y - off;

      for (CompoundTag s : mine) {
         String key = s.getString("key");
         this.row(g, 28, yy, 158, 12, key.equals(this.selSettlement), () -> this.selSettlement = key);
         String mark = s.getBoolean("capital") ? "♛ " : (s.getInt("siege") != 0 ? "⚔ " : "");
         Component name = Component.literal(mark + s.getString("name"));
         Ink.text(g, Ink.clip(name, Ink.F_BODY, 106), Ink.F_BODY, 28, yy + 1, s.getInt("siege") != 0 ? -7464176 : -13951730);
         Component t = tier(s.getInt("tier")).copy().append(" " + s.getInt("citizens"));
         Ink.text(g, t, Ink.F_SMALL, 186 - Ink.width(t, Ink.F_SMALL), yy + 2, -9544893);
         yy += 13;
      }

      if (ruler() && !free.isEmpty()) {
         yy += 4;
         Ink.text(g, Ink.tr("free_settlements"), Ink.F_BOLD, 28, yy, -7464176);
         yy += 12;

         for (CompoundTag fx : free) {
            String key = fx.getString("key");
            Ink.text(
               g, Ink.clip(Component.literal(fx.getString("name") + " (" + fx.getString("owner") + ")"), Ink.F_SMALL, 98), Ink.F_SMALL, 28, yy + 3, -13951730
            );
            this.button(g, fx.getBoolean("invited") ? Ink.tr("invited") : Ink.tr("invite"), 130, yy, 56, !fx.getBoolean("invited"), () -> order("invite " + key));
            yy += 17;
         }
      }

      if (ruler()) {
         List<CompoundTag> apps = list(realm(), "applications");
         if (!apps.isEmpty()) {
            yy += 4;
            Ink.text(g, Ink.tr("applications"), Ink.F_BOLD, 28, yy, -7464176);
            yy += 12;

            for (CompoundTag a : apps) {
               String key = a.getString("key");
               Ink.text(g, Ink.clip(Component.literal(a.getString("name")), Ink.F_SMALL, 88), Ink.F_SMALL, 28, yy + 3, -13951730);
               this.button(g, Component.literal("✔"), 136, yy, 22, true, () -> order("approve " + key));
               this.button(g, Component.literal("✖"), 162, yy, 22, true, () -> order("reject " + key));
               yy += 17;
            }
         }
      }

      if (mine.isEmpty()) {
         yy = this.small(g, Ink.tr("no_settlements"), 28, yy, 158, -9544893);
      }

      this.endScroll(g, "settlements", yy + off - y);
      CompoundTag s = this.selSettlement == null ? null : this.settlement(this.selSettlement);
      if (s == null) {
         this.line(g, Ink.tr("pick_settlement"), 214, 54, 158, -9544893);
      } else {
         int ry = this.heading(g, Component.literal(s.getString("name")), 214, 24, 158);
         Component role = (Component)(s.getBoolean("capital") ? Component.translatable("prestol.role.capital") : tier(s.getInt("tier")));
         ry = this.line(
            g, role.copy().append(s.contains("realm") ? Component.literal(" · " + s.getString("realm")) : Ink.tr("independent")), 214, ry, 158, -9544893
         );
         ry = this.small(g, Ink.tr("owner", s.getString("owner")).append("   " + s.getInt("x") + ", " + s.getInt("z")), 214, ry, 158, -9544893) + 3;
         ry = this.stat(g, Ink.tr("citizens"), s.getInt("citizens"), s.contains("needCitizens") ? s.getInt("needCitizens") : -1, ry);
         ry = this.stat(g, Ink.tr("hall"), s.getInt("hall"), s.contains("needHall") ? s.getInt("needHall") : -1, ry);
         ry = this.stat(g, Ink.tr("buildings"), s.getInt("buildings"), s.contains("needBuildings") ? s.getInt("needBuildings") : -1, ry);
         if (s.contains("needCitizens")) {
            ry = this.small(g, Ink.tr("next_tier", tier(s.getInt("tier") + 1)), 214, ry, 158, -9544893);
         }

         double happy = s.getDouble("happiness");
         ry = this.small(g, Ink.tr("happiness", String.format("%.1f", happy)), 214, ry + 2, 158, happy < 4.0 ? -7464176 : (happy < 6.0 ? -9544893 : -13673956));
         if (s.contains("militia")) {
            int up = s.getInt("militiaUp");
            ry = this.small(g, up > 0 ? Ink.tr("militia_up", up) : Ink.tr("militia_line", s.getInt("militia")), 214, ry, 158, up > 0 ? -7464176 : -9544893);
         }

         if (s.getBoolean("enslaved")) {
            ry = this.small(g, Ink.tr("enslaved"), 214, ry, 158, -7464176);
         }

         if (s.contains("immuneDay")) {
            ry = this.small(g, Ink.tr("immune_until", s.getLong("immuneDay")), 214, ry, 158, -14730389);
         }

         if (s.contains("unhappyDays")) {
            ry = this.small(g, Ink.tr("unrest", s.getInt("unhappyDays"), s.getInt("mutinyDays")), 214, ry, 158, -7464176);
         }

         String key = s.getString("key");
         int by = 208;
         if (s.getBoolean("former")) {
            this.small(g, Ink.tr("revolt_terms", s.getLong("revoltDay"), String.format("%.1f", s.getDouble("revoltHappiness"))), 214, by - 34, 158, -9544893);
            this.sealButton(g, Ink.tr("revolt"), 214, by - 4, 8006282, true, () -> order("revolt " + key));
         } else {
            if (ruler() && s.contains("realm") && s.getString("realm").equals(realm().getString("name"))) {
               if (!s.getBoolean("capital")) {
                  this.button(g, Ink.tr("make_capital"), 214, by, 78, s.getBoolean("canBeCapital"), () -> order("capital " + key));
                  if (!s.getBoolean("canBeCapital")) {
                     this.tip(214, by, 78, 15, Ink.tr("capital_needs", s.getInt("capitalCitizens")));
                  }

                  this.dangerous(g, "kick" + key, Ink.tr("kick"), 296, by, 76, true, () -> order("kick " + key));
               }
            } else if (s.getBoolean("mine") && s.contains("realm") && !s.getBoolean("capital")) {
               this.dangerous(g, "leave" + key, Ink.tr("leave"), 214, by, 100, true, () -> order("leave " + key));
            }

            if (s.getBoolean("enslaved") && (s.getBoolean("mine") || ruler())) {
               this.button(g, Ink.tr("free_slaves"), 214, by - 18, 100, true, () -> order("free " + key));
            }
         }
      }
   }

   private static String clock(int seconds) {
      return seconds / 60 + ":" + String.format("%02d", seconds % 60);
   }

   private void armyPage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.ARMY.title(), 28, 24, 158);
      if (!state.getBoolean("armyOn")) {
         this.line(g, Ink.tr("army_off"), 28, y, 158, -9544893);
      } else {
         List<CompoundTag> posts = list(state, "army");
         CompoundTag sel = null;

         for (CompoundTag p : posts) {
            if (p.getString("key").equals(this.selPost)) {
               sel = p;
            }
         }

         if (sel == null && !posts.isEmpty()) {
            sel = posts.get(0);
            this.selPost = sel.getString("key");
         }

         int off = this.beginScroll(g, "army", 28, y, 158, 162 - y);
         int yy = y - off;
         String last = null;

         for (CompoundTag px : posts) {
            String settlement = px.getString("settlement");
            if (!settlement.equals(last)) {
               Ink.text(g, Ink.clip(Component.literal(settlement), Ink.F_BOLD, 158), Ink.F_BOLD, 28, yy + 1, -7464176);
               yy += 12;
               last = settlement;
            }

            String key = px.getString("key");
            this.row(g, 28, yy, 158, 12, key.equals(this.selPost), () -> this.selPost = key);
            Component name = Component.translatable("prestol.army.building." + px.getString("building")).append(" " + px.getInt("level"));
            Ink.text(g, Ink.clip(name, Ink.F_BODY, 124), Ink.F_BODY, 34, yy + 1, -13951730);
            Component count = Component.literal(px.getInt("have") + " / " + px.getInt("slots"));
            Ink.text(g, count, Ink.F_SMALL, 186 - Ink.width(count, Ink.F_SMALL), yy + 2, px.getInt("have") < px.getInt("slots") ? -7464176 : -13673956);
            yy += 13;
         }

         if (posts.isEmpty()) {
            yy = this.small(g, Ink.tr("army_none"), 28, yy, 158, -9544893);
         }

         this.endScroll(g, "army", yy + off - y);
         this.small(g, Ink.tr("army_intro"), 28, 166, 158, -9544893);
         if (sel == null) {
            this.line(g, Ink.tr("army_pick"), 214, 54, 158, -9544893);
         } else {
            int ry = this.heading(g, Component.translatable("prestol.army.building." + sel.getString("building")), 214, 24, 158);
            ry = this.line(
               g, Component.literal(sel.getString("settlement") + " · ").append(Ink.tr("army_level", sel.getInt("level"))), 214, ry, 158, -9544893
            );
            int have = sel.getInt("have");
            int slots = sel.getInt("slots");
            ry = this.stat(g, Ink.tr("army_garrison"), have, slots, ry);
            List<Component> lack = new ArrayList<>();

            for (Tag t : sel.getList("lack", 8)) {
               lack.add(
                  Component.translatable(
                     "prestol.army.need." + t.getAsString().toLowerCase(Locale.ROOT), new Object[]{state.getInt("armyArrows"), state.getInt("armyFood")}
                  )
               );
            }

            int color = -9544893;
            Component status;
            if (have >= slots) {
               status = Ink.tr("army_full");
               color = -13673956;
            } else if (sel.getBoolean("siege")) {
               status = Ink.tr("army_siege");
               color = -7464176;
            } else if (!lack.isEmpty()) {
               MutableComponent list = Component.empty();

               for (int i = 0; i < lack.size(); i++) {
                  list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(lack.get(i));
               }

               status = Ink.tr("army_lack", list);
               color = -7464176;
            } else if (sel.contains("wait")) {
               status = Ink.tr("army_next", clock(sel.getInt("wait")));
            } else {
               status = Ink.tr("army_soon");
            }

            ry = this.small(g, status, 214, ry + 2, 158, color);
            ry = this.small(g, Ink.tr("army_task", Component.translatable("prestol.army.task." + sel.getString("task"))), 214, ry, 158, -9544893);
            Component school = Component.translatable("prestol.army.school." + sel.getString("school"));
            if (sel.getInt("schoolLevel") > 0) {
               String rank = sel.contains("rankHigh")
                  ? (
                     sel.getInt("rankLow") == sel.getInt("rankHigh")
                        ? String.valueOf(sel.getInt("rankHigh"))
                        : sel.getInt("rankLow") + "–" + sel.getInt("rankHigh")
                  )
                  : "?";
               ry = this.small(
                     g,
                     Ink.tr("army_school", school, sel.getInt("schoolLevel"), rank, sel.getInt("cap"), state.getInt("armyTraining")),
                     214,
                     ry,
                     158,
                     -9544893
                  )
                  + 3;
            } else {
               ry = this.small(g, Ink.tr("army_noschool", school), 214, ry, 158, -9544893) + 3;
            }

            ry = this.sub(g, Ink.tr("army_choose"), 214, ry);
            String current = sel.getString("type");
            String postKey = sel.getString("key");

            for (String kind : KINDS) {
               boolean on = kind.equals(current);
               this.row(g, 214, ry, 158, 19, on, () -> {
                  if (!on) {
                     order("army " + postKey + " " + kind);
                  }
               });
               Ink.text(
                  g,
                  Component.literal(on ? "✔ " : "").append(Component.translatable("prestol.army.type." + kind)),
                  Ink.F_BODY,
                  216,
                  ry + 1,
                  on ? -7464176 : -13951730
               );
               Ink.text(
                  g,
                  Ink.clip(Component.translatable("prestol.army.cost." + kind, new Object[]{state.getInt("armyArrows")}), Ink.F_SMALL, 146),
                  Ink.F_SMALL,
                  224,
                  ry + 10,
                  -9544893
               );
               ry += 19;
            }
         }
      }
   }

   private int stat(GuiGraphics g, Component label, int have, int need, int y) {
      Ink.text(g, label, Ink.F_BODY, 214, y, -13951730);
      String v = need > 0 ? have + " / " + need : String.valueOf(have);
      Ink.text(
         g, Component.literal(v), Ink.F_BODY, 312 - Ink.width(Component.literal(v), Ink.F_BODY) - 4, y, need > 0 && have < need ? -7464176 : -13951730
      );
      if (need > 0) {
         Ink.bar(g, 314, y + 2, 58, 6, (float)have / (float)need, have >= need ? -11634130 : -6669790);
      }

      return y + 12;
   }

   private void landsPage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.LANDS.title(), 28, 24, 158);
      CompoundTag land = state.getCompound("land");
      int used = land.getInt("used");
      int limit = land.getInt("limit");
      y = this.line(g, Ink.tr("land_count", used, limit), 28, y, 158, -13951730);
      Ink.bar(g, 28, y + 1, 158, 7, limit == 0 ? 0.0F : (float)used / (float)limit, -9532870);
      y += 14;
      y = this.small(g, Ink.tr("land_rules", land.getInt("reach")), 28, y, 158, -9544893) + 6;
      y = this.sub(g, Ink.tr("how_to_draw"), 28, y);

      for (int i = 1; i <= 4; i++) {
         y = this.small(g, Component.literal(i + ". ").append(Ink.tr("draw_step" + i)), 28, y, 158, -13951730) + 2;
      }

      int ry = this.heading(g, Ink.tr("map_of_realms"), 214, 24, 158);
      if (PrestolClient.hasMapMod()) {
         this.sealButton(g, Ink.tr("open_map"), 214, ry, 2776186, true, this::openMap);
         ry += 28;
      } else {
         ry = this.line(g, Ink.tr("no_xaero"), 214, ry, 158, -7464176) + 6;
      }

      ry = this.sub(g, Ink.tr("legend"), 214, ry);
      int color = hasRealm() ? 0xFF000000 | realm().getInt("color") : -6381922;
      ry = this.legend(g, color, Ink.tr("legend_own"), ry);
      ry = this.legend(g, -6381922, Ink.tr("legend_free"), ry);
      ry = this.legend(g, -4186086, Ink.tr("legend_siege"), ry);
      this.small(g, Ink.tr("legend_flags"), 214, ry + 4, 158, -9544893);
   }

   private int legend(GuiGraphics g, int color, Component text, int y) {
      g.fill(214, y + 1, 224, y + 9, color);
      g.renderOutline(214, y + 1, 10, 8, -13951730);
      Ink.text(g, text, Ink.F_SMALL, 228, y + 1, -13951730);
      return y + 12;
   }

   private void openMap() {
      try {
         Class.forName("com.nick.prestol.client.xaero.MapMenu").getMethod("openWorldMap", Screen.class).invoke(null, null);
      } catch (RuntimeException | LinkageError | ReflectiveOperationException var2) {
         this.onClose();
      }
   }

   private CompoundTag realmById(String id) {
      for (CompoundTag r : list(state, "realms")) {
         if (r.getUUID("id").toString().equals(id)) {
            return r;
         }
      }

      return null;
   }

   private void warPage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.WAR.title(), 28, 24, 158);
      List<CompoundTag> others = new ArrayList<>();

      for (CompoundTag r : list(state, "realms")) {
         if (!"self".equals(r.getString("stance"))) {
            others.add(r);
         }
      }

      if (this.selRealm == null && !others.isEmpty()) {
         this.selRealm = others.get(0).getUUID("id").toString();
      }

      int sieges = list(state, "sieges").size();
      int listH = 224 - y - (sieges > 0 ? 18 + sieges * 20 : 0);
      int off = this.beginScroll(g, "realms", 28, y, 158, Math.max(30, listH));
      int yy = y - off;

      for (CompoundTag rx : others) {
         String id = rx.getUUID("id").toString();
         this.row(g, 28, yy, 158, 16, id.equals(this.selRealm), () -> this.selRealm = id);
         FlagArt.draw(g, rx.getString("flag"), banner(rx), 28, yy + 1, 18, 12, false);
         Ink.text(g, Ink.clip(Component.literal(rx.getString("name")), Ink.F_BODY, 88), Ink.F_BODY, 50, yy + 3, -13951730);
         String st = rx.getString("stance");
         Component w = Ink.tr("stance." + (st.isEmpty() ? "neutral" : st));
         Ink.text(g, w, Ink.F_SMALL, 186 - Ink.width(w, Ink.F_SMALL), yy + 4, st.equals("war") ? -7464176 : (st.equals("ally") ? -13673956 : -9544893));
         yy += 17;
      }

      if (others.isEmpty()) {
         yy = this.small(g, Ink.tr("no_other_realms"), 28, yy, 158, -9544893);
      }

      this.endScroll(g, "realms", yy + off - y);
      if (sieges > 0) {
         int sy = y + Math.max(30, listH) + 4;
         sy = this.sub(g, Ink.tr("sieges"), 28, sy);

         for (CompoundTag s : list(state, "sieges")) {
            boolean revolt = s.getBoolean("revolt");
            Component t = Component.literal((revolt ? "✊ " : "⚔ ") + s.getString("name") + "  ")
               .append(Ink.time(s.getInt("needed") - s.getInt("held")));
            Ink.text(g, Ink.clip(t, Ink.F_SMALL, 158), Ink.F_SMALL, 28, sy, s.getBoolean("contested") ? -14730389 : -7464176);
            Ink.bar(g, 28, sy + 10, 158, 5, (float)s.getInt("held") / (float)Math.max(1, s.getInt("needed")), revolt ? -8770934 : -5234662);
            sy += 20;
         }
      }

      CompoundTag rx = this.selRealm == null ? null : this.realmById(this.selRealm);
      if (rx == null) {
         this.line(g, Ink.tr("war_rules"), 214, 54, 158, -9544893);
      } else {
         int ry = this.heading(g, Component.literal(rx.getString("name")), 214, 24, 158);
         FlagArt.draw(g, rx.getString("flag"), banner(rx), 332, ry, 40, 26, false);
         ry = this.small(g, Component.translatable(rx.getString("rank")), 214, ry, 114, -9544893);
         ry = this.small(g, Ink.tr("ruler", rx.getString("rulerName")), 214, ry, 114, -13951730);
         ry = this.small(g, Ink.tr("capital", rx.getString("capital")), 214, ry, 114, -13951730);
         ry = this.small(g, Ink.tr("population", rx.getInt("population")), 214, ry, 158, -13951730) + 4;
         String st = rx.getString("stance");
         String id = this.selRealm;
         if (!hasRealm()) {
            this.line(g, Ink.tr("no_realm_no_war"), 214, ry, 158, -9544893);
         } else if (st.equals("war")) {
            int in = rx.getInt("warIn");
            ry = this.line(g, in > 0 ? Ink.tr("mobilizing", Ink.time(in)) : Ink.tr("war_on"), 214, ry, 158, -7464176);
            ry = this.small(g, Ink.tr("siege_rules"), 214, ry, 158, -9544893) + 4;
            if (rx.getBoolean("peaceFromThem")) {
               ry = this.small(g, Ink.tr("they_offer_peace"), 214, ry, 158, -13673956);
            }

            if (ruler()) {
               this.sealButton(
                  g,
                  rx.getBoolean("peaceFromThem") ? Ink.tr("accept_peace") : (rx.getBoolean("peaceFromUs") ? Ink.tr("peace_sent") : Ink.tr("offer_peace")),
                  214,
                  204,
                  3963438,
                  !rx.getBoolean("peaceFromUs") || rx.getBoolean("peaceFromThem"),
                  () -> order("peace " + id)
               );
            }
         } else if (st.equals("ally")) {
            ry = this.line(g, Ink.tr("allied"), 214, ry, 158, -13673956);
            if (ruler()) {
               this.dangerous(g, "unally" + id, Ink.tr("break_alliance"), 214, 208, 110, true, () -> order("unally " + id));
            }
         } else {
            if (rx.contains("truceDay")) {
               ry = this.line(g, Ink.tr("truce_until", rx.getLong("truceDay")), 214, ry, 158, -14730389);
            }

            if (rx.getBoolean("allyFromThem")) {
               ry = this.small(g, Ink.tr("they_offer_alliance"), 214, ry, 158, -13673956);
            }

            if (!ruler()) {
               this.small(g, Ink.tr("not_ruler_note"), 214, 206, 158, -9544893);
            } else {
               this.sealButton(
                  g,
                  rx.getBoolean("allyFromThem") ? Ink.tr("accept_alliance") : (rx.getBoolean("allyFromUs") ? Ink.tr("alliance_sent") : Ink.tr("offer_alliance")),
                  214,
                  178,
                  3963438,
                  !rx.getBoolean("allyFromUs") || rx.getBoolean("allyFromThem"),
                  () -> order("ally " + id)
               );
               boolean armed = ("war" + id).equals(this.confirm);
               this.sealButton(g, armed ? Ink.tr("confirm_war") : Ink.tr("declare_war"), 214, 202, 11541008, !rx.contains("truceDay"), () -> {
                  if (armed) {
                     this.confirm = null;
                     order("war " + id);
                  } else {
                     this.confirm = "war" + id;
                  }
               });
            }
         }
      }
   }

   private void decreesPage(GuiGraphics g) {
      int y = this.heading(g, Ink.tr("laws"), 28, 24, 158);
      if (!hasRealm()) {
         this.line(g, Ink.tr("no_realm_no_laws"), 28, y, 158, -9544893);
      } else {
         CompoundTag r = realm();
         ListTag enacted = r.getList("laws", 8);
         ListTag active = r.getList("lawsActive", 8);
         y = this.small(g, Ink.tr("law_slots", enacted.size(), r.getInt("lawSlots")), 28, y, 158, -9544893);
         if (r.getInt("lawLock") > 0) {
            y = this.small(g, Ink.tr("law_lock", Ink.time(r.getInt("lawLock"))), 28, y, 158, -7464176);
         }

         y += 2;

         for (String id : new String[]{"levy", "guilds", "fields", "roads", "charter", "schools"}) {
            boolean in = contains(enacted, id);
            boolean works = contains(active, id);
            Component name = Component.translatable("prestol.law." + id);
            Ink.text(g, Ink.clip(name, Ink.F_BODY, 98), Ink.F_BODY, 28, y + 3, in ? (works ? -13673956 : -9544893) : -13951730);
            this.tip(28, y, 98, 14, Component.translatable("prestol.law." + id + ".desc"));
            if (ruler()) {
               this.button(g, in ? Ink.tr("repeal") : Ink.tr("enact"), 130, y, 56, true, () -> order("law " + (in ? "repeal " : "enact ") + id));
            } else if (in) {
               Ink.text(g, Ink.tr(works ? "in_force" : "dormant"), Ink.F_SMALL, 136, y + 4, -9544893);
            }

            y += 17;
         }
      }

      List<CompoundTag> decreeable = new ArrayList<>();

      for (CompoundTag s : list(state, "settlements")) {
         if (s.contains("decrees")) {
            decreeable.add(s);
         }
      }

      int ry = this.heading(g, Ink.tr("decrees"), 214, 24, 158);
      if (decreeable.isEmpty()) {
         this.line(g, Ink.tr("no_decrees"), 214, ry, 158, -9544893);
      } else {
         this.decreeIndex = Math.floorMod(this.decreeIndex, decreeable.size());
         CompoundTag sx = decreeable.get(this.decreeIndex);
         this.button(g, Component.literal("«"), 214, ry - 2, 16, decreeable.size() > 1, () -> this.decreeIndex--);
         Ink.centered(g, Ink.clip(Component.literal(sx.getString("name")), Ink.F_BOLD, 118), Ink.F_BOLD, 293, ry + 2, -13951730);
         this.button(g, Component.literal("»"), 356, ry - 2, 16, decreeable.size() > 1, () -> this.decreeIndex++);
         ry += 16;
         String key = sx.getString("key");
         int off = this.beginScroll(g, "decrees", 214, ry, 158, 224 - ry);
         int yy = ry - off;

         for (CompoundTag d : list(sx, "decrees")) {
            String id = d.getString("id");
            Component name = Component.translatable("prestol.decree." + id);
            int color = d.contains("left") ? -13673956 : (d.contains("cooldown") ? -9544893 : -13951730);
            Ink.text(g, Ink.clip(name, Ink.F_BODY, 96), Ink.F_BODY, 214, yy + 3, color);
            this.tip(214, yy, 96, 14, Component.translatable("prestol.decree." + id + ".desc"));
            if (d.contains("left")) {
               Ink.text(g, Component.literal(Ink.time(d.getInt("left"))), Ink.F_SMALL, 280, yy + 4, -13673956);
               this.button(g, Ink.tr("revoke"), 316, yy, 56, true, () -> order("revoke " + key + " " + id));
            } else if (d.contains("cooldown")) {
               Ink.text(g, Ink.tr("cooldown", Ink.time(d.getInt("cooldown"))), Ink.F_SMALL, 316, yy + 4, -9544893);
            } else {
               this.button(g, Ink.tr("issue"), 316, yy, 56, true, () -> order("decree " + key + " " + id));
            }

            yy += 17;
         }

         this.endScroll(g, "decrees", yy + off - ry);
      }
   }

   private static boolean contains(ListTag l, String s) {
      for (int i = 0; i < l.size(); i++) {
         if (l.getString(i).equals(s)) {
            return true;
         }
      }

      return false;
   }

   private void chroniclePage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.CHRONICLE.title(), 28, 24, 158);
      Ink.text(g, Ink.tr("chronicle_note"), Ink.F_SMALL, 214, 30, -9544893);
      Ink.divider(g, 214, 44, 158);
      List<CompoundTag> entries = list(state, "chronicle");
      if (entries.isEmpty()) {
         this.line(g, Ink.tr("chronicle_empty"), 28, y, 158, -9544893);
      } else {
         int[][] columns = new int[][]{{28, y, 224}, {214, 54, 224}};
         int off = this.scroll.getOrDefault("chronicle", 0);
         int col = 0;
         int cy = columns[0][1];
         int skipped = 0;
         int total = 0;
         this.scrollAreas.put("chronicle", new int[]{28, y, 344, 224 - y});

         for (CompoundTag e : entries) {
            Object[] args = list(e).toArray();
            MutableComponent text = Component.translatable("prestol.chronicle." + e.getString("key"), args);
            Component dayLine = Ink.tr("day_short", e.getLong("day"));
            int h = 10 + Ink.lines(text, Ink.F_SMALL, 158) * 9 + 4;
            total++;
            if (skipped < off) {
               skipped++;
            } else {
               if (cy + h > columns[col][2]) {
                  if (++col >= columns.length) {
                     break;
                  }

                  cy = columns[col][1];
               }

               int x = columns[col][0];
               Ink.text(g, dayLine, Ink.F_BOLD, x, cy, -7464176);
               cy += 10;
               cy += Ink.para(g, text, Ink.F_SMALL, x, cy, 158, -13951730) + 4;
            }
         }

         this.scroll.put("chronicle", Math.min(off, Math.max(0, total - 1)));
      }
   }

   private static List<Object> list(CompoundTag e) {
      List<Object> out = new ArrayList<>();
      ListTag a = e.getList("args", 8);

      for (int i = 0; i < a.size(); i++) {
         out.add(arg(a.getString(i)));
      }

      return out;
   }

   private void bannerPage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.BANNER.title(), 28, 24, 158);
      if (!hasRealm()) {
         this.line(g, Ink.tr("no_realm_no_banner"), 28, y, 158, -9544893);
      } else {
         CompoundTag r = realm();
         Ink.frame(g, 42, y, 130, 82);
         String preview = this.selFlag != null ? this.selFlag : r.getString("flag");
         FlagArt.draw(g, preview, banner(r), 49, y + 7, 116, 68, true);
         y += 86;
         if (this.selFlag != null && !this.selFlag.equals(r.getString("flag"))) {
            y = this.small(g, Ink.tr("preview"), 28, y, 158, -14730389);
         }

         int wool = state.getInt("standardWool");
         this.button(g, Ink.tr("take_standard"), 28, y + 2, 158, true, () -> order("standard"));
         this.tip(28, y + 2, 158, 15, Ink.tr("standard_cost", wool));
         y += 22;
         if (ruler()) {
            y = this.sub(g, Ink.tr("realm_color"), 28, y);
            DyeColor[] dyes = DyeColor.values();

            for (int i = 0; i < dyes.length; i++) {
               int cx = 28 + i % 8 * 19;
               int cy = y + i / 8 * 13;
               float[] c = dyes[i].getTextureDiffuseColors();
               int rgb = (int)(c[0] * 255.0F) << 16 | (int)(c[1] * 255.0F) << 8 | (int)(c[2] * 255.0F);
               g.fill(cx, cy, cx + 16, cy + 10, 0xFF000000 | rgb);
               boolean current = (r.getInt("color") & 16777215) == rgb;
               g.renderOutline(cx - 1, cy - 1, 18, 12, current ? -7464176 : -10862298);
               String hex = Integer.toHexString(rgb);
               this.hits.add(new BookScreen.Hit(cx, cy, 16, 10, () -> order("color " + hex), null));
            }
         }

         int ry = this.heading(g, Ink.tr("images"), 214, 24, 158);
         if (!state.getBoolean("paintings")) {
            this.line(g, Ink.tr("no_paintings"), 214, ry, 158, -9544893);
         } else {
            ry = this.small(g, Ink.tr("images_note"), 214, ry, 158, -9544893) + 2;
            List<String[]> choices = FlagArt.choices();
            int listBottom = ruler() ? 186 : 224;
            int top = ry;
            int off = this.beginScroll(g, "images", 214, ry, 158, listBottom - ry);
            int col = 0;
            int yy = ry - off;

            for (String[] c : choices) {
               int cx = 214 + col * 40;
               boolean sel = c[0].equals(this.selFlag) || this.selFlag == null && c[0].equals(r.getString("flag"));
               Ink.frame(g, cx, yy, 38, 28);
               FlagArt.draw(g, c[0], List.of(), cx + 3, yy + 3, 32, 22, false);
               if (sel) {
                  g.renderOutline(cx - 1, yy - 1, 40, 30, -7464176);
               }

               String id = c[0];
               boolean visible = yy + 28 > top && yy < listBottom;
               if (visible) {
                  this.hits.add(new BookScreen.Hit(cx, yy, 38, 28, () -> this.selFlag = id, null));
                  this.tips.add(new BookScreen.Hit(cx, yy, 38, 28, null, Component.literal(c[1] + (c[2].isEmpty() ? "" : " — " + c[2]) + " (" + c[3] + ")")));
               }

               if (++col == 4) {
                  col = 0;
                  yy += 31;
               }
            }

            if (col != 0) {
               yy += 31;
            }

            if (choices.isEmpty()) {
               yy = this.small(g, Ink.tr("no_images"), 214, yy, 158, -9544893);
            }

            this.endScroll(g, "images", yy + off - top);
            if (ruler()) {
               String chosen = this.selFlag;
               this.sealButton(
                  g, Ink.tr("raise_flag"), 214, 188, 11569706, chosen != null && !chosen.equals(r.getString("flag")), () -> order("flag " + chosen)
               );
               this.button(g, Ink.tr("use_banner"), 214, 210, 158, !r.getString("flag").isEmpty(), () -> {
                  this.selFlag = null;
                  order("flag -");
               });
            }
         }
      }
   }

   private void adminPage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.ADMIN.title(), 28, 24, 158);
      y = this.sub(g, Ink.tr("realms"), 28, y);
      int off = this.beginScroll(g, "admin", 28, y, 158, 224 - y);
      int yy = y - off;

      for (CompoundTag r : list(state, "realms")) {
         String id = r.getUUID("id").toString();
         Ink.text(
            g, Ink.clip(Component.literal(r.getString("name") + " (" + r.getString("rulerName") + ")"), Ink.F_SMALL, 98), Ink.F_SMALL, 28, yy + 3, -13951730
         );
         this.dangerous(g, "del" + id, Ink.tr("delete"), 130, yy, 56, true, () -> order("admin delete " + id));
         yy += 17;
      }

      this.endScroll(g, "admin", yy + off - y);
      int ry = this.heading(g, Ink.tr("wars_and_sieges"), 214, 24, 158);

      for (CompoundTag r : list(state, "realms")) {
         if ("war".equals(r.getString("stance")) && hasRealm()) {
            String a = realm().getUUID("id").toString();
            String b = r.getUUID("id").toString();
            Ink.text(g, Ink.clip(Component.literal("⚔ " + r.getString("name")), Ink.F_SMALL, 98), Ink.F_SMALL, 214, ry + 3, -7464176);
            this.button(g, Ink.tr("force_peace"), 316, ry, 56, true, () -> order("admin peace " + a + " " + b));
            ry += 17;
         }
      }

      for (CompoundTag s : list(state, "sieges")) {
         String key = s.getString("key");
         Ink.text(g, Ink.clip(Component.literal("⚑ " + s.getString("name")), Ink.F_SMALL, 98), Ink.F_SMALL, 214, ry + 3, -13951730);
         this.button(g, Ink.tr("lift"), 316, ry, 56, true, () -> order("admin lift " + key));
         ry += 17;
      }

      this.small(g, Ink.tr("admin_note"), 214, ry + 4, 158, -9544893);
      this.button(g, Ink.tr("redraw"), 214, 208, 158, true, () -> order("admin redraw"));
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int px = (int)((mouseX - (double)this.ox) / (double)this.k);
      int py = (int)((mouseY - (double)this.oy) / (double)this.k);
      if (button == 0) {
         for (int i = this.hits.size() - 1; i >= 0; i--) {
            BookScreen.Hit h = this.hits.get(i);
            if (h.in(px, py) && h.action() != null) {
               this.focus = null;
               Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
               h.action().run();
               return true;
            }
         }

         this.focus = null;
         this.confirm = null;
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      int px = (int)((mouseX - (double)this.ox) / (double)this.k);
      int py = (int)((mouseY - (double)this.oy) / (double)this.k);

      for (Entry<String, int[]> e : this.scrollAreas.entrySet()) {
         int[] a = e.getValue();
         if (px >= a[0] - 3 && py >= a[1] && px < a[0] + a[2] + 4 && py < a[1] + a[3]) {
            int step = e.getKey().equals("chronicle") ? 1 : 12;
            this.scroll.put(e.getKey(), Math.max(0, this.scroll.getOrDefault(e.getKey(), 0) - (int)Math.signum(delta) * step));
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, delta);
   }

   public boolean charTyped(char c, int modifiers) {
      if (this.focus != null && c >= ' ' && c != 127) {
         StringBuilder sb = this.fields.get(this.focus);
         if (sb != null && sb.length() < 32) {
            sb.append(c);
         }

         return true;
      } else {
         return super.charTyped(c, modifiers);
      }
   }

   public boolean keyPressed(int key, int scan, int modifiers) {
      if (this.focus != null) {
         StringBuilder sb = this.fields.get(this.focus);
         if (key == 259 && sb != null && sb.length() > 0) {
            sb.setLength(sb.length() - 1);
            return true;
         } else if (key == 256 || key == 257 || key == 258) {
            this.focus = null;
            return true;
         } else if (key == 86 && hasControlDown() && sb != null) {
            String paste = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n]", " ");
            sb.append(paste, 0, Math.min(paste.length(), 32 - sb.length()));
            return true;
         } else {
            return true;
         }
      } else if (PrestolClient.OPEN_BOOK.matches(key, scan)) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(key, scan, modifiers);
      }
   }

   private static record Hit(int x, int y, int w, int h, Runnable action, Component tip) {
      boolean in(int px, int py) {
         return px >= this.x && py >= this.y && px < this.x + this.w && py < this.y + this.h;
      }
   }

   static enum Page {
      REALM(Items.GOLDEN_HELMET, 9316892),
      SETTLEMENTS(Items.BELL, 3107626),
      LANDS(Items.FILLED_MAP, 2776186),
      WAR(Items.IRON_SWORD, 7017492),
      ARMY(Items.SHIELD, 4084840),
      DECREES(Items.WRITABLE_BOOK, 5975928),
      CHRONICLE(Items.BOOK, 7227938),
      BANNER(Items.RED_BANNER, 11569706),
      ADMIN(Items.COMPASS, 5592405);

      final ItemStack icon;
      final int color;

      private Page(Item icon, int color) {
         this.icon = new ItemStack(icon);
         this.color = color;
      }

      Component title() {
         return Ink.tr("page." + this.name().toLowerCase(Locale.ROOT));
      }
   }
}
