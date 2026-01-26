package com.example.modmenu.store.logistics;

import java.util.UUID;

public class LogicCondition {
    public UUID targetId;
    public boolean isGroup;
    public String type = "ITEMS"; // "ITEMS", "ENERGY", "FLUIDS"
    public String operator = "LESS"; // "LESS", "GREATER", "EQUAL"
    public int value = 0;
    public LogisticsFilter filter = new LogisticsFilter();

    public LogicCondition snapshot() {
        LogicCondition snap = new LogicCondition();
        snap.targetId = this.targetId;
        snap.isGroup = this.isGroup;
        snap.type = this.type;
        snap.operator = this.operator;
        snap.value = this.value;
        snap.filter = this.filter.snapshot();
        return snap;
    }
}
