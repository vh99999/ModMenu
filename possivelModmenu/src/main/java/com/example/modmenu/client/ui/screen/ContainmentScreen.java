package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class ContainmentScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ScrollableUIContainer list;

    public ContainmentScreen(Screen parent) {
        super(Component.literal("Virtual Containment"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(list);

        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();

        int currentY = 0;
        int rowHeight = 80;

        List<StorePriceManager.ChamberData> chambers = StorePriceManager.clientSkills.chambers;
        int unlocked = StorePriceManager.clientSkills.unlockedChambers;

        for (int i = 0; i < chambers.size(); i++) {
            list.addElement(new ChamberRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5, chambers.get(i), i));
            currentY += rowHeight;
        }

        // Add Empty Slots for unlocked but unused chambers
        for (int i = chambers.size(); i < unlocked; i++) {
            list.addElement(new UIElement(0, currentY, list.getWidth() - 10, rowHeight - 5) {
                @Override
                public void render(GuiGraphics g, int mx, int my, float pt) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x33FFFFFF);
                    g.drawCenteredString(Minecraft.getInstance().font, "Empty Chamber Slot", getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, 0xFFAAAAAA);
                }
            });
            currentY += rowHeight;
        }

        // Add Buy Button if more can be unlocked
        java.math.BigDecimal cost = new java.math.BigDecimal("5000");
        int chambersToCalc = unlocked - 1;
        if (chambersToCalc > 0) {
            int damped = StorePriceManager.dampedExponent(chambersToCalc);
            cost = cost.multiply(new java.math.BigDecimal("3").pow(damped))
                       .divide(new java.math.BigDecimal("2").pow(damped), 10, java.math.RoundingMode.HALF_UP);
        }
        cost = cost.setScale(0, java.math.RoundingMode.HALF_UP);

        list.addElement(new ResponsiveButton(0, currentY, list.getWidth() - 10, rowHeight - 5, Component.literal("§dUnlock Next Chamber Slot (" + cost + " SP)"), btn -> {
            com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(-1, 6));
            refreshList();
        }));
        currentY += rowHeight;

        list.setContentHeight(currentY + rowHeight);
    }

    private class ChamberRowComponent extends UIElement {
        private final StorePriceManager.ChamberData data;
        private final int index;
        private LivingEntity renderEntity;

        public ChamberRowComponent(int x, int y, int width, int height, StorePriceManager.ChamberData data, int index) {
            super(x, y, width, height);
            this.data = data;
            this.index = index;
            
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(data.mobId));
            if (type != null) {
                net.minecraft.world.entity.Entity e = type.create(Minecraft.getInstance().level);
                if (e instanceof LivingEntity le) {
                    if (data.isExact && data.nbt != null) le.load(data.nbt);
                    this.renderEntity = le;
                }
            }
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0x66FFFFFF : 0x33FFFFFF);

            if (renderEntity != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, getX() + 30, getY() + 70, 25, (float)(getX() + 30) - mx, (float)(getY() + 40) - my, renderEntity);
            }

            String name = data.customName != null ? data.customName : (data.isExcavation ? data.lootTableId : data.mobId);
            if (data.isExcavation) name = "Excavation: " + name.substring(name.lastIndexOf('/') + 1);
            g.drawString(Minecraft.getInstance().font, "Target: §6" + name, getX() + 60, getY() + 10, 0xFFFFFFFF);
            
            String status = data.paused ? "§cPAUSED" : "§aACTIVE";
            if (data.barteringMode) status += " §e(BARTER)";
            g.drawString(Minecraft.getInstance().font, "Status: " + status, getX() + 60, getY() + 22, 0xFFFFFFFF);
            
            String xpText = data.storedXP.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            g.drawString(Minecraft.getInstance().font, "XP: §a" + xpText, getX() + 60, getY() + 34, 0xFFFFFFFF);
            g.drawString(Minecraft.getInstance().font, "Loot: §e" + data.storedLoot.size() + " types", getX() + 60, getY() + 46, 0xFFFFFFFF);
            
            g.drawString(Minecraft.getInstance().font, "S:" + data.speedSlider + " T:" + data.threadSlider, getX() + 60, getY() + 58, 0xFFAAAAAA);

            if (!data.killerWeapon.isEmpty()) {
                g.drawString(Minecraft.getInstance().font, "Weapon:", getX() + 160, getY() + 22, 0xFFFFFFFF);
                g.renderItem(data.killerWeapon, getX() + 160, getY() + 34);
            }

            int bx = getX() + getWidth() - 85;
            renderBtn(g, bx, getY() + 5, 80, 18, "Harvest", mx, my);
            renderBtn(g, bx, getY() + 25, 80, 18, "Manage", mx, my);
            String pauseTxt = data.paused ? "Resume" : "Stop";
            renderBtn(g, bx, getY() + 45, 80, 18, pauseTxt, mx, my);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF666666 : 0xFF444444);
            g.drawCenteredString(Minecraft.getInstance().font, txt, bx + bw / 2, by + bh / 2 - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int bx = getX() + getWidth() - 85;
            if (mx >= bx && mx < bx + 80) {
                if (my >= getY() + 5 && my < getY() + 23) {
                    com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(index, 0));
                    return true;
                }
                if (my >= getY() + 25 && my < getY() + 43) {
                    Minecraft.getInstance().setScreen(new ChamberLootScreen(ContainmentScreen.this, index));
                    return true;
                }
                if (my >= getY() + 45 && my < getY() + 63) {
                    com.example.modmenu.network.PacketHandler.sendToServer(new com.example.modmenu.network.ActionChamberPacket(index, 7)); // 7: Toggle Pause
                    data.paused = !data.paused;
                    return true;
                }
            }
            return false;
        }
    }
}
