package com.nick.prestol.client;

import com.nick.prestol.trade.MerchantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Merchants are drawn as men, on the player model, in one of Prestol's merchant skins. */
public class MerchantRenderer extends HumanoidMobRenderer<MerchantEntity, PlayerModel<MerchantEntity>> {
   private static final ResourceLocation[] SKINS = new ResourceLocation[MerchantEntity.VARIANTS];

   static {
      for (int i = 0; i < SKINS.length; i++) {
         SKINS[i] = new ResourceLocation("prestol", "textures/entity/merchant_" + i + ".png");
      }
   }

   public MerchantRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
      this.addLayer(new HumanoidArmorLayer<>(
         this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()
      ));
   }

   public ResourceLocation getTextureLocation(MerchantEntity merchant) {
      return SKINS[Math.floorMod(merchant.variant(), SKINS.length)];
   }
}
