package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.*;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.NodeGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NodeGroupConfigScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private final NetworkData networkData;
    private final NodeGroup group;
    private final boolean isNew;
    private EditBox nameField;
    private ScrollableUIContainer nodeList;
    private final List<UUID> selectedNodes = new ArrayList<>();

    public NodeGroupConfigScreen(Screen parent, UUID networkId, NetworkData networkData, NodeGroup group, boolean isNew) {
        super(Component.literal(isNew ? "Create Group" : "Edit Group"));
        this.parent = parent;
        this.networkId = networkId;
        this.networkData = networkData;
        this.group = group;
        this.isNew = isNew;
        this.selectedNodes.addAll(group.nodeIds);
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new UIElement(70, 10, 100, 20) {
            @Override
            public void render(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, "Group Name:", getX(), getY() + 5, 0xFFFFFFFF);
            }
        });

        nameField = new EditBox(font, 150, 10, 150, 20, Component.literal("Group Name"));
        nameField.setValue(group.name != null ? group.name : "");
        this.addWidget(nameField);

        nodeList = new ScrollableUIContainer(10, 40, this.width - 20, this.height - 80);
        this.layoutRoot.addElement(nodeList);

        int currentY = 0;
        int rowHeight = 25;
        for (NetworkNode node : networkData.nodes) {
            if (node == null) continue;
            nodeList.addElement(new NodeRowComponent(0, currentY, nodeList.getWidth() - 10, rowHeight - 2, node));
            currentY += rowHeight;
        }
        nodeList.setContentHeight(currentY);

        this.layoutRoot.addElement(new ResponsiveButton(this.width / 2 - 50, this.height - 30, 100, 20, Component.literal("Save"), btn -> {
            group.name = nameField.getValue();
            group.nodeIds.clear();
            group.nodeIds.addAll(selectedNodes);
            
            if (isNew && !selectedNodes.isEmpty()) {
                int avgX = 0, avgY = 0;
                int count = 0;
                for (UUID id : selectedNodes) {
                    NetworkNode n = networkData.nodes.stream()
                            .filter(java.util.Objects::nonNull)
                            .filter(node -> node.nodeId.equals(id))
                            .findFirst().orElse(null);
                    if (n != null) {
                        avgX += n.guiX;
                        avgY += n.guiY;
                        count++;
                    }
                }
                if (count > 0) {
                    group.guiX = avgX / count;
                    group.guiY = avgY / count;
                }
            }

            PacketHandler.sendToServer(GroupManagementPacket.addUpdate(networkId, group));
            if (isNew && !networkData.groups.contains(group)) {
                networkData.groups.add(group);
            }
            this.minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        nameField.render(g, mx, my, pt);
    }

    private class NodeRowComponent extends UIElement {
        private final NetworkNode node;

        public NodeRowComponent(int x, int y, int width, int height, NetworkNode node) {
            super(x, y, width, height);
            this.node = node;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = isMouseOver(mx, my);
            boolean selected = selectedNodes.contains(node.nodeId);
            
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), selected ? 0x66AAFFAA : (hovered ? 0x66FFFFFF : 0x33FFFFFF));
            
            String iconId = node.iconItemId != null ? node.iconItemId : (node.nodeType.equals("BLOCK") ? node.blockId : null);
            if (iconId != null) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(iconId));
                if (item != null) {
                    g.renderItem(new ItemStack(item), getX() + 5, getY() + 3);
                }
            }

            String name = node.customName != null ? node.customName : node.nodeType;
            g.drawString(font, name, getX() + 25, getY() + 7, 0xFFFFFFFF);
            
            if (node.pos != null) {
                String posStr = "[" + node.pos.getX() + ", " + node.pos.getY() + ", " + node.pos.getZ() + "]";
                g.drawString(font, posStr, getX() + getWidth() - 150, getY() + 7, 0xFFAAAAAA);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (isMouseOver(mx, my)) {
                if (selectedNodes.contains(node.nodeId)) {
                    selectedNodes.remove(node.nodeId);
                } else {
                    selectedNodes.add(node.nodeId);
                }
                return true;
            }
            return false;
        }
    }
}
