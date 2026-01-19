package com.example.modmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenLootRecalibrationPacket {
    private final int entityId;
    private final List<ItemStack> drops;
    private final int rerollCount;

    public OpenLootRecalibrationPacket(int entityId, List<ItemStack> drops, int rerollCount) {
        this.entityId = entityId;
        this.drops = drops;
        this.rerollCount = rerollCount;
    }

    public OpenLootRecalibrationPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        int size = buf.readInt();
        this.drops = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (buf.readBoolean()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
                int count = buf.readInt();
                net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item == null ? net.minecraft.world.item.Items.AIR : item, count);
                stack.setTag(tag);
                this.drops.add(stack);
            } else {
                this.drops.add(net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        this.rerollCount = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(drops.size());
        for (net.minecraft.world.item.ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeResourceLocation(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
                buf.writeInt(stack.getCount());
                buf.writeNbt(stack.getTag());
            }
        }
        buf.writeInt(rerollCount);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.modmenu.client.ui.screen.LootRecalibrationScreen screen = new com.example.modmenu.client.ui.screen.LootRecalibrationScreen(entityId, drops, rerollCount);
            net.minecraft.client.Minecraft.getInstance().setScreen(screen);
        });
        ctx.get().setPacketHandled(true);
    }
}
