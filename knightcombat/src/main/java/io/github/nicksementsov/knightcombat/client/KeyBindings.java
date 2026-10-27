package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Combat keys. The slash is the attack key and the block is the use key; these are the other actions. */
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.knightcombat";

    public static final KeyMapping OVERHEAD = key("overhead", GLFW.GLFW_KEY_R);
    public static final KeyMapping STAB = key("stab", GLFW.GLFW_KEY_Z);
    public static final KeyMapping FEINT = key("feint", GLFW.GLFW_KEY_G);
    public static final KeyMapping KICK = key("kick", GLFW.GLFW_KEY_V);
    public static final KeyMapping BATTLE_CRY = key("battle_cry", GLFW.GLFW_KEY_B);

    private KeyBindings() {
    }

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.knightcombat." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    static KeyMapping[] all() {
        return new KeyMapping[]{OVERHEAD, STAB, FEINT, KICK, BATTLE_CRY};
    }
}
