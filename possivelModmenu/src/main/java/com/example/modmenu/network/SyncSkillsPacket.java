package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class SyncSkillsPacket {
    private final StorePriceManager.SkillData data;

    public SyncSkillsPacket(StorePriceManager.SkillData data) {
        this.data = data.snapshot();
    }

    public SyncSkillsPacket(FriendlyByteBuf buf) {
        this.data = new StorePriceManager.SkillData();
        this.data.totalSP = new java.math.BigDecimal(buf.readUtf());
        this.data.spentSP = new java.math.BigDecimal(buf.readUtf());
        
        int ranksSize = buf.readInt();
        for (int i = 0; i < ranksSize; i++) {
            this.data.skillRanks.put(buf.readUtf(), buf.readInt());
        }

        int unlockedSize = buf.readInt();
        for (int i = 0; i < unlockedSize; i++) {
            this.data.unlockedRanks.put(buf.readUtf(), buf.readInt());
        }
        
        int togglesSize = buf.readInt();
        for (int i = 0; i < togglesSize; i++) {
            this.data.activeToggles.add(buf.readUtf());
        }
        
        int satietySize = buf.readInt();
        for (int i = 0; i < satietySize; i++) {
            this.data.mobSatiety.put(buf.readUtf(), buf.readFloat());
        }
        
        int branchSize = buf.readInt();
        for (int i = 0; i < branchSize; i++) {
            this.data.branchOrder.add(buf.readUtf());
        }

        int attrSize = buf.readInt();
        for (int i = 0; i < attrSize; i++) {
            this.data.permanentAttributes.put(buf.readUtf(), new java.math.BigDecimal(buf.readUtf()));
        }

        int captureTimesSize = buf.readInt();
        for (int i = 0; i < captureTimesSize; i++) {
            this.data.lastCaptureTimes.put(buf.readUtf(), buf.readLong());
        }

        int blacklistSize = buf.readInt();
        for (int i = 0; i < blacklistSize; i++) {
            this.data.blacklistedSpecies.add(buf.readUtf());
        }
        this.data.overclockKillsRemaining = buf.readInt();
        this.data.unlockedChambers = buf.readInt();
        this.data.totalKills = new java.math.BigDecimal(buf.readUtf());
        this.data.damageReflected = new java.math.BigDecimal(buf.readUtf());
        this.data.damageHealed = new java.math.BigDecimal(buf.readUtf());
        
        int chamberSize = buf.readInt();
        for (int i = 0; i < chamberSize; i++) {
            StorePriceManager.ChamberData chamber = new StorePriceManager.ChamberData();
            chamber.mobId = buf.readUtf();
            chamber.customName = buf.readBoolean() ? buf.readUtf() : null;
            chamber.isExact = buf.readBoolean();
            if (chamber.isExact) chamber.nbt = buf.readNbt();
            
            // Stored loot is no longer sent in this packet to avoid overflow.
            // It is requested per-chamber via RequestChamberLootPacket.
            chamber.storedLoot.clear();
            
            chamber.storedXP = new java.math.BigDecimal(buf.readUtf());
            chamber.lastHarvestTime = buf.readLong();
            if (buf.readBoolean()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
                int count = buf.readInt();
                net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item == null ? net.minecraft.world.item.Items.AIR : item, count);
                stack.setTag(tag);
                chamber.killerWeapon = stack;
            } else {
                chamber.killerWeapon = net.minecraft.world.item.ItemStack.EMPTY;
            }
            chamber.rerollCount = buf.readInt();
            int filterSize = buf.readInt();
            for (int j = 0; j < filterSize; j++) {
                chamber.voidFilter.add(buf.readUtf());
            }
            chamber.updateVersion = buf.readInt();
            this.data.chambers.add(chamber);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(data.totalSP.toString());
        buf.writeUtf(data.spentSP.toString());
        
        buf.writeInt(data.skillRanks.size());
        data.skillRanks.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeInt(v);
        });

        buf.writeInt(data.unlockedRanks.size());
        data.unlockedRanks.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeInt(v);
        });
        
        buf.writeInt(data.activeToggles.size());
        data.activeToggles.forEach(buf::writeUtf);
        
        buf.writeInt(data.mobSatiety.size());
        data.mobSatiety.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeFloat(v);
        });
        
        buf.writeInt(data.branchOrder.size());
        data.branchOrder.forEach(buf::writeUtf);

        buf.writeInt(data.permanentAttributes.size());
        data.permanentAttributes.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeUtf(v.toString());
        });

        buf.writeInt(data.lastCaptureTimes.size());
        data.lastCaptureTimes.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeLong(v);
        });

        buf.writeInt(data.blacklistedSpecies.size());
        data.blacklistedSpecies.forEach(buf::writeUtf);
        buf.writeInt(data.overclockKillsRemaining);
        buf.writeInt(data.unlockedChambers);
        buf.writeUtf(data.totalKills.toString());
        buf.writeUtf(data.damageReflected.toString());
        buf.writeUtf(data.damageHealed.toString());
        
        buf.writeInt(data.chambers.size());
        for (StorePriceManager.ChamberData chamber : data.chambers) {
            buf.writeUtf(chamber.mobId);
            buf.writeBoolean(chamber.customName != null);
            if (chamber.customName != null) buf.writeUtf(chamber.customName);
            buf.writeBoolean(chamber.isExact);
            if (chamber.isExact) buf.writeNbt(chamber.nbt);
            
            // Stored loot excluded here
            
            buf.writeUtf(chamber.storedXP.toString());
            buf.writeLong(chamber.lastHarvestTime);
            if (chamber.killerWeapon == null || chamber.killerWeapon.isEmpty()) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeResourceLocation(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(chamber.killerWeapon.getItem()));
                buf.writeInt(chamber.killerWeapon.getCount());
                buf.writeNbt(chamber.killerWeapon.getTag());
            }
            buf.writeInt(chamber.rerollCount);
            buf.writeInt(chamber.voidFilter.size());
            for (String filterId : chamber.voidFilter) {
                buf.writeUtf(filterId);
            }
            buf.writeInt(chamber.updateVersion);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            synchronized (StorePriceManager.clientSkills) {
                StorePriceManager.SkillData client = StorePriceManager.clientSkills;
                client.totalSP = this.data.totalSP;
                client.spentSP = this.data.spentSP;
                client.skillRanks.clear(); client.skillRanks.putAll(this.data.skillRanks);
                client.unlockedRanks.clear(); client.unlockedRanks.putAll(this.data.unlockedRanks);
                client.activeToggles.clear(); client.activeToggles.addAll(this.data.activeToggles);
                client.mobSatiety.clear(); client.mobSatiety.putAll(this.data.mobSatiety);
                client.branchOrder.clear(); client.branchOrder.addAll(this.data.branchOrder);
                client.permanentAttributes.clear(); client.permanentAttributes.putAll(this.data.permanentAttributes);
                client.lastCaptureTimes.clear(); client.lastCaptureTimes.putAll(this.data.lastCaptureTimes);
                client.blacklistedSpecies.clear(); client.blacklistedSpecies.addAll(this.data.blacklistedSpecies);
                client.overclockKillsRemaining = this.data.overclockKillsRemaining;
                client.unlockedChambers = this.data.unlockedChambers;
                client.totalKills = this.data.totalKills;
                client.damageReflected = this.data.damageReflected;
                client.damageHealed = this.data.damageHealed;
                
                for (int i = 0; i < this.data.chambers.size(); i++) {
                    StorePriceManager.ChamberData other = this.data.chambers.get(i);
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
                        // Stored loot is kept as is on client
                    } else {
                        client.chambers.add(other);
                    }
                }
                while (client.chambers.size() > this.data.chambers.size()) {
                    client.chambers.remove(client.chambers.size() - 1);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
