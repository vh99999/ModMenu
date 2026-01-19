package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class UpdatePricePacket {
    private final Item item;
    private final BigDecimal price;

    public UpdatePricePacket(Item item, BigDecimal price) {
        this.item = item;
        this.price = price;
    }

    public UpdatePricePacket(FriendlyByteBuf buf) {
        this.item = buf.readRegistryIdSafe(Item.class);
        this.price = new BigDecimal(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeRegistryId(ForgeRegistries.ITEMS, item);
        buf.writeUtf(price.toString());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player != null && StorePriceManager.isEditor(player.getUUID())) {
                StorePriceManager.setPrice(item, price);
                StorePriceManager.sync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
