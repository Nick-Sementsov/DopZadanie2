package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import net.minecraft.resources.ResourceLocation;

/**
 * The one texture of the siege engines (256x256), made by {@code tools/siege_texture.py}: a field of each material,
 * from whose corner the boxes of that material take their faces. The places here must match the script.
 */
public final class SiegeTexture {
    public static final ResourceLocation LOCATION = Druzhina.id("textures/entity/siege.png");
    public static final int W = 256;
    public static final int H = 256;
    /** Planks and beams, the grain along them: 256x64. */
    public static final int WOOD_U = 0;
    public static final int WOOD_V = 0;
    /** Upright posts, the grain up and down: 96x96. */
    public static final int POST_U = 0;
    public static final int POST_V = 64;
    /** Old tarred wood (axles, the ballista's arms, bolts): 96x64. */
    public static final int DARK_U = 96;
    public static final int DARK_V = 64;
    /** Iron: 64x48. */
    public static final int IRON_U = 192;
    public static final int IRON_V = 64;
    /** Rope and sinew: 64x48. */
    public static final int ROPE_U = 192;
    public static final int ROPE_V = 112;
    /** Hides over the ram's roof: 128x96. */
    public static final int HIDE_U = 0;
    public static final int HIDE_V = 160;
    /** A big wheel (a box 2x10x10) and a small one (2x8x8). */
    public static final int WHEEL_U = 128;
    public static final int WHEEL_V = 160;
    public static final int SMALL_WHEEL_U = 128;
    public static final int SMALL_WHEEL_V = 192;
    /** The stone in the trebuchet's sling (a box 5x5x5). */
    public static final int STONE_U = 192;
    public static final int STONE_V = 160;
    /** The trebuchet's box of stones (12x11x10). */
    public static final int WEIGHT_U = 128;
    public static final int WEIGHT_V = 224;

    private SiegeTexture() {
    }
}
