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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
   private static String guideChapter = "start";
   private static boolean guideOffered;
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
            // a newcomer without a settlement or a realm first sees the guide
            if (pendingHint.isEmpty() && !guideOffered && !hasRealm() && list(state, "settlements").isEmpty()) {
               book.page = BookScreen.Page.GUIDE;
            }

            guideOffered = true;
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

   /** The banner of the player's realm as last sent by the server, for the flag editor. */
   static List<MapSnapshot.Layer> realmBanner() {
      CompoundTag r = state.getCompound("realm");
      return r.isEmpty() ? List.of() : banner(r);
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
         case GUIDE:
            this.guidePage(g);
            break;
         case TRADE:
            this.tradePage(g);
            break;
         case COURT:
            this.courtPage(g);
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
         if ((p != BookScreen.Page.ADMIN || state.getBoolean("op")) && (p != BookScreen.Page.COURT || state.contains("court"))) {
            out.add(p);
         }
      }

      return out;
   }

   private void ribbons(GuiGraphics g, boolean cloth) {
      List<BookScreen.Page> list = this.pages();
      int step = Math.min(24, 220 / Math.max(1, list.size()));

      for (int i = 0; i < list.size(); i++) {
         BookScreen.Page p = list.get(i);
         int y = 28 + i * step;
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
      // narrow buttons carry a single sign, too thin in the book's face: draw it in the plain pixel font
      ResourceLocation font = w < 30 ? Style.DEFAULT_FONT : Ink.F_SMALL;
      Component l = Ink.clip(label, font, w < 30 ? w - 2 : w - 10);
      Ink.centered(g, l, font, x + w / 2, y + 4, enabled ? -994166 : -5199720);
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
            if (sel.contains("food")) {
               int f = sel.getInt("food");
               ry = this.meter(g, Ink.tr("army_food"), f + "%", f, f >= 50 ? -11634130 : (f >= 25 ? -4684245 : -6669790), ry);
            }

            if (sel.contains("morale")) {
               int m = sel.getInt("morale");
               ry = this.meter(g, Ink.tr("army_morale"), String.valueOf(m), m, m >= 80 ? -11634130 : (m >= 40 ? -4684245 : -6669790), ry);
            }

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
            if (sel.contains("stockDays")) {
               ry = this.small(g, Ink.tr("army_food_days", sel.getInt("stockDays")), 214, ry, 158, -9544893);
            }

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
               this.row(g, 214, ry, 158, 17, on, () -> {
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
                  ry + 9,
                  -9544893
               );
               ry += 17;
            }
         }
      }
   }

   /** A labelled bar out of 100 with its value written before it; red ink when the bar is red. */
   private int meter(GuiGraphics g, Component label, String value, int percent, int rgb, int y) {
      Component v = Component.literal(value);
      Ink.text(g, label, Ink.F_BODY, 214, y, -13951730);
      Ink.text(g, v, Ink.F_BODY, 312 - Ink.width(v, Ink.F_BODY) - 4, y, rgb == -6669790 ? -7464176 : -13951730);
      Ink.bar(g, 314, y + 2, 58, 6, percent / 100.0F, rgb);
      return y + 12;
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
      List<CompoundTag> camps = list(state, "camps");
      int listH = 224 - y - (sieges > 0 ? 18 + sieges * 20 : 0) - (camps.isEmpty() ? 0 : 16 + camps.size() * 10);
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

      if (!camps.isEmpty()) {
         int cy = 224 - camps.size() * 10 - 14;
         cy = this.sub(g, Ink.tr("camps"), 28, cy);

         for (CompoundTag c : camps) {
            boolean own = c.getBoolean("mine");
            Component t = own
               ? Ink.tr("camp_own", c.getString("target"), c.getInt("x"), c.getInt("z"))
               : Ink.tr("camp_enemy", c.getString("realm"), c.getString("target"), c.getInt("x"), c.getInt("z"));
            Ink.text(g, Ink.clip(t, Ink.F_SMALL, 158), Ink.F_SMALL, 28, cy, own ? -13673956 : -7464176);
            cy += 10;
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
            if (!ruler()) {
               ry = this.small(g, Ink.tr("siege_rules"), 214, ry, 158, -9544893) + 2;
               ry = this.small(g, Ink.tr("camp_hint"), 214, ry, 158, -9544893) + 4;
               if (rx.getBoolean("peaceFromThem")) {
                  ry = this.small(g, Ink.tr("they_offer_terms", terms(rx, "theirTerms")), 214, ry, 158, -13673956);
               }
            } else {
               // a ruler needs the room for the terms: the rules of the siege are under the mouse
               this.tip(214, ry - 12, 158, 12, Ink.tr("siege_rules").copy().append(" ").append(Ink.tr("camp_hint")));
               if (rx.getBoolean("peaceFromThem")) {
                  ry = this.small(g, Ink.tr("they_offer_terms", terms(rx, "theirTerms")), 214, ry, 158, -13673956) + 1;
                  this.button(g, Ink.tr("accept_terms"), 214, ry, 90, true, () -> order("peace " + id));
                  ry += 18;
               } else if (rx.getBoolean("peaceFromUs")) {
                  ry = this.small(g, Ink.tr("we_offered_terms", terms(rx, "ourTerms")), 214, ry, 158, -9544893) + 2;
               }

               ry = this.sub(g, Ink.tr("treaty_terms"), 214, ry + 2);
               int perDay = TRIBUTE[Math.floorMod(this.treatyAmount, TRIBUTE.length)];
               int days = TRIBUTE_DAYS[Math.floorMod(this.treatyDays, TRIBUTE_DAYS.length)];
               this.stepper(g, 214, ry, Ink.tr("treaty_per_day", perDay), 78, () -> this.treatyAmount = step(TRIBUTE, this.treatyAmount, -1), () -> this.treatyAmount = step(TRIBUTE, this.treatyAmount, 1));
               this.stepper(g, 294, ry, Ink.tr("treaty_days", days), 78, () -> this.treatyDays = step(TRIBUTE_DAYS, this.treatyDays, -1), () -> this.treatyDays = step(TRIBUTE_DAYS, this.treatyDays, 1));
               this.tip(214, ry, 158, 15, Ink.tr("treaty_tribute_tip"));
               ry += 17;
               this.button(g, Ink.tr(this.treatyTheyPay ? "treaty_they_pay_btn" : "treaty_we_pay_btn"), 214, ry, 78, perDay > 0, () -> this.treatyTheyPay = !this.treatyTheyPay);
               List<CompoundTag> cede = list(rx, "cede");
               int ci = Math.floorMod(this.treatyCede, cede.size() + 1) - 1;
               CompoundTag pick = ci < 0 ? null : cede.get(ci);
               Component what = pick == null ? Ink.tr("treaty_no_cede") : Component.literal(pick.getString("name"));
               this.button(g, Component.literal("◀"), 294, ry, 14, !cede.isEmpty(), () -> this.treatyCede--);
               Ink.centered(g, Ink.clip(what, Ink.F_SMALL, 46), Ink.F_SMALL, 333, ry + 4, pick == null ? -9544893 : (pick.getBoolean("ours") ? -7464176 : -13673956));
               this.button(g, Component.literal("▶"), 358, ry, 14, !cede.isEmpty(), () -> this.treatyCede++);
               this.tip(294, ry, 78, 15, pick == null ? Ink.tr("treaty_cede_tip") : Ink.tr(pick.getBoolean("ours") ? "treaty_we_cede_tip" : "treaty_they_cede_tip", pick.getString("name")));
               String cedeKey = pick == null ? "-" : pick.getString("key");
               String payer = this.treatyTheyPay ? "them" : "us";
               this.sealButton(
                  g,
                  rx.getBoolean("peaceFromUs") ? Ink.tr("change_terms") : Ink.tr("offer_peace"),
                  214,
                  204,
                  3963438,
                  true,
                  () -> order("treaty " + id + " " + perDay + " " + days + " " + payer + " " + cedeKey)
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

   private void guidePage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.GUIDE.title(), 28, 24, 158);
      CompoundTag rules = state.getCompound("rules");
      List<GuideChapters.Chapter> chapters = GuideChapters.shown(rules);
      GuideChapters.Chapter current = chapters.get(0);

      for (GuideChapters.Chapter c : chapters) {
         if (c.id().equals(guideChapter)) {
            current = c;
         }
      }

      int off = this.beginScroll(g, "guide_list", 28, y, 158, 224 - y);
      int yy = y - off;

      for (int i = 0; i < chapters.size(); i++) {
         GuideChapters.Chapter c = chapters.get(i);
         this.row(g, 28, yy, 158, 14, c == current, () -> this.openChapter(c));
         PoseStack pose = g.pose();
         pose.pushPose();
         pose.translate(28.0F, (float)yy, 0.0F);
         pose.scale(0.75F, 0.75F, 1.0F);
         g.renderItem(c.icon(), 0, 0);
         pose.popPose();
         Component title = Component.literal((i + 1) + ". ").append(c.title());
         Ink.text(g, Ink.clip(title, Ink.F_BODY, 140), Ink.F_BODY, 44, yy + 2, c == current ? -7464176 : -13951730);
         yy += 15;
      }

      this.endScroll(g, "guide_list", yy + off - y);
      int ty = this.heading(g, current.title(), 214, 24, 158);
      int top = ty;
      int scrolled = this.beginScroll(g, "guide", 214, top, 158, 224 - top);
      ty -= scrolled;

      for (Component para : current.text(rules)) {
         ty += Ink.para(g, para, Ink.F_SMALL, 214, ty, 156, -13951730) + 5;
      }

      int index = chapters.indexOf(current);
      if (index + 1 < chapters.size()) {
         GuideChapters.Chapter next = chapters.get(index + 1);
         ty += 2;
         this.button(g, Ink.tr("guide_next", next.title()), 214, ty, 156, true, () -> this.openChapter(next));
         ty += 17;
      }

      this.endScroll(g, "guide", ty + scrolled - top);
   }

   private void openChapter(GuideChapters.Chapter c) {
      guideChapter = c.id();
      this.scroll.put("guide", 0);
   }

   // ================================================================== treasury and trade

   private static final int[] AMOUNTS = {1, 4, 8, 16, 32, 64, 128, 256, 512, 1024};
   private static final int[] PRICES = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50, 64, 81, 100, 128, 162, 200, 256, 324, 500, 729};
   private static final String[] ORDER_KINDS = {"load", "unload", "buy", "sell"};
   private static String tradeTab = "treasury";
   private String selTrader;
   private int selStop = -1;
   private String selMarket;
   private int placeIndex;
   private int orderKind;
   private int orderAmount = 5;
   private int orderPrice = 4;
   private boolean offerSell = true;
   private int offerAmount = 5;
   private int offerPrice = 4;

   private static int step(int[] values, int index, int dir) {
      return Math.max(0, Math.min(values.length - 1, index + dir));
   }

   private static String coins(long n) {
      String s = Long.toString(Math.abs(n));
      StringBuilder b = new StringBuilder();

      for (int i = 0; i < s.length(); i++) {
         if (i > 0 && (s.length() - i) % 3 == 0) {
            b.append(' ');
         }

         b.append(s.charAt(i));
      }

      return (n < 0L ? "−" : "") + b;
   }

   private static ItemStack stackOf(String id, int count) {
      ResourceLocation rl = ResourceLocation.tryParse(id);
      Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
      return new ItemStack(item == null ? Items.AIR : item, Math.max(1, Math.min(count, 64)));
   }

   private static Component itemName(String id) {
      return stackOf(id, 1).getHoverName();
   }

   private void icon(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate((float)x, (float)y, 0.0F);
      pose.scale(scale, scale, 1.0F);
      g.renderItem(stack, 0, 0);
      pose.popPose();
   }

   /** A pair of little − + buttons around a value. */
   private void stepper(GuiGraphics g, int x, int y, Component value, int w, Runnable less, Runnable more) {
      this.button(g, Component.literal("-"), x, y, 14, true, less);
      Ink.centered(g, Ink.clip(value, Ink.F_SMALL, w - 30), Ink.F_SMALL, x + w / 2, y + 4, -13951730);
      this.button(g, Component.literal("+"), x + w - 14, y, 14, true, more);
   }

   private static final int[] BOUNTIES = {0, 9, 27, 45, 81, 162, 243, 405, 729};
   private static final int[] TRIBUTE = {0, 9, 18, 27, 45, 81, 162, 324};
   private static final int[] TRIBUTE_DAYS = {3, 5, 7, 14, 30};
   private int treatyAmount;
   private int treatyDays = 2;
   private boolean treatyTheyPay = true;
   private int treatyCede;

   /** Peace terms the server sent as text, or "no terms". */
   private static Component terms(CompoundTag rx, String key) {
      if (rx.contains(key)) {
         try {
            Component c = Serializer.fromJson(rx.getString(key));
            if (c != null) {
               return c;
            }
         } catch (RuntimeException var3) {
         }
      }

      return Component.translatable("prestol.treaty.plain");
   }

   private static String capitalize(String s) {
      return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
   }

   private final int[] courtPick = new int[3];
   private int courtBounty = 2;

   /** Court and law: the offices and the lords of the realm on the left, outlaws and prices on heads on the right. */
   private void courtPage(GuiGraphics g) {
      CompoundTag court = state.getCompound("court");
      int y = this.heading(g, BookScreen.Page.COURT.title(), 28, 24, 158);
      boolean appoint = court.getBoolean("appoint");
      List<CompoundTag> lords = list(court, "lords");
      List<CompoundTag> candidates = new ArrayList<>();

      for (CompoundTag l : lords) {
         if (!l.getBoolean("ruler")) {
            candidates.add(l);
         }
      }

      y = this.sub(g, Ink.tr("court_offices"), 28, y);
      int i = 0;

      for (CompoundTag o : list(court, "offices")) {
         int index = i++;
         String id = o.getString("id");
         Component office = Component.translatable("prestol.court.office." + id);
         Ink.text(g, Component.literal(capitalize(office.getString())), Ink.F_BOLD, 28, y, -13951730);
         Component holder = o.contains("name") ? Component.literal(o.getString("name")) : Ink.tr("court_vacant");
         Ink.text(g, holder, Ink.F_BODY, 186 - Ink.width(holder, Ink.F_BODY), y, o.contains("name") ? -13673956 : -9544893);
         this.tip(28, y, 158, 11, Component.translatable("prestol.court.office." + id + ".desc"));
         y += 12;
         if (appoint && !candidates.isEmpty()) {
            int pick = Math.floorMod(this.courtPick[index], candidates.size());
            CompoundTag c = candidates.get(pick);
            this.button(g, Component.literal("◀"), 28, y, 14, candidates.size() > 1, () -> this.courtPick[index]--);
            Ink.centered(g, Ink.clip(Component.literal(c.getString("name")), Ink.F_SMALL, 38), Ink.F_SMALL, 62, y + 4, -13951730);
            this.button(g, Component.literal("▶"), 82, y, 14, candidates.size() > 1, () -> this.courtPick[index]++);
            String who = c.getUUID("id").toString();
            this.button(g, Ink.tr("court_appoint"), 98, y, 52, !c.getString("name").equals(o.getString("name")), () -> order("court appoint " + id + " " + who));
            if (o.contains("name")) {
               this.button(g, Ink.tr("court_dismiss"), 152, y, 34, true, () -> order("court dismiss " + id));
            }

            y += 18;
         } else if (o.getBoolean("me")) {
            this.button(g, Ink.tr("court_resign"), 28, y, 50, true, () -> order("court dismiss " + id));
            y += 18;
         } else {
            y += 2;
         }
      }

      y = this.sub(g, Ink.tr("court_lords"), 28, y + 2);
      int off = this.beginScroll(g, "lords", 28, y, 158, 196 - y);
      int yy = y - off;

      for (CompoundTag l : lords) {
         MutableComponent line = Component.empty();
         if (l.contains("title")) {
            try {
               Component t = Serializer.fromJson(l.getString("title"));
               if (t != null) {
                  // the realm's colour is for chat; on parchment titles are written in red ink
                  line.append(Component.literal(t.getString()).withStyle(st -> st.withColor(0x8E1B10))).append(" ");
               }
            } catch (RuntimeException var9) {
            }
         }

         line.append(Component.literal(l.getString("name")).withStyle(ChatFormatting.BLACK));
         Ink.text(g, Ink.clip(line, Ink.F_BODY, 158), Ink.F_BODY, 28, yy, -13951730);
         yy += 11;
      }

      this.endScroll(g, "lords", yy + off - y);
      this.small(g, Ink.tr("court_titles_note"), 28, 198, 158, -9544893);
      // the law
      int ry = this.heading(g, Ink.tr("court_law"), 214, 24, 158);
      boolean law = court.getBoolean("law");
      ry = this.sub(g, Ink.tr("court_outlaws"), 214, ry);
      List<CompoundTag> outlaws = list(court, "outlaws");
      int bottom = law ? 150 : 176;
      int roff = this.beginScroll(g, "outlaws", 214, ry, 158, bottom - ry);
      int oy = ry - roff;

      for (CompoundTag o : outlaws) {
         Component reason = Component.translatable("prestol.court.reason." + o.getString("reason"));
         int top = oy;
         oy += Ink.para(g, Ink.tr("court_outlaw_line", o.getString("name"), reason, o.getLong("bounty"), o.getInt("days")), Ink.F_SMALL, 214, oy, law ? 100 : 158, -7464176);
         if (law) {
            String who = o.getUUID("id").toString();
            this.button(g, Ink.tr("court_pardon"), 316, top, 56, true, () -> order("court pardon " + who));
            oy = Math.max(oy, top + 16);
         }

         oy += 4;
      }

      if (outlaws.isEmpty()) {
         oy = this.small(g, Ink.tr("court_no_outlaws"), 214, oy, 158, -9544893);
      }

      List<CompoundTag> wanted = list(court, "wanted");
      if (!wanted.isEmpty()) {
         oy = this.sub(g, Ink.tr("court_wanted"), 214, oy + 4);

         for (CompoundTag w : wanted) {
            oy = this.small(g, Ink.tr("court_wanted_line", w.getString("name"), w.getLong("bounty"), w.getString("realm")), 214, oy, 158, -4684245);
         }
      }

      this.endScroll(g, "outlaws", oy + roff - ry);
      if (law) {
         String name = this.field(g, "outlaw", 214, 154, 158, Ink.tr("court_name"));
         this.stepper(g, 214, 170, Ink.tr("court_bounty", BOUNTIES[this.courtBounty]), 62, () -> this.courtBounty = step(BOUNTIES, this.courtBounty, -1), () -> this.courtBounty = step(BOUNTIES, this.courtBounty, 1));
         int bounty = BOUNTIES[this.courtBounty];
         this.dangerous(g, "outlaw", Ink.tr("court_outlaw"), 280, 170, 92, !name.isEmpty() && !name.contains(" "), () -> {
            order("court outlaw " + name + " " + bounty);
            this.fields.remove("outlaw");
         });
      }

      this.small(g, Ink.tr("court_law_note", court.getInt("days")), 214, law ? 190 : 180, 158, -9544893);
   }

   private void tradePage(GuiGraphics g) {
      int y = this.heading(g, BookScreen.Page.TRADE.title(), 28, 24, 158);
      if (!state.getBoolean("economy")) {
         this.line(g, Ink.tr("economy_off"), 28, y, 158, -9544893);
      } else if (!state.contains("treasury")) {
         this.line(g, Ink.tr("no_realm_no_trade"), 28, y, 158, -9544893);
      } else {
         String[] tabs = state.contains("trade") ? new String[]{"treasury", "traders", "markets"} : new String[]{"treasury"};
         int tx = 28;

         for (String tab : tabs) {
            boolean on = tab.equals(tradeTab);
            int w = 158 / tabs.length - 2;
            this.button(g, Ink.tr("tab_" + tab).withStyle(on ? ChatFormatting.UNDERLINE : ChatFormatting.RESET), tx, y - 4, w, !on, () -> tradeTab = tab);
            tx += w + 3;
         }

         y += 16;
         if (tradeTab.equals("traders") && state.contains("trade")) {
            this.tradersTab(g, y);
         } else if (tradeTab.equals("markets") && state.contains("trade")) {
            this.marketsTab(g, y);
         } else {
            this.treasuryTab(g, y);
         }
      }
   }

   private void treasuryTab(GuiGraphics g, int y) {
      CompoundTag t = state.getCompound("treasury");
      boolean ruler = t.getBoolean("ruler");
      Ink.text(g, Ink.tr("treasury_balance"), Ink.F_BODY, 28, y, -13951730);
      Component bal = Ink.tr("coins", coins(t.getLong("coins")));
      Ink.text(g, bal, Ink.F_BOLD, 186 - Ink.width(bal, Ink.F_BOLD), y, -7464176);
      y += 13;
      long income = t.getLong("income");
      long pay = t.getLong("pay");
      y = this.small(g, Ink.tr("treasury_income", coins(income)), 28, y, 158, -13673956);
      y = this.small(g, Ink.tr("treasury_pay", coins(pay), t.getInt("soldiers")), 28, y, 158, -7464176);
      y = this.small(g, Ink.tr("treasury_net", (income - pay >= 0L ? "+" : "") + coins(income - pay)), 28, y, 158, -13951730);
      if (t.getInt("unpaid") > 0) {
         y = this.small(g, Ink.tr("treasury_unpaid", t.getInt("unpaid")), 28, y + 1, 158, -7464176);
      }

      List<CompoundTag> tributes = list(t, "tributes");

      for (int i = 0; i < Math.min(2, tributes.size()); i++) {
         CompoundTag tr = tributes.get(i);
         boolean pays = tr.getBoolean("pays");
         Component line = Ink.tr(pays ? "treasury_tribute_out" : "treasury_tribute_in", tr.getString("realm"), coins(tr.getLong("perDay")), tr.getInt("days"));
         y = this.small(g, Ink.clip(line, Ink.F_SMALL, 158), 28, y, 158, pays ? -7464176 : -13673956);
      }

      if (tributes.size() > 2) {
         y = this.small(g, Ink.tr("treasury_tribute_more", tributes.size() - 2), 28, y, 158, -9544893);
      }

      y = this.sub(g, Ink.tr("treasury_tax"), 28, y + 4);
      String tax = t.getString("tax");

      for (CompoundTag r : list(t, "rates")) {
         String id = r.getString("id");
         boolean on = id.equals(tax);
         this.row(g, 28, y, 158, 12, on, () -> {
            if (ruler && !on) {
               order("treasury tax " + id);
            }
         });
         Ink.text(g, Component.literal(on ? "✔ " : "").append(Component.translatable("prestol.tax." + id)), Ink.F_BODY, 30, y + 1, on ? -7464176 : -13951730);
         int h = r.getInt("happiness");
         Component right = Component.literal("+" + coins(r.getLong("income")) + "  ").append(Ink.tr("happiness_short", (h > 0 ? "+" : "") + h + "%"));
         Ink.text(g, right, Ink.F_SMALL, 186 - Ink.width(right, Ink.F_SMALL), y + 2, h < 0 ? -7464176 : -9544893);
         y += 13;
      }

      y = this.sub(g, Ink.tr("treasury_duty"), 28, y + 3);
      int duty = t.getInt("duty");
      int bx = 28;

      for (int d : new int[]{0, 5, 10, 25}) {
         this.button(g, Component.literal((d == duty ? "✔" : "") + d + "%"), bx, y, 37, ruler && d != duty, () -> order("treasury duty " + d));
         bx += 40;
      }

      this.small(g, Ink.tr("treasury_duty_note"), 28, y + 18, 158, -9544893);
      int ry = 30;
      ry = this.sub(g, Ink.tr("treasury_gold"), 214, ry);
      this.button(g, Ink.tr("treasury_deposit"), 214, ry, 158, true, () -> order("treasury deposit"));
      ry += 18;
      int wx = 214;
      String[] labels = {"withdraw_ingot", "withdraw_block", "withdraw_blocks"};
      int[] amounts = {9, 81, 729};

      for (int i = 0; i < 3; i++) {
         int n = amounts[i];
         this.button(g, Ink.tr(labels[i]), wx, ry, 51, ruler && t.getLong("coins") >= n, () -> order("treasury withdraw " + n));
         wx += 53;
      }

      ry += 17;
      ry = this.small(g, Ink.tr(ruler ? "treasury_gold_note" : "treasury_gold_note_member"), 214, ry, 158, -9544893) + 4;
      ry = this.sub(g, Ink.tr("treasury_ledger"), 214, ry);
      int off = this.beginScroll(g, "ledger", 214, ry, 158, 224 - ry);
      int ly = ry - off;
      List<CompoundTag> ledger = list(t, "ledger");

      for (CompoundTag e : ledger) {
         List<Object> args = list(e);
         for (int i = 0; i < args.size(); i++) {
            if (args.get(i) instanceof String s && s.startsWith("item.") || args.get(i) instanceof String s2 && s2.startsWith("block.")) {
               args.set(i, Component.translatable((String)args.get(i)));
            }
         }

         Component what = Component.translatable("prestol.ledger." + e.getString("key"), args.toArray());
         long a = e.getLong("amount");
         Component sum = Component.literal((a >= 0L ? "+" : "") + coins(a));
         Ink.text(g, sum, Ink.F_SMALL, 372 - Ink.width(sum, Ink.F_SMALL), ly, a >= 0L ? -13673956 : -7464176);
         int h = Ink.para(g, Ink.tr("day_short", e.getLong("day")).append(" · ").append(what), Ink.F_SMALL, 214, ly, 120, -13951730);
         ly += Math.max(9, h) + 2;
      }

      if (ledger.isEmpty()) {
         ly = this.small(g, Ink.tr("treasury_ledger_empty"), 214, ly, 158, -9544893);
      }

      this.endScroll(g, "ledger", ly + off - ry);
   }

   private CompoundTag selectedTrader(List<CompoundTag> traders) {
      CompoundTag sel = null;

      for (CompoundTag t : traders) {
         if (t.getString("id").equals(this.selTrader)) {
            sel = t;
         }
      }

      if (sel == null && !traders.isEmpty()) {
         sel = traders.get(0);
         this.selTrader = sel.getString("id");
         this.selStop = -1;
      }

      return sel;
   }

   private void tradersTab(GuiGraphics g, int y) {
      CompoundTag trade = state.getCompound("trade");
      boolean ruler = trade.getBoolean("ruler");
      List<CompoundTag> traders = list(trade, "traders");
      CompoundTag sel = this.selectedTrader(traders);
      int listBottom = ruler ? 178 : 224;
      int off = this.beginScroll(g, "traders", 28, y, 158, listBottom - y);
      int yy = y - off;

      for (CompoundTag t : traders) {
         String id = t.getString("id");
         this.row(g, 28, yy, 158, 22, id.equals(this.selTrader), () -> {
            this.selTrader = id;
            this.selStop = -1;
            this.scroll.put("route", 0);
         });
         this.icon(g, new ItemStack(t.getString("kind").equals("ship") ? Items.DARK_OAK_CHEST_BOAT : Items.LEAD), 28, yy + 2, 0.75F);
         Ink.text(g, Ink.clip(Component.literal(t.getString("name")), Ink.F_BODY, 110), Ink.F_BODY, 42, yy + 1, -13951730);
         Component cargo = Component.literal(t.getInt("used") + "/" + t.getInt("slots"));
         Ink.text(g, cargo, Ink.F_SMALL, 186 - Ink.width(cargo, Ink.F_SMALL), yy + 2, -9544893);
         Component status = this.traderStatus(t);
         Ink.text(g, Ink.clip(status, Ink.F_SMALL, 140), Ink.F_SMALL, 42, yy + 12, t.getBoolean("running") ? -13673956 : -9544893);
         yy += 23;
      }

      if (traders.isEmpty()) {
         yy = this.small(g, Ink.tr("traders_none"), 28, yy, 158, -9544893);
      }

      this.endScroll(g, "traders", yy + off - y);
      if (ruler) {
         int cy = 182;
         String name = this.field(g, "trader_name", 28, cy, 158, Ink.tr("trader_name"));
         boolean okName = name.length() >= 2 && name.length() <= 24 && traders.size() < trade.getInt("max");
         this.button(g, Ink.tr("new_caravan", coins(trade.getInt("caravanCost"))), 28, cy + 15, 78, okName, () -> {
            order("trade new caravan " + name);
            this.fields.remove("trader_name");
         });
         this.button(g, Ink.tr("new_ship", coins(trade.getInt("shipCost"))), 108, cy + 15, 78, okName, () -> {
            order("trade new ship " + name);
            this.fields.remove("trader_name");
         });
         this.tip(28, cy + 15, 158, 15, Ink.tr("new_trader_tip"));
      }

      if (sel == null) {
         this.line(g, Ink.tr("traders_intro"), 214, 30, 158, -9544893);
      } else {
         this.traderDetail(g, trade, sel, ruler);
      }
   }

   private Component traderStatus(CompoundTag t) {
      List<Object> args = new ArrayList<>();

      for (Tag a : t.getList("statusArgs", 8)) {
         args.add(a.getAsString());
      }

      return Component.translatable("prestol.trade.status." + t.getString("status"), args.toArray());
   }

   private void traderDetail(GuiGraphics g, CompoundTag trade, CompoundTag t, boolean ruler) {
      String id = t.getString("id");
      boolean ship = t.getString("kind").equals("ship");
      int ry = this.heading(g, Component.literal(t.getString("name")), 214, 24, 158);
      ry = this.small(g, Component.translatable("prestol.trade.kind." + t.getString("kind")).append(" · ").append(this.traderStatus(t)), 214, ry - 3, 158, -9544893);
      ry = this.stat(g, Ink.tr("trader_cargo"), t.getInt("used"), t.getInt("slots"), ry + 1);
      int ix = 214;

      for (CompoundTag c : list(t, "cargo")) {
         if (ix > 356) {
            break;
         }

         ItemStack s = stackOf(c.getString("item"), c.getInt("n"));
         this.icon(g, s, ix, ry, 0.75F);
         Component n = Component.literal(String.valueOf(c.getInt("n")));
         Ink.text(g, n, Ink.F_SMALL, ix + 13 - Ink.width(n, Ink.F_SMALL), ry + 7, -13951730);
         this.tip(ix, ry, 14, 14, s.getHoverName().copy().append(" ×" + c.getInt("n")));
         ix += 16;
      }

      if (!list(t, "cargo").isEmpty()) {
         ry += 15;
      }

      if (ruler) {
         boolean running = t.getBoolean("running");
         this.button(g, Ink.tr(running ? "trader_halt" : "trader_go"), 214, ry, 50, true, () -> order("trade run " + id + (running ? " off" : " on")));
         if (!ship) {
            int a = t.getInt("animals");
            this.stepper(g, 266, ry, Ink.tr("trader_mules", a), 52, () -> order("trade animals " + id + " " + (a - 1)), () -> order("trade animals " + id + " " + (a + 1)));
            this.tip(266, ry, 52, 15, Ink.tr("trader_mules_tip", trade.getInt("animalCost")));
         }

         int gd = t.getInt("guards");
         this.stepper(g, 320, ry, Ink.tr("trader_guards", gd), 52, () -> order("trade guards " + id + " " + (gd - 1)), () -> order("trade guards " + id + " " + (gd + 1)));
         this.tip(320, ry, 52, 15, Ink.tr("trader_guards_tip", trade.getInt("escortCost")));
         ry += 18;
      }

      List<CompoundTag> route = list(t, "route");
      int top = ry;
      int bottom = ruler ? 190 : 224;
      int off = this.beginScroll(g, "route", 214, top, 158, bottom - top);
      int yy = top - off;
      yy = this.sub(g, Ink.tr("trader_route"), 214, yy);

      for (int i = 0; i < route.size(); i++) {
         CompoundTag s = route.get(i);
         int index = i;
         boolean on = i == this.selStop;
         this.row(g, 214, yy, 158, 12, on, () -> this.selStop = on ? -1 : index);
         boolean next = i == t.getInt("target") && t.getBoolean("running");
         MutableComponent label = Component.literal((next ? "➜ " : "") + (i + 1) + ". " + s.getString("name"));
         if (!s.getBoolean("own") && !s.getString("realm").isEmpty()) {
            label.append(Component.literal(" (" + s.getString("realm") + ")").withStyle(ChatFormatting.ITALIC));
         }

         Ink.text(g, Ink.clip(label, Ink.F_BODY, 112), Ink.F_BODY, 216, yy + 1, on ? -7464176 : -13951730);
         Component meta = Component.literal(s.getList("orders", 10).size() + "⚖" + (s.contains("dist") ? " " + s.getInt("dist") : ""));
         Ink.text(g, meta, Ink.F_SMALL, 372 - Ink.width(meta, Ink.F_SMALL), yy + 2, -9544893);
         yy += 13;
         if (on) {
            yy = this.stopEditor(g, trade, t, s, index, ruler, yy);
         }
      }

      List<CompoundTag> journal = list(t, "journal");
      if (!journal.isEmpty()) {
         yy = this.sub(g, Ink.tr("trader_journal"), 214, yy + 3);
         int shown = 0;

         for (Tag j : t.getList("journal", 8)) {
            if (shown++ >= 6) {
               break;
            }

            Component c;
            try {
               c = Serializer.fromJson(j.getAsString());
            } catch (RuntimeException var17) {
               c = null;
            }

            if (c != null) {
               yy += Ink.para(g, Component.literal(c.getString()), Ink.F_SMALL, 214, yy, 156, -9544893) + 2;
            }
         }
      }

      this.endScroll(g, "route", yy + off - top);
      if (ruler) {
         List<CompoundTag> places = list(trade, "places");
         if (!places.isEmpty()) {
            this.placeIndex = Math.floorMod(this.placeIndex, places.size());
            CompoundTag p = places.get(this.placeIndex);
            MutableComponent name = Component.literal(p.getString("name"));
            if (!p.getBoolean("own")) {
               name.append(" (" + p.getString("realm") + (p.getBoolean("war") ? ", ⚔" : "") + ")");
            }

            this.button(g, Component.literal("◀"), 214, 194, 14, true, () -> this.placeIndex--);
            Ink.centered(g, Ink.clip(name, Ink.F_SMALL, 96), Ink.F_SMALL, 280, 198, p.getBoolean("war") ? -7464176 : -13951730);
            this.button(g, Component.literal("▶"), 330, 194, 14, true, () -> this.placeIndex++);
            String key = p.getString("key");
            this.button(g, Component.literal("+"), 346, 194, 26, route.size() < 8, () -> order("trade stop " + id + " add " + key));
            this.tip(214, 194, 158, 15, this.placeTip(p));
            this.small(g, Ink.tr("trader_add_stop"), 214, 211, 158, -9544893);
         }
      }
   }

   private Component placeTip(CompoundTag p) {
      MutableComponent c = Component.literal(p.getString("name"));
      if (!p.getBoolean("own")) {
         c.append("\n").append(Ink.tr("place_foreign", p.getString("realm"), p.getInt("duty")));
      }

      for (CompoundTag o : list(p, "offers")) {
         c.append("\n").append(Ink.tr(o.getBoolean("sell") ? "offer_line_sell" : "offer_line_buy", itemName(o.getString("item")), o.getInt("price"), o.getInt("stock")));
      }

      return c;
   }

   private int stopEditor(GuiGraphics g, CompoundTag trade, CompoundTag t, CompoundTag s, int index, boolean ruler, int yy) {
      String id = t.getString("id");
      List<CompoundTag> orders = list(s, "orders");

      for (int oi = 0; oi < orders.size(); oi++) {
         CompoundTag o = orders.get(oi);
         int orderIndex = oi;
         String kind = o.getString("kind");
         this.icon(g, stackOf(o.getString("item"), 1), 220, yy, 0.6F);
         Component text = Ink.tr(
            "order_" + kind, itemName(o.getString("item")), o.getInt("amount"), o.getInt("price")
         );
         Ink.text(g, Ink.clip(text, Ink.F_SMALL, 128), Ink.F_SMALL, 232, yy + 2, -13951730);
         if (ruler) {
            this.button(g, Component.literal("✕"), 360, yy - 1, 12, true, () -> order("trade order " + id + " " + index + " del " + orderIndex));
         }

         yy += 11;
      }

      if (orders.isEmpty()) {
         yy = this.small(g, Ink.tr("stop_no_orders"), 220, yy, 150, -9544893);
      }

      if (ruler) {
         String kind = ORDER_KINDS[this.orderKind];
         this.button(g, Ink.tr("kind_" + kind), 214, yy + 1, 48, true, () -> this.orderKind = (this.orderKind + 1) % ORDER_KINDS.length);
         this.tip(214, yy + 1, 48, 15, Ink.tr("kind_" + kind + "_tip"));
         this.stepper(g, 264, yy + 1, Component.literal("×" + AMOUNTS[this.orderAmount]), 52, () -> this.orderAmount = step(AMOUNTS, this.orderAmount, -1), () -> this.orderAmount = step(AMOUNTS, this.orderAmount, 1));
         boolean priced = kind.equals("buy") || kind.equals("sell");
         if (priced) {
            this.stepper(g, 318, yy + 1, Component.literal((kind.equals("buy") ? "≤" : "≥") + PRICES[this.orderPrice]), 54, () -> this.orderPrice = step(PRICES, this.orderPrice, -1), () -> this.orderPrice = step(PRICES, this.orderPrice, 1));
            this.tip(318, yy + 1, 54, 15, Ink.tr("order_price_tip"));
         }

         yy += 17;
         this.button(g, Ink.tr("order_from_hand"), 214, yy, 158, true, () -> order(
            "trade order " + id + " " + index + " add " + kind + " " + AMOUNTS[this.orderAmount] + " " + (priced ? PRICES[this.orderPrice] : 0)
         ));
         yy += 17;
         this.button(g, Ink.tr("stop_up"), 214, yy, 24, index > 0, () -> {
            order("trade stop " + id + " up " + index);
            this.selStop = index - 1;
         });
         this.button(g, Ink.tr("stop_down"), 240, yy, 24, true, () -> {
            order("trade stop " + id + " down " + index);
            this.selStop = index + 1;
         });
         this.button(g, Ink.tr("wp_add", s.getInt("wps")), 266, yy, 66, true, () -> order("trade wp " + id + " " + index + " add"));
         this.tip(266, yy, 66, 15, Ink.tr("wp_tip"));
         this.button(g, Ink.tr("wp_clear"), 334, yy, 18, s.getInt("wps") > 0, () -> order("trade wp " + id + " " + index + " clear"));
         this.button(g, Component.literal("✕"), 354, yy, 18, true, () -> {
            order("trade stop " + id + " del " + index);
            this.selStop = -1;
         });
         yy += 19;
      }

      return yy;
   }

   private void marketsTab(GuiGraphics g, int y) {
      CompoundTag trade = state.getCompound("trade");
      List<CompoundTag> mine = list(trade, "markets");
      List<CompoundTag> foreign = new ArrayList<>();

      for (CompoundTag p : list(trade, "places")) {
         if (!p.getBoolean("own") && p.contains("offers")) {
            foreign.add(p);
         }
      }

      List<CompoundTag> all = new ArrayList<>(mine);
      all.addAll(foreign);
      CompoundTag sel = null;

      for (CompoundTag m : all) {
         if (m.getString("key").equals(this.selMarket)) {
            sel = m;
         }
      }

      if (sel == null && !all.isEmpty()) {
         sel = all.get(0);
         this.selMarket = sel.getString("key");
      }

      int off = this.beginScroll(g, "markets", 28, y, 158, 224 - y);
      int yy = y - off;
      yy = this.sub(g, Ink.tr("markets_mine"), 28, yy);

      for (CompoundTag m : mine) {
         String key = m.getString("key");
         this.row(g, 28, yy, 158, 12, key.equals(this.selMarket), () -> this.selMarket = key);
         Ink.text(g, Ink.clip(Component.literal(m.getString("name")), Ink.F_BODY, 130), Ink.F_BODY, 30, yy + 1, -13951730);
         Component n = Component.literal(String.valueOf(m.getList("offers", 10).size()));
         Ink.text(g, n, Ink.F_SMALL, 186 - Ink.width(n, Ink.F_SMALL), yy + 2, -9544893);
         yy += 13;
      }

      if (mine.isEmpty()) {
         yy = this.small(g, Ink.tr("markets_none"), 28, yy, 158, -9544893);
      }

      yy = this.sub(g, Ink.tr("markets_foreign"), 28, yy + 4);

      for (CompoundTag m : foreign) {
         String key = m.getString("key");
         this.row(g, 28, yy, 158, 12, key.equals(this.selMarket), () -> this.selMarket = key);
         Component name = Component.literal(m.getString("name") + " (" + m.getString("realm") + ")");
         Ink.text(g, Ink.clip(name, Ink.F_BODY, 150), Ink.F_BODY, 30, yy + 1, m.getBoolean("war") ? -7464176 : -13951730);
         yy += 13;
      }

      if (foreign.isEmpty()) {
         yy = this.small(g, Ink.tr("markets_foreign_none"), 28, yy, 158, -9544893);
      }

      this.endScroll(g, "markets", yy + off - y);
      if (sel == null) {
         this.line(g, Ink.tr("markets_intro"), 214, 30, 158, -9544893);
      } else {
         boolean editable = mine.contains(sel);
         String key = sel.getString("key");
         int ry = this.heading(g, Component.literal(sel.getString("name")), 214, 24, 158);
         if (!editable) {
            ry = this.small(g, Ink.tr("place_foreign", sel.getString("realm"), sel.getInt("duty")), 214, ry - 3, 158, -9544893) + 2;
         }

         List<CompoundTag> offers = list(sel, "offers");
         int top = ry;
         int bottom = editable ? 172 : 224;
         int mo = this.beginScroll(g, "offers", 214, top, 158, bottom - top);
         int oy = top - mo;

         for (int i = 0; i < offers.size(); i++) {
            CompoundTag o = offers.get(i);
            int index = i;
            boolean sell = o.getBoolean("sell");
            this.icon(g, stackOf(o.getString("item"), 1), 214, oy, 0.75F);
            Component a = Ink.tr(sell ? "offer_sell" : "offer_buy", itemName(o.getString("item")), o.getInt("price"));
            Ink.text(g, Ink.clip(a, Ink.F_SMALL, 130), Ink.F_SMALL, 228, oy, sell ? -13673956 : -7464176);
            Component b = Ink.tr(sell ? "offer_keep" : "offer_upto", o.getInt("amount"), o.getInt("stock"));
            Ink.text(g, Ink.clip(b, Ink.F_SMALL, 130), Ink.F_SMALL, 228, oy + 9, -9544893);
            if (editable) {
               this.button(g, Component.literal("✕"), 360, oy + 1, 12, true, () -> order("trade market " + key + " del " + index));
            }

            oy += 20;
         }

         if (offers.isEmpty()) {
            oy = this.small(g, Ink.tr("offers_none"), 214, oy, 158, -9544893);
         }

         this.endScroll(g, "offers", oy + mo - top);
         if (editable) {
            int ey = 176;
            this.button(g, Ink.tr(this.offerSell ? "offer_kind_sell" : "offer_kind_buy"), 214, ey, 52, true, () -> this.offerSell = !this.offerSell);
            this.stepper(g, 268, ey, Ink.tr("offer_price", PRICES[this.offerPrice]), 52, () -> this.offerPrice = step(PRICES, this.offerPrice, -1), () -> this.offerPrice = step(PRICES, this.offerPrice, 1));
            this.stepper(g, 322, ey, Component.literal((this.offerSell ? "⌂" : "≤") + AMOUNTS[this.offerAmount]), 50, () -> this.offerAmount = step(AMOUNTS, this.offerAmount, -1), () -> this.offerAmount = step(AMOUNTS, this.offerAmount, 1));
            this.tip(322, ey, 50, 15, Ink.tr(this.offerSell ? "offer_keep_tip" : "offer_upto_tip"));
            this.button(g, Ink.tr("offer_from_hand"), 214, ey + 17, 158, true, () -> order(
               "trade market " + key + " " + (this.offerSell ? "sell " : "buy ") + PRICES[this.offerPrice] + " " + AMOUNTS[this.offerAmount]
            ));
            this.small(g, Ink.tr("market_note"), 214, ey + 34, 158, -9544893);
         }
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

            if (state.getBoolean("paintings")) {
               this.button(g, Ink.tr("draw_flag"), 28, y + 28, 158, true, () -> FlagEditorScreen.open(this, banner(r)));
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
      COURT(Items.NAME_TAG, 9118290),
      TRADE(Items.GOLD_INGOT, 12093995),
      CHRONICLE(Items.BOOK, 7227938),
      BANNER(Items.RED_BANNER, 11569706),
      GUIDE(Items.KNOWLEDGE_BOOK, 3043214),
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
