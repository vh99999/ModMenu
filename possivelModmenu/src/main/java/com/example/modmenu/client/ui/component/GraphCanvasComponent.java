package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.screen.NodeConfigScreen;
import com.example.modmenu.network.*;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.NodeGroup;
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
    private UUID draggingGroupId = null;
    private UUID linkingFromNodeId = null;
    private UUID linkingFromGroupId = null;
    private double lastMouseX, lastMouseY;
    private NetworkNode hoveredNode = null;
    private NodeGroup hoveredGroup = null;
    private LogisticsRule hoveredRule = null;
    private final java.util.List<UIParticle> particles = new java.util.ArrayList<>();
    private String historySearchTerm = "";
    private NetworkNode copiedConfig = null;
    private boolean brushMode = false;

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

    public void setHistorySearchTerm(String term) {
        this.historySearchTerm = term.toLowerCase();
    }

    public void setBrushMode(boolean active) {
        this.brushMode = active;
    }

    public boolean isBrushMode() {
        return brushMode;
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
        hoveredRule = null;
        drawRuleConnections(g, worldMX, worldMY);

        // Linking Preview
        if (linkingFromNodeId != null) {
            NetworkNode src = findNode(linkingFromNodeId);
            if (src != null) {
                drawLine(g, src.guiX, src.guiY, (int)worldMX, (int)worldMY, 0xFFFFAA00, true, false);
            }
        }
        if (linkingFromGroupId != null) {
            com.example.modmenu.store.logistics.NodeGroup src = findGroup(linkingFromGroupId);
            if (src != null) {
                drawLine(g, src.guiX, src.guiY, (int)worldMX, (int)worldMY, 0xFFFFAA00, true, false);
            }
        }

        // Simulation / Active Flow Particles
        updateAndRenderParticles(g, pt);
        
        // Draw Groups
        hoveredGroup = null;
        for (NodeGroup group : network.groups) {
            boolean isHovered = Math.abs(worldMX - group.guiX) < 35 && Math.abs(worldMY - group.guiY) < 35;
            if (isHovered) hoveredGroup = group;
            renderGroup(g, group, isHovered);
        }

        // Draw Nodes
        hoveredNode = null;
        for (NetworkNode node : network.nodes) {
            if (node == null || !isNodeVisible(node)) continue;
            // Expanded padding zone to ensure sub-buttons (P and X) don't flicker
            boolean isHovered = Math.abs(worldMX - node.guiX) < 30 && worldMY > node.guiY - 32 && worldMY < node.guiY + 25;
            if (isHovered && hoveredGroup == null) hoveredNode = node;
            renderNode(g, node, isHovered);
        }

        g.pose().popPose();
        g.disableScissor();

        if (brushMode) {
            String txt = copiedConfig == null ? "\u00A7e[BRUSH] Select source node" : "\u00A7a[BRUSH] Ready to paint (Right-click to clear)";
            g.drawString(Minecraft.getInstance().font, txt, getX() + 10, getY() + getHeight() - 20, 0xFFFFFFFF);
            if (copiedConfig != null) {
                String iconId = copiedConfig.iconItemId != null ? copiedConfig.iconItemId : (copiedConfig.nodeType.equals("BLOCK") ? copiedConfig.blockId : null);
                if (iconId != null) {
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(iconId));
                    if (item != null) {
                        g.renderItem(new ItemStack(item), getX() + 10, getY() + getHeight() - 40);
                    }
                }
            }
        }
        
        if (hoveredNode != null) {
            // Diagnostic Tooltip
            final NetworkNode node = hoveredNode;
            parentScreen.addPostRenderTask(graphics -> {
                java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                lines.add(net.minecraft.network.chat.Component.literal("\u00A7e\u00A7l" + (node.customName != null ? node.customName : node.nodeType)));
                
                // Detailed Node Type Info
                switch(node.nodeType) {
                    case "BLOCK" -> {
                        if (node.pos != null) {
                            lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fPhysical Block"));
                            lines.add(net.minecraft.network.chat.Component.literal("\u00A77Pos: \u00A7f" + node.pos.toShortString()));
                            lines.add(net.minecraft.network.chat.Component.literal("\u00A77Dim: \u00A7f" + node.dimension));
                        }
                    }
                    case "PLAYER" -> lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fPlayer Inventory (Virtual)"));
                    case "MARKET" -> lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fGlobal Market (Auto-Sell)"));
                    case "CHAMBER" -> lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fVirtual Chamber (Simulated)"));
                    case "BUFFER" -> lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fNetwork Cloud Buffer"));
                    case "TRASH" -> lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fAbsolute Deletion Port"));
                    case "PORT_INPUT" -> lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fSub-Network Entrance"));
                    case "PORT_OUTPUT" -> lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fSub-Network Exit"));
                    case "SUB_NETWORK" -> {
                        lines.add(net.minecraft.network.chat.Component.literal("\u00A77Type: \u00A7fExternal Network Proxy"));
                        if (node.referencedNetworkId != null) {
                            lines.add(net.minecraft.network.chat.Component.literal("\u00A77ID: \u00A7f" + node.referencedNetworkId.toString().substring(0, 8) + "..."));
                        }
                    }
                }

                if (!node.virtualItemBuffer.isEmpty() || node.virtualEnergyBuffer > 0 || !node.virtualFluidBuffer.isEmpty()) {
                    lines.add(net.minecraft.network.chat.Component.literal(""));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A7b[ INTERNAL BUFFER ]"));
                    if (node.virtualEnergyBuffer > 0) lines.add(net.minecraft.network.chat.Component.literal("\u00A76Energy: \u00A7f" + node.virtualEnergyBuffer + " FE"));
                    if (!node.virtualFluidBuffer.isEmpty()) lines.add(net.minecraft.network.chat.Component.literal("\u00A7aFluids: \u00A7f" + node.virtualFluidBuffer.size() + " types"));
                    if (!node.virtualItemBuffer.isEmpty()) lines.add(net.minecraft.network.chat.Component.literal("\u00A7eItems: \u00A7f" + node.virtualItemBuffer.size() + " stacks"));
                }
                
                if (node.isMissing) {
                    lines.add(net.minecraft.network.chat.Component.literal(""));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A7c\u00A7l\u26A0 ERROR: BLOCK OFFLINE"));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Expected: \u00A7f" + node.blockId));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Status: Block missing or chunk unloaded."));
                }
                
                graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mx, my);

                // Render Large Icon if present
                String tooltipIcon = node.iconItemId != null ? node.iconItemId : (node.nodeType.equals("BLOCK") ? node.blockId : null);
                if (tooltipIcon != null) {
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(tooltipIcon));
                    if (item != null) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(mx, my - 22, 500);
                        graphics.renderItem(new ItemStack(item), 0, 0);
                        graphics.pose().popPose();
                    }
                }
            });
        } else if (hoveredGroup != null) {
            final com.example.modmenu.store.logistics.NodeGroup group = hoveredGroup;
            parentScreen.addPostRenderTask(graphics -> {
                java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                lines.add(net.minecraft.network.chat.Component.literal("\u00A76\u00A7l[GROUP] " + (group.name != null ? group.name : "Unnamed")));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Nodes: \u00A7f" + group.nodeIds.size()));
                lines.add(net.minecraft.network.chat.Component.literal(""));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Ctrl+Click to " + (group.expanded ? "Collapse" : "Expand")));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Right-click to Configure"));
                graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mx, my);
            });
        } else if (hoveredRule != null) {
            final LogisticsRule rule = hoveredRule;
            parentScreen.addPostRenderTask(graphics -> {
                java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                String typeCol = switch(rule.type) {
                    case "ENERGY" -> "\u00A76";
                    case "FLUIDS" -> "\u00A7a";
                    default -> "\u00A7b";
                };
                lines.add(net.minecraft.network.chat.Component.literal(typeCol + "\u00A7l[ RULE: " + rule.type + " ]"));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Action: \u00A7f" + rule.ruleAction));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Trigger: \u00A7f" + rule.triggerType + (rule.triggerType.equals("SIGNAL") ? " (" + rule.signalFilter + ")" : "")));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Priority: \u00A7f" + rule.priority + " \u00A77| Mode: \u00A7f" + rule.mode));
                
                if (!rule.variableName.isEmpty()) {
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Target Var: \u00A7e" + rule.variableName));
                }

                if (rule.lastReport != null && !rule.lastReport.isEmpty()) {
                    lines.add(net.minecraft.network.chat.Component.literal(""));
                    lines.add(net.minecraft.network.chat.Component.literal("\u00A77Last Report:"));
                    lines.add(net.minecraft.network.chat.Component.literal("  " + rule.lastReport.replace("[ACTIVE] ", "\u00A7a").replace("[SEARCH] ", "\u00A7e").replace("[FULL] ", "\u00A76").replace("[BLOCKED] ", "\u00A7c").replace("[ERROR] ", "\u00A7c\u00A7l")));
                }
                
                lines.add(net.minecraft.network.chat.Component.literal(""));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Click Left Side to Edit"));
                lines.add(net.minecraft.network.chat.Component.literal("\u00A77Click Right Side to Test"));
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
                    int[] srcPos = getTargetPos(rule.sourceNodeId, rule.sourceIsGroup);
                    int[] dstPos = getTargetPos(rule.destNodeId, rule.destIsGroup);
                    
                    if (srcPos != null && dstPos != null && Math.random() < 0.1) {
                        int color = network.simulationActive ? 0xFF00FFFF : 0xFF55FF55;
                        if (rule.lastReport != null && rule.lastReport.startsWith("[OVERFLOW]")) {
                            color = 0xFFDD00FF;
                            // For overflow, the destination pos should be the overflow target!
                            dstPos = getTargetPos(network.overflowTargetId, network.overflowIsGroup);
                        }
                        if (dstPos != null) particles.add(new UIParticle(srcPos[0], srcPos[1], dstPos[0], dstPos[1], color));
                    }
                }
            }

            // Flight Recorder: Spawn particles for history matches
            if (!historySearchTerm.isEmpty() && !network.movementHistory.isEmpty()) {
                for (com.example.modmenu.store.logistics.MovementRecord rec : network.movementHistory) {
                    if (rec.itemName.toLowerCase().contains(historySearchTerm) || rec.itemId.toLowerCase().contains(historySearchTerm)) {
                        int[] srcPos = getTargetPos(rec.sourceNodeId, false);
                        int[] dstPos = getTargetPos(rec.destNodeId, false);
                        if (srcPos != null && dstPos != null && Math.random() < 0.05) {
                            particles.add(new UIParticle(srcPos[0], srcPos[1], dstPos[0], dstPos[1], 0xFFFFCC00, 2)); // Gold for history, size 2
                        }
                    }
                }
            }
        }

        // Phase 5: Signal Pulses visualization
        if (!network.recentSignals.isEmpty() && System.currentTimeMillis() % 100 < 50) {
            for (com.example.modmenu.store.logistics.LogisticsSignal sig : network.recentSignals) {
                int[] srcPos = getTargetPos(sig.sourceNodeId, false);
                if (srcPos == null) continue;
                
                // Visualize signal traveling to matching rules
                for (LogisticsRule rule : network.rules) {
                    if (rule.active && "SIGNAL".equals(rule.triggerType) && sig.type.equals(rule.signalFilter)) {
                        if (rule.triggerNodeId == null || rule.triggerNodeId.equals(sig.sourceNodeId)) {
                            int[] dstPos = getTargetPos(rule.ruleId, false); // Signals target the rule itself!
                            if (dstPos == null) dstPos = getTargetPos(rule.sourceNodeId, rule.sourceIsGroup);
                            
                            if (dstPos != null && Math.random() < 0.2) {
                                particles.add(new UIParticle(srcPos[0], srcPos[1], dstPos[0], dstPos[1], 0xFFFF55FF, 1)); // Magenta for signals
                            }
                        }
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
            int[] srcPos = getTargetPos(rule.sourceNodeId, rule.sourceIsGroup);
            int[] dstPos = getTargetPos(rule.destNodeId, rule.destIsGroup);
            
            if (srcPos != null && dstPos != null) {
                int color = switch(rule.type) {
                    case "ENERGY" -> 0xFFFFAA00;
                    case "FLUIDS" -> 0xFF00FF00;
                    default -> 0xFF00AAFF; // ITEMS
                };
                
                if (!rule.active) color = 0xFF555555;
                if (rule.lastReport != null && rule.lastReport.contains("Moved")) {
                    // Success highlight
                    int r = (color >> 16) & 0xFF;
                    int gr = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    color = 0xFF000000 | (Math.min(255, r + 50) << 16) | (Math.min(255, gr + 100) << 8) | Math.min(255, b + 50);
                }
                if (rule.lastReport != null && rule.lastReport.startsWith("[OVERFLOW]")) {
                    color = 0xFFDD00FF; // Distinct Purple for overflow
                }
                
                boolean isBroken = false;
                if (rule.sourceIsGroup) {
                    com.example.modmenu.store.logistics.NodeGroup sg = findGroup(rule.sourceNodeId);
                    if (sg == null || sg.nodeIds.isEmpty()) isBroken = true;
                } else {
                    NetworkNode sn = findNode(rule.sourceNodeId);
                    if (sn == null || sn.isMissing) isBroken = true;
                }
                if (rule.destIsGroup) {
                    com.example.modmenu.store.logistics.NodeGroup dg = findGroup(rule.destNodeId);
                    if (dg == null || dg.nodeIds.isEmpty()) isBroken = true;
                } else {
                    NetworkNode dn = findNode(rule.destNodeId);
                    if (dn == null || dn.isMissing) isBroken = true;
                }
                
                if (isBroken) color = 0xFFFF0000;
                
                drawLine(g, srcPos[0], srcPos[1], dstPos[0], dstPos[1], color, rule.active, isBroken);

                if (rule.lastReport != null && rule.lastReport.startsWith("[OVERFLOW]")) {
                    int[] ovPos = getTargetPos(network.overflowTargetId, network.overflowIsGroup);
                    if (ovPos != null) {
                        drawLine(g, srcPos[0], srcPos[1], ovPos[0], ovPos[1], 0xFFDD00FF, true, true);
                    }
                }
                
                // Draw Status Icon
                String statusIcon = "";
                if (rule.lastReport != null) {
                    if (rule.lastReport.startsWith("[SEARCH]")) statusIcon = "\uD83D\uDD0D"; // 🔍
                    else if (rule.lastReport.startsWith("[FULL]")) statusIcon = "\uD83D\uDCE6"; // 📦
                    else if (rule.lastReport.startsWith("[ACTIVE]")) statusIcon = "\u26A1"; // ⚡
                    else if (rule.lastReport.startsWith("[BLOCKED]")) statusIcon = "\uD83D\uDEAB"; // 🚫
                    else if (rule.lastReport.startsWith("[ERROR]")) statusIcon = "\u26A0"; // ⚠
                }
                
                int midX = (srcPos[0] + dstPos[0]) / 2;
                int midY = (srcPos[1] + dstPos[1]) / 2;

                if (Math.sqrt(Math.pow(worldMX - midX, 2) + Math.pow(worldMY - midY, 2)) < 15) {
                    hoveredRule = rule;
                }

                if (!statusIcon.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(midX, midY - 15, 0);
                    g.drawCenteredString(Minecraft.getInstance().font, statusIcon, 0, 0, 0xFFFFFFFF);
                    g.pose().popPose();
                }

                // Safety Alert: Valuable to Market/Trash
                boolean isDestPotentiallyDangerous = false;
                if (rule.destIsGroup) {
                    // Groups are harder to judge but let's assume if name has trash
                    NodeGroup dg = findGroup(rule.destNodeId);
                    if (dg != null && dg.name != null && dg.name.toLowerCase().contains("trash")) isDestPotentiallyDangerous = true;
                } else {
                    NetworkNode dn = findNode(rule.destNodeId);
                    if (dn != null) {
                        if (dn.nodeType.equals("MARKET")) isDestPotentiallyDangerous = true;
                        if (dn.customName != null && dn.customName.toLowerCase().contains("trash")) isDestPotentiallyDangerous = true;
                    }
                }

                if (isDestPotentiallyDangerous) {
                    boolean isValuable = false;
                    if (rule.filter.matchType.equals("SEMANTIC") && rule.filter.matchValues.contains("IS_ORE")) {
                        isValuable = true;
                    } else {
                        for (String val : rule.filter.matchValues) {
                            String low = val.toLowerCase();
                            if (low.contains("diamond") || low.contains("netherite") || low.contains("emerald") || low.contains("gold") || low.contains("totem") || low.contains("elytra") || low.contains("shulker") || low.contains("ancient_debris")) {
                                isValuable = true;
                                break;
                            }
                        }
                    }
                    
                    if (isValuable && !rule.filter.blacklist) {
                        // Draw warning triangle
                        g.pose().pushPose();
                        g.pose().translate(midX, midY + 10, 0);
                        g.pose().scale(0.8f, 0.8f, 1.0f);
                        g.drawCenteredString(Minecraft.getInstance().font, "\u00A7c\u26A0 VALUABLE AT RISK", 0, 0, 0xFFFF0000);
                        g.pose().popPose();
                    }
                }

                // Draw Flow Arrow in middle
                drawArrow(g, srcPos[0], srcPos[1], dstPos[0], dstPos[1], color);

                // Draw Edit handle in middle
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

    private void renderGroup(GuiGraphics g, com.example.modmenu.store.logistics.NodeGroup group, boolean hovered) {
        int color = hovered ? 0xAA66AAFF : 0xAA4488DD;
        if (!group.expanded) color = hovered ? 0xAAFFAA00 : 0xAAFF8800; // Orange for collapsed
        
        g.pose().pushPose();
        g.pose().translate(group.guiX, group.guiY, 0);
        
        // Draw bubble
        int radius = group.expanded ? 40 : 25;
        g.fill(-radius, -radius, radius, radius, color);
        g.renderOutline(-radius, -radius, radius * 2, radius * 2, 0xFFFFFFFF);
        
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 1.0f);
        g.drawCenteredString(Minecraft.getInstance().font, "[GROUP]", 0, -radius + 10, 0xFFEEEEEE);
        g.drawCenteredString(Minecraft.getInstance().font, group.name != null ? group.name : "Unnamed", 0, -radius + 20, 0xFFFFFFFF);
        
        if (!group.expanded) {
            g.drawCenteredString(Minecraft.getInstance().font, group.nodeIds.size() + " Nodes", 0, 5, 0xFFAAAAAA);
        } else {
            g.drawCenteredString(Minecraft.getInstance().font, "(Expanded)", 0, radius - 15, 0xFFAAAAAA);
        }
        g.pose().popPose();

        if (hovered) {
             // Expand/Collapse sub-button
             int bx = radius - 12;
             int by = -radius - 15;
             g.fill(bx, by, bx + 18, by + 12, group.expanded ? 0xFF555555 : 0xFF00AAFF);
             g.renderOutline(bx, by, 18, 12, 0xFFFFFFFF);
             g.drawCenteredString(Minecraft.getInstance().font, group.expanded ? "COL" : "EXP", bx + 9, by + 2, 0xFFFFFFFF);
        }
        
        g.pose().popPose();
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
        String displayItemId = node.iconItemId != null ? node.iconItemId : (node.nodeType.equals("BLOCK") ? node.blockId : null);
        if (displayItemId != null) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(displayItemId));
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
        if (id == null) return null;
        for (NetworkNode n : network.nodes) {
            if (n != null && n.nodeId.equals(id)) return n;
        }
        return null;
    }

    private com.example.modmenu.store.logistics.NodeGroup findGroup(UUID id) {
        for (com.example.modmenu.store.logistics.NodeGroup g : network.groups) if (g.groupId.equals(id)) return g;
        return null;
    }

    private com.example.modmenu.store.logistics.NodeGroup getNodeGroup(UUID nodeId) {
        for (com.example.modmenu.store.logistics.NodeGroup g : network.groups) {
            if (g.nodeIds.contains(nodeId)) return g;
        }
        return null;
    }

    private boolean isNodeVisible(NetworkNode node) {
        if (node == null) return false;
        com.example.modmenu.store.logistics.NodeGroup g = getNodeGroup(node.nodeId);
        return g == null || g.expanded;
    }

    private int[] getTargetPos(UUID id, boolean isGroup) {
        if (isGroup) {
            com.example.modmenu.store.logistics.NodeGroup g = findGroup(id);
            if (g != null) return new int[]{g.guiX, g.guiY};
        } else {
            NetworkNode n = findNode(id);
            if (n != null) {
                com.example.modmenu.store.logistics.NodeGroup g = getNodeGroup(id);
                if (g != null && !g.expanded) return new int[]{g.guiX, g.guiY};
                return new int[]{n.guiX, n.guiY};
            }
        }
        return new int[]{0, 0};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my)) return false;

        if (brushMode && button == 1) {
            copiedConfig = null;
            return true;
        }
        
        double worldMX = (mx - getX() - cameraX) / zoom;
        double worldMY = (my - getY() - cameraY) / zoom;

        // Check Groups
        for (com.example.modmenu.store.logistics.NodeGroup group : network.groups) {
            int radius = group.expanded ? 40 : 25;
            if (Math.abs(worldMX - group.guiX) < radius && Math.abs(worldMY - group.guiY) < radius) {
                // Check EXP/COL sub-button
                if (worldMX > group.guiX + radius - 12 && worldMX < group.guiX + radius + 6 && worldMY > group.guiY - radius - 15 && worldMY < group.guiY - radius - 3) {
                    group.expanded = !group.expanded;
                    PacketHandler.sendToServer(GroupManagementPacket.addUpdate(network.networkId, group));
                    return true;
                }

                if (button == 0) {
                    if (Screen.hasShiftDown()) {
                        linkingFromGroupId = group.groupId;
                    } else if (Screen.hasControlDown()) {
                        group.expanded = !group.expanded;
                        PacketHandler.sendToServer(GroupManagementPacket.addUpdate(network.networkId, group));
                    } else {
                        draggingGroupId = group.groupId;
                    }
                    lastMouseX = mx;
                    lastMouseY = my;
                    return true;
                } else if (button == 1) {
                    Minecraft.getInstance().setScreen(new com.example.modmenu.client.ui.screen.NodeGroupConfigScreen(parentScreen, network.networkId, network, group, false));
                    return true;
                }
            }
        }

        // Check Nodes
        for (NetworkNode node : network.nodes) {
            if (node == null || !isNodeVisible(node)) continue;
            if (Math.abs(worldMX - node.guiX) < 30 && worldMY > node.guiY - 32 && worldMY < node.guiY + 25) {
                // Check sub-buttons hitboxes
                if (worldMX > node.guiX + 11 && worldMX < node.guiX + 25 && worldMY > node.guiY - 28 && worldMY < node.guiY - 16) {
                    // Delete
                    PacketHandler.sendToServer(NodeManagementPacket.remove(network.networkId, node.nodeId));
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
                    if (brushMode) {
                        if (copiedConfig == null) {
                            copiedConfig = node.snapshot();
                            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f));
                        } else {
                            PacketHandler.sendToServer(NodeManagementPacket.pasteConfig(network.networkId, node.nodeId, copiedConfig));
                            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0f));
                        }
                        return true;
                    }
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
                        PacketHandler.sendToServer(RuleManagementPacket.test(network.networkId, rule.ruleId));
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
                if (node != null) PacketHandler.sendToServer(NodeManagementPacket.update(network.networkId, node));
            }
            if (draggingGroupId != null) {
                com.example.modmenu.store.logistics.NodeGroup group = findGroup(draggingGroupId);
                if (group != null) PacketHandler.sendToServer(GroupManagementPacket.addUpdate(network.networkId, group));
            }

            if (linkingFromNodeId != null || linkingFromGroupId != null) {
                double worldMX = (mx - getX() - cameraX) / zoom;
                double worldMY = (my - getY() - cameraY) / zoom;
                
                UUID targetId = null;
                boolean targetIsGroup = false;

                // Check Group targets
                for (com.example.modmenu.store.logistics.NodeGroup g : network.groups) {
                    int radius = g.expanded ? 40 : 25;
                    if (Math.abs(worldMX - g.guiX) < radius && Math.abs(worldMY - g.guiY) < radius) {
                        targetId = g.groupId;
                        targetIsGroup = true;
                        break;
                    }
                }
                
                // Check Node targets if no group found
                if (targetId == null) {
                    for (NetworkNode n : network.nodes) {
                        if (n == null || !isNodeVisible(n)) continue;
                        if (Math.abs(worldMX - n.guiX) < 30 && Math.abs(worldMY - n.guiY) < 30) {
                            targetId = n.nodeId;
                            targetIsGroup = false;
                            break;
                        }
                    }
                }

                if (targetId != null) {
                    UUID fromId = linkingFromNodeId != null ? linkingFromNodeId : linkingFromGroupId;
                    boolean fromIsGroup = linkingFromGroupId != null;
                    
                    if (!targetId.equals(fromId)) {
                        LogisticsRule newRule = new LogisticsRule();
                        newRule.sourceNodeId = fromId;
                        newRule.sourceIsGroup = fromIsGroup;
                        newRule.destNodeId = targetId;
                        newRule.destIsGroup = targetIsGroup;
                        Minecraft.getInstance().setScreen(new com.example.modmenu.client.ui.screen.RuleConfigScreen(parentScreen, network.networkId, network, newRule, true));
                    }
                }
            }
            isPanning = false;
            draggingNodeId = null;
            draggingGroupId = null;
            linkingFromNodeId = null;
            linkingFromGroupId = null;
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
        if (draggingGroupId != null) {
            com.example.modmenu.store.logistics.NodeGroup group = findGroup(draggingGroupId);
            if (group != null) {
                group.guiX += dx / zoom;
                group.guiY += dy / zoom;
                for (UUID id : group.nodeIds) {
                    NetworkNode n = findNode(id);
                    if (n != null) {
                        n.guiX += dx / zoom;
                        n.guiY += dy / zoom;
                    }
                }
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
        int size = 1;
        
        public UIParticle(float x, float y, float tx, float ty, int color) {
            this(x, y, tx, ty, color, 1);
        }

        public UIParticle(float x, float y, float tx, float ty, int color, int size) {
            this.x = x; this.y = y; this.targetX = tx; this.y2 = ty; this.color = color;
            this.size = size;
        }
        
        public boolean update(float delta) {
            progress += delta;
            return progress >= 1.0f;
        }
        
        public void render(GuiGraphics g) {
            float px = x + (targetX - x) * progress;
            float py = y + (y2 - y) * progress;
            g.fill((int)px - size, (int)py - size, (int)px + size, (int)py + size, color);
        }
    }
}
