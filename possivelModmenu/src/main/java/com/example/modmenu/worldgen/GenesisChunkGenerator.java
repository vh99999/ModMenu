package com.example.modmenu.worldgen;

import com.example.modmenu.store.GenesisManager;
import com.example.modmenu.store.StorePriceManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class GenesisChunkGenerator extends NoiseBasedChunkGenerator {
    public static final Codec<GenesisChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> 
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(GenesisChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(GenesisChunkGenerator::getOriginalSettings)
        ).apply(instance, GenesisChunkGenerator::new)
    );

    private final Holder<NoiseGeneratorSettings> originalSettings;
    private final StorePriceManager.GenesisConfig config;

    public GenesisChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, GenesisManager.getSettingsForGenerator(settings));
        this.originalSettings = settings;
        
        StorePriceManager.GenesisConfig current = GenesisManager.getServerConfig();
        this.config = (current != null ? current : StorePriceManager.clientGenesisConfig).snapshot();
    }

    public StorePriceManager.GenesisConfig getConfig() {
        return config;
    }

    public Holder<NoiseGeneratorSettings> getOriginalSettings() {
        return originalSettings;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {
        if (config != null && (config.genType.equals("Void") || config.genType.equals("Superflat"))) {
            return; // No surface building for these types
        }
        
        super.buildSurface(region, structures, randomState, chunk);
        
        if (config != null) {
            // Apply Bedrock Control
            applyBedrockControl(chunk, config);
        }
    }

    private void applyBedrockControl(ChunkAccess chunk, StorePriceManager.GenesisConfig config) {
        String control = config.bedrockControl;
        if (control.equals("Normal")) return;

        boolean floor = control.equals("Floor") || control.equals("Both");
        boolean ceiling = control.equals("Ceiling") || control.equals("Both");
        boolean none = control.equals("None");

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (none || !floor) {
                    // Remove floor bedrock (typically at the very bottom)
                    for (int y = chunk.getMinBuildHeight(); y < chunk.getMinBuildHeight() + 10; y++) {
                        pos.set(x, y, z);
                        if (chunk.getBlockState(pos).is(Blocks.BEDROCK)) {
                            chunk.setBlockState(pos, Blocks.DEEPSLATE.defaultBlockState(), false);
                        }
                    }
                }
                if (none || !ceiling) {
                    // Remove ceiling bedrock (typically at the very top)
                    for (int y = chunk.getMaxBuildHeight() - 10; y < chunk.getMaxBuildHeight(); y++) {
                        pos.set(x, y, z);
                        if (chunk.getBlockState(pos).is(Blocks.BEDROCK)) {
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        if (config != null) {
            if (config.genType.equals("Void")) {
                return CompletableFuture.completedFuture(chunk);
            }
            if (config.genType.equals("Superflat")) {
                BlockState stone = Blocks.STONE.defaultBlockState();
                BlockState dirt = Blocks.DIRT.defaultBlockState();
                BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
                BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.setBlockState(pos.set(x, chunk.getMinBuildHeight(), z), bedrock, false);
                        for (int y = chunk.getMinBuildHeight() + 1; y < 60; y++) {
                            chunk.setBlockState(pos.set(x, y, z), stone, false);
                        }
                        for (int y = 60; y < 63; y++) {
                            chunk.setBlockState(pos.set(x, y, z), dirt, false);
                        }
                        chunk.setBlockState(pos.set(x, 63, z), grass, false);
                    }
                }
                return CompletableFuture.completedFuture(chunk);
            }
        }

        return super.fillFromNoise(executor, blender, randomState, structureManager, chunk).thenApply(access -> {
            if (config != null && !config.seaLevelFluid.equals("minecraft:water")) {
                // Change fluid
                replaceFluids(access, config);
            }
            return access;
        });
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // Resource Density (>1.0) and filtering are now handled in PlacedFeatureMixin
        // for much better performance.
        super.applyBiomeDecoration(level, chunk, structureManager);
    }

    private void replaceFluids(ChunkAccess chunk, StorePriceManager.GenesisConfig config) {
        ResourceLocation fluidLoc = ResourceLocation.tryParse(config.seaLevelFluid);
        if (fluidLoc == null || !net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(fluidLoc)) return;
        
        BlockState targetFluid = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(fluidLoc).defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int seaLevel = this.getSeaLevel();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getMinBuildHeight(); y < seaLevel; y++) {
                    pos.set(x, y, z);
                    if (chunk.getBlockState(pos).is(Blocks.WATER)) {
                        chunk.setBlockState(pos, targetFluid, false);
                    }
                }
            }
        }
    }
}
