package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionNetworkPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.logistics.LogisticsFilter;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

public class FilterConfigScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final LogisticsFilter filter;
    private final UUID networkId;
    private final NetworkData networkData;
    private final UUID sourceNodeId;
    private final boolean sourceIsGroup;
    private EditBox filterInput;
    private List<ItemStack> sourceInventoryPreview = null;

    public FilterConfigScreen(Screen parent, LogisticsFilter filter, UUID networkId, NetworkData networkData, UUID sourceNodeId, boolean sourceIsGroup) {
        super(Component.literal("Filter Configuration"));
        this.parent = parent;
        this.filter = filter;
        this.networkId = networkId;
        this.networkData = networkData;
        this.sourceNodeId = sourceNodeId;
        this.sourceIsGroup = sourceIsGroup;
        
        if (sourceNodeId != null) {
            if (sourceIsGroup) {
                PacketHandler.sendToServer(ActionNetworkPacket.requestGroupInventoryProbe(networkId, sourceNodeId));
            } else {
                PacketHandler.sendToServer(ActionNetworkPacket.requestInventoryProbe(networkId, sourceNodeId));
            }
        }
    }

    public void handleSyncInventory(UUID id, List<ItemStack> inventory, List<Integer> slotX, List<Integer> slotY, net.minecraft.resources.ResourceLocation guiTexture) {
        if (sourceNodeId != null && sourceNodeId.equals(id)) {
            this.sourceInventoryPreview = inventory;
        }
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> this.minecraft.setScreen(parent)));

        int midX = this.width / 2;
        int currentY = 40;

        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 105, 20, Component.literal("Type: " + filter.matchType), btn -> {
            filter.matchType = (btn.lastClickedButton == 1) ? cycleMatchTypeBack(filter.matchType) : cycleMatchType(filter.matchType);
            this.init(this.minecraft, this.width, this.height);
        }));
        this.layoutRoot.addElement(new ResponsiveButton(midX - 40, currentY, 40, 20, Component.literal("Clear"), btn -> {
            filter.matchValues.clear();
            this.init(this.minecraft, this.width, this.height);
        }));
        this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Filter: " + (filter.blacklist ? "BLACKLIST" : "WHITELIST")), btn -> {
            filter.blacklist = !filter.blacklist;
            btn.setText(Component.literal("Filter: " + (filter.blacklist ? "BLACKLIST" : "WHITELIST")));
        }));
        currentY += 25;

        if (filter.matchType.equals("NBT")) {
            this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 300, 20, Component.literal("NBT Mode: " + (filter.fuzzyNbt ? "FUZZY" : "STRICT")), btn -> {
                filter.fuzzyNbt = !filter.fuzzyNbt;
                btn.setText(Component.literal("NBT Mode: " + (filter.fuzzyNbt ? "FUZZY" : "STRICT")));
            }));
            currentY += 25;
        }

        if (filter.matchType.equals("SEMANTIC")) {
            String[] categories = {"IS_FOOD", "IS_FUEL", "IS_ORE", "IS_DAMAGED"};
            int bx = midX - 150;
            for (String cat : categories) {
                boolean active = filter.matchValues.contains(cat);
                this.layoutRoot.addElement(new ResponsiveButton(bx, currentY, 72, 20, Component.literal((active ? "\u00A7a" : "\u00A77") + cat.substring(3)), btn -> {
                    if (filter.matchValues.contains(cat)) filter.matchValues.remove(cat);
                    else filter.matchValues.add(cat);
                    this.init(this.minecraft, this.width, this.height);
                }));
                bx += 75;
            }
            currentY += 25;
        }

        if (!filter.matchType.equals("ALL") && !filter.matchType.equals("SEMANTIC")) {
            filterInput = new EditBox(font, midX - 150, currentY, 300, 20, Component.literal("Filter Value"));
            filterInput.setValue(String.join(", ", filter.matchValues));
            filterInput.setResponder(s -> {
                filter.matchValues.clear();
                for (String part : s.split(",")) {
                    String trim = part.trim();
                    if (!trim.isEmpty()) filter.matchValues.add(trim);
                }
            });
            this.addRenderableWidget(filterInput);
            currentY += 25;
        }

        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 145, 20, Component.literal("Set from Hand"), btn -> {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (!held.isEmpty()) applyItemToFilter(held);
        }));
        
        if (sourceNodeId != null) {
            this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Pick from Source"), btn -> {
                this.minecraft.setScreen(new PickItemFromNodeScreen(this, networkId, sourceNodeId, sourceIsGroup, this::applyItemToFilter));
            }));
        }
        currentY += 25;

        // Match Preview
        this.layoutRoot.addElement(new UIElement(midX - 150, currentY, 300, 30) {
            @Override
            public void render(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, "Match Preview:", getX(), getY(), 0xFFAAAAAA);
                if (filter.matchType.equals("ALL")) {
                    g.drawString(font, "\u00A7e[MATCHING ALL]", getX() + 80, getY(), 0xFFFFFF00);
                }
                if (sourceInventoryPreview != null) {
                    int dx = 0;
                    for (ItemStack stack : sourceInventoryPreview) {
                        if (!stack.isEmpty() && matchesFilterLocal(stack)) {
                            g.renderItem(stack, getX() + dx, getY() + 12);
                            if (mx >= getX() + dx && mx < getX() + dx + 16 && my >= getY() + 12 && my < getY() + 12 + 16) {
                                g.renderComponentTooltip(font, List.of(stack.getHoverName(), Component.literal("\u00A7cRight-click to remove")), mx, my);
                            }
                            dx += 18;
                            if (dx > 280) break;
                        }
                    }
                }
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (button == 1 && sourceInventoryPreview != null && !filter.matchType.equals("ALL")) {
                    int dx = 0;
                    for (ItemStack stack : sourceInventoryPreview) {
                        if (!stack.isEmpty() && matchesFilterLocal(stack)) {
                            if (mx >= getX() + dx && mx < getX() + dx + 16 && my >= getY() + 12 && my < getY() + 12 + 16) {
                                String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                                if (filter.matchType.equals("ID") || filter.matchType.equals("NBT")) {
                                    filter.matchValues.remove(id);
                                } else if (filter.matchType.equals("TAG")) {
                                    stack.getTags().forEach(t -> filter.matchValues.remove(t.location().toString()));
                                }
                                init(minecraft, width, height);
                                return true;
                            }
                            dx += 18;
                            if (dx > 280) break;
                        }
                    }
                }
                return false;
            }
        });
    }

    private boolean matchesFilterLocal(ItemStack stack) {
        if (filter.matchType.equals("ALL")) return !filter.blacklist;
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        boolean match = false;
        for (String finalVal : filter.matchValues) {
            if (finalVal.isEmpty()) continue;
            boolean m = false;
            switch (filter.matchType) {
                case "ID" -> {
                    if (finalVal.contains("*")) {
                        try { m = itemId.matches(finalVal.replace(".", "\\.").replace("*", ".*")); } catch (Exception e) {}
                    } else m = itemId.equals(finalVal);
                }
                case "TAG" -> m = stack.getTags().anyMatch(t -> t.location().toString().equals(finalVal));
                case "NBT" -> m = itemId.equals(finalVal) && (filter.nbtSample == null ? !stack.hasTag() : (stack.hasTag() && net.minecraft.nbt.NbtUtils.compareNbt(filter.nbtSample, stack.getTag(), !filter.fuzzyNbt)));
            }
            if (m) { match = true; break; }
        }
        return filter.blacklist != match;
    }

    private void applyItemToFilter(ItemStack stack) {
        if (filter.matchType.equals("ALL")) filter.matchType = "ID";
        if (filter.matchType.equals("TAG")) {
            this.minecraft.setScreen(new PickTagScreen(this, stack, tag -> {
                if (!filter.matchValues.contains(tag)) filter.matchValues.add(tag);
                init(minecraft, width, height);
            }));
        } else {
            String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            if (!filter.matchValues.contains(id)) filter.matchValues.add(id);
            if (filter.matchType.equals("NBT")) {
                filter.nbtSample = stack.hasTag() ? stack.getTag().copy() : null;
            }
            init(minecraft, width, height);
        }
    }

    private String cycleMatchType(String current) {
        String[] types = {"ALL", "ID", "TAG", "NBT", "SEMANTIC"};
        for (int i = 0; i < types.length; i++) if (types[i].equals(current)) return types[(i + 1) % types.length];
        return "ALL";
    }

    private String cycleMatchTypeBack(String current) {
        String[] types = {"ALL", "ID", "TAG", "NBT", "SEMANTIC"};
        for (int i = 0; i < types.length; i++) if (types[i].equals(current)) return types[(i + types.length - 1) % types.length];
        return "ALL";
    }
}
