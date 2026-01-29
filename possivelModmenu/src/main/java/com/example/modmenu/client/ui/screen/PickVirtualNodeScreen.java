package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.*;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class PickVirtualNodeScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;

    public PickVirtualNodeScreen(Screen parent, UUID networkId) {
        super(Component.literal("Pick Virtual Node"));
        this.parent = parent;
        this.networkId = networkId;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        ScrollableUIContainer list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(list);

        int currentY = 0;

        // 1. Player Node
        list.addElement(new ResponsiveButton(0, currentY, list.getWidth() - 10, 20, Component.literal("Player Inventory"), btn -> {
            PacketHandler.sendToServer(NodeManagementPacket.addVirtual(networkId, "PLAYER", -1));
            this.minecraft.setScreen(parent);
        }));
        currentY += 25;

        // 2. Market Node
        list.addElement(new ResponsiveButton(0, currentY, list.getWidth() - 10, 20, Component.literal("Market (Sell Node)"), btn -> {
            PacketHandler.sendToServer(NodeManagementPacket.addVirtual(networkId, "MARKET", -1));
            this.minecraft.setScreen(parent);
        }));
        currentY += 25;

        // 3. Chambers
        for (int i = 0; i < StorePriceManager.clientSkills.chambers.size(); i++) {
            StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(i);
            String name = chamber.customName != null ? chamber.customName : chamber.mobId;
            int idx = i;
            list.addElement(new ResponsiveButton(0, currentY, list.getWidth() - 10, 20, Component.literal("Chamber #" + i + ": " + name), btn -> {
                PacketHandler.sendToServer(NodeManagementPacket.addVirtual(networkId, "CHAMBER", idx));
                this.minecraft.setScreen(parent);
            }));
            currentY += 25;
        }

        list.setContentHeight(currentY);
    }
}
