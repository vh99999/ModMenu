package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EnterDimensionPacket {
    public EnterDimensionPacket() {}

    public EnterDimensionPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                if (player.level().dimension().equals(com.example.modmenu.store.GenesisManager.GENESIS_DIM)) {
                    com.example.modmenu.store.GenesisManager.leaveGenesis(player);
                } else {
                    com.example.modmenu.store.GenesisManager.teleportToGenesis(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
