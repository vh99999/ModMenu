package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.AttributeUpgradePacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class SystemScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ScrollableUIContainer list;
    private java.util.Map<String, Double> lastBonuses = new java.util.HashMap<>();

    public SystemScreen(Screen parent) {
        super(Component.literal("System Upgrades"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(70, 10, 150, 20, Component.literal("Executive Protocols"), btn -> {
            this.minecraft.setScreen(new ExecutiveProtocolsScreen(this));
        }));

        list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(list);
        
        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();
        
        int rowHeight = 40;
        int currentY = 0;

        List<AttributeData> attributes = new ArrayList<>();
        
        for (Attribute attr : ForgeRegistries.ATTRIBUTES.getValues()) {
            ResourceLocation rl = ForgeRegistries.ATTRIBUTES.getKey(attr);
            if (rl == null) continue;
            
            if (rl.getNamespace().equals("minecraft")) {
                String path = rl.getPath();
                if (path.startsWith("generic.") || path.startsWith("player.")) {
                    String id = rl.toString();
                    String name = Component.translatable(attr.getDescriptionId()).getString();
                    double bonus = StorePriceManager.clientAttributeBonuses.getOrDefault(id, 0.0);
                    attributes.add(new AttributeData(id, name, bonus));
                }
            }
        }
        
        // Sort attributes by name
        attributes.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        
        for (int i = 0; i < attributes.size(); i++) {
            AttributeData data = attributes.get(i);
            list.addElement(new AttributeRowComponent(0, currentY + i * rowHeight, list.getWidth() - 10, rowHeight - 5, data.id, data.name, data.bonus));
        }
        list.setContentHeight(currentY + attributes.size() * rowHeight);
        lastBonuses = new java.util.HashMap<>(StorePriceManager.clientAttributeBonuses);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!lastBonuses.equals(StorePriceManager.clientAttributeBonuses)) {
            refreshList();
        }
        super.render(g, mx, my, pt);
    }

    private static class AttributeData {
        final String id, name;
        final double bonus;
        AttributeData(String id, String name, double bonus) {
            this.id = id; this.name = name; this.bonus = bonus;
        }
    }

    private class AttributeRowComponent extends UIElement {
        private final String id;
        private final String name;
        private final double bonus;

        public AttributeRowComponent(int x, int y, int width, int height, String id, String name, double bonus) {
            super(x, y, width, height);
            this.id = id;
            this.name = name;
            this.bonus = bonus;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0xCC2D2D3A : 0xCC1A1A1A);
            
            int borderColor = hovered ? 0xFF00AAFF : 0xFF444444;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + 1, borderColor);
            g.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), borderColor);
            g.fill(getX(), getY(), getX() + 1, getY() + getHeight(), borderColor);
            g.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), borderColor);

            g.drawString(Minecraft.getInstance().font, "§l" + name, getX() + 10, getY() + 10, 0xFFFFFFFF);
            g.drawString(Minecraft.getInstance().font, "Bonus: +" + String.format("%.1f", bonus), getX() + 10, getY() + 22, 0xFF55FF55);

            int valX = getX() + getWidth() - 50;
            renderBtn(g, valX - 35, getY() + 12, 20, 16, "-", mx, my);
            g.drawCenteredString(Minecraft.getInstance().font, String.valueOf((int)bonus), valX, getY() + 16, 0xFFFFFFFF);
            renderBtn(g, valX + 15, getY() + 12, 20, 16, "+", mx, my);
            
            String costText = "$1.000.000";
            g.drawString(Minecraft.getInstance().font, costText, valX - 100, getY() + 16, 0xFFFFFF55);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF444444 : 0xFF333333);
            g.drawCenteredString(Minecraft.getInstance().font, txt, bx + bw / 2, by + (bh - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int valX = getX() + getWidth() - 50;
            if (mx >= valX - 35 && mx < valX - 15 && my >= getY() + 12 && my < getY() + 28) {
                PacketHandler.sendToServer(new AttributeUpgradePacket(id, false));
                return true;
            }
            if (mx >= valX + 15 && mx < valX + 35 && my >= getY() + 12 && my < getY() + 28) {
                PacketHandler.sendToServer(new AttributeUpgradePacket(id, true));
                return true;
            }
            return false;
        }
    }

}
