package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import io.github.nicksementsov.knightcombat.entity.ThrownWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a thrown weapon with its own model, as large as in the hand: spinning end over end or flying point first,
 * stuck into a wall (quivering for a moment after it hits) or lying flat where it fell.
 */
public class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon> {
    private static final ItemDisplayContext CONTEXT = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    /** How deep a stuck weapon is in the block, in blocks. */
    private static final float DEPTH = 0.2f;

    public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.15f;
        shadowStrength = 0.6f;
    }

    @Override
    public void render(@NotNull ThrownWeapon entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffers, int light) {
        ItemStack stack = entity.getWeapon();
        if (stack.isEmpty()) {
            return;
        }
        HeldModel.Axes axes = HeldModel.axes(stack, CONTEXT);
        WeaponStats weapon = Weapons.of(stack);
        float scale = weapon != null ? WeaponLayer.scale(weapon, axes) : 1;
        // The flight direction; projectiles keep yRot = atan2(x, z) and xRot = atan2(y, horizontal).
        float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) * Mth.DEG_TO_RAD;
        float xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;
        Vector3f flight = new Vector3f(Mth.sin(yRot) * Mth.cos(xRot), Mth.sin(xRot), Mth.cos(yRot) * Mth.cos(xRot));
        Vector3f side = new Vector3f(Mth.cos(yRot), 0, -Mth.sin(yRot));
        Vector3f up = new Vector3f(flight).cross(side).normalize();

        Vector3f blade;
        Vector3f edge;
        float pivot;
        Vector3f shift = new Vector3f();
        byte rest = entity.getRest();
        if (rest == ThrownWeapon.STUCK) {
            // Point (or head) first in the block, quivering for a moment.
            float shake = entity.shakeTime - partialTick;
            float wobble = shake > 0 ? -Mth.sin(shake * 3) * shake * 1.5f * Mth.DEG_TO_RAD : 0;
            blade = new Vector3f(flight).rotateAxis(wobble, side.x, side.y, side.z);
            edge = new Vector3f(up).rotateAxis(wobble, side.x, side.y, side.z);
            pivot = 1;
            shift.set(flight).mul(DEPTH);
        } else if (rest == ThrownWeapon.LYING) {
            // Flat on the ground along the way it came.
            blade = new Vector3f(flight.x, 0, flight.z);
            if (blade.lengthSquared() < 1.0E-4f) {
                blade.set(side.z, 0, -side.x);
            }
            blade.normalize();
            edge = new Vector3f(blade.z, 0, -blade.x);
            pivot = 0.5f;
            shift.set(0, 0.03f, 0);
        } else if (entity.getSpin() == 0) {
            blade = flight;
            edge = up;
            pivot = 0.6f;
        } else {
            // End over end: the tip goes forward over the top and the edge leads.
            float angle = (entity.tickCount + partialTick) * entity.getSpin() * Mth.DEG_TO_RAD;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            blade = new Vector3f(up).mul(cos).fma(sin, flight);
            edge = new Vector3f(flight).mul(cos).fma(-sin, up);
            pivot = 0.5f;
        }
        Quaternionf rotation = PoseMath.frame(axes.blade(), axes.edge(), blade, edge);
        Vector3f anchor = rotation.transform(axes.point(pivot)).mul(scale);

        poseStack.pushPose();
        poseStack.translate(shift.x - anchor.x, shift.y - anchor.y, shift.z - anchor.z);
        poseStack.mulPose(rotation);
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(null, stack, CONTEXT, false, poseStack, buffers, entity.level(),
                light, OverlayTexture.NO_OVERLAY, entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ThrownWeapon entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
