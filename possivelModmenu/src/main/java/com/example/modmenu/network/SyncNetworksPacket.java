package com.example.modmenu.network;

import com.example.modmenu.store.logistics.LogisticsFilter;
import com.example.modmenu.store.logistics.LogisticsRule;
import com.example.modmenu.store.logistics.NodeGroup;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.NetworkNode;
import com.example.modmenu.store.logistics.LogisticsCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncNetworksPacket {
    private final List<NetworkData> networks;

    public SyncNetworksPacket(List<NetworkData> networks) {
        this.networks = new ArrayList<>();
        for (NetworkData nd : networks) {
            this.networks.add(nd.snapshot());
        }
    }

    public SyncNetworksPacket(FriendlyByteBuf buf) {
        int netSize = buf.readInt();
        this.networks = new ArrayList<>(netSize);
        for (int i = 0; i < netSize; i++) {
            NetworkData nd = new NetworkData();
            nd.networkId = buf.readUUID();
            nd.networkName = buf.readUtf();
            nd.tickBudget = buf.readInt();
            nd.active = buf.readBoolean();
            nd.showConnections = buf.readBoolean();
            nd.simulationActive = buf.readBoolean();
            nd.lastReport = buf.readUtf();
            nd.itemsMovedLastMin = buf.readLong();
            nd.energyMovedLastMin = buf.readLong();
            nd.fluidsMovedLastMin = buf.readLong();

            int groupSize = buf.readInt();
            for (int j = 0; j < groupSize; j++) {
                NodeGroup group = new NodeGroup();
                group.groupId = buf.readUUID();
                group.name = buf.readUtf();
                int members = buf.readInt();
                for (int k = 0; k < members; k++) group.nodeIds.add(buf.readUUID());
                nd.groups.add(group);
            }

            int nodeSize = buf.readInt();
            for (int j = 0; j < nodeSize; j++) {
                NetworkNode node = new NetworkNode();
                node.nodeId = buf.readUUID();
                node.nodeType = buf.readUtf();
                node.chamberIndex = buf.readInt();
                if (buf.readBoolean()) node.pos = buf.readBlockPos();
                if (buf.readBoolean()) node.dimension = buf.readUtf();
                if (buf.readBoolean()) node.blockId = buf.readUtf();
                if (buf.readBoolean()) node.customName = buf.readUtf();
                node.guiX = buf.readInt();
                node.guiY = buf.readInt();
                int sideSize = buf.readInt();
                for (int k = 0; k < sideSize; k++) {
                    node.sideConfig.put(buf.readEnum(Direction.class), buf.readUtf());
                }
                int slotSize = buf.readInt();
                for (int k = 0; k < slotSize; k++) {
                    node.slotConfig.put(buf.readInt(), buf.readUtf());
                }
                node.isMissing = buf.readBoolean();
                nd.nodes.add(node);
            }

            int ruleSize = buf.readInt();
            for (int j = 0; j < ruleSize; j++) {
                LogisticsRule rule = new LogisticsRule();
                rule.ruleId = buf.readUUID();
                rule.sourceNodeId = buf.readUUID();
                rule.sourceIsGroup = buf.readBoolean();
                rule.destNodeId = buf.readUUID();
                rule.destIsGroup = buf.readBoolean();
                rule.sourceSide = buf.readUtf();
                rule.destSide = buf.readUtf();
                rule.mode = buf.readUtf();
                rule.amountPerTick = buf.readInt();
                rule.minAmount = buf.readInt();
                rule.maxAmount = buf.readInt();
                rule.priority = buf.readInt();
                rule.speedMode = buf.readUtf();
                rule.type = buf.readUtf();
                int srcSlotSize = buf.readInt();
                rule.sourceSlots = new ArrayList<>(srcSlotSize);
                for (int k = 0; k < srcSlotSize; k++) rule.sourceSlots.add(buf.readInt());
                int dstSlotSize = buf.readInt();
                rule.destSlots = new ArrayList<>(dstSlotSize);
                for (int k = 0; k < dstSlotSize; k++) rule.destSlots.add(buf.readInt());
                rule.active = buf.readBoolean();
                rule.lastReport = buf.readUtf();

                rule.filter = new LogisticsFilter();
                rule.filter.matchType = buf.readUtf();
                int valSize = buf.readInt();
                rule.filter.matchValues = new ArrayList<>(valSize);
                for (int k = 0; k < valSize; k++) rule.filter.matchValues.add(buf.readUtf());
                if (buf.readBoolean()) rule.filter.nbtSample = buf.readNbt();
                rule.filter.blacklist = buf.readBoolean();
                rule.filter.fuzzyNbt = buf.readBoolean();

                nd.rules.add(rule);
            }
            this.networks.add(nd);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(networks.size());
        for (NetworkData nd : networks) {
            buf.writeUUID(nd.networkId);
            buf.writeUtf(nd.networkName != null ? nd.networkName : "Unnamed Network");
            buf.writeInt(nd.tickBudget);
            buf.writeBoolean(nd.active);
            buf.writeBoolean(nd.showConnections);
            buf.writeBoolean(nd.simulationActive);
            buf.writeUtf(nd.lastReport != null ? nd.lastReport : "");
            buf.writeLong(nd.itemsMovedLastMin);
            buf.writeLong(nd.energyMovedLastMin);
            buf.writeLong(nd.fluidsMovedLastMin);

            buf.writeInt(nd.groups.size());
            for (NodeGroup group : nd.groups) {
                buf.writeUUID(group.groupId);
                buf.writeUtf(group.name != null ? group.name : "Unnamed Group");
                buf.writeInt(group.nodeIds.size());
                for (UUID id : group.nodeIds) buf.writeUUID(id);
            }

            buf.writeInt(nd.nodes.size());
            for (NetworkNode node : nd.nodes) {
                buf.writeUUID(node.nodeId);
                buf.writeUtf(node.nodeType);
                buf.writeInt(node.chamberIndex);
                buf.writeBoolean(node.pos != null);
                if (node.pos != null) buf.writeBlockPos(node.pos);
                buf.writeBoolean(node.dimension != null);
                if (node.dimension != null) buf.writeUtf(node.dimension);
                buf.writeBoolean(node.blockId != null);
                if (node.blockId != null) buf.writeUtf(node.blockId);
                buf.writeBoolean(node.customName != null);
                if (node.customName != null) buf.writeUtf(node.customName);
                buf.writeInt(node.guiX);
                buf.writeInt(node.guiY);
                buf.writeInt(node.sideConfig.size());
                for (Map.Entry<Direction, String> entry : node.sideConfig.entrySet()) {
                    buf.writeEnum(entry.getKey());
                    buf.writeUtf(entry.getValue());
                }
                buf.writeInt(node.slotConfig.size());
                for (Map.Entry<Integer, String> entry : node.slotConfig.entrySet()) {
                    buf.writeInt(entry.getKey());
                    buf.writeUtf(entry.getValue());
                }
                buf.writeBoolean(node.isMissing);
            }

            buf.writeInt(nd.rules.size());
            for (LogisticsRule rule : nd.rules) {
                buf.writeUUID(rule.ruleId);
                buf.writeUUID(rule.sourceNodeId);
                buf.writeBoolean(rule.sourceIsGroup);
                buf.writeUUID(rule.destNodeId);
                buf.writeBoolean(rule.destIsGroup);
                buf.writeUtf(rule.sourceSide);
                buf.writeUtf(rule.destSide);
                buf.writeUtf(rule.mode);
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
                buf.writeUtf(rule.lastReport != null ? rule.lastReport : "");

                buf.writeUtf(rule.filter.matchType);
                buf.writeInt(rule.filter.matchValues.size());
                for (String s : rule.filter.matchValues) buf.writeUtf(s);
                buf.writeBoolean(rule.filter.nbtSample != null);
                if (rule.filter.nbtSample != null) buf.writeNbt(rule.filter.nbtSample);
                buf.writeBoolean(rule.filter.blacklist);
                buf.writeBoolean(rule.filter.fuzzyNbt);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                LogisticsCapability.getNetworks(player).ifPresent(data -> {
                    data.setNetworks(this.networks);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}