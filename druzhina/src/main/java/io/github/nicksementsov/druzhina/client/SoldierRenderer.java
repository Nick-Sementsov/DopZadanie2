package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.knightcombat.client.WeaponLayer;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/** Soldiers are men: the player model, their armour, and their weapons drawn by Knightly Combat. */
public class SoldierRenderer extends HumanoidMobRenderer<Soldier, PlayerModel<Soldier>> {
    private static final ResourceLocation[] SOLDIERS = new ResourceLocation[Soldier.SKINS];
    private static final ResourceLocation[] BANDITS = new ResourceLocation[Soldier.BANDIT_SKINS];

    static {
        for (int i = 0; i < SOLDIERS.length; i++) {
            SOLDIERS[i] = Druzhina.id("textures/entity/soldier/soldier_" + i + ".png");
        }
        for (int i = 0; i < BANDITS.length; i++) {
            BANDITS[i] = Druzhina.id("textures/entity/soldier/bandit_" + i + ".png");
        }
    }

    public SoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()) {
            @Override
            protected void setPartVisibility(HumanoidArmorModel<Soldier> model, net.minecraft.world.entity.EquipmentSlot slot) {
                super.setPartVisibility(model, slot);
                // no armour where the limb is gone
                hideCut(model, cut);
            }
        });
        this.addLayer(new WeaponLayer<>(this));
    }

    /** The parts cut off the soldier being drawn (see {@link Soldier#wounds()}). */
    private static int cut;

    @Override
    public void render(Soldier soldier, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // how the arms are held; Knightly Combat poses them over this while they fight
        PlayerModel<Soldier> model = this.getModel();
        model.crouching = soldier.isCrouching();
        model.rightArmPose = this.armPose(soldier, InteractionHand.MAIN_HAND);
        model.leftArmPose = this.armPose(soldier, InteractionHand.OFF_HAND);
        int wounds = soldier.isDeadOrDying() ? soldier.wounds() : 0;
        if (wounds == 0) {
            cut = 0;
            super.render(soldier, yaw, partialTick, pose, buffers, light);
            return;
        }
        // what the cut-off hands held is not drawn in the air
        boolean rightMain = soldier.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        ItemStack main = soldier.getMainHandItem();
        ItemStack off = soldier.getOffhandItem();
        boolean dropMain = (wounds & (rightMain ? Soldier.RIGHT_ARM : Soldier.LEFT_ARM)) != 0;
        boolean dropOff = (wounds & (rightMain ? Soldier.LEFT_ARM : Soldier.RIGHT_ARM)) != 0;
        if (dropMain) {
            soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (dropOff) {
            soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        cut = wounds;
        try {
            super.render(soldier, yaw, partialTick, pose, buffers, light);
            Severed.render(soldier, model, this.getTextureLocation(soldier), wounds, partialTick, pose, buffers, light);
        } finally {
            cut = 0;
            if (dropMain) {
                soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, main);
            }
            if (dropOff) {
                soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, off);
            }
        }
    }

    /** Hides on {@code model} the parts in {@code wounds}. */
    static void hideCut(HumanoidModel<?> model, int wounds) {
        if ((wounds & Soldier.HEAD) != 0) {
            model.head.visible = false;
            model.hat.visible = false;
        }
        if ((wounds & Soldier.RIGHT_ARM) != 0) {
            model.rightArm.visible = false;
        }
        if ((wounds & Soldier.LEFT_ARM) != 0) {
            model.leftArm.visible = false;
        }
        if ((wounds & Soldier.RIGHT_LEG) != 0) {
            model.rightLeg.visible = false;
        }
        if ((wounds & Soldier.LEFT_LEG) != 0) {
            model.leftLeg.visible = false;
        }
        if (model instanceof PlayerModel<?> p) {
            p.rightSleeve.visible &= model.rightArm.visible;
            p.leftSleeve.visible &= model.leftArm.visible;
            p.rightPants.visible &= model.rightLeg.visible;
            p.leftPants.visible &= model.leftLeg.visible;
        }
    }

    private HumanoidModel.ArmPose armPose(Soldier soldier, InteractionHand hand) {
        ItemStack stack = soldier.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return soldier.isUsingItem() && soldier.getUsedItemHand() == hand ? HumanoidModel.ArmPose.CROSSBOW_CHARGE : HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        if (soldier.isUsingItem() && soldier.getUsedItemHand() == hand) {
            if (stack.getItem() instanceof BowItem) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (stack.getItem() instanceof ShieldItem) {
                return HumanoidModel.ArmPose.BLOCK;
            }
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public ResourceLocation getTextureLocation(Soldier soldier) {
        ResourceLocation[] skins = soldier.isBandit() ? BANDITS : SOLDIERS;
        return skins[Math.floorMod(soldier.skin(), skins.length)];
    }

    /** The player model, whose limbs go slack when he falls. */
    static class Model extends PlayerModel<Soldier> {
        Model(net.minecraft.client.model.geom.ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(Soldier s, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(s, limbSwing, limbAmount, age, headYaw, headPitch);
            this.setAllVisible(true);
            float since = DeathPoses.since(s, net.minecraft.client.Minecraft.getInstance().getFrameTime());
            if (since < 0) {
                return;
            }
            hideCut(this, cut);
            float k = net.minecraft.util.Mth.clamp(since / DeathPoses.FALL, 0, 1);
            boolean back = DeathPoses.fall(s) == DeathPoses.Fall.BACK;
            // the arms fly up as he goes over, and fall spread on the ground; the legs give way
            float flail = net.minecraft.util.Mth.sin(k * net.minecraft.util.Mth.PI);
            this.rightArm.xRot = net.minecraft.util.Mth.lerp(k, this.rightArm.xRot, back ? -2.6f : -0.4f) - flail * 0.8f;
            this.leftArm.xRot = net.minecraft.util.Mth.lerp(k, this.leftArm.xRot, back ? -2.3f : -0.2f) - flail * 0.6f;
            this.rightArm.zRot = net.minecraft.util.Mth.lerp(k, this.rightArm.zRot, 0.55f);
            this.leftArm.zRot = net.minecraft.util.Mth.lerp(k, this.leftArm.zRot, -0.7f);
            this.rightArm.yRot = net.minecraft.util.Mth.lerp(k, this.rightArm.yRot, 0);
            this.leftArm.yRot = net.minecraft.util.Mth.lerp(k, this.leftArm.yRot, 0);
            this.rightLeg.xRot = net.minecraft.util.Mth.lerp(k, this.rightLeg.xRot, back ? -0.25f : 0.15f);
            this.leftLeg.xRot = net.minecraft.util.Mth.lerp(k, this.leftLeg.xRot, back ? 0.1f : -0.1f);
            // the knees give first: the legs fold under him, then straighten as he lies
            float kneel = net.minecraft.util.Mth.sin(net.minecraft.util.Mth.clamp(since / (DeathPoses.FALL * 0.8f), 0, 1) * net.minecraft.util.Mth.PI);
            this.rightLeg.xRot -= 0.9f * kneel;
            this.leftLeg.xRot -= 0.7f * kneel;
            this.rightLeg.zRot = net.minecraft.util.Mth.lerp(k, this.rightLeg.zRot, 0.18f);
            this.leftLeg.zRot = net.minecraft.util.Mth.lerp(k, this.leftLeg.zRot, -0.12f);
            this.head.xRot = net.minecraft.util.Mth.lerp(k, this.head.xRot, back ? -0.35f : 0.25f);
            this.head.yRot = net.minecraft.util.Mth.lerp(k, this.head.yRot, (s.getId() % 3 - 1) * 0.6f);
            this.hat.copyFrom(this.head);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftPants.copyFrom(this.leftLeg);
            this.rightPants.copyFrom(this.rightLeg);
        }
    }

    @Override
    protected void scale(Soldier soldier, PoseStack pose, float partialTick) {
        // the player model is drawn a little smaller than a player
        pose.scale(0.9375f, 0.9375f, 0.9375f);
    }
}
