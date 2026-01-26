package com.example.modmenu.client.ui.base;

import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

public class UIContainer extends UIElement {
    protected List<UIElement> children = new ArrayList<>();

    public UIContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void addChild(UIElement element) {
        children.add(element);
    }

    public void addElement(UIElement element) {
        addChild(element);
    }

    public void clearChildren() {
        children.clear();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(getX(), getY(), 0);
        
        int relMouseX = mouseX - getX();
        int relMouseY = mouseY - getY();
        
        for (UIElement child : children) {
            child.render(guiGraphics, relMouseX, relMouseY, partialTick);
        }
        guiGraphics.pose().popPose();
    }

    @Override
    public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return;
        
        int relMouseX = mouseX - getX();
        int relMouseY = mouseY - getY();
        
        for (UIElement child : children) {
            child.renderTooltip(guiGraphics, relMouseX, relMouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        
        double relMouseX = mouseX - getX();
        double relMouseY = mouseY - getY();
        
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseClicked(relMouseX, relMouseY, button)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        double relMouseX = mouseX - getX();
        double relMouseY = mouseY - getY();
        
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseScrolled(relMouseX, relMouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double relMouseX = mouseX - getX();
        double relMouseY = mouseY - getY();
        
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseReleased(relMouseX, relMouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double relMouseX = mouseX - getX();
        double relMouseY = mouseY - getY();
        
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseDragged(relMouseX, relMouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).keyPressed(key, scan, mod)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int code) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).charTyped(chr, code)) {
                return true;
            }
        }
        return false;
    }

    public List<UIElement> getChildren() {
        return children;
    }
}
