package com.example.modmenu.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MiningChunkGenerator extends ChunkGenerator {
    public static final Codec<MiningChunkGenerator> CODEC = RecordCodecBuilder.create((instance) -> 
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter((gen) -> gen.biomeSource)
        ).apply(instance, instance.stable(MiningChunkGenerator::new))
    );

    public MiningChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // Bedrock at -64
                    chunk.setBlockState(pos.set(x, -64, z), Blocks.BEDROCK.defaultBlockState(), false);
                    
                    // Deepslate layer -63 to -17
                    for (int y = -63; y < -16; y++) {
                        chunk.setBlockState(pos.set(x, y, z), Blocks.DEEPSLATE.defaultBlockState(), false);
                    }
                    
                    // Transition layer -16 to 0
                    for (int y = -16; y < 0; y++) {
                        int blockX = chunk.getPos().getMinBlockX() + x;
                        int blockZ = chunk.getPos().getMinBlockZ() + z;
                        long seed = (long)blockX * 3129871L ^ (long)blockZ * 116129781L ^ (long)y;
                        double threshold = (double)(y - (-16)) / 16.0; // 0 at -16, 1 at 0
                        
                        // Simple deterministic pseudo-random
                        double randomVal = (double)((seed ^ (seed >>> 32)) & 0x7FFFFFFF) / (double)0x7FFFFFFF;

                        if (randomVal > threshold) {
                             chunk.setBlockState(pos.set(x, y, z), Blocks.DEEPSLATE.defaultBlockState(), false);
                        } else {
                             chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                        }
                    }
                    
                    // Stone layer 0 to 60
                    for (int y = 0; y < 61; y++) {
                        chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                    }
                    
                    // Dirt layer 61 to 63
                    for (int y = 61; y < 64; y++) {
                        chunk.setBlockState(pos.set(x, y, z), Blocks.DIRT.defaultBlockState(), false);
                    }
                    
                    // Grass layer 64
                    chunk.setBlockState(pos.set(x, 64, z), Blocks.GRASS_BLOCK.defaultBlockState(), false);
                }
            }
            return chunk;
        }, executor);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        // Surface is already built in fillFromNoise for a flat world
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // No carvers
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No mob spawning
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            chunk.fillBiomesFromNoise(this.biomeSource, randomState.sampler());
            return chunk;
        }, executor);
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return 65;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return new NoiseColumn(-64, new BlockState[0]); // Simplified
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
    }
}
