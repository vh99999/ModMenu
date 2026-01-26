package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PickBiomeScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final Set<String> selectedBiomes;
    private final Consumer<List<String>> onConfirm;
    private ScrollableUIContainer list;
    private EditBox searchBox;

    public PickBiomeScreen(Screen parent, List<String> current, Consumer<List<String>> onConfirm) {
        super(Component.literal("Select Biomes"));
        this.parent = parent;
        this.selectedBiomes = new HashSet<>(current);
        this.onConfirm = onConfirm;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(this.width - 60, 10, 50, 20, Component.literal("\u00A7aApply"), btn -> {
            onConfirm.accept(new ArrayList<>(selectedBiomes));
            this.minecraft.setScreen(parent);
        }));

        searchBox = new EditBox(font, 70, 10, this.width - 140, 20, Component.literal("Search..."));
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
        int innerWidth = list.getWidth() - 20;

        Iterable<ResourceLocation> biomeKeys;
        if (Minecraft.getInstance().level != null) {
            biomeKeys = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.BIOME).keySet();
        } else {
            biomeKeys = ForgeRegistries.BIOMES.getKeys();
        }

        List<ResourceLocation> allBiomes = new ArrayList<>();
        biomeKeys.forEach(allBiomes::add);
        allBiomes.sort((a, b) -> a.toString().compareTo(b.toString()));

        for (ResourceLocation rl : allBiomes) {
            String id = rl.toString();
            if (!search.isEmpty() && !id.toLowerCase().contains(search)) continue;

            final String biomeId = id;
            boolean selected = selectedBiomes.contains(biomeId);
            String label = (selected ? "\u00A7a[X] " : "[ ] ") + biomeId;

            list.addElement(new ResponsiveButton(0, cy, innerWidth, 25, Component.literal(label), btn -> {
                if (selectedBiomes.contains(biomeId)) {
                    selectedBiomes.remove(biomeId);
                } else {
                    selectedBiomes.add(biomeId);
                }
                refreshList();
            }));
            cy += 27;
        }

        list.setContentHeight(cy + 20);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (searchBox != null) searchBox.render(g, mx, my, pt);
    }
}
