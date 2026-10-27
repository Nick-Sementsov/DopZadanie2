package io.github.nicksementsov.knightcombat.registry;

import io.github.nicksementsov.knightcombat.KnightCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Sounds of the fights. The sound files are Minecraft's own, mixed and pitched in sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, KnightCombat.MODID);

    public static final RegistryObject<SoundEvent> WHOOSH = register("weapon.whoosh");
    public static final RegistryObject<SoundEvent> HIT = register("weapon.hit");
    public static final RegistryObject<SoundEvent> BLOCK = register("weapon.block");
    public static final RegistryObject<SoundEvent> PARRY = register("weapon.parry");
    public static final RegistryObject<SoundEvent> GUARD_BREAK = register("weapon.guard_break");
    public static final RegistryObject<SoundEvent> WALL = register("weapon.wall");
    public static final RegistryObject<SoundEvent> FEINT = register("weapon.feint");
    public static final RegistryObject<SoundEvent> KICK = register("combat.kick");
    public static final RegistryObject<SoundEvent> BATTLE_CRY = register("combat.battle_cry");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(KnightCombat.id(name)));
    }
}
