package com.example.modmenu.client.ui.layout;

import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import java.util.ArrayList;
import java.util.List;

public class VerticalLayoutContainer extends UIContainer {
    private int spacing;

    public VerticalLayoutContainer(int x, int y, int width, int height, int spacing) {
        super(x, y, width, height);
        this.spacing = spacing;
    }

    @Override
    public void addChild(UIElement element) {
        super.addChild(element);
        recalculateLayout();
    }

    @Override
    public void addElement(UIElement element) {
        super.addElement(element);
        recalculateLayout();
    }

    private void recalculateLayout() {
        if (children.isEmpty()) return;
        
        int totalHeight = 0;
        for (UIElement child : children) {
            totalHeight += child.getHeight();
        }
        totalHeight += spacing * (children.size() - 1);
        
        // If height was 0 or smaller than totalHeight, we grow.
        // Otherwise we keep original height for centering purposes.
        if (this.height < totalHeight) {
            this.height = totalHeight;
        }
        
        int currentY = (this.height - totalHeight) / 2;
        if (currentY < 0) currentY = 0;
        int centerX = getWidth() / 2;
        
        for (UIElement child : children) {
            child.setX(centerX - child.getWidth() / 2);
            child.setY(currentY);
            currentY += child.getHeight() + spacing;
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // We don't call super.render here if we want to control rendering, 
        // but UIContainer.render already iterates over children.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
