package com.example.modmenu.store.logistics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NetworkData {
    public UUID networkId;
    public String networkName;
    public List<NetworkNode> nodes = new ArrayList<>();
    public List<NodeGroup> groups = new ArrayList<>();
    public List<LogisticsRule> rules = new ArrayList<>();
    public List<RuleTemplate> ruleTemplates = new ArrayList<>();
    public List<MovementRecord> movementHistory = new ArrayList<>();
    public UUID overflowTargetId;
    public boolean overflowIsGroup = false;
    public int tickBudget = 100; // Rules per tick
    public boolean active = true;
    public boolean showConnections = true;
    public boolean simulationActive = false;
    public String lastReport = "";

    // Virtual Buffers (Phase 1)
    public List<net.minecraft.world.item.ItemStack> virtualItemBuffer = new ArrayList<>();
    public long virtualEnergyBuffer = 0;
    public List<net.minecraftforge.fluids.FluidStack> virtualFluidBuffer = new ArrayList<>();

    // Phase 4: Variables
    public java.util.Map<String, Double> variables = new java.util.HashMap<>();

    // Phase 2: Signals
    public transient List<LogisticsSignal> pendingSignals = new ArrayList<>();
    public List<LogisticsSignal> recentSignals = new ArrayList<>();
    public transient Map<String, List<UUID>> signalSubscriptions = null;

    // Optimization transient fields
    public transient List<LogisticsRule> sortedRules = null;
    public transient boolean needsSorting = true;
    public transient Map<UUID, NetworkNode> nodeMap = null;
    public transient Map<UUID, NodeGroup> groupMap = null;
    
    // Stats
    public long itemsMovedLastMin = 0;
    public long energyMovedLastMin = 0;
    public long fluidsMovedLastMin = 0;
    public transient long itemsMovedThisMin = 0;
    public transient long energyMovedThisMin = 0;
    public transient long fluidsMovedThisMin = 0;
    public transient long lastStatsReset = 0;

    public NetworkData() {
        this.networkId = UUID.randomUUID();
    }

    public NetworkData snapshot() {
        NetworkData snap = new NetworkData();
        snap.networkId = this.networkId;
        snap.networkName = this.networkName;
        for (NetworkNode node : this.nodes) snap.nodes.add(node.snapshot());
        for (NodeGroup group : this.groups) snap.groups.add(group.snapshot());
        for (LogisticsRule rule : this.rules) snap.rules.add(rule.snapshot());
        for (RuleTemplate template : this.ruleTemplates) snap.ruleTemplates.add(template.snapshot());
        for (MovementRecord record : this.movementHistory) snap.movementHistory.add(record.snapshot());
        snap.overflowTargetId = this.overflowTargetId;
        snap.overflowIsGroup = this.overflowIsGroup;
        snap.tickBudget = this.tickBudget;
        snap.active = this.active;
        snap.showConnections = this.showConnections;
        snap.simulationActive = this.simulationActive;
        snap.lastReport = this.lastReport;

        for (ItemStack stack : this.virtualItemBuffer) snap.virtualItemBuffer.add(stack.copy());
        snap.virtualEnergyBuffer = this.virtualEnergyBuffer;
        for (FluidStack fluid : this.virtualFluidBuffer) snap.virtualFluidBuffer.add(fluid.copy());
        snap.variables.putAll(this.variables);
        for (LogisticsSignal sig : this.recentSignals) snap.recentSignals.add(sig);

        snap.itemsMovedLastMin = this.itemsMovedLastMin;
        snap.energyMovedLastMin = this.energyMovedLastMin;
        snap.fluidsMovedLastMin = this.fluidsMovedLastMin;
        return snap;
    }

    public void saveNBT(CompoundTag nbt) {
        if (networkId != null) nbt.putUUID("networkId", networkId);
        nbt.putString("networkName", networkName != null ? networkName : "");
        
        ListTag nodeList = new ListTag();
        for (NetworkNode node : nodes) {
            CompoundTag tag = new CompoundTag();
            node.saveNBT(tag);
            nodeList.add(tag);
        }
        nbt.put("nodes", nodeList);

        ListTag groupList = new ListTag();
        for (NodeGroup group : groups) {
            CompoundTag tag = new CompoundTag();
            group.saveNBT(tag);
            groupList.add(tag);
        }
        nbt.put("groups", groupList);

        ListTag ruleList = new ListTag();
        for (LogisticsRule rule : rules) {
            CompoundTag tag = new CompoundTag();
            rule.saveNBT(tag);
            ruleList.add(tag);
        }
        nbt.put("rules", ruleList);

        ListTag templateList = new ListTag();
        for (RuleTemplate template : ruleTemplates) {
            CompoundTag tag = new CompoundTag();
            template.saveNBT(tag);
            templateList.add(tag);
        }
        nbt.put("ruleTemplates", templateList);

        ListTag historyList = new ListTag();
        for (MovementRecord record : movementHistory) {
            CompoundTag tag = new CompoundTag();
            record.saveNBT(tag);
            historyList.add(tag);
        }
        nbt.put("movementHistory", historyList);

        if (overflowTargetId != null) nbt.putUUID("overflowTargetId", overflowTargetId);
        nbt.putBoolean("overflowIsGroup", overflowIsGroup);
        nbt.putInt("tickBudget", tickBudget);
        nbt.putBoolean("active", active);
        nbt.putBoolean("showConnections", showConnections);
        nbt.putBoolean("simulationActive", simulationActive);
        nbt.putString("lastReport", lastReport != null ? lastReport : "");

        ListTag vItems = new ListTag();
        for (ItemStack stack : virtualItemBuffer) vItems.add(stack.save(new CompoundTag()));
        nbt.put("virtualItemBuffer", vItems);
        nbt.putLong("virtualEnergyBuffer", virtualEnergyBuffer);
        ListTag vFluids = new ListTag();
        for (FluidStack fluid : virtualFluidBuffer) {
            CompoundTag tag = new CompoundTag();
            fluid.writeToNBT(tag);
            vFluids.add(tag);
        }
        nbt.put("virtualFluidBuffer", vFluids);

        CompoundTag varNbt = new CompoundTag();
        for (java.util.Map.Entry<String, Double> entry : variables.entrySet()) varNbt.putDouble(entry.getKey(), entry.getValue());
        nbt.put("variables", varNbt);

        ListTag sigList = new ListTag();
        for (LogisticsSignal sig : recentSignals) {
            CompoundTag tag = new CompoundTag();
            sig.saveNBT(tag);
            sigList.add(tag);
        }
        nbt.put("recentSignals", sigList);

        nbt.putLong("itemsMovedLastMin", itemsMovedLastMin);
        nbt.putLong("energyMovedLastMin", energyMovedLastMin);
        nbt.putLong("fluidsMovedLastMin", fluidsMovedLastMin);
    }

    public static NetworkData loadNBT(CompoundTag nbt) {
        NetworkData net = new NetworkData();
        if (nbt.hasUUID("networkId")) net.networkId = nbt.getUUID("networkId");
        net.networkName = nbt.getString("networkName");
        
        ListTag nodeList = nbt.getList("nodes", 10);
        for (int i = 0; i < nodeList.size(); i++) net.nodes.add(NetworkNode.loadNBT(nodeList.getCompound(i)));

        ListTag groupList = nbt.getList("groups", 10);
        for (int i = 0; i < groupList.size(); i++) net.groups.add(NodeGroup.loadNBT(groupList.getCompound(i)));

        ListTag ruleList = nbt.getList("rules", 10);
        for (int i = 0; i < ruleList.size(); i++) net.rules.add(LogisticsRule.loadNBT(ruleList.getCompound(i)));

        ListTag templateList = nbt.getList("ruleTemplates", 10);
        for (int i = 0; i < templateList.size(); i++) net.ruleTemplates.add(RuleTemplate.loadNBT(templateList.getCompound(i)));

        ListTag historyList = nbt.getList("movementHistory", 10);
        for (int i = 0; i < historyList.size(); i++) net.movementHistory.add(MovementRecord.loadNBT(historyList.getCompound(i)));

        if (nbt.hasUUID("overflowTargetId")) net.overflowTargetId = nbt.getUUID("overflowTargetId");
        net.overflowIsGroup = nbt.getBoolean("overflowIsGroup");
        net.tickBudget = nbt.getInt("tickBudget");
        net.active = nbt.getBoolean("active");
        net.showConnections = nbt.getBoolean("showConnections");
        net.simulationActive = nbt.getBoolean("simulationActive");
        net.lastReport = nbt.getString("lastReport");

        ListTag vItems = nbt.getList("virtualItemBuffer", 10);
        for (int i = 0; i < vItems.size(); i++) net.virtualItemBuffer.add(ItemStack.of(vItems.getCompound(i)));
        net.virtualEnergyBuffer = nbt.getLong("virtualEnergyBuffer");
        ListTag vFluids = nbt.getList("virtualFluidBuffer", 10);
        for (int i = 0; i < vFluids.size(); i++) net.virtualFluidBuffer.add(FluidStack.loadFluidStackFromNBT(vFluids.getCompound(i)));

        CompoundTag varNbt = nbt.getCompound("variables");
        for (String key : varNbt.getAllKeys()) net.variables.put(key, varNbt.getDouble(key));

        ListTag sigList = nbt.getList("recentSignals", 10);
        for (int i = 0; i < sigList.size(); i++) net.recentSignals.add(LogisticsSignal.loadNBT(sigList.getCompound(i)));

        net.itemsMovedLastMin = nbt.getLong("itemsMovedLastMin");
        net.energyMovedLastMin = nbt.getLong("energyMovedLastMin");
        net.fluidsMovedLastMin = nbt.getLong("fluidsMovedLastMin");
        
        return net;
    }
}
