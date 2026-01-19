package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestChamberLootPacket {
    private final int index;

    public RequestChamberLootPacket(int index) {
        this.index = index;
    }

    public RequestChamberLootPacket(FriendlyByteBuf buf) {
        this.index = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(index);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (index >= 0 && index < data.chambers.size()) {
                    PacketHandler.sendToPlayer(new SyncChamberLootPacket(index, data.chambers.get(index)), player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
