package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.component.StoreItemComponent;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.PurchasePacket;
import com.example.modmenu.network.SellAllPacket;
import com.example.modmenu.network.SellPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StoreScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private EditBox searchBox;
    private EditBox quantityBox;
    private boolean isSellMode = false;
    private boolean isSetPriceMode = false;
    private String currentCategory = "ALL";
    private String currentMod = "ALL";
    private ScrollableUIContainer itemGrid;
    private final List<StoreItem> allItems = new ArrayList<>();
    private List<StoreItem> visibleItems = new ArrayList<>();
    
    private EditBox priceEditBox;
    private Item itemToEdit;

    public StoreScreen(Screen parent) {
        super(Component.literal("Store"));
        this.parent = parent;
        loadItems();
    }

    private void loadItems() {
        allItems.clear();
        ForgeRegistries.ITEMS.getValues().stream()
                .filter(i -> i != Items.AIR)
                .forEach(i -> allItems.add(new StoreItem(i)));
        updateFilter();
    }

    @Override
    protected void init() {
        super.init();
        
        int topY = 15;
        // Search Box
        searchBox = new EditBox(font, this.width / 2 - 120, topY, 120, 18, Component.empty());
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(s -> updateFilter());
        this.addWidget(searchBox);

        // Quantity Box
        quantityBox = new EditBox(font, this.width / 2 + 5, topY, 30, 18, Component.empty());
        quantityBox.setValue("1");
        quantityBox.setFilter(s -> s.matches("\\d*"));
        this.addWidget(quantityBox);

        // Price Edit Box (Hidden by default)
        priceEditBox = new EditBox(font, this.width / 2 - 50, this.height / 2 - 10, 100, 20, Component.empty());
        priceEditBox.setFilter(s -> s.matches("\\d*"));
        priceEditBox.setVisible(false);
        this.addWidget(priceEditBox);
    }

    @Override
    protected void setupLayout() {
        // Top Bar Layout
        com.example.modmenu.client.ui.layout.HorizontalLayoutContainer topBar = new com.example.modmenu.client.ui.layout.HorizontalLayoutContainer(this.width / 2 + 40, 15, this.width / 2 - 50, 20, 5);

        // Mode Toggle
        topBar.addElement(new ResponsiveButton(0, 0, 80, 18, 
                Component.literal(isSellMode ? "Mode: SELL" : "Mode: BUY"), btn -> {
            isSellMode = !isSellMode;
            btn.setText(Component.literal(isSellMode ? "Mode: SELL" : "Mode: BUY"));
            updateFilter();
        }));

        // Sell All Button
        topBar.addElement(new ResponsiveButton(0, 0, 70, 18, Component.literal("SELL ALL"), btn -> {
            PacketHandler.sendToServer(new SellAllPacket("ALL"));
        }));

        // Editor: Reset Defaults
        if (StorePriceManager.isEditor) {
            topBar.addElement(new ResponsiveButton(0, 0, 80, 18, 
                    Component.literal("Reset Defaults"), btn -> {
                PacketHandler.sendToServer(new com.example.modmenu.network.AdminActionPacket(com.example.modmenu.network.AdminActionPacket.Action.RESET_DEFAULT_PRICES));
            }));
        }
        this.layoutRoot.addElement(topBar);

        // Back Button
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        // Sidebar for categories and filter
        int sidebarWidth = 80;
        ScrollableUIContainer sidebar = new ScrollableUIContainer(5, 45, sidebarWidth, this.height - 55);
        VerticalLayoutContainer sidebarLayout = new VerticalLayoutContainer(0, 0, sidebarWidth, 0, 4);
        
        // Categories
        addHeader(sidebarLayout, "CATEGORIES");
        String[] categories = {"ALL", "BUILDING", "COMBAT", "FOOD", "TOOLS", "REDSTONE", "MISC"};
        for (String cat : categories) {
            sidebarLayout.addElement(new ResponsiveButton(0, 0, sidebarWidth - 15, 18, Component.literal(cat), btn -> {
                currentCategory = cat;
                currentMod = "ALL";
                updateFilter();
            }));
        }

        // Filter Button
        sidebarLayout.addElement(new ResponsiveButton(0, 5, sidebarWidth - 15, 20, Component.literal("Filter"), btn -> {
            this.minecraft.setScreen(new ModFilterScreen(this, getMods(), mod -> {
                this.currentMod = mod;
                if (!mod.equals("ALL")) this.currentCategory = "ALL";
                updateFilter();
            }));
        }));

        sidebar.addElement(sidebarLayout);
        sidebar.setContentHeight(sidebarLayout.getChildren().stream().mapToInt(e -> e.getHeight() + 4).sum() + 10);
        this.layoutRoot.addElement(sidebar);

        // Item Grid
        int gridX = sidebarWidth + 10;
        itemGrid = new ScrollableUIContainer(gridX, 45, this.width - gridX - 10, this.height - 55);
        this.layoutRoot.addElement(itemGrid);
        
        refreshGrid();
    }

    private void addHeader(VerticalLayoutContainer list, String title) {
        list.addElement(new UIContainer(0, 0, list.getWidth(), 15) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, "§7" + title, getX() + 2, getY() + 4, 0xAAAAAA);
            }
        });
    }

    private List<String> getMods() {
        return allItems.stream()
                .filter(i -> i.item != null)
                .map(i -> ForgeRegistries.ITEMS.getKey(i.item).getNamespace())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private void updateFilter() {
        if (!isSellMode) {
            boolean hasDimension = allItems.stream().anyMatch(i -> "mining_dimension".equals(i.specialId));
            boolean unlocked = StorePriceManager.clientUnlockedHouses.contains("mining_dimension");
            if (hasDimension && unlocked) {
                loadItems();
                return; // loadItems calls updateFilter again
            }
        }
        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        visibleItems = allItems.stream()
                .filter(i -> {
                    if (isSellMode) {
                        if (i.item == null) return false;
                        return this.minecraft.player.getInventory().contains(new ItemStack(i.item));
                    }
                    return true;
                })
                .filter(i -> {
                    if (i.item == null) return i.specialId.toLowerCase().contains(search);
                    String name = i.item.getDescription().getString().toLowerCase();
                    String id = ForgeRegistries.ITEMS.getKey(i.item).toString().toLowerCase();
                    return name.contains(search) || id.contains(search);
                })
                .filter(this::matchesCategory)
                .filter(i -> {
                    if (currentMod.equals("ALL")) return true;
                    if (i.item == null) return false;
                    return ForgeRegistries.ITEMS.getKey(i.item).getNamespace().equals(currentMod);
                })
                .collect(Collectors.toList());
        refreshGrid();
    }

    private boolean matchesCategory(StoreItem i) {
        if (currentCategory.equals("ALL")) return true;
        if (i.item == null) return false;
        String id = ForgeRegistries.ITEMS.getKey(i.item).toString();
        return switch (currentCategory) {
            case "COMBAT" -> id.contains("sword") || id.contains("armor") || id.contains("shield") || id.contains("bow") || id.contains("arrow") || id.contains("trident");
            case "TOOLS" -> id.contains("pickaxe") || id.contains("axe") || id.contains("shovel") || id.contains("hoe") || id.contains("shears");
            case "FOOD" -> i.item.isEdible() || id.contains("apple") || id.contains("beef") || id.contains("pork") || id.contains("chicken");
            case "REDSTONE" -> id.contains("redstone") || id.contains("piston") || id.contains("repeater") || id.contains("observer") || id.contains("lever") || id.contains("button");
            case "BUILDING" -> id.contains("block") || id.contains("brick") || id.contains("stone") || id.contains("plank") || id.contains("log") || id.contains("glass") || id.contains("slab") || id.contains("stair");
            case "MISC" -> true; // Default for others
            default -> true;
        };
    }

    private void refreshGrid() {
        if (itemGrid == null) return;
        itemGrid.clearChildren();
        
        int slotSize = 50;
        int spacing = 2;
        int cols = Math.max(1, itemGrid.getWidth() / (slotSize + spacing));
        
        for (int i = 0; i < visibleItems.size(); i++) {
            StoreItem item = visibleItems.get(i);
            int row = i / cols;
            int col = i % cols;
            
            itemGrid.addElement(new StoreItemComponent(
                col * (slotSize + spacing), 
                row * (slotSize + spacing), 
                slotSize, slotSize, 
                item.item, item.specialId, isSellMode,
                (it, sid) -> {
                    int qty = getQuantity();
                    if (isSetPriceMode) {
                        if (it != null) PacketHandler.sendToServer(new com.example.modmenu.network.UpdatePricePacket(it, qty));
                        return;
                    }
                    if (isSellMode) {
                        if (it != null) PacketHandler.sendToServer(new SellPacket(it, qty));
                    } else {
                        if (it != null) PacketHandler.sendToServer(new PurchasePacket(it, qty));
                        else if (sid != null) PacketHandler.sendToServer(new PurchasePacket(sid));
                    }
                },
                (it, sid) -> {
                    if (StorePriceManager.isEditor && it != null) {
                        itemToEdit = it;
                        priceEditBox.setVisible(true);
                        priceEditBox.setFocused(true);
                        priceEditBox.setValue(String.valueOf(isSellMode ? StorePriceManager.getSellPrice(it) : StorePriceManager.getBuyPrice(it)));
                    }
                }
            ));
        }
        
        int rows = (int) Math.ceil(visibleItems.size() / (float) cols);
        itemGrid.setContentHeight(rows * (slotSize + spacing));
    }

    private int getQuantity() {
        try { return Math.max(1, Integer.parseInt(quantityBox.getValue())); }
        catch (Exception e) { return 1; }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (priceEditBox.isVisible() && (keyCode == 257 || keyCode == 335)) { // ENTER
            try {
                int newPrice = Integer.parseInt(priceEditBox.getValue());
                if (itemToEdit != null) {
                    PacketHandler.sendToServer(new com.example.modmenu.network.UpdatePricePacket(itemToEdit, newPrice));
                }
            } catch (Exception ignored) {}
            priceEditBox.setVisible(false);
            itemToEdit = null;
            return true;
        }
        if (keyCode == 256) { // ESC
            if (priceEditBox.isVisible()) {
                priceEditBox.setVisible(false);
                itemToEdit = null;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        quantityBox.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Render Balance
        String balance = "Balance: $" + StorePriceManager.formatCurrency(StorePriceManager.playerMoney);
        guiGraphics.drawString(font, balance, this.width - font.width(balance) - 10, 10, 0xFFFF55);

        if (priceEditBox.isVisible()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);
            guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);
            guiGraphics.drawCenteredString(font, "Set New Price for " + itemToEdit.getDescription().getString(), this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
            priceEditBox.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }
    }

    private static class StoreItem {
        final Item item;
        final String specialId;
        StoreItem(Item i) { this.item = i; this.specialId = null; }
        StoreItem(String sid) { this.item = null; this.specialId = sid; }
    }
}
