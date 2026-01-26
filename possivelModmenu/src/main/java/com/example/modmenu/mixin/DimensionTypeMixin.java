package com.example.modmenu.mixin;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.GenesisManager;
import com.example.modmenu.client.ClientHelper;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {
    @Inject(method = "ambientLight", at = @At("HEAD"), cancellable = true)
    private void onAmbientLight(CallbackInfoReturnable<Float> cir) {
        if (FMLEnvironment.dist == Dist.CLIENT && ClientHelper.isGenesisLevel()) {
            cir.setReturnValue(ClientHelper.getAmbientLight());
        }
    }

    @Inject(method = "coordinateScale", at = @At("HEAD"), cancellable = true)
    private void onCoordinateScale(CallbackInfoReturnable<Double> cir) {
        // This is used on server too for portal logic.
        if (isGenesisType()) {
            double scale = GenesisManager.getSnapshottedDimensionScale();
            if (scale != 1.0) {
                cir.setReturnValue(scale);
            }
        }
    }

    private boolean isGenesisType() {
        return GenesisManager.isGenesisType((DimensionType)(Object)this);
    }

    @Inject(method = "bedWorks", at = @At("HEAD"), cancellable = true)
    private void onBedWorks(CallbackInfoReturnable<Boolean> cir) {
        if (isGenesisType()) {
            StorePriceManager.GenesisConfig config = GenesisManager.getServerConfig();
            if (config == null) config = StorePriceManager.clientGenesisConfig;
            if (config != null) {
                cir.setReturnValue(config.respawnLogicEnabled);
            }
        }
    }

    @Inject(method = "respawnAnchorWorks", at = @At("HEAD"), cancellable = true)
    private void onRespawnAnchorWorks(CallbackInfoReturnable<Boolean> cir) {
        if (isGenesisType()) {
            StorePriceManager.GenesisConfig config = GenesisManager.getServerConfig();
            if (config == null) config = StorePriceManager.clientGenesisConfig;
            if (config != null) {
                cir.setReturnValue(config.respawnLogicEnabled);
            }
        }
    }
}
