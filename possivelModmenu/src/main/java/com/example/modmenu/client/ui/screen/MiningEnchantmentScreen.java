package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.UpdateAbilityPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

import com.example.modmenu.client.ui.component.EnchantmentRowComponent;
import java.util.HashMap;

public class MiningEnchantmentScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final Map<String, Integer> enchantMap;
    private ScrollableUIContainer listContainer;
    private final Map<ResourceLocation, Integer> pendingEnchants = new HashMap<>();

    public MiningEnchantmentScreen(Screen parent, Map<String, Integer> enchantMap) {
        super(Component.literal("Mining Enchantments"));
        this.parent = parent;
        this.enchantMap = enchantMap;
        // Initialize pending with current
        enchantMap.forEach((id, lvl) -> pendingEnchants.put(ResourceLocation.tryParse(id), lvl));
    }

    @Override
    protected void setupLayout() {
        com.example.modmenu.client.ui.layout.HorizontalLayoutContainer topButtons = new com.example.modmenu.client.ui.layout.HorizontalLayoutContainer(10, 10, this.width - 20, 25, 5);
        
        topButtons.addElement(new ResponsiveButton(0, 0, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
            PacketHandler.sendToServer(new UpdateAbilityPacket(StorePriceManager.clientAbilities));
        }));

        topButtons.addElement(new ResponsiveButton(0, 0, 100, 20, Component.literal("Save Changes"), btn -> {
            enchantMap.clear();
            pendingEnchants.forEach((id, lvl) -> {
                if (lvl > 0) enchantMap.put(id.toString(), lvl);
            });
            PacketHandler.sendToServer(new UpdateAbilityPacket(StorePriceManager.clientAbilities));
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(topButtons);

        listContainer = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 60);
        this.layoutRoot.addElement(listContainer);
        
        refreshList();
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.clearChildren();

        Map<String, Integer> prices = StorePriceManager.getAllEnchantPrices();
        int rowHeight = 35;
        int spacing = 4;
        int i = 0;

        for (Map.Entry<String, Integer> entry : prices.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) continue;
            
            int currentLevel = pendingEnchants.getOrDefault(id, 0);

            listContainer.addElement(new EnchantmentRowComponent(
                0, i * (rowHeight + spacing), 
                listContainer.getWidth() - 15, rowHeight, 
                id, 0, currentLevel, (eid, lvl) -> {
                    pendingEnchants.put(eid, lvl);
                    refreshList();
                }));
            i++;
        }

        listContainer.setContentHeight(i * (rowHeight + spacing) + 10);
    }
}
