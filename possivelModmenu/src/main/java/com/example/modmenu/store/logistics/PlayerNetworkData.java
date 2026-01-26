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
        String json = GSON.toJson(networks);
        nbt.putString("networks", json);
    }

    public void loadNBT(CompoundTag nbt) {
        if (nbt.contains("networks")) {
            String json = nbt.getString("networks");
            try {
                List<NetworkData> loaded = GSON.fromJson(json, new TypeToken<List<NetworkData>>(){}.getType());
                if (loaded != null) {
                    this.networks = loaded;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public void copyFrom(PlayerNetworkData other) {
        this.networks = new CopyOnWriteArrayList<>();
        for (NetworkData nd : other.networks) {
            this.networks.add(nd.snapshot());
        }
    }
}
