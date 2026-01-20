package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import com.example.modmenu.network.ActionChamberPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class AdvancedFilterScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final int chamberIndex;
    private ScrollableUIContainer ruleList;
    private int lastVersion = -1;

    public AdvancedFilterScreen(Screen parent, int chamberIndex) {
        super(Component.literal("Advanced Item Filtering"));
        this.parent = parent;
        this.chamberIndex = chamberIndex;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(70, 10, 100, 20, Component.literal("Add ID"), btn -> {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (!held.isEmpty()) {
                String id = ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
                PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 14, id, (0 << 16) | 0)); 
            }
        }));

        this.layoutRoot.addElement(new ResponsiveButton(175, 10, 100, 20, Component.literal("Add Tags"), btn -> {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (!held.isEmpty()) {
                held.getTags().forEach(tag -> {
                    String tagId = tag.location().toString();
                    PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 14, tagId, (1 << 16) | 0)); 
                });
            }
        }));

        this.layoutRoot.addElement(new ResponsiveButton(280, 10, 100, 20, Component.literal("Add NBT"), btn -> {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (!held.isEmpty() && held.hasTag()) {
                String id = ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
                PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 14, id, (2 << 16) | 0, held.getTag())); 
            }
        }));

        ruleList = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(ruleList);
        refreshRules();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (chamberIndex >= 0 && chamberIndex < StorePriceManager.clientSkills.chambers.size()) {
            int currentVer = StorePriceManager.clientSkills.chambers.get(chamberIndex).updateVersion;
            if (currentVer != lastVersion) {
                refreshRules();
                lastVersion = currentVer;
            }
        }
        super.render(g, mx, my, pt);
    }

    private void refreshRules() {
        if (ruleList == null) return;
        ruleList.clearChildren();

        if (chamberIndex < 0 || chamberIndex >= StorePriceManager.clientSkills.chambers.size()) return;
        StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(chamberIndex);

        VerticalLayoutContainer list = new VerticalLayoutContainer(0, 0, ruleList.getWidth() - 10, 0, 5);
        for (StorePriceManager.FilterRule rule : chamber.advancedFilters) {
            list.addElement(new RuleRowComponent(0, 0, list.getWidth(), 25, rule));
        }
        ruleList.addElement(list);
        ruleList.setContentHeight(list.getHeight() + 20);
    }

    private class RuleRowComponent extends UIElement {
        private final StorePriceManager.FilterRule rule;

        public RuleRowComponent(int x, int y, int width, int height, StorePriceManager.FilterRule rule) {
            super(x, y, width, height);
            this.rule = rule;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x33FFFFFF);
            String text = rule.matchType + ": " + rule.matchValue;
            g.drawString(font, text, getX() + 5, getY() + 8, 0xFFFFFFFF);

            String[] actions = {"KEEP", "VOID", "LIQUIDATE"};
            int[] colors = {0xFFAAAAFF, 0xFFFF5555, 0xFF55FF55};
            
            int bx = getX() + getWidth() - 105;
            renderBtn(g, bx, getY() + 3, 80, 18, actions[rule.action], colors[rule.action], mx, my);
            
            g.fill(getX() + getWidth() - 20, getY() + 3, getX() + getWidth() - 2, getY() + 21, 0xFFFF0000);
            g.drawCenteredString(font, "X", getX() + getWidth() - 11, getY() + 8, 0xFFFFFFFF);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int color, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF666666 : 0xFF444444);
            g.drawCenteredString(font, txt, bx + bw / 2, by + bh / 2 - 4, color);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int bx = getX() + getWidth() - 105;
            if (mx >= bx && mx < bx + 80 && my >= getY() + 3 && my < getY() + 21) {
                rule.action = (rule.action + 1) % 3;
                int typeIdx = rule.matchType.equals("ID") ? 0 : (rule.matchType.equals("TAG") ? 1 : 2);
                PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 14, rule.matchValue, (typeIdx << 16) | rule.action));
                return true;
            }
            if (mx >= getX() + getWidth() - 20 && mx < getX() + getWidth() - 2 && my >= getY() + 3 && my < getY() + 21) {
                int typeIdx = rule.matchType.equals("ID") ? 0 : (rule.matchType.equals("TAG") ? 1 : 2);
                PacketHandler.sendToServer(new ActionChamberPacket(chamberIndex, 14, rule.matchValue, (typeIdx << 16) | 0xFFFF)); // -1
                StorePriceManager.clientSkills.chambers.get(chamberIndex).advancedFilters.remove(rule);
                return true;
            }
            return false;
        }
    }
}
