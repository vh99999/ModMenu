package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class UpdateAbilityPacket {
    private final StorePriceManager.AbilitySettings settings;

    public UpdateAbilityPacket(StorePriceManager.AbilitySettings settings) {
        this.settings = settings;
    }

    public UpdateAbilityPacket(FriendlyByteBuf buf) {
        this.settings = new StorePriceManager.AbilitySettings();
        this.settings.miningActive = buf.readBoolean();
        this.settings.miningRange = buf.readInt();
        this.settings.autoSell = buf.readBoolean();
        this.settings.autoSellerIsBlacklist = buf.readBoolean();
        this.settings.useEnchantments = buf.readBoolean();
        this.settings.miningEnchants = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        this.settings.miningBlacklist = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        
        this.settings.focusedMiningActive = buf.readBoolean();
        this.settings.focusedMiningRange = buf.readInt();
        this.settings.repairActive = buf.readBoolean();
        
        this.settings.itemMagnetActive = buf.readBoolean();
        this.settings.itemMagnetRange = buf.readInt();
        this.settings.itemMagnetOpsPerTick = buf.readInt();
        
        this.settings.xpMagnetActive = buf.readBoolean();
        this.settings.xpMagnetRange = buf.readInt();
        this.settings.xpMagnetOpsPerTick = buf.readInt();
        
        this.settings.autoSellerActive = buf.readBoolean();
        this.settings.autoSellerWhitelist = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.settings.sellAllWhitelistActive = buf.readBoolean();
        this.settings.sellAllWhitelist = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.settings.sellAllBlacklist = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        
        this.settings.smelterWhitelist = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        
        this.settings.chestHighlightActive = buf.readBoolean();
        this.settings.chestHighlightRange = buf.readInt();
        
        this.settings.trapHighlightActive = buf.readBoolean();
        this.settings.trapHighlightRange = buf.readInt();
        
        this.settings.entityESPActive = buf.readBoolean();
        this.settings.entityESPRange = buf.readInt();
        
        this.settings.damageCancelActive = buf.readBoolean();
        this.settings.damageCancelMultiplier = buf.readDouble();

        this.settings.stepAssistActive = buf.readBoolean();
        this.settings.stepAssistHeight = buf.readFloat();
        
        this.settings.areaMiningActive = buf.readBoolean();
        this.settings.areaMiningSize = buf.readInt();
        
        this.settings.flightActive = buf.readBoolean();
        this.settings.sureKillActive = buf.readBoolean();
        this.settings.noAggroActive = buf.readBoolean();
        this.settings.captureActive = buf.readBoolean();
        this.settings.spawnBoostActive = buf.readBoolean();
        this.settings.spawnBoostMultiplier = buf.readDouble();
        this.settings.spawnBoostTargets = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.settings.disabledSpawnConditions = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.settings.selectedEnchantments = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.settings.growCropsActive = buf.readBoolean();
        this.settings.growCropsRange = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(settings.miningActive);
        buf.writeInt(settings.miningRange);
        buf.writeBoolean(settings.autoSell);
        buf.writeBoolean(settings.autoSellerIsBlacklist);
        buf.writeBoolean(settings.useEnchantments);
        buf.writeMap(settings.miningEnchants, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeCollection(settings.miningBlacklist, FriendlyByteBuf::writeUtf);
        
        buf.writeBoolean(settings.focusedMiningActive);
        buf.writeInt(settings.focusedMiningRange);
        buf.writeBoolean(settings.repairActive);
        
        buf.writeBoolean(settings.itemMagnetActive);
        buf.writeInt(settings.itemMagnetRange);
        buf.writeInt(settings.itemMagnetOpsPerTick);
        
        buf.writeBoolean(settings.xpMagnetActive);
        buf.writeInt(settings.xpMagnetRange);
        buf.writeInt(settings.xpMagnetOpsPerTick);
        
        buf.writeBoolean(settings.autoSellerActive);
        buf.writeCollection(settings.autoSellerWhitelist, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(settings.sellAllWhitelistActive);
        buf.writeCollection(settings.sellAllWhitelist, FriendlyByteBuf::writeUtf);
        buf.writeCollection(settings.sellAllBlacklist, FriendlyByteBuf::writeUtf);
        
        buf.writeCollection(settings.smelterWhitelist, FriendlyByteBuf::writeUtf);
        
        buf.writeBoolean(settings.chestHighlightActive);
        buf.writeInt(settings.chestHighlightRange);
        
        buf.writeBoolean(settings.trapHighlightActive);
        buf.writeInt(settings.trapHighlightRange);
        
        buf.writeBoolean(settings.entityESPActive);
        buf.writeInt(settings.entityESPRange);
        
        buf.writeBoolean(settings.damageCancelActive);
        buf.writeDouble(settings.damageCancelMultiplier);
        
        buf.writeBoolean(settings.stepAssistActive);
        buf.writeFloat(settings.stepAssistHeight);
        
        buf.writeBoolean(settings.areaMiningActive);
        buf.writeInt(settings.areaMiningSize);
        
        buf.writeBoolean(settings.flightActive);
        buf.writeBoolean(settings.sureKillActive);
        buf.writeBoolean(settings.noAggroActive);
        buf.writeBoolean(settings.captureActive);
        buf.writeBoolean(settings.spawnBoostActive);
        buf.writeDouble(settings.spawnBoostMultiplier);
        buf.writeCollection(settings.spawnBoostTargets, FriendlyByteBuf::writeUtf);
        buf.writeCollection(settings.disabledSpawnConditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(settings.selectedEnchantments, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(settings.growCropsActive);
        buf.writeInt(settings.growCropsRange);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.AbilitySettings current = StorePriceManager.getAbilities(player.getUUID());
                
                // Mutual exclusion: if one is newly enabled, disable others
                if (this.settings.miningActive && !current.miningActive) {
                    this.settings.focusedMiningActive = false;
                    this.settings.areaMiningActive = false;
                } else if (this.settings.focusedMiningActive && !current.focusedMiningActive) {
                    this.settings.miningActive = false;
                    this.settings.areaMiningActive = false;
                } else if (this.settings.areaMiningActive && !current.areaMiningActive) {
                    this.settings.miningActive = false;
                    this.settings.focusedMiningActive = false;
                }
                
                StorePriceManager.setAbilities(player.getUUID(), this.settings);
                StorePriceManager.sync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
