package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

public class BasePlaceholderScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;

    public BasePlaceholderScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        VerticalLayoutContainer layout = new VerticalLayoutContainer(0, 0, this.width, this.height, 20);
        
        layout.addElement(new ResponsiveButton(0, 0, 200, 25, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));
        
        this.layoutRoot.addChild(layout);
    }
}
