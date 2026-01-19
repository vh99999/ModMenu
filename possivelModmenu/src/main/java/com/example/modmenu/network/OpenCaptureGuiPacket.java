package com.example.modmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenCaptureGuiPacket {
    private final int entityId;

    public OpenCaptureGuiPacket(int entityId) {
        this.entityId = entityId;
    }

    public OpenCaptureGuiPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.modmenu.client.ui.screen.CaptureMobScreen.open(entityId);
        });
        ctx.get().setPacketHandled(true);
    }
}
