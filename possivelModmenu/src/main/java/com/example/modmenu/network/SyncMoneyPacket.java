package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SyncMoneyPacket {
    private final BigDecimal money;
    private final boolean isEditor;
    private final Map<String, Integer> activeEffects;
    private final BigDecimal drain;
    private final StorePriceManager.AbilitySettings abilities;
    private final java.util.Set<String> unlockedHouses;
    private final Map<String, Double> attributeBonuses;

    public SyncMoneyPacket(BigDecimal money, boolean isEditor, Map<String, Integer> activeEffects, BigDecimal drain, StorePriceManager.AbilitySettings abilities, java.util.Set<String> unlockedHouses, Map<String, Double> attributeBonuses) {
        this.money = money;
        this.isEditor = isEditor;
        this.activeEffects = activeEffects;
        this.drain = drain;
        this.abilities = abilities;
        this.unlockedHouses = unlockedHouses;
        this.attributeBonuses = attributeBonuses;
    }

    public SyncMoneyPacket(FriendlyByteBuf buf) {
        this.money = new BigDecimal(buf.readUtf());
        this.isEditor = buf.readBoolean();
        this.activeEffects = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        this.drain = new BigDecimal(buf.readUtf());
        
        this.abilities = new StorePriceManager.AbilitySettings();
        this.abilities.miningActive = buf.readBoolean();
        this.abilities.miningRange = buf.readInt();
        this.abilities.autoSell = buf.readBoolean();
        this.abilities.autoSellerIsBlacklist = buf.readBoolean();
        this.abilities.useEnchantments = buf.readBoolean();
        this.abilities.miningEnchants = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        this.abilities.miningBlacklist = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        
        this.abilities.focusedMiningActive = buf.readBoolean();
        this.abilities.focusedMiningRange = buf.readInt();
        this.abilities.repairActive = buf.readBoolean();
        
        this.abilities.itemMagnetActive = buf.readBoolean();
        this.abilities.itemMagnetRange = buf.readInt();
        this.abilities.itemMagnetOpsPerTick = buf.readInt();
        
        this.abilities.xpMagnetActive = buf.readBoolean();
        this.abilities.xpMagnetRange = buf.readInt();
        this.abilities.xpMagnetOpsPerTick = buf.readInt();
        
        this.abilities.autoSellerActive = buf.readBoolean();
        this.abilities.autoSellerWhitelist = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        this.abilities.sellAllWhitelistActive = buf.readBoolean();
        this.abilities.sellAllWhitelist = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        this.abilities.sellAllBlacklist = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        
        this.abilities.smelterWhitelist = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        
        this.abilities.chestHighlightActive = buf.readBoolean();
        this.abilities.chestHighlightRange = buf.readInt();
        
        this.abilities.trapHighlightActive = buf.readBoolean();
        this.abilities.trapHighlightRange = buf.readInt();
        
        this.abilities.entityESPActive = buf.readBoolean();
        this.abilities.entityESPRange = buf.readInt();
        
        this.abilities.damageCancelActive = buf.readBoolean();
        this.abilities.damageCancelMultiplier = buf.readDouble();

        this.abilities.stepAssistActive = buf.readBoolean();
        this.abilities.stepAssistHeight = buf.readFloat();
        
        this.abilities.areaMiningActive = buf.readBoolean();
        this.abilities.areaMiningSize = buf.readInt();
        
        this.abilities.flightActive = buf.readBoolean();
        this.abilities.sureKillActive = buf.readBoolean();
        this.abilities.noAggroActive = buf.readBoolean();
        this.abilities.captureActive = buf.readBoolean();
        this.abilities.spawnBoostActive = buf.readBoolean();
        this.abilities.spawnBoostMultiplier = buf.readDouble();
        this.abilities.spawnBoostTargets = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        this.abilities.disabledSpawnConditions = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        this.abilities.selectedEnchantments = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        this.abilities.growCropsActive = buf.readBoolean();
        this.abilities.growCropsRange = buf.readInt();
        
        this.abilities.linkMagnetActive = buf.readBoolean();
        if (buf.readBoolean()) {
            this.abilities.linkedStoragePos = buf.readBlockPos();
        }
        this.abilities.linkedStorageDim = buf.readUtf();
        
        this.unlockedHouses = buf.readCollection(java.util.HashSet::new, FriendlyByteBuf::readUtf);
        this.attributeBonuses = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readDouble);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(money.toString());
        buf.writeBoolean(isEditor);
        buf.writeMap(activeEffects, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeUtf(drain.toString());
        
        buf.writeBoolean(abilities.miningActive);
        buf.writeInt(abilities.miningRange);
        buf.writeBoolean(abilities.autoSell);
        buf.writeBoolean(abilities.autoSellerIsBlacklist);
        buf.writeBoolean(abilities.useEnchantments);
        buf.writeMap(abilities.miningEnchants, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeCollection(abilities.miningBlacklist, FriendlyByteBuf::writeUtf);
        
        buf.writeBoolean(abilities.focusedMiningActive);
        buf.writeInt(abilities.focusedMiningRange);
        buf.writeBoolean(abilities.repairActive);
        
        buf.writeBoolean(abilities.itemMagnetActive);
        buf.writeInt(abilities.itemMagnetRange);
        buf.writeInt(abilities.itemMagnetOpsPerTick);
        
        buf.writeBoolean(abilities.xpMagnetActive);
        buf.writeInt(abilities.xpMagnetRange);
        buf.writeInt(abilities.xpMagnetOpsPerTick);
        
        buf.writeBoolean(abilities.autoSellerActive);
        buf.writeCollection(abilities.autoSellerWhitelist, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(abilities.sellAllWhitelistActive);
        buf.writeCollection(abilities.sellAllWhitelist, FriendlyByteBuf::writeUtf);
        buf.writeCollection(abilities.sellAllBlacklist, FriendlyByteBuf::writeUtf);
        
        buf.writeCollection(abilities.smelterWhitelist, FriendlyByteBuf::writeUtf);
        
        buf.writeBoolean(abilities.chestHighlightActive);
        buf.writeInt(abilities.chestHighlightRange);
        
        buf.writeBoolean(abilities.trapHighlightActive);
        buf.writeInt(abilities.trapHighlightRange);
        
        buf.writeBoolean(abilities.entityESPActive);
        buf.writeInt(abilities.entityESPRange);
        
        buf.writeBoolean(abilities.damageCancelActive);
        buf.writeDouble(abilities.damageCancelMultiplier);

        
        buf.writeBoolean(abilities.stepAssistActive);
        buf.writeFloat(abilities.stepAssistHeight);
        
        buf.writeBoolean(abilities.areaMiningActive);
        buf.writeInt(abilities.areaMiningSize);
        
        buf.writeBoolean(abilities.flightActive);
        buf.writeBoolean(abilities.sureKillActive);
        buf.writeBoolean(abilities.noAggroActive);
        buf.writeBoolean(abilities.captureActive);
        buf.writeBoolean(abilities.spawnBoostActive);
        buf.writeDouble(abilities.spawnBoostMultiplier);
        buf.writeCollection(abilities.spawnBoostTargets, FriendlyByteBuf::writeUtf);
        buf.writeCollection(abilities.disabledSpawnConditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(abilities.selectedEnchantments, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(abilities.growCropsActive);
        buf.writeInt(abilities.growCropsRange);
        
        buf.writeBoolean(abilities.linkMagnetActive);
        buf.writeBoolean(abilities.linkedStoragePos != null);
        if (abilities.linkedStoragePos != null) {
            buf.writeBlockPos(abilities.linkedStoragePos);
        }
        buf.writeUtf(abilities.linkedStorageDim != null ? abilities.linkedStorageDim : "minecraft:overworld");
        
        buf.writeCollection(unlockedHouses, FriendlyByteBuf::writeUtf);
        buf.writeMap(attributeBonuses, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeDouble);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            StorePriceManager.playerMoney = money;
            StorePriceManager.isEditor = isEditor;
            StorePriceManager.playerDrain = drain;
            
            if (activeEffects != null) {
                StorePriceManager.clientActiveEffects.clear();
                StorePriceManager.clientActiveEffects.putAll(activeEffects);
            }
            if (unlockedHouses != null) {
                StorePriceManager.clientUnlockedHouses.clear();
                StorePriceManager.clientUnlockedHouses.addAll(unlockedHouses);
            }
            if (attributeBonuses != null) {
                StorePriceManager.clientAttributeBonuses.clear();
                StorePriceManager.clientAttributeBonuses.putAll(attributeBonuses);
            }

            if (StorePriceManager.clientAbilities == null) {
                StorePriceManager.clientAbilities = abilities;
            } else {
                StorePriceManager.clientAbilities.copyFrom(abilities);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
