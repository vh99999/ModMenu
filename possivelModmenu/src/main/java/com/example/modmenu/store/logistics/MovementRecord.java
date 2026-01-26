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
}
