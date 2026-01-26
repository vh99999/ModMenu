package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class UpdateGenesisConfigPacket {
    private final StorePriceManager.GenesisConfig config;

    public UpdateGenesisConfigPacket(StorePriceManager.GenesisConfig config) {
        this.config = config.snapshot();
    }

    public UpdateGenesisConfigPacket(FriendlyByteBuf buf) {
        this.config = new StorePriceManager.GenesisConfig();
        this.config.genType = buf.readUtf();
        int biomesSize = buf.readInt();
        this.config.biomes = new ArrayList<>();
        for (int i = 0; i < biomesSize; i++) {
            this.config.biomes.add(buf.readUtf());
        }
        int caveBiomesSize = buf.readInt();
        this.config.caveBiomes = new ArrayList<>();
        for (int i = 0; i < caveBiomesSize; i++) {
            this.config.caveBiomes.add(buf.readUtf());
        }
        this.config.resourceDensity = buf.readDouble();
        this.config.structureDensity = buf.readDouble();
        this.config.seaLevelFluid = buf.readUtf();
        this.config.dimensionScale = buf.readDouble();
        this.config.spawnHostile = buf.readBoolean();
        this.config.spawnPassive = buf.readBoolean();
        this.config.spawnNeutral = buf.readBoolean();
        this.config.dayNightRatio = buf.readDouble();
        this.config.temporalVelocity = buf.readDouble();
        this.config.tickFreeze = buf.readBoolean();
        this.config.frozenTime = buf.readInt();
        this.config.persistentWeather = buf.readUtf();
        this.config.skyColor = buf.readInt();
        this.config.fogColor = buf.readInt();
        this.config.waterColor = buf.readInt();
        this.config.grassColor = buf.readInt();
        this.config.foliageColor = buf.readInt();
        this.config.ambientLight = buf.readDouble();
        this.config.fogDensity = buf.readDouble();
        this.config.celestialSync = buf.readBoolean();
        this.config.gravity = buf.readDouble();
        this.config.thermalRegulation = buf.readUtf();
        this.config.fluidViscosityHigh = buf.readBoolean();
        this.config.explosionYield = buf.readDouble();
        this.config.fallDamageMultiplier = buf.readDouble();
        this.config.difficulty = buf.readUtf();
        this.config.respawnLogicEnabled = buf.readBoolean();
        this.config.lootXpMultiplier = buf.readDouble();
        this.config.realityPersistence = buf.readBoolean();
        this.config.bedrockControl = buf.readUtf();
        this.config.voidMirror = buf.readBoolean();
        this.config.mobSpawnRate = buf.readDouble();
        this.config.mobMutationRate = buf.readDouble();
        this.config.hazardRadiation = buf.readBoolean();
        this.config.hazardOxygen = buf.readBoolean();
        this.config.joinMessage = buf.readUtf();
        this.config.locked = buf.readBoolean();
        this.config.fullResetRequested = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(config.genType);
        buf.writeInt(config.biomes.size());
        for (String biome : config.biomes) {
            buf.writeUtf(biome);
        }
        buf.writeInt(config.caveBiomes.size());
        for (String biome : config.caveBiomes) {
            buf.writeUtf(biome);
        }
        buf.writeDouble(config.resourceDensity);
        buf.writeDouble(config.structureDensity);
        buf.writeUtf(config.seaLevelFluid);
        buf.writeDouble(config.dimensionScale);
        buf.writeBoolean(config.spawnHostile);
        buf.writeBoolean(config.spawnPassive);
        buf.writeBoolean(config.spawnNeutral);
        buf.writeDouble(config.dayNightRatio);
        buf.writeDouble(config.temporalVelocity);
        buf.writeBoolean(config.tickFreeze);
        buf.writeInt(config.frozenTime);
        buf.writeUtf(config.persistentWeather);
        buf.writeInt(config.skyColor);
        buf.writeInt(config.fogColor);
        buf.writeInt(config.waterColor);
        buf.writeInt(config.grassColor);
        buf.writeInt(config.foliageColor);
        buf.writeDouble(config.ambientLight);
        buf.writeDouble(config.fogDensity);
        buf.writeBoolean(config.celestialSync);
        buf.writeDouble(config.gravity);
        buf.writeUtf(config.thermalRegulation);
        buf.writeBoolean(config.fluidViscosityHigh);
        buf.writeDouble(config.explosionYield);
        buf.writeDouble(config.fallDamageMultiplier);
        buf.writeUtf(config.difficulty);
        buf.writeBoolean(config.respawnLogicEnabled);
        buf.writeDouble(config.lootXpMultiplier);
        buf.writeBoolean(config.realityPersistence);
        buf.writeUtf(config.bedrockControl);
        buf.writeBoolean(config.voidMirror);
        buf.writeDouble(config.mobSpawnRate);
        buf.writeDouble(config.mobMutationRate);
        buf.writeBoolean(config.hazardRadiation);
        buf.writeBoolean(config.hazardOxygen);
        buf.writeUtf(config.joinMessage);
        buf.writeBoolean(config.locked);
        buf.writeBoolean(config.fullResetRequested);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                data.genesisConfig.copyFrom(this.config);
                
                // Update global config as well
                StorePriceManager.getGlobalGenesisConfig().copyFrom(this.config);
                
                StorePriceManager.setActiveGod(player.getUUID());
                StorePriceManager.markDirty(player.getUUID());
                // Apply "soft" changes (Time, Weather, etc.)
                applySoftChanges(player, data.genesisConfig);
                
                // Sync everyone to reflect the new global dimension state
                if (player.server != null) {
                    for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                        StorePriceManager.sync(p);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void applySoftChanges(ServerPlayer player, StorePriceManager.GenesisConfig config) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(com.example.modmenu.store.GenesisManager.GENESIS_DIM);
        if (level != null) {
            // Force immediate update of time/weather if anyone is there
            if (config.tickFreeze) {
                level.setDayTime(config.frozenTime);
            }
            if (config.persistentWeather.equals("Clear")) {
                level.setWeatherParameters(6000, 0, false, false);
            }
        }
    }
}
