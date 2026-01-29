package com.example.modmenu.store.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NodeGroup {
    public UUID groupId;
    public String name;
    public List<UUID> nodeIds = new ArrayList<>();
    public int guiX = 0, guiY = 0;
    public boolean expanded = false;

    public NodeGroup() {
        this.groupId = UUID.randomUUID();
    }

    public NodeGroup(String name) {
        this();
        this.name = name;
    }

    public NodeGroup snapshot() {
        NodeGroup snap = new NodeGroup();
        snap.groupId = this.groupId;
        snap.name = this.name;
        snap.nodeIds = new ArrayList<>(this.nodeIds);
        snap.guiX = this.guiX;
        snap.guiY = this.guiY;
        snap.expanded = this.expanded;
        return snap;
    }

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        if (groupId != null) nbt.putUUID("groupId", groupId);
        nbt.putString("name", name != null ? name : "");
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (UUID id : nodeIds) list.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        nbt.put("nodeIds", list);
        nbt.putInt("guiX", guiX);
        nbt.putInt("guiY", guiY);
        nbt.putBoolean("expanded", expanded);
    }

    public static NodeGroup loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        NodeGroup group = new NodeGroup();
        if (nbt.hasUUID("groupId")) group.groupId = nbt.getUUID("groupId");
        group.name = nbt.getString("name");
        net.minecraft.nbt.ListTag list = nbt.getList("nodeIds", 11); // UUID list
        for (int i = 0; i < list.size(); i++) group.nodeIds.add(net.minecraft.nbt.NbtUtils.loadUUID(list.get(i)));
        group.guiX = nbt.getInt("guiX");
        group.guiY = nbt.getInt("guiY");
        group.expanded = nbt.getBoolean("expanded");
        return group;
    }
}
