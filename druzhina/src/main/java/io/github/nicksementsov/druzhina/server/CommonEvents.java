package io.github.nicksementsov.druzhina.server;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.compat.Vessels;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Druzhina.MODID)
public final class CommonEvents {
    private static final String BOARD_ORDERED = "druzhina.board_ordered";

    private CommonEvents() {
    }

    /** A soldier who kills someone counts it toward his next rank. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSource().getEntity() instanceof Soldier s && s != event.getEntity()) {
            s.scoredKill(event.getEntity());
        }
    }

    /** Arrows fly past friends: an archer behind the line does not shoot his own men in the back. */
    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit) {
            Entity owner = event.getProjectile().getOwner();
            Entity struck = hit.getEntity();
            if (owner != null && struck != owner && (owner instanceof Soldier || struck instanceof Soldier)
                    && (owner.isAlliedTo(struck) || struck.isAlliedTo(owner))) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            }
        }
    }

    /** The baton on a ship (or a piece of her hull): the men go aboard her instead of the commander. */
    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        batonOnShip(event, event.getTarget());
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        batonOnShip(event, event.getTarget());
    }

    private static void batonOnShip(PlayerInteractEvent event, Entity target) {
        if (!event.getItemStack().is(DruzhinaRegistry.BATON.get())) {
            return;
        }
        Entity ship = Vessels.resolve(target);
        if (!Vessels.isVessel(ship)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && player.level().getGameTime() != player.getPersistentData().getLong(BOARD_ORDERED)) {
            // once a click (the click on a hull piece is both kinds of interaction)
            player.getPersistentData().putLong(BOARD_ORDERED, player.level().getGameTime());
            Army.board(player, ship);
        }
    }

    /** A new world: soldiers are let at the helm of Small Ships' ships again. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Vessels.resetHelm();
        Vessels.allowSoldiersAtHelm();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        DruzhinaCommands.register(event.getDispatcher());
    }
}
