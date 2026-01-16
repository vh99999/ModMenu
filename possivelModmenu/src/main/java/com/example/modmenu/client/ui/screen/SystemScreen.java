package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.AttributeUpgradePacket;
import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.ai.ControlMode;
import com.example.modmenu.ai.AIHandler;
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

        list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(list);
        
        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();
        
        int rowHeight = 40;
        int currentY = 0;

        // Add AI Control Toggle at the top
        list.addElement(new AIControlRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5));
        currentY += rowHeight;

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

    private class AIControlRowComponent extends UIElement {
        public AIControlRowComponent(int x, int y, int width, int height) {
            super(x, y, width, height);
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

            g.drawString(Minecraft.getInstance().font, "§lAI AUTHORITY BRIDGE", getX() + 10, getY() + 10, 0xFFFFFFFF);
            
            ControlMode mode = AIHandler.getController().getMode();
            boolean autoPromote = AIHandler.getController().isAutoPromoteEnabled();
            
            int toggleBtnWidth = 70;
            int toggleBtnX = getX() + getWidth() - toggleBtnWidth - 10;
            int btnY = getY() + 10;
            int btnHeight = 20;

            boolean toggleHovered = mx >= toggleBtnX && my >= btnY && mx < toggleBtnX + toggleBtnWidth && my < btnY + btnHeight;
            int toggleColor = mode == ControlMode.AI ? (toggleHovered ? 0xFF00CC00 : 0xFF00AA00) : (toggleHovered ? 0xFFCC0000 : 0xFFAA0000);
            g.fill(toggleBtnX, btnY, toggleBtnX + toggleBtnWidth, btnY + btnHeight, toggleColor);
            
            String toggleText = mode == ControlMode.AI ? "AI: ON" : "AI: OFF";
            g.drawCenteredString(Minecraft.getInstance().font, toggleText, toggleBtnX + toggleBtnWidth / 2, btnY + (btnHeight - 8) / 2, 0xFFFFFFFF);

            // Auto-Promote Toggle
            int promoteBtnWidth = 90;
            int promoteBtnX = toggleBtnX - promoteBtnWidth - 5;
            boolean promoteHovered = mx >= promoteBtnX && my >= btnY && mx < promoteBtnX + promoteBtnWidth && my < btnY + btnHeight;
            int promoteColor = autoPromote ? (promoteHovered ? 0xFF00CCFF : 0xFF00AAFF) : (promoteHovered ? 0xFF555555 : 0xFF333333);
            g.fill(promoteBtnX, btnY, promoteBtnX + promoteBtnWidth, btnY + btnHeight, promoteColor);
            String promoteText = autoPromote ? "PROMOTE: ON" : "PROMOTE: OFF";
            g.drawCenteredString(Minecraft.getInstance().font, promoteText, promoteBtnX + promoteBtnWidth / 2, btnY + (btnHeight - 8) / 2, 0xFFFFFFFF);
            
            int reloadBtnWidth = 50;
            int reloadBtnX = promoteBtnX - reloadBtnWidth - 5;
            boolean reloadHovered = mx >= reloadBtnX && my >= btnY && mx < reloadBtnX + reloadBtnWidth && my < btnY + btnHeight;
            g.fill(reloadBtnX, btnY, reloadBtnX + reloadBtnWidth, btnY + btnHeight, reloadHovered ? 0xFF555555 : 0xFF333333);
            g.drawCenteredString(Minecraft.getInstance().font, "RELOAD", reloadBtnX + reloadBtnWidth / 2, btnY + (btnHeight - 8) / 2, 0xFFFFFFFF);

            int clearBtnWidth = 40;
            int clearBtnX = reloadBtnX - clearBtnWidth - 5;
            boolean clearHovered = mx >= clearBtnX && my >= btnY && mx < clearBtnX + clearBtnWidth && my < btnY + btnHeight;
            g.fill(clearBtnX, btnY, clearBtnX + clearBtnWidth, btnY + btnHeight, clearHovered ? 0xFFCC5555 : 0xFF993333);
            g.drawCenteredString(Minecraft.getInstance().font, "CLEAR", clearBtnX + clearBtnWidth / 2, btnY + (btnHeight - 8) / 2, 0xFFFFFFFF);

            g.drawString(Minecraft.getInstance().font, "Exclusive AI Authority", getX() + 10, getY() + 22, 0xFFCCCCCC);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int toggleBtnWidth = 70;
            int toggleBtnX = getX() + getWidth() - toggleBtnWidth - 10;
            int btnY = getY() + 10;
            int btnHeight = 20;

            if (mx >= toggleBtnX && mx < toggleBtnX + toggleBtnWidth && my >= btnY && my < btnY + btnHeight) {
                AIHandler.getController().attemptModeToggle();
                return true;
            }

            int promoteBtnWidth = 90;
            int promoteBtnX = toggleBtnX - promoteBtnWidth - 5;
            if (mx >= promoteBtnX && mx < promoteBtnX + promoteBtnWidth && my >= btnY && my < btnY + btnHeight) {
                AIHandler.getController().toggleAutoPromote();
                return true;
            }

            int reloadBtnWidth = 50;
            int reloadBtnX = promoteBtnX - reloadBtnWidth - 5;
            if (mx >= reloadBtnX && mx < reloadBtnX + reloadBtnWidth && my >= btnY && my < btnY + btnHeight) {
                AIHandler.getController().reloadKnowledge();
                return true;
            }

            int clearBtnWidth = 40;
            int clearBtnX = reloadBtnX - clearBtnWidth - 5;
            if (mx >= clearBtnX && mx < clearBtnX + clearBtnWidth && my >= btnY && my < btnY + btnHeight) {
                AIHandler.getController().resetPassiveStore();
                return true;
            }
            return false;
        }
    }
}
