package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class SaturatedItemsScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ScrollableUIContainer list;

    public SaturatedItemsScreen(Screen parent) {
        super(Component.literal("Market Saturation Registry"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 60);
        this.layoutRoot.addElement(list);
        
        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();
        
        int rowHeight = 40;
        int currentY = 0;

        Map<String, Long> volume = StorePriceManager.clientSoldVolume;
        List<String> itemIds = new ArrayList<>(volume.keySet());
        
        // Filter those with volume > 0 and sort by volume descending
        itemIds.removeIf(id -> volume.get(id) <= 0);
        itemIds.sort((a, b) -> volume.get(b).compareTo(volume.get(a)));

        for (String id : itemIds) {
            long val = volume.get(id);
            list.addElement(new SaturationRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5, id, val));
            currentY += rowHeight;
        }
        list.setContentHeight(currentY);
    }

    private class SaturationRowComponent extends UIElement {
        private final String itemId;
        private final long volume;
        private final ItemStack stack;

        public SaturationRowComponent(int x, int y, int width, int height, String itemId, long volume) {
            super(x, y, width, height);
            this.itemId = itemId;
            this.volume = volume;
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
            this.stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0xCC2D2D3A : 0xCC1A1A1A);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF444444);

            if (!stack.isEmpty()) {
                g.renderFakeItem(stack, getX() + 5, getY() + (getHeight() - 16) / 2);
            }

            String name = stack.isEmpty() ? itemId : stack.getHoverName().getString();
            g.drawString(Minecraft.getInstance().font, name, getX() + 30, getY() + 5, 0xFFFFFFFF);
            
            double factor = 1.0;
            if (volume > 1000) {
                factor = Math.pow(0.95, volume / 1000.0);
            }
            
            String info = "Volume: \u00A7e" + volume + " \u00A77| Efficiency: " + (factor < 1.0 ? "\u00A7c" : "\u00A7a") + (int)(factor * 100) + "%";
            g.drawString(Minecraft.getInstance().font, info, getX() + 30, getY() + 18, 0xFFAAAAAA);
            
            if (hovered && !stack.isEmpty()) {
                addPostRenderTask(gui -> {
                    gui.renderTooltip(Minecraft.getInstance().font, stack, absMouseX, absMouseY);
                });
            }
        }
    }
}
