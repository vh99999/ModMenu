package com.example.modmenu.store.logistics;

import java.util.UUID;

public class RuleTemplate {
    public UUID templateId;
    public String name;
    public LogisticsRule rule;

    public RuleTemplate() {
        this.templateId = UUID.randomUUID();
    }

    public RuleTemplate(String name, LogisticsRule rule) {
        this.templateId = UUID.randomUUID();
        this.name = name;
        this.rule = rule.snapshot();
    }

    public RuleTemplate snapshot() {
        RuleTemplate snap = new RuleTemplate();
        snap.templateId = this.templateId;
        snap.name = this.name;
        snap.rule = this.rule.snapshot();
        return snap;
    }

    public void saveNBT(net.minecraft.nbt.CompoundTag nbt) {
        if (templateId != null) nbt.putUUID("templateId", templateId);
        nbt.putString("name", name != null ? name : "");
        if (rule != null) {
            net.minecraft.nbt.CompoundTag ruleNbt = new net.minecraft.nbt.CompoundTag();
            rule.saveNBT(ruleNbt);
            nbt.put("rule", ruleNbt);
        }
    }

    public static RuleTemplate loadNBT(net.minecraft.nbt.CompoundTag nbt) {
        RuleTemplate template = new RuleTemplate();
        if (nbt.hasUUID("templateId")) template.templateId = nbt.getUUID("templateId");
        template.name = nbt.getString("name");
        if (nbt.contains("rule")) template.rule = LogisticsRule.loadNBT(nbt.getCompound("rule"));
        return template;
    }
}
