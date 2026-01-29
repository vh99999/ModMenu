package com.example.modmenu.store.logistics;

import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.SyncNetworksPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public class LogisticsUtil {
    public static final int MAX_NODES_PER_NETWORK = 1000;
    public static final int MAX_RULES_PER_NETWORK = 500;

    private static final ThreadLocal<Boolean> IS_PERMISSION_CHECK = ThreadLocal.withInitial(() -> false);

    public static boolean isPermissionCheck() {
        return IS_PERMISSION_CHECK.get();
    }

    public static boolean canPlayerAccess(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!level.hasChunkAt(pos)) return false;
        
        IS_PERMISSION_CHECK.set(true);
        try {
            // Ownership check: use mayInteract and fire a dummy interact event for protection mods
            if (!level.mayInteract(player, pos)) return false;
            
            PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, 
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            MinecraftForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        } finally {
            IS_PERMISSION_CHECK.set(false);
        }
    }

    public static String cleanName(String name) {
        if (name == null) return "Unknown";
        // stripColor handles \u00A7. formatting codes
        String cleaned = StringUtil.stripColor(name);
        
        // Remove common placeholders and garbage
        cleaned = cleaned.replaceAll("(?i)%s|XsXs", "");
        
        // Remove common modded garbage prefixes like "64x " or "x "
        cleaned = cleaned.replaceAll("(?i)^(\\d+x|x|\\d+)\\s+", "");
        
        // Final trim and cleanup of leading/trailing non-alphanumeric (except brackets)
        cleaned = cleaned.replaceAll("^[^\\w\\s\\(\\)\\[\\]\\{\\}]+|[^\\w\\s\\(\\)\\[\\]\\{\\}]+$", "");
        
        cleaned = cleaned.trim();
        if (cleaned.length() > 64) cleaned = cleaned.substring(0, 61) + "...";
        
        return cleaned.isEmpty() ? "Block" : cleaned;
    }

    public static void syncAndNotify(ServerPlayer player, PlayerNetworkData data) {
        PacketHandler.sendToPlayer(new SyncNetworksPacket(data.getNetworks(), data.viewedNetworkId), player);
    }

    public static LogisticsRule readRule(net.minecraft.network.FriendlyByteBuf buf) {
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
        rule.ruleAction = buf.readUtf();
        rule.triggerType = buf.readUtf();
        rule.variableName = buf.readUtf();
        rule.variableOp = buf.readUtf();
        rule.secondaryVariableName = buf.readUtf();
        rule.constantValue = buf.readDouble();
        rule.signalFilter = buf.readUtf();
        if (buf.readBoolean()) rule.triggerNodeId = buf.readUUID();
        rule.emitSignalOnSuccess = buf.readUtf();
        rule.scanItems = buf.readBoolean();

        int srcSlotSize = Math.min(buf.readInt(), 1000);
        rule.sourceSlots = new java.util.ArrayList<>(Math.max(0, srcSlotSize));
        for (int i = 0; i < srcSlotSize; i++) rule.sourceSlots.add(buf.readInt());
        int dstSlotSize = Math.min(buf.readInt(), 1000);
        rule.destSlots = new java.util.ArrayList<>(Math.max(0, dstSlotSize));
        for (int i = 0; i < dstSlotSize; i++) rule.destSlots.add(buf.readInt());
        rule.active = buf.readBoolean();
        rule.maintenanceMode = buf.readBoolean();
        int condSize = Math.min(buf.readInt(), 100);
        for (int i = 0; i < condSize; i++) {
            LogicCondition cond = new LogicCondition();
            if (buf.readBoolean()) cond.targetId = buf.readUUID();
            cond.isGroup = buf.readBoolean();
            cond.type = buf.readUtf();
            cond.operator = buf.readUtf();
            cond.value = buf.readInt();
            cond.variableName = buf.readUtf();
            cond.compareToVariable = buf.readBoolean();
            cond.compareVariableName = buf.readUtf();
            cond.filter = new LogisticsFilter();
            cond.filter.matchType = buf.readUtf();
            int vSize = Math.min(buf.readInt(), 100);
            for (int j = 0; j < vSize; j++) cond.filter.matchValues.add(buf.readUtf());
            if (buf.readBoolean()) cond.filter.nbtSample = buf.readNbt();
            cond.filter.blacklist = buf.readBoolean();
            cond.filter.fuzzyNbt = buf.readBoolean();
            rule.conditions.add(cond);
        }
        rule.lastReport = buf.readUtf();
        rule.filter = new LogisticsFilter();
        rule.filter.matchType = buf.readUtf();
        int valSize = Math.min(buf.readInt(), 100);
        for (int i = 0; i < valSize; i++) rule.filter.matchValues.add(buf.readUtf());
        if (buf.readBoolean()) rule.filter.nbtSample = buf.readNbt();
        rule.filter.blacklist = buf.readBoolean();
        rule.filter.fuzzyNbt = buf.readBoolean();
        return rule;
    }

    public static void writeRule(net.minecraft.network.FriendlyByteBuf buf, LogisticsRule rule) {
        buf.writeUUID(rule.ruleId);
        buf.writeUUID(rule.sourceNodeId != null ? rule.sourceNodeId : java.util.UUID.randomUUID());
        buf.writeBoolean(rule.sourceIsGroup);
        buf.writeUUID(rule.destNodeId != null ? rule.destNodeId : java.util.UUID.randomUUID());
        buf.writeBoolean(rule.destIsGroup);
        buf.writeUtf(rule.sourceSide != null ? rule.sourceSide : "AUTO");
        buf.writeUtf(rule.destSide != null ? rule.destSide : "AUTO");
        buf.writeUtf(rule.mode != null ? rule.mode : "MOVE");
        buf.writeUtf(rule.distributionMode != null ? rule.distributionMode : "ROUND_ROBIN");
        buf.writeInt(rule.amountPerTick);
        buf.writeInt(rule.minAmount);
        buf.writeInt(rule.maxAmount);
        buf.writeInt(rule.priority);
        buf.writeUtf(rule.speedMode != null ? rule.speedMode : "NORMAL");
        buf.writeUtf(rule.type != null ? rule.type : "ITEMS");
        buf.writeUtf(rule.ruleAction != null ? rule.ruleAction : "MOVE");
        buf.writeUtf(rule.triggerType != null ? rule.triggerType : "ALWAYS");
        buf.writeUtf(rule.variableName != null ? rule.variableName : "");
        buf.writeUtf(rule.variableOp != null ? rule.variableOp : "SET");
        buf.writeUtf(rule.secondaryVariableName != null ? rule.secondaryVariableName : "");
        buf.writeDouble(rule.constantValue);
        buf.writeUtf(rule.signalFilter != null ? rule.signalFilter : "");
        buf.writeBoolean(rule.triggerNodeId != null);
        if (rule.triggerNodeId != null) buf.writeUUID(rule.triggerNodeId);
        buf.writeUtf(rule.emitSignalOnSuccess != null ? rule.emitSignalOnSuccess : "");
        buf.writeBoolean(rule.scanItems);

        buf.writeInt(rule.sourceSlots.size());
        for (int s : rule.sourceSlots) buf.writeInt(s);
        buf.writeInt(rule.destSlots.size());
        for (int s : rule.destSlots) buf.writeInt(s);
        buf.writeBoolean(rule.active);
        buf.writeBoolean(rule.maintenanceMode);
        buf.writeInt(rule.conditions.size());
        for (LogicCondition cond : rule.conditions) {
            buf.writeBoolean(cond.targetId != null);
            if (cond.targetId != null) buf.writeUUID(cond.targetId);
            buf.writeBoolean(cond.isGroup);
            buf.writeUtf(cond.type != null ? cond.type : "ITEMS");
            buf.writeUtf(cond.operator != null ? cond.operator : "LESS");
            buf.writeInt(cond.value);
            buf.writeUtf(cond.variableName != null ? cond.variableName : "");
            buf.writeBoolean(cond.compareToVariable);
            buf.writeUtf(cond.compareVariableName != null ? cond.compareVariableName : "");
            buf.writeUtf(cond.filter.matchType != null ? cond.filter.matchType : "ALL");
            buf.writeInt(cond.filter.matchValues != null ? cond.filter.matchValues.size() : 0);
            if (cond.filter.matchValues != null) {
                for (String s : cond.filter.matchValues) buf.writeUtf(s);
            }
            buf.writeBoolean(cond.filter.nbtSample != null);
            if (cond.filter.nbtSample != null) buf.writeNbt(cond.filter.nbtSample);
            buf.writeBoolean(cond.filter.blacklist);
            buf.writeBoolean(cond.filter.fuzzyNbt);
        }
        buf.writeUtf(rule.lastReport != null ? rule.lastReport : "");
        buf.writeUtf(rule.filter.matchType != null ? rule.filter.matchType : "ALL");
        buf.writeInt(rule.filter.matchValues != null ? rule.filter.matchValues.size() : 0);
        if (rule.filter.matchValues != null) {
            for (String s : rule.filter.matchValues) buf.writeUtf(s);
        }
        buf.writeBoolean(rule.filter.nbtSample != null);
        if (rule.filter.nbtSample != null) buf.writeNbt(rule.filter.nbtSample);
        buf.writeBoolean(rule.filter.blacklist);
        buf.writeBoolean(rule.filter.fuzzyNbt);
    }

    public static java.util.List<net.minecraft.world.item.ItemStack> getItemsFromNode(ServerPlayer player, NetworkNode node) {
        java.util.List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
        if (node.nodeType.equals("BLOCK")) {
            if (node.pos != null && node.dimension != null) {
                net.minecraft.resources.ResourceLocation dimLoc = net.minecraft.resources.ResourceLocation.tryParse(node.dimension);
                if (dimLoc != null) {
                    ServerLevel level = player.serverLevel().getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc));
                    if (level != null && canPlayerAccess(player, level, node.pos)) {
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(node.pos);
                        if (be != null) {
                            be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                                for (int i = 0; i < handler.getSlots(); i++) {
                                    net.minecraft.world.item.ItemStack s = handler.getStackInSlot(i);
                                    if (!s.isEmpty()) items.add(s.copy());
                                }
                            });
                        }
                    }
                }
            }
        } else if (node.nodeType.equals("PLAYER")) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty()) items.add(s.copy());
            }
        } else if (node.nodeType.equals("CHAMBER")) {
            com.example.modmenu.store.StorePriceManager.SkillData skillData = com.example.modmenu.store.StorePriceManager.getSkills(player.getUUID());
            if (node.chamberIndex >= 0 && node.chamberIndex < skillData.chambers.size()) {
                com.example.modmenu.store.StorePriceManager.ChamberData chamber = skillData.chambers.get(node.chamberIndex);
                for (net.minecraft.world.item.ItemStack s : chamber.storedLoot) {
                    if (!s.isEmpty()) items.add(s.copy());
                }
            }
        }
        return items;
    }

    public static NetworkNode findNode(NetworkData network, java.util.UUID nodeId) {
        if (network == null || nodeId == null) return null;
        if (network.nodeMap != null) return network.nodeMap.get(nodeId);
        for (NetworkNode node : network.nodes) {
            if (node.nodeId.equals(nodeId)) return node;
        }
        return null;
    }

    public static boolean isPermanent(NetworkData network, java.util.UUID id, boolean isGroup) {
        if (id == null) return false;
        if (isGroup) return false;
        NetworkNode node = findNode(network, id);
        return node != null && !node.nodeType.equals("BLOCK");
    }
}
