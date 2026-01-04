package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public class SyncPricesPacket {
    private final Map<String, Integer> buyPrices;
    private final Map<String, Integer> sellPrices;
    private final Map<String, Integer> enchantPrices;
    private final Map<String, Integer> effectPrices;

    public SyncPricesPacket(Map<String, Integer> buy, Map<String, Integer> sell, Map<String, Integer> enchants, Map<String, Integer> effects) {
        this.buyPrices = buy;
        this.sellPrices = sell;
        this.enchantPrices = enchants;
        this.effectPrices = effects;
    }

    public SyncPricesPacket(FriendlyByteBuf buf) {
        this.buyPrices = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        this.sellPrices = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        this.enchantPrices = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        this.effectPrices = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeMap(buyPrices, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeMap(sellPrices, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeMap(enchantPrices, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeMap(effectPrices, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            StorePriceManager.setClientPrices(buyPrices, sellPrices, enchantPrices, effectPrices);
        });
        ctx.get().setPacketHandled(true);
    }
}