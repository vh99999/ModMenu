package com.example.modmenu.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event bridge that connects Minecraft tick events to the AIController.
 * This class MUST NOT contain gameplay logic or decisions.
 */
@Mod.EventBusSubscriber(modid = "modmenu", value = Dist.CLIENT)
public class AIHandler {
    private static final AIController CONTROLLER = new AIController("127.0.0.1", 5001);

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer()) {
            return;
        }

        Player player = event.player;
        if (player != Minecraft.getInstance().player) {
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            CONTROLLER.onTickStart(player);
        } else {
            CONTROLLER.onTickEnd(player);
        }
    }

    @SubscribeEvent
    public static void onWorldJoin(EntityJoinLevelEvent event) {
        // Ensure we only reset when the LOCAL player joins a CLIENT level
        if (event.getLevel().isClientSide() && event.getEntity() == Minecraft.getInstance().player) {
            CONTROLLER.reset();
        }
    }

    public static AIController getController() {
        return CONTROLLER;
    }
}
