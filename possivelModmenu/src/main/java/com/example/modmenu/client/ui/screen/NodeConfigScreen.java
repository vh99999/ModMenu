package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.BlockSideSelector;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.*;
import com.example.modmenu.store.logistics.NetworkNode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class NodeConfigScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private final NetworkNode node;
    private net.minecraft.client.gui.components.EditBox nameField;
    
    private static java.util.List<net.minecraft.world.item.ItemStack> lastProbedInventory = null;
    private static java.util.List<Integer> lastProbedSlotX = null;
    private static java.util.List<Integer> lastProbedSlotY = null;
    private static net.minecraft.resources.ResourceLocation lastProbedTexture = null;
    private static UUID lastProbedNodeId = null;

    public static void handleSyncInventory(UUID nodeId, java.util.List<net.minecraft.world.item.ItemStack> inventory, java.util.List<Integer> slotX, java.util.List<Integer> slotY, net.minecraft.resources.ResourceLocation guiTexture) {
        lastProbedNodeId = nodeId;
        lastProbedInventory = inventory;
        lastProbedSlotX = slotX;
        lastProbedSlotY = slotY;
        lastProbedTexture = guiTexture;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof NodeConfigScreen screen) {
            if (screen.node.nodeId.equals(nodeId)) {
                screen.init(net.minecraft.client.Minecraft.getInstance(), screen.width, screen.height);
            }
        }
    }

    public NodeConfigScreen(Screen parent, UUID networkId, NetworkNode node) {
        super(Component.literal("Node Configuration"));
        this.parent = parent;
        this.networkId = networkId;
        this.node = node;
        
        // Request probe on open
        PacketHandler.sendToServer(NodeManagementPacket.probeInventory(networkId, node.nodeId));
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        int midX = this.width / 2;

        nameField = new net.minecraft.client.gui.components.EditBox(font, midX - 100, 15, 180, 20, Component.literal("Node Name"));
        nameField.setValue(node.customName != null ? node.customName : "");
        nameField.setResponder(s -> node.customName = s);
        this.addWidget(nameField);

        this.layoutRoot.addElement(new ResponsiveButton(midX + 85, 15, 50, 20, Component.literal("Icon"), btn -> {
            this.minecraft.setScreen(new PickIconScreen(this, id -> node.iconItemId = id));
        }));

        this.layoutRoot.addElement(new ResponsiveButton(midX + 140, 15, 50, 20, Component.literal("\u00A76PING"), btn -> {
            if (node.pos != null) {
                com.example.modmenu.client.ClientForgeEvents.addPing(node.pos);
                this.minecraft.setScreen(null);
            }
        }));

        // Left Side: Sides Config
        int currentY = 40;
        this.layoutRoot.addElement(new com.example.modmenu.client.ui.base.UIElement(20, currentY, 150, 20) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, "Side Configuration", getX(), getY(), 0xFFFFFFFF);
            }
        });
        currentY += 15;

        if (node.nodeType.equals("BLOCK")) {
            this.layoutRoot.addElement(new BlockSideSelector(20, currentY, 150, 150, node, (dir, mode) -> {
                String current = node.sideConfig.getOrDefault(dir, "NONE");
                String next = mode.equals("PREV") ? cycleTypeBack(current) : cycleType(current);
                if (next.equals("NONE")) node.sideConfig.remove(dir);
                else node.sideConfig.put(dir, next);
            }));
            currentY += 155;

            // Open Real Machine GUI button
            this.layoutRoot.addElement(new ResponsiveButton(20, currentY, 150, 20, Component.literal("\u00A7eOpen Machine GUI"), btn -> {
                PacketHandler.sendToServer(NodeManagementPacket.openGui(networkId, node.nodeId));
                this.minecraft.setScreen(null);
            }));
            currentY += 22;
        } else {
            for (Direction dir : Direction.values()) {
                String currentType = node.sideConfig.getOrDefault(dir, "NONE");
                this.layoutRoot.addElement(new ResponsiveButton(20, currentY, 150, 20, Component.literal(dir.name() + ": " + currentType), btn -> {
                    String nextType = (btn.lastClickedButton == 1) ? cycleTypeBack(currentType) : cycleType(currentType);
                    if (nextType.equals("NONE")) node.sideConfig.remove(dir);
                    else node.sideConfig.put(dir, nextType);
                    btn.setText(Component.literal(dir.name() + ": " + nextType));
                }));
                currentY += 22;
            }
        }

        // Right Side: Slot Grid
        int gridX = 180;
        int gridY = 40;
        int gridWidth = this.width - gridX - 20;
        int gridHeight = this.height - 100;
        
        this.layoutRoot.addElement(new com.example.modmenu.client.ui.base.UIElement(gridX, gridY, 150, 20) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, "Slot Specific Configuration", getX(), getY(), 0xFFFFFFFF);
                g.drawString(font, "\u00A77Click to cycle: \u00A7bBOTH \u00A77-> \u00A76IN \u00A77-> \u00A7dOUT \u00A77-> \u00A78OFF", getX(), getY() + 10, 0xFFAAAAAA);
            }
        });
        gridY += 25;

        if (lastProbedNodeId != null && lastProbedNodeId.equals(node.nodeId) && lastProbedInventory != null) {
            com.example.modmenu.client.ui.base.ScrollableUIContainer slotList = new com.example.modmenu.client.ui.base.ScrollableUIContainer(gridX, gridY, gridWidth, gridHeight);
            this.layoutRoot.addElement(slotList);
            
            // Texture background for slots
            if (lastProbedTexture != null) {
                int texW = 176;
                int texH = lastProbedTexture.getPath().contains("generic_54") ? 222 : 166;
                slotList.addElement(new com.example.modmenu.client.ui.base.UIElement(0, 0, texW, texH) {
                    @Override
                    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                        g.blit(lastProbedTexture, getX(), getY(), 0, 0, getWidth(), getHeight());
                    }
                });
            }

            int maxY = 0;
            if (lastProbedTexture != null) {
                maxY = lastProbedTexture.getPath().contains("generic_54") ? 222 : 166;
            }
            for (int i = 0; i < lastProbedInventory.size(); i++) {
                int sx = lastProbedSlotX.get(i);
                int sy = lastProbedSlotY.get(i);
                maxY = Math.max(maxY, sy + 18);
                slotList.addElement(new SlotComponent(sx, sy, 18, 18, i, node, lastProbedInventory.get(i)));
            }
            slotList.setContentHeight(maxY + 20);
        } else {
            this.layoutRoot.addElement(new com.example.modmenu.client.ui.base.UIElement(gridX, gridY + 20, 200, 20) {
                @Override
                public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(font, "\u00A77Probing inventory...", getX(), getY(), 0xFFAAAAAA);
                }
            });
        }

        this.layoutRoot.addElement(new ResponsiveButton(midX - 50, this.height - 30, 100, 20, Component.literal("Save"), btn -> {
            PacketHandler.sendToServer(NodeManagementPacket.update(networkId, node));
            this.minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (nameField != null) nameField.render(g, mx, my, pt);
    }

    private String cycleType(String current) {
        return switch (current) {
            case "NONE" -> "ITEMS";
            case "ITEMS" -> "ENERGY";
            case "ENERGY" -> "FLUIDS";
            default -> "NONE";
        };
    }

    private String cycleTypeBack(String current) {
        return switch (current) {
            case "NONE" -> "FLUIDS";
            case "FLUIDS" -> "ENERGY";
            case "ENERGY" -> "ITEMS";
            default -> "NONE";
        };
    }

    private class SlotComponent extends com.example.modmenu.client.ui.base.UIElement {
        private final int slotIdx;
        private final NetworkNode node;
        private final net.minecraft.world.item.ItemStack stack;

        public SlotComponent(int x, int y, int w, int h, int slotIdx, NetworkNode node, net.minecraft.world.item.ItemStack stack) {
            super(x, y, w, h);
            this.slotIdx = slotIdx;
            this.node = node;
            this.stack = stack;
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isMouseOver(mx, my);
            String mode = node.slotConfig.getOrDefault(slotIdx, "BOTH");
            
            int color = switch(mode) {
                case "IN" -> 0xAAFFAA00; // Orange
                case "OUT" -> 0xAAFF00FF; // Purple
                case "OFF" -> 0xAA555555; // Gray
                default -> 0xAA00AAFF; // BOTH (Blue)
            };
            
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hov ? 0xDDFFFFFF : color);
            
            if (!stack.isEmpty()) {
                g.renderItem(stack, getX() + 1, getY() + 1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isMouseOver(mx, my)) return false;
            String current = node.slotConfig.getOrDefault(slotIdx, "BOTH");
            String next;
            if (button == 1) { // Right Click
                next = switch(current) {
                    case "BOTH" -> "OFF";
                    case "IN" -> "BOTH";
                    case "OUT" -> "IN";
                    default -> "OUT";
                };
            } else {
                next = switch(current) {
                    case "BOTH" -> "IN";
                    case "IN" -> "OUT";
                    case "OUT" -> "OFF";
                    default -> "BOTH";
                };
            }
            node.slotConfig.put(slotIdx, next);
            return true;
        }
    }
}
