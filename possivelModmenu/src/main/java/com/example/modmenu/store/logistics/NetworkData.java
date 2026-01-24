package com.example.modmenu.store.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NetworkData {
    public UUID networkId;
    public String networkName;
    public List<NetworkNode> nodes = new ArrayList<>();
    public List<NodeGroup> groups = new ArrayList<>();
    public List<LogisticsRule> rules = new ArrayList<>();
    public int tickBudget = 100; // Rules per tick
    public boolean active = true;
    public boolean showConnections = true;
    public boolean simulationActive = false;
    public String lastReport = "";
    
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
