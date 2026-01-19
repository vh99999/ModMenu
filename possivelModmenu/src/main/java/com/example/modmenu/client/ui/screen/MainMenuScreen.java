package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.chat.Component;

public class MainMenuScreen extends BaseResponsiveLodestoneScreen {
    private boolean lastHouseState = false;
    private boolean lastSystemState = false;
    
    public MainMenuScreen() {
        super(Component.literal("Main Menu"));
    }

    @Override
    protected void setupLayout() {
        // Initialize states on first setup to avoid immediate re-init loop
        this.lastHouseState = StorePriceManager.clientUnlockedHouses.contains("mining_dimension");
        this.lastSystemState = StorePriceManager.clientUnlockedHouses.contains("system_screen");

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

        
        // 5. House (Locked/Travel)
        boolean houseUnlocked = StorePriceManager.clientUnlockedHouses.contains("mining_dimension");
        String houseLabel = houseUnlocked ? "House (Enter)" : "House (Unlock: $100.000.000)";
        ResponsiveButton houseButton = new ResponsiveButton(0, 0, buttonWidth, buttonHeight, Component.literal(houseLabel), btn -> {
            if (houseUnlocked) {
                com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.TeleportToMiningDimensionPacket());
            } else {
                com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.UnlockFeaturePacket("mining_dimension", java.math.BigDecimal.valueOf(100000000)));
            }
        });
        menuLayout.addElement(houseButton);
        
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
        boolean houseUnlocked = StorePriceManager.clientUnlockedHouses.contains("mining_dimension");
        boolean systemUnlocked = StorePriceManager.clientUnlockedHouses.contains("system_screen");
        
        // Use hash or simple state check
        if (lastHouseState != houseUnlocked || lastSystemState != systemUnlocked) {
            this.init(); // Re-initialize layout
            lastHouseState = houseUnlocked;
            lastSystemState = systemUnlocked;
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
