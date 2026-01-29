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
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
public class PickItemFromNodeScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private final UUID targetId;
    private final boolean isGroup;
    private final Consumer<ItemStack> onPick;

    private List<ItemStack> inventory = null;
    private List<Integer> slotX = null;
    private List<Integer> slotY = null;
    private UUID probedTargetId = null;
    public PickItemFromNodeScreen(Screen parent, UUID networkId, UUID targetId, boolean isGroup, Consumer<ItemStack> onPick) {
        super(Component.literal(isGroup ? "Pick Item from Group" : "Pick Item from Source"));
        this.parent = parent;
        this.networkId = networkId;
        this.targetId = targetId;
        this.isGroup = isGroup;
        this.onPick = onPick;

        if (isGroup) {
            PacketHandler.sendToServer(GroupManagementPacket.probeInventory(networkId, targetId));
        } else {
            PacketHandler.sendToServer(NodeManagementPacket.probeInventory(networkId, targetId));
        }
    }
    public void handleSyncInventory(UUID id, List<ItemStack> inventory, List<Integer> slotX, List<Integer> slotY) {
        if (this.targetId.equals(id)) {
            this.probedTargetId = id;
            this.inventory = inventory;
            this.slotX = slotX;
            this.slotY = slotY;
            this.init(this.minecraft, this.width, this.height);
        }
    }
    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));
        if (inventory != null && targetId.equals(probedTargetId)) {
            ScrollableUIContainer slotList = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 80);
            this.layoutRoot.addElement(slotList);

            int slotSize = 18;
            int maxY = 0;

            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (stack.isEmpty()) continue;

                int sx = slotX.get(i);
                int sy = slotY.get(i);
                maxY = Math.max(maxY, sy + slotSize);

                slotList.addElement(new UIElement(sx, sy, slotSize, slotSize) {
                    @Override
                    public void render(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hov ? 0x66FFFFFF : 0x33FFFFFF);
                        g.renderItem(stack, getX() + 1, getY() + 1);

                        if (hov) {
                            addPostRenderTask(graphics -> {
                                java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
                                tooltip.add(stack.getHoverName());
                                if (stack.hasTag()) {
                                    tooltip.add(net.minecraft.network.chat.Component.literal("\u00A7bNBT: \u00A77" + stack.getTag().getAllKeys().size() + " tags"));
                                    for (String key : stack.getTag().getAllKeys()) {
                                        if (tooltip.size() > 10) { tooltip.add(net.minecraft.network.chat.Component.literal("\u00A77...")); break; }
                                        tooltip.add(net.minecraft.network.chat.Component.literal(" \u00A78- " + key));
                                    }
                                }
                                if (!stack.getTags().toList().isEmpty()) {
                                    tooltip.add(net.minecraft.network.chat.Component.literal("\u00A76Tags:"));
                                    stack.getTags().forEach(t -> tooltip.add(net.minecraft.network.chat.Component.literal(" \u00A78# " + t.location())));
                                }
                                graphics.renderComponentTooltip(font, tooltip, absMouseX, absMouseY);
                            });
                        }
                    }
                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        if (!isMouseOver(mx, my)) return false;
                        onPick.accept(stack);
                        minecraft.setScreen(parent);
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
}
