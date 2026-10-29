package com.nick.prestol.trade;

import com.nick.prestol.util.Msg;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The merchant who leads a caravan or steers a ship: a man in a fur-trimmed coat, not a villager. He does not
 * wander or trade with passers-by: he goes where his route leads. He lives only while players are near and is
 * never saved with the chunk; the trader's record keeps everything else.
 */
public class MerchantEntity extends PathfinderMob {
   public static final int VARIANTS = 4;
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(MerchantEntity.class, EntityDataSerializers.INT);
   UUID trader;

   public MerchantEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setPersistenceRequired();
   }

   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(VARIANT, 0);
   }

   public int variant() {
      return this.entityData.get(VARIANT);
   }

   public void setVariant(int v) {
      this.entityData.set(VARIANT, Math.floorMod(v, VARIANTS));
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
   }

   public boolean shouldBeSaved() {
      return false;
   }

   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.PLAYER_HURT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.PLAYER_DEATH;
   }

   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND && this.level() instanceof ServerLevel level) {
         Trader t = this.trader == null ? null : TradeData.get(level.getServer()).traders.get(this.trader);
         if (t != null) {
            player.sendSystemMessage(TradeWorld.card(level.getServer(), t).withStyle(ChatFormatting.GOLD));
         } else {
            player.sendSystemMessage(Msg.info("trade.stranger"));
         }
      }

      return InteractionResult.sidedSuccess(this.level().isClientSide);
   }

   public void die(DamageSource source) {
      super.die(source);
      if (this.level() instanceof ServerLevel level && this.trader != null) {
         TradeWorld.onMerchantKilled(level, this, source);
      }
   }
}
