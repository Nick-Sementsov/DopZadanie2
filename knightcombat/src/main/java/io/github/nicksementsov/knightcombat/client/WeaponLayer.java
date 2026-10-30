package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the weapon in the hand of a posed player, with the blade and the edge pointing where the pose says,
 * and the trail of a swing behind it. The weapon keeps its own model and hand transform (so it looks the way
 * its mod made it) and is only turned around the hand and made a little larger.
 * <p>
 * The shield in the other hand is drawn here too, where Minecraft would draw it, but always with its model at rest:
 * Minecraft's model of a raised shield would turn it on the arm that this mod raises its own way.
 */
public class WeaponLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Longest weapons that are still made larger, in blocks from the hand to the tip. */
    private static final float MAX_LENGTH_ONE = 1.3f;
    private static final float MAX_LENGTH_TWO = 2.1f;
    private static final float MAX_LENGTH_POLEARM = 3.2f;
    private static final float TRAIL_OPACITY = 0.5f;

    public WeaponLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffers, int light, @NotNull AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack shield = ThirdPersonAnimator.hiddenShield(player);
        if (shield != null) {
            renderShield(poseStack, buffers, light, player, shield, partialTick);
        }
        ItemStack stack = ThirdPersonAnimator.hiddenWeapon(player);
        WeaponStats weapon = ThirdPersonAnimator.posedStats();
        if (stack == null || weapon == null) {
            return;
        }
        HumanoidArm arm = player.getMainArm();
        boolean left = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        HeldModel.Axes axes = HeldModel.axes(stack, context);
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ModelPart part = left ? model.leftArm : model.rightArm;
        float scale = scale(weapon, axes);

        poseStack.pushPose();
        model.translateToHand(arm, poseStack);
        Vector3f grip = left ? PoseMath.LEFT_GRIP : PoseMath.RIGHT_GRIP;
        poseStack.translate(grip.x / 16f, grip.y / 16f, grip.z / 16f);
        // The pose gives the blade and the edge in model space; the stack is already turned with the arm.
        Quaternionf armRotation = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        Quaternionf weaponRotation = PoseMath.frame(axes.blade(), axes.edge(), ThirdPersonAnimator.blade(), ThirdPersonAnimator.edge());
        poseStack.mulPose(armRotation.conjugate().mul(weaponRotation));
        poseStack.scale(scale, scale, scale);
        HeldModel.render(stack, context, left, poseStack, buffers, light);
        poseStack.popPose();

        FighterAnim anim = ClientCombat.anim(player);
        float[] range = CombatConfig.SWING_TRAILS.get() ? SwingTrail.range(anim, partialTick) : null;
        if (range != null) {
            Vector3f[] bases = new Vector3f[SwingTrail.STEPS + 1];
            Vector3f[] tips = new Vector3f[SwingTrail.STEPS + 1];
            boolean mirrored = anim.has(CombatState.MIRRORED);
            for (int k = 0; k <= SwingTrail.STEPS; k++) {
                ThirdPersonPose pose = ThirdPersonPoses.release(anim.attack, weapon.grip(), mirrored, anim.combo, SwingTrail.progress(range, k));
                Matrix4f transform = ThirdPersonAnimator.weaponTransform(pose, left, axes, scale);
                bases[k] = transform.transformPosition(axes.point(0.45f));
                tips[k] = transform.transformPosition(axes.point(1));
            }
            SwingTrail.draw(poseStack, buffers, bases, tips, SwingTrail.color(anim), Math.min(1, TRAIL_OPACITY * SwingTrail.strength(anim) * range[2]));
        }
    }

    /** How far Minecraft raises the arm of a raised shield (radians about the shoulder). */
    private static final float VANILLA_BLOCK_X = -0.9424779f;

    /** The shield in the off hand, placed on the hand as Minecraft's item layer places it. */
    private void renderShield(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack shield,
                              float partialTick) {
        HumanoidArm arm = player.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;
        FighterAnim anim = ClientCombat.anim(player);
        float raise = anim != null ? anim.shieldRaise(partialTick) : 0;
        poseStack.pushPose();
        getParentModel().translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        if (raise > 0) {
            // The arm is raised higher than Minecraft raises it: the shield is turned back on the hand by as much,
            // so it stands upright again, and then turned the way a raised shield's model turns it.
            poseStack.mulPose(Axis.XP.rotation(raise * (ThirdPersonAnimator.SHIELD_UP_X - VANILLA_BLOCK_X)));
            poseStack.mulPoseMatrix(raisedShield(left, raise));
        }
        HeldModel.render(shield, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left,
                poseStack, buffers, light);
        poseStack.popPose();
    }

    /**
     * How a raised shield's model turns a shield on the hand, {@code raise} of the way: the change from the hand
     * transform of a shield at rest to that of a raised one (Minecraft's shield; Epic Knights copies it), applied to the
     * model at rest.
     */
    private static Matrix4f raisedShield(boolean left, float raise) {
        float mirror = left ? -1 : 1;
        Vector3f restT = left ? new Vector3f(-10, 6, 12) : new Vector3f(10, 6, -4);
        Vector3f blockT = left ? new Vector3f(-13.51f, 3, 5) : new Vector3f(3.51f, 11, -2);
        Quaternionf restR = new Quaternionf().rotationXYZ(0, mirror * 90 * Mth.DEG_TO_RAD, 0);
        Quaternionf blockR = new Quaternionf().rotationXYZ(45 * Mth.DEG_TO_RAD, mirror * 135 * Mth.DEG_TO_RAD, 0);
        Vector3f t = new Vector3f(restT).lerp(blockT, raise).div(16);
        Quaternionf r = new Quaternionf(restR).slerp(blockR, raise);
        return new Matrix4f().translation(t).rotate(r).rotate(new Quaternionf(restR).conjugate()).translate(new Vector3f(restT).div(-16));
    }

    /**
     * A little larger than the model of the weapon, two-handed weapons the most, but models that are already huge
     * (some mods draw a greatsword taller than the player) keep their size.
     */
    static float scale(WeaponStats weapon, HeldModel.Axes axes) {
        float maxLength = switch (weapon.grip()) {
            case ONE_HANDED -> MAX_LENGTH_ONE;
            case TWO_HANDED -> MAX_LENGTH_TWO;
            case POLEARM -> MAX_LENGTH_POLEARM;
        };
        float scale = weapon.size();
        if (axes.length() * scale > maxLength) {
            scale = Math.max(1, maxLength / axes.length());
        }
        return scale * CombatConfig.WEAPON_SCALE.get().floatValue();
    }
}
