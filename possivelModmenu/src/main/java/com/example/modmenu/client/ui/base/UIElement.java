package com.example.modmenu.client.ui.base;

import net.minecraft.client.gui.GuiGraphics;

public abstract class UIElement {
    protected int x, y, width, height;

    protected java.util.List<net.minecraft.network.chat.Component> tooltip = null;

    public UIElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip) {
        this.tooltip = tooltip;
    }

    public abstract void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (tooltip != null && isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(net.minecraft.client.Minecraft.getInstance().font, tooltip, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return isMouseOver(mouseX, mouseY);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int mod) {
        return false;
    }

    public boolean charTyped(char chr, int code) {
        return false;
    }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
}
