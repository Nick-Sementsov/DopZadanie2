package com.nick.prestol.trade;

import com.nick.prestol.Prestol;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TradeEntities {
   private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Prestol.MODID);
   public static final RegistryObject<EntityType<MerchantEntity>> MERCHANT = TYPES.register(
      "merchant",
      () -> EntityType.Builder.<MerchantEntity>of(MerchantEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10).build("prestol:merchant")
   );

   private TradeEntities() {
   }

   public static void register(IEventBus modBus) {
      TYPES.register(modBus);
      modBus.addListener(TradeEntities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(MERCHANT.get(), Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.MAX_HEALTH, 30.0).build());
   }
}
