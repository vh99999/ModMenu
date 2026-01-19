package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncChamberLootPacket {
    private final int index;
    private final List<ItemStack> loot;
    private final List<ItemStack> input;
    private final java.util.Map<String, Integer> yield;
    private final int version;

    public SyncChamberLootPacket(int index, StorePriceManager.ChamberData chamber) {
        this.index = index;
        this.loot = new ArrayList<>();
        synchronized (chamber.storedLoot) {
            for (ItemStack stack : chamber.storedLoot) {
                this.loot.add(stack.copy());
            }
        }
        this.input = new ArrayList<>();
        for (ItemStack stack : chamber.inputBuffer) {
            this.input.add(stack.copy());
        }
        this.yield = new java.util.HashMap<>(chamber.yieldTargets);
        this.version = chamber.updateVersion;
    }

    public SyncChamberLootPacket(FriendlyByteBuf buf) {
        this.index = buf.readInt();
        int size = buf.readInt();
        this.loot = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (buf.readBoolean()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
                int count = buf.readInt();
                net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                ItemStack stack = new ItemStack(item == null ? net.minecraft.world.item.Items.AIR : item, count);
                stack.setTag(tag);
                this.loot.add(stack);
            } else {
                this.loot.add(ItemStack.EMPTY);
            }
        }
        
        int inputSize = buf.readInt();
        this.input = new ArrayList<>(inputSize);
        for (int i = 0; i < inputSize; i++) {
            if (buf.readBoolean()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
                int count = buf.readInt();
                net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                ItemStack stack = new ItemStack(item == null ? net.minecraft.world.item.Items.AIR : item, count);
                stack.setTag(tag);
                this.input.add(stack);
            } else {
                this.input.add(ItemStack.EMPTY);
            }
        }
        
        this.yield = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        this.version = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(index);
        buf.writeInt(loot.size());
        for (ItemStack stack : loot) {
            if (stack == null || stack.isEmpty()) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeResourceLocation(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
                buf.writeInt(stack.getCount());
                buf.writeNbt(stack.getTag());
            }
        }
        
        buf.writeInt(input.size());
        for (ItemStack stack : input) {
            if (stack == null || stack.isEmpty()) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeResourceLocation(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
                buf.writeInt(stack.getCount());
                buf.writeNbt(stack.getTag());
            }
        }
        
        buf.writeMap(yield, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeInt(version);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (index >= 0 && index < StorePriceManager.clientSkills.chambers.size()) {
                StorePriceManager.ChamberData chamber = StorePriceManager.clientSkills.chambers.get(index);
                synchronized (chamber.storedLoot) {
                    chamber.storedLoot.clear();
                    chamber.storedLoot.addAll(this.loot);
                }
                chamber.inputBuffer.clear();
                chamber.inputBuffer.addAll(this.input);
                chamber.yieldTargets.clear();
                chamber.yieldTargets.putAll(this.yield);
                chamber.updateVersion = this.version;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
