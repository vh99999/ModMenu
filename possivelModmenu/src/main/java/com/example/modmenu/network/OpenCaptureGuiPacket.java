package com.example.modmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenCaptureGuiPacket {
    private final int entityId;
    private final String lootTableId;

    public OpenCaptureGuiPacket(int entityId) {
        this.entityId = entityId;
        this.lootTableId = null;
    }

    public OpenCaptureGuiPacket(String lootTableId) {
        this.entityId = -1;
        this.lootTableId = lootTableId;
    }

    public OpenCaptureGuiPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.lootTableId = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(lootTableId != null);
        if (lootTableId != null) buf.writeUtf(lootTableId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (lootTableId != null) {
                com.example.modmenu.client.ui.screen.CaptureMobScreen.open(lootTableId);
            } else {
                com.example.modmenu.client.ui.screen.CaptureMobScreen.open(entityId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
