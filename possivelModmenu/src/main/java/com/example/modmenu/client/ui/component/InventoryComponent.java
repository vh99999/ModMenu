package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.UIElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiConsumer;

public class InventoryComponent extends UIElement {
    private final BiConsumer<ItemStack, Integer> onItemSelected;

    public InventoryComponent(int x, int y, int width, int height, BiConsumer<ItemStack, Integer> onItemSelected) {
        super(x, y, width, height);
        this.onItemSelected = onItemSelected;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        int slotSize = 22; // Slightly larger for better feel
        int padding = 4;
        int totalWidth = 9 * slotSize + 8 * padding;
        int startX = getX() + (getWidth() - totalWidth) / 2;
        int startY = getY() + 10;

        // Padronized background for the inventory area
        int areaColor = 0xCC111111;
        guiGraphics.fill(startX - 10, startY - 10, startX + totalWidth + 10, startY + 4 * (slotSize + padding) + 10, areaColor);
        renderBorder(guiGraphics, startX - 10, startY - 10, totalWidth + 20, 4 * (slotSize + padding) + 20, 0xFF444444);

        // Main inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = startX + col * (slotSize + padding);
                int slotY = startY + row * (slotSize + padding);
                renderSlot(guiGraphics, slotX, slotY, player.getInventory().items.get(col + (row + 1) * 9), mouseX, mouseY);
            }
        }

        // Hotbar separation
        int hotbarY = startY + 3 * (slotSize + padding) + 8;
        
        for (int col = 0; col < 9; col++) {
            int slotX = startX + col * (slotSize + padding);
            renderSlot(guiGraphics, slotX, hotbarY, player.getInventory().items.get(col), mouseX, mouseY);
        }
    }

    private void renderSlot(GuiGraphics guiGraphics, int x, int y, ItemStack stack, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + 22 && mouseY < y + 22;
        
        // Lodestone-style slot: Semi-transparent, sleek
        int bgColor = hovered ? 0x66555555 : 0x331A1A1A;
        
        int lockState = !stack.isEmpty() ? stack.getOrCreateTag().getInt("modmenu_lock_state") : 0;
        int borderColor = hovered ? 0xAA00AAFF : 0x44FFFFFF;
        
        if (lockState == 1) { // Locked
            borderColor = 0xFFFF0000;
            if (!hovered) bgColor = 0x33FF0000;
        } else if (lockState == 2) { // Frozen
            borderColor = 0xFF00AAFF;
            if (!hovered) bgColor = 0x3300AAFF;
        }

        guiGraphics.fill(x, y, x + 22, y + 22, bgColor);
        renderBorder(guiGraphics, x, y, 22, 22, borderColor);
        
        if (!stack.isEmpty()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x + 3, y + 3, 0);
            guiGraphics.renderFakeItem(stack, 0, 0);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
            guiGraphics.pose().popPose();
            
            // Render Symbol/Feedback
            if (lockState == 2) { // Frozen: snowflake or similar
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(x + 14, y + 2, 300);
                guiGraphics.pose().scale(0.5f, 0.5f, 1.0f);
                guiGraphics.drawString(Minecraft.getInstance().font, "❄", 0, 0, 0xFFFFFFFF);
                guiGraphics.pose().popPose();
            } else if (lockState == 1) { // Locked: lock symbol
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(x + 14, y + 2, 300);
                guiGraphics.pose().scale(0.5f, 0.5f, 1.0f);
                guiGraphics.drawString(Minecraft.getInstance().font, "🔒", 0, 0, 0xFFFFFFFF);
                guiGraphics.pose().popPose();
            }

            if (hovered && Minecraft.getInstance().screen instanceof BaseResponsiveLodestoneScreen screen) {
                screen.addPostRenderTask(g -> g.renderTooltip(Minecraft.getInstance().font, stack, screen.absMouseX, screen.absMouseY));
            }
        }
    }

    private void renderBorder(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        guiGraphics.fill(x, y, x + w, y + 1, color);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, color);
        guiGraphics.fill(x, y, x + 1, y + h, color);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;

        boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();

        int slotSize = 22;
        int padding = 4;
        int totalWidth = 9 * slotSize + 8 * padding;
        int startX = getX() + (getWidth() - totalWidth) / 2;
        int startY = getY() + 10;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = startX + col * (slotSize + padding);
                int slotY = startY + row * (slotSize + padding);
                if (mouseX >= slotX && mouseY >= slotY && mouseX < slotX + 22 && mouseY < slotY + 22) {
                    int slotIndex = col + (row + 1) * 9;
                    ItemStack stack = player.getInventory().items.get(slotIndex);
                    if (isShift && button == 1) { // Right Click
                        com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ToggleItemLockPacket(slotIndex));
                        return true;
                    }
                    if (!stack.isEmpty() && stack.getOrCreateTag().getInt("modmenu_lock_state") == 2) return true; // Frozen
                    onItemSelected.accept(stack, slotIndex);
                    return true;
                }
            }
        }

        int hotbarY = startY + 3 * (slotSize + padding) + 8;
        for (int col = 0; col < 9; col++) {
            int slotX = startX + col * (slotSize + padding);
            if (mouseX >= slotX && mouseY >= hotbarY && mouseX < slotX + 22 && mouseY < hotbarY + 22) {
                ItemStack stack = player.getInventory().items.get(col);
                if (isShift && button == 1) { // Right Click
                    com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ToggleItemLockPacket(col));
                    return true;
                }
                if (!stack.isEmpty() && stack.getOrCreateTag().getInt("modmenu_lock_state") == 2) return true; // Frozen
                onItemSelected.accept(stack, col);
                return true;
            }
        }

        return false;
    }
}
