package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.ExecuteProtocolPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ExecutiveProtocolsScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ScrollableUIContainer list;

    public ExecutiveProtocolsScreen(Screen parent) {
        super(Component.literal("Executive Protocols"));
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
        int rowHeight = 50;

        addProtocol("Personal Nexus Sync", "Sets personal spawn point at current location.", 1000, 0);
        addProtocol("Sector Zero Alignment", "Overwrites World Spawn to current location.", 5000, 1);
        addProtocol("Dimensional Anchor", "Immune to forced teleports (Endermen, Chorus).", 500, 2);
        addProtocol("Inventory Preservation", "Permanently enables keepInventory for you.", 25000, 3);
        addProtocol("Neural Experience Backup", "Permanently retain 100% XP on death.", 10000, 4);
        addProtocol("Anti-Griefing Aura", "64-block zone where mobs cannot damage blocks.", 5000, 5);
        addProtocol("Emergency System Restore", "Pay to respawn at death point with all items.", 5000, 6);
        addProtocol("Global Registry Purge", "Reset Data Satiety for all mobs in the universe.", 100000, 7);
        addProtocol("Chronos Lock", "Permanently freeze world time and weather.", 2500, 8);
        addProtocol("Tectonic Stabilization", "Permanent immunity to fall and kinetic damage.", 7500, 9);
        addProtocol("Species Blacklist", "Remove specific mob types from spawning registries.", 15000, 10);
        addProtocol("Substrate Injection", "Transmute stone veins into any ore (target block).", 50, 11);
        addProtocol("Loot Table Overclock", "Guarantee best loot for the next 100 kills.", 10000, 12);
        addProtocol("Registry Editor", "Change an entity's type (e.g. Warden to Pig).", 250000, 13);
        addProtocol("Code Optimization", "Permanently reduce all future SP costs by 15%.", 500000, 14);
        addProtocol("God Strength Protocol", "Permanently multiply all base damage by 10x.", 1000000, 15);

        list.setContentHeight(16 * rowHeight);
    }

    private void addProtocol(String name, String desc, int cost, int id) {
        int y = list.getChildren().size() * 50;
        list.addElement(new ProtocolRowComponent(0, y, list.getWidth() - 10, 45, name, desc, cost, id));
    }

    private class ProtocolRowComponent extends UIElement {
        private final String name, desc;
        private final int cost, id;

        public ProtocolRowComponent(int x, int y, int width, int height, String name, String desc, int cost, int id) {
            super(x, y, width, height);
            this.name = name;
            this.desc = desc;
            this.cost = cost;
            this.id = id;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0xCC2D3A2D : 0xCC1A1A1A);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF00FF00);

            g.drawString(Minecraft.getInstance().font, "§a§l" + name, getX() + 5, getY() + 5, 0xFFFFFFFF);
            g.drawString(Minecraft.getInstance().font, "§7" + desc, getX() + 5, getY() + 18, 0xFFFFFFFF);
            
            int bx = getX() + getWidth() - 95;
            java.math.BigDecimal availableSP = StorePriceManager.clientSkills.totalSP.subtract(StorePriceManager.clientSkills.spentSP);
            boolean canAfford = availableSP.compareTo(java.math.BigDecimal.valueOf(cost)) >= 0;
            renderBtn(g, bx, getY() + 12, 90, 20, "§d" + cost + " SP", mx, my, canAfford);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my, boolean enabled) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov && enabled ? 0xFF444444 : 0xFF333333);
            if (!enabled) g.fill(bx, by, bx + bw, by + bh, 0x88CC0000);
            g.drawCenteredString(Minecraft.getInstance().font, txt, bx + bw / 2, by + bh / 2 - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int bx = getX() + getWidth() - 95;
            if (mx >= bx && mx < bx + 90 && my >= getY() + 12 && my < getY() + 32) {
                PacketHandler.sendToServer(new ExecuteProtocolPacket(id));
                return true;
            }
            return false;
        }
    }
}
