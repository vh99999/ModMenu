package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.AtmosphericControlPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AtmosphericControlScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;

    public AtmosphericControlScreen(Screen parent) {
        super(Component.literal("Atmospheric Control"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        int center = this.width / 2;
        int y = 60;
        int btnWidth = 150;

        // Weather
        this.layoutRoot.addElement(new ResponsiveButton(center - btnWidth - 5, y, btnWidth, 20, Component.literal("Clear Weather"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(0));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(center + 5, y, btnWidth, 20, Component.literal("Rain"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(1));
        }));
        y += 25;
        this.layoutRoot.addElement(new ResponsiveButton(center - (btnWidth / 2), y, btnWidth, 20, Component.literal("Thunderstorm"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(2));
        }));

        y += 40;

        // Time
        this.layoutRoot.addElement(new ResponsiveButton(center - btnWidth - 5, y, btnWidth, 20, Component.literal("Set Day"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(3));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(center + 5, y, btnWidth, 20, Component.literal("Set Night"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(4));
        }));

        y += 40;

        // World Rules (Placeholders for now, I'll update the packet later)
        this.layoutRoot.addElement(new ResponsiveButton(center - btnWidth - 5, y, btnWidth, 20, Component.literal("Toggle Fire Spread"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(5));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(center + 5, y, btnWidth, 20, Component.literal("Toggle Mob Spawning"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(6));
        }));
        y += 25;
        this.layoutRoot.addElement(new ResponsiveButton(center - btnWidth - 5, y, btnWidth, 20, Component.literal("Fast Tick Speed"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(7));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(center + 5, y, btnWidth, 20, Component.literal("Normal Tick Speed"), btn -> {
            PacketHandler.sendToServer(new AtmosphericControlPacket(8));
        }));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int center = this.width / 2;
        g.drawCenteredString(font, "Atmospheric & World Control", center, 40, 0xFF66AAFF);
    }
}
