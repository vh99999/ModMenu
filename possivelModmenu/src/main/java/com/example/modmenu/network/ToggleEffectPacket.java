package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleEffectPacket {
    private final String effectId;
    private final int level;

    public ToggleEffectPacket(String effectId, int level) {
        this.effectId = effectId;
        this.level = level;
    }

    public ToggleEffectPacket(FriendlyByteBuf buf) {
        this.effectId = buf.readUtf();
        this.level = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(effectId);
        buf.writeInt(level);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.toggleEffect(player.getUUID(), effectId, level);
                StorePriceManager.sync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
