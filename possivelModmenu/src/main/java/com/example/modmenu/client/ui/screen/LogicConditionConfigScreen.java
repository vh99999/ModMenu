package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.store.logistics.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class LogicConditionConfigScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final NetworkData networkData;
    private final LogisticsRule rule;
    private ScrollableUIContainer list;

    public LogicConditionConfigScreen(Screen parent, NetworkData networkData, LogisticsRule rule) {
        super(Component.literal("Rule Conditions"));
        this.parent = parent;
        this.networkData = networkData;
        this.rule = rule;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(70, 10, 100, 20, Component.literal("Add Condition"), btn -> {
            LogicCondition cond = new LogicCondition();
            rule.conditions.add(cond);
            init(minecraft, width, height);
        }));

        list = new ScrollableUIContainer(10, 40, this.width - 20, this.height - 50);
        this.layoutRoot.addElement(list);

        int cy = 0;
        for (int i = 0; i < rule.conditions.size(); i++) {
            LogicCondition cond = rule.conditions.get(i);
            list.addElement(new ConditionRow(0, cy, list.getWidth() - 20, 45, cond, i));
            cy += 47;
        }
        list.setContentHeight(cy + 20);
    }

    private class ConditionRow extends UIElement {
        private final LogicCondition cond;
        private final int index;
        private final EditBox valInput;

        public ConditionRow(int x, int y, int w, int h, LogicCondition cond, int index) {
            super(x, y, w, h);
            this.cond = cond;
            this.index = index;
            this.valInput = new EditBox(font, 0, 0, 50, 16, Component.literal("Value"));
            valInput.setValue(String.valueOf(cond.value));
            valInput.setResponder(s -> {
                try { cond.value = Integer.parseInt(s); } catch (Exception e) {}
            });
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x33FFFFFF);
            
            String targetLabel = "Select Target";
            if (cond.targetId != null) {
                if (cond.isGroup) {
                    NodeGroup group = networkData.groups.stream().filter(gr -> gr.groupId.equals(cond.targetId)).findFirst().orElse(null);
                    targetLabel = "[G] " + (group != null ? group.name : "Unknown");
                } else {
                    NetworkNode node = networkData.nodes.stream()
                            .filter(java.util.Objects::nonNull)
                            .filter(n -> n.nodeId.equals(cond.targetId))
                            .findFirst().orElse(null);
                    targetLabel = node != null ? (node.customName != null ? node.customName : node.nodeType) : "Unknown";
                }
            }

            int bx = getX() + 5;
            renderBtn(g, bx, getY() + 5, 120, 16, targetLabel, mx, my);
            bx += 125;
            
            renderBtn(g, bx, getY() + 5, 60, 16, cond.type, mx, my);
            bx += 65;
            
            renderBtn(g, bx, getY() + 5, 60, 16, cond.operator, mx, my);
            bx += 65;
            
            g.drawString(font, "Val:", bx, getY() + 9, 0xFFFFFFFF);
            valInput.setX(bx + 25);
            valInput.setY(getY() + 5);
            valInput.render(g, mx, my, pt);
            
            g.fill(getX() + getWidth() - 20, getY() + 5, getX() + getWidth() - 5, getY() + 21, 0xFFFF0000);
            g.drawCenteredString(font, "X", getX() + getWidth() - 12, getY() + 8, 0xFFFFFFFF);
            
            if (cond.type.equals("ITEMS")) {
                String filterTxt = "Filter: " + (cond.filter.matchType.equals("ALL") ? "ALL" : (cond.filter.matchValues.size() + " items"));
                renderBtn(g, getX() + 5, getY() + 25, 120, 16, filterTxt, mx, my);
            }
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF666666 : 0xFF444444);
            g.drawCenteredString(font, txt, bx + bw / 2, by + bh / 2 - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (valInput.mouseClicked(mx, my, button)) return true;

            int bx = getX() + 5;
            if (mx >= bx && mx < bx + 120 && my >= getY() + 5 && my < getY() + 21) {
                minecraft.setScreen(new PickTargetScreen(LogicConditionConfigScreen.this, networkData, (id, isGroup) -> {
                    cond.targetId = id;
                    cond.isGroup = isGroup;
                }));
                return true;
            }
            bx += 125;
            if (mx >= bx && mx < bx + 60 && my >= getY() + 5 && my < getY() + 21) {
                String[] types = {"ITEMS", "ENERGY", "FLUIDS"};
                for (int i = 0; i < types.length; i++) if (types[i].equals(cond.type)) {
                    cond.type = types[(i + (button == 1 ? -1 : 1) + types.length) % types.length];
                    break;
                }
                return true;
            }
            bx += 65;
            if (mx >= bx && mx < bx + 60 && my >= getY() + 5 && my < getY() + 21) {
                String[] ops = {"LESS", "GREATER", "EQUAL"};
                for (int i = 0; i < ops.length; i++) if (ops[i].equals(cond.operator)) {
                    cond.operator = ops[(i + (button == 1 ? -1 : 1) + ops.length) % ops.length];
                    break;
                }
                return true;
            }
            
            if (mx >= getX() + getWidth() - 20 && mx < getX() + getWidth() - 5 && my >= getY() + 5 && my < getY() + 21) {
                rule.conditions.remove(index);
                init(minecraft, width, height);
                return true;
            }

            if (cond.type.equals("ITEMS")) {
                if (mx >= getX() + 5 && mx < getX() + 125 && my >= getY() + 25 && my < getY() + 41) {
                    minecraft.setScreen(new FilterConfigScreen(LogicConditionConfigScreen.this, cond.filter, rule.ruleId, networkData, cond.targetId, cond.isGroup));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean charTyped(char chr, int code) {
            return valInput.charTyped(chr, code);
        }

        @Override
        public boolean keyPressed(int key, int scan, int mod) {
            return valInput.keyPressed(key, scan, mod);
        }
    }
}
