package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class DruzhinaKeys {
    public static final String CATEGORY = "key.categories.druzhina";
    /**
     * Into the commander's view and out of it: F4 (a new name for the key, so that the K of older versions does not
     * stay in the players' settings).
     */
    public static final KeyMapping COMMAND = new KeyMapping("key.druzhina.commander_view", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F4, CATEGORY);

    private DruzhinaKeys() {
    }
}
