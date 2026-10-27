package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicksementsov.knightcombat.item.HeldSprite;
import io.github.nicksementsov.knightcombat.item.KnightWeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the weapon in the hand of a posed player, with the blade pointing where the pose says. */
public class WeaponLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Size of a 16 pixel item held in the hand, the same as Minecraft's own third person scale. */
    private static final float SCALE_16 = 0.85f;

    public WeaponLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffers, int light, @NotNull AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = ThirdPersonAnimator.hiddenWeapon(player);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        HumanoidArm arm = player.getMainArm();
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        HeldSprite sprite = stack.getItem() instanceof KnightWeaponItem item ? item.heldSprite() : HeldSprite.VANILLA;

        poseStack.pushPose();
        model.translateToHand(arm, poseStack);
        Vector3f grip = arm == HumanoidArm.RIGHT ? PoseMath.RIGHT_GRIP : PoseMath.LEFT_GRIP;
        poseStack.translate(grip.x / 16f, grip.y / 16f, grip.z / 16f);
        // The pose gives the blade in model space; the stack is already turned with the arm.
        Quaternionf armRotation = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        poseStack.mulPose(armRotation.conjugate().mul(PoseMath.spriteRotation(ThirdPersonAnimator.blade(), ThirdPersonAnimator.flat())));
        float scale = SCALE_16 * sprite.size() / 16f;
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.5f - sprite.gripX() / sprite.size(), sprite.gripY() / sprite.size() - 0.5f, 0);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                .renderItem(player, stack, ItemDisplayContext.NONE, arm == HumanoidArm.LEFT, poseStack, buffers, light);
        poseStack.popPose();
    }
}
