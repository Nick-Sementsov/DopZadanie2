package com.nick.prestol.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class Ink {
   public static final ResourceLocation BOOK = tex("book");
   public static final ResourceLocation RIBBON = tex("ribbon");
   public static final ResourceLocation BUTTON = tex("button");
   public static final ResourceLocation SEAL = tex("seal");
   public static final ResourceLocation DIVIDER = tex("divider");
   public static final ResourceLocation INITIAL = tex("initial");
   public static final ResourceLocation FRAME = tex("frame");
   public static final ResourceLocation SCROLL = tex("scroll");
   public static final ResourceLocation STRIP = tex("strip");
   public static final ResourceLocation F_TITLE = new ResourceLocation("prestol", "title");
   public static final ResourceLocation F_BODY = new ResourceLocation("prestol", "body");
   public static final ResourceLocation F_BOLD = new ResourceLocation("prestol", "bold");
   public static final ResourceLocation F_SMALL = new ResourceLocation("prestol", "small");
   public static final ResourceLocation F_INITIAL = new ResourceLocation("prestol", "initial");
   public static final int INK = -13951730;
   public static final int FADED = -9544893;
   public static final int RUBRIC = -7464176;
   public static final int GREEN = -13673956;
   public static final int BLUE = -14730389;
   public static final int GOLD = -994166;
   public static final int PARCHMENT = -924736;

   private Ink() {
   }

   private static ResourceLocation tex(String name) {
      return new ResourceLocation("prestol", "textures/gui/" + name + ".png");
   }

   public static Font font() {
      return Minecraft.getInstance().font;
   }

   private static void smooth(ResourceLocation tex) {
      try {
         AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
         t.setFilter(true, false);
      } catch (RuntimeException var2) {
      }
   }

   public static MutableComponent in(Component c, ResourceLocation font) {
      return c.copy().withStyle(s -> s.withFont(font));
   }

   public static MutableComponent tr(String key, Object... args) {
      return Component.translatable("prestol.book." + key, args);
   }

   public static MutableComponent lit(String s) {
      return Component.literal(s);
   }

   public static int text(GuiGraphics g, Component c, ResourceLocation font, int x, int y, int color) {
      Component styled = in(c, font);
      g.drawString(font(), styled, x, y, color, false);
      return font().width(styled);
   }

   public static int width(Component c, ResourceLocation font) {
      return font().width(in(c, font));
   }

   public static void centered(GuiGraphics g, Component c, ResourceLocation font, int cx, int y, int color) {
      Component styled = in(c, font);
      g.drawString(font(), styled, cx - font().width(styled) / 2, y, color, false);
   }

   public static int para(GuiGraphics g, Component c, ResourceLocation font, int x, int y, int w, int color) {
      List<FormattedCharSequence> lines = font().split(in(c, font), w);
      int lh = lineHeight(font);

      for (int i = 0; i < lines.size(); i++) {
         g.drawString(font(), lines.get(i), x, y + i * lh, color, false);
      }

      return lines.size() * lh;
   }

   public static int lines(Component c, ResourceLocation font, int w) {
      return font().split(in(c, font), w).size();
   }

   public static int lineHeight(ResourceLocation font) {
      return font == F_TITLE ? 13 : (font == F_SMALL ? 9 : 10);
   }

   public static Component clip(Component c, ResourceLocation font, int w) {
      Component styled = in(c, font);
      if (font().width(styled) <= w) {
         return styled;
      } else {
         FormattedText cut = font().substrByWidth(styled, w - font().width("…"));
         return Component.literal(cut.getString() + "…").withStyle(s -> s.withFont(font));
      }
   }

   public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
      smooth(tex);
      RenderSystem.enableBlend();
      g.blit(tex, x, y, w, h, 0.0F, 0.0F, texW, texH, texW, texH);
   }

   public static void tint(int rgb, float alpha) {
      RenderSystem.enableBlend();
      RenderSystem.setShaderColor((float)(rgb >> 16 & 0xFF) / 255.0F, (float)(rgb >> 8 & 0xFF) / 255.0F, (float)(rgb & 0xFF) / 255.0F, alpha);
   }

   public static void untint() {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   public static void book(GuiGraphics g, int x, int y) {
      blit(g, BOOK, x, y, 400, 250, 800, 500);
   }

   public static void ribbon(GuiGraphics g, int x, int y, int length, int thickness, int rgb) {
      smooth(RIBBON);
      tint(rgb, 1.0F);
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate((float)(x + length), (float)y, 0.0F);
      pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
      g.blit(RIBBON, 0, 0, thickness, length, 0.0F, 0.0F, 40, Math.min(92, length * 2), 40, 92);
      pose.popPose();
      untint();
   }

   public static void strap(GuiGraphics g, int x, int y, int w, int h, int state) {
      smooth(BUTTON);
      RenderSystem.enableBlend();
      int v = state * 32;
      // the caps carry the rivets; a narrow button keeps only their rim so its sign stays clear
      int tex = w < 30 ? 4 : 12;
      int cap = Math.min(tex / 2, w / 2);
      g.blit(BUTTON, x, y, cap, h, 0.0F, (float)v, tex, 32, 200, 96);
      g.blit(BUTTON, x + cap, y, w - cap * 2, h, 12.0F, (float)v, 176, 32, 200, 96);
      g.blit(BUTTON, x + w - cap, y, cap, h, (float)(200 - tex), (float)v, tex, 32, 200, 96);
   }

   public static void seal(GuiGraphics g, int x, int y, int size, int rgb) {
      smooth(SEAL);
      tint(rgb, 1.0F);
      g.blit(SEAL, x, y, size, size, 0.0F, 0.0F, 64, 64, 64, 64);
      untint();
   }

   public static void divider(GuiGraphics g, int x, int y, int w) {
      smooth(DIVIDER);
      RenderSystem.enableBlend();
      g.blit(DIVIDER, x, y, w, 8, 0.0F, 0.0F, 320, 16, 320, 16);
   }

   public static void frame(GuiGraphics g, int x, int y, int w, int h) {
      smooth(FRAME);
      RenderSystem.enableBlend();
      g.blit(FRAME, x, y, w, h, 0.0F, 0.0F, 80, 60, 80, 60);
   }

   public static int initial(GuiGraphics g, String letter, int x, int y) {
      blit(g, INITIAL, x, y, 20, 20, 48, 48);
      Component c = in(lit(letter), F_INITIAL);
      g.drawString(font(), c, x + 10 - font().width(c) / 2, y + 4, -7464176, false);
      return 22;
   }

   public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int fill) {
      g.fill(x, y, x + w, y + h, 1073741824);
      g.fill(x + 1, y + 1, x + 1 + Math.round((float)(w - 2) * Math.max(0.0F, Math.min(1.0F, frac))), y + h - 1, fill);
      g.renderOutline(x, y, w, h, -10862298);
   }

   public static String time(int seconds) {
      int s = Math.max(0, seconds);
      return s >= 3600
         ? s / 3600 + ":" + String.format("%02d", s / 60 % 60) + ":" + String.format("%02d", s % 60)
         : s / 60 + ":" + String.format("%02d", s % 60);
   }
}
