package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.logistics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;
import java.util.function.Supplier;

public class ActionNetworkPacket {
    private final int action;
    private UUID networkId;
    private UUID targetId;
    private String stringData;
    private int intData;
    private BlockPos posData;
    private String dimData;
    private LogisticsRule ruleData;
    private NetworkNode nodeData;
    private NodeGroup groupData;

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

    public ActionNetworkPacket(FriendlyByteBuf buf) {
        this.action = buf.readInt();
        if (buf.readBoolean()) this.networkId = buf.readUUID();
        if (buf.readBoolean()) this.targetId = buf.readUUID();
        if (buf.readBoolean()) this.stringData = buf.readUtf();
        if (buf.readBoolean()) this.intData = buf.readInt();
        if (buf.readBoolean()) this.posData = buf.readBlockPos();
        if (buf.readBoolean()) this.dimData = buf.readUtf();
        if (buf.readBoolean()) {
            this.ruleData = new LogisticsRule();
            ruleData.ruleId = buf.readUUID();
            ruleData.sourceNodeId = buf.readUUID();
            ruleData.sourceIsGroup = buf.readBoolean();
            ruleData.destNodeId = buf.readUUID();
            ruleData.destIsGroup = buf.readBoolean();
            ruleData.sourceSide = buf.readUtf();
            ruleData.destSide = buf.readUtf();
            ruleData.mode = buf.readUtf();
            ruleData.amountPerTick = buf.readInt();
            ruleData.minAmount = buf.readInt();
            ruleData.maxAmount = buf.readInt();
            ruleData.priority = buf.readInt();
            ruleData.speedMode = buf.readUtf();
            ruleData.type = buf.readUtf();
            int srcSlotSize = buf.readInt();
            ruleData.sourceSlots = new java.util.ArrayList<>(srcSlotSize);
            for (int i = 0; i < srcSlotSize; i++) ruleData.sourceSlots.add(buf.readInt());
            int dstSlotSize = buf.readInt();
            ruleData.destSlots = new java.util.ArrayList<>(dstSlotSize);
            for (int i = 0; i < dstSlotSize; i++) ruleData.destSlots.add(buf.readInt());
            ruleData.active = buf.readBoolean();
            ruleData.filter = new LogisticsFilter();
            ruleData.filter.matchType = buf.readUtf();
            int valSize = buf.readInt();
            ruleData.filter.matchValues = new java.util.ArrayList<>(valSize);
            for (int i = 0; i < valSize; i++) ruleData.filter.matchValues.add(buf.readUtf());
            if (buf.readBoolean()) ruleData.filter.nbtSample = buf.readNbt();
            ruleData.filter.blacklist = buf.readBoolean();
            ruleData.filter.fuzzyNbt = buf.readBoolean();
        }
        if (buf.readBoolean()) {
            this.nodeData = new NetworkNode();
            nodeData.nodeId = buf.readUUID();
            nodeData.nodeType = buf.readUtf();
            nodeData.chamberIndex = buf.readInt();
            if (buf.readBoolean()) nodeData.pos = buf.readBlockPos();
            if (buf.readBoolean()) nodeData.dimension = buf.readUtf();
            if (buf.readBoolean()) nodeData.blockId = buf.readUtf();
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
            int members = buf.readInt();
            for (int i = 0; i < members; i++) groupData.nodeIds.add(buf.readUUID());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(action);
        buf.writeBoolean(networkId != null);
        if (networkId != null) buf.writeUUID(networkId);
        buf.writeBoolean(targetId != null);
        if (targetId != null) buf.writeUUID(targetId);
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
            buf.writeUUID(ruleData.ruleId);
            buf.writeUUID(ruleData.sourceNodeId);
            buf.writeBoolean(ruleData.sourceIsGroup);
            buf.writeUUID(ruleData.destNodeId);
            buf.writeBoolean(ruleData.destIsGroup);
            buf.writeUtf(ruleData.sourceSide);
            buf.writeUtf(ruleData.destSide);
            buf.writeUtf(ruleData.mode);
            buf.writeInt(ruleData.amountPerTick);
            buf.writeInt(ruleData.minAmount);
            buf.writeInt(ruleData.maxAmount);
            buf.writeInt(ruleData.priority);
            buf.writeUtf(ruleData.speedMode);
            buf.writeUtf(ruleData.type);
            buf.writeInt(ruleData.sourceSlots.size());
            for (int s : ruleData.sourceSlots) buf.writeInt(s);
            buf.writeInt(ruleData.destSlots.size());
            for (int s : ruleData.destSlots) buf.writeInt(s);
            buf.writeBoolean(ruleData.active);
            buf.writeUtf(ruleData.filter.matchType);
            buf.writeInt(ruleData.filter.matchValues.size());
            for (String s : ruleData.filter.matchValues) buf.writeUtf(s);
            buf.writeBoolean(ruleData.filter.nbtSample != null);
            if (ruleData.filter.nbtSample != null) buf.writeNbt(ruleData.filter.nbtSample);
            buf.writeBoolean(ruleData.filter.blacklist);
            buf.writeBoolean(ruleData.filter.fuzzyNbt);
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
            buf.writeInt(groupData.nodeIds.size());
            for (UUID id : groupData.nodeIds) buf.writeUUID(id);
        }
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
                                    break;
                                }
                            }
                        }
                        case 5 -> { // Remove Rule
                            for (NetworkData network : data.getNetworks()) {
                                if (network.networkId.equals(networkId)) {
                                    network.rules.removeIf(rule -> rule.ruleId.equals(targetId));
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
                                    break;
                                }
                            }
                        }
                    }
                    // Sync back
                    PacketHandler.sendToPlayer(new SyncNetworksPacket(data.getNetworks()), player);
                });
            }
        });
        ctx.get().setPacketHandled(true);
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

