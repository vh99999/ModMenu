package com.example.modmenu.client.ui.base;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class ScrollableUIContainer extends UIContainer {
    private float scrollAmount = 0;
    private int contentHeight = 0;
    private boolean isDraggingScrollbar = false;

    public ScrollableUIContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Handle scrollbar dragging
        if (isDraggingScrollbar) {
            float relY = mouseY - getY();
            float scrollPercent = relY / (float) getHeight();
            scrollAmount = Math.max(0, Math.min(contentHeight - getHeight(), scrollPercent * contentHeight - getHeight() / 2f));
        }

        // Absolute coordinates for scissor
        Matrix4f matrix = guiGraphics.pose().last().pose();
        Vector4f tl = new Vector4f(getX(), getY(), 0, 1).mul(matrix);
        Vector4f br = new Vector4f(getX() + getWidth(), getY() + getHeight(), 0, 1).mul(matrix);
        
        guiGraphics.enableScissor((int)tl.x(), (int)tl.y(), (int)br.x(), (int)br.y());
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(getX(), getY() - scrollAmount, 0);
        
        int relMouseX = mouseX - getX();
        int relMouseY = (int) (mouseY - getY() + scrollAmount);
        
        for (UIElement child : children) {
            // Culling: only render if child is at least partially visible
            if (child.getY() + child.getHeight() >= scrollAmount && child.getY() <= scrollAmount + getHeight()) {
                child.render(guiGraphics, relMouseX, relMouseY, partialTick);
            }
        }
        
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        
        // Render scrollbar
        if (contentHeight > getHeight()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(getX(), getY(), 0);
            
            int scrollbarWidth = 4; // Slightly wider for easier dragging
            int scrollbarHeight = Math.max(10, (int) ((getHeight() / (float) contentHeight) * getHeight()));
            int scrollbarY = (int) ((scrollAmount / contentHeight) * getHeight());
            
            // Background track
            guiGraphics.fill(getWidth() - scrollbarWidth - 1, 0, getWidth() - 1, getHeight(), 0x22FFFFFF);

            // Lodestone blue scrollbar
            int color = isDraggingScrollbar ? 0xFFAADFFF : 0xFFAABBFF;
            guiGraphics.fill(getWidth() - scrollbarWidth - 1, scrollbarY, getWidth() - 1, scrollbarY + scrollbarHeight, color);
            // Glow effect
            guiGraphics.fill(getWidth() - scrollbarWidth - 1, scrollbarY, getWidth() - 2, scrollbarY + scrollbarHeight, 0xFF00AAFF);
            
            guiGraphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        // Check scrollbar drag
        if (contentHeight > getHeight()) {
            int scrollbarWidth = 6;
            if (mouseX >= getX() + getWidth() - scrollbarWidth && mouseX <= getX() + getWidth()) {
                isDraggingScrollbar = true;
                return true;
            }
        }
        
        double relMouseX = mouseX - getX();
        double relMouseY = mouseY - getY() + scrollAmount;
        
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseClicked(relMouseX, relMouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollAmount = Math.max(0, Math.min(contentHeight - getHeight(), scrollAmount - (float) amount * 30));
            return true;
        }
        return false;
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
    }
}
