package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ModFilterScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final List<String> allMods;
    private final Consumer<String> onSelect;
    private EditBox searchBox;
    private ScrollableUIContainer modGrid;

    public ModFilterScreen(Screen parent, List<String> allMods, Consumer<String> onSelect) {
        super(Component.literal("Mod Filter"));
        this.parent = parent;
        this.allMods = allMods;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, this.width / 2 - 100, 20, 200, 18, Component.empty());
        searchBox.setHint(Component.literal("Search mods..."));
        searchBox.setResponder(s -> refreshGrid());
        this.addWidget(searchBox);
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        modGrid = new ScrollableUIContainer(50, 50, this.width - 100, this.height - 70);
        this.layoutRoot.addElement(modGrid);
        
        refreshGrid();
    }

    private void refreshGrid() {
        if (modGrid == null) return;
        modGrid.clearChildren();

        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        List<String> filtered = allMods.stream()
                .filter(m -> m.toLowerCase().contains(search))
                .collect(Collectors.toList());

        VerticalLayoutContainer list = new VerticalLayoutContainer(0, 0, modGrid.getWidth() - 15, 0, 4);
        
        list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal("ALL"), btn -> {
            onSelect.accept("ALL");
            this.minecraft.setScreen(parent);
        }));

        for (String mod : filtered) {
            String displayName = mod.substring(0, 1).toUpperCase() + mod.substring(1);
            list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal(displayName), btn -> {
                onSelect.accept(mod);
                this.minecraft.setScreen(parent);
            }));
        }

        modGrid.addElement(list);
        modGrid.setContentHeight(list.getHeight() + 10);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
