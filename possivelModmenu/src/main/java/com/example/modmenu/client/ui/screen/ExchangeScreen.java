package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.ExchangePacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

public class ExchangeScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;

    public ExchangeScreen(Screen parent) {
        super(Component.literal("Meta-Trading (SP Exchange)"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        int center = this.width / 2;
        int y = 60;

        // Money to SP
        this.layoutRoot.addElement(new ResponsiveButton(center - 160, y, 320, 20, Component.literal("Exchange $1,000,000,000 for 1 SP"), btn -> {
            PacketHandler.sendToServer(new ExchangePacket(true, BigDecimal.valueOf(1)));
        }));
        
        y += 25;
        this.layoutRoot.addElement(new ResponsiveButton(center - 160, y, 320, 20, Component.literal("Exchange $1,000,000,000,000 for 1,000 SP"), btn -> {
            PacketHandler.sendToServer(new ExchangePacket(true, BigDecimal.valueOf(1000)));
        }));

        y += 50;

        // SP to Money
        this.layoutRoot.addElement(new ResponsiveButton(center - 160, y, 320, 20, Component.literal("Exchange 1 SP for $100,000,000"), btn -> {
            PacketHandler.sendToServer(new ExchangePacket(false, BigDecimal.valueOf(1)));
        }));
        
        y += 25;
        this.layoutRoot.addElement(new ResponsiveButton(center - 160, y, 320, 20, Component.literal("Exchange 1,000 SP for $100,000,000,000"), btn -> {
            PacketHandler.sendToServer(new ExchangePacket(false, BigDecimal.valueOf(1000)));
        }));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        
        int center = this.width / 2;
        g.drawCenteredString(font, "Current Balance: \u00A7e$" + StorePriceManager.formatCurrency(StorePriceManager.playerMoney), center, 40, 0xFFFFFFFF);
        BigDecimal availableSP = StorePriceManager.clientSkills.totalSP.subtract(StorePriceManager.clientSkills.spentSP);
        g.drawCenteredString(font, "Available SP: \u00A7d" + availableSP, center, 50, 0xFFFFFFFF);
        
        g.drawCenteredString(font, "\u00A77Note: Meta-Trading allows bypassing mob farming for SP.", center, this.height - 30, 0xFFAAAAAA);
    }
}
