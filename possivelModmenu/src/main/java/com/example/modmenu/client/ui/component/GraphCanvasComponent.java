package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.screen.NodeConfigScreen;
import com.example.modmenu.network.ActionNetworkPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.LogisticsRule;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class GraphCanvasComponent extends UIContainer {
    private NetworkData network;
    private final com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen parentScreen;
    private double cameraX, cameraY;
    private double zoom = 1.0;
    private boolean isPanning = false;
    private UUID draggingNodeId = null;
    private UUID linkingFromNodeId = null;
    private double lastMouseX, lastMouseY;
    private NetworkNode hoveredNode = null;
    private final java.util.List<UIParticle> particles = new java.util.ArrayList<>();

    public GraphCanvasComponent(int x, int y, int width, int height, NetworkData network, com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen parentScreen) {
        super(x, y, width, height);
        this.network = network;
        this.parentScreen = parentScreen;
        this.cameraX = width / 2.0;
        this.cameraY = height / 2.0;
    }

    public void setNetworkData(NetworkData network) {
        this.network = network;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        
        // Background
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF050505);
        drawGrid(g);

        g.pose().pushPose();
        g.pose().translate(getX() + cameraX, getY() + cameraY, 0);
        g.pose().scale((float)zoom, (float)zoom, 1.0f);

        double worldMX = (mx - getX() - cameraX) / zoom;
        double worldMY = (my - getY() - cameraY) / zoom;

        // Draw Rules (Lines)
        drawRuleConnections(g, worldMX, worldMY);

        // Linking Preview
        if (linkingFromNodeId != null) {
            NetworkNode src = findNode(linkingFromNodeId);
            if (src != null) {
                drawLine(g, src.guiX, src.guiY, (int)worldMX, (int)worldMY, 0xFFFFAA00, true, false);
            }
        }

        // Simulation / Active Flow Particles
        updateAndRenderParticles(g, pt);

        // Draw Nodes
        hoveredNode = null;
        for (NetworkNode node : network.nodes) {
            // Expanded padding zone to ensure sub-buttons (P and X) don't flicker
            boolean isHovered = Math.abs(worldMX - node.guiX) < 30 && worldMY > node.guiY - 32 && worldMY < node.guiY + 25;
            if (isHovered) hoveredNode = node;
            renderNode(g, node, isHovered);
        }

        g.pose().popPose();
        g.disableScissor();
        
        if (hoveredNode != null) {
            // Diagnostic Tooltip
            parentScreen.addPostRenderTask(graphics -> {
                java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                lines.add(net.minecraft.network.chat.Component.literal("\u00A7e\u00A7l" + (hoveredNode.customName != null ? hoveredNode.customName : hoveredNode.nodeType)));
                if (hoveredNode.nodeType.equals("BLOCK") && hoveredNode.pos != null) {
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Pos: \u00A7f" + hoveredNode.pos.toShortString()));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Dim: \u00A7f" + hoveredNode.dimension));
                }
                
                if (hoveredNode.isMissing) {
                    lines.add(net.minecraft.network.chat.Component.literal(""));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A7c\u00A7l\u26A0 ERROR: BLOCK OFFLINE"));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Expected: \u00A7f" + hoveredNode.blockId));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Status: Block missing or moved."));
                }
                
                graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mx, my);
            });
        }
    }

    private void updateAndRenderParticles(GuiGraphics g, float pt) {
        if (network == null) return;
        
        // Spawn particles for simulation or active rules
        if (System.currentTimeMillis() % 10 < 50) { // Throttle spawning
            for (LogisticsRule rule : network.rules) {
                if (rule.active && (network.simulationActive || (rule.lastReport != null && rule.lastReport.contains("Moved")))) {
                    NetworkNode src = findNode(rule.sourceNodeId);
                    NetworkNode dst = findNode(rule.destNodeId);
                    if (src != null && dst != null && Math.random() < 0.1) {
                        int color = network.simulationActive ? 0xFF00FFFF : 0xFF55FF55;
                        particles.add(new UIParticle(src.guiX, src.guiY, dst.guiX, dst.guiY, color));
                    }
                }
            }
        }

        java.util.Iterator<UIParticle> it = particles.iterator();
        while (it.hasNext()) {
            UIParticle p = it.next();
            if (p.update(0.05f)) {
                it.remove();
            } else {
                p.render(g);
            }
        }
    }

    private void drawGrid(GuiGraphics g) {
        int gridSize = (int)(40 * zoom);
        if (gridSize < 5) return;
        
        int offX = (int)(cameraX % gridSize);
        int offY = (int)(cameraY % gridSize);
        
        for (int x = offX; x < getWidth(); x += gridSize) {
            g.fill(getX() + x, getY(), getX() + x + 1, getY() + getHeight(), 0x11FFFFFF);
        }
        for (int y = offY; y < getHeight(); y += gridSize) {
            g.fill(getX(), getY() + y, getX() + getWidth(), getY() + y + 1, 0x11FFFFFF);
        }
    }

    private void drawRuleConnections(GuiGraphics g, double worldMX, double worldMY) {
        for (LogisticsRule rule : network.rules) {
            NetworkNode src = findNode(rule.sourceNodeId);
            NetworkNode dst = findNode(rule.destNodeId);
            if (src != null && dst != null) {
                int color = switch(rule.type) {
                    case "ENERGY" -> 0xFFFFAA00;
                    case "FLUIDS" -> 0xFF00FF00;
                    default -> 0xFF00AAFF; // ITEMS
                };
                
                if (!rule.active) color = 0xFF555555;
                if (rule.lastReport != null && rule.lastReport.contains("Moved")) {
                    // Success highlight (Greenish tint mixed with base color)
                    int r = (color >> 16) & 0xFF;
                    int gr = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    color = 0xFF000000 | (Math.min(255, r + 50) << 16) | (Math.min(255, gr + 100) << 8) | Math.min(255, b + 50);
                }
                
                boolean isBroken = src.isMissing || dst.isMissing;
                if (isBroken) color = 0xFFFF0000;
                
                drawLine(g, src.guiX, src.guiY, dst.guiX, dst.guiY, color, rule.active, isBroken);
                
                // Draw Flow Arrow in middle
                drawArrow(g, src.guiX, src.guiY, dst.guiX, dst.guiY, color);

                // Draw Edit handle in middle
                int midX = (src.guiX + dst.guiX) / 2;
                int midY = (src.guiY + dst.guiY) / 2;
                boolean hov = Math.sqrt(Math.pow(worldMX - midX, 2) + Math.pow(worldMY - midY, 2)) < 8;
                
                g.fill(midX - 10, midY - 4, midX - 2, midY + 4, hov ? 0xFFFFFFFF : 0xAAFFFFFF); // Edit
                g.fill(midX + 2, midY - 4, midX + 10, midY + 4, hov ? 0xFF00FF00 : 0xAA00FF00); // Test (Green)
                
                if (hov) {
                    if (worldMX < midX) {
                        g.renderOutline(midX - 11, midY - 5, 10, 10, 0xFF00AAFF);
                    } else {
                        g.renderOutline(midX + 1, midY - 5, 10, 10, 0xFFFFFFFF);
                    }
                }
                
                g.pose().pushPose();
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(Minecraft.getInstance().font, "E", (midX - 8) * 2, (midY - 2) * 2, 0xFF000000);
                g.drawString(Minecraft.getInstance().font, "T", (midX + 4) * 2, (midY - 2) * 2, 0xFF000000);
                g.pose().popPose();
            }
        }
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, boolean animated, boolean dashed) {
        double dist = Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2));
        int steps = (int) (dist / 4);
        if (steps < 1) steps = 1;

        for (int i = 0; i <= steps; i++) {
            if (dashed && (i / 2) % 2 == 0) continue; // Basic dash effect
            
            float t = (float) i / steps;
            int px = (int) (x1 + (x2 - x1) * t);
            int py = (int) (y1 + (y2 - y1) * t);
            
            int alpha = 0x88;
            if (animated) {
                float pulse = (float) ((System.currentTimeMillis() / 100.0 - i * 0.5) % 10);
                if (pulse > 0 && pulse < 3) alpha = 0xFF;
            }
            
            int c = (alpha << 24) | (color & 0xFFFFFF);
            g.fill(px - 1, py - 1, px + 1, py + 1, c);
        }
    }

    private void drawArrow(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;
        
        // Offset arrow slightly from the center handle
        midX -= (int)(Math.cos(angle) * 15);
        midY -= (int)(Math.sin(angle) * 15);

        int size = 4;
        double arrowAngle = Math.PI / 6; // 30 degrees

        for (int i = 0; i < size; i++) {
            int ax1 = midX - (int) (i * Math.cos(angle - arrowAngle));
            int ay1 = midY - (int) (i * Math.sin(angle - arrowAngle));
            int ax2 = midX - (int) (i * Math.cos(angle + arrowAngle));
            int ay2 = midY - (int) (i * Math.sin(angle + arrowAngle));
            
            g.fill(ax1, ay1, ax1 + 1, ay1 + 1, color | 0xFF000000);
            g.fill(ax2, ay2, ax2 + 1, ay2 + 1, color | 0xFF000000);
        }
    }

    private void renderNode(GuiGraphics g, NetworkNode node, boolean hovered) {
        int baseColor = 0xFF1A1A1A;
        if (hovered) baseColor = 0xFF2D2D3A;
        if (node.isMissing) baseColor = 0xFF330000;
        
        g.fill(node.guiX - 15, node.guiY - 15, node.guiX + 15, node.guiY + 15, baseColor);
        
        // Highlight active connections
        int borderColor = hovered ? 0xFF00AAFF : 0xFF444444;
        if (node.isMissing) {
            float pulse = (float) Math.abs(Math.sin(System.currentTimeMillis() / 200.0));
            borderColor = (0xFF << 24) | ((int)(pulse * 255) << 16); // Pulsing red
        }
        g.renderOutline(node.guiX - 15, node.guiY - 15, 30, 30, borderColor);
        
        // Glow effect if hovered
        if (hovered && !node.isMissing) {
            g.fill(node.guiX - 16, node.guiY - 16, node.guiX + 16, node.guiY - 15, 0xFF00AAFF);
            g.fill(node.guiX - 16, node.guiY + 15, node.guiX + 16, node.guiY + 16, 0xFF00AAFF);
            g.fill(node.guiX - 16, node.guiY - 16, node.guiX - 15, node.guiY + 16, 0xFF00AAFF);
            g.fill(node.guiX + 15, node.guiY - 16, node.guiX + 16, node.guiY + 16, 0xFF00AAFF);
        }

        // Render Icon
        if (node.nodeType.equals("BLOCK") && node.blockId != null) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(node.blockId));
            if (item != null) {
                g.pose().pushPose();
                g.pose().translate(node.guiX - 8, node.guiY - 8, 0);
                if (node.isMissing) {
                    // Grayscale/Darken effect
                    RenderSystem.setShaderColor(0.3f, 0.3f, 0.3f, 1.0f);
                }
                g.renderItem(new ItemStack(item), 0, 0);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                g.pose().popPose();
            }
        } else {
            String initial = node.nodeType.substring(0, 1);
            g.drawCenteredString(Minecraft.getInstance().font, initial, node.guiX, node.guiY - 4, node.isMissing ? 0xFFFF0000 : 0xFFFFFFFF);
        }
        
        // Name label background
        String name = node.customName != null ? node.customName : node.nodeType;
        if (node.isMissing) name = "\u00A7cMISSING: " + name;
        int textWidth = Minecraft.getInstance().font.width(name) / 2;
        g.fill(node.guiX - textWidth - 2, node.guiY + 16, node.guiX + textWidth + 2, node.guiY + 26, 0xAA000000);
        
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1.0f);
        g.drawCenteredString(Minecraft.getInstance().font, name, node.guiX * 2, (node.guiY + 18) * 2, 0xFFFFFFFF);
        g.pose().popPose();

        if (node.isMissing) {
            g.pose().pushPose();
            g.pose().translate(node.guiX + 8, node.guiY - 18, 0);
            g.drawString(Minecraft.getInstance().font, "\u26A0", 0, 0, 0xFFFF0000);
            g.pose().popPose();
        }

        if (hovered) {
            // High-contrast tabs for sub-buttons
            // PING (Gold)
            g.fill(node.guiX - 25, node.guiY - 28, node.guiX - 11, node.guiY - 16, 0xFFFFAA00);
            g.renderOutline(node.guiX - 25, node.guiY - 28, 14, 12, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, "P", node.guiX - 18, node.guiY - 26, 0xFFFFFFFF);
            
            // DELETE (Red)
            g.fill(node.guiX + 11, node.guiY - 28, node.guiX + 25, node.guiY - 16, 0xFFFF0000);
            g.renderOutline(node.guiX + 11, node.guiY - 28, 14, 12, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, "X", node.guiX + 18, node.guiY - 26, 0xFFFFFFFF);
        }
    }

    private NetworkNode findNode(UUID id) {
        for (NetworkNode n : network.nodes) if (n.nodeId.equals(id)) return n;
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my)) return false;
        
        double worldMX = (mx - getX() - cameraX) / zoom;
        double worldMY = (my - getY() - cameraY) / zoom;

        for (NetworkNode node : network.nodes) {
            if (Math.abs(worldMX - node.guiX) < 30 && worldMY > node.guiY - 32 && worldMY < node.guiY + 25) {
                // Check sub-buttons hitboxes
                if (worldMX > node.guiX + 11 && worldMX < node.guiX + 25 && worldMY > node.guiY - 28 && worldMY < node.guiY - 16) {
                    // Delete
                    PacketHandler.sendToServer(ActionNetworkPacket.removeNode(network.networkId, node.nodeId));
                    return true;
                }
                if (worldMX > node.guiX - 25 && worldMX < node.guiX - 11 && worldMY > node.guiY - 28 && worldMY < node.guiY - 16) {
                    // Ping
                    if (node.pos != null) {
                        com.example.modmenu.client.ClientForgeEvents.addPing(node.pos);
                        Minecraft.getInstance().setScreen(null);
                    }
                    return true;
                }

                if (button == 0) {
                    if (Screen.hasShiftDown()) {
                        linkingFromNodeId = node.nodeId;
                    } else {
                        draggingNodeId = node.nodeId;
                    }
                    lastMouseX = mx;
                    lastMouseY = my;
                    return true;
                } else if (button == 1) {
                    Minecraft.getInstance().setScreen(new NodeConfigScreen(parentScreen, network.networkId, node));
                    return true;
                }
            }
        }

        // Check rule handles
        for (LogisticsRule rule : network.rules) {
            NetworkNode src = findNode(rule.sourceNodeId);
            NetworkNode dst = findNode(rule.destNodeId);
            if (src != null && dst != null) {
                int midX = (src.guiX + dst.guiX) / 2;
                int midY = (src.guiY + dst.guiY) / 2;
                if (Math.sqrt(Math.pow(worldMX - midX, 2) + Math.pow(worldMY - midY, 2)) < 10) {
                    if (worldMX < midX) {
                        // Edit
                        Minecraft.getInstance().setScreen(new com.example.modmenu.client.ui.screen.RuleConfigScreen(parentScreen, network.networkId, network, rule, false));
                    } else {
                        // Test
                        PacketHandler.sendToServer(ActionNetworkPacket.testRule(network.networkId, rule.ruleId));
                    }
                    return true;
                }
            }
        }

        if (button == 0) {
            isPanning = true;
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            if (draggingNodeId != null) {
                NetworkNode node = findNode(draggingNodeId);
                if (node != null) {
                    PacketHandler.sendToServer(new ActionNetworkPacket(11, network.networkId, node));
                }
            }
            if (linkingFromNodeId != null) {
                double worldMX = (mx - getX() - cameraX) / zoom;
                double worldMY = (my - getY() - cameraY) / zoom;
                for (NetworkNode node : network.nodes) {
                    if (Math.abs(worldMX - node.guiX) < 30 && worldMY > node.guiY - 32 && worldMY < node.guiY + 25) {
                        if (!node.nodeId.equals(linkingFromNodeId)) {
                            // Create Rule
                            LogisticsRule newRule = new LogisticsRule();
                            newRule.sourceNodeId = linkingFromNodeId;
                            newRule.destNodeId = node.nodeId;
                            Minecraft.getInstance().setScreen(new com.example.modmenu.client.ui.screen.RuleConfigScreen(parentScreen, network.networkId, network, newRule, true));
                        }
                        break;
                    }
                }
            }
            isPanning = false;
            draggingNodeId = null;
            linkingFromNodeId = null;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isPanning) {
            cameraX += dx;
            cameraY += dy;
            return true;
        }
        if (draggingNodeId != null) {
            NetworkNode node = findNode(draggingNodeId);
            if (node != null) {
                node.guiX += dx / zoom;
                node.guiY += dy / zoom;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!isMouseOver(mx, my)) return false;
        
        double oldZoom = zoom;
        if (delta > 0) zoom *= 1.1;
        else zoom /= 1.1;
        zoom = Mth.clamp(zoom, 0.2, 3.0);
        
        // Zoom towards mouse
        double worldMX = (mx - getX() - cameraX) / oldZoom;
        double worldMY = (my - getY() - cameraY) / oldZoom;
        
        cameraX = (mx - getX()) - worldMX * zoom;
        cameraY = (my - getY()) - worldMY * zoom;
        
        return true;
    }

    private static class UIParticle {
        float x, y;
        float targetX, y2;
        int color;
        float progress = 0;
        
        public UIParticle(float x, float y, float tx, float ty, int color) {
            this.x = x; this.y = y; this.targetX = tx; this.y2 = ty; this.color = color;
        }
        
        public boolean update(float delta) {
            progress += delta;
            return progress >= 1.0f;
        }
        
        public void render(GuiGraphics g) {
            float px = x + (targetX - x) * progress;
            float py = y + (y2 - y) * progress;
            g.fill((int)px - 1, (int)py - 1, (int)px + 1, (int)py + 1, color);
        }
    }
}
