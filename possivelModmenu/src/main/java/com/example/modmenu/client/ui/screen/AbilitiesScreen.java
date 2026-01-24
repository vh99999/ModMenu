package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.client.ui.component.AbilitySettingComponent;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.UpdateAbilityPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AbilitiesScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ScrollableUIContainer content;
    private StorePriceManager.AbilitySettings lastSettings;

    public AbilitiesScreen(Screen parent) {
        super(Component.literal("Abilities"));
        this.parent = parent;
        this.lastSettings = new StorePriceManager.AbilitySettings();
        this.lastSettings.copyFrom(StorePriceManager.clientAbilities);
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        content = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(content);
        
        refreshContent();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!areSettingsEqual(lastSettings, StorePriceManager.clientAbilities)) {
            lastSettings.copyFrom(StorePriceManager.clientAbilities);
            refreshContent();
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private boolean areSettingsEqual(StorePriceManager.AbilitySettings a, StorePriceManager.AbilitySettings b) {
        return a.miningActive == b.miningActive &&
               a.autoSell == b.autoSell &&
               a.itemMagnetActive == b.itemMagnetActive &&
               a.xpMagnetActive == b.xpMagnetActive &&
               a.flightActive == b.flightActive &&
               a.sureKillActive == b.sureKillActive &&
               a.noAggroActive == b.noAggroActive &&
               a.captureActive == b.captureActive &&
               a.areaMiningActive == b.areaMiningActive &&
               a.growCropsActive == b.growCropsActive;
    }

    private void refreshContent() {
        if (content == null) return;
        content.clearChildren();
        
        VerticalLayoutContainer list = new VerticalLayoutContainer(0, 0, content.getWidth() - 10, 0, 5);
        StorePriceManager.AbilitySettings s = StorePriceManager.clientAbilities;

        // Mining
        addHeader(list, "MINING");
        addToggle(list, "Mining Active", s.miningActive, () -> { 
            s.miningActive = !s.miningActive; 
            if (s.miningActive) { s.focusedMiningActive = false; s.areaMiningActive = false; }
            sync(); 
        });
        addSetting(list, "Mining Range", String.valueOf(s.miningRange), () -> { s.miningRange = Math.max(1, s.miningRange - 1); sync(); }, () -> { s.miningRange = Math.min(10000, s.miningRange + 1); sync(); });
        
        addToggle(list, "Focused Mining", s.focusedMiningActive, () -> { 
            s.focusedMiningActive = !s.focusedMiningActive; 
            if (s.focusedMiningActive) { s.miningActive = false; s.areaMiningActive = false; }
            sync(); 
        });
        addSetting(list, "Focused Range", String.valueOf(s.focusedMiningRange), () -> { s.focusedMiningRange = Math.max(1, s.focusedMiningRange - 1); sync(); }, () -> { s.focusedMiningRange = Math.min(10000, s.focusedMiningRange + 1); sync(); });
        
        list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal("Configure Mining Blacklist"), btn -> {
            this.minecraft.setScreen(new ItemFilterScreen(this, s.miningBlacklist, "Mining Blacklist"));
        }));

        addToggle(list, "Mining Auto Sell", s.autoSell, () -> { s.autoSell = !s.autoSell; sync(); });

        addToggle(list, "Use Enchantments", s.useEnchantments, () -> { s.useEnchantments = !s.useEnchantments; sync(); });
        list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal("Configure Mining Enchants"), btn -> {
            this.minecraft.setScreen(new MiningEnchantmentScreen(this, s.miningEnchants));
        }));

        addToggle(list, "Area Mining", s.areaMiningActive, () -> { 
            s.areaMiningActive = !s.areaMiningActive; 
            if (s.areaMiningActive) { s.miningActive = false; s.focusedMiningActive = false; }
            sync(); 
        });
        addSetting(list, "Area Size", String.valueOf(s.areaMiningSize), () -> { s.areaMiningSize = Math.max(1, s.areaMiningSize - 2); sync(); }, () -> { s.areaMiningSize = Math.min(1000, s.areaMiningSize + 2); sync(); });

        // Automation
        addHeader(list, "AUTOMATION");
        addToggle(list, "Item Repair Active", s.repairActive, () -> { s.repairActive = !s.repairActive; sync(); });
        addToggle(list, "Item Magnet", s.itemMagnetActive, () -> { s.itemMagnetActive = !s.itemMagnetActive; sync(); });
        addSetting(list, "Item Magnet Speed", String.valueOf(s.itemMagnetOpsPerTick), () -> { s.itemMagnetOpsPerTick = Math.max(1, s.itemMagnetOpsPerTick - 1); sync(); }, () -> { s.itemMagnetOpsPerTick = Math.min(1000000, s.itemMagnetOpsPerTick + 1); sync(); });
        addSetting(list, "Item Magnet Range", String.valueOf(s.itemMagnetRange), () -> { s.itemMagnetRange = Math.max(1, s.itemMagnetRange - 1); sync(); }, () -> { s.itemMagnetRange = Math.min(10000, s.itemMagnetRange + 1); sync(); });
        
        addToggle(list, "XP Magnet", s.xpMagnetActive, () -> { s.xpMagnetActive = !s.xpMagnetActive; sync(); });
        addSetting(list, "XP Magnet Speed", String.valueOf(s.xpMagnetOpsPerTick), () -> { s.xpMagnetOpsPerTick = Math.max(1, s.xpMagnetOpsPerTick - 1); sync(); }, () -> { s.xpMagnetOpsPerTick = Math.min(1000000, s.xpMagnetOpsPerTick + 1); sync(); });
        addSetting(list, "XP Magnet Range", String.valueOf(s.xpMagnetRange), () -> { s.xpMagnetRange = Math.max(1, s.xpMagnetRange - 1); sync(); }, () -> { s.xpMagnetRange = Math.min(10000, s.xpMagnetRange + 1); sync(); });

        addToggle(list, "Auto Seller", s.autoSellerActive, () -> { s.autoSellerActive = !s.autoSellerActive; sync(); });
        addToggle(list, "Auto Seller Mode: " + (s.autoSellerIsBlacklist ? "Blacklist" : "Whitelist"), s.autoSellerIsBlacklist, () -> { s.autoSellerIsBlacklist = !s.autoSellerIsBlacklist; sync(); });
        list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal("Configure Auto Seller List"), btn -> {
            this.minecraft.setScreen(new ItemFilterScreen(this, s.autoSellerWhitelist, "Auto Seller List"));
        }));

        addToggle(list, "Sell All Mode: " + (s.sellAllWhitelistActive ? "Whitelist" : "Blacklist"), s.sellAllWhitelistActive, () -> { s.sellAllWhitelistActive = !s.sellAllWhitelistActive; sync(); });
        list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal("Configure Sell All Whitelist"), btn -> {
            this.minecraft.setScreen(new ItemFilterScreen(this, s.sellAllWhitelist, "Sell All Whitelist"));
        }));
        list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal("Configure Sell All Blacklist"), btn -> {
            this.minecraft.setScreen(new ItemFilterScreen(this, s.sellAllBlacklist, "Sell All Blacklist"));
        }));

        addToggle(list, "Grow Crops", s.growCropsActive, () -> { s.growCropsActive = !s.growCropsActive; sync(); });
        addSetting(list, "Crop Range", String.valueOf(s.growCropsRange), () -> { s.growCropsRange = Math.max(1, s.growCropsRange - 1); sync(); }, () -> { s.growCropsRange = Math.min(64, s.growCropsRange + 1); sync(); });

        // Defense
        addHeader(list, "DEFENSE");
        addToggle(list, "Damage Cancel", s.damageCancelActive, () -> { s.damageCancelActive = !s.damageCancelActive; sync(); });
        addToggle(list, "No Aggro", s.noAggroActive, () -> { s.noAggroActive = !s.noAggroActive; sync(); });
        addToggle(list, "Capture Mode", s.captureActive, () -> { s.captureActive = !s.captureActive; sync(); });

        // Visuals
        addHeader(list, "VISUALS");
        addToggle(list, "Chest Highlight", s.chestHighlightActive, () -> { s.chestHighlightActive = !s.chestHighlightActive; sync(); });
        addSetting(list, "Chest Range", String.valueOf(s.chestHighlightRange), () -> { s.chestHighlightRange = Math.max(1, s.chestHighlightRange - 1); sync(); }, () -> { s.chestHighlightRange = Math.min(10000, s.chestHighlightRange + 1); sync(); });
        
        addToggle(list, "Trap Highlight", s.trapHighlightActive, () -> { s.trapHighlightActive = !s.trapHighlightActive; sync(); });
        addSetting(list, "Trap Range", String.valueOf(s.trapHighlightRange), () -> { s.trapHighlightRange = Math.max(1, s.trapHighlightRange - 1); sync(); }, () -> { s.trapHighlightRange = Math.min(10000, s.trapHighlightRange + 1); sync(); });
        
        addToggle(list, "Entity ESP", s.entityESPActive, () -> { s.entityESPActive = !s.entityESPActive; sync(); });
        addSetting(list, "ESP Range", String.valueOf(s.entityESPRange), () -> { s.entityESPRange = Math.max(1, s.entityESPRange - 1); sync(); }, () -> { s.entityESPRange = Math.min(10000, s.entityESPRange + 1); sync(); });

        // Utility
        addHeader(list, "UTILITY");
        addToggle(list, "Spawn Boost", s.spawnBoostActive, () -> { s.spawnBoostActive = !s.spawnBoostActive; sync(); });
        list.addElement(new ResponsiveButton(0, 0, list.getWidth(), 20, Component.literal("Configure Spawn Boost Targets"), btn -> {
            this.minecraft.setScreen(new EntityFilterScreen(this, s.spawnBoostTargets));
        }));
        addSetting(list, "Boost Mult", String.format("%.1f", s.spawnBoostMultiplier), () -> { s.spawnBoostMultiplier = Math.max(1.0, s.spawnBoostMultiplier - 0.5); sync(); }, () -> { s.spawnBoostMultiplier = Math.min(1000000.0, s.spawnBoostMultiplier + 0.5); sync(); });

        // Movement / Combat
        addHeader(list, "MOVEMENT & COMBAT");
        addToggle(list, "Flight", s.flightActive, () -> { s.flightActive = !s.flightActive; sync(); });
        addToggle(list, "Step Assist", s.stepAssistActive, () -> { s.stepAssistActive = !s.stepAssistActive; sync(); });
        addSetting(list, "Step Height", String.format("%.1f", s.stepAssistHeight), () -> { s.stepAssistHeight = Math.max(0.5f, s.stepAssistHeight - 0.5f); sync(); }, () -> { s.stepAssistHeight = Math.min(1000.0f, s.stepAssistHeight + 0.5f); sync(); });
        addToggle(list, "Sure Kill", s.sureKillActive, () -> { s.sureKillActive = !s.sureKillActive; sync(); });

        content.addElement(list);
        content.setContentHeight(list.getHeight() + 20);
    }

    private void addHeader(VerticalLayoutContainer list, String title) {
        list.addElement(new UIContainer(0, 0, list.getWidth(), 20) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, "\u00A7l" + title, getX() + 2, getY() + 6, 0xAAAAAA);
                g.fill(getX(), getY() + 18, getX() + getWidth(), getY() + 19, 0xFF888888);
            }
        });
    }

    private void addToggle(VerticalLayoutContainer list, String label, boolean active, Runnable toggle) {
        ResponsiveButton btn = new ResponsiveButton(0, 0, list.getWidth(), 20, 
            Component.literal(label + ": " + (active ? "ON" : "OFF")), b -> {
                toggle.run();
                refreshContent();
            });
        if (active) btn.setText(Component.literal("\u00A7a" + label + ": ON"));
        else btn.setText(Component.literal("\u00A7c" + label + ": OFF"));
        list.addElement(btn);
    }

    private void addSetting(VerticalLayoutContainer list, String label, String value, Runnable dec, Runnable inc) {
        list.addElement(new AbilitySettingComponent(0, 0, list.getWidth(), 20, label, value, () -> { dec.run(); refreshContent(); }, () -> { inc.run(); refreshContent(); }));
    }

    private void sync() {
        PacketHandler.sendToServer(new UpdateAbilityPacket(StorePriceManager.clientAbilities));
    }
}
