package com.example.modmenu.store.logistics;

import com.example.modmenu.store.SkillManager;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.PlayerArmorInvWrapper;
import net.minecraftforge.items.wrapper.PlayerOffhandInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "modmenu")
public class NetworkTickHandler {

    private static final Map<UUID, Integer> networkRuleIndex = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ruleCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ruleSuccessStreak = new ConcurrentHashMap<>();

    private static class TickContext {
        final Map<ResourceKey<Level>, Map<BlockPos, Map<Direction, Map<String, Object>>>> capabilityCache = new HashMap<>();
        final Set<UUID> rulesInChain = new HashSet<>();

        @SuppressWarnings("unchecked")
        <T> T getCache(Level level, BlockPos pos, Direction side, String type) {
            return (T) capabilityCache.getOrDefault(level.dimension(), Collections.emptyMap())
                    .getOrDefault(pos, Collections.emptyMap())
                    .getOrDefault(side, Collections.emptyMap())
                    .get(type);
        }

        void putCache(Level level, BlockPos pos, Direction side, String type, Object cap) {
            capabilityCache.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                    .computeIfAbsent(pos, k -> new HashMap<>())
                    .computeIfAbsent(side, k -> new HashMap<>())
                    .put(type, cap);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getCapability(LogisticsCapability.PLAYER_NETWORKS).ifPresent(data -> {
                List<NetworkData> activeNetworks = data.getNetworks().stream()
                        .filter(n -> n.active && !n.rules.isEmpty())
                        .toList();
                
                if (activeNetworks.isEmpty()) return;

                int globalBudget = 10000;
                int processedThisTick = 0;

                for (NetworkData network : activeNetworks) {
                    if (processedThisTick >= globalBudget) break;
                    
                    long now = server.getTickCount();
                    if (now - network.lastStatsReset > 1200) {
                        network.itemsMovedLastMin = network.itemsMovedThisMin;
                        network.energyMovedLastMin = network.energyMovedThisMin;
                        network.fluidsMovedLastMin = network.fluidsMovedThisMin;
                        network.itemsMovedThisMin = 0;
                        network.energyMovedThisMin = 0;
                        network.fluidsMovedThisMin = 0;
                        network.lastStatsReset = now;
                    }

                    TickContext ctx = new TickContext();
                    processedThisTick += tickNetwork(player, network, globalBudget - processedThisTick, ctx);
                }
            });
        }
    }

    private static int tickNetwork(ServerPlayer player, NetworkData network, int remainingBudget, TickContext ctx) {
        int ruleParallelism = 1000;
        int networkBudget = Math.min(network.tickBudget + ruleParallelism, remainingBudget);
        if (networkBudget <= 0) return 0;

        if (player.level().getGameTime() % 20 == 0) {
            for (NetworkNode node : network.nodes) validateNode(player, node);
        }

        int startIndex = networkRuleIndex.getOrDefault(network.networkId, 0);
        double budgetUsed = 0;
        int totalRules = network.rules.size();

        List<LogisticsRule> sortedRules = new ArrayList<>(network.rules);
        sortedRules.sort((a, b) -> Integer.compare(b.priority, a.priority));

        int i = 0;
        for (; i < totalRules && budgetUsed < networkBudget; i++) {
            int currentIndex = (startIndex + i) % totalRules;
            LogisticsRule rule = sortedRules.get(currentIndex);

            if (rule.active) {
                long now = player.level().getGameTime();
                if (ruleCooldowns.getOrDefault(rule.ruleId, 0L) > now) continue;

                boolean moved = processRule(player, network, rule, false, ctx, 0);
                
                double cost = 0.01;
                budgetUsed += cost;

                if (moved) {
                    long cooldown = switch(rule.speedMode) {
                        case "SLOW" -> 5;
                        case "NORMAL" -> 2;
                        default -> 0;
                    };
                    if (cooldown > 0) ruleCooldowns.put(rule.ruleId, now + cooldown);
                }
                
                if (moved && rule.mode.equals("PRIORITY")) {
                    networkRuleIndex.put(network.networkId, currentIndex);
                    return (int)Math.ceil(budgetUsed);
                }
            }
        }

        networkRuleIndex.put(network.networkId, (startIndex + i) % totalRules);
        return (int)Math.ceil(budgetUsed);
    }

    public static boolean processRule(ServerPlayer player, NetworkData network, LogisticsRule rule, boolean skipCooldownCheck) {
        return processRule(player, network, rule, skipCooldownCheck, new TickContext(), 0);
    }

    public static boolean processRule(ServerPlayer player, NetworkData network, LogisticsRule rule, boolean skipCooldownCheck, TickContext ctx, int depth) {
        if (depth > 20) return false;
        long now = player.level().getGameTime();
        if (!skipCooldownCheck && ruleCooldowns.getOrDefault(rule.ruleId, 0L) > now) return false;

        List<NetworkNode> sources = new ArrayList<>();
        if (rule.sourceIsGroup) {
            NodeGroup group = findGroup(network, rule.sourceNodeId);
            if (group != null) {
                for (UUID id : group.nodeIds) {
                    NetworkNode n = findNode(network, id);
                    if (n != null) sources.add(n);
                }
            }
        } else {
            NetworkNode n = findNode(network, rule.sourceNodeId);
            if (n != null) sources.add(n);
        }

        List<NetworkNode> destinations = new ArrayList<>();
        if (rule.destIsGroup) {
            NodeGroup group = findGroup(network, rule.destNodeId);
            if (group != null) {
                for (UUID id : group.nodeIds) {
                    NetworkNode n = findNode(network, id);
                    if (n != null) destinations.add(n);
                }
            }
        } else {
            NetworkNode n = findNode(network, rule.destNodeId);
            if (n != null) destinations.add(n);
        }

        if (sources.isEmpty() || destinations.isEmpty()) {
            rule.lastReport = "Nodes missing";
            ruleCooldowns.put(rule.ruleId, now + 2);
            return false;
        }

        int maxToMove = rule.amountPerTick == -1 || rule.speedMode.equals("INSTANT") ? Integer.MAX_VALUE : rule.amountPerTick;
        if (rule.type.equals("ENERGY")) {
            if (rule.speedMode.equals("HYPER")) maxToMove *= 100;
        } else if (rule.type.equals("FLUIDS")) {
            if (rule.speedMode.equals("HYPER")) maxToMove *= 100;
        } else {
            if (rule.speedMode.equals("HYPER")) maxToMove *= 10;
        }

        int totalMovedCount = 0;
        boolean movedAnything = false;

        outer: for (NetworkNode sourceNode : sources) {
            for (NetworkNode destNode : destinations) {
                if (sourceNode == destNode) continue;

                int currentLimit = maxToMove - totalMovedCount;
                if (currentLimit <= 0) break outer;

                int moved = 0;
                if (rule.type.equals("ENERGY")) {
                    moved = processEnergyRule(player, network, rule, sourceNode, destNode, ctx, currentLimit);
                } else if (rule.type.equals("FLUIDS")) {
                    moved = processFluidRule(player, network, rule, sourceNode, destNode, ctx, currentLimit);
                } else {
                    moved = processItemRule(player, network, rule, sourceNode, destNode, ctx, currentLimit);
                }

                if (moved > 0) {
                    totalMovedCount += moved;
                    movedAnything = true;
                }
            }
        }

        if (movedAnything) {
            ruleSuccessStreak.put(rule.ruleId, ruleSuccessStreak.getOrDefault(rule.ruleId, 0) + 1);
            for (LogisticsRule r : network.rules) {
                boolean isNextInChain = false;
                if (rule.destIsGroup) {
                    NodeGroup group = findGroup(network, rule.destNodeId);
                    if (group != null && group.nodeIds.contains(r.sourceNodeId)) isNextInChain = true;
                } else {
                    if (r.sourceNodeId.equals(rule.destNodeId)) isNextInChain = true;
                }

                if (r.active && isNextInChain && !ctx.rulesInChain.contains(r.ruleId)) {
                    ctx.rulesInChain.add(r.ruleId);
                    processRule(player, network, r, true, ctx, depth + 1);
                }
            }
        } else {
            ruleSuccessStreak.put(rule.ruleId, -1);
            long wait = depth > 0 ? 0 : 1;
            ruleCooldowns.put(rule.ruleId, now + wait);
            if (rule.lastReport.isEmpty() || rule.lastReport.startsWith("Moved")) {
                rule.lastReport = "No Transfer";
            }
        }

        return movedAnything;
    }

    private static int processItemRule(ServerPlayer player, NetworkData network, LogisticsRule rule, NetworkNode sourceNode, NetworkNode destNode, TickContext ctx, int amountLimit) {
        IItemHandler sourceHandler = resolveItemHandler(player, sourceNode, rule.sourceSide, true, ctx);
        IItemHandler destHandler = resolveItemHandler(player, destNode, rule.destSide, false, ctx);

        if (sourceHandler == null || destHandler == null) {
            rule.lastReport = "No Item Cap";
            return 0;
        }

        int amountToMove = amountLimit;

        if (rule.minAmount > 0) {
            int currentInSource = countItems(sourceHandler, rule.filter, sourceNode, rule.sourceSlots);
            if (currentInSource < rule.minAmount) {
                rule.lastReport = "Source < Min";
                return 0;
            }
        }

        if (rule.maxAmount != Integer.MAX_VALUE) {
            int currentInDest = countItems(destHandler, rule.filter, destNode, rule.destSlots);
            if (currentInDest >= rule.maxAmount) {
                rule.lastReport = "Dest @ Max";
                return 0;
            }
            amountToMove = Math.min(amountToMove, rule.maxAmount - currentInDest);
        }

        if (amountToMove <= 0) return 0;

        int totalAccepted = 0;
        for (int slot = 0; slot < sourceHandler.getSlots() && amountToMove > 0; slot++) {
            if (!isSlotTargeted(slot, rule.sourceSlots)) continue;
            if (sourceNode.slotConfig.getOrDefault(slot, "BOTH").equals("IN") || sourceNode.slotConfig.getOrDefault(slot, "BOTH").equals("OFF")) continue;

            ItemStack stack = sourceHandler.getStackInSlot(slot);
            if (stack.isEmpty() || !matchesFilter(stack, rule.filter)) continue;

            ItemStack extracted = sourceHandler.extractItem(slot, amountToMove, true);
            if (extracted.isEmpty()) continue;

            int accepted = 0;
            for (int dSlot = 0; dSlot < destHandler.getSlots() && !extracted.isEmpty(); dSlot++) {
                if (!isSlotTargeted(dSlot, rule.destSlots)) continue;
                if (destNode.slotConfig.getOrDefault(dSlot, "BOTH").equals("OUT") || destNode.slotConfig.getOrDefault(dSlot, "BOTH").equals("OFF")) continue;
                
                ItemStack remainder = destHandler.insertItem(dSlot, extracted, true);
                int moveCount = extracted.getCount() - remainder.getCount();
                if (moveCount > 0) {
                    destHandler.insertItem(dSlot, sourceHandler.extractItem(slot, moveCount, false), false);
                    accepted += moveCount;
                    extracted.shrink(moveCount);
                }
            }

            if (accepted > 0) {
                amountToMove -= accepted;
                totalAccepted += accepted;
                network.itemsMovedThisMin += accepted;
                rule.lastReport = "Moved " + accepted + "x " + stack.getHoverName().getString();
                network.lastReport = rule.lastReport;
            }
        }
        return totalAccepted;
    }

    private static boolean isSlotTargeted(int slot, List<Integer> targetedSlots) {
        if (targetedSlots == null || targetedSlots.isEmpty() || targetedSlots.contains(-1)) return true;
        return targetedSlots.contains(slot);
    }

    private static NetworkNode findNode(NetworkData network, UUID nodeId) {
        for (NetworkNode node : network.nodes) {
            if (node.nodeId.equals(nodeId)) return node;
        }
        return null;
    }

    private static NodeGroup findGroup(NetworkData network, UUID groupId) {
        for (NodeGroup group : network.groups) {
            if (group.groupId.equals(groupId)) return group;
        }
        return null;
    }

    private static int countItems(IItemHandler handler, LogisticsFilter filter, NetworkNode node, List<Integer> specificSlots) {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!isSlotTargeted(i, specificSlots)) continue;
            if (node.slotConfig.getOrDefault(i, "BOTH").equals("OFF")) continue;
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && matchesFilter(stack, filter)) count += stack.getCount();
        }
        return count;
    }

    private static int processEnergyRule(ServerPlayer player, NetworkData network, LogisticsRule rule, NetworkNode srcNode, NetworkNode dstNode, TickContext ctx, int amountLimit) {
        int amountToMove = amountLimit;

        int totalExtracted = 0;

        List<IEnergyStorage> sources = resolveAllEnergyHandlers(player, srcNode, rule.sourceSide, rule.sourceSlots, ctx);
        List<IEnergyStorage> destinations = resolveAllEnergyHandlers(player, dstNode, rule.destSide, rule.destSlots, ctx);

        for (IEnergyStorage src : sources) {
            int toExtract = src.extractEnergy(amountToMove, true);
            if (toExtract <= 0) continue;

            for (IEnergyStorage dst : destinations) {
                if (src == dst) continue;
                int accepted = dst.receiveEnergy(toExtract, false);
                if (accepted > 0) {
                    src.extractEnergy(accepted, false);
                    totalExtracted += accepted;
                    amountToMove -= accepted;
                    toExtract -= accepted;
                    if (amountToMove <= 0 || toExtract <= 0) break;
                }
            }
            if (amountToMove <= 0) break;
        }

        if (totalExtracted > 0) {
            rule.lastReport = "Moved " + totalExtracted + " FE";
            network.lastReport = rule.lastReport;
            network.energyMovedThisMin += totalExtracted;
        }
        return totalExtracted;
    }

    private static List<IEnergyStorage> resolveAllEnergyHandlers(ServerPlayer player, NetworkNode node, String side, List<Integer> slots, TickContext ctx) {
        List<IEnergyStorage> list = new ArrayList<>();
        if (slots.contains(-1)) {
            IEnergyStorage blockCap = resolveEnergyHandler(player, node, side, -1, ctx);
            if (blockCap != null) list.add(blockCap);
            IItemHandler inv = resolveItemHandler(player, node, "AUTO", false, ctx);
            if (inv != null) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    inv.getStackInSlot(i).getCapability(ForgeCapabilities.ENERGY).ifPresent(list::add);
                }
            }
        } else {
            for (int s : slots) {
                IEnergyStorage cap = resolveEnergyHandler(player, node, side, s, ctx);
                if (cap != null) list.add(cap);
            }
        }
        return list;
    }

    private static int processFluidRule(ServerPlayer player, NetworkData network, LogisticsRule rule, NetworkNode srcNode, NetworkNode dstNode, TickContext ctx, int amountLimit) {
        int amountToMove = amountLimit;

        int totalMoved = 0;
        String fluidName = "Unknown";

        List<IFluidHandler> sources = resolveAllFluidHandlers(player, srcNode, rule.sourceSide, rule.sourceSlots, ctx);
        List<IFluidHandler> destinations = resolveAllFluidHandlers(player, dstNode, rule.destSide, rule.destSlots, ctx);

        for (IFluidHandler src : sources) {
            FluidStack drained = src.drain(amountToMove, IFluidHandler.FluidAction.SIMULATE);
            if (drained.isEmpty()) continue;

            int toMoveFromThisSrc = drained.getAmount();

            for (IFluidHandler dst : destinations) {
                if (src == dst) continue;
                FluidStack toFill = drained.copy();
                toFill.setAmount(toMoveFromThisSrc);
                int accepted = dst.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    FluidStack realDrained = src.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                    dst.fill(realDrained, IFluidHandler.FluidAction.EXECUTE);
                    
                    totalMoved += accepted;
                    amountToMove -= accepted;
                    toMoveFromThisSrc -= accepted;
                    fluidName = realDrained.getDisplayName().getString();
                    
                    if (amountToMove <= 0 || toMoveFromThisSrc <= 0) break;
                }
            }
            if (amountToMove <= 0) break;
        }

        if (totalMoved > 0) {
            rule.lastReport = "Moved " + totalMoved + "mB " + fluidName;
            network.lastReport = rule.lastReport;
            network.fluidsMovedThisMin += totalMoved;
        }
        return totalMoved;
    }

    private static List<IFluidHandler> resolveAllFluidHandlers(ServerPlayer player, NetworkNode node, String side, List<Integer> slots, TickContext ctx) {
        List<IFluidHandler> list = new ArrayList<>();
        if (slots.contains(-1)) {
            IFluidHandler blockCap = resolveFluidHandler(player, node, side, -1, ctx);
            if (blockCap != null) list.add(blockCap);
            IItemHandler inv = resolveItemHandler(player, node, "AUTO", false, ctx);
            if (inv != null) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    inv.getStackInSlot(i).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).ifPresent(list::add);
                }
            }
        } else {
            for (int s : slots) {
                IFluidHandler cap = resolveFluidHandler(player, node, side, s, ctx);
                if (cap != null) list.add(cap);
            }
        }
        return list;
    }

    private static Direction resolveDirection(NetworkNode node, String type) {
        for (java.util.Map.Entry<Direction, String> entry : node.sideConfig.entrySet()) {
            if (entry.getValue().equals(type)) return entry.getKey();
        }
        return null;
    }

    private static IEnergyStorage resolveEnergyHandler(ServerPlayer player, NetworkNode node, String sideStr, int slotIdx, TickContext ctx) {
        if (slotIdx != -1) {
            IItemHandler inv = resolveItemHandler(player, node, "AUTO", false, ctx);
            if (inv != null && slotIdx < inv.getSlots()) {
                ItemStack stack = inv.getStackInSlot(slotIdx);
                if (!stack.isEmpty()) {
                    IEnergyStorage itemCap = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
                    if (itemCap != null) return itemCap;
                }
            }
        }

        if (!node.nodeType.equals("BLOCK")) return null;
        Direction side = sideStr.equals("AUTO") ? resolveDirection(node, "ENERGY") : Direction.byName(sideStr.toLowerCase());
        
        IEnergyStorage cached = ctx.getCache(player.level(), node.pos, side, "ENERGY");
        if (cached != null) return cached;

        ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
        if (dimLoc == null) return null;
        ServerLevel level = player.serverLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
        if (level == null || !level.hasChunkAt(node.pos)) return null;
        
        BlockEntity be = level.getBlockEntity(node.pos);
        if (be == null) return null;
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, side).orElse(null); 
        if (cap == null && sideStr.equals("AUTO")) { 
            for (Direction d : Direction.values()) { 
                if (d == side) continue; 
                cap = be.getCapability(ForgeCapabilities.ENERGY, d).orElse(null); 
                if (cap != null) break; 
            } 
        } 
        if (cap != null) ctx.putCache(level, node.pos, side, "ENERGY", cap);
        return cap;
    }

    private static IFluidHandler resolveFluidHandler(ServerPlayer player, NetworkNode node, String sideStr, int slotIdx, TickContext ctx) {
        if (slotIdx != -1) {
            IItemHandler inv = resolveItemHandler(player, node, "AUTO", false, ctx);
            if (inv != null && slotIdx < inv.getSlots()) {
                ItemStack stack = inv.getStackInSlot(slotIdx);
                if (!stack.isEmpty()) {
                    IFluidHandler itemCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
                    if (itemCap != null) return itemCap;
                }
            }
        }

        if (!node.nodeType.equals("BLOCK")) return null;
        Direction side = sideStr.equals("AUTO") ? resolveDirection(node, "FLUIDS") : Direction.byName(sideStr.toLowerCase());
        
        IFluidHandler cached = ctx.getCache(player.level(), node.pos, side, "FLUIDS");
        if (cached != null) return cached;

        ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
        if (dimLoc == null) return null;
        ServerLevel level = player.serverLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
        if (level == null || !level.hasChunkAt(node.pos)) return null;
        
        BlockEntity be = level.getBlockEntity(node.pos);
        if (be == null) return null;
        IFluidHandler cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null); 
        if (cap == null && sideStr.equals("AUTO")) { 
            for (Direction d : Direction.values()) { 
                if (d == side) continue; 
                cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, d).orElse(null); 
                if (cap != null) break; 
            } 
        } 
        if (cap != null) ctx.putCache(level, node.pos, side, "FLUIDS", cap);
        return cap;
    }

    private static IItemHandler resolveItemHandler(ServerPlayer player, NetworkNode node, String sideStr, boolean isSource, TickContext ctx) {
        Direction side = sideStr.equals("AUTO") ? resolveDirection(node, "ITEMS") : Direction.byName(sideStr.toLowerCase());

        if (node.nodeType.equals("BLOCK") && node.pos != null) {
            IItemHandler cached = ctx.getCache(player.level(), node.pos, side, "ITEMS");
            if (cached != null) return cached;
        }

        switch (node.nodeType) {
            case "BLOCK" -> {
                if (node.pos == null || node.dimension == null) return null;
                
                ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
                if (dimLoc == null) return null;
                
                ServerLevel level = player.serverLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
                if (level == null || !level.hasChunkAt(node.pos)) return null;
                
                BlockEntity be = level.getBlockEntity(node.pos);
                if (be == null) return null;
                IItemHandler cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null); 
                if (cap != null) ctx.putCache(level, node.pos, side, "ITEMS", cap);
                return cap;
            }
            case "PLAYER" -> {
                return new PlayerMainInvWrapper(player.getInventory());
            }
            case "CHAMBER" -> {
                if (node.chamberIndex < 0) return null;
                StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
                if (node.chamberIndex >= skillData.chambers.size()) return null;
                
                StorePriceManager.ChamberData chamber = skillData.chambers.get(node.chamberIndex);
                return new ChamberItemHandler(chamber, isSource);
            }
            case "MARKET" -> {
                if (isSource) return null;
                return new MarketItemHandler(player.getUUID());
            }
        }
        return null;
    }

    private static boolean matchesFilter(ItemStack stack, LogisticsFilter filter) {
        if (filter.matchType.equals("ALL")) return !filter.blacklist;

        boolean match = false;
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();

        for (String finalVal : filter.matchValues) {
            if (finalVal.isEmpty()) continue;

            boolean currentMatch = false;
            switch (filter.matchType) {
                case "ID" -> {
                    if (finalVal.contains("*")) {
                        try {
                            String regex = finalVal.replace(".", "\\.").replace("*", ".*");
                            currentMatch = itemId.matches(regex);
                        } catch (Exception e) {
                            currentMatch = itemId.equals(finalVal);
                        }
                    } else {
                        currentMatch = itemId.equals(finalVal);
                    }
                }
                case "TAG" -> currentMatch = stack.getTags().anyMatch(t -> t.location().toString().equals(finalVal));
                case "NBT" -> {
                    boolean idMatch = itemId.equals(finalVal);
                    boolean nbtMatch = false;
                    if (filter.nbtSample == null) nbtMatch = !stack.hasTag();
                    else if (stack.hasTag()) {
                        if (filter.fuzzyNbt) {
                            nbtMatch = true;
                            for (String key : filter.nbtSample.getAllKeys()) {
                                if (!net.minecraft.nbt.NbtUtils.compareNbt(filter.nbtSample.get(key), stack.getTag().get(key), true)) {
                                    nbtMatch = false;
                                    break;
                                }
                            }
                        } else {
                            nbtMatch = net.minecraft.nbt.NbtUtils.compareNbt(filter.nbtSample, stack.getTag(), true);
                        }
                    }
                    currentMatch = idMatch && nbtMatch;
                }
            }
            if (currentMatch) {
                match = true;
                break;
            }
        }

        return filter.blacklist != match;
    }

    private static void validateNode(ServerPlayer player, NetworkNode node) {
        if (!node.nodeType.equals("BLOCK")) {
            node.isMissing = false;
            return;
        }
        
        if (node.pos == null || node.dimension == null) {
            node.isMissing = true;
            return;
        }

        ResourceLocation dimLoc = ResourceLocation.tryParse(node.dimension);
        if (dimLoc == null) {
            node.isMissing = true;
            return;
        }

        ServerLevel level = player.serverLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
        if (level == null) {
            node.isMissing = true;
            return;
        }

        if (!level.hasChunkAt(node.pos)) {
            return; 
        }

        BlockEntity be = level.getBlockEntity(node.pos);
        if (be == null) {
            node.isMissing = true;
            return;
        }

        if (node.blockId != null) {
            String currentId = ForgeRegistries.BLOCKS.getKey(level.getBlockState(node.pos).getBlock()).toString();
            if (!currentId.equals(node.blockId)) {
                node.isMissing = true;
                return;
            }
        }

        node.isMissing = false;
    }

    private static class ChamberItemHandler implements IItemHandler {
        private final StorePriceManager.ChamberData chamber;
        private final boolean isSource;

        public ChamberItemHandler(StorePriceManager.ChamberData chamber, boolean isSource) {
            this.chamber = chamber;
            this.isSource = isSource;
        }

        @Override public int getSlots() { return isSource ? chamber.storedLoot.size() : 1; }
        
        @Override 
        public ItemStack getStackInSlot(int slot) { 
            if (!isSource) return ItemStack.EMPTY;
            synchronized(chamber.storedLoot) {
                return slot < chamber.storedLoot.size() ? chamber.storedLoot.get(slot) : ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (isSource) return stack;
            if (!chamber.barteringMode || !chamber.mobId.contains("piglin")) return stack;
            
            if (!simulate) {
                boolean merged = false;
                for (ItemStack existing : chamber.inputBuffer) {
                    if (ItemStack.isSameItemSameTags(existing, stack)) {
                        existing.grow(stack.getCount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) chamber.inputBuffer.add(stack.copy());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isSource) return ItemStack.EMPTY;
            synchronized (chamber.storedLoot) {
                if (slot >= chamber.storedLoot.size()) return ItemStack.EMPTY;
                ItemStack existing = chamber.storedLoot.get(slot);
                int toExtract = Math.min(amount, existing.getCount());
                ItemStack result = existing.copy();
                result.setCount(toExtract);
                if (!simulate) {
                    existing.shrink(toExtract);
                    if (existing.isEmpty()) chamber.storedLoot.remove(slot);
                    chamber.updateVersion++;
                }
                return result;
            }
        }

        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return !isSource; }
    }

    private static class MarketItemHandler implements IItemHandler {
        private final UUID playerUuid;

        public MarketItemHandler(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            BigDecimal price = StorePriceManager.getSellPrice(stack.getItem(), playerUuid);
            if (price.compareTo(BigDecimal.ZERO) <= 0) return stack;

            if (!simulate) {
                BigDecimal gain = price.multiply(BigDecimal.valueOf(stack.getCount()));
                StorePriceManager.addMoney(playerUuid, gain);
                StorePriceManager.recordSale(stack.getItem(), BigDecimal.valueOf(stack.getCount()));
            }
            return ItemStack.EMPTY;
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }
}