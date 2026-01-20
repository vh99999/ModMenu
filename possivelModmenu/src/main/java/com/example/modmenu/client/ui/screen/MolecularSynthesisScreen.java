package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.SynthesisPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
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
import java.util.stream.Collectors;

public class MolecularSynthesisScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private EditBox searchBox;
    private ScrollableUIContainer itemGrid;
    private List<Item> filteredItems = new ArrayList<>();

    public MolecularSynthesisScreen(Screen parent) {
        super(Component.literal("Molecular Synthesis"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(this.font, this.width / 2 - 100, 40, 200, 20, Component.literal("Search..."));
        searchBox.setResponder(this::updateSearch);
        this.addRenderableWidget(searchBox);
        updateSearch("");
    }

    private void updateSearch(String query) {
        String q = query.toLowerCase();
        filteredItems = ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> ForgeRegistries.ITEMS.getKey(item).toString().contains(q) || 
                               item.getDescription().getString().toLowerCase().contains(q))
                .limit(1000)
                .collect(Collectors.toList());
        refreshGrid();
    }

    private void refreshGrid() {
        if (itemGrid == null) return;
        itemGrid.clearChildren();
        
        int slotSize = 40;
        int cols = Math.max(1, itemGrid.getWidth() / slotSize);
        
        for (int i = 0; i < filteredItems.size(); i++) {
            Item item = filteredItems.get(i);
            int r = i / cols;
            int c = i % cols;
            itemGrid.addElement(new SynthesisSlot(c * slotSize, r * slotSize, slotSize, slotSize, item));
        }
        itemGrid.setContentHeight((filteredItems.size() / cols + 1) * slotSize);
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        itemGrid = new ScrollableUIContainer(50, 70, this.width - 100, this.height - 100);
        this.layoutRoot.addElement(itemGrid);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "Molecular Synthesis (System Crafting)", this.width / 2, 20, 0xFFFFCC00);
        g.drawCenteredString(font, "Current Balance: §e$" + StorePriceManager.formatCurrency(StorePriceManager.playerMoney), this.width / 2, 32, 0xFFFFFFFF);
    }

    private class SynthesisSlot extends UIElement {
        private final Item item;
        private final ItemStack stack;

        public SynthesisSlot(int x, int y, int width, int height, Item item) {
            super(x, y, width, height);
            this.item = item;
            this.stack = new ItemStack(item);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            
            int lockState = !stack.isEmpty() ? stack.getOrCreateTag().getInt("modmenu_lock_state") : 0;
            int bgColor = hovered ? 0x66FFFFFF : 0x33FFFFFF;
            
            if (lockState == 1) { // Locked
                bgColor = hovered ? 0x66FF5555 : 0x33FF0000;
            } else if (lockState == 2) { // Frozen
                bgColor = hovered ? 0x665555FF : 0x3300AAFF;
            }

            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
            
            if (lockState == 1) {
                renderBorder(g, getX(), getY(), getWidth(), getHeight(), 0xFFFF0000);
            } else if (lockState == 2) {
                renderBorder(g, getX(), getY(), getWidth(), getHeight(), 0xFF00AAFF);
            }

            g.renderItem(stack, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            
            if (lockState == 2) {
                g.pose().pushPose();
                g.pose().translate(getX() + getWidth() - 10, getY() + 2, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(Minecraft.getInstance().font, "❄", 0, 0, 0xFFFFFFFF);
                g.pose().popPose();
            } else if (lockState == 1) {
                g.pose().pushPose();
                g.pose().translate(getX() + getWidth() - 10, getY() + 2, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(Minecraft.getInstance().font, "🔒", 0, 0, 0xFFFFFFFF);
                g.pose().popPose();
            }

            if (hovered) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(stack.getHoverName());
                java.math.BigDecimal cost = StorePriceManager.getBuyPrice(item).multiply(java.math.BigDecimal.valueOf(10));
                tooltip.add(Component.literal("§eSynthesis Cost: $" + StorePriceManager.formatCurrency(cost)));
                tooltip.add(Component.literal("§6Click to Synthesize"));
                addPostRenderTask(gui -> {
                    gui.renderComponentTooltip(Minecraft.getInstance().font, tooltip, absMouseX, absMouseY);
                });
            }
        }

        private void renderBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y, x + 1, y + h, color);
            g.fill(x + w - 1, y, x + w, y + h, color);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight()) {
                PacketHandler.sendToServer(new SynthesisPacket(ForgeRegistries.ITEMS.getKey(item)));
                return true;
            }
            return false;
        }
    }
}
