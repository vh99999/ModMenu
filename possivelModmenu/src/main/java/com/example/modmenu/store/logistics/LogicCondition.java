package com.example.modmenu.store.logistics;

import java.util.UUID;

public class LogicCondition {
    public UUID targetId;
    public boolean isGroup;
    public String type = "ITEMS"; // "ITEMS", "ENERGY", "FLUIDS", "VARIABLE"
    public String operator = "LESS"; // "LESS", "GREATER", "EQUAL"
    public int value = 0;
    public LogisticsFilter filter = new LogisticsFilter();

    // Phase 4: Variables
    public String variableName = "";
    public boolean compareToVariable = false;
    public String compareVariableName = "";

    public LogicCondition snapshot() {
        LogicCondition snap = new LogicCondition();
        snap.targetId = this.targetId;
        snap.isGroup = this.isGroup;
        snap.type = this.type;
        snap.operator = this.operator;
        snap.value = this.value;
        snap.filter = this.filter.snapshot();
        snap.variableName = this.variableName;
        snap.compareToVariable = this.compareToVariable;
        snap.compareVariableName = this.compareVariableName;
        return snap;
    }

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        if (targetId != null) nbt.putUUID("targetId", targetId);
        nbt.putBoolean("isGroup", isGroup);
        nbt.putString("type", type);
        nbt.putString("operator", operator);
        nbt.putInt("value", value);
        net.minecraft.nbt.CompoundTag filterNbt = new net.minecraft.nbt.CompoundTag();
        filter.saveNBT(filterNbt);
        nbt.put("filter", filterNbt);
        nbt.putString("variableName", variableName);
        nbt.putBoolean("compareToVariable", compareToVariable);
        nbt.putString("compareVariableName", compareVariableName);
    }

    public static LogicCondition loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        LogicCondition cond = new LogicCondition();
        if (nbt.hasUUID("targetId")) cond.targetId = nbt.getUUID("targetId");
        cond.isGroup = nbt.getBoolean("isGroup");
        cond.type = nbt.getString("type");
        cond.operator = nbt.getString("operator");
        cond.value = nbt.getInt("value");
        if (nbt.contains("filter")) cond.filter = LogisticsFilter.loadNBT(nbt.getCompound("filter"));
        cond.variableName = nbt.getString("variableName");
        cond.compareToVariable = nbt.getBoolean("compareToVariable");
        cond.compareVariableName = nbt.getString("compareVariableName");
        return cond;
    }
}
