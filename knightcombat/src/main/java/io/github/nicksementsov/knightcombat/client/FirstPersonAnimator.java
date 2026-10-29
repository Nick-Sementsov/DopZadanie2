package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Grip;
import io.github.nicksementsov.knightcombat.combat.Lances;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.Shields;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the weapon in the first person view at the place {@link FirstPersonPoses} gives for the current phase,
 * held by the player's arms: one arm for one-handed weapons, both for two-handed weapons and polearms.
 */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class FirstPersonAnimator {
    /** Size of weapons in the first person view compared to the hands of other players, like Minecraft's 0.68 to 0.85. */
    private static final float VIEW_SCALE = 0.95f;
    /** Longest weapon shown at full size in the first person view, in blocks from the hand to the tip. */
    private static final float MAX_LENGTH_ONE = 1.15f;
    private static final float MAX_LENGTH_TWO = 1.5f;
    private static final float MAX_LENGTH_POLEARM = 2.3f;
    private static final float MAX_LENGTH_LANCE = 4.5f;
    /** Where the arms come from, beyond the lower corner of the view (right arm; the left one is mirrored). */
    private static final Vector3f SHOULDER = new Vector3f(0.95f, -0.95f, 0.1f);
    /** The arms are drawn a little slimmer than the model's, so they frame the view without filling it. */
    private static final float ARM_WIDTH = 0.85f;
    private static final float TRAIL_OPACITY = 0.45f;
    /** How long a hit shakes the weapon, in ticks. */
    private static final float IMPACT_TICKS = 4;

    private FirstPersonAnimator() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        // A raised shield is drawn by Minecraft; the weapon stays ready beside it.
        if (player == null || player.isUsingItem() && !Shields.raisedInOffHand(player) || player.isSpectator()) {
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND && !player.isUsingItem() && Shields.isShield(event.getItemStack())
                && renderShieldBash(event, player)) {
            return;
        }
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        boolean lance = weapon == null && CombatConfig.mountedCharges() && Lances.isLance(player.getMainHandItem());
        if (lance) {
            weapon = Lances.RENDER;
        }
        if (weapon == null || !HeldModel.measurable(player.getMainHandItem())) {
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            // Both hands hold a two-handed weapon, so nothing else is shown in the off hand.
            if (weapon.twoHanded()) {
                event.setCanceled(true);
            }
            return;
        }
        event.setCanceled(true);
        ItemStack stack = event.getItemStack();
        float partialTick = event.getPartialTick();
        FighterAnim anim = ClientCombat.anim(player);
        FirstPersonPose pose = FirstPersonPoses.current(anim, weapon.grip(), lance, partialTick);
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        HeldModel.Axes axes = HeldModel.axes(stack, context);

        Vector3f hand = new Vector3f(pose.hand);
        Vector3f blade = new Vector3f(pose.blade);
        Vector3f edge = new Vector3f(pose.edge);
        hand.y -= 0.6f * event.getEquipProgress();
        impact(hand, blade, ClientCombat.ticks() - ClientCombat.hitAt() + partialTick);
        weight(hand, blade, player, anim, weapon, partialTick);
        adjust(hand, blade, edge, weapon, left);
        float scale = scale(weapon, axes);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        if (CombatConfig.FIRST_PERSON_ARMS.get()) {
            renderArm(poseStack, buffers, light, player, !left, hand, blade);
            boolean couched = anim != null && (anim.phase() == Phase.COUCH || anim.phase() == Phase.RECOVERY && anim.has(CombatState.LANCE));
            if (weapon.twoHanded() && !couched) {
                float along = (weapon.grip() == Grip.POLEARM ? 0.42f : -0.17f) * scale;
                Vector3f offHand = new Vector3f(blade).mul(along).add(hand);
                renderArm(poseStack, buffers, light, player, left, offHand, blade);
            }
        }

        poseStack.pushPose();
        poseStack.translate(hand.x, hand.y, hand.z);
        poseStack.mulPose(PoseMath.frame(axes.blade(), axes.edge(), blade, edge));
        poseStack.scale(scale, scale, scale);
        HeldModel.render(stack, context, left, poseStack, buffers, light);
        poseStack.popPose();

        float[] range = CombatConfig.SWING_TRAILS.get() ? SwingTrail.range(anim, partialTick) : null;
        if (range != null) {
            Vector3f[] bases = new Vector3f[SwingTrail.STEPS + 1];
            Vector3f[] tips = new Vector3f[SwingTrail.STEPS + 1];
            boolean mirrored = anim.has(CombatState.MIRRORED);
            for (int k = 0; k <= SwingTrail.STEPS; k++) {
                FirstPersonPose step = FirstPersonPoses.release(anim.attack, weapon.grip(), mirrored, anim.combo, SwingTrail.progress(range, k));
                Vector3f stepHand = new Vector3f(step.hand);
                Vector3f stepBlade = new Vector3f(step.blade);
                Vector3f stepEdge = new Vector3f(step.edge);
                adjust(stepHand, stepBlade, stepEdge, weapon, left);
                Matrix4f transform = new Matrix4f().translation(stepHand)
                        .rotate(PoseMath.frame(axes.blade(), axes.edge(), stepBlade, stepEdge)).scale(scale);
                bases[k] = transform.transformPosition(axes.point(0.4f));
                tips[k] = transform.transformPosition(axes.point(1));
            }
            SwingTrail.draw(poseStack, buffers, bases, tips, SwingTrail.color(anim), Math.min(1, TRAIL_OPACITY * SwingTrail.strength(anim) * range[2]));
        }
    }

    /**
     * Draws the shield in the off hand thrust forward and across the view during a bash, where Minecraft would
     * draw it at its usual place. Returns false (and draws nothing) when the player is not bashing.
     */
    private static boolean renderShieldBash(RenderHandEvent event, LocalPlayer player) {
        float bash = ThirdPersonPoses.bash(ClientCombat.anim(player), event.getPartialTick());
        if (bash == 0) {
            return false;
        }
        event.setCanceled(true);
        HumanoidArm arm = player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // Where Minecraft holds an item in this hand, moved in towards the middle, up and forward.
        poseStack.translate(side * (0.56f - 0.3f * bash), -0.52f - 0.6f * event.getEquipProgress() + 0.12f * bash, -0.72f - 0.3f * bash);
        poseStack.mulPose(Axis.YP.rotationDegrees(-side * 20 * bash));
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, event.getItemStack(),
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !right,
                poseStack, event.getMultiBufferSource(), event.getPackedLight());
        poseStack.popPose();
        return true;
    }

    /** Places a pose for the weapon: long weapons a little further away, and everything mirrored for left-handed players. */
    private static void adjust(Vector3f hand, Vector3f blade, Vector3f edge, WeaponStats weapon, boolean left) {
        if (weapon.twoHanded()) {
            // Long weapons are held further from the eyes and lower, so their hilts do not fill the view.
            hand.z -= 0.1f;
            hand.y -= 0.04f;
        }
        if (left) {
            hand.x = -hand.x;
            blade.x = -blade.x;
            edge.x = -edge.x;
        }
    }

    /** As large as for other players, but long weapons are shortened a little so they do not fill the view. */
    private static float scale(WeaponStats weapon, HeldModel.Axes axes) {
        float maxLength = weapon == Lances.RENDER ? MAX_LENGTH_LANCE : switch (weapon.grip()) {
            case ONE_HANDED -> MAX_LENGTH_ONE;
            case TWO_HANDED -> MAX_LENGTH_TWO;
            case POLEARM -> MAX_LENGTH_POLEARM;
        };
        float scale = weapon.size() * VIEW_SCALE;
        if (axes.length() * scale > maxLength) {
            scale = maxLength / axes.length();
        }
        return scale * CombatConfig.WEAPON_SCALE.get().floatValue();
    }

    /**
     * The weight of the weapon in the hands: it lags behind turns of the view, trembles at the end of a heavy
     * wind-up, and is knocked down and aside by blows that land on the player or on their guard.
     */
    private static void weight(Vector3f hand, Vector3f blade, LocalPlayer player, FighterAnim anim, WeaponStats weapon, float partialTick) {
        float weight = weapon.weight();
        float yawLag = Mth.wrapDegrees(player.getViewYRot(partialTick) - Mth.lerp(partialTick, player.yBobO, player.yBob));
        float pitchLag = player.getViewXRot(partialTick) - Mth.lerp(partialTick, player.xBobO, player.xBob);
        float lag = 0.0025f * (0.6f + weight);
        hand.x -= Mth.clamp(yawLag * lag, -0.08f, 0.08f);
        hand.y += Mth.clamp(pitchLag * lag, -0.06f, 0.06f);
        if (anim == null) {
            return;
        }
        if (anim.phase() == Phase.WINDUP && anim.attack() != AttackType.KICK) {
            // The end of a heavy weapon's wind-up trembles a little; the charge of a heavy attack trembles harder as it grows.
            float tension = anim.has(CombatState.HEAVY)
                    ? (0.6f + 1.4f * anim.progress(partialTick)) * (0.5f + weight)
                    : Mth.clamp((anim.progress(partialTick) - 0.7f) / 0.3f, 0, 1) * weight;
            float time = ClientCombat.ticks() + partialTick;
            hand.add(0.005f * tension * Mth.sin(time * 5.1f), 0.005f * tension * Mth.sin(time * 6.7f + 1), 0);
        }
        float flinch = anim.flinch(partialTick);
        if (flinch != 0) {
            float side = anim.hurtSide;
            hand.add(-side * 0.05f * flinch, -0.07f * flinch, 0.05f * flinch);
            blade.add(-side * 0.18f * flinch, -0.12f * flinch, 0).normalize();
        }
    }

    /** A blow that lands jolts the weapon: it stops for a moment and shakes. */
    private static void impact(Vector3f hand, Vector3f blade, float age) {
        if (age < 0 || age >= IMPACT_TICKS) {
            return;
        }
        float strength = 1 - age / IMPACT_TICKS;
        float wobble = (float) Math.sin(age * 9) * strength;
        hand.add(0.015f * wobble, -0.02f * strength, 0.02f * strength);
        blade.add(0, 0.08f * wobble, 0).normalize();
    }

    /**
     * Draws an arm of the player whose hand holds the weapon at {@code hand}, coming from below the view.
     * The fist faces along the blade, as it does when the player is seen from outside.
     */
    private static void renderArm(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                  boolean right, Vector3f hand, Vector3f blade) {
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        Vector3f shoulder = new Vector3f(right ? SHOULDER.x : -SHOULDER.x, SHOULDER.y, SHOULDER.z);
        Quaternionf rotation = PoseMath.armFrame(new Vector3f(hand).sub(shoulder).normalize(), blade);
        Vector3f grip = right ? PoseMath.RIGHT_GRIP : PoseMath.LEFT_GRIP;

        poseStack.pushPose();
        poseStack.translate(hand.x, hand.y, hand.z);
        poseStack.mulPose(rotation);
        poseStack.scale(ARM_WIDTH, 1, ARM_WIDTH);
        poseStack.translate(-grip.x / 16f, -grip.y / 16f, -grip.z / 16f);
        PartPose armPose = arm.storePose();
        PartPose sleevePose = sleeve.storePose();
        boolean armVisible = arm.visible;
        boolean sleeveVisible = sleeve.visible;
        arm.loadPose(PartPose.ZERO);
        sleeve.loadPose(PartPose.ZERO);
        arm.visible = true;
        sleeve.visible = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        ResourceLocation skin = player.getSkinTextureLocation();
        arm.render(poseStack, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        sleeve.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        arm.loadPose(armPose);
        sleeve.loadPose(sleevePose);
        arm.visible = armVisible;
        sleeve.visible = sleeveVisible;
        poseStack.popPose();
    }
}
