package com.example.modmenu.store.logistics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class PlayerNetworkData {
    private static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(net.minecraft.world.item.ItemStack.class, new com.example.modmenu.store.GsonAdapters.ItemStackAdapter())
            .registerTypeAdapter(net.minecraft.nbt.CompoundTag.class, new com.example.modmenu.store.GsonAdapters.CompoundTagAdapter())
            .registerTypeAdapter(net.minecraftforge.fluids.FluidStack.class, new com.example.modmenu.store.GsonAdapters.FluidStackAdapter())
            .create();

    private List<NetworkData> networks = new CopyOnWriteArrayList<>();
    public int updateVersion = 0;
    public transient java.util.UUID linkingNetworkId = null;
    public transient java.util.UUID viewedNetworkId = null;

    public List<NetworkData> getNetworks() {
        return networks;
    }

    public void setNetworks(List<NetworkData> networks) {
        this.networks = networks;
        this.updateVersion++;
    }

    public void saveNBT(CompoundTag nbt) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (NetworkData net : networks) {
            CompoundTag tag = new CompoundTag();
            net.saveNBT(tag);
            list.add(tag);
        }
        nbt.put("networks_v2", list);
        nbt.putInt("updateVersion", updateVersion);
    }

    public void loadNBT(CompoundTag nbt) {
        if (nbt.contains("networks_v2")) {
            net.minecraft.nbt.ListTag list = nbt.getList("networks_v2", 10);
            this.networks = new java.util.concurrent.CopyOnWriteArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                this.networks.add(NetworkData.loadNBT(list.getCompound(i)));
            }
        } else if (nbt.contains("networks")) {
            // Legacy GSON fallback
            String json = nbt.getString("networks");
            try {
                List<NetworkData> loaded = GSON.fromJson(json, new TypeToken<List<NetworkData>>(){}.getType());
                if (loaded != null) {
                    this.networks = new java.util.concurrent.CopyOnWriteArrayList<>(loaded);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.updateVersion = nbt.getInt("updateVersion");
    }
    
    public void copyFrom(PlayerNetworkData other) {
        this.networks = new CopyOnWriteArrayList<>();
        for (NetworkData nd : other.networks) {
            this.networks.add(nd.snapshot());
        }
    }
}
