package com.example.modmenu.client.ui.screen;
import com.example.modmenu.client.ui.screen.PickTagScreen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.ActionNetworkPacket;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.store.logistics.LogisticsRule;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.RuleTemplate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RuleConfigScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final UUID networkId;
    private final NetworkData networkData;
    private final LogisticsRule rule;
    private final boolean isNew;
    private int sourceIdx = 0;
    private int destIdx = 0;
    private EditBox amountInput;
    private EditBox filterInput;
    
    private List<ItemStack> sourceInventoryPreview = null;
    private UUID probedNodeId = null;

    public RuleConfigScreen(Screen parent, UUID networkId, NetworkData networkData, LogisticsRule rule, boolean isNew) {
        super(Component.literal("Rule Configuration"));
        this.parent = parent;
        this.networkId = networkId;
        this.networkData = networkData;
        this.rule = rule;
        this.isNew = isNew;
        if (!isNew) {
            sourceIdx = findTargetIndex(rule.sourceNodeId, rule.sourceIsGroup);
            destIdx = findTargetIndex(rule.destNodeId, rule.destIsGroup);
        }
        requestProbe();
    }

    private int findTargetIndex(UUID id, boolean isGroup) {
        if (isGroup) {
            for (int i = 0; i < networkData.groups.size(); i++) {
                if (networkData.groups.get(i).groupId.equals(id)) return networkData.nodes.size() + i;
            }
        } else {
            for (int i = 0; i < networkData.nodes.size(); i++) {
                if (networkData.nodes.get(i).nodeId.equals(id)) return i;
            }
        }
        return 0;
    }

    private void requestProbe() {
        int nodeSize = networkData.nodes.size();
        if (sourceIdx < nodeSize && !networkData.nodes.isEmpty()) {
            PacketHandler.sendToServer(ActionNetworkPacket.requestInventoryProbe(networkId, networkData.nodes.get(sourceIdx).nodeId));
        } else if (!networkData.groups.isEmpty()) {
            int groupIdx = sourceIdx - nodeSize;
            if (groupIdx >= 0 && groupIdx < networkData.groups.size()) {
                PacketHandler.sendToServer(ActionNetworkPacket.requestGroupInventoryProbe(networkId, networkData.groups.get(groupIdx).groupId));
            }
        }
    }

    public void handleSyncInventory(UUID id, List<ItemStack> inventory, List<Integer> slotX, List<Integer> slotY, net.minecraft.resources.ResourceLocation guiTexture) {
        int nodeSize = networkData.nodes.size();
        boolean matched = false;
        if (sourceIdx < nodeSize && !networkData.nodes.isEmpty()) {
            if (networkData.nodes.get(sourceIdx).nodeId.equals(id)) matched = true;
        } else {
            int groupIdx = sourceIdx - nodeSize;
            if (groupIdx >= 0 && groupIdx < networkData.groups.size()) {
                if (networkData.groups.get(groupIdx).groupId.equals(id)) matched = true;
            }
        }

        if (matched) {
            this.probedNodeId = id;
            this.sourceInventoryPreview = inventory;
        }
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        int midX = this.width / 2;
        int currentY = 35;

        // Source Node
        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 145, 20, Component.literal("Src: " + getSourceLabel()), btn -> {
            this.minecraft.setScreen(new PickTargetScreen(this, networkData, (id, isGroup) -> {
                sourceIdx = findTargetIndex(id, isGroup);
                requestProbe();
                this.init(this.minecraft, this.width, this.height);
            }));
        }));
        if (sourceIdx < networkData.nodes.size()) {
            this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Src Side: " + rule.sourceSide), btn -> {
                if (!networkData.nodes.isEmpty()) {
                    NetworkNode srcNode = networkData.nodes.get(sourceIdx);
                    this.minecraft.setScreen(new PickSideScreen(this, srcNode, side -> {
                        rule.sourceSide = side;
                        btn.setText(Component.literal("Src Side: " + rule.sourceSide));
                    }));
                }
            }));
        } else {
            rule.sourceSide = "AUTO";
        }
        currentY += 22;

        // Source Slots
        if (sourceIdx < networkData.nodes.size()) {
            String srcSlotsTxt = rule.sourceSlots.contains(-1) ? "ALL" : rule.sourceSlots.stream().map(String::valueOf).collect(Collectors.joining(","));
            if (srcSlotsTxt.length() > 30) srcSlotsTxt = srcSlotsTxt.substring(0, 27) + "...";
            this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 240, 20, Component.literal("Source Slots: " + srcSlotsTxt), btn -> {
                if (!networkData.nodes.isEmpty()) {
                    NetworkNode src = networkData.nodes.get(sourceIdx);
                    this.minecraft.setScreen(new PickSlotFromNodeScreen(this, networkId, src, rule.type, rule.sourceSlots, slots -> {
                        rule.sourceSlots = new ArrayList<>(slots);
                        this.init(this.minecraft, this.width, this.height);
                    }));
                }
            }));
            this.layoutRoot.addElement(new ResponsiveButton(midX + 95, currentY, 55, 20, Component.literal("Clear"), btn -> {
                rule.sourceSlots.clear();
                rule.sourceSlots.add(-1);
                this.init(this.minecraft, this.width, this.height);
            }));
            currentY += 25;
        } else {
            rule.sourceSlots.clear();
            rule.sourceSlots.add(-1);
            currentY += 5; // Minimal gap
        }

        // Destination Node
        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 145, 20, Component.literal("Dst: " + getDestLabel()), btn -> {
            this.minecraft.setScreen(new PickTargetScreen(this, networkData, (id, isGroup) -> {
                destIdx = findTargetIndex(id, isGroup);
                this.init(this.minecraft, this.width, this.height);
            }));
        }));
        if (destIdx < networkData.nodes.size()) {
            this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Dst Side: " + rule.destSide), btn -> {
                if (!networkData.nodes.isEmpty()) {
                    NetworkNode dstNode = networkData.nodes.get(destIdx);
                    this.minecraft.setScreen(new PickSideScreen(this, dstNode, side -> {
                        rule.destSide = side;
                        btn.setText(Component.literal("Dst Side: " + rule.destSide));
                    }));
                }
            }));
        } else {
            rule.destSide = "AUTO";
        }
        currentY += 22;

        // Destination Slots
        if (destIdx < networkData.nodes.size()) {
            String dstSlotsTxt = rule.destSlots.contains(-1) ? "ALL" : rule.destSlots.stream().map(String::valueOf).collect(Collectors.joining(","));
            if (dstSlotsTxt.length() > 30) dstSlotsTxt = dstSlotsTxt.substring(0, 27) + "...";
            this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 240, 20, Component.literal("Dest Slots: " + dstSlotsTxt), btn -> {
                if (!networkData.nodes.isEmpty()) {
                    NetworkNode dst = networkData.nodes.get(destIdx);
                    this.minecraft.setScreen(new PickSlotFromNodeScreen(this, networkId, dst, rule.type, rule.destSlots, slots -> {
                        rule.destSlots = new ArrayList<>(slots);
                        this.init(this.minecraft, this.width, this.height);
                    }));
                }
            }));
            this.layoutRoot.addElement(new ResponsiveButton(midX + 95, currentY, 55, 20, Component.literal("Clear"), btn -> {
                rule.destSlots.clear();
                rule.destSlots.add(-1);
                this.init(this.minecraft, this.width, this.height);
            }));
            currentY += 25;
        } else {
            rule.destSlots.clear();
            rule.destSlots.add(-1);
            currentY += 5; // Minimal gap
        }

        // Logistics Type
        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 300, 20, Component.literal("Logistics Type: " + rule.type), btn -> {
            rule.type = (btn.lastClickedButton == 1) ? cycleTypeBack(rule.type) : cycleType(rule.type);
            if (rule.amountPerTick == 64 || rule.amountPerTick == 1000 || rule.amountPerTick == 10000) {
                if (rule.type.equals("ENERGY")) rule.amountPerTick = 10000;
                else if (rule.type.equals("FLUIDS")) rule.amountPerTick = 1000;
                else rule.amountPerTick = 64;
            }
            this.init(this.minecraft, this.width, this.height);
        }));
        currentY += 25;

        // Mode & Amount
        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 145, 20, Component.literal("Mode: " + rule.mode), btn -> {
            rule.mode = rule.mode.equals("ROUND_ROBIN") ? "PRIORITY" : "ROUND_ROBIN";
            btn.setText(Component.literal("Mode: " + rule.mode));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Stockkeep: " + (rule.maintenanceMode ? "\u00A7aON" : "\u00A77OFF")), btn -> {
            rule.maintenanceMode = !rule.maintenanceMode;
            btn.setText(Component.literal("Stockkeep: " + (rule.maintenanceMode ? "\u00A7aON" : "\u00A77OFF")));
        }));
        currentY += 25;

        if (!rule.type.equals("ITEMS")) {
            this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 300, 20, Component.literal("Scan items for Caps: " + (rule.scanItems ? "\u00A7aON" : "\u00A77OFF")), btn -> {
                rule.scanItems = !rule.scanItems;
                btn.setText(Component.literal("Scan items for Caps: " + (rule.scanItems ? "\u00A7aON" : "\u00A77OFF")));
            }));
            currentY += 25;
        }

        if (rule.destIsGroup) {
            this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Dist: " + rule.distributionMode), btn -> {
                rule.distributionMode = (btn.lastClickedButton == 1) ? cycleDistBack(rule.distributionMode) : cycleDist(rule.distributionMode);
                btn.setText(Component.literal("Dist: " + rule.distributionMode));
            }));
            currentY += 25;
        }

        String unit = switch (rule.type) {
            case "ENERGY" -> " FE";
            case "FLUIDS" -> " mB";
            default -> "";
        };

        if (rule.amountPerTick == -1) {
            this.layoutRoot.addElement(new ResponsiveButton(rule.destIsGroup ? midX - 150 : midX + 5, currentY, 145, 20, Component.literal("Amount: MAX" + unit), btn -> {
                rule.amountPerTick = rule.type.equals("ENERGY") ? 10000 : (rule.type.equals("FLUIDS") ? 1000 : 64);
                this.init(this.minecraft, this.width, this.height);
            }));
        } else {
            this.layoutRoot.addElement(new ResponsiveButton((rule.destIsGroup ? midX - 150 : midX + 5) + 105, currentY, 40, 20, Component.literal("MAX"), btn -> {
                rule.amountPerTick = -1;
                this.init(this.minecraft, this.width, this.height);
            }));
            amountInput = new EditBox(font, rule.destIsGroup ? midX - 150 : midX + 5, currentY, 100, 20, Component.literal("Amount"));
            amountInput.setValue(String.valueOf(rule.amountPerTick));
            amountInput.setResponder(s -> {
                try { rule.amountPerTick = Integer.parseInt(s); } catch (Exception e) {}
            });
            this.addRenderableWidget(amountInput);
            this.layoutRoot.addElement(new UIElement((rule.destIsGroup ? midX - 150 : midX + 5) + 101, currentY + 5, 10, 10) {
                @Override
                public void render(GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(font, unit, getX(), getY(), 0xFFAAAAAA);
                }
            });
        }
        if (!rule.destIsGroup) currentY += 25;
        else currentY += 25; // Adjusted spacing if group mode is on

        // Speed & Priority
        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 145, 20, Component.literal("Speed: " + rule.speedMode), btn -> {
            rule.speedMode = (btn.lastClickedButton == 1) ? cycleSpeedBack(rule.speedMode) : cycleSpeed(rule.speedMode);
            btn.setText(Component.literal("Speed: " + rule.speedMode));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Priority: " + rule.priority), btn -> {
            if (btn.lastClickedButton == 1) rule.priority--; else rule.priority++;
            btn.setText(Component.literal("Priority: " + rule.priority));
        }));
        currentY += 25;

        // Status & Thresholds
        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 145, 20, Component.literal("Status: " + (rule.active ? "\u00A7aACTIVE" : "\u00A7cPAUSED")), btn -> {
            rule.active = !rule.active;
            btn.setText(Component.literal("Status: " + (rule.active ? "\u00A7aACTIVE" : "\u00A7cPAUSED")));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Conditions: " + rule.conditions.size()), btn -> {
            this.minecraft.setScreen(new LogicConditionConfigScreen(this, networkData, rule));
        }));
        currentY += 25;
        this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 145, 20, Component.literal("Min Src: " + rule.minAmount), btn -> {
            rule.minAmount = cycleThreshold(rule.minAmount, btn.lastClickedButton);
            btn.setText(Component.literal("Min Src: " + rule.minAmount));
        }));
        this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 145, 20, Component.literal("Max Dst: " + (rule.maxAmount == Integer.MAX_VALUE ? "INF" : rule.maxAmount)), btn -> {
            rule.maxAmount = cycleThresholdMax(rule.maxAmount, btn.lastClickedButton);
            btn.setText(Component.literal("Max Dst: " + (rule.maxAmount == Integer.MAX_VALUE ? "INF" : rule.maxAmount)));
        }));
        currentY += 30;

        // Filters (Items only)
        if (rule.type.equals("ITEMS")) {
            this.layoutRoot.addElement(new ResponsiveButton(midX - 150, currentY, 300, 20, Component.literal("Configure Item Filter"), btn -> {
                this.minecraft.setScreen(new FilterConfigScreen(this, rule.filter, networkId, networkData, rule.sourceNodeId, rule.sourceIsGroup));
            }));
            currentY += 25;
            
            // Match Preview
            final int previewY = currentY;
            this.layoutRoot.addElement(new UIElement(midX - 150, previewY, 300, 30) {
                @Override
                public void render(GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(font, "Match Preview:", getX(), getY(), 0xFFAAAAAA);
                    if (rule.filter.matchType.equals("ALL")) {
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
                    if (button == 1 && sourceInventoryPreview != null && !rule.filter.matchType.equals("ALL")) {
                        int dx = 0;
                        for (ItemStack stack : sourceInventoryPreview) {
                            if (!stack.isEmpty() && matchesFilterLocal(stack)) {
                                if (mx >= getX() + dx && mx < getX() + dx + 16 && my >= getY() + 12 && my < getY() + 12 + 16) {
                                    String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                                    if (rule.filter.matchType.equals("ID") || rule.filter.matchType.equals("NBT")) {
                                        rule.filter.matchValues.remove(id);
                                    } else if (rule.filter.matchType.equals("TAG")) {
                                        stack.getTags().forEach(t -> rule.filter.matchValues.remove(t.location().toString()));
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
            currentY += 40;
        } else {
            this.layoutRoot.addElement(new UIElement(midX - 150, currentY, 300, 20) {
                @Override public void render(GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(font, rule.type + " mode selected. No filters required.", getX(), getY(), 0xFFAAAAAA);
                }
            });
            currentY += 25;
        }

        this.layoutRoot.addElement(new ResponsiveButton(midX - 105, currentY, 100, 20, Component.literal("\u00A76SAVE RULE"), btn -> {
            int nodeSize = networkData.nodes.size();
            if (sourceIdx < nodeSize) {
                rule.sourceNodeId = networkData.nodes.get(sourceIdx).nodeId;
                rule.sourceIsGroup = false;
            } else {
                rule.sourceNodeId = networkData.groups.get(sourceIdx - nodeSize).groupId;
                rule.sourceIsGroup = true;
            }
            
            if (destIdx < nodeSize) {
                rule.destNodeId = networkData.nodes.get(destIdx).nodeId;
                rule.destIsGroup = false;
            } else {
                rule.destNodeId = networkData.groups.get(destIdx - nodeSize).groupId;
                rule.destIsGroup = true;
            }

            PacketHandler.sendToServer(new ActionNetworkPacket(4, networkId, rule));
            this.minecraft.setScreen(parent);
        }));
        this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 100, 20, Component.literal("\u00A7bTEST RULE"), btn -> {
            if (!isNew) PacketHandler.sendToServer(ActionNetworkPacket.testRule(networkId, rule.ruleId));
        }));
        currentY += 25;

        this.layoutRoot.addElement(new ResponsiveButton(midX - 105, currentY, 100, 20, Component.literal("\u00A7eSave Blueprint"), btn -> {
            this.minecraft.setScreen(new RenameNetworkScreen(this, networkId, "New Blueprint", name -> {
                RuleTemplate template = new RuleTemplate(name, rule);
                PacketHandler.sendToServer(ActionNetworkPacket.addTemplate(networkId, template));
                this.minecraft.setScreen(this);
            }));
        }));

        this.layoutRoot.addElement(new ResponsiveButton(midX + 5, currentY, 100, 20, Component.literal("\u00A7dLoad Blueprint"), btn -> {
            this.minecraft.setScreen(new PickTemplateScreen(this, networkData, template -> {
                LogisticsRule tr = template.rule.snapshot();
                // Apply template values to current rule
                rule.type = tr.type;
                rule.filter = tr.filter;
                rule.mode = tr.mode;
                rule.distributionMode = tr.distributionMode;
                rule.amountPerTick = tr.amountPerTick;
                rule.minAmount = tr.minAmount;
                rule.maxAmount = tr.maxAmount;
                rule.maintenanceMode = tr.maintenanceMode;
                rule.scanItems = tr.scanItems;
                rule.speedMode = tr.speedMode;
                rule.priority = tr.priority;
                rule.conditions = tr.conditions;
                this.init(this.minecraft, this.width, this.height);
            }));
        }));
    }

    private boolean matchesFilterLocal(ItemStack stack) {
        if (rule.filter.matchType.equals("ALL")) return !rule.filter.blacklist;
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        boolean match = false;
        for (String finalVal : rule.filter.matchValues) {
            if (finalVal.isEmpty()) continue;
            boolean m = false;
            switch (rule.filter.matchType) {
                case "ID" -> {
                    if (finalVal.contains("*")) {
                        try { m = itemId.matches(finalVal.replace(".", "\\.").replace("*", ".*")); } catch (Exception e) {}
                    } else m = itemId.equals(finalVal);
                }
                case "TAG" -> m = stack.getTags().anyMatch(t -> t.location().toString().equals(finalVal));
                case "NBT" -> m = itemId.equals(finalVal) && (rule.filter.nbtSample == null ? !stack.hasTag() : (stack.hasTag() && net.minecraft.nbt.NbtUtils.compareNbt(rule.filter.nbtSample, stack.getTag(), !rule.filter.fuzzyNbt)));
            }
            if (m) { match = true; break; }
        }
        return rule.filter.blacklist != match;
    }

    private String getSourceLabel() {
        int nodeSize = networkData.nodes.size();
        if (sourceIdx < nodeSize && !networkData.nodes.isEmpty()) {
            NetworkNode n = networkData.nodes.get(sourceIdx);
            return n.customName != null ? n.customName : n.nodeType;
        } else {
            int gIdx = sourceIdx - nodeSize;
            if (gIdx >= 0 && gIdx < networkData.groups.size()) {
                com.example.modmenu.store.logistics.NodeGroup g = networkData.groups.get(gIdx);
                return "[G] " + (g.name != null ? g.name : "Unnamed");
            }
        }
        return "None";
    }

    private String getDestLabel() {
        int nodeSize = networkData.nodes.size();
        if (destIdx < nodeSize && !networkData.nodes.isEmpty()) {
            NetworkNode n = networkData.nodes.get(destIdx);
            return n.customName != null ? n.customName : n.nodeType;
        } else {
            int gIdx = destIdx - nodeSize;
            if (gIdx >= 0 && gIdx < networkData.groups.size()) {
                com.example.modmenu.store.logistics.NodeGroup g = networkData.groups.get(gIdx);
                return "[G] " + (g.name != null ? g.name : "Unnamed");
            }
        }
        return "None";
    }

    private String cycleType(String current) {
        String[] types = {"ITEMS", "ENERGY", "FLUIDS"};
        for (int i = 0; i < types.length; i++) if (types[i].equals(current)) return types[(i + 1) % types.length];
        return "ITEMS";
    }

    private String cycleTypeBack(String current) {
        String[] types = {"ITEMS", "ENERGY", "FLUIDS"};
        for (int i = 0; i < types.length; i++) if (types[i].equals(current)) return types[(i + types.length - 1) % types.length];
        return "ITEMS";
    }

    private int cycleThreshold(int current, int button) {
        if (button == 1) {
            if (current <= 0) return 1024; if (current <= 8) return 0;
            if (current <= 64) return current - 8; if (current <= 256) return current - 64;
            return current - 256;
        } else {
            if (current >= 1024) return 0; if (current >= 256) return current + 256;
            if (current >= 64) return current + 64; return current + 8;
        }
    }

    private int cycleThresholdMax(int current, int button) {
        if (button == 1) {
            if (current == Integer.MAX_VALUE) return 2048; if (current <= 64) return Integer.MAX_VALUE;
            if (current <= 512) return current - 64; return current - 512;
        } else {
            if (current == Integer.MAX_VALUE) return 64; if (current >= 2048) return Integer.MAX_VALUE;
            if (current >= 512) return current + 512; return current + 64;
        }
    }

    private String cycleSpeed(String current) {
        String[] speeds = {"SLOW", "NORMAL", "FAST", "HYPER", "INSTANT"};
        for (int i = 0; i < speeds.length; i++) if (speeds[i].equals(current)) return speeds[(i + 1) % speeds.length];
        return "NORMAL";
    }

    private String cycleSpeedBack(String current) {
        String[] speeds = {"SLOW", "NORMAL", "FAST", "HYPER", "INSTANT"};
        for (int i = 0; i < speeds.length; i++) if (speeds[i].equals(current)) return speeds[(i + speeds.length - 1) % speeds.length];
        return "NORMAL";
    }

    private String cycleDist(String current) {
        String[] modes = {"BALANCED", "ROUND_ROBIN", "OVERFLOW"};
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(current)) return modes[(i + 1) % modes.length];
        return "BALANCED";
    }

    private String cycleDistBack(String current) {
        String[] modes = {"BALANCED", "ROUND_ROBIN", "OVERFLOW"};
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(current)) return modes[(i + modes.length - 1) % modes.length];
        return "BALANCED";
    }
}