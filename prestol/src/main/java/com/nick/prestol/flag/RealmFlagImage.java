package com.nick.prestol.flag;

import immersive_paintings.cobalt.network.NetworkHandler;
import immersive_paintings.network.s2c.PaintingListMessage;
import immersive_paintings.resources.ByteImage;
import immersive_paintings.resources.Painting;
import immersive_paintings.resources.ServerPaintingManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Turns a flag drawn in the realm book into an Immersive Paintings image; loaded only when that mod is present. */
final class RealmFlagImage {
   static final String PREFIX = "realm_";

   private RealmFlagImage() {
   }

   static ResourceLocation register(MinecraftServer server, String realmId, String realmName, String author, int w, int h, byte[] rgb) {
      ByteImage image = new ByteImage(w, h);

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 3;
            image.setPixel(x, y, rgb[i] & 0xFF, rgb[i + 1] & 0xFF, rgb[i + 2] & 0xFF, 255);
         }
      }

      String hash = Integer.toHexString(java.util.Arrays.hashCode(rgb)) + Long.toString(System.currentTimeMillis(), 36);
      ResourceLocation id = new ResourceLocation("prestol", PREFIX + realmId + "_" + hash);
      // a flag is 3 x 2 blocks: 48 x 32 pixels at 16 pixels a block
      Painting painting = new Painting(image, w / 16, h / 16, 16, realmName, author, false, true, false, hash);
      ServerPaintingManager.registerPainting(id, painting);
      PaintingListMessage msg = new PaintingListMessage(id, painting);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         NetworkHandler.sendToPlayer(msg, p);
      }

      return id;
   }

   /** Drops an older drawn flag of the realm so redrawing does not pile up images. */
   static void forget(String flagId) {
      if (flagId != null && flagId.startsWith("prestol:" + PREFIX) && ResourceLocation.isValidResourceLocation(flagId)) {
         ServerPaintingManager.deregisterPainting(new ResourceLocation(flagId));
      }
   }
}
