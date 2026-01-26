package com.example.modmenu.worldgen;

import com.example.modmenu.store.GenesisManager;
import com.example.modmenu.store.StorePriceManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GenesisBiomeSource extends BiomeSource {
    public static final Codec<GenesisBiomeSource> CODEC = RecordCodecBuilder.create(instance -> 
        instance.group(
            Codec.LONG.fieldOf("seed").forGetter(s -> s.seed)
        ).apply(instance, GenesisBiomeSource::new)
    );

    private final long seed;
    private final PerlinSimplexNoise noise;
    private final StorePriceManager.GenesisConfig config;

    public GenesisBiomeSource(long seed) {
        this.seed = seed;
        this.noise = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(seed)), List.of(0, 1));
        
        StorePriceManager.GenesisConfig current = GenesisManager.getServerConfig();
        this.config = (current != null ? current : StorePriceManager.clientGenesisConfig).snapshot();
    }

    public StorePriceManager.GenesisConfig getConfig() {
        return config;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Stream<Holder<Biome>> collectPossibleBiomes() {
        if (config == null) return Stream.empty();
        return Stream.concat(config.biomes.stream(), config.caveBiomes.stream())
            .distinct()
            .map(GenesisManager::getBiomeHolder)
            .filter(java.util.Objects::nonNull);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // Determine if we should use cave biomes or surface biomes
        // y is in quart coordinates (4 blocks per quart). 
        // We use a more robust check for "underground"
        boolean isUnderground = y < 16; // Below Y=64
        
        // Handle "Caves" world type: everything is caves
        if (config.genType.equals("Caves")) isUnderground = true;
        
        List<String> biomes = isUnderground ? config.caveBiomes : config.biomes;
        
        if (biomes.isEmpty()) {
            biomes = isUnderground ? List.of("minecraft:lush_caves") : List.of("minecraft:plains");
        }

        // Use noise to pick a biome if multiple are selected
        String biomeId;
        if (biomes.size() == 1) {
            biomeId = biomes.get(0);
        } else {
            // Natural noise-based distribution
            double scale = 0.01;
            double noiseValue = noise.getValue(x * scale, z * scale, false);
            double normalized = (noiseValue + 1.0) / 2.0;
            int idx = (int)(normalized * biomes.size());
            idx = Math.max(0, Math.min(biomes.size() - 1, idx));
            
            biomeId = biomes.get(idx);
        }
        
        return GenesisManager.getBiomeHolder(biomeId);
    }
}
