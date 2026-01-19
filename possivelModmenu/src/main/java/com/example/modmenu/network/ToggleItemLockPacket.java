package com.example.modmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleItemLockPacket {
    private final int slotIndex;

    public ToggleItemLockPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public ToggleItemLockPacket(FriendlyByteBuf buf) {
        this.slotIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(slotIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack stack = ItemStack.EMPTY;
                if (slotIndex >= 0 && slotIndex < player.getInventory().getContainerSize()) {
                    stack = player.getInventory().getItem(slotIndex);
                }

                if (!stack.isEmpty()) {
                    int currentState = stack.getOrCreateTag().getInt("modmenu_lock_state");
                    int newState = (currentState + 1) % 3;
                    stack.getOrCreateTag().putInt("modmenu_lock_state", newState);

                    String message = switch (newState) {
                        case 1 -> "§cItem Locked (No Sell/Drop)";
                        case 2 -> "§bItem Frozen (No Move)";
                        default -> "§aItem Protection Disabled";
                    };
                    player.displayClientMessage(Component.literal(message), true);
                    
                    // Force a container update to sync NBT back to client
                    player.containerMenu.broadcastChanges();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
