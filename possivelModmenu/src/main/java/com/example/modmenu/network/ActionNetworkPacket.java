package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.StoreSecurity;
import com.example.modmenu.store.logistics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

public class ActionNetworkPacket {
    public static final int MAX_NODES_PER_NETWORK = 1000;
    public static final int MAX_RULES_PER_NETWORK = 500;
    private static final Map<UUID, Long> testRuleCooldowns = new HashMap<>();
    
    private final ActionType actionType;
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

    public ActionNetworkPacket(ActionType actionType) {
        this.actionType = actionType;
    }

    public ActionNetworkPacket(ActionType actionType, UUID networkId, LogisticsRule rule) {
        this.actionType = actionType;
        this.networkId = networkId;
        this.ruleData = rule;
    }

    public ActionNetworkPacket(ActionType actionType, UUID networkId, NetworkNode node) {
        this.actionType = actionType;
        this.networkId = networkId;
        this.nodeData = node;
    }

    public ActionNetworkPacket(ActionType actionType, UUID networkId) {
        this.actionType = actionType;
        this.networkId = networkId;
    }

    public static ActionNetworkPacket createNetwork(String name) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.CREATE_NETWORK);
        p.stringData = name;
        return p;
    }

    public static ActionNetworkPacket deleteNetwork(UUID networkId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.DELETE_NETWORK);
        p.networkId = networkId;
        return p;
    }

    public static ActionNetworkPacket renameNetwork(UUID networkId, String name) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.RENAME_NETWORK, networkId);
        p.stringData = name;
        return p;
    }

    public static ActionNetworkPacket addNode(UUID networkId, BlockPos pos, String dimension) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.ADD_PHYSICAL_NODE);
        p.networkId = networkId;
        p.posData = pos;
        p.dimData = dimension;
        p.intData = 0;
        return p;
    }

    public static ActionNetworkPacket addBulkNodes(UUID networkId, BlockPos pos, String dimension) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.ADD_PHYSICAL_NODE);
        p.networkId = networkId;
        p.posData = pos;
        p.dimData = dimension;
        p.intData = 1;
        return p;
    }

    public static ActionNetworkPacket addVirtualNode(UUID networkId, String type, int index) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.ADD_VIRTUAL_NODE);
        p.networkId = networkId;
        p.stringData = type;
        p.intData = index;
        return p;
    }

    public static ActionNetworkPacket removeNode(UUID networkId, UUID nodeId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.REMOVE_NODE);
        p.networkId = networkId;
        p.targetId = nodeId;
        return p;
    }

    public static ActionNetworkPacket removeRule(UUID networkId, UUID ruleId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.REMOVE_RULE);
        p.networkId = networkId;
        p.targetId = ruleId;
        return p;
    }

    public static ActionNetworkPacket requestInventoryProbe(UUID networkId, UUID nodeId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.REQUEST_NODE_INVENTORY_PROBE);
        p.networkId = networkId;
        p.targetId = nodeId;
        return p;
    }

    public static ActionNetworkPacket requestGroupInventoryProbe(UUID networkId, UUID groupId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.REQUEST_GROUP_INVENTORY_PROBE);
        p.networkId = networkId;
        p.targetId = groupId;
        return p;
    }

    public static ActionNetworkPacket testRule(UUID networkId, UUID ruleId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.TEST_RULE);
        p.networkId = networkId;
        p.targetId = ruleId;
        return p;
    }

    public static ActionNetworkPacket openNodeGui(UUID networkId, UUID nodeId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.OPEN_MACHINE_GUI, networkId);
        p.targetId = nodeId;
        return p;
    }

    public static ActionNetworkPacket addGroup(UUID networkId, NodeGroup group) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.ADD_UPDATE_GROUP, networkId);
        p.groupData = group;
        return p;
    }

    public static ActionNetworkPacket removeGroup(UUID networkId, UUID groupId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.REMOVE_GROUP, networkId);
        p.targetId = groupId;
        return p;
    }

    public static ActionNetworkPacket addTemplate(UUID networkId, RuleTemplate template) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.ADD_UPDATE_TEMPLATE, networkId);
        p.templateData = template;
        return p;
    }

    public static ActionNetworkPacket removeTemplate(UUID networkId, UUID templateId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.REMOVE_TEMPLATE, networkId);
        p.targetId = templateId;
        return p;
    }

    public static ActionNetworkPacket applyTemplate(UUID networkId, UUID targetId, boolean isGroup, UUID templateId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.APPLY_TEMPLATE, networkId);
        p.targetId = targetId;
        p.intData = isGroup ? 1 : 0;
        p.secondTargetId = templateId;
        return p;
    }

    public static ActionNetworkPacket pasteNodeConfig(UUID networkId, UUID targetId, NetworkNode config) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.PASTE_NODE_CONFIG, networkId);
        p.targetId = targetId;
        p.nodeData = config;
        return p;
    }

    public static ActionNetworkPacket setOverflowTarget(UUID networkId, UUID targetId, boolean isGroup) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.SET_OVERFLOW_TARGET, networkId);
        p.targetId = targetId;
        p.intData = isGroup ? 1 : 0;
        return p;
    }

    public static ActionNetworkPacket setViewedNetwork(UUID networkId) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.SET_VIEWED_NETWORK, networkId);
        return p;
    }

    public static ActionNetworkPacket importBlueprint(UUID networkId, String blueprint) {
        ActionNetworkPacket p = new ActionNetworkPacket(ActionType.IMPORT_BLUEPRINT, networkId);
        p.stringData = blueprint;
        return p;
    }

    public ActionNetworkPacket(FriendlyByteBuf buf) {
        this.actionType = buf.readEnum(ActionType.class);
        if (buf.readBoolean()) this.networkId = buf.readUUID();
        if (buf.readBoolean()) this.targetId = buf.readUUID();
        if (buf.readBoolean()) this.secondTargetId = buf.readUUID();
        if (buf.readBoolean()) this.stringData = buf.readUtf();
        if (buf.readBoolean()) this.intData = buf.readInt();
        if (buf.readBoolean()) this.posData = buf.readBlockPos();
        if (buf.readBoolean()) this.dimData = buf.readUtf();
        if (buf.readBoolean()) {
            this.ruleData = LogisticsUtil.readRule(buf);
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
            if (sideSize > 6) throw new RuntimeException("Too many side configs");
            for (int i = 0; i < sideSize; i++) {
                nodeData.sideConfig.put(buf.readEnum(Direction.class), buf.readUtf());
            }
            int slotSize = buf.readInt();
            if (slotSize > 1000) throw new RuntimeException("Too many slot configs");
            for (int i = 0; i < slotSize; i++) {
                nodeData.slotConfig.put(buf.readInt(), buf.readUtf());
            }
            nodeData.isMissing = buf.readBoolean();

            if (buf.readBoolean()) nodeData.referencedNetworkId = buf.readUUID();
            if (buf.readBoolean()) nodeData.targetPortId = buf.readUUID();
            int itemBufferSize = buf.readInt();
            if (itemBufferSize > 1000) throw new RuntimeException("Too many items in buffer");
            for (int i = 0; i < itemBufferSize; i++) {
                ItemStack stack = buf.readItem();
                if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) nodeData.virtualItemBuffer.add(stack);
            }
            long energyBuffer = buf.readLong();
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) nodeData.virtualEnergyBuffer = energyBuffer;
            int fluidBufferSize = buf.readInt();
            if (fluidBufferSize > 100) throw new RuntimeException("Too many fluids in buffer");
            for (int i = 0; i < fluidBufferSize; i++) {
                net.minecraft.nbt.CompoundTag fluidNbt = buf.readNbt();
                if (fluidNbt != null && net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                    nodeData.virtualFluidBuffer.add(net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(fluidNbt));
                }
            }
        }
        if (buf.readBoolean()) {
            this.groupData = new NodeGroup();
            groupData.groupId = buf.readUUID();
            groupData.name = buf.readUtf();
            groupData.guiX = buf.readInt();
            groupData.guiY = buf.readInt();
            groupData.expanded = buf.readBoolean();
            int members = buf.readInt();
            if (members > 1000) throw new RuntimeException("Too many members in group");
            for (int i = 0; i < members; i++) groupData.nodeIds.add(buf.readUUID());
        }
        if (buf.readBoolean()) {
            this.templateData = new RuleTemplate();
            templateData.templateId = buf.readUUID();
            templateData.name = buf.readUtf();
            templateData.rule = LogisticsUtil.readRule(buf);
        }
    }


    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(actionType);
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
            LogisticsUtil.writeRule(buf, ruleData);
        }
        buf.writeBoolean(nodeData != null);
        if (nodeData != null) {
            buf.writeUUID(nodeData.nodeId);
            buf.writeUtf(nodeData.nodeType != null ? nodeData.nodeType : "BLOCK");
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

            buf.writeBoolean(nodeData.referencedNetworkId != null);
            if (nodeData.referencedNetworkId != null) buf.writeUUID(nodeData.referencedNetworkId);
            buf.writeBoolean(nodeData.targetPortId != null);
            if (nodeData.targetPortId != null) buf.writeUUID(nodeData.targetPortId);
            buf.writeInt(nodeData.virtualItemBuffer.size());
            for (ItemStack stack : nodeData.virtualItemBuffer) buf.writeItem(stack);
            buf.writeLong(nodeData.virtualEnergyBuffer);
            buf.writeInt(nodeData.virtualFluidBuffer.size());
            for (net.minecraftforge.fluids.FluidStack fluid : nodeData.virtualFluidBuffer) {
                if (fluid != null) {
                    buf.writeNbt(fluid.writeToNBT(new net.minecraft.nbt.CompoundTag()));
                } else {
                    buf.writeNbt(new net.minecraft.nbt.CompoundTag());
                }
            }
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
            buf.writeUtf(templateData.name != null ? templateData.name : "");
            LogisticsUtil.writeRule(buf, templateData.rule);
        }
    }


    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                handleServer(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public void handleServer(ServerPlayer player) {
        if (nodeData != null) {
            // SECURITY: Never trust virtual buffers sent from the client
            nodeData.virtualItemBuffer.clear();
            nodeData.virtualEnergyBuffer = 0;
            nodeData.virtualFluidBuffer.clear();
        }
        LogisticsCapability.getNetworks(player).ifPresent(data -> {
            switch (actionType) {
                case CREATE_NETWORK -> {
                    if (data.getNetworks().size() >= 20) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cYou have reached the maximum number of networks (20)!"), true);
                        return;
                    }
                    NetworkData net = new NetworkData();
                    net.networkName = LogisticsUtil.cleanName(stringData);
                    data.getNetworks().add(net);
                }
                case DELETE_NETWORK -> data.getNetworks().removeIf(n -> n.networkId.equals(networkId));
                case ADD_PHYSICAL_NODE -> {
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            if (network.nodes.size() >= MAX_NODES_PER_NETWORK) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cNetwork node limit reached!"), true);
                                return;
                            }
                            ResourceLocation dimLoc = ResourceLocation.tryParse(dimData);
                            ServerLevel targetLevel = dimLoc != null ? player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc)) : null;
                            if (targetLevel == null) targetLevel = player.serverLevel();

                            if (intData == 1) { // Bulk addition (BFS)
                                addBulkPhysicalNodes(network, player, posData, targetLevel, dimData);
                            } else { // Single addition
                                if (network.nodes.stream().anyMatch(n -> posData.equals(n.pos))) {
                                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cBlock already in network!"), true);
                                    return;
                                }
                                if (addSinglePhysicalNode(network, player, posData, targetLevel, dimData)) {
                                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7aAdded block to network!"), true);
                                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                                }
                            }
                            network.nodeMap = null;
                            break;
                        }
                    }
                }
                case REMOVE_NODE -> {
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
                case ADD_UPDATE_RULE -> { // Add/Update Rule
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
                            if (!updated) {
                                if (network.rules.size() >= MAX_RULES_PER_NETWORK) {
                                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cNetwork rule limit reached!"), true);
                                    return;
                                }
                                network.rules.add(ruleData);
                            }
                            network.needsSorting = true;
                            break;
                        }
                    }
                }
                case REMOVE_RULE -> { // Remove Rule
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            network.rules.removeIf(rule -> rule.ruleId.equals(targetId));
                            network.needsSorting = true;
                            break;
                        }
                    }
                }
                case TOGGLE_NETWORK_ACTIVE -> { // Toggle Network Active
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            network.active = !network.active;
                            break;
                        }
                    }
                }
                case ADD_VIRTUAL_NODE -> {
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            if (network.nodes.size() >= MAX_NODES_PER_NETWORK) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cNetwork node limit reached!"), true);
                                return;
                            }
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
                                    node.customName = "Chamber: " + LogisticsUtil.cleanName(chamber.customName != null ? chamber.customName : chamber.mobId);
                                }
                            } else if (node.nodeType.equals("PORT_INPUT")) {
                                node.customName = LogisticsUtil.cleanName(stringData != null && !stringData.equals("PORT_INPUT") ? stringData : "Input Port");
                            } else if (node.nodeType.equals("PORT_OUTPUT")) {
                                node.customName = LogisticsUtil.cleanName(stringData != null && !stringData.equals("PORT_OUTPUT") ? stringData : "Output Port");
                            } else if (node.nodeType.equals("SUB_NETWORK")) {
                                node.referencedNetworkId = targetId;
                                node.targetPortId = secondTargetId;
                                NetworkData otherNet = NetworkTickHandler.findNetwork(player, targetId);
                                if (otherNet != null) {
                                    node.customName = "Net: " + otherNet.networkName;
                                    NetworkNode port = LogisticsUtil.findNode(otherNet, secondTargetId);
                                    if (port != null) node.customName += " (" + port.customName + ")";
                                } else {
                                    node.customName = "Sub-Network (Missing)";
                                }
                            }

                            network.nodes.add(node);
                            network.nodeMap = null;
                            break;
                        }
                    }
                }
                case UPDATE_NODE -> { // Update Node
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            for (NetworkNode existing : network.nodes) {
                                if (existing.nodeId.equals(nodeData.nodeId)) {
                                    // SECURITY: Only allow updating cosmetic and configuration fields.
                                    // Functional fields like pos, dimension, and virtual buffers must be immutable from the client.
                                    existing.customName = LogisticsUtil.cleanName(nodeData.customName);
                                    existing.guiX = nodeData.guiX;
                                    existing.guiY = nodeData.guiY;
                                    existing.iconItemId = nodeData.iconItemId;
                                    existing.sideConfig = new HashMap<>(nodeData.sideConfig);
                                    existing.slotConfig = new HashMap<>(nodeData.slotConfig);
                                    network.nodeMap = null;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                case RENAME_NETWORK -> { // Rename Network
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            network.networkName = LogisticsUtil.cleanName(stringData);
                            break;
                        }
                    }
                }
                case REQUEST_NODE_INVENTORY_PROBE -> { // Request Node Inventory Probe
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
                case TEST_RULE -> { // Test Rule
                    long now = System.currentTimeMillis();
                    if (testRuleCooldowns.getOrDefault(player.getUUID(), 0L) > now) return;
                    testRuleCooldowns.put(player.getUUID(), now + 500);

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
                case OPEN_MACHINE_GUI -> { // Open Machine GUI
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            for (NetworkNode node : network.nodes) {
                                if (node.nodeId.equals(targetId)) {
                                    if (node.nodeType.equals("BLOCK") && node.pos != null) {
                                        // SECURITY: Add distance check to prevent remote GUI access abuse
                                        if (player.blockPosition().distSqr(node.pos) > 4096) { // 64 block range
                                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cTarget block is too far away!"), true);
                                            return;
                                        }

                                        ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
                                        ServerLevel targetLevel = dimLoc != null ? player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc)) : null;
                                        if (targetLevel == null) targetLevel = player.serverLevel();
                                        net.minecraft.world.level.block.entity.BlockEntity be = targetLevel.getBlockEntity(node.pos);
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
                case TOGGLE_CONNECTIONS_VISIBILITY -> { // Toggle Connections Visibility
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            network.showConnections = !network.showConnections;
                            break;
                        }
                    }
                }
                case SET_LINK_MODE -> { // Set Link Mode
                    data.linkingNetworkId = networkId;
                }
                case TOGGLE_SIMULATION -> { // Toggle Simulation
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            network.simulationActive = !network.simulationActive;
                            break;
                        }
                    }
                }
                case ADD_UPDATE_GROUP -> { // Add/Update Group
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
                case REMOVE_GROUP -> { // Remove Group
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
                case REQUEST_GROUP_INVENTORY_PROBE -> { // Request Group Inventory Probe
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
                case ADD_UPDATE_TEMPLATE -> { // Add/Update Template
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
                case REMOVE_TEMPLATE -> { // Remove Template
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            network.ruleTemplates.removeIf(t -> t.templateId.equals(targetId));
                            break;
                        }
                    }
                }
                case APPLY_TEMPLATE -> { // Apply Template
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
                                boolean sourceIsPerm = LogisticsUtil.isPermanent(network, newRule.sourceNodeId, newRule.sourceIsGroup);
                                boolean destIsPerm = LogisticsUtil.isPermanent(network, newRule.destNodeId, newRule.destIsGroup);

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
                case PASTE_NODE_CONFIG -> { // Paste Node Config
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
                case SET_OVERFLOW_TARGET -> { // Set Overflow Target
                    for (NetworkData network : data.getNetworks()) {
                        if (network.networkId.equals(networkId)) {
                            network.overflowTargetId = targetId;
                            network.overflowIsGroup = intData == 1;
                            break;
                        }
                    }
                }
                case SET_VIEWED_NETWORK -> { // Set Viewed Network
                    data.viewedNetworkId = networkId;
                }
                case IMPORT_BLUEPRINT -> { // Import Blueprint
                    LogisticsBlueprint bp = LogisticsBlueprint.deserialize(stringData);
                    if (bp != null) {
                        // Security/Stability Limits
                        if (bp.nodes.size() > 200 || bp.rules.size() > MAX_RULES_PER_NETWORK) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cBlueprint too complex! (Max 200 nodes, " + MAX_RULES_PER_NETWORK + " rules)"), true);
                            return;
                        }

                        for (NetworkData network : data.getNetworks()) {
                            if (network.networkId.equals(networkId)) {
                                if (network.nodes.size() + bp.nodes.size() > MAX_NODES_PER_NETWORK || network.rules.size() + bp.rules.size() > MAX_RULES_PER_NETWORK) {
                                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cNetwork would exceed limits after import!"), true);
                                    return;
                                }
                                Map<UUID, UUID> idMap = new HashMap<>();

                                // 1. Remap Nodes
                                net.minecraft.core.BlockPos importPivot = player.blockPosition();
                                String currentDim = player.level().dimension().location().toString();
                                int importedBlockCount = 0;

                                for (NetworkNode node : bp.nodes) {
                                    if ("BLOCK".equals(node.nodeType) && node.pos != null) {
                                        // Apply relative offset from player's position
                                        node.pos = node.pos.offset(importPivot);
                                        node.dimension = currentDim;
                                        
                                        // SECURITY: Validate ownership
                                        if (!LogisticsUtil.canPlayerAccess(player, player.serverLevel(), node.pos)) {
                                            continue; // Skip this node if no access
                                        }
                                        importedBlockCount++;
                                    }

                                    UUID oldId = node.nodeId;
                                    node.nodeId = UUID.randomUUID();
                                    idMap.put(oldId, node.nodeId);
                                    network.nodes.add(node);
                                }

                                if (importedBlockCount > 0) {
                                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7aBlueprint imported! Verified " + importedBlockCount + " blocks."), true);
                                }

                                // 2. Remap Groups
                                for (NodeGroup group : bp.groups) {
                                    UUID oldId = group.groupId;
                                    group.groupId = UUID.randomUUID();
                                    idMap.put(oldId, group.groupId);

                                    List<UUID> newMembers = new ArrayList<>();
                                    for (UUID m : group.nodeIds) {
                                        if (idMap.containsKey(m)) newMembers.add(idMap.get(m));
                                    }
                                    group.nodeIds = newMembers;
                                    if (!group.nodeIds.isEmpty()) network.groups.add(group);
                                }

                                // 3. Remap and add Rules
                                for (LogisticsRule rule : bp.rules) {
                                    // Only add rule if both source and destination nodes/groups were successfully imported
                                    if (!idMap.containsKey(rule.sourceNodeId) || !idMap.containsKey(rule.destNodeId)) continue;

                                    rule.ruleId = UUID.randomUUID();
                                    rule.sourceNodeId = idMap.get(rule.sourceNodeId);
                                    rule.destNodeId = idMap.get(rule.destNodeId);
                                    
                                    if (rule.triggerNodeId != null && idMap.containsKey(rule.triggerNodeId)) {
                                        rule.triggerNodeId = idMap.get(rule.triggerNodeId);
                                    }

                                    // Update conditions
                                    for (LogicCondition cond : rule.conditions) {
                                        if (cond.targetId != null && idMap.containsKey(cond.targetId)) {
                                            cond.targetId = idMap.get(cond.targetId);
                                        }
                                    }

                                    network.rules.add(rule);
                                }

                                // 4. Remap Overflow
                                if (bp.overflowTargetId != null && idMap.containsKey(bp.overflowTargetId)) {
                                    network.overflowTargetId = idMap.get(bp.overflowTargetId);
                                    network.overflowIsGroup = bp.overflowIsGroup;
                                }

                                network.nodeMap = null;
                                network.groupMap = null;
                                network.needsSorting = true;
                                break;
                            }
                        }
                    }
                }
            }
            // Sync back
            PacketHandler.sendToPlayer(new SyncNetworksPacket(data.getNetworks(), data.viewedNetworkId), player);
        });
    }

    private void probeAndSyncGroupInventory(ServerPlayer player, NetworkData network, NodeGroup group) {
        Map<String, ItemStack> combined = new HashMap<>();
        for (UUID id : group.nodeIds) {
            NetworkNode node = LogisticsUtil.findNode(network, id);
            if (node == null) continue;
            
            // Re-using the logic from probeAndSyncInventory but accumulating
            List<ItemStack> items = LogisticsUtil.getItemsFromNode(player, node);
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
                    if (level != null && LogisticsUtil.canPlayerAccess(player, level, node.pos)) {
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


    private static boolean addSinglePhysicalNode(NetworkData network, ServerPlayer player, BlockPos pos, ServerLevel level, String dim) {
        if (!LogisticsUtil.canPlayerAccess(player, level, pos)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cAccess Denied: You cannot access this block!"), true);
            return false;
        }
        NetworkNode node = new NetworkNode();
        node.pos = pos;
        node.dimension = dim;
        node.nodeType = "BLOCK";
        node.guiX = (network.nodes.size() % 5) * 40 - 80;
        node.guiY = (network.nodes.size() / 5) * 40 - 80;

        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        node.blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        node.customName = LogisticsUtil.cleanName(state.getBlock().getName().getString()) + " [" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]";

        network.nodes.add(node);
        return true;
    }

    private static void addBulkPhysicalNodes(NetworkData network, ServerPlayer player, BlockPos startPos, ServerLevel level, String dim) {
        if (!LogisticsUtil.canPlayerAccess(player, level, startPos)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cAccess Denied: You cannot access the starting block!"), true);
            return;
        }
        net.minecraft.world.level.block.state.BlockState startState = level.getBlockState(startPos);
        net.minecraft.world.level.block.Block targetBlock = startState.getBlock();
        String blockName = LogisticsUtil.cleanName(targetBlock.getName().getString());

        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(startPos);
        Set<BlockPos> visited = new HashSet<>();
        visited.add(startPos);

        List<UUID> newNodeIds = new ArrayList<>();
        int limit = Math.min(64, MAX_NODES_PER_NETWORK - network.nodes.size());

        while (!queue.isEmpty() && newNodeIds.size() < limit) {
            BlockPos current = queue.poll();

            if (network.nodes.stream().noneMatch(n -> current.equals(n.pos))) {
                NetworkNode node = new NetworkNode();
                node.pos = current;
                node.dimension = dim;
                node.nodeType = "BLOCK";
                node.blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(targetBlock).toString();
                node.customName = blockName + " [" + current.getX() + "," + current.getY() + "," + current.getZ() + "]";
                node.guiX = (network.nodes.size() % 5) * 40 - 80;
                node.guiY = (network.nodes.size() / 5) * 40 - 80;

                network.nodes.add(node);
                newNodeIds.add(node.nodeId);
            }

            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!visited.contains(next) && LogisticsUtil.canPlayerAccess(player, level, next) && level.getBlockState(next).is(targetBlock)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        if (!newNodeIds.isEmpty()) {
            NodeGroup group = new NodeGroup("Group: " + blockName + "s");
            group.nodeIds.addAll(newNodeIds);
            group.guiX = -150;
            group.guiY = -150;
            network.groups.add(group);
            network.groupMap = null;
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7aBulk added " + newNodeIds.size() + " " + blockName + " blocks!"), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

}

