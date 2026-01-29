package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.NodeGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class PickTargetScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final NetworkData data;
    private final BiConsumer<UUID, Boolean> onSelect;
    private ScrollableUIContainer list;
    private NodeGroup selectedGroup = null;
    private net.minecraft.client.gui.components.EditBox searchBox;

    public PickTargetScreen(Screen parent, NetworkData data, BiConsumer<UUID, Boolean> onSelect) {
        super(Component.literal("Select Target"));
        this.parent = parent;
        this.data = data;
        this.onSelect = onSelect;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            if (selectedGroup != null) {
                selectedGroup = null;
                init(minecraft, width, height);
            } else {
                this.minecraft.setScreen(parent);
            }
        }));

        searchBox = new net.minecraft.client.gui.components.EditBox(font, 70, 10, 150, 20, Component.literal("Search..."));
        searchBox.setResponder(s -> refreshList());
        this.addWidget(searchBox);

        list = new ScrollableUIContainer(20, 40, this.width - 40, this.height - 60);
        this.layoutRoot.addElement(list);

        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();
        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        int cy = 0;
        if (selectedGroup == null) {
            // Render Groups
            for (NodeGroup g : data.groups) {
                if (!search.isEmpty() && !g.name.toLowerCase().contains(search)) continue;
                final NodeGroup group = g;
                list.addElement(new ResponsiveButton(0, cy, list.getWidth() - 20, 25, Component.literal("[GROUP] " + (group.name != null ? group.name : "Unnamed")), btn -> {
                    if (btn.lastClickedButton == 1) { // Right click selects the group itself
                        onSelect.accept(group.groupId, true);
                        minecraft.setScreen(parent);
                    } else { // Left click expands
                        selectedGroup = group;
                        searchBox.setValue("");
                        init(minecraft, width, height);
                    }
                }));
                cy += 27;
            }
            // Render Loose Nodes
            Set<UUID> groupedIds = data.groups.stream()
                .flatMap(g -> g.nodeIds.stream())
                .collect(Collectors.toSet());

            for (NetworkNode n : data.nodes) {
                if (n == null) continue;
                if (!groupedIds.contains(n.nodeId)) {
                    String name = n.customName != null ? n.customName : n.nodeType;
                    if (!search.isEmpty() && !name.toLowerCase().contains(search) && (n.blockId == null || !n.blockId.toLowerCase().contains(search))) continue;
                    
                    final NetworkNode node = n;
                    list.addElement(new ResponsiveButton(0, cy, list.getWidth() - 20, 25, Component.literal("      " + name), btn -> {
                        onSelect.accept(node.nodeId, false);
                        minecraft.setScreen(parent);
                    }) {
                        @Override
                        public void render(GuiGraphics g, int mx, int my, float pt) {
                            super.render(g, mx, my, pt);
                            String iconId = node.iconItemId != null ? node.iconItemId : (node.nodeType.equals("BLOCK") ? node.blockId : null);
                            if (iconId != null) {
                                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(iconId));
                                if (item != null) {
                                    g.renderItem(new ItemStack(item), getX() + 5, getY() + 4);
                                }
                            }
                        }
                    });
                    cy += 27;
                }
            }
        } else {
            // Render Nodes in Group
            list.addElement(new ResponsiveButton(0, cy, list.getWidth() - 20, 25, Component.literal("<- Back to Groups"), btn -> {
                selectedGroup = null;
                init(minecraft, width, height);
            }));
            cy += 27;

            list.addElement(new ResponsiveButton(0, cy, list.getWidth() - 20, 25, Component.literal("\u00A7aSELECT ENTIRE GROUP: " + (selectedGroup.name != null ? selectedGroup.name : "Unnamed")), btn -> {
                onSelect.accept(selectedGroup.groupId, true);
                minecraft.setScreen(parent);
            }));
            cy += 27;

            for (UUID id : selectedGroup.nodeIds) {
                NetworkNode n = data.nodes.stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(node -> node.nodeId.equals(id))
                        .findFirst().orElse(null);
                if (n == null) continue;
                String name = n.customName != null ? n.customName : n.nodeType;
                if (!search.isEmpty() && !name.toLowerCase().contains(search) && (n.blockId == null || !n.blockId.toLowerCase().contains(search))) continue;

                final NetworkNode node = n;
                list.addElement(new ResponsiveButton(0, cy, list.getWidth() - 20, 25, Component.literal("      " + name), btn -> {
                    onSelect.accept(node.nodeId, false);
                    minecraft.setScreen(parent);
                }) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        super.render(g, mx, my, pt);
                        String iconId = node.iconItemId != null ? node.iconItemId : (node.nodeType.equals("BLOCK") ? node.blockId : null);
                        if (iconId != null) {
                            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(iconId));
                            if (item != null) {
                                g.renderItem(new ItemStack(item), getX() + 5, getY() + 4);
                            }
                        }
                    }
                });
                cy += 27;
            }
        }
        list.setContentHeight(cy + 20);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (searchBox != null) searchBox.render(g, mx, my, pt);
    }
}
