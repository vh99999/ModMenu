package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.SkillUpgradePacket;
import com.example.modmenu.network.ToggleSkillPacket;
import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillDefinitions;
import com.example.modmenu.store.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.math.BigDecimal;
import java.util.*;

public class SkillTreeScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    
    private double cameraX = 0;
    private double cameraY = 0;
    private double zoom = 1.0;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 2.0;
    
    private boolean isDragging = false;
    
    private SkillDefinitions.SkillPath hoveredSkill = null;

    public SkillTreeScreen(Screen parent) {
        super(Component.literal("Transcendence Skill Tree"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));
        
        this.layoutRoot.addElement(new ResponsiveButton(70, 10, 120, 20, Component.literal("Diagnostics"), btn -> {
            this.minecraft.setScreen(new DiagnosticsScreen(this));
        }));

        this.layoutRoot.addElement(new ResponsiveButton(this.width - 130, 10, 120, 20, Component.literal("Satiety Registry"), btn -> {
            this.minecraft.setScreen(new SatietyRegistryScreen(this));
        }));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        
        // Render Deep Space Background
        g.fill(0, 0, this.width, this.height, 0xFF000005);
        renderStars(g);
        
        // Pan & Zoom Transform
        g.pose().pushPose();
        g.pose().translate(this.width / 2f, this.height / 2f, 0);
        g.pose().scale((float)zoom, (float)zoom, 1.0f);
        g.pose().translate((float)cameraX, (float)cameraY, 0);
        
        drawConduits(g);
        drawNodes(g, mx, my);
        
        g.pose().popPose();
        
        renderOverlay(g);
        
        super.render(g, mx, my, pt);
        
        if (hoveredSkill != null) {
            renderTooltip(g, hoveredSkill, mx, my);
        }
    }

    private void drawConduits(GuiGraphics g) {
        for (SkillDefinitions.SkillPath skill : SkillDefinitions.ALL_SKILLS.values()) {
            if (skill.prerequisiteId != null) {
                SkillDefinitions.SkillPath prereq = SkillDefinitions.ALL_SKILLS.get(skill.prerequisiteId);
                if (prereq != null) {
                    boolean met = StorePriceManager.clientSkills.unlockedRanks.getOrDefault(prereq.id, 0) >= skill.prerequisiteRank;
                    int color = met ? 0xFF00AAFF : 0xFF333333;
                    if (met && StorePriceManager.clientSkills.unlockedRanks.getOrDefault(skill.id, 0) > 0) {
                        color = 0xFF55FF55; // Fully active connection
                    }
                    
                    // Draw Line
                    drawLine(g, prereq.x, prereq.y, skill.x, skill.y, color, met);
                }
            }
        }
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, boolean glow) {
        int steps = (int) Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2)) / 5;
        if (steps < 1) steps = 1;
        for(int i=0; i<=steps; i++) {
            float t = (float)i / steps;
            int px = (int) (x1 + (x2-x1)*t);
            int py = (int) (y1 + (y2-y1)*t);
            g.fill(px-1, py-1, px+1, py+1, color);
            
            if (glow) {
                // Outer glow
                g.fill(px-2, py-2, px+2, py+2, color & 0x44FFFFFF);
                
                // Pulsing light along the conduit
                float pulse = (float)((System.currentTimeMillis() / 200.0 + i * 0.5) % steps);
                if (Math.abs(pulse - i) < 2) {
                    g.fill(px-3, py-3, px+3, py+3, color | 0xFF000000);
                }
            }
        }
    }

    private void drawNodes(GuiGraphics g, int mx, int my) {
        hoveredSkill = null;
        
        // Translate mouse coords to world space
        double worldMX = (mx - this.width / 2f) / zoom - cameraX;
        double worldMY = (my - this.height / 2f) / zoom - cameraY;

        for (SkillDefinitions.SkillPath skill : SkillDefinitions.ALL_SKILLS.values()) {
            boolean isHovered = Math.sqrt(Math.pow(worldMX - skill.x, 2) + Math.pow(worldMY - skill.y, 2)) < 20;
            if (isHovered) hoveredSkill = skill;
            
            renderNode(g, skill, isHovered);
        }
    }

    private void renderNode(GuiGraphics g, SkillDefinitions.SkillPath skill, boolean hovered) {
        int unlockedRank = StorePriceManager.clientSkills.unlockedRanks.getOrDefault(skill.id, 0);
        boolean isKeystone = skill.id.contains("KEYSTONE");
        boolean active = StorePriceManager.clientSkills.activeToggles.contains(skill.id);
        
        int baseColor = 0xFF333333;
        if (unlockedRank > 0) {
            baseColor = active ? 0xFF55FF55 : 0xFF888888;
            if (isKeystone) baseColor = 0xFFAA00FF;
        } else {
            // Check if available
            boolean canBuy = skill.prerequisiteId == null || StorePriceManager.clientSkills.unlockedRanks.getOrDefault(skill.prerequisiteId, 0) >= skill.prerequisiteRank;
            if (canBuy) baseColor = 0xFF5555AA;
        }

        if (hovered) baseColor = (baseColor & 0x00FFFFFF) | 0xFFFFFFFF;

        // Draw node frame
        int size = isKeystone ? 25 : 15;
        if (isKeystone) {
            drawHexagon(g, skill.x, skill.y, size, baseColor);
        } else {
            g.fill(skill.x - size, skill.y - size, skill.x + size, skill.y + size, baseColor);
            g.renderOutline(skill.x - size, skill.y - size, size * 2, size * 2, 0xFFFFFFFF);
        }
        
        String initial = skill.name.substring(0, 1);
        g.drawCenteredString(font, initial, skill.x, skill.y - 4, 0xFFFFFFFF);
    }

    private void drawHexagon(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x - size, y - size/2, x + size, y + size/2, color);
        g.fill(x - size/2, y - size, x + size/2, y + size, color);
        g.renderOutline(x - size, y - size/2, size * 2, size, 0xFFFFFFFF);
        g.renderOutline(x - size/2, y - size, size, size * 2, 0xFFFFFFFF);
    }

    private void renderOverlay(GuiGraphics g) {
        String spText = "\u00A7dTotal SP: " + StorePriceManager.clientSkills.totalSP + " \u00A77| \u00A76Available: " + (StorePriceManager.clientSkills.totalSP.subtract(StorePriceManager.clientSkills.spentSP));
        g.drawString(font, spText, 10, this.height - 20, 0xFFFFFFFF);
        
        String helpText = "\u00A77Scroll to Zoom | Drag to Pan | Left-Click to Buy | Right-Click to Toggle | +/- Key to select Rank";
        g.drawCenteredString(font, helpText, this.width / 2, this.height - 20, 0xFFAAAAAA);
    }

    private void renderTooltip(GuiGraphics g, SkillDefinitions.SkillPath skill, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        int unlocked = StorePriceManager.clientSkills.unlockedRanks.getOrDefault(skill.id, 0);
        int current = StorePriceManager.clientSkills.skillRanks.getOrDefault(skill.id, 0);
        boolean active = StorePriceManager.clientSkills.activeToggles.contains(skill.id);
        
        lines.add(Component.literal("\u00A7e\u00A7l" + skill.name));
        lines.add(Component.literal("\u00A77" + skill.description));
        lines.add(Component.literal(""));
        lines.add(Component.literal("\u00A7bRank: \u00A7f" + current + " / " + skill.maxRank + " \u00A77(Unlocked: " + unlocked + ")"));
        lines.add(Component.literal("\u00A7bStatus: " + (active ? "\u00A7aACTIVE" : "\u00A7cINACTIVE")));
        
        if (unlocked < skill.maxRank) {
            BigDecimal mult = getClientMultiplier(skill.branch.name());
            BigDecimal cost = SkillManager.getSkillCost(skill.id, unlocked + 1, mult, Minecraft.getInstance().player.getUUID());
            lines.add(Component.literal("\u00A7dUpgrade Cost: " + cost + " SP"));
        }
        
        if (skill.prerequisiteId != null) {
            int prereqOwned = StorePriceManager.clientSkills.unlockedRanks.getOrDefault(skill.prerequisiteId, 0);
            String color = prereqOwned >= skill.prerequisiteRank ? "\u00A7a" : "\u00A7c";
            lines.add(Component.literal(color + "Requires: " + SkillDefinitions.ALL_SKILLS.get(skill.prerequisiteId).name + " Rank " + skill.prerequisiteRank));
        }

        g.renderComponentTooltip(font, lines, mx, my);
    }

    private BigDecimal getClientMultiplier(String branch) {
        int index = StorePriceManager.clientSkills.branchOrder.indexOf(branch);
        if (index < 0) return BigDecimal.ONE;
        return BigDecimal.TEN.pow(index);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (hoveredSkill != null) {
            if (button == 0) { 
                int unlocked = StorePriceManager.clientSkills.unlockedRanks.getOrDefault(hoveredSkill.id, 0);
                if (unlocked < hoveredSkill.maxRank) {
                    PacketHandler.sendToServer(new SkillUpgradePacket(hoveredSkill.id));
                }
            } else if (button == 1) { 
                if (StorePriceManager.clientSkills.unlockedRanks.getOrDefault(hoveredSkill.id, 0) > 0) {
                    PacketHandler.sendToServer(new ToggleSkillPacket(hoveredSkill.id));
                }
            }
            return true;
        }
        
        if (button == 0) {
            isDragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) isDragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDragging) {
            cameraX += dx / zoom;
            cameraY += dy / zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0) zoom *= 1.1;
        else zoom /= 1.1;
        zoom = Mth.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    private void renderStars(GuiGraphics g) {
        Random rand = new Random(12345);
        for (int i = 0; i < 200; i++) {
            int x = rand.nextInt(this.width);
            int y = rand.nextInt(this.height);
            float brightness = 0.5f + 0.5f * (float)Math.sin((System.currentTimeMillis() / 500.0) + i);
            int alpha = (int)(brightness * 255);
            int color = (alpha << 24) | 0xFFFFFF;
            g.fill(x, y, x + 1, y + 1, color);
        }
        
        // Parallax stars
        rand = new Random(6789);
        for (int i = 0; i < 100; i++) {
            double worldX = rand.nextInt(4000) - 2000;
            double worldY = rand.nextInt(4000) - 2000;
            
            double screenX = (worldX + cameraX * 0.5) * zoom + this.width / 2.0;
            double screenY = (worldY + cameraY * 0.5) * zoom + this.height / 2.0;
            
            if (screenX >= 0 && screenX < this.width && screenY >= 0 && screenY < this.height) {
                g.fill((int)screenX, (int)screenY, (int)screenX + 2, (int)screenY + 2, 0x44FFFFFF);
            }
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hoveredSkill != null && StorePriceManager.clientSkills.unlockedRanks.getOrDefault(hoveredSkill.id, 0) > 0) {
            int current = StorePriceManager.clientSkills.skillRanks.getOrDefault(hoveredSkill.id, 0);
            if (keyCode == 265 || keyCode == 334 || keyCode == 61) { // Up or +
                if (current < StorePriceManager.clientSkills.unlockedRanks.get(hoveredSkill.id)) {
                    PacketHandler.sendToServer(new com.example.modmenu.network.SetSkillRankPacket(hoveredSkill.id, current + 1));
                }
                return true;
            }
            if (keyCode == 264 || keyCode == 333 || keyCode == 45) { // Down or -
                if (current > 0) {
                    PacketHandler.sendToServer(new com.example.modmenu.network.SetSkillRankPacket(hoveredSkill.id, current - 1));
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
