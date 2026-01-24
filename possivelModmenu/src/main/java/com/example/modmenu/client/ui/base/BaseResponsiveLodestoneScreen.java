package com.example.modmenu.client.ui.base;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import team.lodestar.lodestone.systems.easing.Easing;

public abstract class BaseResponsiveLodestoneScreen extends Screen {
    protected UIContainer layoutRoot;

    protected BaseResponsiveLodestoneScreen(Component title) {
        super(title);
    }

    private float transitionTimer = 0;

    @Override
    protected void init() {
        super.init();
        this.layoutRoot = new UIContainer(0, 0, this.width, this.height);
        setupLayout();
        transitionTimer = 0;
    }

    protected abstract void setupLayout();

    public int absMouseX, absMouseY;
    private java.util.List<java.util.function.Consumer<GuiGraphics>> postRenderTasks = new java.util.ArrayList<>();

    public void addPostRenderTask(java.util.function.Consumer<GuiGraphics> task) {
        postRenderTasks.add(task);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.absMouseX = mouseX;
        this.absMouseY = mouseY;
        postRenderTasks.clear();
        transitionTimer = Math.min(1.0f, transitionTimer + partialTick * 0.1f);
        float alpha = Easing.SINE_IN_OUT.ease(transitionTimer, 0, 1, 1);
        
        // Darker, more premium background vignette
        int bgColor = (int)(alpha * 0xCC) << 24; // Deeper black
        guiGraphics.fill(0, 0, this.width, this.height, bgColor);
        
        renderVignette(guiGraphics, alpha);
        
        float scale = 0.95f + 0.05f * alpha;
        double scaledMouseX = (mouseX - this.width / 2.0) / scale + this.width / 2.0;
        double scaledMouseY = (mouseY - this.height / 2.0) / scale + this.height / 2.0;

        if (layoutRoot != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(this.width / 2f, this.height / 2f, 0);
            guiGraphics.pose().scale(scale, scale, 1);
            guiGraphics.pose().translate(-this.width / 2f, -this.height / 2f, 0);
            
            layoutRoot.render(guiGraphics, (int)scaledMouseX, (int)scaledMouseY, partialTick);
            guiGraphics.pose().popPose();
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Render post-render tasks (like tooltips) outside of scaling/scissoring
        for (java.util.function.Consumer<GuiGraphics> task : postRenderTasks) {
            task.accept(guiGraphics);
        }
    }

    private void renderVignette(GuiGraphics guiGraphics, float alpha) {
        int color = (int)(alpha * 0x55) << 24;
        int height = (int)(this.height * 0.15f);
        guiGraphics.fillGradient(0, 0, this.width, height, color | 0x000000, color);
        guiGraphics.fillGradient(0, this.height - height, this.width, this.height, color, color | 0x000000);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (layoutRoot != null) {
            float alpha = Easing.SINE_IN_OUT.ease(transitionTimer, 0, 1, 1);
            float scale = 0.95f + 0.05f * alpha;
            double scaledX = (mouseX - this.width / 2.0) / scale + this.width / 2.0;
            double scaledY = (mouseY - this.height / 2.0) / scale + this.height / 2.0;
            if (layoutRoot.mouseClicked(scaledX, scaledY, button)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (super.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        if (layoutRoot != null) {
            float alpha = Easing.SINE_IN_OUT.ease(transitionTimer, 0, 1, 1);
            float scale = 0.95f + 0.05f * alpha;
            double scaledX = (mouseX - this.width / 2.0) / scale + this.width / 2.0;
            double scaledY = (mouseY - this.height / 2.0) / scale + this.height / 2.0;
            if (layoutRoot.mouseScrolled(scaledX, scaledY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (layoutRoot != null) {
            float alpha = Easing.SINE_IN_OUT.ease(transitionTimer, 0, 1, 1);
            float scale = 0.95f + 0.05f * alpha;
            double scaledX = (mouseX - this.width / 2.0) / scale + this.width / 2.0;
            double scaledY = (mouseY - this.height / 2.0) / scale + this.height / 2.0;
            layoutRoot.mouseReleased(scaledX, scaledY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (layoutRoot != null) {
            float alpha = Easing.SINE_IN_OUT.ease(transitionTimer, 0, 1, 1);
            float scale = 0.95f + 0.05f * alpha;
            double scaledX = (mouseX - this.width / 2.0) / scale + this.width / 2.0;
            double scaledY = (mouseY - this.height / 2.0) / scale + this.height / 2.0;
            if (layoutRoot.mouseDragged(scaledX, scaledY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }
    
    // Add other input handling as needed
}
