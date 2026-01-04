package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class SmeltAllPacket {
    public SmeltAllPacket() {}
    public SmeltAllPacket(FriendlyByteBuf buf) {}
    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
                int coalPrice = StorePriceManager.getPrice(Items.COAL);
                if (coalPrice <= 0) coalPrice = 128;
                
                int totalCoalNeeded = 0;
                List<Integer> slotsToSmelt = new ArrayList<>();
                List<ItemStack> results = new ArrayList<>();
                
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;
                    
                    String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    if (!settings.smelterWhitelist.contains(itemId)) continue;
                    
                    Optional<SmeltingRecipe> recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), player.level());
                    
                    if (recipe.isPresent()) {
                        ItemStack result = recipe.get().getResultItem(player.level().registryAccess());
                        if (!result.isEmpty()) {
                            int count = stack.getCount();
                            ItemStack smeltedStack = result.copy();
                            smeltedStack.setCount(count * result.getCount());
                            
                            slotsToSmelt.add(i);
                            results.add(smeltedStack);
                            totalCoalNeeded += (int) Math.ceil(count / 8.0);
                        }
                    }
                }
                
                if (!slotsToSmelt.isEmpty()) {
                    long coalCost = (long) totalCoalNeeded * coalPrice;
                    long fee = coalCost; // 100% fee
                    long totalCost = coalCost + fee;
                    
                    long currentMoney = StorePriceManager.getMoney(player.getUUID());
                    if (currentMoney >= totalCost) {
                        StorePriceManager.setMoney(player.getUUID(), currentMoney - totalCost);
                        
                        for (int i = 0; i < slotsToSmelt.size(); i++) {
                            player.getInventory().setItem(slotsToSmelt.get(i), results.get(i));
                        }
                        
                        player.displayClientMessage(Component.literal("§aSmelted " + results.size() + " stacks. Cost: §e$" + StorePriceManager.formatCurrency(totalCost)), true);
                        StorePriceManager.sync(player);
                    } else {
                        player.displayClientMessage(Component.literal("§cNot enough money! Need §e$" + StorePriceManager.formatCurrency(totalCost)), true);
                    }
                } else {
                    player.displayClientMessage(Component.literal("§cNo smeltable items from whitelist in inventory!"), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
