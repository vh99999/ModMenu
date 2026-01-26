package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.logistics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

public class ActionNetworkPacket {
    private final int action;
    private UUID networkId;
    private UUID targetId;
    private UUID secondTargetId;
    private String stringData;
    private int intData;
    private BlockPos posData;
    private String dimData;
    private LogisticsRule ruleData;
    private NetworkNode nodeData;
    private NodeGroup groupData;
    private RuleTemplate templateData;

    public ActionNetworkPacket(int action) {
        this.action = action;
    }

    public ActionNetworkPacket(int action, UUID networkId, LogisticsRule rule) {
        this.action = action;
        this.networkId = networkId;
        this.ruleData = rule;
    }

    public ActionNetworkPacket(int action, UUID networkId, NetworkNode node) {
        this.action = action;
        this.networkId = networkId;
        this.nodeData = node;
    }

    public ActionNetworkPacket(int action, UUID networkId) {
        this.action = action;
        this.networkId = networkId;
    }

    public static ActionNetworkPacket createNetwork(String name) {
        ActionNetworkPacket p = new ActionNetworkPacket(0);
        p.stringData = name;
        return p;
    }

    public static ActionNetworkPacket deleteNetwork(UUID networkId) {
        ActionNetworkPacket p = new ActionNetworkPacket(1);
        p.networkId = networkId;
        return p;
    }

    public static ActionNetworkPacket renameNetwork(UUID networkId, String name) {
        ActionNetworkPacket p = new ActionNetworkPacket(12, networkId);
        p.stringData = name;
        return p;
    }

    public static ActionNetworkPacket addNode(UUID networkId, BlockPos pos, String dimension) {
        ActionNetworkPacket p = new ActionNetworkPacket(2);
        p.networkId = networkId;
        p.posData = pos;
        p.dimData = dimension;
        return p;
    }

    public static ActionNetworkPacket addVirtualNode(UUID networkId, String type, int index) {
        ActionNetworkPacket p = new ActionNetworkPacket(9);
        p.networkId = networkId;
        p.stringData = type;
        p.intData = index;
        return p;
    }

    public static ActionNetworkPacket removeNode(UUID networkId, UUID nodeId) {
        ActionNetworkPacket p = new ActionNetworkPacket(3);
        p.networkId = networkId;
        p.targetId = nodeId;
        return p;
    }

    public static ActionNetworkPacket removeRule(UUID networkId, UUID ruleId) {
        ActionNetworkPacket p = new ActionNetworkPacket(5);
        p.networkId = networkId;
        p.targetId = ruleId;
        return p;
    }

    public static ActionNetworkPacket requestInventoryProbe(UUID networkId, UUID nodeId) {
        ActionNetworkPacket p = new ActionNetworkPacket(13);
        p.networkId = networkId;
        p.targetId = nodeId;
        return p;
    }

    public static ActionNetworkPacket requestGroupInventoryProbe(UUID networkId, UUID groupId) {
        ActionNetworkPacket p = new ActionNetworkPacket(21);
        p.networkId = networkId;
        p.targetId = groupId;
        return p;
    }

    public static ActionNetworkPacket testRule(UUID networkId, UUID ruleId) {
        ActionNetworkPacket p = new ActionNetworkPacket(14);
        p.networkId = networkId;
        p.targetId = ruleId;
        return p;
    }

    public static ActionNetworkPacket openNodeGui(UUID networkId, UUID nodeId) {
        ActionNetworkPacket p = new ActionNetworkPacket(15, networkId);
        p.targetId = nodeId;
        return p;
    }

    public static ActionNetworkPacket addGroup(UUID networkId, NodeGroup group) {
        ActionNetworkPacket p = new ActionNetworkPacket(19, networkId);
        p.groupData = group;
        return p;
    }

    public static ActionNetworkPacket removeGroup(UUID networkId, UUID groupId) {
        ActionNetworkPacket p = new ActionNetworkPacket(20, networkId);
        p.targetId = groupId;
        return p;
    }

    public static ActionNetworkPacket addTemplate(UUID networkId, RuleTemplate template) {
        ActionNetworkPacket p = new ActionNetworkPacket(22, networkId);
        p.templateData = template;
        return p;
    }

    public static ActionNetworkPacket removeTemplate(UUID networkId, UUID templateId) {
        ActionNetworkPacket p = new ActionNetworkPacket(23, networkId);
        p.targetId = templateId;
        return p;
    }

    public static ActionNetworkPacket applyTemplate(UUID networkId, UUID targetId, boolean isGroup, UUID templateId) {
        ActionNetworkPacket p = new ActionNetworkPacket(24, networkId);
        p.targetId = targetId;
        p.intData = isGroup ? 1 : 0;
        p.secondTargetId = templateId;
        return p;
    }

    public static ActionNetworkPacket pasteNodeConfig(UUID networkId, UUID targetId, NetworkNode config) {
        ActionNetworkPacket p = new ActionNetworkPacket(25, networkId);
        p.targetId = targetId;
        p.nodeData = config;
        return p;
    }

    public static ActionNetworkPacket setOverflowTarget(UUID networkId, UUID targetId, boolean isGroup) {
        ActionNetworkPacket p = new ActionNetworkPacket(26, networkId);
        p.targetId = targetId;
        p.intData = isGroup ? 1 : 0;
        return p;
    }

    public static ActionNetworkPacket setViewedNetwork(UUID networkId) {
        ActionNetworkPacket p = new ActionNetworkPacket(27, networkId);
        return p;
    }

    public ActionNetworkPacket(FriendlyByteBuf buf) {
        this.action = buf.readInt();
        if (buf.readBoolean()) this.networkId = buf.readUUID();
        if (buf.readBoolean()) this.targetId = buf.readUUID();
        if (buf.readBoolean()) this.secondTargetId = buf.readUUID();
        if (buf.readBoolean()) this.stringData = buf.readUtf();
        if (buf.readBoolean()) this.intData = buf.readInt();
        if (buf.readBoolean()) this.posData = buf.readBlockPos();
        if (buf.readBoolean()) this.dimData = buf.readUtf();
        if (buf.readBoolean()) {
            this.ruleData = readRule(buf);
        }
        if (buf.readBoolean()) {
            this.nodeData = new NetworkNode();
            nodeData.nodeId = buf.readUUID();
            nodeData.nodeType = buf.readUtf();
            nodeData.chamberIndex = buf.readInt();
            if (buf.readBoolean()) nodeData.pos = buf.readBlockPos();
            if (buf.readBoolean()) nodeData.dimension = buf.readUtf();
            if (buf.readBoolean()) nodeData.blockId = buf.readUtf();
            if (buf.readBoolean()) nodeData.iconItemId = buf.readUtf();
            if (buf.readBoolean()) nodeData.customName = buf.readUtf();
            nodeData.guiX = buf.readInt();
            nodeData.guiY = buf.readInt();
            int sideSize = buf.readInt();
            for (int i = 0; i < sideSize; i++) {
                nodeData.sideConfig.put(buf.readEnum(Direction.class), buf.readUtf());
            }
            int slotSize = buf.readInt();
            for (int i = 0; i < slotSize; i++) {
                nodeData.slotConfig.put(buf.readInt(), buf.readUtf());
            }
            nodeData.isMissing = buf.readBoolean();
        }
        if (buf.readBoolean()) {
            this.groupData = new NodeGroup();
            groupData.groupId = buf.readUUID();
            groupData.name = buf.readUtf();
            groupData.guiX = buf.readInt();
            groupData.guiY = buf.readInt();
            groupData.expanded = buf.readBoolean();
            int members = buf.readInt();
            for (int i = 0; i < members; i++) groupData.nodeIds.add(buf.readUUID());
        }
        if (buf.readBoolean()) {
            this.templateData = new RuleTemplate();
            templateData.templateId = buf.readUUID();
            templateData.name = buf.readUtf();
            templateData.rule = readRule(buf);
        }
    }

    private LogisticsRule readRule(FriendlyByteBuf buf) {
        LogisticsRule rule = new LogisticsRule();
        rule.ruleId = buf.readUUID();
        rule.sourceNodeId = buf.readUUID();
        rule.sourceIsGroup = buf.readBoolean();
        rule.destNodeId = buf.readUUID();
        rule.destIsGroup = buf.readBoolean();
        rule.sourceSide = buf.readUtf();
        rule.destSide = buf.readUtf();
        rule.mode = buf.readUtf();
        rule.distributionMode = buf.readUtf();
        rule.amountPerTick = buf.readInt();
        rule.minAmount = buf.readInt();
        rule.maxAmount = buf.readInt();
        rule.priority = buf.readInt();
        rule.speedMode = buf.readUtf();
        rule.type = buf.readUtf();
        int srcSlotSize = buf.readInt();
        rule.sourceSlots = new java.util.ArrayList<>(srcSlotSize);
        for (int i = 0; i < srcSlotSize; i++) rule.sourceSlots.add(buf.readInt());
        int dstSlotSize = buf.readInt();
        rule.destSlots = new java.util.ArrayList<>(dstSlotSize);
        for (int i = 0; i < dstSlotSize; i++) rule.destSlots.add(buf.readInt());
        rule.active = buf.readBoolean();
        rule.maintenanceMode = buf.readBoolean();
        rule.scanItems = buf.readBoolean();
        int condSize = buf.readInt();
        rule.conditions = new java.util.ArrayList<>(condSize);
        for (int i = 0; i < condSize; i++) {
            LogicCondition cond = new LogicCondition();
            if (buf.readBoolean()) cond.targetId = buf.readUUID();
            cond.isGroup = buf.readBoolean();
            cond.type = buf.readUtf();
            cond.operator = buf.readUtf();
            cond.value = buf.readInt();
            cond.filter = new LogisticsFilter();
            cond.filter.matchType = buf.readUtf();
            int vSize = buf.readInt();
            cond.filter.matchValues = new java.util.ArrayList<>(vSize);
            for (int j = 0; j < vSize; j++) cond.filter.matchValues.add(buf.readUtf());
            if (buf.readBoolean()) cond.filter.nbtSample = buf.readNbt();
            cond.filter.blacklist = buf.readBoolean();
            cond.filter.fuzzyNbt = buf.readBoolean();
            rule.conditions.add(cond);
        }
        rule.filter = new LogisticsFilter();
        rule.filter.matchType = buf.readUtf();
        int valSize = buf.readInt();
        rule.filter.matchValues = new java.util.ArrayList<>(valSize);
        for (int i = 0; i < valSize; i++) rule.filter.matchValues.add(buf.readUtf());
        if (buf.readBoolean()) rule.filter.nbtSample = buf.readNbt();
        rule.filter.blacklist = buf.readBoolean();
        rule.filter.fuzzyNbt = buf.readBoolean();
        return rule;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(action);
        buf.writeBoolean(networkId != null);
        if (networkId != null) buf.writeUUID(networkId);
        buf.writeBoolean(targetId != null);
        if (targetId != null) buf.writeUUID(targetId);
        buf.writeBoolean(secondTargetId != null);
        if (secondTargetId != null) buf.writeUUID(secondTargetId);
        buf.writeBoolean(stringData != null);
        if (stringData != null) buf.writeUtf(stringData);
        buf.writeBoolean(true);
        buf.writeInt(intData);
        buf.writeBoolean(posData != null);
        if (posData != null) buf.writeBlockPos(posData);
        buf.writeBoolean(dimData != null);
        if (dimData != null) buf.writeUtf(dimData);
        buf.writeBoolean(ruleData != null);
        if (ruleData != null) {
            writeRule(buf, ruleData);
        }
        buf.writeBoolean(nodeData != null);
        if (nodeData != null) {
            buf.writeUUID(nodeData.nodeId);
            buf.writeUtf(nodeData.nodeType);
            buf.writeInt(nodeData.chamberIndex);
            buf.writeBoolean(nodeData.pos != null);
            if (nodeData.pos != null) buf.writeBlockPos(nodeData.pos);
            buf.writeBoolean(nodeData.dimension != null);
            if (nodeData.dimension != null) buf.writeUtf(nodeData.dimension);
            buf.writeBoolean(nodeData.blockId != null);
            if (nodeData.blockId != null) buf.writeUtf(nodeData.blockId);
            buf.writeBoolean(nodeData.iconItemId != null);
            if (nodeData.iconItemId != null) buf.writeUtf(nodeData.iconItemId);
            buf.writeBoolean(nodeData.customName != null);
            if (nodeData.customName != null) buf.writeUtf(nodeData.customName);
            buf.writeInt(nodeData.guiX);
            buf.writeInt(nodeData.guiY);
            buf.writeInt(nodeData.sideConfig.size());
            for (java.util.Map.Entry<Direction, String> entry : nodeData.sideConfig.entrySet()) {
                buf.writeEnum(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
            buf.writeInt(nodeData.slotConfig.size());
            for (java.util.Map.Entry<Integer, String> entry : nodeData.slotConfig.entrySet()) {
                buf.writeInt(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
            buf.writeBoolean(nodeData.isMissing);
        }
        buf.writeBoolean(groupData != null);
        if (groupData != null) {
            buf.writeUUID(groupData.groupId);
            buf.writeUtf(groupData.name != null ? groupData.name : "");
            buf.writeInt(groupData.guiX);
            buf.writeInt(groupData.guiY);
            buf.writeBoolean(groupData.expanded);
            buf.writeInt(groupData.nodeIds.size());
            for (UUID id : groupData.nodeIds) buf.writeUUID(id);
        }
        buf.writeBoolean(templateData != null);
        if (templateData != null) {
            buf.writeUUID(templateData.templateId);
            buf.writeUtf(templateData.name);
            writeRule(buf, templateData.rule);
        }
    }

    private void writeRule(FriendlyByteBuf buf, LogisticsRule rule) {
        buf.writeUUID(rule.ruleId);
        buf.writeUUID(rule.sourceNodeId != null ? rule.sourceNodeId : UUID.randomUUID());
        buf.writeBoolean(rule.sourceIsGroup);
        buf.writeUUID(rule.destNodeId != null ? rule.destNodeId : UUID.randomUUID());
        buf.writeBoolean(rule.destIsGroup);
        buf.writeUtf(rule.sourceSide);
        buf.writeUtf(rule.destSide);
        buf.writeUtf(rule.mode);
        buf.writeUtf(rule.distributionMode);
        buf.writeInt(rule.amountPerTick);
        buf.writeInt(rule.minAmount);
        buf.writeInt(rule.maxAmount);
        buf.writeInt(rule.priority);
        buf.writeUtf(rule.speedMode);
        buf.writeUtf(rule.type);
        buf.writeInt(rule.sourceSlots.size());
        for (int s : rule.sourceSlots) buf.writeInt(s);
        buf.writeInt(rule.destSlots.size());
        for (int s : rule.destSlots) buf.writeInt(s);
        buf.writeBoolean(rule.active);
        buf.writeBoolean(rule.maintenanceMode);
        buf.writeBoolean(rule.scanItems);
        buf.writeInt(rule.conditions.size());
        for (LogicCondition cond : rule.conditions) {
            buf.writeBoolean(cond.targetId != null);
            if (cond.targetId != null) buf.writeUUID(cond.targetId);
            buf.writeBoolean(cond.isGroup);
            buf.writeUtf(cond.type);
            buf.writeUtf(cond.operator);
            buf.writeInt(cond.value);
            buf.writeUtf(cond.filter.matchType);
            buf.writeInt(cond.filter.matchValues.size());
            for (String s : cond.filter.matchValues) buf.writeUtf(s);
            buf.writeBoolean(cond.filter.nbtSample != null);
            if (cond.filter.nbtSample != null) buf.writeNbt(cond.filter.nbtSample);
            buf.writeBoolean(cond.filter.blacklist);
            buf.writeBoolean(cond.filter.fuzzyNbt);
        }
        buf.writeUtf(rule.filter.matchType);
        buf.writeInt(rule.filter.matchValues.size());
        for (String s : rule.filter.matchValues) buf.writeUtf(s);
        buf.writeBoolean(rule.filter.nbtSample != null);
        if (rule.filter.nbtSample != null) buf.writeNbt(rule.filter.nbtSample);
        buf.writeBoolean(rule.filter.blacklist);
        buf.writeBoolean(rule.filter.fuzzyNbt);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                LogisticsCapability.getNetworks(player).ifPresent(data -> {
                    switch (action) {
                        case 0 -> {
                            NetworkData net = new NetworkData();
                            net.networkName = cleanName(stringData);
                            data.getNetworks().add(net);
                        }
                        case 1 -> data.getNetworks().removeIf(n -> n.networkId.equals(networkId));
                        case 2 -> {
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    // Duplicate Check
                                    if (network.nodes.stream().anyMatch(n -> posData.equals(n.pos))) {
                                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cBlock already in network!"), true);
                                        return;
                                    }

                                    NetworkNode node = new NetworkNode();
                                    node.pos = posData;
                                    node.dimension = dimData;
                                    node.nodeType = "BLOCK";
                                    node.guiX = (network.nodes.size() % 5) * 40 - 80;
                                    node.guiY = (network.nodes.size() / 5) * 40 - 80;
                                    
                                    // Capture Block Identity
                                    net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(posData);
                                    node.blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
                                    node.customName = cleanName(state.getBlock().getName().getString()) + " [" + posData.getX() + "," + posData.getY() + "," + posData.getZ() + "]";
                                    
                                    network.nodes.add(node);
                                    network.nodeMap = null; // Force rebuild
                                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7aAdded " + node.customName + " to network!"), true);
                                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                                    break;
                                }
                            }
                        }
                        case 3 -> {
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.nodes.removeIf(node -> node.nodeId.equals(targetId));
                                    network.rules.removeIf(rule -> rule.sourceNodeId.equals(targetId) || rule.destNodeId.equals(targetId));
                                    network.nodeMap = null;
                                    network.needsSorting = true;
                                    break;
                                }
                            }
                        }
                        case 4 -> { // Add/Update Rule
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    boolean updated = false;
                                    for (int i = 0; i < network.rules.size(); i++) {
                                        if (network.rules.get(i).ruleId.equals(ruleData.ruleId)) {
                                            network.rules.set(i, ruleData);
                                            updated = true;
                                            break;
                                        }
                                    }
                                    if (!updated) network.rules.add(ruleData);
                                    network.needsSorting = true;
                                    break;
                                }
                            }
                        }
                        case 5 -> { // Remove Rule
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.rules.removeIf(rule -> rule.ruleId.equals(targetId));
                                    network.needsSorting = true;
                                    break;
                                }
                            }
                        }
                        case 7 -> { // Toggle Network Active
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.active = !network.active;
                                    break;
                                }
                            }
                        }
                        case 9 -> {
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    NetworkNode node = new NetworkNode();
                                    node.nodeType = stringData;
                                    node.chamberIndex = intData;
                                    node.guiX = (network.nodes.size() % 5) * 40 - 80;
                                    node.guiY = (network.nodes.size() / 5) * 40 - 80;
                                    
                                    if (node.nodeType.equals("PLAYER")) node.customName = "Player Inventory";
                                    else if (node.nodeType.equals("MARKET")) node.customName = "Market (Auto-Sell)";
                                    else if (node.nodeType.equals("CHAMBER")) {
                                        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
                                        if (intData >= 0 && intData < skillData.chambers.size()) {
                                            StorePriceManager.ChamberData chamber = skillData.chambers.get(intData);
                                            node.customName = "Chamber: " + cleanName(chamber.customName != null ? chamber.customName : chamber.mobId);
                                        }
                                    }
                                    
                                    network.nodes.add(node);
                                    network.nodeMap = null;
                                    break;
                                }
                            }
                        }
                        case 11 -> { // Update Node
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    for (int i = 0; i < network.nodes.size(); i++) {
                                        if (network.nodes.get(i).nodeId.equals(nodeData.nodeId)) {
                                            network.nodes.set(i, nodeData);
                                            network.nodeMap = null;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        case 12 -> { // Rename Network
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.networkName = cleanName(stringData);
                                    break;
                                }
                            }
                        }
                        case 13 -> { // Request Node Inventory Probe
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    for (NetworkNode node : network.nodes) {
                                        if (node.nodeId.equals(targetId)) {
                                            probeAndSyncInventory(player, node);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        case 14 -> { // Test Rule
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    for (LogisticsRule rule : network.rules) {
                                        if (rule.ruleId.equals(targetId)) {
                                            boolean moved = NetworkTickHandler.processRule(player, network, rule, true);
                                            float pitch = moved ? 1.0f : 0.5f;
                                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, pitch);
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        case 15 -> { // Open Machine GUI
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    for (NetworkNode node : network.nodes) {
                                        if (node.nodeId.equals(targetId)) {
                                            if (node.nodeType.equals("BLOCK") && node.pos != null) {
                                                net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(node.pos);
                                                if (be instanceof net.minecraft.world.MenuProvider mp) {
                                                    NetworkHooks.openScreen(player, mp, node.pos);
                                                }
                                            }
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        case 16 -> { // Toggle Connections Visibility
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.showConnections = !network.showConnections;
                                    break;
                                }
                            }
                        }
                        case 18 -> { // Set Link Mode
                            data.linkingNetworkId = networkId;
                        }
                        case 17 -> { // Toggle Simulation
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.simulationActive = !network.simulationActive;
                                    break;
                                }
                            }
                        }
                        case 19 -> { // Add/Update Group
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    boolean updated = false;
                                    for (int i = 0; i < network.groups.size(); i++) {
                                        if (network.groups.get(i).groupId.equals(groupData.groupId)) {
                                            network.groups.set(i, groupData);
                                            updated = true;
                                            break;
                                        }
                                    }
                                    if (!updated) network.groups.add(groupData);
                                    network.groupMap = null;
                                    break;
                                }
                            }
                        }
                        case 20 -> { // Remove Group
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.groups.removeIf(group -> group.groupId.equals(targetId));
                                    network.rules.removeIf(rule -> (rule.sourceIsGroup && rule.sourceNodeId.equals(targetId)) ||
                                                                  (rule.destIsGroup && rule.destNodeId.equals(targetId)));
                                    network.groupMap = null;
                                    network.needsSorting = true;
                                    break;
                                }
                            }
                        }
                        case 21 -> { // Request Group Inventory Probe
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    for (NodeGroup group : network.groups) {
                                        if (group.groupId.equals(targetId)) {
                                            probeAndSyncGroupInventory(player, network, group);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        case 22 -> { // Add/Update Template
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    boolean updated = false;
                                    for (int i = 0; i < network.ruleTemplates.size(); i++) {
                                        if (network.ruleTemplates.get(i).templateId.equals(templateData.templateId)) {
                                            network.ruleTemplates.set(i, templateData);
                                            updated = true;
                                            break;
                                        }
                                    }
                                    if (!updated) network.ruleTemplates.add(templateData);
                                    break;
                                }
                            }
                        }
                        case 23 -> { // Remove Template
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.ruleTemplates.removeIf(t -> t.templateId.equals(targetId));
                                    break;
                                }
                            }
                        }
                        case 24 -> { // Apply Template
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    RuleTemplate template = network.ruleTemplates.stream()
                                            .filter(t -> t.templateId.equals(secondTargetId))
                                            .findFirst().orElse(null);
                                    if (template != null) {
                                        LogisticsRule newRule = template.rule.snapshot();
                                        newRule.ruleId = UUID.randomUUID();
                                        
                                        // Intelligent placement: 
                                        // If source is permanent (PLAYER/MARKET), target becomes destination.
                                        // Otherwise target becomes source.
                                        boolean sourceIsPerm = isPermanent(network, newRule.sourceNodeId, newRule.sourceIsGroup);
                                        boolean destIsPerm = isPermanent(network, newRule.destNodeId, newRule.destIsGroup);
                                        
                                        if (sourceIsPerm && !destIsPerm) {
                                            newRule.destNodeId = targetId;
                                            newRule.destIsGroup = intData == 1;
                                        } else {
                                            newRule.sourceNodeId = targetId;
                                            newRule.sourceIsGroup = intData == 1;
                                        }
                                        
                                        network.rules.add(newRule);
                                        network.needsSorting = true;
                                    }
                                    break;
                                }
                            }
                        }
                        case 25 -> { // Paste Node Config
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    for (NetworkNode node : network.nodes) {
                                        if (node.nodeId.equals(targetId)) {
                                            node.iconItemId = nodeData.iconItemId;
                                            node.sideConfig = new HashMap<>(nodeData.sideConfig);
                                            node.slotConfig = new HashMap<>(nodeData.slotConfig);
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        case 26 -> { // Set Overflow Target
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.overflowTargetId = targetId;
                                    network.overflowIsGroup = intData == 1;
                                    break;
                                }
                            }
                        }
                        case 27 -> { // Set Viewed Network
                            data.viewedNetworkId = networkId;
                        }
                    }
                    // Sync back
                    PacketHandler.sendToPlayer(new SyncNetworksPacket(data.getNetworks(), data.viewedNetworkId), player);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void probeAndSyncGroupInventory(ServerPlayer player, NetworkData network, NodeGroup group) {
        Map<String, ItemStack> combined = new HashMap<>();
        for (UUID id : group.nodeIds) {
            NetworkNode node = network.nodes.stream().filter(n -> n.nodeId.equals(id)).findFirst().orElse(null);
            if (node == null) continue;
            
            // Re-using the logic from probeAndSyncInventory but accumulating
            List<ItemStack> items = getItemsFromNode(player, node);
            for (ItemStack stack : items) {
                if (stack.isEmpty()) continue;
                String key = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                if (stack.hasTag()) key += stack.getTag().toString();
                
                if (combined.containsKey(key)) {
                    combined.get(key).grow(stack.getCount());
                } else {
                    combined.put(key, stack.copy());
                }
            }
        }
        
        List<ItemStack> finalItems = new ArrayList<>(combined.values());
        List<Integer> slotX = new ArrayList<>();
        List<Integer> slotY = new ArrayList<>();
        for (int i = 0; i < finalItems.size(); i++) {
            slotX.add((i % 9) * 18);
            slotY.add((i / 9) * 18);
        }
        
        PacketHandler.sendToPlayer(new SyncNodeInventoryPacket(group.groupId, finalItems, slotX, slotY, null), player);
    }

    private List<ItemStack> getItemsFromNode(ServerPlayer player, NetworkNode node) {
        List<ItemStack> items = new ArrayList<>();
        if (node.nodeType.equals("BLOCK")) {
            if (node.pos != null && node.dimension != null) {
                ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
                if (dimLoc != null) {
                    ServerLevel level = player.serverLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
                    if (level != null && level.hasChunkAt(node.pos)) {
                        BlockEntity be = level.getBlockEntity(node.pos);
                        if (be != null) {
                            be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                                for (int i = 0; i < handler.getSlots(); i++) {
                                    ItemStack s = handler.getStackInSlot(i);
                                    if (!s.isEmpty()) items.add(s.copy());
                                }
                            });
                        }
                    }
                }
            }
        } else if (node.nodeType.equals("PLAYER")) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty()) items.add(s.copy());
            }
        } else if (node.nodeType.equals("CHAMBER")) {
            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
            if (node.chamberIndex >= 0 && node.chamberIndex < skillData.chambers.size()) {
                StorePriceManager.ChamberData chamber = skillData.chambers.get(node.chamberIndex);
                for (ItemStack s : chamber.storedLoot) {
                    if (!s.isEmpty()) items.add(s.copy());
                }
            }
        }
        return items;
    }

    private void probeAndSyncInventory(ServerPlayer player, NetworkNode node) {
        java.util.List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
        java.util.List<Integer> slotX = new java.util.ArrayList<>();
        java.util.List<Integer> slotY = new java.util.ArrayList<>();
        net.minecraft.resources.ResourceLocation guiTexture = null;
        
        if (node.nodeType.equals("BLOCK")) {
            if (node.pos != null && node.dimension != null) {
                net.minecraft.resources.ResourceLocation dimLoc = net.minecraft.resources.ResourceLocation.tryParse(node.dimension);
                if (dimLoc != null) {
                    net.minecraft.server.level.ServerLevel level = player.serverLevel().getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc));
                    if (level != null && level.hasChunkAt(node.pos)) {
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(node.pos);
                        if (be != null) {
                            // Try to guess texture based on BlockEntity type
                            if (be instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity) guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/furnace.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity) guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/blast_furnace.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity) guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/smoker.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity) guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/hopper.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity) guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/dispenser.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/brewing_stand.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/generic_54.png");

                            // Try to get real slot positions from MenuProvider
                            if (be instanceof net.minecraft.world.MenuProvider mp) {
                                try {
                                    net.minecraft.world.inventory.AbstractContainerMenu menu = mp.createMenu(0, player.getInventory(), player);
                                    if (menu != null) {
                                        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
                                            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) {
                                                items.add(slot.getItem().copy());
                                                slotX.add(slot.x);
                                                slotY.add(slot.y);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    items.clear(); slotX.clear(); slotY.clear();
                                }
                            }

                            // Fallback to capability if items still empty or not a MenuProvider
                            if (items.isEmpty()) {
                                be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                                    for (int i = 0; i < handler.getSlots(); i++) {
                                        items.add(handler.getStackInSlot(i).copy());
                                        slotX.add((i % 9) * 18);
                                        slotY.add((i / 9) * 18);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        } else if (node.nodeType.equals("PLAYER")) {
            guiTexture = net.minecraft.resources.ResourceLocation.tryParse("textures/gui/container/inventory.png");
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                items.add(player.getInventory().getItem(i).copy());
                int x, y;
                if (i >= 0 && i <= 8) { // Hotbar
                    x = 8 + i * 18;
                    y = 142;
                } else if (i >= 9 && i <= 35) { // Main Inventory
                    x = 8 + (i - 9) % 9 * 18;
                    y = 84 + (i - 9) / 9 * 18;
                } else if (i >= 36 && i <= 39) { // Armor
                    x = 8;
                    y = 62 - (i - 36) * 18;
                } else if (i == 40) { // Offhand
                    x = 77;
                    y = 62;
                } else {
                    x = (i % 9) * 18 + 8;
                    y = (i / 9) * 18 + 8;
                }
                slotX.add(x);
                slotY.add(y);
            }
        } else if (node.nodeType.equals("CHAMBER")) {
            com.example.modmenu.store.StorePriceManager.SkillData skillData = com.example.modmenu.store.StorePriceManager.getSkills(player.getUUID());
            if (node.chamberIndex >= 0 && node.chamberIndex < skillData.chambers.size()) {
                com.example.modmenu.store.StorePriceManager.ChamberData chamber = skillData.chambers.get(node.chamberIndex);
                for (int i = 0; i < chamber.storedLoot.size(); i++) {
                    items.add(chamber.storedLoot.get(i).copy());
                    slotX.add((i % 9) * 18);
                    slotY.add((i / 9) * 18);
                }
            }
        }

        PacketHandler.sendToPlayer(new SyncNodeInventoryPacket(node.nodeId, items, slotX, slotY, guiTexture), player);
    }

    private static NetworkNode findNode(NetworkData network, UUID nodeId) {
        for (NetworkNode node : network.nodes) {
            if (node.nodeId.equals(nodeId)) return node;
        }
        return null;
    }

    private static boolean isPermanent(NetworkData network, UUID id, boolean isGroup) {
        if (id == null) return false;
        if (isGroup) return false;
        NetworkNode node = findNode(network, id);
        return node != null && !node.nodeType.equals("BLOCK");
    }

    private static String cleanName(String name) {
        if (name == null) return "Unknown";
        String cleaned = net.minecraft.util.StringUtil.stripColor(name);
        // Remove ALL formatting codes
        cleaned = cleaned.replaceAll("\u00A7.", "");
        cleaned = cleaned.replaceAll("\u00A7.", "");
        // Remove common placeholders and garbage
        cleaned = cleaned.replaceAll("(?i)%s", "");
        cleaned = cleaned.replaceAll("(?i)XsXs", "");
        // Remove common modded garbage prefixes like "64x " or "x "
        cleaned = cleaned.replaceAll("(?i)^\\d+x\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^[xX]?[0-9]*[xX ]+", "");
        // Final trim and cleanup of leading/trailing non-alphanumeric (except brackets)
        cleaned = cleaned.replaceAll("^[^\\w\\s\\(\\)\\[\\]\\{\\}]+", "");
        cleaned = cleaned.replaceAll("[^\\w\\s\\(\\)\\[\\]\\{\\}]+$", "");
        return cleaned.trim().isEmpty() ? "Block" : cleaned.trim();
    }
}

