package com.example.modmenu.client;

import com.example.modmenu.modmenu;
import com.example.modmenu.client.ui.screen.MainMenuScreen;
import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.world.level.chunk.LevelChunk;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "modmenu", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvents {
    private static List<BlockPos> cachedChests = new ArrayList<>();
    private static List<BlockPos> cachedTraps = new ArrayList<>();
    private static List<net.minecraft.world.entity.Entity> cachedEntities = new ArrayList<>();
    private static int scanTick = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (KeyMappings.OPEN_MENU_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new MainMenuScreen());
            }

            while (KeyMappings.REMOTE_ACCESS_KEY.consumeClick()) {
                if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "UTILITY_SPATIAL_FOLDING") > 0) {
                    Minecraft.getInstance().setScreen(new MainMenuScreen());
                }
            }

            // Combat Analytics (Combat Branch)
            if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "COMBAT_ANALYTICS") > 0) {
                net.minecraft.world.entity.Entity target = Minecraft.getInstance().crosshairPickEntity;
                if (target instanceof LivingEntity living) {
                    List<Component> hud = new ArrayList<>();
                    hud.add(Component.literal("§6--- Combat Analytics ---"));
                    hud.add(Component.literal("§fHP: §a" + (int)living.getHealth() + "§7/§a" + (int)living.getMaxHealth()));
                    hud.add(Component.literal("§fSP Value: §d" + (int)Math.floor(Math.sqrt(living.getMaxHealth() / 10.0))));
                    hud.add(Component.literal("§fLoot: §e" + living.getLootTable().toString()));
                    
                    // Simple HUD rendering logic (ideally in RenderGuiEvent)
                    // For now we can just store it or use a simpler way
                }
            }

            if (StorePriceManager.clientAbilities.chestHighlightActive || StorePriceManager.clientAbilities.trapHighlightActive || StorePriceManager.clientAbilities.entityESPActive) {
                scanTick++;
                if (scanTick >= 20) { // Scan every 1 second
                    scanTick = 0;
                    if (StorePriceManager.clientAbilities.chestHighlightActive) scanChests();
                    if (StorePriceManager.clientAbilities.trapHighlightActive) scanTraps();
                    if (StorePriceManager.clientAbilities.entityESPActive) scanEntities();
                }
            } else {
                cachedChests.clear();
                cachedTraps.clear();
                cachedEntities.clear();
            }
        }
    }

    @SubscribeEvent
    public static void onScreenMouseEvent(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            net.minecraft.world.inventory.Slot slot = screen.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                if (isShift && event.getButton() == 1) { // Right Click
                    if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ToggleItemLockPacket(slot.getContainerSlot()));
                        event.setCanceled(true);
                    }
                } else {
                    int lockState = slot.getItem().getOrCreateTag().getInt("modmenu_lock_state");
                    if (lockState == 2) { // Frozen
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.isEmpty()) {
            int lockState = stack.getOrCreateTag().getInt("modmenu_lock_state");
            if (lockState == 1) {
                event.getToolTip().add(Component.literal("§c[LOCKED: No Sell/Drop]"));
            } else if (lockState == 2) {
                event.getToolTip().add(Component.literal("§b[FROZEN: No Move]"));
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        StorePriceManager.clearClientData();
    }

    private static void scanChests() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        cachedChests.clear();
        BlockPos playerPos = mc.player.blockPosition();
        int range = StorePriceManager.clientAbilities.chestHighlightRange;
        
        int minX = (playerPos.getX() - range) >> 4;
        int maxX = (playerPos.getX() + range) >> 4;
        int minZ = (playerPos.getZ() - range) >> 4;
        int maxZ = (playerPos.getZ() + range) >> 4;
        
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk != null) {
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof ChestBlockEntity || be.getType().toString().contains("chest")) {
                            if (be.getBlockPos().closerThan(playerPos, range)) {
                                cachedChests.add(be.getBlockPos());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void scanTraps() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        cachedTraps.clear();
        BlockPos playerPos = mc.player.blockPosition();
        int range = StorePriceManager.clientAbilities.trapHighlightRange;
        
        int minX = (playerPos.getX() - range) >> 4;
        int maxX = (playerPos.getX() + range) >> 4;
        int minZ = (playerPos.getZ() - range) >> 4;
        int maxZ = (playerPos.getZ() + range) >> 4;

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk != null) {
                    // Search in the chunk. Unfortunately we have to iterate blocks or use a different search
                    // since traps are mostly blocks, not block entities.
                    // To optimize, we only check chunks within range.
                    int startX = Math.max(cx << 4, playerPos.getX() - range);
                    int endX = Math.min((cx << 4) + 15, playerPos.getX() + range);
                    int startZ = Math.max(cz << 4, playerPos.getZ() - range);
                    int endZ = Math.min((cz << 4) + 15, playerPos.getZ() + range);
                    int startY = Math.max(mc.level.getMinBuildHeight(), playerPos.getY() - range);
                    int endY = Math.min(mc.level.getMaxBuildHeight(), playerPos.getY() + range);

                    for (int x = startX; x <= endX; x++) {
                        for (int z = startZ; z <= endZ; z++) {
                            for (int y = startY; y <= endY; y++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                net.minecraft.world.level.block.state.BlockState state = chunk.getBlockState(pos);
                                net.minecraft.world.level.block.Block block = state.getBlock();
                                if (isTrap(block)) {
                                    cachedTraps.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isTrap(net.minecraft.world.level.block.Block block) {
        return block == net.minecraft.world.level.block.Blocks.TNT ||
               block == net.minecraft.world.level.block.Blocks.REDSTONE_WIRE ||
               block instanceof net.minecraft.world.level.block.BasePressurePlateBlock ||
               block == net.minecraft.world.level.block.Blocks.TRIPWIRE ||
               block == net.minecraft.world.level.block.Blocks.TRIPWIRE_HOOK ||
               block == net.minecraft.world.level.block.Blocks.DISPENSER;
    }

    private static void scanEntities() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        cachedEntities.clear();
        int range = StorePriceManager.clientAbilities.entityESPRange;
        net.minecraft.world.phys.AABB area = mc.player.getBoundingBox().inflate(range);
        
        for (net.minecraft.world.entity.Entity entity : mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area)) {
            if (entity != mc.player) {
                cachedEntities.add(entity);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (StorePriceManager.clientAbilities.chestHighlightActive && !cachedChests.isEmpty()) {
                renderHighlights(event.getPoseStack(), cachedChests, 0.0f, 1.0f, 1.0f); // Cyan
            }
            if (StorePriceManager.clientAbilities.trapHighlightActive && !cachedTraps.isEmpty()) {
                renderHighlights(event.getPoseStack(), cachedTraps, 1.0f, 0.0f, 0.0f); // Red
            }
            if (StorePriceManager.clientAbilities.entityESPActive && !cachedEntities.isEmpty()) {
                renderEntityESP(event.getPoseStack(), cachedEntities, 1.0f, 1.0f, 0.0f); // Yellow
            }
        }
    }

    private static void renderEntityESP(PoseStack poseStack, List<net.minecraft.world.entity.Entity> entities, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getRendertypeLinesShader);
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(2.0f);

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bufferbuilder = tesselator.getBuilder();
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        bufferbuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL);
        
        for (net.minecraft.world.entity.Entity entity : entities) {
            if (!entity.isAlive()) continue;
            net.minecraft.world.phys.AABB aabb = entity.getBoundingBox();
            LevelRenderer.renderLineBox(poseStack, bufferbuilder, 
                aabb.minX, aabb.minY, aabb.minZ, 
                aabb.maxX, aabb.maxY, aabb.maxZ, 
                r, g, b, 1.0f);
        }
        
        tesselator.end();
        poseStack.popPose();
        
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    private static void renderHighlights(PoseStack poseStack, List<BlockPos> blocks, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getRendertypeLinesShader);
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(2.0f);

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bufferbuilder = tesselator.getBuilder();
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        bufferbuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL);
        
        for (BlockPos pos : blocks) {
            LevelRenderer.renderLineBox(poseStack, bufferbuilder, 
                pos.getX(), pos.getY(), pos.getZ(), 
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, 
                r, g, b, 1.0f);
        }
        
        tesselator.end();
        poseStack.popPose();
        
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }
}
