package io.github.nicksementsov.knightcombat.registry;

import io.github.nicksementsov.knightcombat.KnightCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Sounds of the fights: recordings from free sound libraries, prepared by tools/import_sounds.py. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, KnightCombat.MODID);

    public static final RegistryObject<SoundEvent> WHOOSH = register("weapon.whoosh");
    public static final RegistryObject<SoundEvent> WHOOSH_HEAVY = register("weapon.whoosh_heavy");
    public static final RegistryObject<SoundEvent> HIT = register("weapon.hit");
    public static final RegistryObject<SoundEvent> HIT_HEAVY = register("weapon.hit_heavy");
    public static final RegistryObject<SoundEvent> BLOCK = register("weapon.block");
    public static final RegistryObject<SoundEvent> PARRY = register("weapon.parry");
    public static final RegistryObject<SoundEvent> GUARD_BREAK = register("weapon.guard_break");
    public static final RegistryObject<SoundEvent> WALL = register("weapon.wall");
    public static final RegistryObject<SoundEvent> FEINT = register("weapon.feint");
    public static final RegistryObject<SoundEvent> KICK = register("combat.kick");
    public static final RegistryObject<SoundEvent> BATTLE_CRY = register("combat.battle_cry");
    public static final RegistryObject<SoundEvent> ATTACK_YELL = register("combat.attack_yell");
    public static final RegistryObject<SoundEvent> PAIN = register("combat.pain");
    public static final RegistryObject<SoundEvent> HIT_ARMOR = register("weapon.hit_armor");
    public static final RegistryObject<SoundEvent> DRAW = register("weapon.draw");
    public static final RegistryObject<SoundEvent> THROW = register("weapon.throw");
    public static final RegistryObject<SoundEvent> SPIN = register("weapon.spin");
    public static final RegistryObject<SoundEvent> STICK = register("weapon.stick");
    public static final RegistryObject<SoundEvent> CLATTER = register("weapon.clatter");
    public static final RegistryObject<SoundEvent> DODGE = register("combat.dodge");
    public static final RegistryObject<SoundEvent> EFFORT = register("combat.effort");
    public static final RegistryObject<SoundEvent> BODY_FALL = register("combat.body_fall");
    public static final RegistryObject<SoundEvent> SHIELD_BLOCK = register("weapon.shield_block");
    public static final RegistryObject<SoundEvent> SHIELD_BASH = register("combat.shield_bash");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(KnightCombat.id(name)));
    }
}
