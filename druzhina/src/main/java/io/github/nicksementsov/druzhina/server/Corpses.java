package io.github.nicksementsov.druzhina.server;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.DruzhinaConfig;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import io.github.nicksementsov.druzhina.entity.siege.SiegeEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The fallen of a battle lie where they fell for a few minutes (the server's setting {@code corpseSeconds}, each body
 * a little longer or shorter) instead of vanishing at once: soldiers and bandits, war elephants, the horses of riders,
 * and whoever soldiers (or a commander with his men about him) slay. A body is no one any more: it is not counted,
 * not fought, not ridden; it only lies there, and at last is gone in a puff of dust as Minecraft's dead are.
 */
@Mod.EventBusSubscriber(modid = Druzhina.MODID)
public final class Corpses {
    /** Ticks a body has still to lie (-1: it does not lie, it goes as usual). */
    private static final String LEFT = "druzhina.corpse";

    private Corpses() {
    }

    /** How long the body of {@code e} lies, in ticks: the setting, give or take a quarter, the same on every side. */
    public static int ticks(Entity e) {
        int seconds;
        try {
            seconds = DruzhinaConfig.CORPSE_SECONDS.get();
        } catch (IllegalStateException notLoaded) {
            seconds = 240;
        }
        double spread = (e.getUUID().getLeastSignificantBits() >>> 1) % 1000 / 1000.0;
        return (int) (seconds * 20 * (0.75 + 0.5 * spread));
    }

    /** Whether {@code e}, fallen, lies on the field. */
    private static boolean lies(LivingEntity e) {
        if (e instanceof Player || e instanceof SiegeEngine || e instanceof WitherBoss || ticks(e) <= 0
                || e.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)) {
            return false;
        }
        net.minecraft.resources.ResourceLocation type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (type != null && type.getNamespace().equals("minecolonies")) {
            // a colony keeps its own count of its living and its dead: its citizens go as they always do
            return false;
        }
        if (e instanceof Soldier || e instanceof WarElephant) {
            return true;
        }
        for (Entity rider : e.getPassengers()) {
            if (rider instanceof Soldier) {
                // a rider's horse
                return true;
            }
        }
        LivingEntity killer = e.getKillCredit();
        if (killer == null) {
            killer = e.getLastHurtByMob();
        }
        if (killer instanceof Soldier || killer instanceof SiegeEngine || killer instanceof WarElephant) {
            return true;
        }
        return killer instanceof Player p && !Army.near(p).isEmpty();
    }

    /**
     * The fallen lie flat: their room is low, so that they do not stand in the way of a blow or a click aimed past
     * them (on both sides: the pose of the dying is shared). Soldiers and elephants are still drawn from as far as
     * the living (see their {@code shouldRenderAtSqrDistance}).
     */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (event.getPose() == Pose.DYING && event.getEntity() instanceof LivingEntity e && !(e instanceof Player) && !(e instanceof SiegeEngine)) {
            // no wider than it stood: a wider box would be lifted off the ground to fit
            EntityDimensions standing = event.getOldSize();
            event.setNewSize(EntityDimensions.scalable(standing.width, Math.min(standing.height, 0.3f)), true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !e.isDeadOrDying()) {
            return;
        }
        CompoundTag data = e.getPersistentData();
        int left;
        if (!data.contains(LEFT)) {
            left = lies(e) ? ticks(e) : -1;
            data.putInt(LEFT, left);
            if (left > 0) {
                // a body lies on the ground: not in a saddle, and nobody rides it
                e.ejectPassengers();
            }
        } else {
            left = data.getInt(LEFT);
        }
        if (left <= 0) {
            return;
        }
        if (e.getY() < e.level().getMinBuildHeight() - 32) {
            // fallen out of the world: nothing to lie on
            data.putInt(LEFT, 0);
            return;
        }
        data.putInt(LEFT, left - 1);
        if (e.getPose() != Pose.DYING) {
            // a body from a saved world: lying again
            e.setPose(Pose.DYING);
        }
        if (e.isPassenger()) {
            e.stopRiding();
        }
        // Minecraft takes the dead away when their count of ticks reaches 20: it is held short of that
        if (e.deathTime > 18) {
            e.deathTime = 18;
        }
    }
}
