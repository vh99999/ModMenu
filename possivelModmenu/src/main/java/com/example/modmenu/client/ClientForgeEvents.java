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
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
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
    
    private static int musicTickDelay = 0;
    private static net.minecraft.client.resources.sounds.SoundInstance currentMusicInstance = null;

    public static void addPing(BlockPos pos) {
        activePings.put(pos, System.currentTimeMillis() + 5000); // 5 seconds
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                if (transferModeChamberIndex != -1 || linkModeChamberIndex != -1 || linkModeProviderChamberIndex != -1 || networkLinkModeId != null) {
                    transferModeChamberIndex = -1;
                    linkModeChamberIndex = -1;
                    linkModeProviderChamberIndex = -1;
                    networkLinkModeId = null;
                    if (mc.getConnection() != null) {
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionNetworkPacket(18, (java.util.UUID)null));
                    }
                }
            }

            // Exit modes on ESC or Screen opening
            if (mc.screen != null) {
                if (transferModeChamberIndex != -1 || linkModeChamberIndex != -1 || linkModeProviderChamberIndex != -1 || networkLinkModeId != null) {
                    transferModeChamberIndex = -1;
                    linkModeChamberIndex = -1;
                    linkModeProviderChamberIndex = -1;
                    networkLinkModeId = null;
                    if (mc.getConnection() != null) {
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionNetworkPacket(18, (java.util.UUID)null));
                    }
                }
            }
            while (KeyMappings.OPEN_MENU_KEY.consumeClick()) {
                mc.setScreen(new MainMenuScreen());
            }

            while (KeyMappings.REMOTE_ACCESS_KEY.consumeClick()) {
                if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "UTILITY_SPATIAL_FOLDING") > 0) {
                    mc.setScreen(new MainMenuScreen());
                }
            }

            // Combat Analytics
            if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "COMBAT_ANALYTICS") > 0) {
                net.minecraft.world.entity.Entity target = mc.crosshairPickEntity;
                if (target instanceof LivingEntity living) {
                    // Analytics logic here...
                }
            }

            if (StorePriceManager.clientAbilities.chestHighlightActive || StorePriceManager.clientAbilities.trapHighlightActive || StorePriceManager.clientAbilities.entityESPActive) {
                scanTick++;
                if (scanTick >= 20) {
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

            // Custom Music Handler
            if (currentMusicInstance != null) {
                mc.getSoundManager().stop(currentMusicInstance);
                currentMusicInstance = null;
            }
        }
    }

    @SubscribeEvent
    public static void onScreenMouseEvent(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            net.minecraft.world.inventory.Slot slot = screen.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                if (isShift && event.getButton() == 1) {
                    if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ToggleItemLockPacket(slot.getContainerSlot()));
                        event.setCanceled(true);
                    }
                } else {
                    int lockState = slot.getItem().getOrCreateTag().getInt("modmenu_lock_state");
                    if (lockState == 2) event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.isEmpty()) {
            int lockState = stack.getOrCreateTag().getInt("modmenu_lock_state");
            if (lockState == 1) event.getToolTip().add(Component.literal("\u00A7c[LOCKED: No Sell/Drop]"));
            else if (lockState == 2) event.getToolTip().add(Component.literal("\u00A7b[FROZEN: No Move]"));
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
                        if (lockState == 1) {
                            g.fill(x, y, x + 16, y + 16, 0x33FF0000);
                            renderBorder(g, x - 1, y - 1, 18, 18, 0xFFFF0000);
                        } else if (lockState == 2) {
                            g.fill(x, y, x + 16, y + 16, 0x3300AAFF);
                            renderBorder(g, x - 1, y - 1, 18, 18, 0xFF00AAFF);
                        }
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
                            if (be.getBlockPos().closerThan(playerPos, range)) cachedChests.add(be.getBlockPos());
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
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int y = -range; y <= range; y++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (isTrap(mc.level.getBlockState(pos).getBlock())) cachedTraps.add(pos);
                }
            }
        }
    }

    private static boolean isTrap(net.minecraft.world.level.block.Block block) {
        return block == net.minecraft.world.level.block.Blocks.TNT || block == net.minecraft.world.level.block.Blocks.REDSTONE_WIRE || block instanceof net.minecraft.world.level.block.BasePressurePlateBlock || block == net.minecraft.world.level.block.Blocks.TRIPWIRE || block == net.minecraft.world.level.block.Blocks.TRIPWIRE_HOOK || block == net.minecraft.world.level.block.Blocks.DISPENSER;
    }

    private static void scanEntities() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        cachedEntities.clear();
        int range = StorePriceManager.clientAbilities.entityESPRange;
        net.minecraft.world.phys.AABB area = mc.player.getBoundingBox().inflate(range);
        for (net.minecraft.world.entity.Entity entity : mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area)) {
            if (entity != mc.player) cachedEntities.add(entity);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (StorePriceManager.clientAbilities.chestHighlightActive && !cachedChests.isEmpty()) renderHighlights(event.getPoseStack(), cachedChests, 0.0f, 1.0f, 1.0f);
            if (StorePriceManager.clientAbilities.trapHighlightActive && !cachedTraps.isEmpty()) renderHighlights(event.getPoseStack(), cachedTraps, 1.0f, 0.0f, 0.0f);
            if (StorePriceManager.clientAbilities.entityESPActive && !cachedEntities.isEmpty()) renderEntityESP(event.getPoseStack(), cachedEntities, 1.0f, 1.0f, 0.0f);
            if (networkLinkModeId != null) {
                net.minecraft.world.phys.HitResult hit = Minecraft.getInstance().hitResult;
                if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) renderHighlights(event.getPoseStack(), List.of(blockHit.getBlockPos()), 0.0f, 1.0f, 0.5f);
            }
        }
    }

    private static void renderEntityESP(PoseStack poseStack, List<net.minecraft.world.entity.Entity> entities, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getRendertypeLinesShader);
        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bufferbuilder = tesselator.getBuilder();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        bufferbuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (net.minecraft.world.entity.Entity entity : entities) {
            if (!entity.isAlive()) continue;
            LevelRenderer.renderLineBox(poseStack, bufferbuilder, entity.getBoundingBox(), r, g, b, 1.0f);
        }
        tesselator.end();
        poseStack.popPose();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    private static void renderHighlights(PoseStack poseStack, List<BlockPos> blocks, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getRendertypeLinesShader);
        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bufferbuilder = tesselator.getBuilder();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        bufferbuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (BlockPos pos : blocks) {
            LevelRenderer.renderLineBox(poseStack, bufferbuilder, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, r, g, b, 1.0f);
        }
        tesselator.end();
        poseStack.popPose();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            ItemStack held = event.getItemStack();
            if (held.is(com.example.modmenu.registry.ItemRegistry.LOGISTICS_TOOL.get()) && !event.getEntity().isShiftKeyDown()) {
                Minecraft.getInstance().setScreen(new com.example.modmenu.client.ui.screen.NetworkListScreen(null));
                event.setCanceled(true);
                return;
            }
            if (transferModeChamberIndex != -1 && event.getEntity().isShiftKeyDown()) {
                BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
                if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                    com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(transferModeChamberIndex, 17, event.getPos()));
                    transferModeChamberIndex = -1;
                    event.setCanceled(true);
                }
            } else if (linkModeChamberIndex != -1 && event.getEntity().isShiftKeyDown()) {
                BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
                if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                    com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(linkModeChamberIndex, 18, event.getPos(), event.getLevel().dimension().location().toString()));
                    linkModeChamberIndex = -1;
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(com.example.modmenu.store.GenesisManager.GENESIS_DIM)) {
            StorePriceManager.GenesisConfig config = StorePriceManager.clientGenesisConfig;
            int color = config.fogColor;
            event.setRed(((color >> 16) & 0xFF) / 255f);
            event.setGreen(((color >> 8) & 0xFF) / 255f);
            event.setBlue((color & 0xFF) / 255f);
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(com.example.modmenu.store.GenesisManager.GENESIS_DIM)) {
            StorePriceManager.GenesisConfig config = StorePriceManager.clientGenesisConfig;
            if (config.fogDensity != 1.0) {
                float factor = (float) config.fogDensity;
                if (factor > 0) {
                    event.scaleFarPlaneDistance(1.0f / factor);
                    event.scaleNearPlaneDistance(1.0f / factor);
                }
            }
        }
    }
}
