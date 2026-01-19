package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.EffectRowComponent;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.ToggleEffectPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EffectsScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private EditBox searchBox;
    private ScrollableUIContainer effectList;
    private List<Map.Entry<String, BigDecimal>> allEffects = new ArrayList<>();
    private List<Map.Entry<String, BigDecimal>> visibleEffects = new ArrayList<>();

    public EffectsScreen(Screen parent) {
        super(Component.literal("Effects Store"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        allEffects = new ArrayList<>(StorePriceManager.getAllEffectBasePrices().entrySet());
        
        searchBox = new EditBox(font, this.width / 2 - 75, 20, 150, 18, Component.empty());
        searchBox.setHint(Component.literal("Search effects..."));
        searchBox.setResponder(s -> updateFilter());
        this.addWidget(searchBox);
        
        updateFilter();
    }

    @Override
    protected void setupLayout() {
        com.example.modmenu.client.ui.layout.HorizontalLayoutContainer topButtons = new com.example.modmenu.client.ui.layout.HorizontalLayoutContainer(10, 10, this.width - 20, 25, 5);

        topButtons.addElement(new ResponsiveButton(0, 0, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        if (StorePriceManager.isEditor) {
            topButtons.addElement(new ResponsiveButton(0, 0, 100, 20, Component.literal("Add All Effects"), btn -> {
                PacketHandler.sendToServer(new com.example.modmenu.network.AdminActionPacket(com.example.modmenu.network.AdminActionPacket.Action.ADD_ALL_EFFECTS));
            }));
        }

        // Clear All Effects Button
        topButtons.addElement(new ResponsiveButton(0, 0, 100, 20, Component.literal("Clear Effects"), btn -> {
            PacketHandler.sendToServer(new com.example.modmenu.network.ClearEffectsPacket(false));
        }));

        this.layoutRoot.addElement(topButtons);

        effectList = new ScrollableUIContainer(50, 50, this.width - 100, this.height - 70);
        this.layoutRoot.addElement(effectList);
        
        refreshEffects();
    }

    private void updateFilter() {
        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        visibleEffects = allEffects.stream()
                .filter(entry -> {
                    MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(entry.getKey()));
                    String name = (effect != null ? effect.getDisplayName().getString() : entry.getKey()).toLowerCase();
                    return name.contains(search) || entry.getKey().toLowerCase().contains(search);
                })
                .collect(Collectors.toList());
        refreshEffects();
    }

    private void refreshEffects() {
        if (effectList == null) return;
        effectList.clearChildren();
        
        int rowHeight = 35;
        for (int i = 0; i < visibleEffects.size(); i++) {
            Map.Entry<String, java.math.BigDecimal> entry = visibleEffects.get(i);
            effectList.addElement(new EffectRowComponent(
                0, i * rowHeight, effectList.getWidth() - 10, rowHeight - 5,
                entry.getKey(), entry.getValue(),
                (id, level) -> {
                    PacketHandler.sendToServer(new ToggleEffectPacket(id, level));
                }
            ));
        }
        effectList.setContentHeight(visibleEffects.size() * rowHeight);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        
        String drain = "Drain: -$" + StorePriceManager.formatCurrency(StorePriceManager.playerDrain) + "/s";
        guiGraphics.drawString(font, drain, this.width - font.width(drain) - 10, 10, 0xFF5555);
    }
}
