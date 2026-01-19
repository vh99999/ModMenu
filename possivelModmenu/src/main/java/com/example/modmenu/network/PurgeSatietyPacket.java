package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

public class PurgeSatietyPacket {
    private final String mobId;

    public PurgeSatietyPacket(String mobId) {
        this.mobId = mobId;
    }

    public PurgeSatietyPacket(FriendlyByteBuf buf) {
        this.mobId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.mobId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                float satiety = data.mobSatiety.getOrDefault(mobId, 0f);
                if (satiety <= 0) return;
                
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(mobId));
                if (type == null) return;
                
                double baseHP = 20.0;
                try {
                    net.minecraft.world.entity.ai.attributes.AttributeSupplier supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier((net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) type);
                    if (supplier != null) {
                        baseHP = supplier.getBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    }
                } catch (Exception ignored) {}
                
                BigDecimal cost;
                double pow = Math.pow(baseHP, 1.8);
                if (Double.isInfinite(pow)) {
                    cost = BigDecimal.valueOf(baseHP).pow(2).multiply(BigDecimal.valueOf(satiety)).multiply(BigDecimal.valueOf(1000));
                } else {
                    cost = BigDecimal.valueOf(pow).multiply(BigDecimal.valueOf(satiety)).multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP);
                }

                if (StorePriceManager.canAfford(player.getUUID(), cost)) {
                    StorePriceManager.addMoney(player.getUUID(), cost.negate());
                    data.mobSatiety.remove(mobId);
                    player.displayClientMessage(Component.literal("§aPurged Satiety for " + mobId + "! Cost: §e$" + StorePriceManager.formatCurrency(cost)), true);
                    StorePriceManager.sync(player);
                } else {
                    player.displayClientMessage(Component.literal("§cNot enough money to purge! Need §e$" + StorePriceManager.formatCurrency(cost)), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
