package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.CaptureMobPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class CaptureMobScreen extends BaseResponsiveLodestoneScreen {
    private final int entityId;
    private final Entity entity;
    private boolean isExact = false;

    public CaptureMobScreen(int entityId) {
        super(Component.literal("Capture Mob"));
        this.entityId = entityId;
        this.entity = Minecraft.getInstance().level.getEntity(entityId);
    }

    public static void open(int entityId) {
        Minecraft.getInstance().setScreen(new CaptureMobScreen(entityId));
    }

    @Override
    protected void setupLayout() {
        int bw = 120;
        int bh = 20;
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.layoutRoot.addElement(new ResponsiveButton(cx - bw - 5, cy + 40, bw, bh, Component.literal("Mode: Generic"), btn -> {
            isExact = !isExact;
            btn.setText(Component.literal("Mode: " + (isExact ? "§eExact" : "§7Generic")));
        }));

        this.layoutRoot.addElement(new ResponsiveButton(cx + 5, cy + 40, bw, bh, Component.literal("§aCapture"), btn -> {
            PacketHandler.sendToServer(new CaptureMobPacket(entityId, isExact));
            this.minecraft.setScreen(null);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(cx - 40, cy + 70, 80, bh, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(null);
        }));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (entity instanceof LivingEntity living) {
            String name = living.getName().getString();
            java.math.BigDecimal cost = java.math.BigDecimal.valueOf(living.getMaxHealth()).multiply(java.math.BigDecimal.valueOf(isExact ? 100 : 10));
            g.drawCenteredString(font, "Capture " + name + "?", this.width / 2, this.height / 2 - 20, 0xFFFFFFFF);
            g.drawCenteredString(font, "Cost: §d" + cost + " SP", this.width / 2, this.height / 2, 0xFFFFFFFF);
            if (isExact) {
                g.drawCenteredString(font, "§eExact mode stores all NBT (gear, HP, effects)", this.width / 2, this.height / 2 + 15, 0xFFFFFF55);
            }
        }
    }
}
