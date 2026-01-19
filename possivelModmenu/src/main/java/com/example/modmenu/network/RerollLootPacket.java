package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class RerollLootPacket {
    private final int index;
    private final boolean isChamber;

    public RerollLootPacket(int index, boolean isChamber) {
        this.index = index;
        this.isChamber = isChamber;
    }

    public RerollLootPacket(FriendlyByteBuf buf) {
        this.index = buf.readInt();
        this.isChamber = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(index);
        buf.writeBoolean(isChamber);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (isChamber) {
                    if (index >= 0 && index < data.chambers.size()) {
                        StorePriceManager.ChamberData chamber = data.chambers.get(index);
                        BigDecimal cost = BigDecimal.valueOf(100);
                        if (chamber.rerollCount > 0) {
                            cost = cost.multiply(BigDecimal.valueOf(2).pow(chamber.rerollCount));
                        }
                        
                        int lootRank = SkillManager.getActiveRank(data, "COMBAT_LOOT_RECALIBRATION");
                        if (lootRank >= 5 && chamber.rerollCount < 5) cost = BigDecimal.ZERO;

                        if (data.totalSP.subtract(data.spentSP).compareTo(cost) >= 0) {
                            data.spentSP = data.spentSP.add(cost);
                            chamber.rerollCount++;
                            chamber.storedLoot.clear();
                            SkillManager.simulateMobKillInternal(player, chamber, BigDecimal.ONE);
                            StorePriceManager.sync(player);
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Chamber] §aLoot Rerolled! §dCost: " + cost + " SP"), true);
                        } else {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot enough SP to reroll! Need " + cost), true);
                        }
                    }
                } else {
                    // Skill-based reroll for world mob (index is entityId)
                    com.example.modmenu.ServerForgeEvents.LootBuffer buffer = com.example.modmenu.ServerForgeEvents.bufferedLoot.get(index);
                    if (buffer != null) {
                        BigDecimal cost = BigDecimal.valueOf(100);
                        if (buffer.rerollCount > 0) {
                            cost = cost.multiply(BigDecimal.valueOf(2).pow(buffer.rerollCount));
                        }
                        
                        int lootRank = SkillManager.getActiveRank(data, "COMBAT_LOOT_RECALIBRATION");
                        if (lootRank >= 5 && buffer.rerollCount < 5) cost = BigDecimal.ZERO;

                        if (data.totalSP.subtract(data.spentSP).compareTo(cost) >= 0) {
                            data.spentSP = data.spentSP.add(cost);
                            buffer.rerollCount++;
                            
                            // Regenerate loot
                            net.minecraft.server.level.ServerLevel level = player.serverLevel();
                            net.minecraft.world.level.storage.loot.LootTable lootTable = level.getServer().getLootData().getLootTable(buffer.lootTable);
                            
                            net.minecraft.world.level.storage.loot.LootParams.Builder lootParamsBuilder = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, buffer.pos)
                                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.KILLER_ENTITY, player)
                                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_KILLER_ENTITY, player)
                                .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER, player)
                                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, player.getMainHandItem());

                            if (buffer.entityType != null) {
                                net.minecraft.world.entity.Entity dummy = buffer.entityType.create(level);
                                if (dummy != null) {
                                    lootParamsBuilder.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, dummy);
                                } else {
                                    return;
                                }
                            } else {
                                return;
                            }

                            if (SkillManager.getActiveRank(data, "VIRT_LOOT_INJECTION") > 0) {
                                lootParamsBuilder.withLuck(SkillManager.getActiveRank(data, "VIRT_LOOT_INJECTION") * 5.0f);
                            }

                            buffer.drops = lootTable.getRandomItems(lootParamsBuilder.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY));
                            
                            // Send updated drops back to client
                            PacketHandler.sendToPlayer(new OpenLootRecalibrationPacket(index, buffer.drops, buffer.rerollCount), player);
                            
                            StorePriceManager.sync(player);
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Loot Recalibration] §aLoot Rerolled! §dCost: " + cost + " SP"), true);
                        } else {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot enough SP to reroll! Need " + cost), true);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
