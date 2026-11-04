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
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        this.addLayer(new WeaponLayer<>(this));
    }

    @Override
    public void render(Soldier soldier, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // how the arms are held; Knightly Combat poses them over this while they fight
        PlayerModel<Soldier> model = this.getModel();
        model.crouching = soldier.isCrouching();
        model.rightArmPose = this.armPose(soldier, InteractionHand.MAIN_HAND);
        model.leftArmPose = this.armPose(soldier, InteractionHand.OFF_HAND);
        super.render(soldier, yaw, partialTick, pose, buffers, light);
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

    @Override
    protected void scale(Soldier soldier, PoseStack pose, float partialTick) {
        // the player model is drawn a little smaller than a player
        pose.scale(0.9375f, 0.9375f, 0.9375f);
    }
}
