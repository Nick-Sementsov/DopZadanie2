package io.github.nicksementsov.knightcombat.registry;

import io.github.nicksementsov.knightcombat.KnightCombat;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Particles of the fights. Clients make them where blows land (see {@code client.CombatParticles}); the textures come
 * from a free particle pack, prepared by tools/import_particles.py.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, KnightCombat.MODID);

    /** A glowing spark flying off steel. */
    public static final RegistryObject<SimpleParticleType> SPARK = register("spark");
    /** A bright star where blades meet. */
    public static final RegistryObject<SimpleParticleType> FLASH = register("flash");
    /** A drop of blood, which leaves a stain where it falls. */
    public static final RegistryObject<SimpleParticleType> BLOOD = register("blood");
    /** A splash of blood where a blow lands. */
    public static final RegistryObject<SimpleParticleType> BLOOD_SPLASH = register("blood_splash");
    /** A puff of dust kicked up from the ground. */
    public static final RegistryObject<SimpleParticleType> DUST = register("dust");
    /** A ring of dust spreading over the ground. */
    public static final RegistryObject<SimpleParticleType> RING = register("ring");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
