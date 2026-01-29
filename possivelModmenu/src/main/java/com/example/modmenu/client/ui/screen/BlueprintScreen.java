package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.*;
import com.example.modmenu.store.logistics.LogisticsBlueprint;
import com.example.modmenu.store.logistics.NetworkData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class BlueprintScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private final NetworkData network;
    private net.minecraft.client.gui.components.EditBox importBox;

    public BlueprintScreen(Screen parent, UUID networkId, NetworkData network) {
        super(Component.literal("Blueprint Management"));
        this.parent = parent;
        this.networkId = networkId;
        this.network = network;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        int y = 50;
        this.layoutRoot.addElement(new ResponsiveButton(this.width / 2 - 100, y, 200, 20, Component.literal("\u00A7aExport Entire Network"), btn -> {
            LogisticsBlueprint bp = new LogisticsBlueprint(network.networkName + " Blueprint", network.nodes, network.groups, network.rules, network.overflowTargetId, network.overflowIsGroup);
            String encoded = bp.serialize();
            Minecraft.getInstance().keyboardHandler.setClipboard(encoded);
            Minecraft.getInstance().player.displayClientMessage(Component.literal("\u00A7aBlueprint copied to clipboard!"), true);
        }));
        y += 30;

        importBox = new net.minecraft.client.gui.components.EditBox(font, this.width / 2 - 150, y, 300, 20, Component.literal("Paste Blueprint string here..."));
        importBox.setMaxLength(32767);
        this.addWidget(importBox);
        y += 30;

        this.layoutRoot.addElement(new ResponsiveButton(this.width / 2 - 100, y, 200, 20, Component.literal("\u00A7bImport Blueprint"), btn -> {
            String encoded = importBox.getValue();
            if (!encoded.isEmpty()) {
                PacketHandler.sendToServer(new LogisticsBlueprintPacket(networkId, encoded));
                this.minecraft.setScreen(parent);
            }
        }));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "Blueprint System", this.width / 2, 20, 0xFFFFFFFF);
        g.drawCenteredString(font, "\u00A77Share your logistics designs!", this.width / 2, 32, 0xFFAAAAAA);
        
        if (importBox != null) {
            importBox.render(g, mx, my, pt);
        }
    }
}
