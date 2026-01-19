package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class UpdateEffectPricePacket {
    private final String effectId;
    private final BigDecimal price;

    public UpdateEffectPricePacket(String effectId, BigDecimal price) {
        this.effectId = effectId;
        this.price = price;
    }

    public UpdateEffectPricePacket(FriendlyByteBuf buf) {
        this.effectId = buf.readUtf();
        this.price = new BigDecimal(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(effectId);
        buf.writeUtf(price.toString());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player != null && StorePriceManager.isEditor(player.getUUID())) {
                StorePriceManager.setEffectBasePrice(effectId, price);
                StorePriceManager.sync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
