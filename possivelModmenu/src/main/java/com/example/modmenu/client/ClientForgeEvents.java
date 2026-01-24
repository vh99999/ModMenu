package com.example.modmenu.client;

import com.example.modmenu.modmenu;
import com.example.modmenu.client.ui.screen.MainMenuScreen;
import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import com.example.modmenu.store.logistics.*;
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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
    public static int transferModeChamberIndex = -1;
    public static int linkModeChamberIndex = -1;
    public static int linkModeProviderChamberIndex = -1;
    public static java.util.UUID networkLinkModeId = null;
    public static java.util.UUID viewedNetworkId = null;
    private static java.util.Map<BlockPos, Long> activePings = new java.util.concurrent.ConcurrentHashMap<>();

    public static void addPing(BlockPos pos) {
        activePings.put(pos, System.currentTimeMillis() + 5000); // 5 seconds
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (Minecraft.getInstance().level == null) {
                if (transferModeChamberIndex != -1 || linkModeChamberIndex != -1 || linkModeProviderChamberIndex != -1 || networkLinkModeId != null) {
                    transferModeChamberIndex = -1;
                    linkModeChamberIndex = -1;
                    linkModeProviderChamberIndex = -1;
                    networkLinkModeId = null;
                    if (Minecraft.getInstance().getConnection() != null) {
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionNetworkPacket(18, (java.util.UUID)null));
                    }
                }
            }

            // Exit modes on ESC or Screen opening
            if (Minecraft.getInstance().screen != null) {
                if (transferModeChamberIndex != -1 || linkModeChamberIndex != -1 || linkModeProviderChamberIndex != -1 || networkLinkModeId != null) {
                    transferModeChamberIndex = -1;
                    linkModeChamberIndex = -1;
                    linkModeProviderChamberIndex = -1;
                    networkLinkModeId = null;
                    if (Minecraft.getInstance().getConnection() != null) {
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionNetworkPacket(18, (java.util.UUID)null));
                    }
                }
            }
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
                    hud.add(Component.literal("\u00A76--- Combat Analytics ---"));
                    hud.add(Component.literal("\u00A7fHP: \u00A7a" + (int)living.getHealth() + "\u00A77/\u00A7a" + (int)living.getMaxHealth()));
                    hud.add(Component.literal("\u00A7fSP Value: \u00A7d" + (int)Math.floor(Math.sqrt(living.getMaxHealth() / 10.0))));
                    hud.add(Component.literal("\u00A7fLoot: \u00A7e" + living.getLootTable().toString()));
                    
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
                event.getToolTip().add(Component.literal("\u00A7c[LOCKED: No Sell/Drop]"));
            } else if (lockState == 2) {
                event.getToolTip().add(Component.literal("\u00A7b[FROZEN: No Move]"));
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
            int guiLeft = screen.getGuiLeft();
            int guiTop = screen.getGuiTop();

            for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    int lockState = stack.getOrCreateTag().getInt("modmenu_lock_state");
                    if (lockState >= 1) {
                        int x = guiLeft + slot.x;
                        int y = guiTop + slot.y;

                        // Render feedback
                        if (lockState == 1) { // Locked
                            g.fill(x, y, x + 16, y + 16, 0x33FF0000);
                            renderBorder(g, x - 1, y - 1, 18, 18, 0xFFFF0000);
                        } else if (lockState == 2) { // Frozen
                            g.fill(x, y, x + 16, y + 16, 0x3300AAFF);
                            renderBorder(g, x - 1, y - 1, 18, 18, 0xFF00AAFF);
                        }

                        // Render Symbol
                        g.pose().pushPose();
                        g.pose().translate(x + 10, y, 300);
                        g.pose().scale(0.5f, 0.5f, 1.0f);
                        String icon = lockState == 2 ? "\u2744" : "\uD83D\uDD12";
                        g.drawString(Minecraft.getInstance().font, icon, 0, 0, 0xFFFFFFFF);
                        g.pose().popPose();
                    }
                }
            }
        }
    }

    private static void renderBorder(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
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

            // Link Mode Highlighting
            if (networkLinkModeId != null) {
                net.minecraft.world.phys.HitResult hit = Minecraft.getInstance().hitResult;
                if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                    List<BlockPos> target = List.of(blockHit.getBlockPos());
                    renderHighlights(event.getPoseStack(), target, 0.0f, 1.0f, 0.5f); // Soft Cyan/Green
                }
            }

            // Ping Highlights
            if (!activePings.isEmpty()) {
                long now = System.currentTimeMillis();
                activePings.entrySet().removeIf(entry -> entry.getValue() < now);
                if (!activePings.isEmpty()) {
                    renderHighlights(event.getPoseStack(), new ArrayList<>(activePings.keySet()), 1.0f, 0.8f, 0.0f); // Gold/Orange
                }
            }

            // World-Space Ghost Outlines for Missing Nodes
            ItemStack held = Minecraft.getInstance().player.getMainHandItem();
            if (held.is(com.example.modmenu.registry.ItemRegistry.LOGISTICS_TOOL.get()) || viewedNetworkId != null) {
                LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
                    List<BlockPos> missingPositions = new ArrayList<>();
                    for (NetworkData nd : data.getNetworks()) {
                        // If UI open, only show for that network. If not, show for all.
                        if (viewedNetworkId != null && !nd.networkId.equals(viewedNetworkId)) continue;
                        
                        for (NetworkNode node : nd.nodes) {
                            if (node.isMissing && node.pos != null) {
                                String currentDim = Minecraft.getInstance().level.dimension().location().toString();
                                if (currentDim.equals(node.dimension)) {
                                    missingPositions.add(node.pos);
                                }
                            }
                        }
                    }
                    if (!missingPositions.isEmpty()) {
                        // Pulsing Red for missing blocks
                        float pulse = (float) Math.abs(Math.sin(System.currentTimeMillis() / 400.0));
                        renderHighlights(event.getPoseStack(), missingPositions, 1.0f, 0.2f * pulse, 0.2f * pulse);
                    }
                });
            }

            // Traffic Overlay
            if (viewedNetworkId != null) {
                LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
                    for (NetworkData nd : data.getNetworks()) {
                        if (nd.networkId.equals(viewedNetworkId)) {
                            if (nd.showConnections) {
                                renderTrafficLines(event.getPoseStack(), nd);
                            }
                            break;
                        }
                    }
                });
            }
        }
    }

    private static void renderTrafficLines(PoseStack poseStack, NetworkData network) {
        if (network == null || network.rules.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getRendertypeLinesShader);
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(3.0f);

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bufferbuilder = tesselator.getBuilder();
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        bufferbuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL);
        
        for (LogisticsRule rule : network.rules) {
            if (!rule.active) continue;
            
            NetworkNode src = null;
            NetworkNode dst = null;
            for (NetworkNode n : network.nodes) {
                if (n.nodeId.equals(rule.sourceNodeId)) src = n;
                if (n.nodeId.equals(rule.destNodeId)) dst = n;
            }
            
            if (src != null && dst != null && src.pos != null && dst.pos != null) {
                String currentDim = mc.level.dimension().location().toString();
                if (currentDim.equals(src.dimension) && currentDim.equals(dst.dimension)) {
                    float x1 = src.pos.getX() + 0.5f;
                    float y1 = src.pos.getY() + 0.5f;
                    float z1 = src.pos.getZ() + 0.5f;
                    float x2 = dst.pos.getX() + 0.5f;
                    float y2 = dst.pos.getY() + 0.5f;
                    float z2 = dst.pos.getZ() + 0.5f;
                    
                    float r = 0.0f, g = 1.0f, b = 1.0f; 
                    
                    bufferbuilder.vertex(poseStack.last().pose(), x1, y1, z1).color(r, g, b, 0.8f).normal(0, 1, 0).endVertex();
                    bufferbuilder.vertex(poseStack.last().pose(), x2, y2, z2).color(r, g, b, 0.8f).normal(0, 1, 0).endVertex();
                }
            }
        }
        
        tesselator.end();
        poseStack.popPose();
        
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            ItemStack held = event.getItemStack();
            boolean isTool = held.is(com.example.modmenu.registry.ItemRegistry.LOGISTICS_TOOL.get());

            if (isTool && !event.getEntity().isShiftKeyDown()) {
                Minecraft.getInstance().setScreen(new com.example.modmenu.client.ui.screen.NetworkListScreen(null));
                event.setCanceled(true);
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                return;
            }

            if (transferModeChamberIndex != -1) {
                if (event.getEntity().isShiftKeyDown()) {
                    BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
                    if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(transferModeChamberIndex, 17, event.getPos()));
                        transferModeChamberIndex = -1;
                        event.setCanceled(true);
                        event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
                        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    }
                } else {
                    transferModeChamberIndex = -1;
                }
            } else if (linkModeChamberIndex != -1) {
                if (event.getEntity().isShiftKeyDown()) {
                    BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
                    if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                        String dimension = event.getLevel().dimension().location().toString();
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(linkModeChamberIndex, 18, event.getPos(), dimension));
                        linkModeChamberIndex = -1;
                        event.setCanceled(true);
                        event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
                        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    }
                } else {
                    linkModeChamberIndex = -1;
                }
            } else if (linkModeProviderChamberIndex != -1) {
                if (event.getEntity().isShiftKeyDown()) {
                    BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
                    if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                        String dimension = event.getLevel().dimension().location().toString();
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(linkModeProviderChamberIndex, 20, event.getPos(), dimension));
                        linkModeProviderChamberIndex = -1;
                        event.setCanceled(true);
                        event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
                        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    }
                } else {
                    linkModeProviderChamberIndex = -1;
                }
            } else if (networkLinkModeId != null) {
                if (event.getEntity().isShiftKeyDown()) {
                    net.minecraft.world.level.block.entity.BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
                    if (be != null) {
                        String dimension = event.getLevel().dimension().location().toString();
                        com.example.modmenu.network.PacketHandler.sendToServer(com.example.modmenu.network.ActionNetworkPacket.addNode(networkLinkModeId, event.getPos(), dimension));
                        // networkLinkModeId = null; com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionNetworkPacket(18, (java.util.UUID)null)); // Removed to allow continuous selection
                        event.setCanceled(true);
                        event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
                        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    }
                } else {
                    networkLinkModeId = null;
                    com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionNetworkPacket(18, (java.util.UUID)null));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
            if (transferModeChamberIndex != -1 || linkModeChamberIndex != -1 || linkModeProviderChamberIndex != -1 || networkLinkModeId != null) {
                Minecraft mc = Minecraft.getInstance();
                int x = event.getWindow().getGuiScaledWidth() / 2;
                int y = event.getWindow().getGuiScaledHeight() / 2 + 20;
                
                String text = "";
                if (transferModeChamberIndex != -1) text = "\u00A76[Transfer Mode Active] \u00A7eShift-Right-Click a Container";
                else if (linkModeChamberIndex != -1) text = "\u00A7b[Link Mode Active] \u00A7eShift-Right-Click a Container";
                else if (linkModeProviderChamberIndex != -1) text = "\u00A7d[Provider Link Mode Active] \u00A7eShift-Right-Click a Container";
                else if (networkLinkModeId != null) {
                    net.minecraft.world.phys.HitResult hit = mc.hitResult;
                    String blockName = "Nothing";
                    if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                        blockName = mc.level.getBlockState(bhr.getBlockPos()).getBlock().getName().getString();
                    }
                    text = "\u00A79[Network Mode] \u00A7eShift-Right-Click: \u00A7f" + blockName;
                }
                
                event.getGuiGraphics().drawCenteredString(mc.font, text, x, y, 0xFFFFFFFF);
            }

            // Global Logistics Diagnostics HUD
            ItemStack held = Minecraft.getInstance().player.getMainHandItem();
            if (held.is(com.example.modmenu.registry.ItemRegistry.LOGISTICS_TOOL.get())) {
                LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
                    int errorCount = 0;
                    List<String> errorNetworks = new ArrayList<>();
                    for (NetworkData nd : data.getNetworks()) {
                        long missing = nd.nodes.stream().filter(n -> n.isMissing).count();
                        if (missing > 0) {
                            errorCount++;
                            errorNetworks.add("\u00A7c\u26A0 " + nd.networkName + ": \u00A7f" + missing + " Offline");
                        }
                    }

                    if (errorCount > 0) {
                        net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
                        int dy = 10;
                        g.drawString(Minecraft.getInstance().font, "\u00A7b\u00A7l[ LOGISTICS DIAGNOSTICS ]", 10, dy, 0xFFFFFFFF);
                        dy += 12;
                        for (String err : errorNetworks) {
                            g.drawString(Minecraft.getInstance().font, err, 15, dy, 0xFFFFFFFF);
                            dy += 10;
                        }
                    }
                });
            }
        }
    }
}
