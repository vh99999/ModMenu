package com.example.modmenu.mixin;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.GenesisManager;
import com.example.modmenu.client.ClientHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeMixin {
    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void onGetSkyColor(CallbackInfoReturnable<Integer> cir) {
        if (FMLEnvironment.dist == Dist.CLIENT && ClientHelper.isGenesisLevel()) {
            int custom = StorePriceManager.clientGenesisConfig.skyColor;
            if (custom != -1) cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private void onGetWaterColor(CallbackInfoReturnable<Integer> cir) {
        if (FMLEnvironment.dist == Dist.CLIENT && ClientHelper.isGenesisLevel()) {
            int custom = StorePriceManager.clientGenesisConfig.waterColor;
            if (custom != -1) cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getWaterFogColor", at = @At("HEAD"), cancellable = true)
    private void onGetWaterFogColor(CallbackInfoReturnable<Integer> cir) {
        if (FMLEnvironment.dist == Dist.CLIENT && ClientHelper.isGenesisLevel()) {
            int custom = StorePriceManager.clientGenesisConfig.waterColor;
            if (custom != -1) cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getGrassColor", at = @At("HEAD"), cancellable = true)
    private void onGetGrassColor(double x, double z, CallbackInfoReturnable<Integer> cir) {
        if (FMLEnvironment.dist == Dist.CLIENT && ClientHelper.isGenesisLevel()) {
            int custom = StorePriceManager.clientGenesisConfig.grassColor;
            if (custom != -1) cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getFoliageColor", at = @At("HEAD"), cancellable = true)
    private void onGetFoliageColor(CallbackInfoReturnable<Integer> cir) {
        if (FMLEnvironment.dist == Dist.CLIENT && ClientHelper.isGenesisLevel()) {
            int custom = StorePriceManager.clientGenesisConfig.foliageColor;
            if (custom != -1) cir.setReturnValue(custom);
        }
    }
}
