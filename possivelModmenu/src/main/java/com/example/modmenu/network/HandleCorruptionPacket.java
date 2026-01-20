package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class HandleCorruptionPacket {
    private final boolean reset;

    public HandleCorruptionPacket(boolean reset) {
        this.reset = reset;
    }

    public HandleCorruptionPacket(FriendlyByteBuf buf) {
        this.reset = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.reset);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) { // Require op for safety, though it only resets mod data
                if (reset) {
                    StorePriceManager.backupCorruptedData(); // Safety first
                    StorePriceManager.isDataCorrupted = false;
                    StorePriceManager.saveData(); // Overwrites with current (empty) data
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[ModMenu] §aData has been reset. Corrupted files backed up."), false);
                } else {
                    // Try to persist: we don't unset the flag to prevent overwriting
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[ModMenu] §eEntering world in read-only mode to prevent data loss."), false);
                }
                StorePriceManager.sync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
