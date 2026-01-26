package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.chat.Component;

public class MainMenuScreen extends BaseResponsiveLodestoneScreen {
    private boolean lastSystemState = false;
    private boolean lastGenesisState = false;
    
    public MainMenuScreen() {
        super(Component.literal("Main Menu"));
    }

    @Override
    protected void setupLayout() {
        // Initialize states on first setup to avoid immediate re-init loop
        this.lastSystemState = StorePriceManager.clientUnlockedHouses.contains("system_screen");
        this.lastGenesisState = StorePriceManager.clientUnlockedHouses.contains("dimension_configurator");

        // Uniform button sizing calculated based on screen width
        int buttonWidth = Math.max(160, (int)(this.width * 0.35f));
        int buttonHeight = 22;
        int spacing = 6; // Fixed spacing reused everywhere

        // Informations button top left
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 100, 20, Component.literal("Informations"), btn -> {
            this.minecraft.setScreen(new WikiScreen(this));
        }));
        
        // Single vertical layout container centered both horizontally and vertically
        VerticalLayoutContainer menuLayout = new VerticalLayoutContainer(0, 0, this.width, this.height, spacing);
        
        // 1. Store
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Store"), btn -> {
            this.minecraft.setScreen(new StoreScreen(this));
        }));
        
        // 2. Enchant
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Enchant"), btn -> {
            this.minecraft.setScreen(new com.example.modmenu.client.ui.screen.EnchantmentScreen(this));
        }));
        
        // 3. Effects
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Effects"), btn -> {
            this.minecraft.setScreen(new EffectsScreen(this));
        }));
        
        // 4. Abilities
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Abilities"), btn -> {
            this.minecraft.setScreen(new AbilitiesScreen(this));
        }));

        // 4.1 Networks
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Logistics Networks"), btn -> {
            this.minecraft.setScreen(new NetworkListScreen(this));
        }));

        // 4.2 Containment
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Virtual Containment"), btn -> {
            this.minecraft.setScreen(new ContainmentScreen(this));
        }));

        // 4.5 Skill Tree
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Skill Tree"), btn -> {
            this.minecraft.setScreen(new SkillTreeScreen(this));
        }));

        // 4.7 Diagnostics
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("System Diagnostics"), btn -> {
            this.minecraft.setScreen(new DiagnosticsScreen(this));
        }));

        // 5. Genesis Hub (Locked/Enter)
        boolean dimUnlocked = StorePriceManager.clientUnlockedHouses.contains("dimension_configurator");
        String dimLabel = dimUnlocked ? "Genesis Hub" : "Genesis Hub (Unlock: $100.000.000)";
        ResponsiveButton dimButton = new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal(dimLabel), btn -> {
            if (dimUnlocked) {
                this.minecraft.setScreen(new com.example.modmenu.client.ui.screen.GenesisHubScreen(this));
            } else {
                com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.UnlockFeaturePacket("dimension_configurator", java.math.BigDecimal.valueOf(100000000)));
            }
        });
        menuLayout.addElement(dimButton);

        // 6. System (Locked/Enter)
        boolean systemUnlocked = StorePriceManager.clientUnlockedHouses.contains("system_screen");
        String systemLabel = systemUnlocked ? "System" : "System (Unlock: $100.000.000)";
        ResponsiveButton systemButton = new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal(systemLabel), btn -> {
            if (systemUnlocked) {
                this.minecraft.setScreen(new SystemScreen(this));
            } else {
                com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.UnlockFeaturePacket("system_screen", java.math.BigDecimal.valueOf(100000000)));
            }
        });
        menuLayout.addElement(systemButton);
        
        // 7. Clear All
        menuLayout.addElement(new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal("Clear All"), btn -> {
            com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ClearEffectsPacket(true));
        }));
        
        this.layoutRoot.addElement(menuLayout);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dynamic Update Check: if unlocked status changed, rebuild layout
        boolean systemUnlocked = StorePriceManager.clientUnlockedHouses.contains("system_screen");
        boolean dimUnlocked = StorePriceManager.clientUnlockedHouses.contains("dimension_configurator");
        
        // Use hash or simple state check
        if (lastSystemState != systemUnlocked || lastGenesisState != dimUnlocked) {
            this.init(); // Re-initialize layout
            lastSystemState = systemUnlocked;
            lastGenesisState = dimUnlocked;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Render Title with a nice font effect
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.width / 2f, 50, 0);
        guiGraphics.pose().scale(2.0f, 2.0f, 1.0f);
        String title = "MOD MENU";
        guiGraphics.drawCenteredString(font, title, 0, 0, 0xFFFFFF);
        guiGraphics.pose().popPose();
    }
}
