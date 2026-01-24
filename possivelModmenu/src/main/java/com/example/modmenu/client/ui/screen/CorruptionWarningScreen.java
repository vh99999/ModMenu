package com.example.modmenu.client.ui.screen;

import com.example.modmenu.network.HandleCorruptionPacket;
import com.example.modmenu.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public class CorruptionWarningScreen {
    public static void show(String error) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(
            (result) -> {
                PacketHandler.sendToServer(new HandleCorruptionPacket(result));
                mc.setScreen(null);
            },
            Component.literal("\u00A7cModMenu Data Corruption Detected!"),
            Component.literal("The ModMenu mod data for this world is corrupted and cannot be loaded properly.\nError: " + error + "\n\nWish to reset it? (Will lose all progress in this mod ONLY)"),
            Component.literal("Yes, Reset Data"),
            Component.literal("No, Try to Continue")
        ));
    }
}
