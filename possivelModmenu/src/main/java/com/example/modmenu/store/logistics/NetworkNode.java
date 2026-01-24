package com.example.modmenu.store.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetworkNode {
    public UUID nodeId;
    public String nodeType = "BLOCK"; // "BLOCK", "PLAYER", "CHAMBER", "MARKET"
    public int chamberIndex = -1; // For CHAMBER type
    public BlockPos pos;
    public String dimension;
    public String blockId; // Captured for icons
    public String customName;
    public int guiX = 0, guiY = 0;
    public Map<Direction, String> sideConfig = new HashMap<>(); // Direction -> CapabilityType (ITEMS, ENERGY, FLUIDS)
    public Map<Integer, String> slotConfig = new HashMap<>(); // SlotIndex -> Mode (BOTH, IN, OUT, OFF)
    public boolean isMissing = false;

    public NetworkNode() {
        this.nodeId = UUID.randomUUID();
    }

    public NetworkNode snapshot() {
        NetworkNode snap = new NetworkNode();
        snap.nodeId = this.nodeId;
        snap.nodeType = this.nodeType;
        snap.chamberIndex = this.chamberIndex;
        snap.pos = this.pos;
        snap.dimension = this.dimension;
        snap.blockId = this.blockId;
        snap.customName = this.customName;
        snap.guiX = this.guiX;
        snap.guiY = this.guiY;
        snap.sideConfig.putAll(this.sideConfig);
        snap.slotConfig.putAll(this.slotConfig);
        snap.isMissing = this.isMissing;
        return snap;
    }
}
