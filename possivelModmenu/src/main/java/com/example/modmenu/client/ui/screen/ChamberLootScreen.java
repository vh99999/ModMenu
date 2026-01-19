package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionChamberPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.UpdateAbilityPacket;
import com.example.modmenu.store.SkillManager;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class ChamberLootScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final int chamberIndex;
    private ScrollableUIContainer grid;
    private LivingEntity renderEntity;
    private int lastVersion = -1;
    private long lastSyncTime = -1;

    public ChamberLootScreen(Screen parent, int chamberIndex) {
        super(Component.literal("Chamber Management"));
        this.parent = parent;
        this.chamberIndex = chamberIndex;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        int bx = 70;
        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 80, 20, Component.literal("Collect All"), btn -> {
            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 1));
        }));
        bx += 85;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 80, 20, Component.literal("Void All"), btn -> {
            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 2));
        }));
        bx += 85;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 80, 20, Component.literal("Collect XP"), btn -> {
            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 0));
        }));
        bx += 85;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 120, 20, Component.literal("Set Killer Weapon"), btn -> {
            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 3));
        }));
        bx += 125;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 100, 20, Component.literal("Reroll Loot"), btn -> {
            PacketHandler.sendToServer(new com.example.modmenu.network.RerollLootPacket(chamberIndex, true));
        }));
        bx += 105;

        this.layoutRoot.addElement(new ResponsiveButton(bx, 10, 100, 20, Component.literal("Auto-Void Filter"), btn -> {
            if (chamberIndex >= 0 && chamberIndex < StorePriceManager.clientSkills.chambers.size()) {
                StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(chamberIndex);
                if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "VIRT_ADVANCED_FILTERING") > 0) {
                    this.minecraft.setScreen(new AdvancedFilterScreen(this, chamberIndex));
                } else {
                    this.minecraft.setScreen(new ItemFilterScreen(this, chamber.voidFilter, "Chamber Void Filter") {
                        @Override
                        public void removed() {
                            super.removed();
                            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, chamber.voidFilter)); 
                        }
                    });
                }
            }
        }));
        bx += 105;

        // Sliders and Toggles panel on the left
        int leftX = 5;
        int currentY = 40;
        
        if (chamberIndex >= 0 && chamberIndex < StorePriceManager.clientSkills.chambers.size()) {
            StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(chamberIndex);
            
            // Simulation Speed Slider
            int maxSpeed = SkillManager.getActiveRank(StorePriceManager.clientSkills, "VIRT_CLOCK_SPEED");
            if (maxSpeed > 0) {
                this.layoutRoot.addElement(new UIElement(leftX, currentY, 140, 30) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        g.drawString(font, "Speed: " + chamber.speedSlider + "/" + maxSpeed, getX(), getY(), 0xFFFFFFFF);
                        g.fill(getX(), getY() + 12, getX() + 100, getY() + 14, 0xFF444444);
                        float progress = (chamber.speedSlider - 1) / 19.0f;
                        g.fill(getX(), getY() + 12, getX() + (int)(100 * progress), getY() + 14, 0xFF00AAFF);
                    }
                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        if (mx >= getX() && mx <= getX() + 100 && my >= getY() + 10 && my <= getY() + 20) {
                            int newVal = 1 + (int)((mx - getX()) / 100.0 * 19);
                            chamber.speedSlider = Math.max(1, Math.min(maxSpeed, newVal));
                            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 12, null, chamber.speedSlider));
                            return true;
                        }
                        return false;
                    }
                });
                currentY += 35;
            }

            // Multi-Threading Slider
            int maxThread = SkillManager.getActiveRank(StorePriceManager.clientSkills, "VIRT_MULTI_THREAD");
            if (maxThread > 0) {
                this.layoutRoot.addElement(new UIElement(leftX, currentY, 140, 30) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        g.drawString(font, "Threads: " + chamber.threadSlider + "/" + maxThread, getX(), getY(), 0xFFFFFFFF);
                        g.fill(getX(), getY() + 12, getX() + 100, getY() + 14, 0xFF444444);
                        float progress = (chamber.threadSlider - 1) / 19.0f;
                        g.fill(getX(), getY() + 12, getX() + (int)(100 * progress), getY() + 14, 0xFF00AAFF);
                    }
                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        if (mx >= getX() && mx <= getX() + 100 && my >= getY() + 10 && my <= getY() + 20) {
                            int newVal = 1 + (int)((mx - getX()) / 100.0 * 19);
                            chamber.threadSlider = Math.max(1, Math.min(maxThread, newVal));
                            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 13, null, chamber.threadSlider));
                            return true;
                        }
                        return false;
                    }
                });
                currentY += 35;
            }

            // Bartering Toggle
            if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "VIRT_BARTERING_PROTOCOL") > 0 && chamber.mobId.contains("piglin")) {
                this.layoutRoot.addElement(new ResponsiveButton(leftX, currentY, 100, 20, Component.literal("Barter: " + (chamber.barteringMode ? "§aON" : "§cOFF")), btn -> {
                    chamber.barteringMode = !chamber.barteringMode;
                    btn.setText(Component.literal("Barter: " + (chamber.barteringMode ? "§aON" : "§cOFF")));
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 9));
                }));
                currentY += 25;
                
                // Input Buffer Button
                this.layoutRoot.addElement(new ResponsiveButton(leftX, currentY, 100, 20, Component.literal("Input: " + chamber.inputBuffer.size()), btn -> {
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 16)); // Put held into input
                }));
                this.layoutRoot.addElement(new UIElement(leftX + 105, currentY, 20, 20) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFFFF0000);
                        g.drawCenteredString(font, "X", getX() + 10, getY() + 6, 0xFFFFFFFF);
                    }
                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        if (mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight()) {
                            PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 15)); // Clear input
                            return true;
                        }
                        return false;
                    }
                });
                currentY += 25;
            }

            // Condensation Mode
            if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "VIRT_LOOT_CONDENSATION") > 0) {
                String[] modes = {"OFF", "SAFE", "ALL"};
                this.layoutRoot.addElement(new ResponsiveButton(leftX, currentY, 100, 20, Component.literal("Condense: " + modes[chamber.condensationMode]), btn -> {
                    chamber.condensationMode = (chamber.condensationMode + 1) % 3;
                    btn.setText(Component.literal("Condense: " + modes[chamber.condensationMode]));
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 10));
                }));
                currentY += 25;
            }
        }

        grid = new ScrollableUIContainer(150, 40, this.width - 250, this.height - 50);
        this.layoutRoot.addElement(grid);

        PacketHandler.sendToServer(new com.example.modmenu.network.RequestChamberLootPacket(chamberIndex));
        refreshGrid();
    }

    private void refreshGrid() {
        if (grid == null) return;
        grid.clearChildren();

        if (chamberIndex < 0 || chamberIndex >= StorePriceManager.clientSkills.chambers.size()) return;
        StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(chamberIndex);
        
        List<ItemStack> loot = chamber.storedLoot;
        int slotSize = 40;
        int cols = grid.getWidth() / slotSize;
        if (cols <= 0) cols = 1;

        for (int i = 0; i < loot.size(); i++) {
            int r = i / cols;
            int c = i % cols;
            grid.addElement(new LootSlotComponent(c * slotSize, r * slotSize, slotSize, slotSize, loot.get(i), i));
        }

        grid.setContentHeight((loot.size() / cols + 1) * slotSize);
        
        // Initialize render entity
        if (renderEntity == null || !ForgeRegistries.ENTITY_TYPES.getKey(renderEntity.getType()).toString().equals(chamber.mobId)) {
            net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.tryParse(chamber.mobId));
            if (type != null) {
                net.minecraft.world.entity.Entity e = type.create(Minecraft.getInstance().level);
                if (e instanceof LivingEntity le) {
                    if (chamber.isExact && chamber.nbt != null) le.load(chamber.nbt);
                    renderEntity = le;
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Dynamic Refresh: Check if chamber data changed
        if (chamberIndex >= 0 && chamberIndex < StorePriceManager.clientSkills.chambers.size()) {
            StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(chamberIndex);
            if (chamber.updateVersion != lastVersion) {
                if (System.currentTimeMillis() - lastSyncTime > 1000) { // Throttled request
                    PacketHandler.sendToServer(new com.example.modmenu.network.RequestChamberLootPacket(chamberIndex));
                    lastSyncTime = System.currentTimeMillis();
                }
                refreshGrid();
                lastVersion = chamber.updateVersion;
            }
        }

        super.render(g, mx, my, pt);
        
        if (chamberIndex >= 0 && chamberIndex < StorePriceManager.clientSkills.chambers.size()) {
            StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(chamberIndex);
            
            // Draw Mob Info
            int infoX = this.width - 95;
            String xpText = chamber.storedXP.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            g.drawString(font, "XP: §a" + xpText, infoX, 50, 0xFFFFFFFF);
            
            if (renderEntity != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, this.width - 50, 150, 30, (float)(this.width - 50) - mx, (float)(150 - 50) - my, renderEntity);
            }
            
            if (!chamber.killerWeapon.isEmpty()) {
                g.drawString(font, "Weapon:", infoX, 160, 0xFFFFFFFF);
                g.renderItem(chamber.killerWeapon, infoX + 10, 175);
                if (mx >= infoX + 10 && mx < infoX + 30 && my >= 175 && my < 195) {
                    addPostRenderTask(gui -> {
                        gui.renderTooltip(font, chamber.killerWeapon, absMouseX, absMouseY);
                    });
                }
            }
        }
    }

    private class LootSlotComponent extends UIElement {
        private final ItemStack stack;
        private final int itemIndex;

        public LootSlotComponent(int x, int y, int width, int height, ItemStack stack, int itemIndex) {
            super(x, y, width, height);
            this.stack = stack;
            this.itemIndex = itemIndex;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0x66FFFFFF : 0x33FFFFFF);
            g.renderItem(stack, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            g.renderItemDecorations(Minecraft.getInstance().font, stack, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            
            if (hovered) {
                addPostRenderTask(gui -> {
                    gui.renderTooltip(Minecraft.getInstance().font, stack, absMouseX, absMouseY);
                });
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight()) {
                if (button == 0) { // Left click: Collect
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 4, itemIndex, stack.getItem(), stack.getTag()));
                } else if (button == 1) { // Right click: Void
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 5, itemIndex, stack.getItem(), stack.getTag()));
                } else if (button == 2) { // Middle click: Set Yield Target
                    String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    Minecraft.getInstance().setScreen(new SetYieldTargetScreen(ChamberLootScreen.this, chamberIndex, id));
                }
                return true;
            }
            return false;
        }
    }
}
