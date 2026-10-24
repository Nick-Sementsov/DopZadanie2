package io.github.nicksementsov.chessmod.style;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;

/**
 * A look for a chess set: the board's squares and the material of the pieces, both in the world and
 * in the board screen. Stored as a block state property, so a broken board or statue keeps it.
 */
public enum ChessStyle implements StringRepresentable {
    /** Maple and walnut board, ivory and ebony pieces. */
    CLASSIC,
    /** White and green-grey marble, polished marble pieces. */
    MARBLE,
    /** The cream and green board of chess clubs and websites, plain plastic pieces. */
    TOURNAMENT,
    /** Crimson wood and blackstone, gold against netherite. */
    NETHER,
    /** Snow and blue ice, crystal against lapis. */
    FROST,
    /** End stone and purpur, pearl against obsidian. */
    END;

    public static final EnumProperty<ChessStyle> PROPERTY = EnumProperty.create("style", ChessStyle.class);

    private static final ChessStyle[] VALUES = values();

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("style.chessmod." + getSerializedName());
    }

    public ChessStyle next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static ChessStyle byName(String name, ChessStyle fallback) {
        for (ChessStyle style : VALUES) {
            if (style.getSerializedName().equals(name)) {
                return style;
            }
        }
        return fallback;
    }

    /** The style an item will place, read from the block state tag a broken block leaves on its drop. */
    public static ChessStyle fromStack(ItemStack stack) {
        CompoundTag state = stack.getTagElement(BlockItem.BLOCK_STATE_TAG);
        return state == null ? CLASSIC : byName(state.getString(PROPERTY.getName()), CLASSIC);
    }

    /** An item that places its block in the given style. */
    public ItemStack applyTo(ItemStack stack) {
        if (this != CLASSIC) {
            stack.getOrCreateTagElement(BlockItem.BLOCK_STATE_TAG).putString(PROPERTY.getName(), getSerializedName());
        }
        return stack;
    }
}
