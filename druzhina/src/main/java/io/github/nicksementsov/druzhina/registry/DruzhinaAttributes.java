package io.github.nicksementsov.druzhina.registry;

import io.github.nicksementsov.druzhina.Druzhina;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DruzhinaAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Druzhina.MODID);

    /** Damage added to every arrow and bolt a soldier shoots. */
    public static final RegistryObject<Attribute> RANGED_DAMAGE = ATTRIBUTES.register("ranged_damage",
            () -> new RangedAttribute("attribute.druzhina.ranged_damage", 0.0, -10.0, 20.0).setSyncable(true));

    private DruzhinaAttributes() {
    }
}
