package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionChamberPacket;
import com.example.modmenu.network.PacketHandler;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SetYieldTargetScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final int chamberIndex;
    private final String itemId;
    private EditBox input;

    public SetYieldTargetScreen(Screen parent, int chamberIndex, String itemId) {
        super(Component.literal("Set Yield Target"));
        this.parent = parent;
        this.chamberIndex = chamberIndex;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, this.width / 2 - 50, this.height / 2 - 10, 100, 20, Component.literal("Target Count"));
        input.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addWidget(input);
    }

    @Override
    protected void setupLayout() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.layoutRoot.addElement(new ResponsiveButton(cx - 105, cy + 30, 100, 20, Component.literal("Confirm"), btn -> {
            try {
                int val = Integer.parseInt(input.getValue());
                PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 11, itemId, val));
                this.minecraft.setScreen(parent);
            } catch (NumberFormatException e) {}
        }));

        this.layoutRoot.addElement(new ResponsiveButton(cx + 5, cy + 30, 100, 20, Component.literal("Remove"), btn -> {
            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 11, itemId, 0));
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(cx - 50, cy + 60, 100, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "Set Target for " + itemId, this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
        input.render(g, mx, my, pt);
    }
}
