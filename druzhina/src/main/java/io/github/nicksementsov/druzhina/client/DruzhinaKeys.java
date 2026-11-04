package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class DruzhinaKeys {
    public static final String CATEGORY = "key.categories.druzhina";
    /** Into the commander's view and out of it. */
    public static final KeyMapping COMMAND = new KeyMapping("key.druzhina.command", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, CATEGORY);

    private DruzhinaKeys() {
    }
}
