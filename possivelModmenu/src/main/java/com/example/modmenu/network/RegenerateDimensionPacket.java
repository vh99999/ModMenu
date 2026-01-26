package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RegenerateDimensionPacket {
    public RegenerateDimensionPacket() {}

    public RegenerateDimensionPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (!data.genesisConfig.locked) {
                    com.example.modmenu.store.GenesisManager.regenerateGenesis(player);
                    
                    // Sync everyone so they see the "Wipe Pending" status
                    if (player.server != null) {
                        for (net.minecraft.server.level.ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                            StorePriceManager.sync(p);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
