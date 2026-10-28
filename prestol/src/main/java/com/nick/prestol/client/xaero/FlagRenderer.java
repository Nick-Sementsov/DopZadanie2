package com.nick.prestol.client.xaero;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nick.prestol.client.ClientMap;
import com.nick.prestol.client.FlagArt;
import com.nick.prestol.map.MapSnapshot;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

public final class FlagRenderer extends ElementRenderer<MapSnapshot.SettlementInfo, FlagRenderer.Context, FlagRenderer> {
   private static final int INK = -13952500;
   private static final int GOLD = -1523638;

   private FlagRenderer(FlagRenderer.Context context) {
      super(context, new FlagRenderer.Provider(), new FlagRenderer.Reader());
   }

   public static FlagRenderer create() {
      return new FlagRenderer(new FlagRenderer.Context());
   }

   private static float scale(MapSnapshot.SettlementInfo s) {
      if (s.capital()) {
         return 1.35F;
      } else {
         return switch (s.tier()) {
            case 1 -> 0.85F;
            case 2 -> 1.05F;
            default -> 0.7F;
         };
      }
   }

   public void preRender(ElementRenderInfo info, BufferSource buffers, MultiTextureRenderTypeRendererProvider multi, boolean shadow) {
      ResourceKey<Level> dim = info.mapDimension;
      ((FlagRenderer.Context)this.context).dim = dim == null ? "" : dim.location().toString();
   }

   public void postRender(ElementRenderInfo info, BufferSource buffers, MultiTextureRenderTypeRendererProvider multi, boolean shadow) {
   }

   public void renderElementShadow(
      MapSnapshot.SettlementInfo s,
      boolean hovered,
      float optionalScale,
      double partialX,
      double partialY,
      ElementRenderInfo info,
      GuiGraphics g,
      BufferSource buffers,
      MultiTextureRenderTypeRendererProvider multi
   ) {
   }

   public boolean renderElement(
      MapSnapshot.SettlementInfo s,
      boolean hovered,
      double optionalDepth,
      float optionalScale,
      double partialX,
      double partialY,
      ElementRenderInfo info,
      GuiGraphics g,
      BufferSource buffers,
      MultiTextureRenderTypeRendererProvider multi
   ) {
      MapSnapshot.RealmInfo realm = ClientMap.realm(s.realm());
      if (realm == null) {
         return false;
      } else {
         buffers.endBatch();
         PoseStack pose = g.pose();
         pose.pushPose();
         pose.translate(partialX, partialY, 0.0);
         float k = 2.0F * scale(s);
         pose.scale(k, k, 1.0F);
         g.fill(-2, -1, 1, 0, -13952500);
         g.fill(-1, -26, 0, -1, -11915240);
         if (s.capital()) {
            crown(g, -4, -31);
         } else {
            g.fill(-2, -27, 1, -26, -1523638);
         }

         int[] used = FlagArt.drawOnPole(g, realm.flag(), realm.banner(), 0, -25, 18, 12);
         int fw = used[0] > 0 ? used[0] : 12;
         int fh = used[1] > 0 ? used[1] : 12;
         if (used[0] <= 0) {
            g.fill(0, -25, fw, -25 + fh, 0xFF000000 | realm.color());
         }

         hem(g, 0, -25, fw, fh);
         if (s.siege() != 0) {
            boolean on = System.currentTimeMillis() / 400L % 2L == 0L;
            swords(g, fw + 2, -27, on ? -50384 : -5234662);
         }

         pose.popPose();
         pose.pushPose();
         pose.translate(partialX, partialY, 0.0);
         pose.scale(1.2F, 1.2F, 1.0F);
         g.drawCenteredString(Minecraft.getInstance().font, s.name(), 0, 3, 0xFF000000 | ClientMap.colorOf(s));
         pose.popPose();
         return true;
      }
   }

   private static void hem(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y - 1, x + w + 1, y, -13952500);
      g.fill(x, y + h, x + w + 1, y + h + 1, -13952500);
      g.fill(x + w, y - 1, x + w + 1, y + h + 1, -13952500);
   }

   private static void crown(GuiGraphics g, int x, int y) {
      for (int pass = 0; pass < 2; pass++) {
         int d = pass == 0 ? 1 : 0;
         int c = pass == 0 ? -13952500 : -1523638;
         g.fill(x + d, y + 1 + d, x + 1 + d, y + 3 + d, c);
         g.fill(x + 3 + d, y + d, x + 4 + d, y + 3 + d, c);
         g.fill(x + 6 + d, y + 1 + d, x + 7 + d, y + 3 + d, c);
         g.fill(x + d, y + 3 + d, x + 7 + d, y + 5 + d, c);
      }

      g.fill(x + 3, y + 3, x + 4, y + 4, -4186086);
   }

   private static void swords(GuiGraphics g, int x, int y, int color) {
      for (int i = 0; i < 7; i++) {
         g.fill(x + i + 1, y + i + 1, x + i + 2, y + i + 2, -13952500);
         g.fill(x + 7 - i, y + i + 1, x + 8 - i, y + i + 2, -13952500);
      }

      for (int i = 0; i < 7; i++) {
         g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
         g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color);
      }

      g.fill(x + 1, y + 4, x + 2, y + 5, -1523638);
      g.fill(x + 5, y + 4, x + 6, y + 5, -1523638);
   }

   public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
      return !shadow && location == ElementRenderLocation.WORLD_MAP;
   }

   public static final class Context {
      String dim = "";
   }

   private static final class Provider extends ElementRenderProvider<MapSnapshot.SettlementInfo, FlagRenderer.Context> {
      private Iterator<MapSnapshot.SettlementInfo> it;
      private MapSnapshot.SettlementInfo next;

      public void begin(ElementRenderLocation location, FlagRenderer.Context context) {
         this.it = ClientMap.settlements().iterator();
         this.advance(context);
      }

      private void advance(FlagRenderer.Context context) {
         this.next = null;

         while (this.it != null && this.it.hasNext()) {
            MapSnapshot.SettlementInfo s = this.it.next();
            if (s.realm() != null && s.dim().equals(context.dim)) {
               this.next = s;
               return;
            }
         }
      }

      public boolean hasNext(ElementRenderLocation location, FlagRenderer.Context context) {
         return this.next != null;
      }

      public MapSnapshot.SettlementInfo getNext(ElementRenderLocation location, FlagRenderer.Context context) {
         MapSnapshot.SettlementInfo s = this.next;
         this.advance(context);
         return s;
      }

      public void end(ElementRenderLocation location, FlagRenderer.Context context) {
         this.it = null;
         this.next = null;
      }
   }

   private static final class Reader extends ElementReader<MapSnapshot.SettlementInfo, FlagRenderer.Context, FlagRenderer> {
      public boolean isHidden(MapSnapshot.SettlementInfo s, FlagRenderer.Context context) {
         return s.realm() == null;
      }

      public double getRenderX(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return (double)s.x() + 0.5;
      }

      public double getRenderZ(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return (double)s.z() + 0.5;
      }

      public double getRenderY(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return (double)s.y();
      }

      public int getInteractionBoxLeft(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return -4;
      }

      public int getInteractionBoxRight(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return 40;
      }

      public int getInteractionBoxTop(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return -60;
      }

      public int getInteractionBoxBottom(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return 0;
      }

      public int getRenderBoxLeft(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return -120;
      }

      public int getRenderBoxRight(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return 120;
      }

      public int getRenderBoxTop(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return -160;
      }

      public int getRenderBoxBottom(MapSnapshot.SettlementInfo s, FlagRenderer.Context context, float partialTicks) {
         return 40;
      }

      public int getLeftSideLength(MapSnapshot.SettlementInfo s, Minecraft mc) {
         return 0;
      }

      public String getMenuName(MapSnapshot.SettlementInfo s) {
         return s.name();
      }

      public String getFilterName(MapSnapshot.SettlementInfo s) {
         return s.name();
      }

      public int getMenuTextFillLeftPadding(MapSnapshot.SettlementInfo s) {
         return 0;
      }

      public int getRightClickTitleBackgroundColor(MapSnapshot.SettlementInfo s) {
         return 0xFF000000 | ClientMap.colorOf(s);
      }

      public boolean shouldScaleBoxWithOptionalScale() {
         return false;
      }

      public boolean isInteractable(ElementRenderLocation location, MapSnapshot.SettlementInfo s) {
         return false;
      }
   }
}
