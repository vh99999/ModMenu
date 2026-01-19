package com.example.modmenu.network;

import com.example.modmenu.ServerForgeEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AcceptLootPacket {
    private final int entityId;

    public AcceptLootPacket(int entityId) {
        this.entityId = entityId;
    }

    public AcceptLootPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerForgeEvents.LootBuffer buffer = ServerForgeEvents.bufferedLoot.remove(entityId);
                if (buffer != null) {
                    for (ItemStack stack : buffer.drops) {
                        if (!stack.isEmpty()) {
                            ItemEntity entityItem = new ItemEntity(player.level(), buffer.pos.x, buffer.pos.y, buffer.pos.z, stack.copy());
                            player.level().addFreshEntity(entityItem);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
