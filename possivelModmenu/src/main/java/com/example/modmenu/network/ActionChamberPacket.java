package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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
    private final int itemIndex;
    private List<String> filterData;

    public ActionChamberPacket(int index, int action) {
        this(index, action, -1);
    }

    public ActionChamberPacket(int index, int action, int itemIndex) {
        this.index = index;
        this.action = action;
        this.itemIndex = itemIndex;
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
                                ItemStack stack = chamber.storedLoot.remove(itemIndex);
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                                chamber.updateVersion++;
                            }
                        }
                        case 5 -> {
                            if (itemIndex >= 0 && itemIndex < chamber.storedLoot.size()) {
                                chamber.storedLoot.remove(itemIndex);
                                chamber.updateVersion++;
                            }
                        }
                        case 7 -> {
                            chamber.paused = !chamber.paused;
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Chamber] §eSimulation " + (chamber.paused ? "PAUSED" : "RESUMED")), true);
                        }
                        case 8 -> {
                            if (filterData != null) {
                                chamber.voidFilter.clear();
                                chamber.voidFilter.addAll(filterData);
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
