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
    private final String lootTableId;
    private boolean isExact = false;

    public CaptureMobScreen(int entityId) {
        super(Component.literal("Capture Mob"));
        this.entityId = entityId;
        this.entity = Minecraft.getInstance().level.getEntity(entityId);
        this.lootTableId = null;
    }

    public CaptureMobScreen(String lootTableId) {
        super(Component.literal("Virtual Excavation"));
        this.entityId = -1;
        this.entity = null;
        this.lootTableId = lootTableId;
    }

    public static void open(int entityId) {
        Minecraft.getInstance().setScreen(new CaptureMobScreen(entityId));
    }

    public static void open(String lootTableId) {
        Minecraft.getInstance().setScreen(new CaptureMobScreen(lootTableId));
    }

    @Override
    protected void setupLayout() {
        int bw = 120;
        int bh = 20;
        int cx = this.width / 2;
        int cy = this.height / 2;

        if (lootTableId == null) {
            this.layoutRoot.addElement(new ResponsiveButton(cx - bw - 5, cy + 40, bw, bh, Component.literal("Mode: Generic"), btn -> {
                isExact = !isExact;
                btn.setText(Component.literal("Mode: " + (isExact ? "\u00A7eExact" : "\u00A77Generic")));
            }));
        }

        this.layoutRoot.addElement(new ResponsiveButton(cx + 5, cy + 40, bw, bh, Component.literal("\u00A7aCapture"), btn -> {
            if (lootTableId != null) {
                PacketHandler.sendToServer(new CaptureMobPacket(lootTableId));
            } else {
                PacketHandler.sendToServer(new CaptureMobPacket(entityId, isExact));
            }
            this.minecraft.setScreen(null);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(cx - 40, cy + 70, 80, bh, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(null);
        }));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (lootTableId != null) {
            g.drawCenteredString(font, "Initialize Excavation Protocol?", this.width / 2, this.height / 2 - 20, 0xFFFFFFFF);
            g.drawCenteredString(font, "Loot Table: \u00A7b" + lootTableId, this.width / 2, this.height / 2, 0xFFFFFFFF);
            g.drawCenteredString(font, "Cost: \u00A7d10,000 SP", this.width / 2, this.height / 2 + 15, 0xFFFFFFFF);
        } else if (entity instanceof LivingEntity living) {
            String name = living.getName().getString();
            java.math.BigDecimal cost = java.math.BigDecimal.valueOf(living.getMaxHealth()).multiply(java.math.BigDecimal.valueOf(isExact ? 100 : 10));
            g.drawCenteredString(font, "Capture " + name + "?", this.width / 2, this.height / 2 - 20, 0xFFFFFFFF);
            g.drawCenteredString(font, "Cost: \u00A7d" + cost + " SP", this.width / 2, this.height / 2, 0xFFFFFFFF);
            if (isExact) {
                g.drawCenteredString(font, "\u00A7eExact mode stores all NBT (gear, HP, effects)", this.width / 2, this.height / 2 + 15, 0xFFFFFF55);
            }
        }
    }
}
