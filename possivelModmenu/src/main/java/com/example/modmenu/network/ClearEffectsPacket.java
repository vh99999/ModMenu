package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
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
                BigDecimal cost = BigDecimal.ZERO;
                for (int lvl : active.values()) {
                    cost = cost.add(BigDecimal.valueOf(2000).multiply(BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(lvl - 1))));
                }
                
                BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
                if (currentMoney.compareTo(cost) >= 0) {
                    StorePriceManager.setMoney(player.getUUID(), currentMoney.subtract(cost));
                    active.clear();
                    if (clearEverything) {
                        StorePriceManager.setAbilities(player.getUUID(), new StorePriceManager.AbilitySettings());
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7aCleared all for \u00A7e$" + StorePriceManager.formatCurrency(cost)), true);
                    StorePriceManager.save();
                    StorePriceManager.sync(player);
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cNot enough money to clear effects! Cost: $" + StorePriceManager.formatCurrency(cost)), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
