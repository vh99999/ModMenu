package com.example.modmenu.mixin;

import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    // This Mixin is now empty.
    // The previous dynamic setting swapping was causing crashes and has been replaced 
    // by constructor-level snapshotting in GenesisChunkGenerator.
}
