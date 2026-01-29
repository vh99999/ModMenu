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
        
        int miningEnchantsSize = buf.readVarInt();
        if (miningEnchantsSize > 100) throw new RuntimeException("Too many mining enchants");
        this.settings.miningEnchants = new java.util.HashMap<>();
        for (int i = 0; i < miningEnchantsSize; i++) this.settings.miningEnchants.put(buf.readUtf(), buf.readInt());

        int miningBlacklistSize = buf.readVarInt();
        if (miningBlacklistSize > 1000) throw new RuntimeException("Too many mining blacklist entries");
        this.settings.miningBlacklist = new ArrayList<>(miningBlacklistSize);
        for (int i = 0; i < miningBlacklistSize; i++) this.settings.miningBlacklist.add(buf.readUtf());
        
        this.settings.focusedMiningActive = buf.readBoolean();
        this.settings.focusedMiningRange = buf.readInt();
        this.settings.repairActive = buf.readBoolean();
        
        this.settings.itemMagnetActive = buf.readBoolean();
        this.settings.itemMagnetRange = buf.readInt();
        this.settings.itemMagnetOpsPerTick = buf.readInt();
        this.settings.quantumVacuumRange = buf.readInt();
        
        this.settings.xpMagnetActive = buf.readBoolean();
        this.settings.xpMagnetRange = buf.readInt();
        this.settings.xpMagnetOpsPerTick = buf.readInt();
        
        this.settings.autoSellerActive = buf.readBoolean();
        
        int autoSellerSize = buf.readVarInt();
        if (autoSellerSize > 1000) throw new RuntimeException("Too many auto seller whitelist entries");
        this.settings.autoSellerWhitelist = new ArrayList<>(autoSellerSize);
        for (int i = 0; i < autoSellerSize; i++) this.settings.autoSellerWhitelist.add(buf.readUtf());

        this.settings.sellAllWhitelistActive = buf.readBoolean();
        
        int sellAllWhitelistSize = buf.readVarInt();
        if (sellAllWhitelistSize > 1000) throw new RuntimeException("Too many sell all whitelist entries");
        this.settings.sellAllWhitelist = new ArrayList<>(sellAllWhitelistSize);
        for (int i = 0; i < sellAllWhitelistSize; i++) this.settings.sellAllWhitelist.add(buf.readUtf());

        int sellAllBlacklistSize = buf.readVarInt();
        if (sellAllBlacklistSize > 1000) throw new RuntimeException("Too many sell all blacklist entries");
        this.settings.sellAllBlacklist = new ArrayList<>(sellAllBlacklistSize);
        for (int i = 0; i < sellAllBlacklistSize; i++) this.settings.sellAllBlacklist.add(buf.readUtf());
        
        int smelterWhitelistSize = buf.readVarInt();
        if (smelterWhitelistSize > 1000) throw new RuntimeException("Too many smelter whitelist entries");
        this.settings.smelterWhitelist = new ArrayList<>(smelterWhitelistSize);
        for (int i = 0; i < smelterWhitelistSize; i++) this.settings.smelterWhitelist.add(buf.readUtf());
        
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
        
        int spawnBoostSize = buf.readVarInt();
        if (spawnBoostSize > 100) throw new RuntimeException("Too many spawn boost targets");
        this.settings.spawnBoostTargets = new ArrayList<>(spawnBoostSize);
        for (int i = 0; i < spawnBoostSize; i++) this.settings.spawnBoostTargets.add(buf.readUtf());

        int disabledSpawnSize = buf.readVarInt();
        if (disabledSpawnSize > 100) throw new RuntimeException("Too many disabled spawn conditions");
        this.settings.disabledSpawnConditions = new ArrayList<>(disabledSpawnSize);
        for (int i = 0; i < disabledSpawnSize; i++) this.settings.disabledSpawnConditions.add(buf.readUtf());

        int selectedEnchantsSize = buf.readVarInt();
        if (selectedEnchantsSize > 100) throw new RuntimeException("Too many selected enchantments");
        this.settings.selectedEnchantments = new ArrayList<>(selectedEnchantsSize);
        for (int i = 0; i < selectedEnchantsSize; i++) this.settings.selectedEnchantments.add(buf.readUtf());
        
        this.settings.growCropsActive = buf.readBoolean();
        this.settings.growCropsRange = buf.readInt();
        
        this.settings.linkMagnetActive = buf.readBoolean();
        if (buf.readBoolean()) {
            this.settings.linkedStoragePos = buf.readBlockPos();
        }
        this.settings.linkedStorageDim = buf.readUtf();
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
        buf.writeInt(settings.quantumVacuumRange);
        
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
        
        buf.writeBoolean(settings.linkMagnetActive);
        buf.writeBoolean(settings.linkedStoragePos != null);
        if (settings.linkedStoragePos != null) {
            buf.writeBlockPos(settings.linkedStoragePos);
        }
        buf.writeUtf(settings.linkedStorageDim != null ? settings.linkedStorageDim : "minecraft:overworld");
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
