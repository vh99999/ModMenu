package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import com.example.modmenu.store.StorePriceManager.SkillData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class SynthesisPacket {
    private final ResourceLocation itemId;

    public SynthesisPacket(ResourceLocation itemId) {
        this.itemId = itemId;
    }

    public SynthesisPacket(FriendlyByteBuf buf) {
        this.itemId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(itemId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (SkillManager.getActiveRank(data, "UTILITY_MOLECULAR_SYNTHESIS") > 0) {
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item != null) {
                        BigDecimal itemPrice = StorePriceManager.getBuyPrice(item, player.getUUID());
                        BigDecimal cost = itemPrice.multiply(BigDecimal.valueOf(10));
                        
                        // Base cost if item has no price and not free via Monopoly
                        if (cost.compareTo(BigDecimal.ZERO) == 0 && !data.activeToggles.contains("WEALTH_KEYSTONE_MONOPOLY")) {
                            cost = BigDecimal.valueOf(1000000);
                        }
                        
                        if (StorePriceManager.canAfford(player.getUUID(), cost)) {
                            StorePriceManager.addMoney(player.getUUID(), cost.negate());
                            player.getInventory().add(new ItemStack(item));
                            StorePriceManager.sync(player);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
