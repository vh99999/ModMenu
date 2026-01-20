package com.example.modmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import com.example.modmenu.store.StorePriceManager;

public class DataCorruptionPacket {
    private final String error;

    public DataCorruptionPacket(String error) {
        this.error = error;
    }

    public DataCorruptionPacket(FriendlyByteBuf buf) {
        this.error = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.error);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.example.modmenu.client.ui.screen.CorruptionWarningScreen.show(error);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
