package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionChamberPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.UpdateAbilityPacket;
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
                this.minecraft.setScreen(new ItemFilterScreen(this, chamber.voidFilter, "Chamber Void Filter") {
                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        boolean result = super.mouseClicked(mx, my, button);
                        // When closing or changing, we might want to sync. 
                        // ItemFilterScreen's Back button already sends UpdateAbilityPacket, but that's for clientAbilities.
                        // We need a way to sync chamber data. 
                        // Let's just rely on the fact that we modified the live list in clientSkills.
                        // We should send a packet to update the server's chamber filter.
                        return result;
                    }
                    
                    @Override
                    public void removed() {
                        super.removed();
                        // Custom sync for chamber filter
                        PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, chamber.voidFilter)); 
                    }
                });
            }
        }));

        grid = new ScrollableUIContainer(50, 40, this.width - 150, this.height - 50);
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
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 4, itemIndex));
                } else if (button == 1) { // Right click: Void
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 5, itemIndex));
                }
                return true;
            }
            return false;
        }
    }
}
