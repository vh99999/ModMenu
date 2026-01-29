package com.example.modmenu.store.logistics;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class LogisticsFilter {
    public String matchType = "ALL"; // "ALL", "ID", "TAG", "NBT"
    public List<String> matchValues = new ArrayList<>();
    public CompoundTag nbtSample;
    public boolean blacklist = false;
    public boolean fuzzyNbt = false;
    private transient java.util.Map<String, java.util.regex.Pattern> patternCache = new java.util.HashMap<>();

    public java.util.regex.Pattern getOrCreatePattern(String regexStr) {
        if (patternCache == null) patternCache = new java.util.HashMap<>();
        return patternCache.computeIfAbsent(regexStr, r -> {
            try {
                return java.util.regex.Pattern.compile(r.replace(".", "\\.").replace("*", ".*"));
            } catch (Exception e) {
                return java.util.regex.Pattern.compile("INVALID_REGEX");
            }
        });
    }

    public LogisticsFilter snapshot() {
        LogisticsFilter snap = new LogisticsFilter();
        snap.matchType = this.matchType;
        snap.matchValues = new ArrayList<>(this.matchValues);
        snap.nbtSample = this.nbtSample != null ? this.nbtSample.copy() : null;
        snap.blacklist = this.blacklist;
        snap.fuzzyNbt = this.fuzzyNbt;
        return snap;
    }

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        nbt.putString("matchType", matchType);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String s : matchValues) list.add(net.minecraft.nbt.StringTag.valueOf(s));
        nbt.put("matchValues", list);
        if (nbtSample != null) nbt.put("nbtSample", nbtSample);
        nbt.putBoolean("blacklist", blacklist);
        nbt.putBoolean("fuzzyNbt", fuzzyNbt);
    }

    public static LogisticsFilter loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        LogisticsFilter filter = new LogisticsFilter();
        filter.matchType = nbt.getString("matchType");
        net.minecraft.nbt.ListTag list = nbt.getList("matchValues", 8);
        for (int i = 0; i < list.size(); i++) filter.matchValues.add(list.getString(i));
        if (nbt.contains("nbtSample")) filter.nbtSample = nbt.getCompound("nbtSample");
        filter.blacklist = nbt.getBoolean("blacklist");
        filter.fuzzyNbt = nbt.getBoolean("fuzzyNbt");
        return filter;
    }
}
