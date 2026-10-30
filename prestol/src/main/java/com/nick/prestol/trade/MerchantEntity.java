package com.nick.prestol.trade;

import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

/**
 * The merchant who leads a caravan or steers a ship: a man in a fur-trimmed coat, not a villager. He does not
 * wander: he goes where his route leads, but a passer-by may stop him and haggle (see {@link Haggle}). He lives
 * only while players are near and is never saved with the chunk; the trader's record keeps everything else.
 */
public class MerchantEntity extends PathfinderMob implements Merchant {
   public static final int VARIANTS = 4;
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(MerchantEntity.class, EntityDataSerializers.INT);
   UUID trader;
   private Player customer;
   private MerchantOffers offers = new MerchantOffers();
   private List<Haggle.Deal> deals = new ArrayList<>();

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
      if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND && this.level() instanceof ServerLevel level && player instanceof ServerPlayer sp) {
         Trader t = this.trader == null ? null : TradeData.get(level.getServer()).traders.get(this.trader);
         if (t == null) {
            player.sendSystemMessage(Msg.info("trade.stranger"));
         } else if (player.isShiftKeyDown()) {
            player.sendSystemMessage(TradeWorld.card(level.getServer(), t).withStyle(ChatFormatting.GOLD));
         } else if (this.customer != null && this.customer != player) {
            player.sendSystemMessage(Msg.info("trade.haggle_busy"));
         } else {
            String no = Haggle.refusal(level.getServer(), t, sp);
            this.deals = no == null ? Haggle.deals(level.getServer(), t) : List.of();
            if (no != null || this.deals.isEmpty()) {
               player.sendSystemMessage(TradeWorld.card(level.getServer(), t).withStyle(ChatFormatting.GOLD));
               player.sendSystemMessage(Msg.info(no == null ? "trade.haggle_nothing" : no));
            } else {
               this.offers = Haggle.offers(level.getServer(), t, this.deals);
               this.getNavigation().stop();
               this.setTradingPlayer(player);
               this.openTradingScreen(player, this.getDisplayName(), 0);
            }
         }
      }

      return InteractionResult.sidedSuccess(this.level().isClientSide);
   }

   // ---- the trading window ----

   public void setTradingPlayer(Player player) {
      this.customer = player;
   }

   public Player getTradingPlayer() {
      return this.customer;
   }

   public MerchantOffers getOffers() {
      return this.offers;
   }

   public void overrideOffers(MerchantOffers offers) {
      this.offers = offers;
   }

   public void notifyTrade(MerchantOffer offer) {
      int i = this.offers.indexOf(offer);
      if (i >= 0 && i < this.deals.size() && this.level() instanceof ServerLevel level) {
         Trader t = this.trader == null ? null : TradeData.get(level.getServer()).traders.get(this.trader);
         if (t != null) {
            Haggle.done(level.getServer(), t, this.deals.get(i), this.customer == null ? "?" : this.customer.getGameProfile().getName());
         }
      }

      this.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
   }

   public void notifyTradeUpdated(ItemStack stack) {
   }

   public int getVillagerXp() {
      return 0;
   }

   public void overrideXp(int xp) {
   }

   public boolean showProgressBar() {
      return false;
   }

   public SoundEvent getNotifyTradeSound() {
      return SoundEvents.ITEM_PICKUP;
   }

   public boolean isClientSide() {
      return this.level().isClientSide;
   }

   /** Whether someone is haggling with him: he stands still meanwhile. */
   public boolean haggling() {
      if (this.customer != null && (!this.customer.isAlive() || !(this.customer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu))) {
         this.customer = null;
      }

      return this.customer != null;
   }

   public void remove(RemovalReason reason) {
      if (this.customer != null && this.customer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu) {
         this.customer.closeContainer();
      }

      this.customer = null;
      super.remove(reason);
   }

   public void die(DamageSource source) {
      super.die(source);
      if (this.level() instanceof ServerLevel level && this.trader != null) {
         TradeWorld.onMerchantKilled(level, this, source);
      }
   }
}
