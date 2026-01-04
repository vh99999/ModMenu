package com.example.modmenu.worldgen;

import com.example.modmenu.modmenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

public class WorldGenRegistry {
    public static final DeferredRegister<com.mojang.serialization.Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = 
            DeferredRegister.create(Registries.CHUNK_GENERATOR, modmenu.MODID);

    public static void register(IEventBus eventBus) {
        CHUNK_GENERATORS.register(eventBus);
        CHUNK_GENERATORS.register("mining_chunk_generator", () -> MiningChunkGenerator.CODEC);
    }
}
