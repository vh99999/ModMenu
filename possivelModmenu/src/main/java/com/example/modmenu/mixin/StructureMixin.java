package com.example.modmenu.mixin;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Structure.class)
public class StructureMixin {
    @Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void onFindValidGenerationPoint(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        // Check if we are in the Genesis dimension. 
        // GenerationContext doesn't have direct level access easily in all mappings, 
        // but we can try to get it from the seed or other context.
        // Actually, we can check if GenesisManager's server is active and the dimension matches.
        
        // A better way: check if the chunk generator is GenesisChunkGenerator
        if (context.chunkGenerator() instanceof com.example.modmenu.worldgen.GenesisChunkGenerator gen) {
            StorePriceManager.GenesisConfig config = gen.getConfig();
            if (config != null) {
                if (config.structureDensity <= 0) {
                    cir.setReturnValue(Optional.empty());
                } else if (config.structureDensity < 1.0) {
                    // Seed-based random to be consistent for the same position
                    long seed = context.seed() ^ (long)context.chunkPos().x ^ (long)context.chunkPos().z;
                    java.util.Random rand = new java.util.Random(seed);
                    if (rand.nextDouble() > config.structureDensity) {
                        cir.setReturnValue(Optional.empty());
                    }
                }
            }
        }
    }
}
