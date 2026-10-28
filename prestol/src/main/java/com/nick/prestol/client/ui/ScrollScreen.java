package com.nick.prestol.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nick.prestol.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ScrollScreen extends Screen {
   private static final int W = 300;
   private static final int H = 210;
   private final String key;
   private final String name;
   private final long deadline;
   private float k = 1.0F;
   private int ox;
   private int oy;

   private ScrollScreen(String key, String name, int seconds) {
      super(Component.translatable("prestol.scroll.title"));
      this.key = key;
      this.name = name;
      this.deadline = System.currentTimeMillis() + (long)seconds * 1000L;
   }

   public static void open(String key, String name, int seconds) {
      Minecraft.getInstance().setScreen(new ScrollScreen(key, name, seconds));
   }

   protected void init() {
      this.k = Math.min(1.0F, Math.min((float)(this.width - 8) / 300.0F, (float)(this.height - 8) / 210.0F));
      this.ox = (int)(((float)this.width - 300.0F * this.k) / 2.0F);
      this.oy = (int)(((float)this.height - 210.0F * this.k) / 2.0F);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private int[] seal(int i) {
      return new int[]{38 + i * 82, 112, 60, 60};
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.renderBackground(g);
      int mx = (int)((float)(mouseX - this.ox) / this.k);
      int my = (int)((float)(mouseY - this.oy) / this.k);
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate((float)this.ox, (float)this.oy, 0.0F);
      pose.scale(this.k, this.k, 1.0F);
      Ink.blit(g, Ink.SCROLL, 0, 0, 300, 210, 600, 420);
      Ink.centered(g, Component.translatable("prestol.scroll.title"), Ink.F_TITLE, 150, 30, -7464176);
      Ink.divider(g, 70, 46, 160);
      Ink.para(g, Component.translatable("prestol.scroll.text", new Object[]{this.name}), Ink.F_BODY, 34, 58, 232, -13951730);
      String[] ids = new String[]{"keep", "sack", "enslave"};
      int[] colors = new int[]{3963438, 11569706, 9313040};

      for (int i = 0; i < 3; i++) {
         int[] s = this.seal(i);
         boolean hover = mx >= s[0] && my >= s[1] && mx < s[0] + s[2] && my < s[1] + s[3] + 26;
         int size = hover ? 40 : 36;
         Ink.seal(g, s[0] + (s[2] - size) / 2, s[1] + (36 - size) / 2, size, colors[i]);
         Ink.centered(g, Component.translatable("prestol.scroll." + ids[i]), Ink.F_BOLD, s[0] + s[2] / 2, s[1] + 40, hover ? -7464176 : -13951730);
         if (hover) {
            g.renderTooltip(this.font, this.font.split(Component.translatable("prestol.scroll." + ids[i] + ".desc"), 200), mouseX, mouseY);
         }
      }

      int left = (int)Math.max(0L, (this.deadline - System.currentTimeMillis()) / 1000L);
      Ink.centered(g, Component.translatable("prestol.scroll.time", new Object[]{Ink.time(left)}), Ink.F_SMALL, 150, 184, -9544893);
      pose.popPose();
      if (left <= 0) {
         this.onClose();
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int mx = (int)((mouseX - (double)this.ox) / (double)this.k);
      int my = (int)((mouseY - (double)this.oy) / (double)this.k);
      String[] ids = new String[]{"keep", "sack", "enslave"};

      for (int i = 0; i < 3; i++) {
         int[] s = this.seal(i);
         if (mx >= s[0] && my >= s[1] && mx < s[0] + s[2] && my < s[1] + s[3] + 26) {
            Net.order("conquest " + this.key + " " + ids[i]);
            this.onClose();
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }
}
