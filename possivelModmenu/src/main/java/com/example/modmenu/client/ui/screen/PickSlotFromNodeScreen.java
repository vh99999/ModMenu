package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.*;
import com.example.modmenu.store.logistics.NetworkNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PickSlotFromNodeScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private final NetworkNode node;
    private final String ruleType;
    private final List<Integer> selectedSlots;
    private final Consumer<List<Integer>> onPick;
    private List<ItemStack> inventory = null;
    private List<Integer> slotX = null;
    private List<Integer> slotY = null;
    private ResourceLocation guiTexture = null;
    private UUID probedNodeId = null;

    public PickSlotFromNodeScreen(Screen parent, UUID networkId, NetworkNode node, String ruleType, List<Integer> currentSlots, Consumer<List<Integer>> onPick) {
        super(Component.literal("Pick Slot"));
        this.parent = parent;
        this.networkId = networkId;
        this.node = node;
        this.ruleType = ruleType;
        this.selectedSlots = new java.util.ArrayList<>(currentSlots);
        this.onPick = onPick;
        PacketHandler.sendToServer(NodeManagementPacket.probeInventory(networkId, node.nodeId));
    }

    public void handleSyncInventory(UUID nodeId, List<ItemStack> inventory, List<Integer> slotX, List<Integer> slotY, ResourceLocation guiTexture) {
        this.probedNodeId = nodeId;
        this.inventory = inventory;
        this.slotX = slotX;
        this.slotY = slotY;
        this.guiTexture = guiTexture;
        this.init(this.minecraft, this.width, this.height);
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));
        this.layoutRoot.addElement(new ResponsiveButton(70, 10, 80, 20, Component.literal("Confirm"), btn -> {
            if (selectedSlots.isEmpty()) selectedSlots.add(-1);
            onPick.accept(selectedSlots);
            this.minecraft.setScreen(parent);
        }));
        this.layoutRoot.addElement(new ResponsiveButton(160, 10, 80, 20, Component.literal("Select ALL"), btn -> {
            selectedSlots.clear();
            selectedSlots.add(-1);
            onPick.accept(selectedSlots);
            this.minecraft.setScreen(parent);
        }));
        if (inventory != null && probedNodeId.equals(node.nodeId)) {
            ScrollableUIContainer slotList = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 80);
            this.layoutRoot.addElement(slotList);
            if (guiTexture != null) {
                int texW = 176;
                int texH = guiTexture.getPath().contains("generic_54") ? 222 : 166;
                slotList.addElement(new UIElement(0, 0, texW, texH) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        g.blit(guiTexture, getX(), getY(), 0, 0, getWidth(), getHeight());
                    }
                });
            }

            if (node.nodeType.equals("PLAYER")) {
                slotList.addElement(new UIElement(26, 8, 51, 72) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        InventoryScreen.renderEntityInInventoryFollowsMouse(g, getX() + 25, getY() + 65, 30, (float)(getX() + 25 - mx), (float)(getY() + 30 - my), minecraft.player);
                    }
                });
            }

            int slotSize = 18;
            int maxY = 0;
            for (int i = 0; i < inventory.size(); i++) {
                final int slotIndex = i;
                ItemStack stack = inventory.get(i);
                int sx = slotX.get(i);
                int sy = slotY.get(i);
                maxY = Math.max(maxY, sy + slotSize);
                slotList.addElement(new UIElement(sx, sy, slotSize, slotSize) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
                        boolean selected = selectedSlots.contains(slotIndex);
                        
                        int baseColor = selected ? 0xAAFFFF00 : 0x33FFFFFF; // Yellow if selected
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hov ? 0xAA00AAFF : baseColor);
                        
                        if (!stack.isEmpty()) {
                            g.renderItem(stack, getX() + 1, getY() + 1);
                            if (isCompatible(stack, ruleType)) {
                                int color = ruleType.equals("ENERGY") ? 0x66FFAA00 : 0x6600AAFF;
                                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
                            }
                        }
                        if (hov) {
                            addPostRenderTask(graphics -> {
                                java.util.List<Component> tooltip = new java.util.ArrayList<>();
                                tooltip.add(Component.literal("\u00A7bSlot: " + slotIndex));
                                if (selected) tooltip.add(Component.literal("\u00A7e[SELECTED]"));
                                if (!stack.isEmpty()) {
                                    tooltip.add(stack.getHoverName());
                                    if (isCompatible(stack, ruleType)) {
                                        tooltip.add(Component.literal("\u00A7e\u26A0 " + ruleType + " COMPATIBLE"));
                                    }
                                } else {
                                    tooltip.add(Component.literal("\u00A78(Empty)"));
                                }
                                graphics.renderComponentTooltip(font, tooltip, absMouseX, absMouseY);
                            });
                        }
                    }

                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        if (!isMouseOver(mx, my)) return false;
                        if (selectedSlots.contains(slotIndex)) {
                            selectedSlots.remove(Integer.valueOf(slotIndex));
                        } else {
                            selectedSlots.remove(Integer.valueOf(-1));
                            selectedSlots.add(slotIndex);
                        }
                        return true;
                    }
                });
            }
            slotList.setContentHeight(maxY + 20);
        } else {
            this.layoutRoot.addElement(new UIElement(this.width / 2 - 50, this.height / 2, 100, 20) {
                @Override
                public void render(GuiGraphics g, int mx, int my, float pt) {
                    g.drawCenteredString(font, "Probing source inventory...", 0, 0, 0xFFAAAAAA);
                }
            });
        }
    }

    private boolean isCompatible(ItemStack stack, String type) {
        if (stack.isEmpty()) return false;
        if (type.equals("ENERGY")) return stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
        if (type.equals("FLUIDS")) return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
        return false;
    }
}
