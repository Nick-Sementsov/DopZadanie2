package io.github.nicksementsov.druzhina.server;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.Soldier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Druzhina.MODID)
public final class CommonEvents {
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

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        DruzhinaCommands.register(event.getDispatcher());
    }
}
