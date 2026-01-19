package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.PurgeSatietyPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

public class SatietyRegistryScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ScrollableUIContainer list;

    public SatietyRegistryScreen(Screen parent) {
        super(Component.literal("Satiety Registry"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 60);
        this.layoutRoot.addElement(list);
        
        refreshList();
    }

    private void refreshList() {
        if (list == null) return;
        list.clearChildren();
        
        int rowHeight = 35;
        int currentY = 0;

        Map<String, Float> satiety = StorePriceManager.clientSkills.mobSatiety;
        List<String> mobIds = new ArrayList<>(satiety.keySet());
        mobIds.sort(String::compareTo);

        for (String id : mobIds) {
            float val = satiety.get(id);
            if (val <= 0) continue;
            list.addElement(new SatietyRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5, id, val));
            currentY += rowHeight;
        }
        list.setContentHeight(currentY);
    }

    private class SatietyRowComponent extends UIElement {
        private final String mobId;
        private final float satiety;

        public SatietyRowComponent(int x, int y, int width, int height, String mobId, float satiety) {
            super(x, y, width, height);
            this.mobId = mobId;
            this.satiety = satiety;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x66000000);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF888888);

            g.drawString(Minecraft.getInstance().font, "§e" + mobId, getX() + 5, getY() + 5, 0xFFFFFFFF);
            g.drawString(Minecraft.getInstance().font, "Satiety: §c" + String.format("%.1f", satiety), getX() + 5, getY() + 15, 0xFFFFFFFF);
            
            // Efficiency: 1 / (1 + satiety)
            int eff = (int) (100.0 / (1.0 + satiety));
            g.drawString(Minecraft.getInstance().font, "Efficiency: §a" + eff + "%", getX() + 150, getY() + 15, 0xFFFFFFFF);

            renderBtn(g, getX() + getWidth() - 80, getY() + 5, 75, 20, "§4PURGE", mx, my);
        }

        private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
            g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF660000 : 0xFF440000);
            g.drawCenteredString(Minecraft.getInstance().font, txt, bx + bw / 2, by + 6, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int bx = getX() + getWidth() - 80;
            if (mx >= bx && mx < bx + 75 && my >= getY() + 5 && my < getY() + 25) {
                PacketHandler.sendToServer(new PurgeSatietyPacket(mobId));
                return true;
            }
            return false;
        }
    }
}
