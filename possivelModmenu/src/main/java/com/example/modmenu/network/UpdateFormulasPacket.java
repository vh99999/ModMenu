package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.StoreSecurity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class UpdateFormulasPacket {
    private final StorePriceManager.FormulaConfig config;

    public UpdateFormulasPacket(StorePriceManager.FormulaConfig config) {
        this.config = config;
    }

    public UpdateFormulasPacket(FriendlyByteBuf buf) {
        this.config = new StorePriceManager.FormulaConfig();
        this.config.stepAssistCostPerAssist = new BigDecimal(buf.readUtf());
        this.config.areaMiningCostBase = new BigDecimal(buf.readUtf());
        this.config.flightCostPerSecond = new BigDecimal(buf.readUtf());
        this.config.sureKillBaseCost = new BigDecimal(buf.readUtf());
        this.config.sureKillHealthMultiplier = buf.readDouble();
        this.config.noAggroCostPerCancel = new BigDecimal(buf.readUtf());
        this.config.noAggroMaintenance = new BigDecimal(buf.readUtf());
        this.config.damageCancelMultiplier = buf.readDouble();
        this.config.damageCancelMaintenance = new BigDecimal(buf.readUtf());
        this.config.repairCostPerPoint = buf.readInt();
        this.config.repairMaintenance = new BigDecimal(buf.readUtf());
        this.config.chestHighlightMaintenancePerRange = new BigDecimal(buf.readUtf());
        this.config.trapHighlightMaintenancePerRange = new BigDecimal(buf.readUtf());
        this.config.entityESPMaintenancePerRange = new BigDecimal(buf.readUtf());
        this.config.itemMagnetMaintenancePerRangeOps = new BigDecimal(buf.readUtf());
        this.config.xpMagnetMaintenancePerRangeOps = new BigDecimal(buf.readUtf());
        this.config.autoSellerMaintenance = new BigDecimal(buf.readUtf());
        this.config.spawnBoostMaintenance = new BigDecimal(buf.readUtf());
        this.config.spawnBoostPerSpawnBase = new BigDecimal(buf.readUtf());
        this.config.growCropsMaintenance = new BigDecimal(buf.readUtf());
        this.config.growCropsPerOperation = new BigDecimal(buf.readUtf());
        this.config.linkMagnetMaintenance = new BigDecimal(buf.readUtf());
        this.config.linkMagnetDistanceMultiplier = new BigDecimal(buf.readUtf());
        this.config.linkMagnetDimensionTax = new BigDecimal(buf.readUtf());
        this.config.spMultiplier = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(config.stepAssistCostPerAssist.toString());
        buf.writeUtf(config.areaMiningCostBase.toString());
        buf.writeUtf(config.flightCostPerSecond.toString());
        buf.writeUtf(config.sureKillBaseCost.toString());
        buf.writeDouble(config.sureKillHealthMultiplier);
        buf.writeUtf(config.noAggroCostPerCancel.toString());
        buf.writeUtf(config.noAggroMaintenance.toString());
        buf.writeDouble(config.damageCancelMultiplier);
        buf.writeUtf(config.damageCancelMaintenance.toString());
        buf.writeInt(config.repairCostPerPoint);
        buf.writeUtf(config.repairMaintenance.toString());
        buf.writeUtf(config.chestHighlightMaintenancePerRange.toString());
        buf.writeUtf(config.trapHighlightMaintenancePerRange.toString());
        buf.writeUtf(config.entityESPMaintenancePerRange.toString());
        buf.writeUtf(config.itemMagnetMaintenancePerRangeOps.toString());
        buf.writeUtf(config.xpMagnetMaintenancePerRangeOps.toString());
        buf.writeUtf(config.autoSellerMaintenance.toString());
        buf.writeUtf(config.spawnBoostMaintenance.toString());
        buf.writeUtf(config.spawnBoostPerSpawnBase.toString());
        buf.writeUtf(config.growCropsMaintenance.toString());
        buf.writeUtf(config.growCropsPerOperation.toString());
        buf.writeUtf(config.linkMagnetMaintenance.toString());
        buf.writeUtf(config.linkMagnetDistanceMultiplier.toString());
        buf.writeUtf(config.linkMagnetDimensionTax.toString());
        buf.writeDouble(config.spMultiplier);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && StoreSecurity.canModifyPrices(player)) {
                StorePriceManager.formulas = this.config;
                StorePriceManager.saveFormulas();
                
                // Sync to all players so they see updated costs
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    StorePriceManager.sync(p);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
