package com.example.modmenu.network;

import com.example.modmenu.store.logistics.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

public class GroupManagementPacket {
    public enum Type {
        ADD_UPDATE, REMOVE, PROBE_INVENTORY
    }

    private final Type type;
    private final UUID networkId;
    private UUID targetId;
    private NodeGroup groupData;

    public GroupManagementPacket(Type type, UUID networkId) {
        this.type = type;
        this.networkId = networkId;
    }

    public static GroupManagementPacket addUpdate(UUID networkId, NodeGroup group) {
        GroupManagementPacket p = new GroupManagementPacket(Type.ADD_UPDATE, networkId);
        p.groupData = group;
        return p;
    }

    public static GroupManagementPacket remove(UUID networkId, UUID groupId) {
        GroupManagementPacket p = new GroupManagementPacket(Type.REMOVE, networkId);
        p.targetId = groupId;
        return p;
    }

    public static GroupManagementPacket probeInventory(UUID networkId, UUID groupId) {
        GroupManagementPacket p = new GroupManagementPacket(Type.PROBE_INVENTORY, networkId);
        p.targetId = groupId;
        return p;
    }

    public GroupManagementPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(Type.class);
        this.networkId = buf.readUUID();
        if (buf.readBoolean()) this.targetId = buf.readUUID();
        if (buf.readBoolean()) {
            this.groupData = new NodeGroup();
            this.groupData.loadNBT(buf.readNbt());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUUID(networkId);
        buf.writeBoolean(targetId != null);
        if (targetId != null) buf.writeUUID(targetId);
        buf.writeBoolean(groupData != null);
        if (groupData != null) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            groupData.saveNBT(tag);
            buf.writeNbt(tag);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LogisticsCapability.getNetworks(player).ifPresent(data -> {
                NetworkData network = data.getNetworks().stream().filter(n -> n.networkId.equals(networkId)).findFirst().orElse(null);
                if (network == null) return;

                switch (type) {
                    case ADD_UPDATE -> {
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
                    }
                    case REMOVE -> {
                        network.groups.removeIf(g -> g.groupId.equals(targetId));
                        network.rules.removeIf(rule -> rule.sourceNodeId.equals(targetId) || rule.destNodeId.equals(targetId));
                        network.groupMap = null;
                        network.needsSorting = true;
                    }
                    case PROBE_INVENTORY -> {
                        NodeGroup group = network.groups.stream().filter(g -> g.groupId.equals(targetId)).findFirst().orElse(null);
                        if (group != null) {
                            probeAndSyncGroupInventory(player, network, group);
                        }
                    }
                }
                LogisticsUtil.syncAndNotify(player, data);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private void probeAndSyncGroupInventory(ServerPlayer player, NetworkData network, NodeGroup group) {
        Map<String, ItemStack> combined = new HashMap<>();
        for (UUID id : group.nodeIds) {
            NetworkNode node = network.nodes.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(n -> n.nodeId.equals(id))
                    .findFirst().orElse(null);
            if (node == null) continue;
            
            // Re-using the logic from NodeManagementPacket.getItemsFromNode logic (which I didn't move yet but I'll implement it here)
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
}
