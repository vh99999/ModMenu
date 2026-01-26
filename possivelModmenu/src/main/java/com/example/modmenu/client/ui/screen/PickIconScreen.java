package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PickIconScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final Consumer<String> onPick;
    private EditBox searchBox;
    private ScrollableUIContainer grid;
    private final List<Item> allItems;

    public PickIconScreen(Screen parent, Consumer<String> onPick) {
        super(Component.literal("Pick Node Icon"));
        this.parent = parent;
        this.onPick = onPick;
        this.allItems = ForgeRegistries.ITEMS.getValues().stream()
            .filter(i -> i != Items.AIR)
            .sorted(Comparator.comparing(i -> i.getDescription().getString()))
            .collect(Collectors.toList());
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> this.minecraft.setScreen(parent)));

        searchBox = new EditBox(font, this.width / 2 - 100, 10, 200, 20, Component.literal("Search..."));
        searchBox.setResponder(s -> refreshGrid());
        this.addRenderableWidget(searchBox);

        grid = new ScrollableUIContainer(20, 40, this.width - 40, this.height - 60);
        this.layoutRoot.addElement(grid);
        refreshGrid();
    }

    private void refreshGrid() {
        grid.clearChildren();
        String search = searchBox.getValue().toLowerCase();
        List<Item> filtered = allItems.stream()
            .filter(i -> i.getDescription().getString().toLowerCase().contains(search) || 
                         ForgeRegistries.ITEMS.getKey(i).toString().toLowerCase().contains(search))
            .collect(Collectors.toList());

        int x = 0, y = 0;
        int cols = (grid.getWidth() - 10) / 20;
        if (cols < 1) cols = 1;

        for (Item item : filtered) {
            String itemId = ForgeRegistries.ITEMS.getKey(item).toString();
            grid.addElement(new UIElement(x * 20, y * 20, 18, 18) {
                @Override public void render(GuiGraphics g, int mx, int my, float pt) {
                    boolean hov = isMouseOver(mx, my);
                    g.fill(getX(), getY(), getX() + 18, getY() + 18, hov ? 0x66FFFFFF : 0x33FFFFFF);
                    g.renderItem(new ItemStack(item), getX() + 1, getY() + 1);
                }
                @Override public boolean mouseClicked(double mx, double my, int b) {
                    if (!isMouseOver(mx, my)) return false;
                    onPick.accept(itemId);
                    minecraft.setScreen(parent);
                    return true;
                }
            });
            if (++x >= cols) { x = 0; y++; }
        }
        grid.setContentHeight(y * 20 + 20);
    }
}
