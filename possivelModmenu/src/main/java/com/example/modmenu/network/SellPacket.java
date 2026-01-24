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
                    player.displayClientMessage(Component.literal("\u00A7cYou cannot sell ore blocks! Only buying is allowed."), true);
                    return;
                }
                BigDecimal pricePerItem = StorePriceManager.getSellPrice(item, player.getUUID());
                
                // Find how many the player actually has
                int totalInInventory = 0;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() == item && stack.getOrCreateTag().getInt("modmenu_lock_state") == 0) {
                        totalInInventory += stack.getCount();
                    }
                }
                for (ItemStack stack : player.getInventory().offhand) {
                    if (stack.getItem() == item && stack.getOrCreateTag().getInt("modmenu_lock_state") == 0) {
                        totalInInventory += stack.getCount();
                    }
                }
                // Note: Not checking armor slots for selling

                int actualQty = Math.min(quantity, totalInInventory);

                if (actualQty <= 0) {
                    player.displayClientMessage(Component.literal("\u00A7cYou don't have any unprotected " + item.getDescription().getString() + " to sell!"), true);
                    return;
                }

                // Remove from inventory
                int remainingToRemove = actualQty;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == item && stack.getOrCreateTag().getInt("modmenu_lock_state") == 0) {
                        int toRemove = Math.min(remainingToRemove, stack.getCount());
                        stack.shrink(toRemove);
                        remainingToRemove -= toRemove;
                        if (remainingToRemove <= 0) break;
                    }
                }

                BigDecimal totalGain = pricePerItem.multiply(BigDecimal.valueOf(actualQty));
                StorePriceManager.addMoney(player.getUUID(), totalGain);
                StorePriceManager.recordSale(item, BigDecimal.valueOf(actualQty));

                player.displayClientMessage(Component.literal("\u00A7aSold " + actualQty + "x " + item.getDescription().getString() + " for \u00A7e$" + StorePriceManager.formatCurrency(totalGain)), true);

                // Sync money to client
                StorePriceManager.sync(player);
                player.containerMenu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
