package com.nick.prestol.flag;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Holding;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Settlements;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraftforge.registries.ForgeRegistries;

public final class TownFlags {
   private static final int MAST = 3;
   private static boolean dirty = true;
   private static int ticks;

   private TownFlags() {
   }

   public static void markDirty() {
      dirty = true;
   }

   public static void tick(MinecraftServer server) {
      ticks++;
      if (dirty || ticks % 5 == 0) {
         dirty = false;
         RealmData data = RealmData.get(server);
         boolean enabled = (Boolean)PrestolConfig.FLAGS_OVER_HALLS.get();
         int budget = 4;

         for (IColony colony : Settlements.all()) {
            if (budget <= 0) {
               dirty = true;
               return;
            }

            String key = Settlements.key(colony);
            Realm realm = data.byMember(key);
            String want = enabled && realm != null ? wanted(server, realm) : "";
            Holding h = data.peek(key);
            String have = h == null ? "" : h.mastFlag;
            if (!want.equals(have)) {
               ServerLevel level = server.getLevel(colony.getDimension());
               if (level != null && level.isLoaded(colony.getCenter())) {
                  h = data.holding(key);

                  try {
                     takeDown(level, h);
                     if (!want.isEmpty()) {
                        raise(server, level, colony, realm, want, h);
                     }

                     h.mastFlag = want;
                     data.setDirty();
                     budget--;
                  } catch (RuntimeException var13) {
                     Prestol.LOGGER.warn("Prestol: could not raise the flag over {}", colony.getName(), var13);
                     h.mastFlag = want;
                  }
               }
            }
         }
      }
   }

   private static String wanted(MinecraftServer server, Realm realm) {
      if (Flags.betsy()) {
         String id = Flags.imageFor(server, realm, true);
         if (!id.isEmpty()) {
            return "img:" + id;
         }
      }

      return "banner:" + Flags.layers(realm).toString().hashCode();
   }

   private static boolean ours(BlockState st) {
      if (!st.is(Blocks.SPRUCE_FENCE) && !(st.getBlock() instanceof AbstractBannerBlock)) {
         ResourceLocation id = ForgeRegistries.BLOCKS.getKey(st.getBlock());
         return Flags.FLAG_BLOCK.equals(id);
      } else {
         return true;
      }
   }

   public static void takeDown(ServerLevel level, Holding h) {
      for (int i = h.mast.size() - 1; i >= 0; i--) {
         BlockPos p = h.mast.get(i);
         if (level.isLoaded(p) && ours(level.getBlockState(p))) {
            level.removeBlock(p, false);
         }
      }

      h.mast.clear();
   }

   private static void raise(MinecraftServer server, ServerLevel level, IColony colony, Realm realm, String want, Holding h) {
      BlockPos base = spot(level, colony.getCenter());
      BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
      List<BlockPos> placed = new ArrayList<>();

      for (int i = 0; i < 3; i++) {
         level.setBlock(base.above(i), fence, 3);
         placed.add(base.above(i));
      }

      BlockPos top = base.above(3);
      int rot = Math.floorMod(colony.getID() * 5 + 2, 16);
      boolean done = false;
      if (want.startsWith("img:")) {
         done = placeImage(level, top, want.substring(4), fence, rot, placed);
      }

      if (!done) {
         placeBanner(level, top, Flags.banner(Flags.layers(realm)), rot, placed);
      }

      h.mast.clear();
      h.mast.addAll(placed);
   }

   private static BlockPos spot(ServerLevel level, BlockPos center) {
      BlockPos best = null;
      int bestScore = Integer.MIN_VALUE;

      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(p.below());
            if (!below.isAir()
               && !below.is(BlockTags.LEAVES)
               && below.getFluidState().isEmpty()
               && !ours(below)
               && !below.getCollisionShape(level, p.below()).isEmpty()) {
               boolean free = true;

               for (int i = 0; i < 9 && free; i++) {
                  free = level.getBlockState(p.above(i)).isAir();
               }

               if (free) {
                  int score = y * 8 - Math.abs(dx) - Math.abs(dz);
                  if (score > bestScore) {
                     bestScore = score;
                     best = p;
                  }
               }
            }
         }
      }

      return best != null
         ? best
         : new BlockPos(center.getX(), level.getHeight(Types.MOTION_BLOCKING, center.getX(), center.getZ()), center.getZ());
   }

   private static boolean placeImage(ServerLevel level, BlockPos top, String id, BlockState fence, int rot, List<BlockPos> placed) {
      try {
         Block block = (Block)ForgeRegistries.BLOCKS.getValue(Flags.FLAG_BLOCK);
         if (block != null && block != Blocks.AIR) {
            BlockState st = block.defaultBlockState();
            if (st.hasProperty(BlockStateProperties.ROTATION_16)) {
               st = (BlockState)st.setValue(BlockStateProperties.ROTATION_16, rot);
            }

            level.setBlock(top, st, 3);
            BlockEntity be = level.getBlockEntity(top);
            if (be == null) {
               level.removeBlock(top, false);
               return false;
            } else {
               CompoundTag tag = be.saveWithoutMetadata();
               tag.putString("FlagId", id);
               tag.put("Flagpole", NbtUtils.writeBlockState(fence));
               be.load(tag);
               be.setChanged();
               level.sendBlockUpdated(top, st, st, 3);
               placed.add(top);
               int[] size = Flags.imageSize(id);
               int h = Math.min(6, size == null ? 2 : Math.max(1, size[1]));

               for (int i = 1; i < h; i++) {
                  level.setBlock(top.above(i), fence, 3);
                  placed.add(top.above(i));
               }

               return true;
            }
         } else {
            return false;
         }
      } catch (RuntimeException var13) {
         Prestol.LOGGER.warn("Prestol: Betsy Ross flag could not be raised, a banner goes up instead", var13);
         return false;
      }
   }

   private static void placeBanner(ServerLevel level, BlockPos top, ItemStack banner, int rot, List<BlockPos> placed) {
      if (banner.getItem() instanceof BannerItem item) {
         BlockState st = item.getBlock().defaultBlockState();
         if (st.hasProperty(BannerBlock.ROTATION)) {
            st = (BlockState)st.setValue(BannerBlock.ROTATION, rot);
         }

         level.setBlock(top, st, 3);
         if (level.getBlockEntity(top) instanceof BannerBlockEntity be) {
            be.fromItem(banner);
            be.setChanged();
            level.sendBlockUpdated(top, st, st, 3);
         }

         placed.add(top);
      }
   }

   public static int count(RealmData data) {
      int n = 0;

      for (Holding h : data.holdings().values()) {
         if (!h.mast.isEmpty()) {
            n++;
         }
      }

      return n;
   }
}
