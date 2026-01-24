package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.GraphCanvasComponent;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionNetworkPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.logistics.LogisticsCapability;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.LogisticsRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public class NetworkManagerScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private NetworkData networkData;
    private GraphCanvasComponent canvas;
    private int lastVersion = -1;
    private ResponsiveButton simButton;
    private ResponsiveButton connButton;

    public NetworkManagerScreen(Screen parent, UUID networkId) {
        super(Component.literal("Network Manager"));
        this.parent = parent;
        this.networkId = networkId;
    }

    @Override
    protected void setupLayout() {
        com.example.modmenu.client.ClientForgeEvents.viewedNetworkId = networkId;
        refreshData();

        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        if (networkData == null) return;

        int bx = 65;
        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 60, 20, Component.literal("Rename"), btn -> {
            this.minecraft.setScreen(new RenameNetworkScreen(this, networkId, networkData.networkName));
        }));
        bx += 65;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 100, 20, Component.literal("Add Physical Node"), btn -> {
            com.example.modmenu.client.ClientForgeEvents.networkLinkModeId = networkId;
            PacketHandler.sendToServer(new ActionNetworkPacket(18, networkId));
            this.minecraft.setScreen(null);
        }));
        bx += 105;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 100, 20, Component.literal("Add Virtual Node"), btn -> {
            // Pick PLAYER, MARKET or iterate CHAMBERS
            this.minecraft.setScreen(new PickVirtualNodeScreen(this, networkId));
        }));
        bx += 105;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 80, 20, Component.literal("Add Rule"), btn -> {
            this.minecraft.setScreen(new RuleConfigScreen(this, networkId, networkData, new LogisticsRule(), true));
        }));
        bx += 85;
        
        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 80, 20, Component.literal("Rules List"), btn -> {
            this.minecraft.setScreen(new RulesListScreen(this, networkId, networkData));
        }));
        bx += 85;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 80, 20, Component.literal("Groups"), btn -> {
            this.minecraft.setScreen(new NodeGroupsListScreen(this, networkId, networkData));
        }));
        bx += 85;

        String simTxt = networkData.simulationActive ? "\u00A7bSIM: ON" : "\u00A77SIM: OFF";
        simButton = new ResponsiveButton(bx, 10, 80, 20, Component.literal(simTxt), btn -> {
            networkData.simulationActive = !networkData.simulationActive;
            updateButtonTexts();
            PacketHandler.sendToServer(new ActionNetworkPacket(17, networkId));
        });
        this.layoutRoot.addElement(simButton);
        bx += 85;

        String connTxt = networkData.showConnections ? "\u00A7aConnections: ON" : "\u00A7cConnections: OFF";
        connButton = new ResponsiveButton(bx, 10, 100, 20, Component.literal(connTxt), btn -> {
            networkData.showConnections = !networkData.showConnections;
            updateButtonTexts();
            PacketHandler.sendToServer(new ActionNetworkPacket(16, networkId));
        });
        this.layoutRoot.addElement(connButton);

        canvas = new GraphCanvasComponent(10, 40, this.width - 20, this.height - 50, networkData, this);
        this.layoutRoot.addElement(canvas);

        // Network Stats Overlay (Top Right)
        this.layoutRoot.addElement(new UIElement(this.width - 160, 45, 150, 60) {
            @Override
            public void render(GuiGraphics g, int mx, int my, float pt) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xAA000000);
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF444444);
                
                int dy = getY() + 5;
                g.drawString(font, "\u00A7b\u00A7l[ NETWORK STATS ]", getX() + 5, dy, 0xFFFFFFFF);
                dy += 12;
                g.drawString(font, "Items: \u00A7e" + networkData.itemsMovedLastMin + "/min", getX() + 10, dy, 0xFFFFFFFF);
                dy += 10;
                g.drawString(font, "Energy: \u00A76" + networkData.energyMovedLastMin + " FE/min", getX() + 10, dy, 0xFFFFFFFF);
                dy += 10;
                g.drawString(font, "Fluids: \u00A7a" + networkData.fluidsMovedLastMin + " mB/min", getX() + 10, dy, 0xFFFFFFFF);
            }
        });
    }

    private void refreshData() {
        LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
            for (NetworkData nd : data.getNetworks()) {
                if (nd.networkId.equals(networkId)) {
                    // Diagnostic Buzz on error detected
                    if (this.networkData == null || !this.networkData.lastReport.equals(nd.lastReport)) {
                        boolean hasMissing = nd.nodes.stream().anyMatch(n -> n.isMissing);
                        if (hasMissing) {
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 2.0f));
                        }
                    }
                    this.networkData = nd;
                    break;
                }
            }
        });
    }

    private void updateButtonTexts() {
        if (networkData == null) return;
        if (simButton != null) {
            simButton.setText(Component.literal(networkData.simulationActive ? "\u00A7bSIM: ON" : "\u00A77SIM: OFF"));
        }
        if (connButton != null) {
            connButton.setText(Component.literal(networkData.showConnections ? "\u00A7aConnections: ON" : "\u00A7cConnections: OFF"));
        }
    }

    @Override
    public void onClose() {
        com.example.modmenu.client.ClientForgeEvents.viewedNetworkId = null;
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
            if (data.updateVersion != lastVersion) {
                refreshData();
                updateButtonTexts();
                if (canvas != null) canvas.setNetworkData(networkData);
                lastVersion = data.updateVersion;
            }
        });
        super.render(g, mx, my, pt);
    }
}
