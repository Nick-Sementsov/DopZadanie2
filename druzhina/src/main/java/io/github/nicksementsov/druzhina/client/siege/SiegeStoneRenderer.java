package io.github.nicksementsov.druzhina.client.siege;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nicksementsov.druzhina.entity.siege.SiegeStone;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/** A stone in flight: a tumbling lump of cobblestone. */
public class SiegeStoneRenderer extends EntityRenderer<SiegeStone> {
    private final BlockRenderDispatcher blocks;

    public SiegeStoneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.35f;
    }

    @Override
    public void render(SiegeStone stone, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float spin = (stone.tickCount + partialTick) * 9;
        pose.translate(0, 0.35, 0);
        pose.mulPose(Axis.XP.rotationDegrees(spin));
        pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.6f));
        pose.scale(0.7f, 0.7f, 0.7f);
        pose.translate(-0.5, -0.5, -0.5);
        this.blocks.renderSingleBlock(Blocks.COBBLESTONE.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(stone, yaw, partialTick, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(SiegeStone stone) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
