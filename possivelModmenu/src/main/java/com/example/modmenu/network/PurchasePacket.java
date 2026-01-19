package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class PurchasePacket {
    private final Item item;
    private final String specialId;
    private final int quantity;

    public PurchasePacket(Item item, int quantity) {
        this.item = item;
        this.specialId = null;
        this.quantity = quantity;
    }

    public PurchasePacket(String specialId) {
        this.item = null;
        this.specialId = specialId;
        this.quantity = 1;
    }

    public PurchasePacket(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            this.item = buf.readRegistryIdSafe(Item.class);
            this.specialId = null;
        } else {
            this.item = null;
            this.specialId = buf.readUtf();
        }
        this.quantity = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(item != null);
        if (item != null) {
            buf.writeRegistryId(ForgeRegistries.ITEMS, item);
        } else {
            buf.writeUtf(specialId);
        }
        buf.writeInt(quantity);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                if (specialId != null) {
                    if ("mining_dimension".equals(specialId)) {
                        BigDecimal cost = BigDecimal.valueOf(100000000);
                        BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
                        if (currentMoney.compareTo(cost) >= 0) {
                            StorePriceManager.setMoney(player.getUUID(), currentMoney.subtract(cost));
                            StorePriceManager.unlockHouse(player.getUUID(), "mining_dimension");
                            player.displayClientMessage(Component.literal("§aHouse Unlocked!"), true);
                            StorePriceManager.sync(player);
                        } else {
                            player.displayClientMessage(Component.literal("§cNot enough money! Cost: $100,000,000"), true);
                        }
                    }
                    return;
                }

                BigDecimal pricePerItem = StorePriceManager.getPrice(item);
                
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                boolean monopoly = data.activeToggles.contains("WEALTH_KEYSTONE_MONOPOLY");
                boolean freeToken = data.activeToggles.contains("FREE_PURCHASE_TOKEN");
                
                if (monopoly || freeToken) pricePerItem = BigDecimal.ZERO;

                BigDecimal totalCost = pricePerItem.multiply(BigDecimal.valueOf(quantity));
                BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());

                if (!StorePriceManager.canAfford(player.getUUID(), totalCost)) {
                    player.displayClientMessage(Component.literal("§cNot enough money!"), true);
                    return;
                }

                StorePriceManager.addMoney(player.getUUID(), totalCost.negate());
                
                if (freeToken && totalCost.compareTo(BigDecimal.ZERO) == 0 && quantity > 0) {
                    data.activeToggles.remove("FREE_PURCHASE_TOKEN");
                }

                // Monopoly Cashback
                if (monopoly && totalCost.compareTo(BigDecimal.ZERO) == 0) {
                    BigDecimal originalPrice = StorePriceManager.getPrice(item);
                    BigDecimal cashback = originalPrice.multiply(BigDecimal.valueOf(quantity)).multiply(BigDecimal.valueOf(0.01));
                    if (cashback.compareTo(BigDecimal.ZERO) > 0) StorePriceManager.addMoney(player.getUUID(), cashback);
                }

                // Give item
                ItemStack stackToGive = new ItemStack(item, quantity);
                if (!player.getInventory().add(stackToGive)) {
                    player.drop(stackToGive, false);
                }

                String message = "§aBought " + quantity + "x " + item.getDescription().getString();
                message += " for §e$" + StorePriceManager.formatCurrency(totalCost);
                player.displayClientMessage(Component.literal(message), true);

                // Sync money to client
                StorePriceManager.sync(player);
                player.containerMenu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
