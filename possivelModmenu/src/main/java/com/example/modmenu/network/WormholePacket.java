package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WormholePacket {
    private final double x, y, z;
    private final String dimension;

    public WormholePacket(double x, double y, double z, String dimension) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
    }

    public WormholePacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.dimension = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeUtf(dimension);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (SkillManager.getActiveRank(data, "UTILITY_WORMHOLE_PROTOCOL") > 0) {
                    ResourceLocation dimLoc = ResourceLocation.tryParse(dimension);
                    if (dimLoc != null) {
                        ServerLevel targetDim = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
                        if (targetDim != null) {
                            // Safe check for coordinates
                            if (y < targetDim.getMinBuildHeight() - 64 || y > targetDim.getMaxBuildHeight() + 64 || 
                                Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cInvalid coordinates!"), true);
                                return;
                            }

                            // Safe check: Ensure destination is not inside a solid block
                            BlockPos targetPos = new BlockPos((int)x, (int)y, (int)z);
                            if (targetDim.getBlockState(targetPos).isSolid() && targetDim.getBlockState(targetPos.above()).isSolid()) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cTarget location is obstructed!"), true);
                                return;
                            }

                            // Prevent chunk loading lag
                            targetDim.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT, new net.minecraft.world.level.ChunkPos(targetPos), 1, player.getId());

                            player.teleportTo(targetDim, x, y, z, player.getYRot(), player.getXRot());
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
