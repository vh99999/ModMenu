package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AdminActionPacket {
    private final Action action;

    public enum Action {
        ADD_ALL_EFFECTS,
        ADD_ALL_ENCHANTS,
        RESET_DEFAULT_PRICES
    }

    public AdminActionPacket(Action action) {
        this.action = action;
    }

    public AdminActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && StorePriceManager.isEditor(player.getUUID())) {
                switch (action) {
                    case ADD_ALL_EFFECTS -> StorePriceManager.addAllEffects();
                    case ADD_ALL_ENCHANTS -> StorePriceManager.addAllEnchantments();
                    case RESET_DEFAULT_PRICES -> StorePriceManager.resetToDefaults(player.level());
                }
                StorePriceManager.sync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
