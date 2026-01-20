package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillDefinitions;
import com.example.modmenu.store.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class SkillUpgradePacket {
    private final String skillId;

    public SkillUpgradePacket(String skillId) {
        this.skillId = skillId;
    }

    public SkillUpgradePacket(FriendlyByteBuf buf) {
        this.skillId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.skillId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                SkillDefinitions.SkillPath path = SkillDefinitions.ALL_SKILLS.get(skillId);
                
                if (path == null) return;
                
                int currentMaxRank = data.unlockedRanks.getOrDefault(skillId, 0);
                if (currentMaxRank >= path.maxRank) return;

                // Prerequisite Logic
                if (path.prerequisiteId != null) {
                    int prereqRank = data.unlockedRanks.getOrDefault(path.prerequisiteId, 0);
                    if (prereqRank < path.prerequisiteRank) {
                        SkillDefinitions.SkillPath prereq = SkillDefinitions.ALL_SKILLS.get(path.prerequisiteId);
                        player.displayClientMessage(Component.literal("§cRequires " + prereq.name + " Rank " + path.prerequisiteRank), true);
                        return;
                    }
                }
                
                // Branch Logic
                String branchStr = path.branch.name();
                if (!data.branchOrder.contains(branchStr)) {
                    // Trying to start a new branch
                    if (!data.branchOrder.isEmpty()) {
                        // Must finish previous branch keystone
                        String lastBranch = data.branchOrder.get(data.branchOrder.size() - 1);
                        boolean hasKeystone = false;
                        
                        // Mapping branch names to potential ID prefixes
                        String prefix = lastBranch;
                        if (lastBranch.equals("CONTAINMENT")) prefix = "VIRT";
                        
                        for (String unlocked : data.unlockedRanks.keySet()) {
                            if (unlocked.contains("_KEYSTONE") && unlocked.startsWith(prefix)) {
                                hasKeystone = true;
                                break;
                            }
                        }
                        if (!hasKeystone) {
                            BigDecimal licenseFeeMoney = new BigDecimal("10000000000000"); // $10 Trillion
                            BigDecimal licenseFeeSP = new BigDecimal("500000"); // 500k SP
                            
                            BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
                            BigDecimal availableSP = data.totalSP.subtract(data.spentSP);
                            
                            if (currentMoney.compareTo(licenseFeeMoney) >= 0) {
                                StorePriceManager.addMoney(player.getUUID(), licenseFeeMoney.negate());
                                player.displayClientMessage(Component.literal("§6[Access License] §aPaid $10 Trillion to open a new branch without a Keystone!"), true);
                            } else if (availableSP.compareTo(licenseFeeSP) >= 0) {
                                data.spentSP = data.spentSP.add(licenseFeeSP);
                                player.displayClientMessage(Component.literal("§6[Access License] §aPaid 500,000 SP to open a new branch without a Keystone!"), true);
                            } else {
                                player.displayClientMessage(Component.literal("§cYou must unlock an Ultimate Keystone first, or pay the $10 Trillion / 500,000 SP License Fee!"), true);
                                return;
                            }
                        }
                    }
                    data.branchOrder.add(branchStr);
                }

                // Keystone Protocol: Only one Keystone per branch
                if (skillId.contains("_KEYSTONE")) {
                    String currentPrefix = path.branch.name();
                    if (currentPrefix.equals("CONTAINMENT")) currentPrefix = "VIRT";

                    for (String unlocked : data.unlockedRanks.keySet()) {
                        if (unlocked.contains("_KEYSTONE") && unlocked.startsWith(currentPrefix)) {
                            player.displayClientMessage(Component.literal("§cSingle Keystone Protocol active: Only one Ultimate Keystone per branch allowed!"), true);
                            return;
                        }
                    }
                }
                
                BigDecimal multiplier = StorePriceManager.getBranchMultiplier(player.getUUID(), branchStr);
                BigDecimal cost = SkillManager.getSkillCost(skillId, currentMaxRank + 1, multiplier, player.getUUID());
                
                if (data.totalSP.subtract(data.spentSP).compareTo(cost) >= 0) {
                    data.spentSP = data.spentSP.add(cost);
                    data.unlockedRanks.put(skillId, currentMaxRank + 1);
                    data.skillRanks.put(skillId, currentMaxRank + 1); // Set active rank to max by default
                    data.activeToggles.add(skillId); // Auto-enable on purchase
                    player.displayClientMessage(Component.literal("§dUpgraded " + path.name + " to Rank " + (currentMaxRank + 1)), true);
                    StorePriceManager.markDirty(player.getUUID());
                    StorePriceManager.applyAllAttributes(player);
                    StorePriceManager.sync(player);
                } else {
                    player.displayClientMessage(Component.literal("§cNot enough Skill Points! Need " + cost), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
