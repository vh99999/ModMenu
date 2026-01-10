package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class AbilitySettingComponent extends UIElement {
    private final String label;
    private final String value;
    private final Runnable onDecrement;
    private final Runnable onIncrement;

    public AbilitySettingComponent(int x, int y, int width, int height, String label, String value, Runnable onDecrement, Runnable onIncrement) {
        super(x, y, width, height);
        this.label = label;
        this.value = value;
        this.onDecrement = onDecrement;
        this.onIncrement = onIncrement;
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

        guiGraphics.drawString(Minecraft.getInstance().font, label, getX() + 8, getY() + (getHeight() - 8) / 2, 0xFFDDDDDD);
        
        String valText = value;
        int valX = getX() + getWidth() - 40;
        
        // Buttons with hover effect
        renderBtn(guiGraphics, valX - 30, getY() + 2, 18, getHeight() - 4, "-", mouseX, mouseY, () -> {});
        
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, valText, valX, getY() + (getHeight() - 8) / 2, 0xFF55FF55);
        
        renderBtn(guiGraphics, valX + 12, getY() + 2, 18, getHeight() - 4, "+", mouseX, mouseY, () -> {});
    }

    private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my, Runnable action) {
        boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
        int btnColor = hov ? 0xFF333333 : 0xFF222222;
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
        int valX = getX() + getWidth() - 40;
        if (checkBtn(valX - 30, getY() + 2, 18, getHeight() - 4, mouseX, mouseY)) {
            onDecrement.run();
            return true;
        }
        if (checkBtn(valX + 12, getY() + 2, 18, getHeight() - 4, mouseX, mouseY)) {
            onIncrement.run();
            return true;
        }
        return false;
    }

    private boolean checkBtn(int bx, int by, int bw, int bh, double mx, double my) {
        return mx >= bx && my >= by && mx < bx + bw && my < by + bh;
    }
}
