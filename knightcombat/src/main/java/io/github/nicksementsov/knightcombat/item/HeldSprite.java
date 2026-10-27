package io.github.nicksementsov.knightcombat.item;

/**
 * Texture of a weapon as it is held: its size in pixels and the pixel the hand holds (from the top left corner).
 * The blade or shaft of every weapon texture goes from the bottom left corner to the top right corner.
 */
public record HeldSprite(int size, float gripX, float gripY) {
    /** Swords and axes from Minecraft use 16x16 textures held near the guard. */
    public static final HeldSprite VANILLA = new HeldSprite(16, 3.0f, 13.0f);
}
