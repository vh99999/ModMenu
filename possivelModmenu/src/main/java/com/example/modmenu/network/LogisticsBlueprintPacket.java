package com.example.modmenu.network;

import com.example.modmenu.store.logistics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class LogisticsBlueprintPacket {
    private final UUID networkId;
    private final String blueprintData;

    public LogisticsBlueprintPacket(UUID networkId, String blueprintData) {
        this.networkId = networkId;
        this.blueprintData = blueprintData;
    }

    public LogisticsBlueprintPacket(FriendlyByteBuf buf) {
        this.networkId = buf.readUUID();
        this.blueprintData = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeUtf(blueprintData);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LogisticsCapability.getNetworks(player).ifPresent(data -> {
                NetworkData network = data.getNetworks().stream().filter(n -> n.networkId.equals(networkId)).findFirst().orElse(null);
                if (network == null) return;

                LogisticsBlueprint bp = LogisticsBlueprint.deserialize(blueprintData);
                if (bp != null) {
                    // Security/Stability Limits
                    if (bp.nodes.size() > 200 || bp.rules.size() > LogisticsUtil.MAX_RULES_PER_NETWORK) {
                        player.displayClientMessage(Component.literal("\u00A7cBlueprint too complex! (Max 200 nodes, " + LogisticsUtil.MAX_RULES_PER_NETWORK + " rules)"), true);
                        return;
                    }

                    if (network.nodes.size() + bp.nodes.size() > LogisticsUtil.MAX_NODES_PER_NETWORK || network.rules.size() + bp.rules.size() > LogisticsUtil.MAX_RULES_PER_NETWORK) {
                        player.displayClientMessage(Component.literal("\u00A7cNetwork would exceed limits after import!"), true);
                        return;
                    }
                    
                    Map<UUID, UUID> idMap = new HashMap<>();

                    // 1. Remap Nodes
                    BlockPos importPivot = player.blockPosition();
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
                        player.displayClientMessage(Component.literal("\u00A7aBlueprint imported! Verified " + importedBlockCount + " blocks."), true);
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
                }
                LogisticsUtil.syncAndNotify(player, data);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
