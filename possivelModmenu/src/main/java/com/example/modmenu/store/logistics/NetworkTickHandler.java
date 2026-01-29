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
    private static final Map<UUID, Integer> groupDistributionIndex = new ConcurrentHashMap<>();
    private static final Object NULL_CAP = new Object();
    private static final UUID BUFFER_NODE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRASH_NODE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static class TickContext {
        final Map<ResourceKey<Level>, Map<BlockPos, Map<Direction, Map<String, Object>>>> capabilityCache = new HashMap<>();
        final Set<UUID> rulesInChain = new HashSet<>();
        double budgetUsed = 0;

        boolean isCached(Level level, BlockPos pos, Direction side, String type) {
            Map<String, Object> sideMap = capabilityCache.getOrDefault(level.dimension(), Collections.emptyMap())
                    .getOrDefault(pos, Collections.emptyMap())
                    .get(side);
            return sideMap != null && sideMap.containsKey(type);
        }

        @SuppressWarnings("unchecked")
        <T> T getCache(Level level, BlockPos pos, Direction side, String type) {
            Map<String, Object> sideMap = capabilityCache.getOrDefault(level.dimension(), Collections.emptyMap())
                    .getOrDefault(pos, Collections.emptyMap())
                    .get(side);
            if (sideMap == null) return null;
            Object cached = sideMap.get(type);
            return cached == NULL_CAP ? null : (T) cached;
        }

        void putCache(Level level, BlockPos pos, Direction side, String type, Object cap) {
            capabilityCache.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                    .computeIfAbsent(pos, k -> new HashMap<>())
                    .computeIfAbsent(side, k -> new HashMap<>())
                    .put(type, cap == null ? NULL_CAP : cap);
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

    private static void recordMovement(NetworkData network, String itemId, String itemName, int count, UUID src, UUID dst, String type) {
        if (network.movementHistory == null) network.movementHistory = new ArrayList<>();
        network.movementHistory.add(0, new MovementRecord(System.currentTimeMillis(), itemId, itemName, count, src, dst, type));
        while (network.movementHistory.size() > 50) {
            network.movementHistory.remove(network.movementHistory.size() - 1);
        }
    }

    public static void fireSignal(NetworkData network, String type, UUID srcNode, UUID srcRule) {
        if (network.pendingSignals == null) network.pendingSignals = new ArrayList<>();
        network.pendingSignals.add(new LogisticsSignal(type, srcNode, srcRule));
    }

    private static void rebuildSubscriptions(NetworkData network) {
        network.signalSubscriptions = new HashMap<>();
        for (LogisticsRule rule : network.rules) {
            if (rule.active && "SIGNAL".equals(rule.triggerType) && rule.signalFilter != null && !rule.signalFilter.isEmpty()) {
                network.signalSubscriptions.computeIfAbsent(rule.signalFilter, k -> new ArrayList<>()).add(rule.ruleId);
            }
        }
    }

    private static double processSignal(ServerPlayer player, NetworkData network, LogisticsSignal signal, double budget, TickContext ctx) {
        if (signal.type == null || signal.type.isEmpty()) return 0;
        if (network.signalSubscriptions == null) rebuildSubscriptions(network);
        List<UUID> ruleIds = network.signalSubscriptions.get(signal.type);
        if (ruleIds == null) return 0;

        double used = 0;
        for (UUID ruleId : ruleIds) {
            if (used >= budget) break;
            LogisticsRule rule = findRule(network, ruleId);
            if (rule == null || !rule.active) continue;
            
            if (rule.triggerNodeId != null && !rule.triggerNodeId.equals(signal.sourceNodeId)) continue;

            ctx.budgetUsed = 0;
            processRule(player, network, rule, true, ctx, 0);
            used += ctx.budgetUsed;
        }
        return used;
    }

    private static LogisticsRule findRule(NetworkData network, UUID ruleId) {
        for (LogisticsRule rule : network.rules) {
            if (rule.ruleId.equals(ruleId)) return rule;
        }
        return null;
    }

    private static int tickNetwork(ServerPlayer player, NetworkData network, int remainingBudget, TickContext ctx) {
        int ruleParallelism = 1000;
        int networkBudget = Math.min(network.tickBudget + ruleParallelism, remainingBudget);
        if (networkBudget <= 0) return 0;

        if (network.nodeMap == null) {
            network.nodeMap = new HashMap<>();
            for (NetworkNode node : network.nodes) {
                if (node != null) network.nodeMap.put(node.nodeId, node);
            }
        }
        if (network.groupMap == null) {
            network.groupMap = new HashMap<>();
            for (NodeGroup group : network.groups) {
                if (group != null) network.groupMap.put(group.groupId, group);
            }
        }
        if (network.sortedRules == null || network.needsSorting) {
            network.sortedRules = new ArrayList<>();
            for (LogisticsRule rule : network.rules) {
                if (rule != null) network.sortedRules.add(rule);
            }
            network.sortedRules.sort((a, b) -> Integer.compare(b.priority, a.priority));
            network.needsSorting = false;
            network.signalSubscriptions = null; // Force rebuild
        }

        if (player.level().getGameTime() % 100 == 0) {
            for (NetworkNode node : network.nodes) {
                if (node != null) validateNode(player, node);
            }
        }

        double budgetUsed = 0;

        // Phase 2: Signal Processing
        if (network.pendingSignals != null && !network.pendingSignals.isEmpty()) {
            List<LogisticsSignal> signalsToProcess = new ArrayList<>(network.pendingSignals);
            
            // Phase 5: Maintain recent signal history for client visualization
            network.recentSignals.addAll(signalsToProcess);
            while (network.recentSignals.size() > 50) network.recentSignals.remove(0);

            network.pendingSignals.clear();

            for (LogisticsSignal signal : signalsToProcess) {
                if (budgetUsed >= networkBudget) break;
                budgetUsed += processSignal(player, network, signal, networkBudget - budgetUsed, ctx);
            }
        }

        int startIndex = networkRuleIndex.getOrDefault(network.networkId, 0);
        int totalRules = network.sortedRules.size();

        int i = 0;
        for (; i < totalRules && budgetUsed < networkBudget; i++) {
            int currentIndex = (startIndex + i) % totalRules;
            LogisticsRule rule = network.sortedRules.get(currentIndex);

            if (rule.active && "ALWAYS".equals(rule.triggerType)) {
                long now = player.level().getGameTime();
                if (ruleCooldowns.getOrDefault(rule.ruleId, 0L) > now) continue;

                ctx.budgetUsed = 0;
                boolean moved = processRule(player, network, rule, false, ctx, 0);
                budgetUsed += ctx.budgetUsed;

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

    private static void finishRule(NetworkData network, LogisticsRule rule, boolean movedAnything, TickContext ctx, ServerPlayer player, long now, int depth) {
        if (movedAnything) {
            ruleSuccessStreak.put(rule.ruleId, ruleSuccessStreak.getOrDefault(rule.ruleId, 0) + 1);
            
            // Phase 2: Signal Emission
            if (rule.emitSignalOnSuccess != null && !rule.emitSignalOnSuccess.isEmpty()) {
                fireSignal(network, rule.emitSignalOnSuccess, rule.sourceNodeId, rule.ruleId);
            }
            
            // Legacy/Smart Chaining
            for (LogisticsRule r : network.rules) {
                boolean isNextInChain = false;
                if (rule.destIsGroup) {
                    NodeGroup group = findGroup(network, rule.destNodeId);
                    if (group != null && group.nodeIds.contains(r.sourceNodeId)) isNextInChain = true;
                } else if (rule.destNodeId != null) {
                    if (rule.destNodeId.equals(r.sourceNodeId)) isNextInChain = true;
                }

                if (depth < 10 && r.active && isNextInChain && !ctx.rulesInChain.contains(r.ruleId) && "ALWAYS".equals(r.triggerType)) {
                    ctx.rulesInChain.add(r.ruleId);
                    processRule(player, network, r, true, ctx, depth + 1);
                }
            }
        } else {
            // Auto-Healing: Try overflow redirection
            if (network.overflowTargetId != null && !rule.ruleAction.equals("SET_VARIABLE") && !rule.ruleAction.equals("MATH")) {
                handleOverflow(player, network, rule, ctx, now);
            }

            if (rule.lastReport.isEmpty() || rule.lastReport.contains("Moved")) {
                rule.lastReport = "[SEARCH] No Transfer";
            }
            ruleSuccessStreak.put(rule.ruleId, -1);
            long wait = depth > 0 ? 0 : 1;
            ruleCooldowns.put(rule.ruleId, now + wait);
        }
    }

    private static void handleOverflow(ServerPlayer player, NetworkData network, LogisticsRule rule, TickContext ctx, long now) {
        // Implementation moved from processRule
        List<NetworkNode> sources = resolveSources(network, rule);
        List<NetworkNode> overflowDestinations = resolveOverflowDestinations(network);

        if (!overflowDestinations.isEmpty() && !sources.isEmpty()) {
            boolean movedAnything = false;
            int maxToMove = calculateMaxToMove(rule);
            
            for (NetworkNode sourceNode : sources) {
                for (NetworkNode destNode : overflowDestinations) {
                    if (sourceNode.nodeId.equals(destNode.nodeId)) continue;
                    int moved = 0;
                    if (rule.type.equals("ENERGY")) {
                        moved = processEnergyRule(player, network, rule, sourceNode, destNode, ctx, maxToMove);
                    } else if (rule.type.equals("FLUIDS")) {
                        moved = processFluidRule(player, network, rule, sourceNode, destNode, ctx, maxToMove);
                    } else {
                        moved = processItemRule(player, network, rule, sourceNode, destNode, ctx, maxToMove);
                    }

                    if (moved > 0) {
                        movedAnything = true;
                        rule.lastReport = "[OVERFLOW] " + rule.lastReport.replace("[ACTIVE] ", "");
                        break;
                    }
                }
                if (movedAnything) break;
            }
        }
    }

    private static List<NetworkNode> resolveSources(NetworkData network, LogisticsRule rule) {
        List<NetworkNode> sources = new ArrayList<>();
        if (rule.ruleAction.equals("INSERT")) {
            NetworkNode bufferNode = new NetworkNode();
            bufferNode.nodeId = BUFFER_NODE_ID;
            bufferNode.nodeType = "BUFFER";
            sources.add(bufferNode);
        } else if (rule.sourceIsGroup) {
            NodeGroup group = findGroup(network, rule.sourceNodeId);
            if (group != null) {
                for (UUID id : group.nodeIds) {
                    NetworkNode n = LogisticsUtil.findNode(network, id);
                    if (n != null) sources.add(n);
                }
            }
        } else {
            NetworkNode n = LogisticsUtil.findNode(network, rule.sourceNodeId);
            if (n != null) sources.add(n);
        }
        return sources;
    }

    private static List<NetworkNode> resolveDestinations(NetworkData network, LogisticsRule rule) {
        List<NetworkNode> destinations = new ArrayList<>();
        if (rule.ruleAction.equals("EXTRACT")) {
            NetworkNode bufferNode = new NetworkNode();
            bufferNode.nodeId = BUFFER_NODE_ID;
            bufferNode.nodeType = "BUFFER";
            destinations.add(bufferNode);
        } else if (rule.destIsGroup) {
            NodeGroup group = findGroup(network, rule.destNodeId);
            if (group != null) {
                for (UUID id : group.nodeIds) {
                    NetworkNode n = LogisticsUtil.findNode(network, id);
                    if (n != null) destinations.add(n);
                }
            }
        } else {
            NetworkNode n = LogisticsUtil.findNode(network, rule.destNodeId);
            if (n != null) destinations.add(n);
        }
        return destinations;
    }

    private static List<NetworkNode> resolveOverflowDestinations(NetworkData network) {
        List<NetworkNode> overflowDestinations = new ArrayList<>();
        if (network.overflowTargetId != null) {
            if (network.overflowIsGroup) {
                NodeGroup group = findGroup(network, network.overflowTargetId);
                if (group != null) {
                    for (UUID id : group.nodeIds) {
                        NetworkNode n = LogisticsUtil.findNode(network, id);
                        if (n != null) overflowDestinations.add(n);
                    }
                }
            } else {
                NetworkNode n = LogisticsUtil.findNode(network, network.overflowTargetId);
                if (n != null) overflowDestinations.add(n);
            }
        }
        return overflowDestinations;
    }

    private static int calculateMaxToMove(LogisticsRule rule) {
        int maxToMove = rule.amountPerTick == -1 || rule.speedMode.equals("INSTANT") ? Integer.MAX_VALUE : rule.amountPerTick;
        if (rule.type.equals("ENERGY") || rule.type.equals("FLUIDS")) {
            if (rule.speedMode.equals("HYPER")) maxToMove *= 100;
        } else {
            if (rule.speedMode.equals("HYPER")) maxToMove *= 10;
        }
        return maxToMove;
    }

    public static boolean processRule(ServerPlayer player, NetworkData network, LogisticsRule rule, boolean skipCooldownCheck, TickContext ctx, int depth) {
        if (depth > 20) return false;
        ctx.budgetUsed += 1.0;
        long now = player.level().getGameTime();
        if (!skipCooldownCheck && ruleCooldowns.getOrDefault(rule.ruleId, 0L) > now) return false;

        // Phase 3: Condition Evaluation (Moved up for efficiency)
        if (!rule.conditions.isEmpty()) {
            if (!checkConditions(player, network, rule, ctx)) {
                rule.lastReport = "[BLOCKED] Conditions not met";
                ruleCooldowns.put(rule.ruleId, now + 5);
                return false;
            }
        }

        // Phase 4: Handle SET_VARIABLE and MATH
        if (rule.ruleAction.equals("SET_VARIABLE")) {
            double value = 0;
            if (rule.sourceNodeId != null) {
                NetworkNode src = LogisticsUtil.findNode(network, rule.sourceNodeId);
                if (src != null) value = getResourceCount(player, network, src, rule.type, rule.filter, ctx);
            } else {
                value = rule.constantValue;
            }
            network.variables.put(rule.variableName, value);
            rule.lastReport = "[ACTIVE] Var '" + rule.variableName + "' = " + String.format("%.2f", value);
            finishRule(network, rule, true, ctx, player, now, depth);
            return true;
        }

        if (rule.ruleAction.equals("MATH")) {
            double v1 = network.variables.getOrDefault(rule.variableName, 0.0);
            double v2 = rule.secondaryVariableName != null && !rule.secondaryVariableName.isEmpty() 
                    ? network.variables.getOrDefault(rule.secondaryVariableName, 0.0) 
                    : rule.constantValue;
            
            double result = switch (rule.variableOp) {
                case "ADD" -> v1 + v2;
                case "SUB" -> v1 - v2;
                case "MUL" -> v1 * v2;
                case "DIV" -> v2 != 0 ? v1 / v2 : 0;
                default -> v2; // SET
            };
            network.variables.put(rule.variableName, result);
            rule.lastReport = "[ACTIVE] Math result: " + String.format("%.2f", result);
            finishRule(network, rule, true, ctx, player, now, depth);
            return true;
        }

        List<NetworkNode> sources = resolveSources(network, rule);
        List<NetworkNode> destinations = resolveDestinations(network, rule);

        if (sources.isEmpty() || destinations.isEmpty()) {
            rule.lastReport = "[ERROR] Nodes missing";
            ruleCooldowns.put(rule.ruleId, now + 2);
            return false;
        }

        // Virtual Aggregated Inventory: Group-wide checks
        if (rule.sourceIsGroup && rule.minAmount > 0) {
            long total = 0;
            for (NetworkNode sn : sources) {
                total += getResourceCount(player, network, sn, rule.type, rule.filter, ctx);
            }
            if (total < rule.minAmount) {
                rule.lastReport = "[SEARCH] Group < Min (" + total + ")";
                ruleCooldowns.put(rule.ruleId, now + 5);
                return false;
            }
        }

        if (rule.destIsGroup && rule.maxAmount != Integer.MAX_VALUE) {
            long total = 0;
            for (NetworkNode dn : destinations) {
                total += getResourceCount(player, network, dn, rule.type, rule.filter, ctx);
            }
            if (total >= rule.maxAmount) {
                rule.lastReport = "[FULL] Group @ Max (" + total + ")";
                ruleCooldowns.put(rule.ruleId, now + 5);
                return false;
            }
        }

        int maxToMove = calculateMaxToMove(rule);
        int totalMovedCount = 0;
        boolean movedAnything = false;

        // Phase 3: Maintenance Mode - Strict limit
        if (rule.maintenanceMode && rule.maxAmount != Integer.MAX_VALUE) {
            long currentTotal = 0;
            for (NetworkNode dn : destinations) currentTotal += getResourceCount(player, network, dn, rule.type, rule.filter, ctx);
            if (currentTotal >= rule.maxAmount) {
                rule.lastReport = "[FULL] Stock OK (" + currentTotal + ")";
                ruleCooldowns.put(rule.ruleId, now + 20);
                return false;
            }
            maxToMove = (int) Math.min(maxToMove, rule.maxAmount - currentTotal);
        }

        List<NetworkNode> sortedDestinations = new ArrayList<>(destinations);
        if (rule.destIsGroup) {
            if (rule.distributionMode.equals("BALANCED")) {
                sortedDestinations.sort(Comparator.comparingDouble(n -> getFullness(player, network, n, rule.type, ctx)));
            } else if (rule.distributionMode.equals("ROUND_ROBIN")) {
                int startIdx = groupDistributionIndex.getOrDefault(rule.ruleId, 0) % destinations.size();
                sortedDestinations = new ArrayList<>();
                for (int i = 0; i < destinations.size(); i++) {
                    sortedDestinations.add(destinations.get((startIdx + i) % destinations.size()));
                }
            }
        }

        outer: for (NetworkNode sourceNode : sources) {
            for (int d = 0; d < sortedDestinations.size(); d++) {
                NetworkNode destNode = sortedDestinations.get(d);
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
                    
                    // Phase 2: Automatic Signals
                    String typePrefix = rule.type.equals("ITEMS") ? "ITEM" : (rule.type.equals("FLUIDS") ? "FLUID" : "ENERGY");
                    fireSignal(network, typePrefix + "_REMOVED", sourceNode.nodeId, rule.ruleId);
                    fireSignal(network, typePrefix + "_ADDED", destNode.nodeId, rule.ruleId);

                    if (rule.destIsGroup && rule.distributionMode.equals("ROUND_ROBIN")) {
                        int originalIdx = destinations.indexOf(destNode);
                        groupDistributionIndex.put(rule.ruleId, (originalIdx + 1) % destinations.size());
                    }
                }
            }
        }

        finishRule(network, rule, movedAnything, ctx, player, now, depth);
        return movedAnything;
    }

    private static int processItemRule(ServerPlayer player, NetworkData network, LogisticsRule rule, NetworkNode sourceNode, NetworkNode destNode, TickContext ctx, int amountLimit) {
        IItemHandler sourceHandler = resolveItemHandler(player, network, sourceNode, rule.sourceSide, true, ctx);
        IItemHandler destHandler = resolveItemHandler(player, network, destNode, rule.destSide, false, ctx);

        if (sourceHandler == null || destHandler == null) {
            rule.lastReport = "[ERROR] No Item Cap";
            return 0;
        }

        int amountToMove = amountLimit;

        if (!rule.sourceIsGroup && rule.minAmount > 0) {
            int currentInSource = countItems(sourceHandler, rule.filter, sourceNode, rule.sourceSlots);
            if (currentInSource < rule.minAmount) {
                rule.lastReport = "[SEARCH] Source < Min";
                return 0;
            }
        }

        if (!rule.destIsGroup && rule.maxAmount != Integer.MAX_VALUE) {
            int currentInDest = countItems(destHandler, rule.filter, destNode, rule.destSlots);
            if (currentInDest >= rule.maxAmount) {
                rule.lastReport = "[FULL] Dest @ Max";
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
                String speedNote = "";
                if (totalAccepted < amountLimit && totalAccepted > 0 && amountLimit != Integer.MAX_VALUE) {
                    speedNote = " (Limited by nodes)";
                }
                rule.lastReport = "[ACTIVE] Moved " + totalAccepted + "x " + stack.getHoverName().getString() + speedNote;
                network.lastReport = rule.lastReport;
                recordMovement(network, ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(), stack.getHoverName().getString(), accepted, sourceNode.nodeId, destNode.nodeId, "ITEMS");
            }
        }
        return totalAccepted;
    }

    private static boolean isSlotTargeted(int slot, List<Integer> targetedSlots) {
        if (targetedSlots == null || targetedSlots.isEmpty() || targetedSlots.contains(-1)) return true;
        return targetedSlots.contains(slot);
    }

    public static NetworkData findNetwork(ServerPlayer player, UUID networkId) {
        if (networkId == null) return null;
        return player.getCapability(LogisticsCapability.PLAYER_NETWORKS)
                .map(data -> data.getNetworks().stream()
                        .filter(n -> n.networkId.equals(networkId))
                        .findFirst().orElse(null))
                .orElse(null);
    }


    private static NodeGroup findGroup(NetworkData network, UUID groupId) {
        if (network.groupMap != null) return network.groupMap.get(groupId);
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

        List<IEnergyStorage> sources = resolveAllEnergyHandlers(player, network, srcNode, rule.sourceSide, rule.sourceSlots, ctx, rule.scanItems);
        List<IEnergyStorage> destinations = resolveAllEnergyHandlers(player, network, dstNode, rule.destSide, rule.destSlots, ctx, rule.scanItems);

        if (!rule.sourceIsGroup && rule.minAmount > 0) {
            int total = 0;
            for (IEnergyStorage s : sources) total += s.getEnergyStored();
            if (total < rule.minAmount) {
                rule.lastReport = "[SEARCH] Source < Min";
                return 0;
            }
        }

        if (!rule.destIsGroup && rule.maxAmount != Integer.MAX_VALUE) {
            int total = 0;
            for (IEnergyStorage d : destinations) total += d.getEnergyStored();
            if (total >= rule.maxAmount) {
                rule.lastReport = "[FULL] Dest @ Max";
                return 0;
            }
            amountToMove = Math.min(amountToMove, rule.maxAmount - total);
        }

        if (amountToMove <= 0) return 0;
        int totalExtracted = 0;

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
                    recordMovement(network, "energy", "Forge Energy", accepted, srcNode.nodeId, dstNode.nodeId, "ENERGY");
                    if (amountToMove <= 0 || toExtract <= 0) break;
                }
            }
            if (amountToMove <= 0) break;
        }

        if (totalExtracted > 0) {
            String speedNote = "";
            if (totalExtracted < amountLimit && amountLimit != Integer.MAX_VALUE) {
                speedNote = " (Limited by nodes)";
            }
            rule.lastReport = "[ACTIVE] Moved " + totalExtracted + " FE" + speedNote;
            network.lastReport = rule.lastReport;
            network.energyMovedThisMin += totalExtracted;
        }
        return totalExtracted;
    }

    private static List<IEnergyStorage> resolveAllEnergyHandlers(ServerPlayer player, NetworkData network, NetworkNode node, String side, List<Integer> slots, TickContext ctx, boolean scanItems) {
        List<IEnergyStorage> list = new ArrayList<>();
        if (slots.contains(-1)) {
            IEnergyStorage blockCap = resolveEnergyHandler(player, network, node, side, -1, ctx);
            if (blockCap != null) list.add(blockCap);
            if (scanItems) {
                IItemHandler inv = resolveItemHandler(player, network, node, "AUTO", false, ctx);
                if (inv != null) {
                    for (int i = 0; i < inv.getSlots(); i++) {
                        inv.getStackInSlot(i).getCapability(ForgeCapabilities.ENERGY).ifPresent(list::add);
                    }
                }
            }
        } else {
            for (int s : slots) {
                IEnergyStorage cap = resolveEnergyHandler(player, network, node, side, s, ctx);
                if (cap != null) list.add(cap);
            }
        }
        return list;
    }

    private static int processFluidRule(ServerPlayer player, NetworkData network, LogisticsRule rule, NetworkNode srcNode, NetworkNode dstNode, TickContext ctx, int amountLimit) {
        int amountToMove = amountLimit;

        List<IFluidHandler> sources = resolveAllFluidHandlers(player, network, srcNode, rule.sourceSide, rule.sourceSlots, ctx, rule.scanItems);
        List<IFluidHandler> destinations = resolveAllFluidHandlers(player, network, dstNode, rule.destSide, rule.destSlots, ctx, rule.scanItems);

        if (!rule.sourceIsGroup && rule.minAmount > 0) {
            int total = 0;
            for (IFluidHandler s : sources) {
                for (int i = 0; i < s.getTanks(); i++) total += s.getFluidInTank(i).getAmount();
            }
            if (total < rule.minAmount) {
                rule.lastReport = "[SEARCH] Source < Min";
                return 0;
            }
        }

        if (!rule.destIsGroup && rule.maxAmount != Integer.MAX_VALUE) {
            int total = 0;
            for (IFluidHandler d : destinations) {
                for (int i = 0; i < d.getTanks(); i++) total += d.getFluidInTank(i).getAmount();
            }
            if (total >= rule.maxAmount) {
                rule.lastReport = "[FULL] Dest @ Max";
                return 0;
            }
            amountToMove = Math.min(amountToMove, rule.maxAmount - total);
        }

        if (amountToMove <= 0) return 0;
        int totalMoved = 0;
        String fluidName = "Unknown";

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
                    recordMovement(network, ForgeRegistries.FLUIDS.getKey(realDrained.getFluid()).toString(), fluidName, accepted, srcNode.nodeId, dstNode.nodeId, "FLUIDS");
                    
                    if (amountToMove <= 0 || toMoveFromThisSrc <= 0) break;
                }
            }
            if (amountToMove <= 0) break;
        }

        if (totalMoved > 0) {
            String speedNote = "";
            if (totalMoved < amountLimit && amountLimit != Integer.MAX_VALUE) {
                speedNote = " (Limited by nodes)";
            }
            rule.lastReport = "[ACTIVE] Moved " + totalMoved + "mB " + fluidName + speedNote;
            network.lastReport = rule.lastReport;
            network.fluidsMovedThisMin += totalMoved;
        }
        return totalMoved;
    }

    private static List<IFluidHandler> resolveAllFluidHandlers(ServerPlayer player, NetworkData network, NetworkNode node, String side, List<Integer> slots, TickContext ctx, boolean scanItems) {
        List<IFluidHandler> list = new ArrayList<>();
        if (slots.contains(-1)) {
            IFluidHandler blockCap = resolveFluidHandler(player, network, node, side, -1, ctx);
            if (blockCap != null) list.add(blockCap);
            if (scanItems) {
                IItemHandler inv = resolveItemHandler(player, network, node, "AUTO", false, ctx);
                if (inv != null) {
                    for (int i = 0; i < inv.getSlots(); i++) {
                        inv.getStackInSlot(i).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).ifPresent(list::add);
                    }
                }
            }
        } else {
            for (int s : slots) {
                IFluidHandler cap = resolveFluidHandler(player, network, node, side, s, ctx);
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

    private static IEnergyStorage resolveEnergyHandler(ServerPlayer player, NetworkData network, NetworkNode node, String sideStr, int slotIdx, TickContext ctx) {
        if (node.nodeType.equals("BUFFER")) return new VirtualBufferEnergyHandler(network);
        if (node.nodeType.equals("TRASH")) return new TrashEnergyHandler();
        
        // Phase 3: Ports & Sub-Networks
        if (node.nodeType.equals("PORT_INPUT") || node.nodeType.equals("PORT_OUTPUT")) {
            return new NodeBufferEnergyHandler(node);
        }
        if (node.nodeType.equals("SUB_NETWORK")) {
            NetworkData otherNet = findNetwork(player, node.referencedNetworkId);
            if (otherNet != null) {
                NetworkNode targetPort = LogisticsUtil.findNode(otherNet, node.targetPortId);
                if (targetPort != null) {
                    return new NodeBufferEnergyHandler(targetPort);
                }
            }
            return null;
        }

        if (slotIdx != -1) {
            IItemHandler inv = resolveItemHandler(player, network, node, "AUTO", false, ctx);
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
        
        // SECURITY: Verify access on every tick to ensure logistics rules respect land claims
        if (!LogisticsUtil.canPlayerAccess(player, level, node.pos)) return null;

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

    private static IFluidHandler resolveFluidHandler(ServerPlayer player, NetworkData network, NetworkNode node, String sideStr, int slotIdx, TickContext ctx) {
        if (node.nodeType.equals("BUFFER")) return new VirtualBufferFluidHandler(network);
        if (node.nodeType.equals("TRASH")) return new TrashFluidHandler();

        // Phase 3: Ports & Sub-Networks
        if (node.nodeType.equals("PORT_INPUT") || node.nodeType.equals("PORT_OUTPUT")) {
            return new NodeBufferFluidHandler(node);
        }
        if (node.nodeType.equals("SUB_NETWORK")) {
            NetworkData otherNet = findNetwork(player, node.referencedNetworkId);
            if (otherNet != null) {
                NetworkNode targetPort = LogisticsUtil.findNode(otherNet, node.targetPortId);
                if (targetPort != null) {
                    return new NodeBufferFluidHandler(targetPort);
                }
            }
            return null;
        }

        if (slotIdx != -1) {
            IItemHandler inv = resolveItemHandler(player, network, node, "AUTO", false, ctx);
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
        
        // SECURITY: Verify access on every tick to ensure logistics rules respect land claims
        if (!LogisticsUtil.canPlayerAccess(player, level, node.pos)) return null;

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

    private static IItemHandler resolveItemHandler(ServerPlayer player, NetworkData network, NetworkNode node, String sideStr, boolean isSource, TickContext ctx) {
        if (node.nodeType.equals("BUFFER")) return new VirtualBufferItemHandler(network);
        if (node.nodeType.equals("TRASH")) return new TrashItemHandler();
        
        // Phase 3: Ports & Sub-Networks
        if (node.nodeType.equals("PORT_INPUT") || node.nodeType.equals("PORT_OUTPUT")) {
            return new NodeBufferItemHandler(node);
        }
        if (node.nodeType.equals("SUB_NETWORK")) {
            NetworkData otherNet = findNetwork(player, node.referencedNetworkId);
            if (otherNet != null) {
                NetworkNode targetPort = LogisticsUtil.findNode(otherNet, node.targetPortId);
                if (targetPort != null) {
                    return new NodeBufferItemHandler(targetPort);
                }
            }
            return null;
        }

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

                // SECURITY: Verify access on every tick to ensure logistics rules respect land claims
                if (!LogisticsUtil.canPlayerAccess(player, level, node.pos)) return null;
                
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
                        currentMatch = filter.getOrCreatePattern(finalVal).matcher(itemId).matches();
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
                case "SEMANTIC" -> {
                    currentMatch = switch (finalVal) {
                        case "IS_FOOD" -> stack.getItem().isEdible();
                        case "IS_FUEL" -> net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null) > 0;
                        case "IS_ORE" -> stack.getTags().anyMatch(t -> t.location().toString().contains("ores"));
                        case "IS_DAMAGED" -> stack.isDamaged();
                        default -> false;
                    };
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
            node.isMissing = true;
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

    private static double getFullness(ServerPlayer player, NetworkData network, NetworkNode node, String type, TickContext ctx) {
        if (type.equals("ENERGY")) {
            IEnergyStorage energy = resolveEnergyHandler(player, network, node, "AUTO", -1, ctx);
            if (energy == null || energy.getMaxEnergyStored() == 0) return 1.0;
            return (double) energy.getEnergyStored() / energy.getMaxEnergyStored();
        } else if (type.equals("FLUIDS")) {
            IFluidHandler fluids = resolveFluidHandler(player, network, node, "AUTO", -1, ctx);
            if (fluids == null) return 1.0;
            long totalCap = 0;
            long totalUsed = 0;
            for (int i = 0; i < fluids.getTanks(); i++) {
                totalCap += fluids.getTankCapacity(i);
                totalUsed += fluids.getFluidInTank(i).getAmount();
            }
            if (totalCap == 0) return 1.0;
            return (double) totalUsed / totalCap;
        } else {
            IItemHandler items = resolveItemHandler(player, network, node, "AUTO", false, ctx);
            if (items == null || items.getSlots() == 0) return 1.0;
            long totalCount = 0;
            long totalMax = 0;
            for (int i = 0; i < items.getSlots(); i++) {
                totalCount += items.getStackInSlot(i).getCount();
                totalMax += items.getSlotLimit(i);
            }
            if (totalMax == 0) return 1.0;
            return (double) totalCount / totalMax;
        }
    }

    private static boolean checkConditions(ServerPlayer player, NetworkData network, LogisticsRule rule, TickContext ctx) {
        for (LogicCondition cond : rule.conditions) {
            double current = 0;
            if (cond.type.equals("VARIABLE")) {
                current = network.variables.getOrDefault(cond.variableName, 0.0);
            } else if (cond.isGroup) {
                NodeGroup g = findGroup(network, cond.targetId);
                if (g != null) {
                    for (UUID id : g.nodeIds) {
                        NetworkNode n = LogisticsUtil.findNode(network, id);
                        if (n != null) current += getResourceCount(player, network, n, cond.type, cond.filter, ctx);
                    }
                }
            } else {
                NetworkNode n = LogisticsUtil.findNode(network, cond.targetId);
                if (n != null) current = getResourceCount(player, network, n, cond.type, cond.filter, ctx);
            }

            double threshold = cond.value;
            if (cond.compareToVariable) {
                threshold = network.variables.getOrDefault(cond.compareVariableName, 0.0);
            }

            boolean met = switch (cond.operator) {
                case "LESS" -> current < threshold;
                case "GREATER" -> current > threshold;
                case "EQUAL" -> Math.abs(current - threshold) < 0.001;
                default -> false;
            };
            if (!met) return false;
        }
        return true;
    }

    private static long getResourceCount(ServerPlayer player, NetworkData network, NetworkNode node, String type, LogisticsFilter filter, TickContext ctx) {
        if (type.equals("ENERGY")) {
            IEnergyStorage energy = resolveEnergyHandler(player, network, node, "AUTO", -1, ctx);
            return energy != null ? energy.getEnergyStored() : 0;
        } else if (type.equals("FLUIDS")) {
            List<IFluidHandler> fluids = resolveAllFluidHandlers(player, network, node, "AUTO", List.of(-1), ctx, true);
            long total = 0;
            for (IFluidHandler fh : fluids) {
                for (int i = 0; i < fh.getTanks(); i++) total += fh.getFluidInTank(i).getAmount();
            }
            return total;
        } else {
            IItemHandler items = resolveItemHandler(player, network, node, "AUTO", true, ctx);
            return items != null ? countItems(items, filter, node, List.of(-1)) : 0;
        }
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

    private static class TrashItemHandler implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return ItemStack.EMPTY; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private static class TrashEnergyHandler implements IEnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) { return maxReceive; }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }

    private static class TrashFluidHandler implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override public int fill(FluidStack resource, FluidAction action) { return resource.getAmount(); }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    private static class VirtualBufferItemHandler implements IItemHandler {
        private final NetworkData network;
        public VirtualBufferItemHandler(NetworkData network) { this.network = network; }
        @Override public int getSlots() { return network.virtualItemBuffer.size() + 1; }
        @Override public ItemStack getStackInSlot(int slot) { 
            return slot < network.virtualItemBuffer.size() ? network.virtualItemBuffer.get(slot) : ItemStack.EMPTY; 
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            if (!simulate) {
                boolean merged = false;
                for (ItemStack existing : network.virtualItemBuffer) {
                    if (ItemStack.isSameItemSameTags(existing, stack)) {
                        existing.grow(stack.getCount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) network.virtualItemBuffer.add(stack.copy());
            }
            return ItemStack.EMPTY;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= network.virtualItemBuffer.size()) return ItemStack.EMPTY;
            ItemStack existing = network.virtualItemBuffer.get(slot);
            int toExtract = Math.min(amount, existing.getCount());
            ItemStack result = existing.copy();
            result.setCount(toExtract);
            if (!simulate) {
                existing.shrink(toExtract);
                if (existing.isEmpty()) network.virtualItemBuffer.remove(slot);
            }
            return result;
        }
        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private static class VirtualBufferEnergyHandler implements IEnergyStorage {
        private final NetworkData network;
        public VirtualBufferEnergyHandler(NetworkData network) { this.network = network; }
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            long space = Long.MAX_VALUE - network.virtualEnergyBuffer;
            int toAdd = (int) Math.min(space, (long) maxReceive);
            if (!simulate) network.virtualEnergyBuffer += toAdd;
            return toAdd;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            int toTake = (int) Math.min(network.virtualEnergyBuffer, (long) maxExtract);
            if (!simulate) network.virtualEnergyBuffer -= toTake;
            return toTake;
        }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, network.virtualEnergyBuffer); }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }

    private static class VirtualBufferFluidHandler implements IFluidHandler {
        private final NetworkData network;
        public VirtualBufferFluidHandler(NetworkData network) { this.network = network; }
        @Override public int getTanks() { return network.virtualFluidBuffer.size() + 1; }
        @Override public FluidStack getFluidInTank(int tank) { 
            return tank < network.virtualFluidBuffer.size() ? network.virtualFluidBuffer.get(tank) : FluidStack.EMPTY; 
        }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            if (action.execute()) {
                boolean merged = false;
                for (FluidStack existing : network.virtualFluidBuffer) {
                    if (existing.isFluidEqual(resource)) {
                        existing.grow(resource.getAmount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) network.virtualFluidBuffer.add(resource.copy());
            }
            return resource.getAmount();
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int i = 0; i < network.virtualFluidBuffer.size(); i++) {
                FluidStack existing = network.virtualFluidBuffer.get(i);
                if (existing.isFluidEqual(resource)) {
                    int toDrain = Math.min(resource.getAmount(), existing.getAmount());
                    FluidStack result = existing.copy();
                    result.setAmount(toDrain);
                    if (action.execute()) {
                        existing.shrink(toDrain);
                        if (existing.isEmpty()) network.virtualFluidBuffer.remove(i);
                    }
                    return result;
                }
            }
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            if (network.virtualFluidBuffer.isEmpty()) return FluidStack.EMPTY;
            FluidStack existing = network.virtualFluidBuffer.get(0);
            int toDrain = Math.min(maxDrain, existing.getAmount());
            FluidStack result = existing.copy();
            result.setAmount(toDrain);
            if (action.execute()) {
                existing.shrink(toDrain);
                if (existing.isEmpty()) network.virtualFluidBuffer.remove(0);
            }
            return result;
        }
    }

    private static class NodeBufferItemHandler implements IItemHandler {
        private final NetworkNode node;
        public NodeBufferItemHandler(NetworkNode node) { this.node = node; }
        @Override public int getSlots() { return node.virtualItemBuffer.size() + 1; }
        @Override public ItemStack getStackInSlot(int slot) { 
            return slot < node.virtualItemBuffer.size() ? node.virtualItemBuffer.get(slot) : ItemStack.EMPTY; 
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            if (!simulate) {
                boolean merged = false;
                for (ItemStack existing : node.virtualItemBuffer) {
                    if (ItemStack.isSameItemSameTags(existing, stack)) {
                        existing.grow(stack.getCount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) node.virtualItemBuffer.add(stack.copy());
            }
            return ItemStack.EMPTY;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= node.virtualItemBuffer.size()) return ItemStack.EMPTY;
            ItemStack existing = node.virtualItemBuffer.get(slot);
            int toExtract = Math.min(amount, existing.getCount());
            ItemStack result = existing.copy();
            result.setCount(toExtract);
            if (!simulate) {
                existing.shrink(toExtract);
                if (existing.isEmpty()) node.virtualItemBuffer.remove(slot);
            }
            return result;
        }
        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private static class NodeBufferEnergyHandler implements IEnergyStorage {
        private final NetworkNode node;
        public NodeBufferEnergyHandler(NetworkNode node) { this.node = node; }
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            long space = Long.MAX_VALUE - node.virtualEnergyBuffer;
            int toAdd = (int) Math.min(space, (long) maxReceive);
            if (!simulate) node.virtualEnergyBuffer += toAdd;
            return toAdd;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            int toTake = (int) Math.min(node.virtualEnergyBuffer, (long) maxExtract);
            if (!simulate) node.virtualEnergyBuffer -= toTake;
            return toTake;
        }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, node.virtualEnergyBuffer); }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }

    private static class NodeBufferFluidHandler implements IFluidHandler {
        private final NetworkNode node;
        public NodeBufferFluidHandler(NetworkNode node) { this.node = node; }
        @Override public int getTanks() { return node.virtualFluidBuffer.size() + 1; }
        @Override public FluidStack getFluidInTank(int tank) { 
            return tank < node.virtualFluidBuffer.size() ? node.virtualFluidBuffer.get(tank) : FluidStack.EMPTY; 
        }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            if (action.execute()) {
                boolean merged = false;
                for (FluidStack existing : node.virtualFluidBuffer) {
                    if (existing.isFluidEqual(resource)) {
                        existing.grow(resource.getAmount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) node.virtualFluidBuffer.add(resource.copy());
            }
            return resource.getAmount();
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int i = 0; i < node.virtualFluidBuffer.size(); i++) {
                FluidStack existing = node.virtualFluidBuffer.get(i);
                if (existing.isFluidEqual(resource)) {
                    int toDrain = Math.min(resource.getAmount(), existing.getAmount());
                    FluidStack result = existing.copy();
                    result.setAmount(toDrain);
                    if (action.execute()) {
                        existing.shrink(toDrain);
                        if (existing.isEmpty()) node.virtualFluidBuffer.remove(i);
                    }
                    return result;
                }
            }
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            if (node.virtualFluidBuffer.isEmpty()) return FluidStack.EMPTY;
            FluidStack existing = node.virtualFluidBuffer.get(0);
            int toDrain = Math.min(maxDrain, existing.getAmount());
            FluidStack result = existing.copy();
            result.setAmount(toDrain);
            if (action.execute()) {
                existing.shrink(toDrain);
                if (existing.isEmpty()) node.virtualFluidBuffer.remove(0);
            }
            return result;
        }
    }
}