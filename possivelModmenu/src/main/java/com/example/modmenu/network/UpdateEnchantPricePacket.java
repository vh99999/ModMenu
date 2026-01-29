package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.StoreSecurity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class UpdateEnchantPricePacket {
    private final ResourceLocation enchantId;
    private final BigDecimal price;

    public UpdateEnchantPricePacket(ResourceLocation enchantId, BigDecimal price) {
        this.enchantId = enchantId;
        this.price = price;
    }

    public UpdateEnchantPricePacket(FriendlyByteBuf buf) {
        this.enchantId = buf.readResourceLocation();
        this.price = new BigDecimal(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(enchantId);
        buf.writeUtf(price.toString());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player != null && StoreSecurity.canModifyPrices(player)) {
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchantId);
                if (enchantment != null) {
                    StorePriceManager.setEnchantPrice(enchantment, price);
                    StorePriceManager.sync(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
