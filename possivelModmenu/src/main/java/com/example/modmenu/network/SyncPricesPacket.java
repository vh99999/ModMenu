package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

public class SyncPricesPacket {
    private final Map<String, BigDecimal> buyPrices;
    private final Map<String, BigDecimal> sellPrices;
    private final Map<String, Long> soldVolume;
    private final Map<String, BigDecimal> enchantPrices;
    private final Map<String, BigDecimal> effectPrices;

    public SyncPricesPacket(Map<String, BigDecimal> buy, Map<String, BigDecimal> sell, Map<String, Long> volume, Map<String, BigDecimal> enchants, Map<String, BigDecimal> effects) {
        this.buyPrices = buy;
        this.sellPrices = sell;
        this.soldVolume = volume;
        this.enchantPrices = enchants;
        this.effectPrices = effects;
    }

    public SyncPricesPacket(FriendlyByteBuf buf) {
        this.buyPrices = buf.readMap(FriendlyByteBuf::readUtf, b -> new BigDecimal(b.readUtf()));
        this.sellPrices = buf.readMap(FriendlyByteBuf::readUtf, b -> new BigDecimal(b.readUtf()));
        this.soldVolume = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readLong);
        this.enchantPrices = buf.readMap(FriendlyByteBuf::readUtf, b -> new BigDecimal(b.readUtf()));
        this.effectPrices = buf.readMap(FriendlyByteBuf::readUtf, b -> new BigDecimal(b.readUtf()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeMap(buyPrices, FriendlyByteBuf::writeUtf, (b, v) -> b.writeUtf(v.toString()));
        buf.writeMap(sellPrices, FriendlyByteBuf::writeUtf, (b, v) -> b.writeUtf(v.toString()));
        buf.writeMap(soldVolume, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeLong);
        buf.writeMap(enchantPrices, FriendlyByteBuf::writeUtf, (b, v) -> b.writeUtf(v.toString()));
        buf.writeMap(effectPrices, FriendlyByteBuf::writeUtf, (b, v) -> b.writeUtf(v.toString()));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            StorePriceManager.setClientPrices(buyPrices, sellPrices, soldVolume, enchantPrices, effectPrices);
        });
        ctx.get().setPacketHandled(true);
    }
}