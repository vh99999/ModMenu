package com.example.modmenu.client;

import com.example.modmenu.store.GenesisManager;
import net.minecraft.client.Minecraft;

public class ClientHelper {
    public static boolean isGenesisLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().equals(GenesisManager.GENESIS_DIM);
    }

    public static float getAmbientLight() {
        return (float)com.example.modmenu.store.StorePriceManager.clientGenesisConfig.ambientLight;
    }
}
