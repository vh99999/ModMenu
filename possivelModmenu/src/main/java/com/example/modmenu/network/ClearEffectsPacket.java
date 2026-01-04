package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public class ClearEffectsPacket {
    private final boolean clearEverything;

    public ClearEffectsPacket() {
        this(false);
    }

    public ClearEffectsPacket(boolean clearEverything) {
        this.clearEverything = clearEverything;
    }

    public ClearEffectsPacket(FriendlyByteBuf buf) {
        this.clearEverything = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(clearEverything);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Map<String, Integer> active = StorePriceManager.getActiveEffects(player.getUUID());
                long cost = 0;
                for (int lvl : active.values()) {
                    cost += (long) (2000 * Math.pow(2, lvl - 1));
                }
                
                long currentMoney = StorePriceManager.getMoney(player.getUUID());
                if (currentMoney >= cost) {
                    StorePriceManager.setMoney(player.getUUID(), currentMoney - cost);
                    active.clear();
                    if (clearEverything) {
                        StorePriceManager.setAbilities(player.getUUID(), new StorePriceManager.AbilitySettings());
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aCleared all for §e$" + StorePriceManager.formatCurrency(cost)), true);
                    StorePriceManager.save();
                    StorePriceManager.sync(player);
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot enough money to clear effects! Cost: $" + StorePriceManager.formatCurrency(cost)), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
