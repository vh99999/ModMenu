package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.base.UIContainer;
import team.lodestar.lodestone.systems.easing.Easing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import java.awt.Color;
import java.util.function.Consumer;

public class ResponsiveButton extends UIElement {
    private Component text;
    private final Consumer<ResponsiveButton> onPress;
    private float hoverTimer = 0;
    private boolean isHovered = false;
    private boolean active = true;

    public ResponsiveButton(int x, int y, int width, int height, Component text, Consumer<ResponsiveButton> onPress) {
        super(x, y, width, height);
        this.text = text;
        this.onPress = onPress;
    }

    public void setText(Component text) {
        this.text = text;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
        
        if (active && isHovered) {
            hoverTimer = Math.min(1.0f, hoverTimer + partialTick * 0.15f);
        } else {
            hoverTimer = Math.max(0.0f, hoverTimer - partialTick * 0.15f);
        }

        float easedHover = active ? Easing.EXPO_OUT.ease(hoverTimer, 0, 1, 1) : 0;
        
        // Background - Lodestone dark theme
        int alpha = 0xCC; 
        
        int r, g, b;
        if (active) {
            r = (int) (0x1A + (0x2D - 0x1A) * easedHover);
            g = (int) (0x1A + (0x2D - 0x1A) * easedHover);
            b = (int) (0x1A + (0x3A - 0x1A) * easedHover);
        } else {
            // Desaturated gray for disabled
            r = g = b = 0x12;
            alpha = 0x88;
        }
        
        int bgColor = (alpha << 24) | (r << 16) | (g << 8) | b;
        
        // Draw background
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        
        // Draw border
        int borderColor;
        if (active) {
            // Bright blue/cyan border that glows on hover
            int br = (int) (0x44 + 0x22 * easedHover);
            int bg = (int) (0x44 + 0xBB * easedHover);
            int bb = (int) (0x55 + 0xFF * easedHover);
            borderColor = (0xFF << 24) | (br << 16) | (bg << 8) | bb;
        } else {
            borderColor = 0xFF222222; // Darker, desaturated border
        }
        
        renderBorder(guiGraphics, getX(), getY(), getWidth(), getHeight(), borderColor);

        // Hover highlight overlay
        if (active && easedHover > 0) {
            int highlightColor = ((int)(easedHover * 0x22) << 24) | 0xFFFFFF;
            guiGraphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + 2, highlightColor);
        }

        // Draw text
        Font font = Minecraft.getInstance().font;
        int textColor;
        if (active) {
            textColor = isHovered ? 0xFFFFFFFF : 0xFFEEEEEE;
        } else {
            textColor = 0xFF444444; // Faded text
        }
        
        guiGraphics.drawCenteredString(font, text, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
    }

    private void renderBorder(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        guiGraphics.fill(x, y, x + w, y + 1, color);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, color);
        guiGraphics.fill(x, y, x + 1, y + h, color);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && isHovered && button == 0) {
            onPress.accept(this);
            return true;
        }
        return false;
    }
}
