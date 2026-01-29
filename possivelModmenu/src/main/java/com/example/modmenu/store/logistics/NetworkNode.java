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
    public String iconItemId; // Custom icon item ID
    public String customName;
    public int guiX = 0, guiY = 0;
    public Map<Direction, String> sideConfig = new HashMap<>(); // Direction -> CapabilityType (ITEMS, ENERGY, FLUIDS)
    public Map<Integer, String> slotConfig = new HashMap<>(); // SlotIndex -> Mode (BOTH, IN, OUT, OFF)
    public boolean isMissing = false;

    // Phase 3: Network-in-Network
    public java.util.UUID referencedNetworkId;
    public java.util.UUID targetPortId;
    public java.util.List<net.minecraft.world.item.ItemStack> virtualItemBuffer = new java.util.ArrayList<>();
    public long virtualEnergyBuffer = 0;
    public java.util.List<net.minecraftforge.fluids.FluidStack> virtualFluidBuffer = new java.util.ArrayList<>();

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
        snap.iconItemId = this.iconItemId;
        snap.customName = this.customName;
        snap.guiX = this.guiX;
        snap.guiY = this.guiY;
        snap.sideConfig.putAll(this.sideConfig);
        snap.slotConfig.putAll(this.slotConfig);
        snap.isMissing = this.isMissing;
        
        snap.referencedNetworkId = this.referencedNetworkId;
        snap.targetPortId = this.targetPortId;
        for (net.minecraft.world.item.ItemStack stack : this.virtualItemBuffer) snap.virtualItemBuffer.add(stack.copy());
        snap.virtualEnergyBuffer = this.virtualEnergyBuffer;
        for (net.minecraftforge.fluids.FluidStack fluid : this.virtualFluidBuffer) snap.virtualFluidBuffer.add(fluid.copy());

        return snap;
    }

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        if (nodeId != null) nbt.putUUID("nodeId", nodeId);
        nbt.putString("nodeType", nodeType != null ? nodeType : "BLOCK");
        nbt.putInt("chamberIndex", chamberIndex);
        if (pos != null) nbt.putLong("pos", pos.asLong());
        nbt.putString("dimension", dimension != null ? dimension : "");
        nbt.putString("blockId", blockId != null ? blockId : "");
        nbt.putString("iconItemId", iconItemId != null ? iconItemId : "");
        nbt.putString("customName", customName != null ? customName : "");
        nbt.putInt("guiX", guiX);
        nbt.putInt("guiY", guiY);
        
        net.minecraft.nbt.CompoundTag sideNbt = new net.minecraft.nbt.CompoundTag();
        for (java.util.Map.Entry<net.minecraft.core.Direction, String> entry : sideConfig.entrySet()) {
            sideNbt.putString(entry.getKey().name(), entry.getValue());
        }
        nbt.put("sideConfig", sideNbt);

        net.minecraft.nbt.CompoundTag slotNbt = new net.minecraft.nbt.CompoundTag();
        for (java.util.Map.Entry<Integer, String> entry : slotConfig.entrySet()) {
            slotNbt.putString(String.valueOf(entry.getKey()), entry.getValue());
        }
        nbt.put("slotConfig", slotNbt);
        
        nbt.putBoolean("isMissing", isMissing);
        if (referencedNetworkId != null) nbt.putUUID("referencedNetworkId", referencedNetworkId);
        if (targetPortId != null) nbt.putUUID("targetPortId", targetPortId);
        
        net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
        for (net.minecraft.world.item.ItemStack stack : virtualItemBuffer) items.add(stack.save(new net.minecraft.nbt.CompoundTag()));
        nbt.put("virtualItemBuffer", items);
        
        nbt.putLong("virtualEnergyBuffer", virtualEnergyBuffer);
        
        net.minecraft.nbt.ListTag fluids = new net.minecraft.nbt.ListTag();
        for (net.minecraftforge.fluids.FluidStack fluid : virtualFluidBuffer) {
            net.minecraft.nbt.CompoundTag fNbt = new net.minecraft.nbt.CompoundTag();
            fluid.writeToNBT(fNbt);
            fluids.add(fNbt);
        }
        nbt.put("virtualFluidBuffer", fluids);
    }

    public static NetworkNode loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        NetworkNode node = new NetworkNode();
        if (nbt.hasUUID("nodeId")) node.nodeId = nbt.getUUID("nodeId");
        node.nodeType = nbt.getString("nodeType");
        node.chamberIndex = nbt.getInt("chamberIndex");
        if (nbt.contains("pos")) node.pos = net.minecraft.core.BlockPos.of(nbt.getLong("pos"));
        node.dimension = nbt.getString("dimension");
        node.blockId = nbt.getString("blockId");
        node.iconItemId = nbt.getString("iconItemId");
        node.customName = nbt.getString("customName");
        node.guiX = nbt.getInt("guiX");
        node.guiY = nbt.getInt("guiY");
        
        net.minecraft.nbt.CompoundTag sideNbt = nbt.getCompound("sideConfig");
        for (String key : sideNbt.getAllKeys()) {
            try {
                node.sideConfig.put(net.minecraft.core.Direction.valueOf(key), sideNbt.getString(key));
            } catch (Exception ignored) {}
        }

        net.minecraft.nbt.CompoundTag slotNbt = nbt.getCompound("slotConfig");
        for (String key : slotNbt.getAllKeys()) {
            try {
                node.slotConfig.put(Integer.parseInt(key), slotNbt.getString(key));
            } catch (Exception ignored) {}
        }
        
        node.isMissing = nbt.getBoolean("isMissing");
        if (nbt.hasUUID("referencedNetworkId")) node.referencedNetworkId = nbt.getUUID("referencedNetworkId");
        if (nbt.hasUUID("targetPortId")) node.targetPortId = nbt.getUUID("targetPortId");
        
        net.minecraft.nbt.ListTag items = nbt.getList("virtualItemBuffer", 10);
        for (int i = 0; i < items.size(); i++) node.virtualItemBuffer.add(net.minecraft.world.item.ItemStack.of(items.getCompound(i)));
        
        node.virtualEnergyBuffer = nbt.getLong("virtualEnergyBuffer");
        
        net.minecraft.nbt.ListTag fluids = nbt.getList("virtualFluidBuffer", 10);
        for (int i = 0; i < fluids.size(); i++) node.virtualFluidBuffer.add(net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(fluids.getCompound(i)));
        
        return node;
    }
}
