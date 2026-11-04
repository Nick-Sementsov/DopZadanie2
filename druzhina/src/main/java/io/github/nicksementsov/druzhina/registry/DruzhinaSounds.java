package io.github.nicksementsov.druzhina.registry;

import io.github.nicksementsov.druzhina.Druzhina;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DruzhinaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Druzhina.MODID);

    public static final RegistryObject<SoundEvent> ELEPHANT_AMBIENT = register("entity.war_elephant.ambient");
    public static final RegistryObject<SoundEvent> ELEPHANT_ATTACK = register("entity.war_elephant.attack");
    public static final RegistryObject<SoundEvent> ELEPHANT_HURT = register("entity.war_elephant.hurt");
    public static final RegistryObject<SoundEvent> ELEPHANT_DEATH = register("entity.war_elephant.death");

    private DruzhinaSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Druzhina.id(name)));
    }
}
