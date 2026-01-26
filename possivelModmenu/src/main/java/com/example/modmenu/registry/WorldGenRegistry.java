package com.example.modmenu.registry;

import com.example.modmenu.modmenu;
import com.example.modmenu.worldgen.GenesisBiomeSource;
import com.example.modmenu.worldgen.GenesisChunkGenerator;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

public class WorldGenRegistry {
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, modmenu.MODID);
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, modmenu.MODID);

    public static void register(IEventBus eventBus) {
        BIOME_SOURCES.register("genesis", () -> GenesisBiomeSource.CODEC);
        CHUNK_GENERATORS.register("genesis", () -> GenesisChunkGenerator.CODEC);
        BIOME_SOURCES.register(eventBus);
        CHUNK_GENERATORS.register(eventBus);
    }
}
