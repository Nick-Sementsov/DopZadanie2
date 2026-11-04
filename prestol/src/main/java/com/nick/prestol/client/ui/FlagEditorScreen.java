package com.nick.prestol.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nick.prestol.flag.BannerImages;
import com.nick.prestol.flag.FlagActions;
import com.nick.prestol.map.MapSnapshot;
import com.nick.prestol.net.Net;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * The realm's flag editor: a 48 x 32 pixel cloth (three by two blocks on a Betsy Ross mast) painted with a brush,
 * bucket and eyedropper in the sixteen dye colours or any colour, or filled from a picture by link or by dropping
 * a file onto the window. Raising the flag sends the pixels to the server, which turns them into the realm's flag.
 */
public class FlagEditorScreen extends Screen {
   private static final int W = FlagActions.DRAW_W;
   private static final int H = FlagActions.DRAW_H;
   private static final int CELL = 3;
   private static final int CX = 35;
   private static final int CY = 50;
   private static final long MAX_BYTES = 5L * 1024L * 1024L;
   private static final int BANNER_Y = 143;
   private static final int URL_Y = 160;
   private static final int LOAD_Y = 177;
   private static final int STATUS_Y = 195;
   private static final int RAISE_Y = 214;
   private static final int INK = -13951730;
   private static final int RED_INK = -7464176;
   private static final int FADED = -9544893;
   /** The cloth survives closing the editor, so a ruler can come back to his drawing. */
   private static int[] kept;
   private final Screen parent;
   private final int[] px;
   private final Deque<int[]> undo = new ArrayDeque<>();
   private final int[] custom = new int[]{200, 40, 40};
   private int color = 0xB02E26;
   private FlagEditorScreen.Tool tool = FlagEditorScreen.Tool.BRUSH;
   private int brush = 1;
   private Component status = Component.empty();
   private int statusColor = FADED;
   private boolean busy;
   private boolean stroke;
   private EditBox url;
   private float k = 1.0F;
   private int ox;
   private int oy;
   private int mx;
   private int my;

   private FlagEditorScreen(Screen parent, int[] start) {
      super(Ink.tr("flag_editor"));
      this.parent = parent;
      this.px = start;
   }

   /** Opens the editor on the last drawing, or on the realm's banner turned into pixels. */
   public static void open(Screen parent, List<MapSnapshot.Layer> banner) {
      int[] start = kept != null ? kept.clone() : fromBanner(banner);
      Minecraft.getInstance().setScreen(new FlagEditorScreen(parent, start));
   }

   private static int[] fromBanner(List<MapSnapshot.Layer> banner) {
      DyeColor base = DyeColor.WHITE;
      ListTag layers = new ListTag();

      for (int i = 0; i < banner.size(); i++) {
         MapSnapshot.Layer l = banner.get(i);
         if (i == 0 && "b".equals(l.pattern())) {
            base = DyeColor.byId(l.color());
         } else {
            CompoundTag t = new CompoundTag();
            t.putString("Pattern", l.pattern());
            t.putInt("Color", l.color());
            layers.add(t);
         }
      }

      return BannerImages.render(base, layers, W, H);
   }

   @Override
   protected void init() {
      this.k = Math.min(1.0F, Math.min((float)(this.width - 12) / 440.0F, (float)(this.height - 8) / 250.0F));
      this.ox = (int)((this.width - 440.0F * this.k) / 2.0F);
      this.oy = (int)((this.height - 250.0F * this.k) / 2.0F);
      String typed = this.url == null ? "" : this.url.getValue();
      // the link field is a plain widget in screen space, placed over the right page
      this.url = new EditBox(this.font, this.sx(214), this.sy(URL_Y), (int)(158 * this.k), 14, Ink.tr("flag_url"));
      this.url.setMaxLength(2048);
      this.url.setHint(Ink.tr("flag_url_hint"));
      this.url.setValue(typed);
      this.addRenderableWidget(this.url);
   }

   private int sx(int x) {
      return this.ox + (int)(x * this.k);
   }

   private int sy(int y) {
      return this.oy + (int)(y * this.k);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.renderBackground(g);
      this.mx = (int)((mouseX - this.ox) / this.k);
      this.my = (int)((mouseY - this.oy) / this.k);
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate((float)this.ox, (float)this.oy, 0.0F);
      pose.scale(this.k, this.k, 1.0F);
      Ink.book(g, 0, 0);
      this.leftPage(g);
      this.rightPage(g);
      pose.popPose();
      super.render(g, mouseX, mouseY, partial);
   }

   private void leftPage(GuiGraphics g) {
      Ink.text(g, Ink.tr("flag_editor"), Ink.F_TITLE, 28, 24, RED_INK);
      Ink.divider(g, 28, 40, 158);
      g.fill(CX - 2, CY - 2, CX + W * CELL + 2, CY + H * CELL + 2, 0xFF3B2A1A);

      for (int y = 0; y < H; y++) {
         for (int x = 0; x < W; x++) {
            int sx = CX + x * CELL;
            int sy = CY + y * CELL;
            g.fill(sx, sy, sx + CELL, sy + CELL, 0xFF000000 | this.px[y * W + x]);
         }
      }

      int[] cell = this.cellAt(this.mx, this.my);
      if (cell != null && this.tool != FlagEditorScreen.Tool.FILL) {
         int size = this.tool == FlagEditorScreen.Tool.BRUSH ? this.brush : 1;
         g.renderOutline(CX + cell[0] * CELL, CY + cell[1] * CELL, size * CELL, size * CELL, 0xFFFFFFFF);
      }

      int y = CY + H * CELL + 8;
      int x = 28;

      for (FlagEditorScreen.Tool t : FlagEditorScreen.Tool.values()) {
         this.toggle(g, Ink.tr("flag_tool_" + t.id), x, y, 50, this.tool == t);
         x += 53;
      }

      y += 18;
      this.toggle(g, Ink.tr("flag_brush", this.brush), 28, y, 76, false);
      this.toggle(g, Ink.tr("flag_undo"), 110, y, 76, false);
      y += 18;
      this.toggle(g, Ink.tr("flag_clear"), 28, y, 76, false);
      this.toggle(g, Ink.tr("flag_mirror"), 110, y, 76, false);
      Ink.para(g, Ink.tr("flag_drop_hint"), Ink.F_SMALL, 28, y + 20, 158, FADED);
   }

   private void rightPage(GuiGraphics g) {
      Ink.text(g, Ink.tr("flag_colors"), Ink.F_BOLD, 214, 26, RED_INK);
      DyeColor[] dyes = DyeColor.values();

      for (int i = 0; i < dyes.length; i++) {
         int cx = 214 + i % 8 * 19;
         int cy = 40 + i / 8 * 13;
         int rgb = dyeRgb(dyes[i]);
         g.fill(cx, cy, cx + 16, cy + 10, 0xFF000000 | rgb);
         g.renderOutline(cx - 1, cy - 1, 18, 12, rgb == this.color ? RED_INK : -10862298);
      }

      int y = 70;
      Ink.text(g, Ink.tr("flag_custom"), Ink.F_BOLD, 214, y, RED_INK);
      int mine = this.custom[0] << 16 | this.custom[1] << 8 | this.custom[2];
      g.fill(330, y - 1, 372, y + 9, 0xFF000000 | mine);
      g.renderOutline(329, y - 2, 44, 12, mine == this.color ? RED_INK : -10862298);
      y += 14;
      String[] names = new String[]{"R", "G", "B"};
      int[] tints = new int[]{0xFFB02E26, 0xFF5E7C16, 0xFF3C44AA};

      for (int c = 0; c < 3; c++) {
         Ink.text(g, Component.literal(names[c]), Ink.F_SMALL, 214, y + 1, INK);
         Ink.bar(g, 226, y + 1, 120, 7, this.custom[c] / 255.0F, tints[c]);
         Ink.text(g, Component.literal(String.valueOf(this.custom[c])), Ink.F_SMALL, 350, y + 1, INK);
         y += 11;
      }

      Ink.text(g, Component.literal(String.format("#%06X", mine)), Ink.F_SMALL, 214, y + 1, FADED);
      y += 14;
      Ink.text(g, Ink.tr("flag_import"), Ink.F_BOLD, 214, y, RED_INK);
      y += 12;
      this.toggle(g, Ink.tr("flag_from_banner"), 214, BANNER_Y, 158, false);
      // the link field (a widget) sits between these two
      this.toggle(g, this.busy ? Ink.tr("flag_loading") : Ink.tr("flag_load_url"), 214, LOAD_Y, 158, false);
      Ink.para(g, this.status, Ink.F_SMALL, 214, STATUS_Y, 158, this.statusColor);
      boolean hover = in(this.mx, this.my, 214, RAISE_Y, 106, 18);
      Ink.seal(g, 214, RAISE_Y, 18, hover ? 13650986 : 11569706);
      Ink.text(g, Ink.tr("flag_raise"), Ink.F_BOLD, 235, RAISE_Y + 5, hover ? RED_INK : INK);
      Ink.text(g, Ink.tr("flag_back"), Ink.F_SMALL, 330, RAISE_Y + 6, in(this.mx, this.my, 324, RAISE_Y, 48, 18) ? RED_INK : FADED);
   }

   private void toggle(GuiGraphics g, Component label, int x, int y, int w, boolean on) {
      boolean hover = this.mx >= x && this.my >= y && this.mx < x + w && this.my < y + 15;
      Ink.strap(g, x, y, w, 15, on ? 2 : (hover ? 1 : 0));
      Ink.centered(g, Ink.clip(label, Ink.F_SMALL, w - 6), Ink.F_SMALL, x + w / 2, y + 4, on ? RED_INK : INK);
   }

   private static int dyeRgb(DyeColor d) {
      float[] c = d.getTextureDiffuseColors();
      return (int)(c[0] * 255.0F) << 16 | (int)(c[1] * 255.0F) << 8 | (int)(c[2] * 255.0F);
   }

   private int[] cellAt(int x, int y) {
      int cx = Math.floorDiv(x - CX, CELL);
      int cy = Math.floorDiv(y - CY, CELL);
      return cx >= 0 && cy >= 0 && cx < W && cy < H ? new int[]{cx, cy} : null;
   }

   private static boolean in(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      }

      this.setFocused(null);
      this.url.setFocused(false);
      int x = (int)((mouseX - this.ox) / this.k);
      int y = (int)((mouseY - this.oy) / this.k);
      int[] cell = this.cellAt(x, y);
      if (cell != null) {
         this.remember();
         this.useTool(cell[0], cell[1], button == 1);
         this.stroke = this.tool == FlagEditorScreen.Tool.BRUSH;
         return true;
      }

      int ty = CY + H * CELL + 8;
      FlagEditorScreen.Tool[] tools = FlagEditorScreen.Tool.values();

      for (int i = 0; i < tools.length; i++) {
         if (in(x, y, 28 + i * 53, ty, 50, 15)) {
            this.tool = tools[i];
            return true;
         }
      }

      if (in(x, y, 28, ty + 18, 76, 15)) {
         this.brush = this.brush >= 3 ? 1 : this.brush + 1;
      } else if (in(x, y, 110, ty + 18, 76, 15)) {
         this.undo();
      } else if (in(x, y, 28, ty + 36, 76, 15)) {
         this.remember();
         java.util.Arrays.fill(this.px, this.color);
      } else if (in(x, y, 110, ty + 36, 76, 15)) {
         this.remember();
         this.mirror();
      } else if (in(x, y, 214, 40, 152, 23)) {
         int i = (y - 40) / 13 * 8 + (x - 214) / 19;
         if (i >= 0 && i < DyeColor.values().length) {
            this.color = dyeRgb(DyeColor.values()[i]);
         }
      } else if (in(x, y, 329, 68, 44, 12)) {
         this.color = this.custom[0] << 16 | this.custom[1] << 8 | this.custom[2];
      } else if (in(x, y, 226, 84, 120, 33)) {
         this.slide(x, y);
      } else if (in(x, y, 214, BANNER_Y, 158, 15)) {
         this.remember();
         this.fillFromBannerOfRealm();
      } else if (in(x, y, 214, LOAD_Y, 158, 15)) {
         this.loadUrl(this.url.getValue().trim());
      } else if (in(x, y, 214, RAISE_Y, 106, 18)) {
         this.raise();
      } else if (in(x, y, 324, RAISE_Y, 48, 18)) {
         this.onClose();
      } else {
         return false;
      }

      return true;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
      int x = (int)((mouseX - this.ox) / this.k);
      int y = (int)((mouseY - this.oy) / this.k);
      if (this.stroke) {
         int[] cell = this.cellAt(x, y);
         if (cell != null) {
            this.useTool(cell[0], cell[1], button == 1);
         }

         return true;
      } else if (in(x, y, 226, 84, 120, 33)) {
         this.slide(x, y);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dx, dy);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.stroke = false;
      return super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (key == InputConstants.KEY_Z && hasControlDown() && !this.url.isFocused()) {
         this.undo();
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   private void slide(int x, int y) {
      int c = Math.max(0, Math.min(2, (y - 84) / 11));
      this.custom[c] = Math.max(0, Math.min(255, Math.round((x - 226) / 120.0F * 255.0F)));
      this.color = this.custom[0] << 16 | this.custom[1] << 8 | this.custom[2];
   }

   private void useTool(int x, int y, boolean secondary) {
      switch (this.tool) {
         case BRUSH:
            int paint = secondary ? 0xFFFFFF : this.color;

            for (int dy = 0; dy < this.brush; dy++) {
               for (int dx = 0; dx < this.brush; dx++) {
                  if (x + dx < W && y + dy < H) {
                     this.px[(y + dy) * W + x + dx] = paint;
                  }
               }
            }
            break;
         case FILL:
            this.flood(x, y, this.color);
            break;
         case PICK:
            this.color = this.px[y * W + x];
            this.custom[0] = this.color >> 16 & 0xFF;
            this.custom[1] = this.color >> 8 & 0xFF;
            this.custom[2] = this.color & 0xFF;
            this.tool = FlagEditorScreen.Tool.BRUSH;
      }

      kept = this.px.clone();
   }

   private void flood(int x, int y, int to) {
      int from = this.px[y * W + x];
      if (from != to) {
         Deque<int[]> todo = new ArrayDeque<>();
         todo.add(new int[]{x, y});

         while (!todo.isEmpty()) {
            int[] p = todo.poll();
            if (p[0] >= 0 && p[1] >= 0 && p[0] < W && p[1] < H && this.px[p[1] * W + p[0]] == from) {
               this.px[p[1] * W + p[0]] = to;
               todo.add(new int[]{p[0] + 1, p[1]});
               todo.add(new int[]{p[0] - 1, p[1]});
               todo.add(new int[]{p[0], p[1] + 1});
               todo.add(new int[]{p[0], p[1] - 1});
            }
         }
      }
   }

   /** Copies the left half onto the right, mirrored: most coats of arms are symmetric. */
   private void mirror() {
      for (int y = 0; y < H; y++) {
         for (int x = 0; x < W / 2; x++) {
            this.px[y * W + W - 1 - x] = this.px[y * W + x];
         }
      }

      kept = this.px.clone();
   }

   private void remember() {
      this.undo.push(this.px.clone());
      while (this.undo.size() > 40) {
         this.undo.removeLast();
      }
   }

   private void undo() {
      if (!this.undo.isEmpty()) {
         System.arraycopy(this.undo.pop(), 0, this.px, 0, this.px.length);
         kept = this.px.clone();
      }
   }

   private void fillFromBannerOfRealm() {
      int[] fresh = fromBanner(BookScreen.realmBanner());
      System.arraycopy(fresh, 0, this.px, 0, this.px.length);
      kept = this.px.clone();
   }

   private void say(Component text, int color) {
      this.status = text;
      this.statusColor = color;
   }

   private void loadUrl(String link) {
      if (!this.busy && !link.isEmpty()) {
         URI uri;
         try {
            uri = URI.create(link);
         } catch (IllegalArgumentException var4) {
            this.say(Ink.tr("flag_bad_url"), RED_INK);
            return;
         }

         if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            this.say(Ink.tr("flag_bad_url"), RED_INK);
         } else {
            this.busy = true;
            this.say(Ink.tr("flag_loading"), FADED);
            CompletableFuture.supplyAsync(() -> {
               try {
                  URLConnection c = uri.toURL().openConnection();
                  c.setConnectTimeout(5000);
                  c.setReadTimeout(10000);
                  c.setRequestProperty("User-Agent", "Prestol flag editor");
                  if (c instanceof HttpURLConnection http && http.getResponseCode() >= 400) {
                     throw new IOException("HTTP " + http.getResponseCode());
                  }

                  try (InputStream in = c.getInputStream()) {
                     return picture(readLimited(in));
                  }
               } catch (IOException | RuntimeException var7) {
                  throw new RuntimeException(var7.getMessage() == null ? var7.getClass().getSimpleName() : var7.getMessage(), var7);
               }
            }).whenComplete((pixels, error) -> Minecraft.getInstance().execute(() -> this.loaded(pixels, error)));
         }
      }
   }

   @Override
   public void onFilesDrop(List<Path> files) {
      if (!files.isEmpty() && !this.busy) {
         Path file = files.get(0);
         this.busy = true;
         CompletableFuture.supplyAsync(() -> {
            try {
               if (Files.size(file) > MAX_BYTES) {
                  throw new IOException(Ink.tr("flag_too_big").getString());
               } else {
                  return picture(Files.readAllBytes(file));
               }
            } catch (IOException var2) {
               throw new RuntimeException(var2.getMessage(), var2);
            }
         }).whenComplete((pixels, error) -> Minecraft.getInstance().execute(() -> this.loaded(pixels, error)));
      }
   }

   private void loaded(int[] pixels, Throwable error) {
      this.busy = false;
      if (error != null || pixels == null) {
         Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
         this.say(Ink.tr("flag_load_failed", cause == null ? "?" : cause.getMessage()), RED_INK);
      } else {
         this.remember();
         System.arraycopy(pixels, 0, this.px, 0, this.px.length);
         kept = this.px.clone();
         this.say(Ink.tr("flag_loaded"), -14730389);
      }
   }

   private static byte[] readLimited(InputStream in) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[16384];

      int n;
      while ((n = in.read(buf)) > 0) {
         out.write(buf, 0, n);
         if (out.size() > MAX_BYTES) {
            throw new IOException(Ink.tr("flag_too_big").getString());
         }
      }

      return out.toByteArray();
   }

   /** Decodes a picture and fits it onto the cloth: cropped to 3:2 around the middle, then smoothly scaled. */
   private static int[] picture(byte[] bytes) throws IOException {
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
      if (src == null) {
         throw new IOException(Ink.tr("flag_not_image").getString());
      } else {
         int sw = src.getWidth();
         int sh = src.getHeight();
         int cw = sw;
         int ch = sw * H / W;
         if (ch > sh) {
            ch = sh;
            cw = sh * W / H;
         }

         int x0 = (sw - cw) / 2;
         int y0 = (sh - ch) / 2;
         BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
         Graphics2D g = out.createGraphics();
         g.setColor(java.awt.Color.WHITE);
         g.fillRect(0, 0, W, H);
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         g.drawImage(src, 0, 0, W, H, x0, y0, x0 + cw, y0 + ch, null);
         g.dispose();
         int[] px = new int[W * H];

         for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
               px[y * W + x] = out.getRGB(x, y) & 0xFFFFFF;
            }
         }

         return px;
      }
   }

   private void raise() {
      byte[] rgb = new byte[W * H * 3];

      for (int i = 0; i < this.px.length; i++) {
         rgb[i * 3] = (byte)(this.px[i] >> 16);
         rgb[i * 3 + 1] = (byte)(this.px[i] >> 8);
         rgb[i * 3 + 2] = (byte)this.px[i];
      }

      Net.uploadFlag(W, H, rgb);
      this.onClose();
   }

   @Override
   public void onClose() {
      kept = this.px.clone();
      Minecraft.getInstance().setScreen(this.parent);
   }

   static enum Tool {
      BRUSH("brush"),
      FILL("fill"),
      PICK("pick");

      final String id;

      private Tool(String id) {
         this.id = id;
      }
   }
}
