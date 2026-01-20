package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

public class EnchantItemPacket {
    private final ResourceLocation enchantId;
    private final int level;
    private final int slotIndex;

    public EnchantItemPacket(ResourceLocation enchantId, int level, int slotIndex) {
        this.enchantId = enchantId;
        this.level = level;
        this.slotIndex = slotIndex;
    }

    public EnchantItemPacket(FriendlyByteBuf buf) {
        this.enchantId = buf.readResourceLocation();
        this.level = buf.readInt();
        this.slotIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(enchantId);
        buf.writeInt(level);
        buf.writeInt(slotIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchantId);
                if (enchantment == null) return;

                ItemStack stack = player.getInventory().getItem(slotIndex);
                if (stack.isEmpty()) return;

                BigDecimal basePrice = StorePriceManager.getEnchantPrice(enchantment);
                BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());

                if (level > 0) {
                    BigDecimal cost = basePrice.multiply(BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(level - 1)));
                    if (currentMoney.compareTo(cost) >= 0) {
                        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
                        enchants.put(enchantment, level);
                        EnchantmentHelper.setEnchantments(enchants, stack);

                        StorePriceManager.setMoney(player.getUUID(), currentMoney.subtract(cost));
                        player.displayClientMessage(Component.literal("§aEnchanted with " + enchantment.getFullname(level).getString() + " for §e$" + StorePriceManager.formatCurrency(cost)), true);
                    } else {
                        player.displayClientMessage(Component.literal("§cNot enough money!"), true);
                    }
                } else {
                    int currentLvl = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
                    if (currentLvl > 0) {
                        BigDecimal cost = basePrice.multiply(BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(currentLvl - 1)));
                        if (currentMoney.compareTo(cost) >= 0) {
                            Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
                            enchants.remove(enchantment);
                            EnchantmentHelper.setEnchantments(enchants, stack);

                            StorePriceManager.setMoney(player.getUUID(), currentMoney.subtract(cost));
                            player.displayClientMessage(Component.literal("§cRemoved " + enchantment.getFullname(currentLvl).getString() + " for §e$" + StorePriceManager.formatCurrency(cost)), true);
                        } else {
                            player.displayClientMessage(Component.literal("§cNot enough money to remove!"), true);
                        }
                    }
                }
                
                // Sync money
                StorePriceManager.sync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
