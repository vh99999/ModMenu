package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.*;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.LogisticsRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.stream.Collectors;

public class RulesListScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private NetworkData networkData;
    private ScrollableUIContainer list;
    private boolean showingTemplates = false;

    public RulesListScreen(Screen parent, UUID networkId, NetworkData networkData) {
        super(Component.literal("Rules List"));
        this.parent = parent;
        this.networkId = networkId;
        this.networkData = networkData;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(70, 10, 100, 20, Component.literal(showingTemplates ? "Show Rules" : "Show Blueprints"), btn -> {
            showingTemplates = !showingTemplates;
            btn.setText(Component.literal(showingTemplates ? "Show Rules" : "Show Blueprints"));
            refreshList();
        }));

        list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(list);

        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();

        int currentY = 0;
        int rowHeight = 40;

        if (showingTemplates) {
            for (com.example.modmenu.store.logistics.RuleTemplate template : networkData.ruleTemplates) {
                list.addElement(new TemplateRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5, template));
                currentY += rowHeight;
            }
        } else {
            for (LogisticsRule rule : networkData.rules) {
                list.addElement(new RuleRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5, rule));
                currentY += rowHeight;
            }
        }
        list.setContentHeight(currentY);
    }

    private class TemplateRowComponent extends UIElement {
        private final com.example.modmenu.store.logistics.RuleTemplate template;

        public TemplateRowComponent(int x, int y, int width, int height, com.example.modmenu.store.logistics.RuleTemplate template) {
            super(x, y, width, height);
            this.template = template;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0x66AAFFAA : 0x33AAFFAA);

            String text = template.name + " (" + template.rule.type + ")";
            g.drawString(font, text, getX() + 10, getY() + 8, 0xFFFFFFFF);
            
            String filterDesc = template.rule.type.equals("ITEMS") ? "Filter: " + template.rule.filter.matchType : "";
            g.drawString(font, filterDesc, getX() + 10, getY() + 20, 0xFFAAAAAA);

            int bx = getX() + getWidth() - 150;
            renderBtn(g, bx, getY() + 5, 70, 25, "Apply", mx, my);
            bx += 75;
            renderBtn(g, bx, getY() + 5, 70, 25, "\u00A7cDelete", mx, my);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF666666 : 0xFF444444);
            g.drawCenteredString(font, txt, bx + bw / 2, by + bh / 2 - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isMouseOver(mx, my)) return false;
            int bx = getX() + getWidth() - 150;
            if (mx >= bx && mx < bx + 70) {
                // Apply Template: needs a target. Let's open target picker.
                minecraft.setScreen(new PickTargetScreen(RulesListScreen.this, networkData, (targetId, isGroup) -> {
                    PacketHandler.sendToServer(RuleManagementPacket.applyTemplate(networkId, targetId, isGroup, template.templateId));
                }));
                return true;
            }
            bx += 75;
            if (mx >= bx && mx < bx + 70) {
                PacketHandler.sendToServer(RuleManagementPacket.removeTemplate(networkId, template.templateId));
                networkData.ruleTemplates.remove(template);
                refreshList();
                return true;
            }
            return false;
        }
    }

    private class RuleRowComponent extends UIElement {
        private final LogisticsRule rule;

        public RuleRowComponent(int x, int y, int width, int height, LogisticsRule rule) {
            super(x, y, width, height);
            this.rule = rule;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0x66FFFFFF : 0x33FFFFFF);

            NetworkNode src = findNode(rule.sourceNodeId);
            NetworkNode dst = findNode(rule.destNodeId);
            String srcName = src != null ? (src.customName != null ? src.customName : src.nodeType) : "Unknown";
            String dstName = dst != null ? (dst.customName != null ? dst.customName : dst.nodeType) : "Unknown";

            String srcSlotStr = rule.sourceSlots.contains(-1) ? "" : "[" + rule.sourceSlots.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
            String dstSlotStr = rule.destSlots.contains(-1) ? "" : "[" + rule.destSlots.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
            if (srcSlotStr.length() > 15) srcSlotStr = srcSlotStr.substring(0, 12) + "...]";
            if (dstSlotStr.length() > 15) dstSlotStr = dstSlotStr.substring(0, 12) + "...]";
            String text = srcName + srcSlotStr + " -> " + dstName + dstSlotStr + " (" + rule.filter.matchType + ")";
            g.drawString(font, text, getX() + 10, getY() + 8, 0xFFFFFFFF);
            
            // Diagnostic Status
            String status = rule.active ? (rule.lastReport.isEmpty() ? "\u00A77Idle" : "\u00A7e" + rule.lastReport) : "\u00A7cPaused";
            if (status.length() > 40) status = status.substring(0, 37) + "...";
            g.drawString(font, status, getX() + 10, getY() + 20, 0xFFAAAAAA);

            int bx = getX() + getWidth() - 225;
            renderBtn(g, bx, getY() + 5, 70, 25, "Test", mx, my);
            bx += 75;
            renderBtn(g, bx, getY() + 5, 70, 25, "Edit", mx, my);
            bx += 75;
            renderBtn(g, bx, getY() + 5, 70, 25, "\u00A7cDelete", mx, my);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF666666 : 0xFF444444);
            g.drawCenteredString(font, txt, bx + bw / 2, by + bh / 2 - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isMouseOver(mx, my)) return false;
            int bx = getX() + getWidth() - 225;
            if (mx >= bx && mx < bx + 70) {
                // Test
                PacketHandler.sendToServer(RuleManagementPacket.test(networkId, rule.ruleId));
                return true;
            }
            bx += 75;
            if (mx >= bx && mx < bx + 70) {
                PacketHandler.sendToServer(RuleManagementPacket.test(networkId, rule.ruleId));
                return true;
            }
            bx += 75;
            if (mx >= bx && mx < bx + 70) {
                PacketHandler.sendToServer(RuleManagementPacket.remove(networkId, rule.ruleId));
                networkData.rules.remove(rule);
                refreshList();
                return true;
            }
            return false;
        }
    }

    private NetworkNode findNode(UUID id) {
        for (NetworkNode n : networkData.nodes) {
            if (n != null && n.nodeId.equals(id)) return n;
        }
        return null;
    }
}
