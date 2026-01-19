package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class NBTRefactorScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final ItemStack target;

    public NBTRefactorScreen(Screen parent, ItemStack target) {
        super(Component.literal("NBT Refactor"));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        int cx = this.width / 2;
        int y = 60;

        this.layoutRoot.addElement(new ResponsiveButton(cx - 100, y, 200, 20, Component.literal("Set Custom Name (50 SP)"), btn -> {
            // logic to send packet
        }));

        y += 25;
        this.layoutRoot.addElement(new ResponsiveButton(cx - 100, y, 200, 20, Component.literal("Buff Attack Damage (100 SP)"), btn -> {
            // logic to send packet
        }));

        y += 25;
        this.layoutRoot.addElement(new ResponsiveButton(cx - 100, y, 200, 20, Component.literal("Buff Reach Distance (100 SP)"), btn -> {
            // logic to send packet
        }));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.renderItem(target, this.width / 2 - 8, 40);
        g.drawCenteredString(font, "Target: " + target.getHoverName().getString(), this.width / 2, 30, 0xFFFFFFFF);
    }
}
