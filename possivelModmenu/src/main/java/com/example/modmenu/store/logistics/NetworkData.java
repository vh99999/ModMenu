package com.example.modmenu.store.logistics;

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
        snap.itemsMovedLastMin = this.itemsMovedLastMin;
        snap.energyMovedLastMin = this.energyMovedLastMin;
        snap.fluidsMovedLastMin = this.fluidsMovedLastMin;
        return snap;
    }
}
