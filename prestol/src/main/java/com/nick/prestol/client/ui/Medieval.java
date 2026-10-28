package com.nick.prestol.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class Medieval {
   public static final Medieval.Tex WINDOW = new Medieval.Tex(new ResourceLocation("prestol", "textures/gui/sheet.png"), 800, 560);
   public static final Medieval.Tex BANNER = new Medieval.Tex(new ResourceLocation("prestol", "textures/gui/title.png"), 480, 68);
   public static final ResourceLocation F_TITLE = Ink.F_TITLE;
   public static final ResourceLocation F_BODY = Ink.F_BODY;
   public static final ResourceLocation F_BOLD = Ink.F_BOLD;
   public static final ResourceLocation F_SMALL = Ink.F_SMALL;
   public static final ResourceLocation F_TAB = Ink.F_BOLD;
   public static final int INK = 2825486;
   public static final int INK_LIGHT = 7228202;
   public static final int INK_FADED = 7232323;
   public static final int RUBRIC = 9313040;
   public static final int GREEN_INK = 3103260;
   public static final int GOLD_TEXT = 15783050;
   public static final int BROWN = 5913114;

   private Medieval() {
   }

   public static MutableComponent txt(String s, ResourceLocation font) {
      return Component.literal(s).withStyle(st -> st.withFont(font));
   }

   public static void draw(GuiGraphics g, Medieval.Tex tex, int x, int y, int w, int h) {
      Ink.blit(g, tex.id(), x, y, w, h, tex.w(), tex.h());
   }

   public static void card(GuiGraphics g, int x, int y, int w, int h, float alpha) {
      int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
      g.fill(x, y, x + w, y + h, a << 24 | 7031330);
      g.renderOutline(x, y, w, h, -2140251325);
   }

   public static void field(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, 822083583);
      g.fill(x, y + h - 1, x + w, y + h, -9544893);
   }

   public static void thumb(GuiGraphics g, int x, int y, int h) {
      g.fill(x, y, x + 3, y + h, -1603380413);
   }

   public static void seal(GuiGraphics g, int x, int y, int size, int rgb) {
      Ink.seal(g, x, y, size, rgb);
   }

   public static void divider(GuiGraphics g, int x, int y, int w) {
      Ink.divider(g, x, y, w);
   }

   public static int darker(int rgb, float f) {
      int r = (int)((float)(rgb >> 16 & 0xFF) * f);
      int g = (int)((float)(rgb >> 8 & 0xFF) * f);
      int b = (int)((float)(rgb & 0xFF) * f);
      return r << 16 | g << 8 | b;
   }

   static Minecraft mc() {
      return Minecraft.getInstance();
   }

   public static class PlaqueButton extends Button {
      private final int color;

      public PlaqueButton(int x, int y, int w, int h, Component label, OnPress press, int color) {
         super(x, y, w, h, label, press, DEFAULT_NARRATION);
         this.color = color;
      }

      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         if (this.color >= 0) {
            Ink.tint(this.color | 4210752, 1.0F);
         }

         Ink.strap(g, this.getX(), this.getY(), this.getWidth(), this.getHeight(), !this.active ? 2 : (this.isHoveredOrFocused() ? 1 : 0));
         Ink.untint();
         ResourceLocation font = this.getMessage().getString().length() <= 3 ? Ink.F_BOLD : Ink.F_SMALL;
         Component l = Ink.clip(this.getMessage(), font, this.getWidth() - 6);
         Ink.centered(g, l, font, this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, this.active ? -994166 : -5199720);
      }
   }

   public static class RibbonTab extends Button {
      private final boolean selected;
      private final int color;

      public RibbonTab(int x, int y, int w, Component label, OnPress press, boolean selected, int color) {
         super(x, y, w, 18, label, press, DEFAULT_NARRATION);
         this.selected = selected;
         this.color = color;
      }

      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         int c = this.selected ? this.color : Medieval.darker(this.color, this.isHoveredOrFocused() ? 0.85F : 0.65F);
         Ink.tint(c, 1.0F);
         Ink.blit(g, Ink.STRIP, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 720, 28);
         Ink.untint();
         Component l = Ink.clip(this.getMessage(), Ink.F_BOLD, this.getWidth() - 8);
         Ink.centered(g, l, Ink.F_BOLD, this.getX() + this.getWidth() / 2, this.getY() + 5, this.selected ? -3896 : -1517392);
         if (this.selected) {
            g.fill(
               this.getX() + 4,
               this.getY() + this.getHeight() - 2,
               this.getX() + this.getWidth() - 4,
               this.getY() + this.getHeight() - 1,
               -994166
            );
         }
      }
   }

   public static record Tex(ResourceLocation id, int w, int h) {
   }
}
