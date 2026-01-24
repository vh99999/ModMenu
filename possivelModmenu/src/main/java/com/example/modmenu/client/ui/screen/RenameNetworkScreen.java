package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionNetworkPacket;
import com.example.modmenu.network.PacketHandler;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class RenameNetworkScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private final String currentName;
    private EditBox input;

    public RenameNetworkScreen(Screen parent, UUID networkId, String currentName) {
        super(Component.literal("Rename Network"));
        this.parent = parent;
        this.networkId = networkId;
        this.currentName = currentName;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, this.width / 2 - 100, this.height / 2 - 10, 200, 20, Component.literal("Network Name"));
        input.setMaxLength(32);
        input.setValue(currentName);
        this.addWidget(input);
        this.setFocused(input);
    }

    @Override
    protected void setupLayout() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.layoutRoot.addElement(new ResponsiveButton(cx - 105, cy + 30, 100, 20, Component.literal("Confirm"), btn -> {
            String newName = input.getValue().trim();
            if (!newName.isEmpty()) {
                PacketHandler.sendToServer(ActionNetworkPacket.renameNetwork(networkId, newName));
                this.minecraft.setScreen(parent);
            }
        }));

        this.layoutRoot.addElement(new ResponsiveButton(cx + 5, cy + 30, 100, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "Rename Network", this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
        input.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            String newName = input.getValue().trim();
            if (!newName.isEmpty()) {
                PacketHandler.sendToServer(ActionNetworkPacket.renameNetwork(networkId, newName));
                this.minecraft.setScreen(parent);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
