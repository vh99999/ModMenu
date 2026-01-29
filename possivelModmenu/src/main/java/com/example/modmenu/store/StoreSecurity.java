package com.example.modmenu.store;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public class StoreSecurity {
    
    public static boolean canModifyPrices(ServerPlayer player) {
        if (player == null) return false;
        if (StorePriceManager.isEditor(player.getUUID())) return true;
        
        player.displayClientMessage(Component.literal("\u00A7cAccess Denied: You must be an Editor to modify prices."), true);
        return false;
    }
    
    public static boolean canAccessDimensionConfigurator(ServerPlayer player) {
        if (player == null) return false;
        UUID uuid = player.getUUID();
        if (StorePriceManager.isEditor(uuid)) return true;
        if (StorePriceManager.getUnlockedHouses(uuid).contains("dimension_configurator")) return true;
        
        player.displayClientMessage(Component.literal("\u00A7c[Genesis Hub] Access Denied: Dimension Configurator not unlocked."), true);
        return false;
    }
    
    public static boolean canRegenerateDimension(ServerPlayer player) {
        if (player == null) return false;
        // First check if they have the configurator
        if (!canAccessDimensionConfigurator(player)) return false;
        
        // Then check if the dimension is currently unlocked for changes
        StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
        if (data.genesisConfig.locked) {
            player.displayClientMessage(Component.literal("\u00A7c[Genesis Hub] Access Denied: Dimension is currently locked."), true);
            return false;
        }
        
        return true;
    }

    public static boolean canAccessLogistics(ServerPlayer player) {
        return true;
    }
}
