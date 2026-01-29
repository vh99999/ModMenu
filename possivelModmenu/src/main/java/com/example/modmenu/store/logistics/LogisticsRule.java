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
    public String distributionMode = "BALANCED"; // "BALANCED", "ROUND_ROBIN", "OVERFLOW"
    public int amountPerTick = 64;
    public int minAmount = 0; // Only pull if source has more than this
    public int maxAmount = Integer.MAX_VALUE; // Only push if dest has less than this
    public boolean active = true;
    public boolean maintenanceMode = false;
    public boolean scanItems = true; // For Energy/Fluids: whether to scan inventory items for caps
    public List<LogicCondition> conditions = new ArrayList<>();
    public int priority = 0;
    public String speedMode = "NORMAL"; // "SLOW", "NORMAL", "FAST", "HYPER", "INSTANT"
    public String type = "ITEMS";
    public String ruleAction = "MOVE"; // "MOVE", "EXTRACT", "INSERT", "SET_VARIABLE", "MATH"
    public String triggerType = "ALWAYS"; // "ALWAYS", "SIGNAL"

    // Phase 4: Variables
    public String variableName = "";
    public String variableOp = "SET"; // "SET", "ADD", "SUB", "MUL", "DIV"
    public String secondaryVariableName = "";
    public double constantValue = 0;

    public String signalFilter = "";
    public UUID triggerNodeId = null;
    public String emitSignalOnSuccess = "";
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
        snap.distributionMode = this.distributionMode;
        snap.amountPerTick = this.amountPerTick;
        snap.minAmount = this.minAmount;
        snap.maxAmount = this.maxAmount;
        snap.active = this.active;
        snap.maintenanceMode = this.maintenanceMode;
        snap.scanItems = this.scanItems;
        for (LogicCondition cond : this.conditions) snap.conditions.add(cond.snapshot());
        snap.priority = this.priority;
        snap.speedMode = this.speedMode;
        snap.type = this.type;
        snap.ruleAction = this.ruleAction;
        snap.variableName = this.variableName;
        snap.variableOp = this.variableOp;
        snap.secondaryVariableName = this.secondaryVariableName;
        snap.constantValue = this.constantValue;
        snap.triggerType = this.triggerType;
        snap.signalFilter = this.signalFilter;
        snap.triggerNodeId = this.triggerNodeId;
        snap.emitSignalOnSuccess = this.emitSignalOnSuccess;
        snap.sourceSlots = new ArrayList<>(this.sourceSlots);
        snap.destSlots = new ArrayList<>(this.destSlots);
        snap.lastReport = this.lastReport;
        return snap;
    }

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        if (ruleId != null) nbt.putUUID("ruleId", ruleId);
        if (sourceNodeId != null) nbt.putUUID("sourceNodeId", sourceNodeId);
        nbt.putBoolean("sourceIsGroup", sourceIsGroup);
        if (destNodeId != null) nbt.putUUID("destNodeId", destNodeId);
        nbt.putBoolean("destIsGroup", destIsGroup);
        nbt.putString("sourceSide", sourceSide);
        nbt.putString("destSide", destSide);
        net.minecraft.nbt.CompoundTag filterNbt = new net.minecraft.nbt.CompoundTag();
        filter.saveNBT(filterNbt);
        nbt.put("filter", filterNbt);
        nbt.putString("mode", mode);
        nbt.putString("distributionMode", distributionMode);
        nbt.putInt("amountPerTick", amountPerTick);
        nbt.putInt("minAmount", minAmount);
        nbt.putInt("maxAmount", maxAmount);
        nbt.putBoolean("active", active);
        nbt.putBoolean("maintenanceMode", maintenanceMode);
        nbt.putBoolean("scanItems", scanItems);
        net.minecraft.nbt.ListTag condList = new net.minecraft.nbt.ListTag();
        for (LogicCondition cond : conditions) {
            net.minecraft.nbt.CompoundTag cNbt = new net.minecraft.nbt.CompoundTag();
            cond.saveNBT(cNbt);
            condList.add(cNbt);
        }
        nbt.put("conditions", condList);
        nbt.putInt("priority", priority);
        nbt.putString("speedMode", speedMode);
        nbt.putString("type", type);
        nbt.putString("ruleAction", ruleAction);
        nbt.putString("triggerType", triggerType);
        nbt.putString("variableName", variableName);
        nbt.putString("variableOp", variableOp);
        nbt.putString("secondaryVariableName", secondaryVariableName);
        nbt.putDouble("constantValue", constantValue);
        nbt.putString("signalFilter", signalFilter);
        if (triggerNodeId != null) nbt.putUUID("triggerNodeId", triggerNodeId);
        nbt.putString("emitSignalOnSuccess", emitSignalOnSuccess);
        nbt.putIntArray("sourceSlots", sourceSlots);
        nbt.putIntArray("destSlots", destSlots);
        nbt.putString("lastReport", lastReport != null ? lastReport : "");
    }

    public static LogisticsRule loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        LogisticsRule rule = new LogisticsRule();
        if (nbt.hasUUID("ruleId")) rule.ruleId = nbt.getUUID("ruleId");
        if (nbt.hasUUID("sourceNodeId")) rule.sourceNodeId = nbt.getUUID("sourceNodeId");
        rule.sourceIsGroup = nbt.getBoolean("sourceIsGroup");
        if (nbt.hasUUID("destNodeId")) rule.destNodeId = nbt.getUUID("destNodeId");
        rule.destIsGroup = nbt.getBoolean("destIsGroup");
        rule.sourceSide = nbt.getString("sourceSide");
        rule.destSide = nbt.getString("destSide");
        if (nbt.contains("filter")) rule.filter = LogisticsFilter.loadNBT(nbt.getCompound("filter"));
        rule.mode = nbt.getString("mode");
        rule.distributionMode = nbt.getString("distributionMode");
        rule.amountPerTick = nbt.getInt("amountPerTick");
        rule.minAmount = nbt.getInt("minAmount");
        rule.maxAmount = nbt.getInt("maxAmount");
        rule.active = nbt.getBoolean("active");
        rule.maintenanceMode = nbt.getBoolean("maintenanceMode");
        rule.scanItems = nbt.getBoolean("scanItems");
        net.minecraft.nbt.ListTag condList = nbt.getList("conditions", 10);
        rule.conditions = new ArrayList<>();
        for (int i = 0; i < condList.size(); i++) rule.conditions.add(LogicCondition.loadNBT(condList.getCompound(i)));
        rule.priority = nbt.getInt("priority");
        rule.speedMode = nbt.getString("speedMode");
        rule.type = nbt.getString("type");
        rule.ruleAction = nbt.getString("ruleAction");
        rule.triggerType = nbt.getString("triggerType");
        rule.variableName = nbt.getString("variableName");
        rule.variableOp = nbt.getString("variableOp");
        rule.secondaryVariableName = nbt.getString("secondaryVariableName");
        rule.constantValue = nbt.getDouble("constantValue");
        rule.signalFilter = nbt.getString("signalFilter");
        if (nbt.hasUUID("triggerNodeId")) rule.triggerNodeId = nbt.getUUID("triggerNodeId");
        rule.emitSignalOnSuccess = nbt.getString("emitSignalOnSuccess");
        int[] srcSlots = nbt.getIntArray("sourceSlots");
        rule.sourceSlots = new ArrayList<>();
        for (int s : srcSlots) rule.sourceSlots.add(s);
        int[] dstSlots = nbt.getIntArray("destSlots");
        rule.destSlots = new ArrayList<>();
        for (int s : dstSlots) rule.destSlots.add(s);
        rule.lastReport = nbt.getString("lastReport");
        return rule;
    }
}
