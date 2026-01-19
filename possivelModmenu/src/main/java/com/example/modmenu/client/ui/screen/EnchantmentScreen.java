package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.component.EnchantmentRowComponent;
import com.example.modmenu.client.ui.component.InventoryComponent;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.network.EnchantItemPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.StorePriceManager;
import team.lodestar.lodestone.systems.easing.Easing;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.stream.Collectors;

public class EnchantmentScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ItemStack selectedItem = ItemStack.EMPTY;
    private int selectedSlot = -1;
    private ScrollableUIContainer enchantmentList;
    private final java.util.Map<ResourceLocation, Integer> pendingEnchants = new java.util.HashMap<>();

    public EnchantmentScreen(Screen parent) {
        super(Component.literal("Custom Enchantment"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        // Horizontal layout for top buttons to avoid gaps
        com.example.modmenu.client.ui.layout.HorizontalLayoutContainer topButtons = new com.example.modmenu.client.ui.layout.HorizontalLayoutContainer(10, 10, this.width - 20, 25, 5);
        
        // Back Button
        topButtons.addElement(new ResponsiveButton(0, 0, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        if (StorePriceManager.isEditor) {
            topButtons.addElement(new ResponsiveButton(0, 0, 100, 20, Component.literal("Add All Enchants"), btn -> {
                PacketHandler.sendToServer(new com.example.modmenu.network.AdminActionPacket(com.example.modmenu.network.AdminActionPacket.Action.ADD_ALL_ENCHANTS));
            }));
        }

        // Buy Enchants Button
        topButtons.addElement(new ResponsiveButton(0, 0, 100, 20, Component.literal("Buy Enchants"), btn -> {
            if (!selectedItem.isEmpty() && !pendingEnchants.isEmpty()) {
                pendingEnchants.forEach((id, lvl) -> {
                    PacketHandler.sendToServer(new EnchantItemPacket(id, lvl, selectedSlot));
                });
                pendingEnchants.clear();
                // We'll wait for server sync to refresh
            }
        }));

        this.layoutRoot.addElement(topButtons);

        // Inventory at the bottom - anchored and responsive
        int invHeight = (int)(this.height * 0.4f);
        InventoryComponent inventory = new InventoryComponent(0, this.height - invHeight, this.width, invHeight, (stack, slot) -> {
            this.selectedItem = stack;
            this.selectedSlot = slot;
            refreshEnchantmentPanel();
        });
        this.layoutRoot.addElement(inventory);

        // Enchantment list on the right - custom panel
        int panelWidth = (int)(this.width * 0.45f);
        int panelHeight = this.height - invHeight - 50;
        enchantmentList = new ScrollableUIContainer(this.width - panelWidth - 10, 40, panelWidth, panelHeight);
        this.layoutRoot.addElement(enchantmentList);
        
        refreshEnchantmentPanel();
    }

    private void refreshEnchantmentPanel() {
        if (enchantmentList == null) return;
        enchantmentList.clearChildren();

        if (selectedItem.isEmpty()) return;

        Map<String, java.math.BigDecimal> prices = StorePriceManager.getAllEnchantPrices();
        int spacing = 4;
        int rowHeight = 35;
        VerticalLayoutContainer list = new VerticalLayoutContainer(0, 0, enchantmentList.getWidth() - 15, 0, spacing);
        
        for (Map.Entry<String, java.math.BigDecimal> entry : prices.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) continue;
            
            int currentLevel = pendingEnchants.getOrDefault(id, EnchantmentHelper.getItemEnchantmentLevel(ForgeRegistries.ENCHANTMENTS.getValue(id), selectedItem));

            list.addElement(new EnchantmentRowComponent(0, 0, list.getWidth(), rowHeight, id, entry.getValue(), currentLevel, (eid, lvl) -> {
                pendingEnchants.put(eid, lvl);
                refreshEnchantmentPanel();
            }));
        }

        enchantmentList.addElement(list);
        enchantmentList.setContentHeight(list.getHeight() + 20);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        if (!selectedItem.isEmpty()) {
            int centerX = this.width / 4 + 20;
            int centerY = (this.height - (int)(this.height * 0.4f)) / 2 + 10;
            
            // Subtle pulse animation using Lodestone Easing
            float time = (Minecraft.getInstance().level.getGameTime() + partialTick) / 20.0f;
            float pulse = (float) Math.sin(time * 2.0f) * 0.5f + 0.5f;
            float easedPulse = Easing.SINE_IN_OUT.ease(pulse, 0, 1, 1);
            
            // Subtle glow effect
            int alpha = (int)(30 + 20 * easedPulse);
            int glowColor = (alpha << 24) | 0x00AAFF;
            guiGraphics.fill(centerX - 50, centerY - 50, centerX + 50, centerY + 50, glowColor);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY, 0);
            float scale = 5.0f + easedPulse * 0.5f;
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.pose().translate(-8, -8, 0);
            guiGraphics.renderFakeItem(selectedItem, 0, 0);
            guiGraphics.pose().popPose();
            
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, selectedItem.getHoverName(), centerX, centerY + 60, 0xFFFFFFFF);
            
            // Draw current enchantments summary
            Map<Enchantment, Integer> enchants = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(selectedItem);
            int ey = centerY + 80;
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                guiGraphics.drawCenteredString(Minecraft.getInstance().font, "§b" + e.getKey().getFullname(e.getValue()).getString(), centerX, ey, 0xAAFFFF);
                ey += 12;
            }
        } else {
            int centerX = this.width / 4 + 20;
            int centerY = (this.height - (int)(this.height * 0.4f)) / 2;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, "Select an item to enchant", centerX, centerY, 0x888888);
        }
    }
}
