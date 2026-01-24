package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionNetworkPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NodeGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class NodeGroupsListScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private NetworkData networkData;
    private ScrollableUIContainer list;

    public NodeGroupsListScreen(Screen parent, UUID networkId, NetworkData networkData) {
        super(Component.literal("Node Groups"));
        this.parent = parent;
        this.networkId = networkId;
        this.networkData = networkData;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(70, 10, 100, 20, Component.literal("Create Group"), btn -> {
            this.minecraft.setScreen(new NodeGroupConfigScreen(this, networkId, networkData, new NodeGroup(), true));
        }));

        list = new ScrollableUIContainer(10, 40, this.width - 20, this.height - 50);
        this.layoutRoot.addElement(list);

        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();

        int currentY = 0;
        int rowHeight = 35;

        for (NodeGroup group : networkData.groups) {
            list.addElement(new GroupRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5, group));
            currentY += rowHeight;
        }
        list.setContentHeight(currentY);
    }

    private class GroupRowComponent extends UIElement {
        private final NodeGroup group;

        public GroupRowComponent(int x, int y, int width, int height, NodeGroup group) {
            super(x, y, width, height);
            this.group = group;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = isMouseOver(mx, my);
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0x66FFFFFF : 0x33FFFFFF);

            g.drawString(font, group.name != null ? group.name : "Unnamed Group", getX() + 10, getY() + 8, 0xFFFFFFFF);
            g.drawString(font, group.nodeIds.size() + " Nodes", getX() + 150, getY() + 8, 0xFFAAAAAA);

            int bx = getX() + getWidth() - 155;
            renderBtn(g, bx, getY() + 3, 70, 20, "Edit", mx, my);
            bx += 75;
            renderBtn(g, bx, getY() + 3, 70, 20, "\u00A7cDelete", mx, my);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF666666 : 0xFF444444);
            g.drawCenteredString(font, txt, bx + bw / 2, by + bh / 2 - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isMouseOver(mx, my)) return false;
            int bx = getX() + getWidth() - 155;
            if (mx >= bx && mx < bx + 70) {
                Minecraft.getInstance().setScreen(new NodeGroupConfigScreen(NodeGroupsListScreen.this, networkId, networkData, group, false));
                return true;
            }
            bx += 75;
            if (mx >= bx && mx < bx + 70) {
                PacketHandler.sendToServer(ActionNetworkPacket.removeGroup(networkId, group.groupId));
                networkData.groups.remove(group);
                refreshList();
                return true;
            }
            return false;
        }
    }
}
