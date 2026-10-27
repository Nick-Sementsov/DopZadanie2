package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import io.github.nicksementsov.knightcombat.item.HeldSprite;
import io.github.nicksementsov.knightcombat.item.KnightWeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Draws the weapon in the first person view at the place {@link FirstPersonPoses} gives for the current phase. */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class FirstPersonAnimator {
    /** Size of a 16 pixel item in the hand, the same as Minecraft's own first person scale. */
    private static final float SCALE_16 = 0.68f;
    /** Long weapons are drawn a little smaller in the first person view than in the hands of other players. */
    private static final float LONG_FACTOR = 0.7f;

    private FirstPersonAnimator() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isUsingItem() || player.isSpectator()) {
            return;
        }
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        if (weapon == null) {
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
        FirstPersonPose pose = FirstPersonPoses.current(ClientCombat.anim(player), weapon.grip(), event.getPartialTick());
        HeldSprite sprite = stack.getItem() instanceof KnightWeaponItem item ? item.heldSprite() : HeldSprite.VANILLA;
        boolean left = player.getMainArm() == HumanoidArm.LEFT;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Vector3f hand = new Vector3f(pose.hand);
        Vector3f blade = new Vector3f(pose.blade);
        Vector3f flat = new Vector3f(pose.flat);
        if (weapon.twoHanded()) {
            // Long weapons are held further from the eyes and lower, so their hilts do not fill the view.
            hand.z -= 0.3f;
            hand.y -= 0.08f;
        }
        if (left) {
            hand.x = -hand.x;
            blade.x = -blade.x;
            flat.x = -flat.x;
        }
        poseStack.translate(hand.x, hand.y - 0.6f * event.getEquipProgress(), hand.z);
        poseStack.mulPose(PoseMath.spriteRotation(blade, flat));
        float scale = SCALE_16 * sprite.size() / 16f * (sprite.size() > 16 ? LONG_FACTOR : 1);
        poseStack.scale(scale, scale, scale);
        // Move the texture so that the pixel held by the hand sits at the hand.
        poseStack.translate(0.5f - sprite.gripX() / sprite.size(), sprite.gripY() / sprite.size() - 0.5f, 0);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                ItemDisplayContext.NONE, false, poseStack, event.getMultiBufferSource(), event.getPackedLight());
        poseStack.popPose();
    }
}
