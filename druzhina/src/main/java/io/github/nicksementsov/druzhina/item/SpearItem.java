package io.github.nicksementsov.druzhina.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraftforge.common.ForgeMod;

/** A plain spear: a long reach and a strong thrust. Knightly Combat fights with it as with a polearm. */
public class SpearItem extends SwordItem {
    private static final UUID REACH = UUID.fromString("5b1f1c1e-4a5b-4f4e-9d1a-6c1d3a2b7e01");
    private Multimap<Attribute, AttributeModifier> modifiers;

    public SpearItem(Tier tier, Properties properties) {
        super(tier, 2, -2.9f, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getDefaultAttributeModifiers(slot);
        }
        if (this.modifiers == null) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(super.getDefaultAttributeModifiers(slot));
            b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH, "Spear reach", 1.0, AttributeModifier.Operation.ADDITION));
            this.modifiers = b.build();
        }
        return this.modifiers;
    }
}
