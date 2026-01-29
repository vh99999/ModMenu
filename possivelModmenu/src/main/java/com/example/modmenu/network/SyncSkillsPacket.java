package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class SyncSkillsPacket {
    private final StorePriceManager.SkillData data;

    public SyncSkillsPacket(StorePriceManager.SkillData data) {
        this.data = data.snapshot();
    }

    public SyncSkillsPacket(FriendlyByteBuf buf) {
        this.data = new StorePriceManager.SkillData();
        this.data.totalSP = new java.math.BigDecimal(buf.readUtf());
        this.data.spentSP = new java.math.BigDecimal(buf.readUtf());
        
        int ranksSize = buf.readInt();
        for (int i = 0; i < ranksSize; i++) {
            this.data.skillRanks.put(buf.readUtf(), buf.readInt());
        }

        int unlockedSize = buf.readInt();
        for (int i = 0; i < unlockedSize; i++) {
            this.data.unlockedRanks.put(buf.readUtf(), buf.readInt());
        }
        
        int togglesSize = buf.readInt();
        for (int i = 0; i < togglesSize; i++) {
            this.data.activeToggles.add(buf.readUtf());
        }
        
        int satietySize = buf.readInt();
        for (int i = 0; i < satietySize; i++) {
            this.data.mobSatiety.put(buf.readUtf(), buf.readFloat());
        }
        
        int branchSize = buf.readInt();
        for (int i = 0; i < branchSize; i++) {
            this.data.branchOrder.add(buf.readUtf());
        }

        int attrSize = buf.readInt();
        for (int i = 0; i < attrSize; i++) {
            this.data.permanentAttributes.put(buf.readUtf(), new java.math.BigDecimal(buf.readUtf()));
        }

        int captureTimesSize = buf.readInt();
        for (int i = 0; i < captureTimesSize; i++) {
            this.data.lastCaptureTimes.put(buf.readUtf(), buf.readLong());
        }

        int blacklistSize = buf.readInt();
        for (int i = 0; i < blacklistSize; i++) {
            this.data.blacklistedSpecies.add(buf.readUtf());
        }
        this.data.overclockKillsRemaining = buf.readInt();
        this.data.unlockedChambers = buf.readInt();
        this.data.totalKills = new java.math.BigDecimal(buf.readUtf());
        this.data.damageReflected = new java.math.BigDecimal(buf.readUtf());
        this.data.damageHealed = new java.math.BigDecimal(buf.readUtf());
        
        int chamberSize = buf.readInt();
        for (int i = 0; i < chamberSize; i++) {
            StorePriceManager.ChamberData chamber = new StorePriceManager.ChamberData();
            chamber.mobId = buf.readUtf();
            chamber.customName = buf.readBoolean() ? buf.readUtf() : null;
            chamber.isExact = buf.readBoolean();
            if (chamber.isExact) chamber.nbt = buf.readNbt();
            
            chamber.storedXP = new java.math.BigDecimal(buf.readUtf());
            chamber.lastHarvestTime = buf.readLong();
            if (buf.readBoolean()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
                int count = buf.readInt();
                net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item == null ? net.minecraft.world.item.Items.AIR : item, count);
                stack.setTag(tag);
                chamber.killerWeapon = stack;
            } else {
                chamber.killerWeapon = net.minecraft.world.item.ItemStack.EMPTY;
            }
            chamber.rerollCount = buf.readInt();
            int filterSize = buf.readInt();
            for (int j = 0; j < filterSize; j++) {
                chamber.voidFilter.add(buf.readUtf());
            }
            chamber.updateVersion = buf.readInt();
            chamber.paused = buf.readBoolean();

            chamber.barteringMode = buf.readBoolean();
            chamber.condensationMode = buf.readInt();
            chamber.speedSlider = buf.readInt();
            chamber.threadSlider = buf.readInt();
            int advFilterSize = buf.readInt();
            for (int j = 0; j < advFilterSize; j++) {
                StorePriceManager.FilterRule rule = new StorePriceManager.FilterRule();
                rule.matchType = buf.readUtf();
                rule.matchValue = buf.readUtf();
                if (buf.readBoolean()) rule.nbtSample = buf.readNbt();
                rule.action = buf.readInt();
                chamber.advancedFilters.add(rule);
            }
            chamber.isExcavation = buf.readBoolean();
            if (buf.readBoolean()) chamber.lootTableId = buf.readUtf();
            if (buf.readBoolean()) chamber.linkedContainerPos = buf.readBlockPos();
            if (buf.readBoolean()) chamber.linkedContainerDimension = buf.readUtf();
            if (buf.readBoolean()) chamber.inputLinkPos = buf.readBlockPos();
            if (buf.readBoolean()) chamber.inputLinkDimension = buf.readUtf();

            this.data.chambers.add(chamber);
        }

        this.data.genesisConfig = new StorePriceManager.GenesisConfig();
        this.data.genesisConfig.genType = buf.readUtf();
        int biomesSize = buf.readInt();
        this.data.genesisConfig.biomes = new ArrayList<>();
        for (int i = 0; i < biomesSize; i++) {
            this.data.genesisConfig.biomes.add(buf.readUtf());
        }
        int caveBiomesSize = buf.readInt();
        this.data.genesisConfig.caveBiomes = new ArrayList<>();
        for (int i = 0; i < caveBiomesSize; i++) {
            this.data.genesisConfig.caveBiomes.add(buf.readUtf());
        }
        this.data.genesisConfig.resourceDensity = buf.readDouble();
        this.data.genesisConfig.structureDensity = buf.readDouble();
        this.data.genesisConfig.seaLevelFluid = buf.readUtf();
        this.data.genesisConfig.spawnLavaLakes = buf.readBoolean();
        this.data.genesisConfig.dimensionScale = buf.readDouble();
        this.data.genesisConfig.spawnHostile = buf.readBoolean();
        this.data.genesisConfig.spawnPassive = buf.readBoolean();
        this.data.genesisConfig.spawnNeutral = buf.readBoolean();
        this.data.genesisConfig.dayNightRatio = buf.readDouble();
        this.data.genesisConfig.temporalVelocity = buf.readDouble();
        this.data.genesisConfig.tickFreeze = buf.readBoolean();
        this.data.genesisConfig.frozenTime = buf.readInt();
        this.data.genesisConfig.persistentWeather = buf.readUtf();
        this.data.genesisConfig.skyColor = buf.readInt();
        this.data.genesisConfig.fogColor = buf.readInt();
        this.data.genesisConfig.waterColor = buf.readInt();
        this.data.genesisConfig.grassColor = buf.readInt();
        this.data.genesisConfig.foliageColor = buf.readInt();
        this.data.genesisConfig.ambientLight = buf.readDouble();
        this.data.genesisConfig.fogDensity = buf.readDouble();
        this.data.genesisConfig.celestialSync = buf.readBoolean();
        this.data.genesisConfig.gravity = buf.readDouble();
        this.data.genesisConfig.thermalRegulation = buf.readUtf();
        this.data.genesisConfig.fluidViscosityHigh = buf.readBoolean();
        this.data.genesisConfig.explosionYield = buf.readDouble();
        this.data.genesisConfig.fallDamageMultiplier = buf.readDouble();
        this.data.genesisConfig.difficulty = buf.readUtf();
        this.data.genesisConfig.respawnLogicEnabled = buf.readBoolean();
        this.data.genesisConfig.lootXpMultiplier = buf.readDouble();
        this.data.genesisConfig.realityPersistence = buf.readBoolean();
        this.data.genesisConfig.bedrockControl = buf.readUtf();
        this.data.genesisConfig.voidMirror = buf.readBoolean();
        this.data.genesisConfig.mobSpawnRate = buf.readDouble();
        this.data.genesisConfig.mobMutationRate = buf.readDouble();
        this.data.genesisConfig.hazardRadiation = buf.readBoolean();
        this.data.genesisConfig.hazardOxygen = buf.readBoolean();
        this.data.genesisConfig.joinMessage = buf.readUtf();
        this.data.genesisConfig.locked = buf.readBoolean();
        this.data.genesisConfig.fullResetRequested = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(data.totalSP.toString());
        buf.writeUtf(data.spentSP.toString());
        
        buf.writeInt(data.skillRanks.size());
        data.skillRanks.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeInt(v);
        });

        buf.writeInt(data.unlockedRanks.size());
        data.unlockedRanks.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeInt(v);
        });
        
        buf.writeInt(data.activeToggles.size());
        data.activeToggles.forEach(buf::writeUtf);
        
        buf.writeInt(data.mobSatiety.size());
        data.mobSatiety.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeFloat(v);
        });
        
        buf.writeInt(data.branchOrder.size());
        data.branchOrder.forEach(buf::writeUtf);

        buf.writeInt(data.permanentAttributes.size());
        data.permanentAttributes.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeUtf(v.toString());
        });

        buf.writeInt(data.lastCaptureTimes.size());
        data.lastCaptureTimes.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeLong(v);
        });

        buf.writeInt(data.blacklistedSpecies.size());
        data.blacklistedSpecies.forEach(buf::writeUtf);
        buf.writeInt(data.overclockKillsRemaining);
        buf.writeInt(data.unlockedChambers);
        buf.writeUtf(data.totalKills.toString());
        buf.writeUtf(data.damageReflected.toString());
        buf.writeUtf(data.damageHealed.toString());
        
        buf.writeInt(data.chambers.size());
        for (StorePriceManager.ChamberData chamber : data.chambers) {
            buf.writeUtf(chamber.mobId);
            buf.writeBoolean(chamber.customName != null);
            if (chamber.customName != null) buf.writeUtf(chamber.customName);
            buf.writeBoolean(chamber.isExact);
            if (chamber.isExact) buf.writeNbt(chamber.nbt);
            
            // Stored loot excluded here
            
            buf.writeUtf(chamber.storedXP.toString());
            buf.writeLong(chamber.lastHarvestTime);
            if (chamber.killerWeapon == null || chamber.killerWeapon.isEmpty()) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeResourceLocation(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(chamber.killerWeapon.getItem()));
                buf.writeInt(chamber.killerWeapon.getCount());
                buf.writeNbt(chamber.killerWeapon.getTag());
            }
            buf.writeInt(chamber.rerollCount);
            buf.writeInt(chamber.voidFilter.size());
            for (String filterId : chamber.voidFilter) {
                buf.writeUtf(filterId);
            }
            buf.writeInt(chamber.updateVersion);
            buf.writeBoolean(chamber.paused);

            buf.writeBoolean(chamber.barteringMode);
            buf.writeInt(chamber.condensationMode);
            buf.writeInt(chamber.speedSlider);
            buf.writeInt(chamber.threadSlider);
            buf.writeInt(chamber.advancedFilters.size());
            for (StorePriceManager.FilterRule rule : chamber.advancedFilters) {
                buf.writeUtf(rule.matchType);
                buf.writeUtf(rule.matchValue);
                buf.writeBoolean(rule.nbtSample != null);
                if (rule.nbtSample != null) buf.writeNbt(rule.nbtSample);
                buf.writeInt(rule.action);
            }
            buf.writeBoolean(chamber.isExcavation);
            buf.writeBoolean(chamber.lootTableId != null);
            if (chamber.lootTableId != null) buf.writeUtf(chamber.lootTableId);
            buf.writeBoolean(chamber.linkedContainerPos != null);
            if (chamber.linkedContainerPos != null) buf.writeBlockPos(chamber.linkedContainerPos);
            buf.writeBoolean(chamber.linkedContainerDimension != null);
            if (chamber.linkedContainerDimension != null) buf.writeUtf(chamber.linkedContainerDimension);
            buf.writeBoolean(chamber.inputLinkPos != null);
            if (chamber.inputLinkPos != null) buf.writeBlockPos(chamber.inputLinkPos);
            buf.writeBoolean(chamber.inputLinkDimension != null);
            if (chamber.inputLinkDimension != null) buf.writeUtf(chamber.inputLinkDimension);
        }

        buf.writeUtf(data.genesisConfig.genType);
        buf.writeInt(data.genesisConfig.biomes.size());
        for (String biome : data.genesisConfig.biomes) {
            buf.writeUtf(biome);
        }
        buf.writeInt(data.genesisConfig.caveBiomes.size());
        for (String biome : data.genesisConfig.caveBiomes) {
            buf.writeUtf(biome);
        }
        buf.writeDouble(data.genesisConfig.resourceDensity);
        buf.writeDouble(data.genesisConfig.structureDensity);
        buf.writeUtf(data.genesisConfig.seaLevelFluid);
        buf.writeBoolean(data.genesisConfig.spawnLavaLakes);
        buf.writeDouble(data.genesisConfig.dimensionScale);
        buf.writeBoolean(data.genesisConfig.spawnHostile);
        buf.writeBoolean(data.genesisConfig.spawnPassive);
        buf.writeBoolean(data.genesisConfig.spawnNeutral);
        buf.writeDouble(data.genesisConfig.dayNightRatio);
        buf.writeDouble(data.genesisConfig.temporalVelocity);
        buf.writeBoolean(data.genesisConfig.tickFreeze);
        buf.writeInt(data.genesisConfig.frozenTime);
        buf.writeUtf(data.genesisConfig.persistentWeather);
        buf.writeInt(data.genesisConfig.skyColor);
        buf.writeInt(data.genesisConfig.fogColor);
        buf.writeInt(data.genesisConfig.waterColor);
        buf.writeInt(data.genesisConfig.grassColor);
        buf.writeInt(data.genesisConfig.foliageColor);
        buf.writeDouble(data.genesisConfig.ambientLight);
        buf.writeDouble(data.genesisConfig.fogDensity);
        buf.writeBoolean(data.genesisConfig.celestialSync);
        buf.writeDouble(data.genesisConfig.gravity);
        buf.writeUtf(data.genesisConfig.thermalRegulation);
        buf.writeBoolean(data.genesisConfig.fluidViscosityHigh);
        buf.writeDouble(data.genesisConfig.explosionYield);
        buf.writeDouble(data.genesisConfig.fallDamageMultiplier);
        buf.writeUtf(data.genesisConfig.difficulty);
        buf.writeBoolean(data.genesisConfig.respawnLogicEnabled);
        buf.writeDouble(data.genesisConfig.lootXpMultiplier);
        buf.writeBoolean(data.genesisConfig.realityPersistence);
        buf.writeUtf(data.genesisConfig.bedrockControl);
        buf.writeBoolean(data.genesisConfig.voidMirror);
        buf.writeDouble(data.genesisConfig.mobSpawnRate);
        buf.writeDouble(data.genesisConfig.mobMutationRate);
        buf.writeBoolean(data.genesisConfig.hazardRadiation);
        buf.writeBoolean(data.genesisConfig.hazardOxygen);
        buf.writeUtf(data.genesisConfig.joinMessage);
        buf.writeBoolean(data.genesisConfig.locked);
        buf.writeBoolean(data.genesisConfig.fullResetRequested);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncSkills(this.data);
        });
        ctx.get().setPacketHandled(true);
    }
}
