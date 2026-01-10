package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.worldgen.MiningDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TeleportToMiningDimensionPacket {
    public TeleportToMiningDimensionPacket() {}

    public TeleportToMiningDimensionPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel currentLevel = player.serverLevel();
            if (currentLevel.dimension() == MiningDimension.MINING_DIMENSION_KEY) {
                // Return to previous position
                String returnDimId = StorePriceManager.getReturnDimension(player.getUUID());
                double[] returnPos = StorePriceManager.getReturnPosition(player.getUUID());

                if (returnDimId != null && returnPos != null) {
                    ResourceKey<Level> returnDimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.tryParse(returnDimId));
                    ServerLevel returnLevel = player.server.getLevel(returnDimKey);
                    if (returnLevel != null) {
                        player.teleportTo(returnLevel, returnPos[0], returnPos[1], returnPos[2], player.getYRot(), player.getXRot());
                    } else {
                        // Fallback to overworld if return dimension is gone
                        player.teleportTo(player.server.overworld(), 0, 100, 0, 0, 0);
                    }
                } else {
                    // Fallback to overworld
                    player.teleportTo(player.server.overworld(), 0, 100, 0, 0, 0);
                }
            } else {
                // Go to mining dimension
                if (StorePriceManager.isHouseUnlocked(player.getUUID(), "mining_dimension")) {
                    // Store return point
                    StorePriceManager.setReturnPoint(player.getUUID(), currentLevel.dimension().location().toString(), player.getX(), player.getY(), player.getZ());
                    
                    ServerLevel miningLevel = player.server.getLevel(MiningDimension.MINING_DIMENSION_KEY);
                    if (miningLevel != null) {
                        player.teleportTo(miningLevel, player.getX(), 65, player.getZ(), player.getYRot(), player.getXRot());
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
