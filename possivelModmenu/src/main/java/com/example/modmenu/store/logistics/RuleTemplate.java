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
}
