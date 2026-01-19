package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.BiConsumer;

public class EnchantmentRowComponent extends UIElement {
    private final ResourceLocation enchantId;
    private final BigDecimal basePrice;
    private final int currentLevel;
    private final BiConsumer<ResourceLocation, Integer> onUpdate;

    public EnchantmentRowComponent(int x, int y, int width, int height, ResourceLocation enchantId, BigDecimal basePrice, int currentLevel, BiConsumer<ResourceLocation, Integer> onUpdate) {
        super(x, y, width, height);
        this.enchantId = enchantId;
        this.basePrice = basePrice;
        this.currentLevel = currentLevel;
        this.onUpdate = onUpdate;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
        
        // Padronized colors
        int bgColor = hovered ? 0xCC2D2D3A : 0xCC1A1A1A;
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        
        int borderColor = hovered ? 0xFF00AAFF : 0xFF444444;
        renderBorder(guiGraphics, getX(), getY(), getWidth(), getHeight(), borderColor);

        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchantId);
        String name = enchantment != null ? enchantment.getFullname(1).getString() : enchantId.toString();
        if (name.endsWith(" I")) name = name.substring(0, name.length() - 2);

        guiGraphics.drawString(Minecraft.getInstance().font, name, getX() + 8, getY() + 6, 0xFFFFFFFF);
        
        // Buttons and Level
        int valX = getX() + getWidth() - 50;
        renderBtn(guiGraphics, valX - 35, getY() + 5, 20, 16, "-", mouseX, mouseY);
        
        String lvlText = String.valueOf(currentLevel);
        // Centralized exactly between buttons (gap is valX-15 to valX+15, center is valX)
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, lvlText, valX, getY() + 9, currentLevel > 0 ? 0xFF55FF55 : 0xFF888888);
        
        renderBtn(guiGraphics, valX + 15, getY() + 5, 20, 16, "+", mouseX, mouseY);

        // Price info - hide if basePrice is 0 (for mining enchants)
        if (basePrice.compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.math.BigDecimal cost = currentLevel > 0 ? basePrice.multiply(java.math.BigDecimal.valueOf(2).pow(currentLevel - 1)) : basePrice;
            String priceText = "$" + StorePriceManager.formatCurrency(cost);
            guiGraphics.drawString(Minecraft.getInstance().font, priceText, getX() + 8, getY() + 18, currentLevel > 0 ? 0xFFFFFF55 : 0xFF888844);
        }
    }

    private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
        boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
        int btnColor = hov ? 0xFF444444 : 0xFF333333;
        int brdColor = hov ? 0xFF00AAFF : 0xFF555555;
        
        g.fill(bx, by, bx + bw, by + bh, btnColor);
        renderBorder(g, bx, by, bw, bh, brdColor);
        g.drawCenteredString(Minecraft.getInstance().font, txt, bx + bw / 2, by + (bh - 8) / 2, 0xFFFFFFFF);
    }

    private void renderBorder(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        guiGraphics.fill(x, y, x + w, y + 1, color);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, color);
        guiGraphics.fill(x, y, x + 1, y + h, color);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int valX = getX() + getWidth() - 50;

        if (mouseX >= valX - 35 && mouseX < valX - 15 && mouseY >= getY() + 5 && mouseY < getY() + 21) {
            onUpdate.accept(enchantId, Math.max(0, currentLevel - 1));
            return true;
        }
        if (mouseX >= valX + 15 && mouseX < valX + 35 && mouseY >= getY() + 5 && mouseY < getY() + 21) {
            onUpdate.accept(enchantId, currentLevel + 1);
            return true;
        }
        return false;
    }
}