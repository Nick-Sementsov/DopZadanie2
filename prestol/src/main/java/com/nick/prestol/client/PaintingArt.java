package com.nick.prestol.client;

import immersive_paintings.resources.ClientPaintingManager;
import immersive_paintings.resources.Painting;
import immersive_paintings.resources.Painting.Texture;
import immersive_paintings.resources.Painting.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

final class PaintingArt {
   private PaintingArt() {
   }

   static PaintingArt.Pic texture(String id, boolean big) {
      ResourceLocation rl = ResourceLocation.tryParse(id);
      if (rl == null) {
         return null;
      } else {
         Painting p = ClientPaintingManager.getPainting(rl);
         if (p != null && p != Painting.DEFAULT) {
            Texture t = ClientPaintingManager.getPaintingTexture(rl, big ? Type.HALF : Type.THUMBNAIL);
            return t != null && t.image != null && t.textureIdentifier != null
               ? new PaintingArt.Pic(t.textureIdentifier, t.image.getWidth(), t.image.getHeight(), p.width, p.height)
               : null;
         } else {
            return null;
         }
      }
   }

   static List<PaintingArt.Choice> choices() {
      String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName();
      List<PaintingArt.Choice> mine = new ArrayList<>();
      List<PaintingArt.Choice> others = new ArrayList<>();

      for (Entry<ResourceLocation, Painting> e : ClientPaintingManager.getPaintings().entrySet()) {
         Painting p = e.getValue();
         if (p != null && !p.hidden && !p.graffiti) {
            PaintingArt.Choice c = new PaintingArt.Choice(
               e.getKey().toString(), p.name == null ? e.getKey().getPath() : p.name, p.author == null ? "" : p.author, p.datapack, p.width, p.height
            );
            if (me.equalsIgnoreCase(c.author())) {
               mine.add(c);
            } else {
               others.add(c);
            }
         }
      }

      mine.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
      others.sort(
         (a, b) -> Boolean.compare(a.datapack(), b.datapack()) != 0 ? Boolean.compare(a.datapack(), b.datapack()) : a.name().compareToIgnoreCase(b.name())
      );
      mine.addAll(others);
      return mine;
   }

   static record Choice(String id, String name, String author, boolean datapack, int w, int h) {
   }

   static record Pic(ResourceLocation texture, int pixelsW, int pixelsH, int blocksW, int blocksH) {
   }
}
