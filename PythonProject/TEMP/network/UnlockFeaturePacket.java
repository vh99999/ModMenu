package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UnlockFeaturePacket {
    private final String featureId;
    private final long cost;

    public UnlockFeaturePacket(String featureId, long cost) {
        this.featureId = featureId;
        this.cost = cost;
    }

    public UnlockFeaturePacket(FriendlyByteBuf buf) {
        this.featureId = buf.readUtf();
        this.cost = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(featureId);
        buf.writeLong(cost);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                long money = StorePriceManager.getMoney(player.getUUID());
                if (money >= cost && !StorePriceManager.getUnlockedHouses(player.getUUID()).contains(featureId)) {
                    StorePriceManager.addMoney(player.getUUID(), -cost);
                    StorePriceManager.unlockHouse(player.getUUID(), featureId);
                    StorePriceManager.sync(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
