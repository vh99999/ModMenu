package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.UpdateAbilityPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.stream.Collectors;

public class ItemFilterScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final List<String> targetList;
    private final String titleStr;
    private ScrollableUIContainer listContainer;
    private EditBox searchBox;
    private String currentMod = "ALL";
    private boolean onlyInventory = false;

    public ItemFilterScreen(Screen parent, List<String> targetList, String title) {
        super(Component.literal(title));
        this.parent = parent;
        this.targetList = targetList;
        this.titleStr = title;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, this.width / 2 - 50, 20, 100, 18, Component.empty());
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(s -> refreshList());
        this.addWidget(searchBox);
    }

    @Override
    protected void setupLayout() {
        com.example.modmenu.client.ui.layout.HorizontalLayoutContainer topButtons = new com.example.modmenu.client.ui.layout.HorizontalLayoutContainer(10, 10, this.width - 20, 25, 5);

        topButtons.addElement(new ResponsiveButton(0, 0, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
            PacketHandler.sendToServer(new UpdateAbilityPacket(StorePriceManager.clientAbilities));
        }));

        topButtons.addElement(new ResponsiveButton(0, 0, 80, 20, Component.literal("Mod Filter"), btn -> {
            List<String> mods = ForgeRegistries.ITEMS.getValues().stream()
                    .map(item -> ForgeRegistries.ITEMS.getKey(item).getNamespace())
                    .distinct().sorted().collect(Collectors.toList());
            this.minecraft.setScreen(new ModFilterScreen(this, mods, mod -> {
                this.currentMod = mod;
                refreshList();
            }));
        }));

        topButtons.addElement(new ResponsiveButton(0, 0, 100, 20, Component.literal(onlyInventory ? "Inventory: ON" : "Inventory: OFF"), btn -> {
            onlyInventory = !onlyInventory;
            btn.setText(Component.literal(onlyInventory ? "Inventory: ON" : "Inventory: OFF"));
            refreshList();
        }));

        topButtons.addElement(new ResponsiveButton(0, 0, 80, 20, Component.literal("Add All"), btn -> {
            List<Item> toAdd = getFilteredItems();
            for (Item item : toAdd) {
                String id = ForgeRegistries.ITEMS.getKey(item).toString();
                if (!targetList.contains(id)) targetList.add(id);
            }
            refreshList();
        }));

        topButtons.addElement(new ResponsiveButton(0, 0, 80, 20, Component.literal("Clear All"), btn -> {
            targetList.clear();
            refreshList();
        }));

        this.layoutRoot.addElement(topButtons);

        listContainer = new ScrollableUIContainer(50, 50, this.width - 100, this.height - 70);
        this.layoutRoot.addElement(listContainer);
        
        refreshList();
    }

    private List<Item> getFilteredItems() {
        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        List<Item> inventoryItems = null;
        if (onlyInventory) {
            inventoryItems = new java.util.ArrayList<>();
            for (ItemStack stack : this.minecraft.player.getInventory().items) {
                if (!stack.isEmpty()) inventoryItems.add(stack.getItem());
            }
        }
        final List<Item> finalInventoryItems = inventoryItems;

        return ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> {
                    if (onlyInventory && finalInventoryItems != null) {
                        return finalInventoryItems.contains(item);
                    }
                    return true;
                })
                .filter(item -> {
                    if (currentMod.equals("ALL")) return true;
                    return ForgeRegistries.ITEMS.getKey(item).getNamespace().equals(currentMod);
                })
                .filter(item -> {
                    String name = item.getDescription().getString().toLowerCase();
                    String id = ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase();
                    return name.contains(search) || id.contains(search);
                })
                .sorted((a, b) -> a.getDescription().getString().compareToIgnoreCase(b.getDescription().getString()))
                .collect(Collectors.toList());
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.clearChildren();

        List<Item> items = getFilteredItems();
        int rowHeight = 22;
        int spacing = 2;

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String id = ForgeRegistries.ITEMS.getKey(item).toString();
            boolean active = targetList.contains(id);
            
            listContainer.addElement(new ResponsiveButton(
                0, i * (rowHeight + spacing), 
                listContainer.getWidth() - 10, rowHeight, 
                Component.literal(item.getDescription().getString() + ": " + (active ? "\u00A7aON" : "\u00A7cOFF")), b -> {
                    if (targetList.contains(id)) {
                        targetList.remove(id);
                    } else {
                        targetList.add(id);
                    }
                    refreshList();
                }));
        }

        listContainer.setContentHeight(items.size() * (rowHeight + spacing) + 10);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
