package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.logistics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

public class NodeManagementPacket {
    public enum Type {
        ADD_PHYSICAL, ADD_VIRTUAL, REMOVE, UPDATE, PASTE_CONFIG, PROBE_INVENTORY, OPEN_GUI
    }

    private final Type type;
    private final UUID networkId;
    private UUID targetId;
    private UUID secondTargetId;
    private BlockPos pos;
    private String dimension;
    private boolean bulk;
    private String nodeType;
    private int index;
    private NetworkNode nodeData;

    public NodeManagementPacket(Type type, UUID networkId) {
        this.type = type;
        this.networkId = networkId;
    }

    public static NodeManagementPacket addPhysical(UUID networkId, BlockPos pos, String dimension, boolean bulk) {
        NodeManagementPacket p = new NodeManagementPacket(Type.ADD_PHYSICAL, networkId);
        p.pos = pos;
        p.dimension = dimension;
        p.bulk = bulk;
        return p;
    }

    public static NodeManagementPacket addVirtual(UUID networkId, String nodeType, int index) {
        NodeManagementPacket p = new NodeManagementPacket(Type.ADD_VIRTUAL, networkId);
        p.nodeType = nodeType;
        p.index = index;
        return p;
    }

    public static NodeManagementPacket remove(UUID networkId, UUID nodeId) {
        NodeManagementPacket p = new NodeManagementPacket(Type.REMOVE, networkId);
        p.targetId = nodeId;
        return p;
    }

    public static NodeManagementPacket update(UUID networkId, NetworkNode node) {
        NodeManagementPacket p = new NodeManagementPacket(Type.UPDATE, networkId);
        p.nodeData = node;
        return p;
    }

    public static NodeManagementPacket pasteConfig(UUID networkId, UUID nodeId, NetworkNode config) {
        NodeManagementPacket p = new NodeManagementPacket(Type.PASTE_CONFIG, networkId);
        p.targetId = nodeId;
        p.nodeData = config;
        return p;
    }

    public static NodeManagementPacket probeInventory(UUID networkId, UUID nodeId) {
        NodeManagementPacket p = new NodeManagementPacket(Type.PROBE_INVENTORY, networkId);
        p.targetId = nodeId;
        return p;
    }

    public static NodeManagementPacket openGui(UUID networkId, UUID nodeId) {
        NodeManagementPacket p = new NodeManagementPacket(Type.OPEN_GUI, networkId);
        p.targetId = nodeId;
        return p;
    }

    public NodeManagementPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(Type.class);
        this.networkId = buf.readUUID();
        if (buf.readBoolean()) this.targetId = buf.readUUID();
        if (buf.readBoolean()) this.secondTargetId = buf.readUUID();
        if (buf.readBoolean()) this.pos = buf.readBlockPos();
        if (buf.readBoolean()) this.dimension = buf.readUtf();
        this.bulk = buf.readBoolean();
        if (buf.readBoolean()) this.nodeType = buf.readUtf();
        this.index = buf.readInt();
        if (buf.readBoolean()) this.nodeData = NetworkNode.loadNBT(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUUID(networkId);
        buf.writeBoolean(targetId != null);
        if (targetId != null) buf.writeUUID(targetId);
        buf.writeBoolean(secondTargetId != null);
        if (secondTargetId != null) buf.writeUUID(secondTargetId);
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeBlockPos(pos);
        buf.writeBoolean(dimension != null);
        if (dimension != null) buf.writeUtf(dimension);
        buf.writeBoolean(bulk);
        buf.writeBoolean(nodeType != null);
        if (nodeType != null) buf.writeUtf(nodeType);
        buf.writeInt(index);
        buf.writeBoolean(nodeData != null);
        if (nodeData != null) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            nodeData.saveNBT(tag);
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
                    case ADD_PHYSICAL -> {
                        if (network.nodes.size() >= LogisticsUtil.MAX_NODES_PER_NETWORK) {
                            player.displayClientMessage(Component.literal("\u00A7cNetwork node limit reached!"), true);
                            return;
                        }
                        ResourceLocation dimLoc = ResourceLocation.tryParse(dimension);
                        ServerLevel targetLevel = dimLoc != null ? player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc)) : null;
                        if (targetLevel == null) targetLevel = player.serverLevel();

                        if (bulk) {
                            addBulkPhysicalNodes(network, player, pos, targetLevel, dimension);
                        } else {
                            if (network.nodes.stream().anyMatch(n -> pos.equals(n.pos))) {
                                player.displayClientMessage(Component.literal("\u00A7cBlock already in network!"), true);
                                return;
                            }
                            if (addSinglePhysicalNode(network, player, pos, targetLevel, dimension)) {
                                player.displayClientMessage(Component.literal("\u00A7aAdded block to network!"), true);
                                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                            }
                        }
                        network.nodeMap = null;
                    }
                    case ADD_VIRTUAL -> {
                        if (network.nodes.size() >= LogisticsUtil.MAX_NODES_PER_NETWORK) {
                            player.displayClientMessage(Component.literal("\u00A7cNetwork node limit reached!"), true);
                            return;
                        }
                        NetworkNode node = new NetworkNode();
                        node.nodeType = nodeType;
                        node.chamberIndex = index;
                        node.guiX = (network.nodes.size() % 5) * 40 - 80;
                        node.guiY = (network.nodes.size() / 5) * 40 - 80;

                        if (node.nodeType.equals("PLAYER")) node.customName = "Player Inventory";
                        else if (node.nodeType.equals("MARKET")) node.customName = "Market (Auto-Sell)";
                        else if (node.nodeType.equals("CHAMBER")) {
                            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
                            if (index >= 0 && index < skillData.chambers.size()) {
                                StorePriceManager.ChamberData chamber = skillData.chambers.get(index);
                                node.customName = "Chamber: " + LogisticsUtil.cleanName(chamber.customName != null ? chamber.customName : chamber.mobId);
                            }
                        }

                        network.nodes.add(node);
                        network.nodeMap = null;
                    }
                    case REMOVE -> {
                        network.nodes.removeIf(node -> node.nodeId.equals(targetId));
                        network.rules.removeIf(rule -> rule.sourceNodeId.equals(targetId) || rule.destNodeId.equals(targetId));
                        network.nodeMap = null;
                        network.needsSorting = true;
                    }
                    case UPDATE -> {
                        for (int i = 0; i < network.nodes.size(); i++) {
                            if (network.nodes.get(i).nodeId.equals(nodeData.nodeId)) {
                                network.nodes.set(i, nodeData);
                                network.nodeMap = null;
                                break;
                            }
                        }
                    }
                    case PASTE_CONFIG -> {
                        for (NetworkNode node : network.nodes) {
                            if (node.nodeId.equals(targetId)) {
                                node.iconItemId = nodeData.iconItemId;
                                node.sideConfig = new HashMap<>(nodeData.sideConfig);
                                node.slotConfig = new HashMap<>(nodeData.slotConfig);
                                break;
                            }
                        }
                    }
                    case PROBE_INVENTORY -> {
                        NetworkNode node = network.nodes.stream().filter(n -> n.nodeId.equals(targetId)).findFirst().orElse(null);
                        if (node != null) {
                            probeAndSyncInventory(player, node);
                        }
                    }
                    case OPEN_GUI -> {
                        for (NetworkNode node : network.nodes) {
                            if (node.nodeId.equals(targetId)) {
                                if (node.nodeType.equals("BLOCK") && node.pos != null) {
                                    // SECURITY: Add distance check to prevent remote GUI access abuse
                                    if (player.blockPosition().distSqr(node.pos) > 4096) { // 64 block range
                                        player.displayClientMessage(Component.literal("\u00A7cTarget block is too far away!"), true);
                                        return;
                                    }

                                    ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
                                    if (dimLoc != null) {
                                        ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
                                        if (level != null && level.hasChunkAt(node.pos) && LogisticsUtil.canPlayerAccess(player, level, node.pos)) {
                                            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(node.pos);
                                            if (be instanceof net.minecraft.world.MenuProvider mp) {
                                                net.minecraftforge.network.NetworkHooks.openScreen(player, mp, node.pos);
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                LogisticsUtil.syncAndNotify(player, data);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private boolean addSinglePhysicalNode(NetworkData network, ServerPlayer player, BlockPos pos, ServerLevel level, String dim) {
        if (!LogisticsUtil.canPlayerAccess(player, level, pos)) {
            player.displayClientMessage(Component.literal("\u00A7cAccess Denied: You cannot access this block!"), true);
            return false;
        }
        NetworkNode node = new NetworkNode();
        node.pos = pos;
        node.dimension = dim;
        node.nodeType = "BLOCK";
        node.guiX = (network.nodes.size() % 5) * 40 - 80;
        node.guiY = (network.nodes.size() / 5) * 40 - 80;

        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        node.blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        node.customName = LogisticsUtil.cleanName(state.getBlock().getName().getString()) + " [" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]";

        network.nodes.add(node);
        return true;
    }

    private void addBulkPhysicalNodes(NetworkData network, ServerPlayer player, BlockPos startPos, ServerLevel level, String dim) {
        if (!LogisticsUtil.canPlayerAccess(player, level, startPos)) {
            player.displayClientMessage(Component.literal("\u00A7cAccess Denied: You cannot access the starting block!"), true);
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
        int limit = Math.min(64, LogisticsUtil.MAX_NODES_PER_NETWORK - network.nodes.size());

        while (!queue.isEmpty() && newNodeIds.size() < limit) {
            BlockPos current = queue.poll();

            if (network.nodes.stream().noneMatch(n -> current.equals(n.pos))) {
                NetworkNode node = new NetworkNode();
                node.pos = current;
                node.dimension = dim;
                node.nodeType = "BLOCK";
                node.blockId = ForgeRegistries.BLOCKS.getKey(targetBlock).toString();
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
            player.displayClientMessage(Component.literal("\u00A7aBulk added " + newNodeIds.size() + " " + blockName + " blocks!"), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void probeAndSyncInventory(ServerPlayer player, NetworkNode node) {
        List<ItemStack> items = new ArrayList<>();
        List<Integer> slotX = new ArrayList<>();
        List<Integer> slotY = new ArrayList<>();
        ResourceLocation guiTexture = null;
        
        if (node.nodeType.equals("BLOCK")) {
            if (node.pos != null && node.dimension != null) {
                ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
                if (dimLoc != null) {
                    ServerLevel level = player.serverLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
                    if (level != null && LogisticsUtil.canPlayerAccess(player, level, node.pos)) {
                        BlockEntity be = level.getBlockEntity(node.pos);
                        if (be != null) {
                            if (be instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity) guiTexture = ResourceLocation.tryParse("textures/gui/container/furnace.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity) guiTexture = ResourceLocation.tryParse("textures/gui/container/hopper.png");
                            else if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) guiTexture = ResourceLocation.tryParse("textures/gui/container/generic_54.png");

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
                                } catch (Exception ignored) {}
                            }

                            if (items.isEmpty()) {
                                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
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
            guiTexture = ResourceLocation.tryParse("textures/gui/container/inventory.png");
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                items.add(player.getInventory().getItem(i).copy());
                int x, y;
                if (i >= 0 && i <= 8) { x = 8 + i * 18; y = 142; }
                else if (i >= 9 && i <= 35) { x = 8 + (i - 9) % 9 * 18; y = 84 + (i - 9) / 9 * 18; }
                else if (i >= 36 && i <= 39) { x = 8; y = 62 - (i - 36) * 18; }
                else if (i == 40) { x = 77; y = 62; }
                else { x = (i % 9) * 18 + 8; y = (i / 9) * 18 + 8; }
                slotX.add(x);
                slotY.add(y);
            }
        } else if (node.nodeType.equals("CHAMBER")) {
            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
            if (node.chamberIndex >= 0 && node.chamberIndex < skillData.chambers.size()) {
                StorePriceManager.ChamberData chamber = skillData.chambers.get(node.chamberIndex);
                for (int i = 0; i < chamber.storedLoot.size(); i++) {
                    items.add(chamber.storedLoot.get(i).copy());
                    slotX.add((i % 9) * 18);
                    slotY.add((i / 9) * 18);
                }
            }
        }

        PacketHandler.sendToPlayer(new SyncNodeInventoryPacket(node.nodeId, items, slotX, slotY, guiTexture), player);
    }
}
