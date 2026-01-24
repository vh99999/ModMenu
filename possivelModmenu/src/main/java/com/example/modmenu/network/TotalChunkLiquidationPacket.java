package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import com.example.modmenu.ServerForgeEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TotalChunkLiquidationPacket {
    public TotalChunkLiquidationPacket() {}
    public TotalChunkLiquidationPacket(FriendlyByteBuf buf) {}
    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                int rank = SkillManager.getActiveRank(data, "UTILITY_CHUNK_LIQUIDATION");
                if (rank > 0) {
                    ServerLevel level = player.serverLevel();
                    int cx = player.getBlockX() >> 4;
                    int cz = player.getBlockZ() >> 4;
                    int radius = rank - 1; 
                    
                    int minX = (cx - radius) << 4;
                    int maxX = ((cx + radius) << 4) + 15;
                    int minZ = (cz - radius) << 4;
                    int maxZ = ((cz + radius) << 4) + 15;
                    int minY = level.getMinBuildHeight();
                    int maxY = level.getMaxBuildHeight() - 1;

                    ServerForgeEvents.LiquidationRegion region = new ServerForgeEvents.LiquidationRegion(minX, maxX, minY, maxY, minZ, maxZ);
                    ServerForgeEvents.pendingRegions.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(region);
                    
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A76[Chunk Liquidation] \u00A7aQueued region for liquidation."), true);
                    StorePriceManager.sync(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
