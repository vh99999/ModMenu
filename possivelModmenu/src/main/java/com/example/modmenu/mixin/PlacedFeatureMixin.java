package com.example.modmenu.mixin;

import com.example.modmenu.worldgen.GenesisChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {
    @Shadow @Final private Holder<ConfiguredFeature<?, ?>> feature;
    
    private static final ThreadLocal<Boolean> IS_DECORATING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (generator instanceof GenesisChunkGenerator genesisGenerator) {
            com.example.modmenu.store.StorePriceManager.GenesisConfig config = genesisGenerator.getConfig();
            if (config == null) return;

            // 1. Lava/Pool Filtering
            if (!config.spawnLavaLakes) {
                feature.unwrapKey().ifPresent(key -> {
                    String path = key.location().getPath();
                    if (path.contains("lava") && (path.contains("lake") || path.contains("pool"))) {
                        cir.setReturnValue(false);
                    }
                });
                if (cir.isCancelled()) return;
            }

            // 2. Resource Density Logic
            double density = config.resourceDensity;
            if (density <= 0) {
                cir.setReturnValue(false);
            } else if (density < 1.0) {
                if (random.nextDouble() > density) {
                    cir.setReturnValue(false);
                }
            } else if (density > 1.0) {
                // To avoid infinite recursion, we check if we are already in a recursive call
                if (IS_DECORATING.get()) return;
                
                IS_DECORATING.set(true);
                try {
                    int extraPasses = (int) Math.floor(density) - 1;
                    double chance = density - Math.floor(density);
                    
                    for (int i = 0; i < extraPasses; i++) {
                        ((PlacedFeature)(Object)this).place(level, generator, random, pos);
                    }
                    if (chance > 0 && random.nextDouble() < chance) {
                        ((PlacedFeature)(Object)this).place(level, generator, random, pos);
                    }
                } finally {
                    IS_DECORATING.set(false);
                }
            }
        }
    }
}
