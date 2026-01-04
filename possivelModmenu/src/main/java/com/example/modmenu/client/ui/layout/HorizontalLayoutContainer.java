package com.example.modmenu.client.ui.layout;

import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.client.ui.base.UIElement;

public class HorizontalLayoutContainer extends UIContainer {
    private int spacing;

    public HorizontalLayoutContainer(int x, int y, int width, int height, int spacing) {
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
        
        int totalWidth = 0;
        for (UIElement child : children) {
            totalWidth += child.getWidth();
        }
        totalWidth += spacing * (children.size() - 1);
        
        int currentX = 0; // Starts from the left of the container
        int centerY = getHeight() / 2;
        
        for (UIElement child : children) {
            child.setX(currentX);
            child.setY(centerY - child.getHeight() / 2);
            currentX += child.getWidth() + spacing;
        }
        
        if (this.width < totalWidth) {
            this.width = totalWidth;
        }
    }
}
