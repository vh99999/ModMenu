package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ActionChamberPacket {
    private final int index;
    private final int action; // 0: Harvest XP, 1: Collect All Loot, 2: Void All Loot, 3: Set Weapon, 4: Collect Single, 5: Void Single, 6: Unlock Chamber, 7: Toggle Pause, 8: Sync Filter
    // 9: Toggle Bartering, 10: Cycle Condensation, 11: Set Yield Target, 12: Set Speed, 13: Set Thread, 14: Update Advanced Filter, 15: Clear Input
    private final int itemIndex;
    private List<String> filterData;
    private String stringData;
    private int intData;
    private net.minecraft.nbt.CompoundTag nbtData;
    private net.minecraft.world.item.Item itemData;

    public ActionChamberPacket(int index, int action) {
        this(index, action, -1);
    }

    public ActionChamberPacket(int index, int action, int itemIndex) {
        this.index = index;
        this.action = action;
        this.itemIndex = itemIndex;
    }

    public ActionChamberPacket(int index, int action, int itemIndex, net.minecraft.world.item.Item item, net.minecraft.nbt.CompoundTag nbt) {
        this.index = index;
        this.action = action;
        this.itemIndex = itemIndex;
        this.itemData = item;
        this.nbtData = nbt;
    }

    public ActionChamberPacket(int index, int action, String stringData, int intData, net.minecraft.nbt.CompoundTag nbt) {
        this.index = index;
        this.action = action;
        this.itemIndex = -1;
        this.stringData = stringData;
        this.intData = intData;
        this.nbtData = nbt;
    }

    public ActionChamberPacket(int index, int action, String stringData, int intData) {
        this(index, action, stringData, intData, null);
    }

    public ActionChamberPacket(int index, List<String> filterData) {
        this.index = index;
        this.action = 8;
        this.itemIndex = -1;
        this.filterData = filterData;
    }

    public ActionChamberPacket(FriendlyByteBuf buf) {
        this.index = buf.readInt();
        this.action = buf.readInt();
        this.itemIndex = buf.readInt();
        if (action == 8) {
            int size = buf.readInt();
            this.filterData = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                this.filterData.add(buf.readUtf());
            }
        } else if (action == 11 || action == 12 || action == 13 || action == 14) {
            this.stringData = buf.readUtf();
            this.intData = buf.readInt();
            if (buf.readBoolean()) this.nbtData = buf.readNbt();
        } else if (action == 4 || action == 5) {
            if (buf.readBoolean()) this.itemData = buf.readRegistryIdSafe(net.minecraft.world.item.Item.class);
            if (buf.readBoolean()) this.nbtData = buf.readNbt();
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(index);
        buf.writeInt(action);
        buf.writeInt(itemIndex);
        if (action == 8) {
            buf.writeInt(filterData.size());
            for (String s : filterData) {
                buf.writeUtf(s);
            }
        } else if (action == 11 || action == 12 || action == 13 || action == 14) {
            buf.writeUtf(stringData != null ? stringData : "");
            buf.writeInt(intData);
            buf.writeBoolean(nbtData != null);
            if (nbtData != null) buf.writeNbt(nbtData);
        } else if (action == 4 || action == 5) {
            buf.writeBoolean(itemData != null);
            if (itemData != null) buf.writeRegistryId(ForgeRegistries.ITEMS, itemData);
            buf.writeBoolean(nbtData != null);
            if (nbtData != null) buf.writeNbt(nbtData);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (action == 6) {
                    BigDecimal cost = new BigDecimal("5000");
                    int chambers = data.unlockedChambers - 1;
                    if (chambers > 0) {
                        // (1.5)^n = (3/2)^n = 3^n / 2^n
                        cost = cost.multiply(new BigDecimal("3").pow(chambers))
                                   .divide(new BigDecimal("2").pow(chambers), 10, RoundingMode.HALF_UP);
                    }
                    cost = cost.setScale(0, RoundingMode.HALF_UP);

                    if (data.totalSP.subtract(data.spentSP).compareTo(cost) >= 0) {
                        data.spentSP = data.spentSP.add(cost);
                        data.unlockedChambers++;
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Chamber] §aUnlocked Chamber Slot " + data.unlockedChambers + "! §dCost: " + cost + " SP"), true);
                        StorePriceManager.sync(player);
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot enough SP to unlock slot! Need " + cost), true);
                    }
                    return;
                }

                if (index >= 0 && index < data.chambers.size()) {
                    StorePriceManager.ChamberData chamber = data.chambers.get(index);
                    switch (action) {
                        case 0 -> {
                            while (chamber.storedXP.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal maxInt = BigDecimal.valueOf(Integer.MAX_VALUE);
                                int toGive = chamber.storedXP.compareTo(maxInt) >= 0 ? Integer.MAX_VALUE : chamber.storedXP.intValue();
                                player.giveExperiencePoints(toGive);
                                chamber.storedXP = chamber.storedXP.subtract(BigDecimal.valueOf(toGive));
                                if (toGive < Integer.MAX_VALUE) break;
                                if (player.totalExperience >= Integer.MAX_VALUE) break;
                            }
                        }
                        case 1 -> {
                            for (ItemStack stack : chamber.storedLoot) {
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                            }
                            chamber.storedLoot.clear();
                            chamber.updateVersion++;
                        }
                        case 2 -> {
                            chamber.storedLoot.clear();
                            chamber.updateVersion++;
                        }
                        case 3 -> {
                            ItemStack held = player.getMainHandItem();
                            if (!held.isEmpty()) {
                                chamber.killerWeapon = held.copy();
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Chamber] §aKiller Weapon set to: §e" + held.getHoverName().getString()), true);
                            } else {
                                chamber.killerWeapon = ItemStack.EMPTY;
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Chamber] §cKiller Weapon cleared!"), true);
                            }
                            chamber.updateVersion++;
                        }
                        case 4 -> {
                            if (itemIndex >= 0 && itemIndex < chamber.storedLoot.size()) {
                                ItemStack stack = chamber.storedLoot.get(itemIndex);
                                // Verify identity
                                if (itemData != null && stack.getItem() == itemData && net.minecraft.nbt.NbtUtils.compareNbt(nbtData, stack.getTag(), true)) {
                                    chamber.storedLoot.remove(itemIndex);
                                    if (!player.getInventory().add(stack)) {
                                        player.drop(stack, false);
                                    }
                                    chamber.updateVersion++;
                                }
                            }
                        }
                        case 5 -> {
                            if (itemIndex >= 0 && itemIndex < chamber.storedLoot.size()) {
                                ItemStack stack = chamber.storedLoot.get(itemIndex);
                                // Verify identity
                                if (itemData != null && stack.getItem() == itemData && net.minecraft.nbt.NbtUtils.compareNbt(nbtData, stack.getTag(), true)) {
                                    chamber.storedLoot.remove(itemIndex);
                                    chamber.updateVersion++;
                                }
                            }
                        }
                        case 7 -> {
                            chamber.paused = !chamber.paused;
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Chamber] §eSimulation " + (chamber.paused ? "PAUSED" : "RESUMED")), true);
                        }
                        case 8 -> {
                            if (filterData != null) {
                                chamber.voidFilter.clear();
                                chamber.voidFilter.addAll(filterData.subList(0, Math.min(filterData.size(), 100))); // Cap generic filter too
                            }
                        }
                        case 9 -> chamber.barteringMode = !chamber.barteringMode;
                        case 10 -> chamber.condensationMode = (chamber.condensationMode + 1) % 3;
                        case 11 -> {
                            if (stringData != null) {
                                if (intData <= 0) chamber.yieldTargets.remove(stringData);
                                else if (chamber.yieldTargets.size() < 50 || chamber.yieldTargets.containsKey(stringData)) {
                                    chamber.yieldTargets.put(stringData, intData);
                                }
                            }
                        }
                        case 12 -> chamber.speedSlider = Math.max(1, Math.min(20, intData));
                        case 13 -> chamber.threadSlider = Math.max(1, Math.min(20, intData));
                        case 14 -> { // Advanced filter update
                            int matchTypeIdx = (intData >> 16) & 0xFFFF;
                            int filterAction = (short)(intData & 0xFFFF); // Use short to allow -1
                            
                            if (filterAction == -1) {
                                String type = matchTypeIdx == 0 ? "ID" : (matchTypeIdx == 1 ? "TAG" : "NBT");
                                chamber.advancedFilters.removeIf(r -> r.matchType.equals(type) && r.matchValue.equals(stringData));
                            } else if (chamber.advancedFilters.size() < 50) {
                                String[] types = {"ID", "TAG", "NBT"};
                                StorePriceManager.FilterRule rule = new StorePriceManager.FilterRule();
                                rule.matchType = types[Math.min(2, matchTypeIdx)];
                                rule.matchValue = stringData;
                                rule.action = filterAction;
                                rule.nbtSample = nbtData != null ? nbtData.copy() : null;
                                
                                boolean ruleExists = false;
                                for (StorePriceManager.FilterRule r : chamber.advancedFilters) {
                                    if (r.matchType.equals(rule.matchType) && r.matchValue.equals(rule.matchValue)) {
                                        r.action = rule.action;
                                        r.nbtSample = rule.nbtSample;
                                        ruleExists = true;
                                        break;
                                    }
                                }
                                if (!ruleExists) chamber.advancedFilters.add(rule);
                            }
                        }
                        case 15 -> {
                            for (ItemStack stack : chamber.inputBuffer) {
                                if (!player.getInventory().add(stack)) player.drop(stack, false);
                            }
                            chamber.inputBuffer.clear();
                        }
                        case 16 -> { // Put held item into input buffer
                            if (chamber.inputBuffer.size() >= 32) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cInput buffer is full!"), true);
                                return;
                            }
                            ItemStack held = player.getMainHandItem();
                            if (!held.isEmpty()) {
                                chamber.inputBuffer.add(held.copy());
                                held.setCount(0);
                            }
                        }
                    }
                    StorePriceManager.sync(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
