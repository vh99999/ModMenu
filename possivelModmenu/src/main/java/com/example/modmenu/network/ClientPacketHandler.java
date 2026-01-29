package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.logistics.LogisticsCapability;
import com.example.modmenu.store.logistics.NetworkData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.UUID;

public class ClientPacketHandler {
    public static void handleOpenLootRecalibration(int entityId, List<ItemStack> drops, int rerollCount) {
        Minecraft.getInstance().setScreen(new com.example.modmenu.client.ui.screen.LootRecalibrationScreen(entityId, drops, rerollCount));
    }

    public static void handleSyncNetworks(List<NetworkData> networks, UUID viewedNetworkId) {
        if (Minecraft.getInstance().player != null) {
            LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
                data.setNetworks(networks);
                data.viewedNetworkId = viewedNetworkId;
            });
        }
    }

    public static void handleSyncNodeInventory(UUID nodeId, List<ItemStack> inventory, List<Integer> slotX, List<Integer> slotY, ResourceLocation guiTexture) {
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof com.example.modmenu.client.ui.screen.NodeConfigScreen ncs) {
            ncs.handleSyncInventory(nodeId, inventory, slotX, slotY, guiTexture);
        } else if (screen instanceof com.example.modmenu.client.ui.screen.PickItemFromNodeScreen pins) {
            pins.handleSyncInventory(nodeId, inventory, slotX, slotY);
        } else if (screen instanceof com.example.modmenu.client.ui.screen.PickSlotFromNodeScreen psns) {
            psns.handleSyncInventory(nodeId, inventory, slotX, slotY, guiTexture);
        } else if (screen instanceof com.example.modmenu.client.ui.screen.RuleConfigScreen rcs) {
            rcs.handleSyncInventory(nodeId, inventory, slotX, slotY, guiTexture);
        } else if (screen instanceof com.example.modmenu.client.ui.screen.FilterConfigScreen fcs) {
            fcs.handleSyncInventory(nodeId, inventory, slotX, slotY, guiTexture);
        }
    }

    public static void handleSyncSkills(StorePriceManager.SkillData data) {
        synchronized (StorePriceManager.clientSkills) {
            StorePriceManager.SkillData client = StorePriceManager.clientSkills;
            client.totalSP = data.totalSP;
            client.spentSP = data.spentSP;
            client.skillRanks.clear(); client.skillRanks.putAll(data.skillRanks);
            client.unlockedRanks.clear(); client.unlockedRanks.putAll(data.unlockedRanks);
            client.activeToggles.clear(); client.activeToggles.addAll(data.activeToggles);
            client.mobSatiety.clear(); client.mobSatiety.putAll(data.mobSatiety);
            client.branchOrder.clear(); client.branchOrder.addAll(data.branchOrder);
            client.permanentAttributes.clear(); client.permanentAttributes.putAll(data.permanentAttributes);
            client.lastCaptureTimes.clear(); client.lastCaptureTimes.putAll(data.lastCaptureTimes);
            client.blacklistedSpecies.clear(); client.blacklistedSpecies.addAll(data.blacklistedSpecies);
            client.overclockKillsRemaining = data.overclockKillsRemaining;
            client.unlockedChambers = data.unlockedChambers;
            client.totalKills = data.totalKills;
            client.damageReflected = data.damageReflected;
            client.damageHealed = data.damageHealed;
            
            for (int i = 0; i < data.chambers.size(); i++) {
                StorePriceManager.ChamberData other = data.chambers.get(i);
                if (i < client.chambers.size()) {
                    StorePriceManager.ChamberData c = client.chambers.get(i);
                    c.mobId = other.mobId;
                    c.customName = other.customName;
                    c.nbt = other.nbt;
                    c.isExact = other.isExact;
                    c.storedXP = other.storedXP;
                    c.lastHarvestTime = other.lastHarvestTime;
                    c.killerWeapon = other.killerWeapon;
                    c.rerollCount = other.rerollCount;
                    c.paused = other.paused;
                    c.lastOfflineProcessingTime = other.lastOfflineProcessingTime;
                    c.voidFilter.clear(); c.voidFilter.addAll(other.voidFilter);
                    c.updateVersion = other.updateVersion;
                    
                    c.barteringMode = other.barteringMode;
                    c.condensationMode = other.condensationMode;
                    c.speedSlider = other.speedSlider;
                    c.threadSlider = other.threadSlider;
                    c.advancedFilters.clear(); c.advancedFilters.addAll(other.advancedFilters);
                    c.isExcavation = other.isExcavation;
                    c.lootTableId = other.lootTableId;
                    c.linkedContainerPos = other.linkedContainerPos;
                    c.linkedContainerDimension = other.linkedContainerDimension;
                    c.inputLinkPos = other.inputLinkPos;
                    c.inputLinkDimension = other.inputLinkDimension;
                } else {
                    client.chambers.add(other);
                }
            }
            while (client.chambers.size() > data.chambers.size()) {
                client.chambers.remove(client.chambers.size() - 1);
            }

            client.genesisConfig.copyFrom(data.genesisConfig);
            StorePriceManager.clientGenesisConfig.copyFrom(data.genesisConfig);
        }
    }
}
