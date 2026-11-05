package io.github.nicksementsov.knightcombat.registry;

import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.entity.ThrownWeapon;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod's only entity: a weapon in flight, stuck in a wall or lying where it fell. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, KnightCombat.MODID);

    public static final RegistryObject<EntityType<ThrownWeapon>> THROWN_WEAPON = ENTITIES.register("thrown_weapon",
            () -> EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(KnightCombat.id("thrown_weapon").toString()));

    private ModEntities() {
    }
}
