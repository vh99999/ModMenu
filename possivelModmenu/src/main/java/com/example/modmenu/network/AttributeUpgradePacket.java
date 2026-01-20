package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

public class AttributeUpgradePacket {
    private final String attributeId;
    private final boolean increase;

    public AttributeUpgradePacket(String attributeId, boolean increase) {
        this.attributeId = attributeId;
        this.increase = increase;
    }

    public AttributeUpgradePacket(FriendlyByteBuf buf) {
        this.attributeId = buf.readUtf();
        this.increase = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(attributeId);
        buf.writeBoolean(increase);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                double currentBonus = StorePriceManager.getAttributeBonuses(player.getUUID()).getOrDefault(attributeId, 0.0);
                
                // Exponential scaling cost: $1M * 2^currentBonus (damped exponent)
                BigDecimal cost = BigDecimal.valueOf(1000000).multiply(BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent((int)currentBonus)));
                
                if (increase) {
                    if (StorePriceManager.canAfford(player.getUUID(), cost)) {
                        StorePriceManager.addMoney(player.getUUID(), cost.negate());
                        StorePriceManager.setAttributeBonus(player.getUUID(), attributeId, currentBonus + 1.0);
                        StorePriceManager.applyAttribute(player, attributeId, currentBonus + 1.0, false);
                        if (attributeId.equals("minecraft:generic.max_health")) {
                            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
                        }
                        StorePriceManager.sync(player);
                    }
                } else {
                    if (currentBonus > 0) {
                        StorePriceManager.setAttributeBonus(player.getUUID(), attributeId, currentBonus - 1.0);
                        StorePriceManager.applyAttribute(player, attributeId, currentBonus - 1.0, false);
                        if (attributeId.equals("minecraft:generic.max_health")) {
                            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
                        }
                        StorePriceManager.sync(player);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
