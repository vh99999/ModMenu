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
}
