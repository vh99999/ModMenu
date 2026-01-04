package com.example.modmenu.worldgen;

import com.example.modmenu.modmenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class MiningDimension {
    public static final ResourceKey<Level> MINING_DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, 
            new ResourceLocation(modmenu.MODID, "mining_dimension"));
    public static final ResourceKey<DimensionType> MINING_DIMENSION_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, 
            new ResourceLocation(modmenu.MODID, "mining_dimension_type"));
}
