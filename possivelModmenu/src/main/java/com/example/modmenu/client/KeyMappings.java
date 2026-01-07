package com.example.modmenu.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyMappings {
    public static final String KEY_CATEGORY_EXAMPLE = "key.categories.modmenu";
    public static final String KEY_OPEN_MENU = "key.modmenu.open_menu";

    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            KEY_OPEN_MENU,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_9,
            KEY_CATEGORY_EXAMPLE
    );
}
