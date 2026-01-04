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

public class SellPacket {
    private final Item item;
    private final int quantity;

    public SellPacket(Item item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    public SellPacket(FriendlyByteBuf buf) {
        this.item = buf.readRegistryIdSafe(Item.class);
        this.quantity = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeRegistryId(ForgeRegistries.ITEMS, item);
        buf.writeInt(quantity);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                if (StorePriceManager.isOre(item)) {
                    player.displayClientMessage(Component.literal("§cYou cannot sell ore blocks! Only buying is allowed."), true);
                    return;
                }
                int pricePerItem = StorePriceManager.getPrice(item);
                
                // Find how many the player actually has
                int totalInInventory = 0;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() == item) {
                        totalInInventory += stack.getCount();
                    }
                }
                for (ItemStack stack : player.getInventory().offhand) {
                    if (stack.getItem() == item) {
                        totalInInventory += stack.getCount();
                    }
                }
                // Note: Not checking armor slots for selling

                int actualQty = Math.min(quantity, totalInInventory);

                if (actualQty <= 0) {
                    player.displayClientMessage(Component.literal("§cYou don't have any " + item.getDescription().getString() + " to sell!"), true);
                    return;
                }

                // Remove from inventory
                int remainingToRemove = actualQty;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == item) {
                        int toRemove = Math.min(remainingToRemove, stack.getCount());
                        stack.shrink(toRemove);
                        remainingToRemove -= toRemove;
                        if (remainingToRemove <= 0) break;
                    }
                }

                long totalGain = (long) actualQty * pricePerItem;
                StorePriceManager.addMoney(player.getUUID(), totalGain);

                player.displayClientMessage(Component.literal("§aSold " + actualQty + "x " + item.getDescription().getString() + " for §e$" + StorePriceManager.formatCurrency(totalGain)), true);

                // Sync money to client
                StorePriceManager.sync(player);
                player.containerMenu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
