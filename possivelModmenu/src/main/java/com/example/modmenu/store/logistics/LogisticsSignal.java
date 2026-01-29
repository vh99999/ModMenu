package com.example.modmenu.store.logistics;

import java.util.UUID;

public class LogisticsSignal {
    public String type;
    public UUID sourceNodeId;
    public UUID sourceRuleId;
    public long timestamp;

    public LogisticsSignal(String type, UUID sourceNodeId, UUID sourceRuleId) {
        this.type = type;
        this.sourceNodeId = sourceNodeId;
        this.sourceRuleId = sourceRuleId;
        this.timestamp = System.currentTimeMillis();
    }

    public LogisticsSignal() {}

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        nbt.putString("type", type != null ? type : "");
        if (sourceNodeId != null) nbt.putUUID("sourceNodeId", sourceNodeId);
        if (sourceRuleId != null) nbt.putUUID("sourceRuleId", sourceRuleId);
        nbt.putLong("timestamp", timestamp);
    }

    public static LogisticsSignal loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        LogisticsSignal signal = new LogisticsSignal();
        signal.type = nbt.getString("type");
        if (nbt.hasUUID("sourceNodeId")) signal.sourceNodeId = nbt.getUUID("sourceNodeId");
        if (nbt.hasUUID("sourceRuleId")) signal.sourceRuleId = nbt.getUUID("sourceRuleId");
        signal.timestamp = nbt.getLong("timestamp");
        return signal;
    }
}
