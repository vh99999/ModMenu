package com.example.modmenu.store.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LogisticsBlueprint {
    public String name;
    public List<NetworkNode> nodes = new ArrayList<>();
    public List<NodeGroup> groups = new ArrayList<>();
    public List<LogisticsRule> rules = new ArrayList<>();
    public java.util.UUID overflowTargetId;
    public boolean overflowIsGroup;

    public LogisticsBlueprint() {}

    public LogisticsBlueprint(String name, List<NetworkNode> nodes, List<NodeGroup> groups, List<LogisticsRule> rules, java.util.UUID overflowTargetId, boolean overflowIsGroup) {
        this.name = name;
        
        net.minecraft.core.BlockPos pivot = null;
        for (NetworkNode node : nodes) {
            if ("BLOCK".equals(node.nodeType) && node.pos != null) {
                if (pivot == null || node.pos.getY() < pivot.getY() || (node.pos.getY() == pivot.getY() && node.pos.getX() < pivot.getX())) {
                    pivot = node.pos;
                }
            }
        }

        for (NetworkNode node : nodes) {
            NetworkNode snap = node.snapshot();
            if ("BLOCK".equals(snap.nodeType) && snap.pos != null && pivot != null) {
                snap.pos = snap.pos.subtract(pivot);
            }
            this.nodes.add(snap);
        }
        for (NodeGroup group : groups) this.groups.add(group.snapshot());
        for (LogisticsRule rule : rules) this.rules.add(rule.snapshot());
        this.overflowTargetId = overflowTargetId;
        this.overflowIsGroup = overflowIsGroup;
    }

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(net.minecraft.world.item.ItemStack.class, new com.example.modmenu.store.GsonAdapters.ItemStackAdapter())
            .registerTypeAdapter(net.minecraft.nbt.CompoundTag.class, new com.example.modmenu.store.GsonAdapters.CompoundTagAdapter())
            .registerTypeAdapter(net.minecraftforge.fluids.FluidStack.class, new com.example.modmenu.store.GsonAdapters.FluidStackAdapter())
            .create();

    public String serialize() {
        String json = GSON.toJson(this);
        return java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static LogisticsBlueprint deserialize(String base64) {
        try {
            String json = new String(java.util.Base64.getDecoder().decode(base64), java.nio.charset.StandardCharsets.UTF_8);
            return GSON.fromJson(json, LogisticsBlueprint.class);
        } catch (Exception e) {
            return null;
        }
    }
}
