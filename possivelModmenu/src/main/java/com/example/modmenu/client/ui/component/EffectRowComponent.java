package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.BiConsumer;

public class EffectRowComponent extends UIElement {
    private final String effectId;
    private final BigDecimal basePrice;
    private final BiConsumer<String, Integer> onToggle;

    public EffectRowComponent(int x, int y, int width, int height, String effectId, BigDecimal basePrice, BiConsumer<String, Integer> onToggle) {
        super(x, y, width, height);
        this.effectId = effectId;
        this.basePrice = basePrice;
        this.onToggle = onToggle;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
        
        // Padronized colors
        int bgColor = hovered ? 0xCC2D2D3A : 0xCC1A1A1A;
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        
        // Border
        int borderColor = hovered ? 0xFF00AAFF : 0xFF444444;
        renderBorder(guiGraphics, getX(), getY(), getWidth(), getHeight(), borderColor);

        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(effectId));
        String name = effect != null ? effect.getDisplayName().getString() : effectId;
        
        guiGraphics.drawString(Minecraft.getInstance().font, "\u00A7l" + name, getX() + 8, getY() + 6, 0xFFFFFFFF);
        
        int activeLevel = StorePriceManager.clientActiveEffects.getOrDefault(effectId, 0);
        
        // Buttons and Level
        int valX = getX() + getWidth() - 50;
        renderBtn(guiGraphics, valX - 35, getY() + 10, 20, 16, "-", mouseX, mouseY);
        
        String lvlText = String.valueOf(activeLevel);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, lvlText, valX, getY() + 14, activeLevel > 0 ? 0xFF55FF55 : 0xFF888888);
        
        renderBtn(guiGraphics, valX + 15, getY() + 10, 20, 16, "+", mouseX, mouseY);

        BigDecimal currentPrice = activeLevel > 0 ? basePrice.multiply(BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(activeLevel - 1))) : basePrice;
        String priceText = "$" + StorePriceManager.formatCurrency(currentPrice) + "/s";
        guiGraphics.drawString(Minecraft.getInstance().font, priceText, getX() + 8, getY() + 18, 0xFFFFFF55);
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
        int currentLevel = StorePriceManager.clientActiveEffects.getOrDefault(effectId, 0);

        if (mouseX >= valX - 35 && mouseX < valX - 15 && mouseY >= getY() + 10 && mouseY < getY() + 26) {
            onToggle.accept(effectId, Math.max(0, currentLevel - 1));
            return true;
        }
        if (mouseX >= valX + 15 && mouseX < valX + 35 && mouseY >= getY() + 10 && mouseY < getY() + 26) {
            onToggle.accept(effectId, currentLevel + 1);
            return true;
        }
        return false;
    }
}
