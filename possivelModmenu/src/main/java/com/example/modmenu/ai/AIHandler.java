package com.example.modmenu.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event bridge that connects Minecraft tick events to the AIController.
 * This class MUST NOT contain gameplay logic or decisions.
 */
@Mod.EventBusSubscriber(modid = "modmenu", value = Dist.CLIENT)
public class AIHandler {
    private static final AIController CONTROLLER = new AIController("127.0.0.1", 5000);

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // We only care about client-side tick end to ensure all game state is updated.
        if (event.side.isServer() || event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        if (player != Minecraft.getInstance().player) {
            return;
        }

        // Delegate all orchestration to the controller.
        CONTROLLER.onTick(player);
    }

    public static AIController getController() {
        return CONTROLLER;
    }
}
