package com.example.modmenu.network;

import com.example.modmenu.store.logistics.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class NetworkControlPacket {
    public enum Type {
        TOGGLE_VISIBILITY, TOGGLE_SIMULATION, SET_LINK_MODE, SET_OVERFLOW
    }

    private final Type type;
    private final UUID networkId;
    private UUID targetId;
    private boolean isGroup;

    public NetworkControlPacket(Type type, UUID networkId) {
        this.type = type;
        this.networkId = networkId;
    }

    public static NetworkControlPacket toggleVisibility(UUID networkId) {
        return new NetworkControlPacket(Type.TOGGLE_VISIBILITY, networkId);
    }

    public static NetworkControlPacket toggleSimulation(UUID networkId) {
        return new NetworkControlPacket(Type.TOGGLE_SIMULATION, networkId);
    }

    public static NetworkControlPacket setLinkMode(UUID networkId) {
        return new NetworkControlPacket(Type.SET_LINK_MODE, networkId);
    }

    public static NetworkControlPacket setOverflow(UUID networkId, UUID targetId, boolean isGroup) {
        NetworkControlPacket p = new NetworkControlPacket(Type.SET_OVERFLOW, networkId);
        p.targetId = targetId;
        p.isGroup = isGroup;
        return p;
    }

    public NetworkControlPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(Type.class);
        this.networkId = buf.readBoolean() ? buf.readUUID() : null;
        if (buf.readBoolean()) this.targetId = buf.readUUID();
        this.isGroup = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeBoolean(networkId != null);
        if (networkId != null) buf.writeUUID(networkId);
        buf.writeBoolean(targetId != null);
        if (targetId != null) buf.writeUUID(targetId);
        buf.writeBoolean(isGroup);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LogisticsCapability.getNetworks(player).ifPresent(data -> {
                NetworkData network = data.getNetworks().stream()
                        .filter(n -> n.networkId.equals(networkId))
                        .findFirst().orElse(null);

                if (network == null && type != Type.SET_LINK_MODE) return;

                switch (type) {
                    case TOGGLE_VISIBILITY -> network.showConnections = !network.showConnections;
                    case TOGGLE_SIMULATION -> network.simulationActive = !network.simulationActive;
                    case SET_LINK_MODE -> data.linkingNetworkId = networkId;
                    case SET_OVERFLOW -> {
                        network.overflowTargetId = targetId;
                        network.overflowIsGroup = isGroup;
                    }
                }
                LogisticsUtil.syncAndNotify(player, data);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
