package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class UpdateEnchantPricePacket {
    private final ResourceLocation enchantId;
    private final int price;

    public UpdateEnchantPricePacket(ResourceLocation enchantId, int price) {
        this.enchantId = enchantId;
        this.price = price;
    }

    public UpdateEnchantPricePacket(FriendlyByteBuf buf) {
        this.enchantId = buf.readResourceLocation();
        this.price = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(enchantId);
        buf.writeInt(price);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player != null && StorePriceManager.isEditor(player.getUUID())) {
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
