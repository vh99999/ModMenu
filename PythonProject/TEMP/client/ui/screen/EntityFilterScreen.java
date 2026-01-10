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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EntityFilterScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final List<String> targetList;
    private ScrollableUIContainer listContainer;
    private EditBox searchBox;

    private String currentMod = "ALL";

    public EntityFilterScreen(Screen parent, List<String> targetList) {
        super(Component.literal("Spawn Boost Targets"));
        this.parent = parent;
        this.targetList = targetList;
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
            List<String> mods = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                    .map(type -> ForgeRegistries.ENTITY_TYPES.getKey(type).getNamespace())
                    .distinct().sorted().collect(Collectors.toList());
            this.minecraft.setScreen(new ModFilterScreen(this, mods, mod -> {
                this.currentMod = mod;
                refreshList();
            }));
        }));

        topButtons.addElement(new ResponsiveButton(0, 0, 80, 20, Component.literal("Add All"), btn -> {
            List<EntityType<?>> toAdd = getFilteredEntities();
            for (EntityType<?> type : toAdd) {
                String id = ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
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

    private List<EntityType<?>> getFilteredEntities() {
        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        return ForgeRegistries.ENTITY_TYPES.getValues().stream()
                .filter(type -> {
                    // Filter out non-living entities (like boats, projectiles, etc)
                    net.minecraft.world.entity.MobCategory cat = type.getCategory();
                    return cat != net.minecraft.world.entity.MobCategory.MISC || type == EntityType.VILLAGER || type == EntityType.IRON_GOLEM || type == EntityType.SNOW_GOLEM;
                })
                .filter(type -> {
                    if (currentMod.equals("ALL")) return true;
                    return ForgeRegistries.ENTITY_TYPES.getKey(type).getNamespace().equals(currentMod);
                })
                .filter(type -> {
                    String name = type.getDescription().getString().toLowerCase();
                    String id = ForgeRegistries.ENTITY_TYPES.getKey(type).toString().toLowerCase();
                    return name.contains(search) || id.contains(search);
                })
                .sorted((a, b) -> a.getDescription().getString().compareToIgnoreCase(b.getDescription().getString()))
                .collect(Collectors.toList());
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.clearChildren();

        List<EntityType<?>> entities = getFilteredEntities();
        int rowHeight = 22;
        int spacing = 2;

        for (int i = 0; i < entities.size(); i++) {
            EntityType<?> type = entities.get(i);
            String id = ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
            boolean active = targetList.contains(id);
            
            listContainer.addElement(new ResponsiveButton(
                0, i * (rowHeight + spacing), 
                listContainer.getWidth() - 10, rowHeight, 
                Component.literal(type.getDescription().getString() + ": " + (active ? "§aON" : "§cOFF")), b -> {
                    if (targetList.contains(id)) {
                        targetList.remove(id);
                    } else {
                        targetList.add(id);
                    }
                    refreshList();
                }));
        }

        listContainer.setContentHeight(entities.size() * (rowHeight + spacing) + 10);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
