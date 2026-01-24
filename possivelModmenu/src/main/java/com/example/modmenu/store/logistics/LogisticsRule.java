package com.example.modmenu.store.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LogisticsRule {
    public UUID ruleId;
    public UUID sourceNodeId;
    public boolean sourceIsGroup = false;
    public UUID destNodeId;
    public boolean destIsGroup = false;
    public String sourceSide = "AUTO"; // "AUTO", "UP", "DOWN", etc.
    public String destSide = "AUTO";
    public LogisticsFilter filter = new LogisticsFilter();
    public String mode = "ROUND_ROBIN"; // "ROUND_ROBIN", "PRIORITY"
    public int amountPerTick = 64;
    public int minAmount = 0; // Only pull if source has more than this
    public int maxAmount = Integer.MAX_VALUE; // Only push if dest has less than this
    public boolean active = true;
    public int priority = 0;
    public String speedMode = "NORMAL"; // "SLOW", "NORMAL", "FAST", "HYPER", "INSTANT"
    public String type = "ITEMS";
    public List<Integer> sourceSlots = new ArrayList<>(List.of(-1));
    public List<Integer> destSlots = new ArrayList<>(List.of(-1));
    public String lastReport = "";

    public LogisticsRule() {
        this.ruleId = UUID.randomUUID();
    }

    public LogisticsRule snapshot() {
        LogisticsRule snap = new LogisticsRule();
        snap.ruleId = this.ruleId;
        snap.sourceNodeId = this.sourceNodeId;
        snap.sourceIsGroup = this.sourceIsGroup;
        snap.destNodeId = this.destNodeId;
        snap.destIsGroup = this.destIsGroup;
        snap.sourceSide = this.sourceSide;
        snap.destSide = this.destSide;
        snap.filter = this.filter.snapshot();
        snap.mode = this.mode;
        snap.amountPerTick = this.amountPerTick;
        snap.minAmount = this.minAmount;
        snap.maxAmount = this.maxAmount;
        snap.active = this.active;
        snap.priority = this.priority;
        snap.speedMode = this.speedMode;
        snap.type = this.type;
        snap.sourceSlots = new ArrayList<>(this.sourceSlots);
        snap.destSlots = new ArrayList<>(this.destSlots);
        snap.lastReport = this.lastReport;
        return snap;
    }
}
