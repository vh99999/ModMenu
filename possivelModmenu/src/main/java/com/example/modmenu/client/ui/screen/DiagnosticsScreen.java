package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.ForgeRegistries;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DiagnosticsScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private long lastSnapshotTime = 0;
    private BigDecimal cachedMultiplier;
    private BigDecimal cachedInterestGain;
    private double cachedKillsPerSecond;
    private BigDecimal cachedSpGain = BigDecimal.ZERO;
    private BigDecimal cachedHpLimit;

    public DiagnosticsScreen(Screen parent) {
        super(Component.literal("System Diagnostics"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));
    }

    private void updateSnapshot() {
        long now = System.currentTimeMillis();
        if (now - lastSnapshotTime < 1000) return;
        lastSnapshotTime = now;
        
        // Income Multiplier
        cachedMultiplier = BigDecimal.ONE;
        int singularityRank = SkillManager.getActiveRank(StorePriceManager.clientSkills, "WEALTH_SINGULARITY");
        if (singularityRank > 0) {
            BigDecimal balance = StorePriceManager.playerMoney;
            BigDecimal divisor = new BigDecimal("10000000000");
            cachedMultiplier = BigDecimal.ONE.add(balance.divide(divisor, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(singularityRank)));
        }
        if (SkillManager.getActiveRank(StorePriceManager.clientSkills, "WEALTH_INTEREST_UNLIMIT") > 0) {
            cachedMultiplier = cachedMultiplier.multiply(BigDecimal.valueOf(2));
        }

        // Interest Forecast
        int basicSavings = SkillManager.getActiveRank(StorePriceManager.clientSkills, "WEALTH_BASIC_SAVINGS");
        int interestRateRank = SkillManager.getActiveRank(StorePriceManager.clientSkills, "WEALTH_INTEREST_RATE");
        int interestCapRank = SkillManager.getActiveRank(StorePriceManager.clientSkills, "WEALTH_INTEREST_CAP");
        boolean unlimit = StorePriceManager.clientSkills.activeToggles.contains("WEALTH_INTEREST_UNLIMIT");

        if (basicSavings > 0) {
            double rate = 0.01 + (0.01 * interestRateRank);
            cachedInterestGain = StorePriceManager.playerMoney.multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP);
            if (!unlimit) {
                BigDecimal cap = new BigDecimal("100000000").add(BigDecimal.valueOf(interestCapRank).multiply(new BigDecimal("10000000000")));
                if (cachedInterestGain.compareTo(cap) > 0) cachedInterestGain = cap;
            }
        } else {
            cachedInterestGain = null;
        }

        // Stasis
        int sovereignRank = SkillManager.getActiveRank(StorePriceManager.clientSkills, "COMBAT_SOVEREIGN_DOMAIN");
        if (sovereignRank > 0) {
            cachedHpLimit = BigDecimal.valueOf(1000).multiply(BigDecimal.TEN.pow(StorePriceManager.dampedExponent(sovereignRank - 1)));
        } else {
            cachedHpLimit = BigDecimal.valueOf(-1);
        }

        // Simulation Throughput
        int chamberCount = StorePriceManager.clientSkills.chambers.size();
        if (chamberCount > 0) {
            int systemOverclock = SkillManager.getActiveRank(StorePriceManager.clientSkills, "UTILITY_SYSTEM_OVERCLOCK");
            int virtClockRank = SkillManager.getActiveRank(StorePriceManager.clientSkills, "VIRT_CLOCK_SPEED");
            long intervalTicks = (long) (1200 * Math.pow(0.8, virtClockRank) / (1 + systemOverclock));
            if (virtClockRank >= 20) intervalTicks = 1;
            if (intervalTicks < 1) intervalTicks = 1;
            
            int multiThreadRank = SkillManager.getActiveRank(StorePriceManager.clientSkills, "VIRT_MULTI_THREAD");
            BigDecimal batchSize = BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(multiThreadRank));

            cachedKillsPerSecond = (20.0 / intervalTicks) * batchSize.doubleValue() * chamberCount;
        } else {
            cachedKillsPerSecond = 0;
        }

        // SP per minute
        int portfolioBasic = SkillManager.getActiveRank(StorePriceManager.clientSkills, "WEALTH_PORTFOLIO_BASIC");
        if (portfolioBasic > 0) {
            int yieldRank = SkillManager.getActiveRank(StorePriceManager.clientSkills, "WEALTH_PORTFOLIO_YIELD");
            BigDecimal baseDivisor = new BigDecimal("100000000000");
            BigDecimal yieldDivisor = new BigDecimal("10000000000");
            cachedSpGain = StorePriceManager.playerMoney.divide(baseDivisor, 0, RoundingMode.FLOOR);
            if (yieldRank > 0) {
                cachedSpGain = cachedSpGain.add(StorePriceManager.playerMoney.divide(yieldDivisor, 0, RoundingMode.FLOOR).multiply(BigDecimal.valueOf(yieldRank)));
            }
        } else {
            cachedSpGain = BigDecimal.ZERO;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        super.render(g, mx, my, pt);
        
        updateSnapshot();
        
        int startY = 50;
        int x = 50;
        int spacing = 15;

        g.drawString(font, "\u00A7b\u00A7l[ SYSTEM DIAGNOSTICS ]", x, startY - 10, 0xFFFFFFFF);
        startY += spacing;

        g.drawString(font, "\u00A76[ Economic Analysis ]", x, startY, 0xFFFFFFFF);
        startY += spacing;
        
        g.drawString(font, "Global Income Multiplier: \u00A7e" + StorePriceManager.formatCurrency(cachedMultiplier) + "x", x + 10, startY, 0xFFFFFFFF);
        startY += spacing;

        if (cachedInterestGain != null) {
            g.drawString(font, "Projected Interest (30s): \u00A7e$" + StorePriceManager.formatCurrency(cachedInterestGain), x + 10, startY, 0xFFFFFFFF);
        } else {
            g.drawString(font, "Projected Interest (30s): \u00A77N/A", x + 10, startY, 0xFFFFFFFF);
        }
        startY += spacing;

        // Drain
        g.drawString(font, "System Drain: \u00A7c-$" + StorePriceManager.formatCurrency(StorePriceManager.playerDrain) + "/s", x + 10, startY, 0xFFFFFFFF);
        startY += spacing * 2;

        g.drawString(font, "\u00A76[ Combat Metrics ]", x, startY, 0xFFFFFFFF);
        startY += spacing;
        
        g.drawString(font, "Total Souls Reaped: \u00A7d" + StorePriceManager.formatCurrency(StorePriceManager.clientSkills.totalKills), x + 10, startY, 0xFFFFFFFF);
        startY += spacing;

        // Soul Reap Stats
        g.drawString(font, "\u00A77- Permanent Attribute Gains:", x + 10, startY, 0xFFFFFFFF);
        startY += spacing;
        for (java.util.Map.Entry<String, BigDecimal> entry : StorePriceManager.clientSkills.permanentAttributes.entrySet()) {
            String attrId = entry.getKey();
            BigDecimal val = entry.getValue();
            String name = attrId.substring(attrId.lastIndexOf('.') + 1).replace('_', ' ');
            if (attrId.contains("max_health")) name = "Max HP";
            if (attrId.contains("attack_damage")) name = "Attack Damage";
            if (attrId.contains("movement_speed")) name = "Movement Speed";
            if (attrId.contains("attack_speed")) name = "Attack Speed";
            if (attrId.contains("reach_distance")) name = "Reach Distance";

            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            double currentVal = 0;
            double maxAttr = Double.MAX_VALUE;
            if (player != null) {
                Attribute attrObj = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(attrId));
                if (attrObj != null) {
                    AttributeInstance inst = player.getAttribute(attrObj);
                    if (inst != null) {
                        currentVal = inst.getValue();
                        if (attrObj instanceof RangedAttribute ranged) {
                            maxAttr = ranged.getMaxValue();
                        }
                    }
                    
                    if (attrId.equals("minecraft:generic.movement_speed")) maxAttr = Math.min(maxAttr, StorePriceManager.MAX_MOVEMENT_SPEED);
                    else if (attrId.equals("minecraft:generic.attack_speed")) maxAttr = Math.min(maxAttr, StorePriceManager.MAX_ATTACK_SPEED);
                    else if (attrId.equals("forge:reach_distance")) maxAttr = Math.min(maxAttr, StorePriceManager.MAX_REACH_DISTANCE);
                }
            }

            String line = "  " + name + ": \u00A7a+" + StorePriceManager.formatCurrency(val);
            if (currentVal >= maxAttr - 0.0001) line += " \u00A7c(MAX)";
            g.drawString(font, line, x + 10, startY, 0xFFFFFFFF);
            startY += spacing;
        }
        
        g.drawString(font, "Total Damage Reflected: \u00A7d" + StorePriceManager.formatCurrency(StorePriceManager.clientSkills.damageReflected), x + 10, startY, 0xFFFFFFFF);
        startY += spacing;

        g.drawString(font, "Total Damage Converted: \u00A7a" + StorePriceManager.formatCurrency(StorePriceManager.clientSkills.damageHealed), x + 10, startY, 0xFFFFFFFF);
        startY += spacing;
        
        if (cachedHpLimit.compareTo(BigDecimal.ZERO) > 0) {
            g.drawString(font, "Stasis Threshold: \u00A7b" + StorePriceManager.formatCurrency(cachedHpLimit) + " HP", x + 10, startY, 0xFFFFFFFF);
        } else {
            g.drawString(font, "Stasis Threshold: \u00A77N/A", x + 10, startY, 0xFFFFFFFF);
        }
        startY += spacing * 2;

        g.drawString(font, "\u00A76[ Virtualization Performance ]", x, startY, 0xFFFFFFFF);
        startY += spacing;
        
        int chamberCount = StorePriceManager.clientSkills.chambers.size();
        g.drawString(font, "Active Chambers: \u00A7e" + chamberCount, x + 10, startY, 0xFFFFFFFF);
        startY += spacing;

        if (chamberCount > 0) {
            g.drawString(font, "Simulation Throughput: \u00A7b" + String.format("%.2f", cachedKillsPerSecond) + " Kills/s", x + 10, startY, 0xFFFFFFFF);
            startY += spacing;
        }
        
        if (cachedSpGain.compareTo(BigDecimal.ZERO) > 0) {
            g.drawString(font, "Passive SP Flow: \u00A7d" + StorePriceManager.formatCurrency(cachedSpGain) + " SP/min", x + 10, startY, 0xFFFFFFFF);
        } else {
            g.drawString(font, "Passive SP Flow: \u00A770 SP/min", x + 10, startY, 0xFFFFFFFF);
        }
    }
}
