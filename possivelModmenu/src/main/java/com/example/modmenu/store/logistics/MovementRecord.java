package com.example.modmenu.store.logistics;

import java.util.UUID;

public class MovementRecord {
    public long timestamp;
    public String itemId;
    public String itemName;
    public int count;
    public UUID sourceNodeId;
    public UUID destNodeId;
    public String type;

    public MovementRecord() {}

    public MovementRecord(long timestamp, String itemId, String itemName, int count, UUID src, UUID dst, String type) {
        this.timestamp = timestamp;
        this.itemId = itemId;
        this.itemName = itemName;
        this.count = count;
        this.sourceNodeId = src;
        this.destNodeId = dst;
        this.type = type;
    }

    public MovementRecord snapshot() {
        return new MovementRecord(timestamp, itemId, itemName, count, sourceNodeId, destNodeId, type);
    }

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        nbt.putLong("timestamp", timestamp);
        nbt.putString("itemId", itemId != null ? itemId : "");
        nbt.putString("itemName", itemName != null ? itemName : "");
        nbt.putInt("count", count);
        if (sourceNodeId != null) nbt.putUUID("sourceNodeId", sourceNodeId);
        if (destNodeId != null) nbt.putUUID("destNodeId", destNodeId);
        nbt.putString("type", type != null ? type : "");
    }

    public static MovementRecord loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        MovementRecord record = new MovementRecord();
        record.timestamp = nbt.getLong("timestamp");
        record.itemId = nbt.getString("itemId");
        record.itemName = nbt.getString("itemName");
        record.count = nbt.getInt("count");
        if (nbt.hasUUID("sourceNodeId")) record.sourceNodeId = nbt.getUUID("sourceNodeId");
        if (nbt.hasUUID("destNodeId")) record.destNodeId = nbt.getUUID("destNodeId");
        record.type = nbt.getString("type");
        return record;
    }
}
