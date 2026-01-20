package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.RerollLootPacket;
import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public class LootRecalibrationScreen extends BaseResponsiveLodestoneScreen {
    private final int entityId;
    private final List<ItemStack> drops;
    private int rerollCount;

    public LootRecalibrationScreen(int entityId, List<ItemStack> drops, int rerollCount) {
        super(Component.literal("Loot Recalibration"));
        this.entityId = entityId;
        this.drops = drops;
        this.rerollCount = rerollCount;
    }

    @Override
    protected void setupLayout() {
        int bw = 100;
        int bh = 20;
        int cx = this.width / 2;

        this.layoutRoot.addElement(new ResponsiveButton(cx - bw - 5, this.height - 40, bw, bh, Component.literal("Accept"), btn -> {
            PacketHandler.sendToServer(new com.example.modmenu.network.AcceptLootPacket(entityId));
            this.minecraft.setScreen(null);
        }));

        BigDecimal cost = BigDecimal.valueOf(100);
        if (rerollCount > 0) {
            cost = cost.multiply(BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(rerollCount)));
        }
        
        // Free Token check (Client display only)
        StorePriceManager.SkillData data = StorePriceManager.clientSkills;
        int lootRank = SkillManager.getActiveRank(data, "COMBAT_LOOT_RECALIBRATION");
        if (lootRank >= 5 && rerollCount < 5) cost = BigDecimal.ZERO;

        String costText = cost.compareTo(BigDecimal.ZERO) == 0 ? "FREE" : cost + " SP";

        this.layoutRoot.addElement(new ResponsiveButton(cx + 5, this.height - 40, bw, bh, Component.literal("Reroll (" + costText + ")"), btn -> {
            PacketHandler.sendToServer(new RerollLootPacket(entityId, false));
        }));

        ScrollableUIContainer grid = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 100);
        this.layoutRoot.addElement(grid);

        int slotSize = 40;
        int cols = grid.getWidth() / slotSize;
        if (cols <= 0) cols = 1;

        for (int i = 0; i < drops.size(); i++) {
            int r = i / cols;
            int c = i % cols;
            grid.addElement(new LootSlot(c * slotSize, r * slotSize, slotSize, slotSize, drops.get(i)));
        }
        grid.setContentHeight((drops.size() / cols + 1) * slotSize);
    }

    private class LootSlot extends UIElement {
        private final ItemStack stack;
        public LootSlot(int x, int y, int width, int height, ItemStack stack) {
            super(x, y, width, height);
            this.stack = stack;
        }
        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            
            int lockState = !stack.isEmpty() ? stack.getOrCreateTag().getInt("modmenu_lock_state") : 0;
            int bgColor = hovered ? 0x66FFFFFF : 0x33FFFFFF;
            
            if (lockState == 1) { // Locked
                bgColor = hovered ? 0x66FF5555 : 0x33FF0000;
            } else if (lockState == 2) { // Frozen
                bgColor = hovered ? 0x665555FF : 0x3300AAFF;
            }

            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
            
            if (lockState == 1) {
                renderBorder(g, getX(), getY(), getWidth(), getHeight(), 0xFFFF0000);
            } else if (lockState == 2) {
                renderBorder(g, getX(), getY(), getWidth(), getHeight(), 0xFF00AAFF);
            }

            g.renderItem(stack, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            g.renderItemDecorations(Minecraft.getInstance().font, stack, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            
            if (lockState == 2) {
                g.pose().pushPose();
                g.pose().translate(getX() + getWidth() - 10, getY() + 2, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(Minecraft.getInstance().font, "❄", 0, 0, 0xFFFFFFFF);
                g.pose().popPose();
            } else if (lockState == 1) {
                g.pose().pushPose();
                g.pose().translate(getX() + getWidth() - 10, getY() + 2, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(Minecraft.getInstance().font, "🔒", 0, 0, 0xFFFFFFFF);
                g.pose().popPose();
            }

            if (hovered) {
                addPostRenderTask(gui -> {
                    gui.renderTooltip(Minecraft.getInstance().font, stack, absMouseX, absMouseY);
                });
            }
        }

        private void renderBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y, x + 1, y + h, color);
            g.fill(x + w - 1, y, x + w, y + h, color);
        }
    }
}
