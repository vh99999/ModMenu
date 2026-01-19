package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

public class SellAllPacket {
    private final String modId;

    public SellAllPacket(String modId) {
        this.modId = modId;
    }

    public SellAllPacket(FriendlyByteBuf buf) {
        this.modId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.modId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
                BigDecimal totalEarned = BigDecimal.ZERO;
                
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;
                    
                    if (stack.getOrCreateTag().getInt("modmenu_lock_state") >= 1) continue;

                    String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    
                    if (settings.sellAllWhitelistActive) {
                        if (!settings.sellAllWhitelist.contains(itemId)) continue;
                    } else {
                        if (settings.sellAllBlacklist.contains(itemId)) continue;
                    }

                    String itemModId = ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace();
                    if (!modId.equals("ALL") && !itemModId.equals(modId)) continue;
                    
                    BigDecimal basePrice = StorePriceManager.getSellPrice(stack.getItem());
                    BigDecimal itemValue = basePrice.multiply(BigDecimal.valueOf(stack.getCount()));
                    StorePriceManager.recordSale(stack.getItem(), BigDecimal.valueOf(stack.getCount()));
                    
                    // Add enchantment value for vanilla enchants
                    Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
                    for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                        Enchantment enchant = entry.getKey();
                        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchant);
                        if (id != null && id.getNamespace().equals("minecraft")) {
                            BigDecimal enchantPrice = StorePriceManager.getEnchantPrice(enchant);
                            itemValue = itemValue.add(enchantPrice.multiply(BigDecimal.valueOf(entry.getValue())));
                        }
                    }
                    
                    totalEarned = totalEarned.add(itemValue);
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                
                if (totalEarned.compareTo(BigDecimal.ZERO) > 0) {
                    StorePriceManager.addMoney(player.getUUID(), totalEarned);
                    player.displayClientMessage(Component.literal("§aSold items for §e$" + StorePriceManager.formatCurrency(totalEarned)), true);
                    StorePriceManager.sync(player);
                    player.containerMenu.broadcastChanges();
                } else {
                    player.displayClientMessage(Component.literal("§cNothing to sell!"), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
