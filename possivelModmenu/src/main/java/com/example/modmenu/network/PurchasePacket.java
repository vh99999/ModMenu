package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

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
                        long cost = 100000000;
                        long currentMoney = StorePriceManager.getMoney(player.getUUID());
                        if (currentMoney >= cost) {
                            StorePriceManager.setMoney(player.getUUID(), currentMoney - cost);
                            StorePriceManager.unlockHouse(player.getUUID(), "mining_dimension");
                            player.displayClientMessage(Component.literal("§aHouse Unlocked!"), true);
                            StorePriceManager.sync(player);
                        } else {
                            player.displayClientMessage(Component.literal("§cNot enough money! Cost: $100,000,000"), true);
                        }
                    }
                    return;
                }

                int pricePerItem = StorePriceManager.getPrice(item);
                if (pricePerItem <= 0) pricePerItem = 1; // Safety check
                
                long currentMoney = StorePriceManager.getMoney(player.getUUID());
                long maxAffordable = currentMoney / pricePerItem;
                int actualQty = (int) Math.min((long)quantity, maxAffordable);

                if (actualQty <= 0) {
                    player.displayClientMessage(Component.literal("§cNot enough money!"), true);
                    return;
                }

                long totalCost = (long) actualQty * pricePerItem;
                StorePriceManager.setMoney(player.getUUID(), currentMoney - totalCost);

                // Give item
                ItemStack stackToGive = new ItemStack(item, actualQty);
                if (!player.getInventory().add(stackToGive)) {
                    player.drop(stackToGive, false);
                }

                String message = "§aBought " + actualQty + "x " + item.getDescription().getString();
                if (actualQty < quantity) {
                    message += " §7(requested " + quantity + ")";
                }
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
