package com.example.modmenu.network;

import com.example.modmenu.store.logistics.LogisticsCapability;
import com.example.modmenu.store.logistics.LogisticsUtil;
import com.example.modmenu.store.logistics.NetworkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class NetworkManagementPacket {
    public enum Type {
        CREATE, DELETE, RENAME, TOGGLE_ACTIVE, SET_VIEWED
    }

    private final Type type;
    private final UUID networkId;
    private final String name;

    public NetworkManagementPacket(Type type, UUID networkId, String name) {
        this.type = type;
        this.networkId = networkId;
        this.name = name;
    }

    public static NetworkManagementPacket create(String name) {
        return new NetworkManagementPacket(Type.CREATE, null, name);
    }

    public static NetworkManagementPacket delete(UUID networkId) {
        return new NetworkManagementPacket(Type.DELETE, networkId, null);
    }

    public static NetworkManagementPacket rename(UUID networkId, String name) {
        return new NetworkManagementPacket(Type.RENAME, networkId, name);
    }

    public static NetworkManagementPacket toggleActive(UUID networkId) {
        return new NetworkManagementPacket(Type.TOGGLE_ACTIVE, networkId, null);
    }

    public static NetworkManagementPacket setViewed(UUID networkId) {
        return new NetworkManagementPacket(Type.SET_VIEWED, networkId, null);
    }

    public NetworkManagementPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(Type.class);
        this.networkId = buf.readBoolean() ? buf.readUUID() : null;
        this.name = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeBoolean(networkId != null);
        if (networkId != null) buf.writeUUID(networkId);
        buf.writeBoolean(name != null);
        if (name != null) buf.writeUtf(name);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LogisticsCapability.getNetworks(player).ifPresent(data -> {
                switch (type) {
                    case CREATE -> {
                        if (data.getNetworks().size() >= 20) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cYou have reached the maximum number of networks (20)!"), true);
                            return;
                        }
                        NetworkData net = new NetworkData();
                        net.networkName = LogisticsUtil.cleanName(name);
                        data.getNetworks().add(net);
                    }
                    case DELETE -> data.getNetworks().removeIf(n -> n.networkId.equals(networkId));
                    case RENAME -> {
                        for (NetworkData network : data.getNetworks()) {
                            if (network.networkId.equals(networkId)) {
                                network.networkName = LogisticsUtil.cleanName(name);
                                break;
                            }
                        }
                    }
                    case TOGGLE_ACTIVE -> {
                        for (NetworkData network : data.getNetworks()) {
                            if (network.networkId.equals(networkId)) {
                                network.active = !network.active;
                                break;
                            }
                        }
                    }
                    case SET_VIEWED -> data.viewedNetworkId = networkId;
                }
                LogisticsUtil.syncAndNotify(player, data);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
