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

    public LogisticsFilter snapshot() {
        LogisticsFilter snap = new LogisticsFilter();
        snap.matchType = this.matchType;
        snap.matchValues = new ArrayList<>(this.matchValues);
        snap.nbtSample = this.nbtSample != null ? this.nbtSample.copy() : null;
        snap.blacklist = this.blacklist;
        snap.fuzzyNbt = this.fuzzyNbt;
        return snap;
    }
}
