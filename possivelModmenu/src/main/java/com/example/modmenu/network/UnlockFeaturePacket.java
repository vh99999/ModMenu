package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class UnlockFeaturePacket {
    private final String featureId;
    private final BigDecimal cost;

    public UnlockFeaturePacket(String featureId, BigDecimal cost) {
        this.featureId = featureId;
        this.cost = cost;
    }

    public UnlockFeaturePacket(FriendlyByteBuf buf) {
        this.featureId = buf.readUtf();
        this.cost = new BigDecimal(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(featureId);
        buf.writeUtf(cost.toString());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BigDecimal money = StorePriceManager.getMoney(player.getUUID());
                if (money.compareTo(cost) >= 0 && !StorePriceManager.getUnlockedHouses(player.getUUID()).contains(featureId)) {
                    StorePriceManager.addMoney(player.getUUID(), cost.negate());
                    StorePriceManager.unlockHouse(player.getUUID(), featureId);
                    StorePriceManager.sync(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
