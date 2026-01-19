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
    private final int version;

    public SyncChamberLootPacket(int index, StorePriceManager.ChamberData chamber) {
        this.index = index;
        this.loot = new ArrayList<>();
        synchronized (chamber.storedLoot) {
            for (ItemStack stack : chamber.storedLoot) {
                this.loot.add(stack.copy());
            }
        }
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
                chamber.updateVersion = this.version;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
