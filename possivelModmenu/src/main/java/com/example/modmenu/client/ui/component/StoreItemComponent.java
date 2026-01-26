package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import java.util.function.BiConsumer;

public class StoreItemComponent extends UIElement {
    private final Item item;
    private final String specialId;
    private final boolean isSellMode;
    private final BiConsumer<Item, String> onPurchase;
    private final BiConsumer<Item, String> onRightClick;

    public StoreItemComponent(int x, int y, int width, int height, Item item, String specialId, boolean isSellMode, BiConsumer<Item, String> onPurchase, BiConsumer<Item, String> onRightClick) {
        super(x, y, width, height);
        this.item = item;
        this.specialId = specialId;
        this.isSellMode = isSellMode;
        this.onPurchase = onPurchase;
        this.onRightClick = onRightClick;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
        
        // Padronized colors
        int bgColor = hovered ? 0xCC2D2D3A : 0xCC1A1A1A;
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        
        // Thin white/gray border as seen in the image
        int borderColor = hovered ? 0xFF00AAFF : 0xFF444444;
        renderBorder(guiGraphics, getX(), getY(), getWidth(), getHeight(), borderColor);

        if (item != null) {
            // Render item icon centered in the upper part
            guiGraphics.renderFakeItem(new ItemStack(item), getX() + (getWidth() - 16) / 2, getY() + 10);
        }

        // Price in GREEN below the item
        java.math.BigDecimal price = item != null ? (isSellMode ? StorePriceManager.getSellPrice(item) : StorePriceManager.getBuyPrice(item)) : java.math.BigDecimal.ZERO;
        String priceText = "$" + StorePriceManager.formatCurrency(price);
        int priceColor = 0xFF55FF55; // Always green as in the image
        
        int textWidth = Minecraft.getInstance().font.width(priceText);
        guiGraphics.drawString(Minecraft.getInstance().font, priceText, getX() + (getWidth() - textWidth) / 2, getY() + 35, priceColor, true);
        
        if (hovered && Minecraft.getInstance().screen instanceof BaseResponsiveLodestoneScreen screen) {
            if (item != null) {
                ItemStack stack = new ItemStack(item);
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
        if (mouseX >= getX() && mouseY >= getY() && mouseX < getX() + getWidth() && mouseY < getY() + getHeight()) {
            if (button == 0) {
                onPurchase.accept(item, specialId);
            } else if (button == 1) {
                onRightClick.accept(item, specialId);
            }
            return true;
        }
        return false;
    }
}
