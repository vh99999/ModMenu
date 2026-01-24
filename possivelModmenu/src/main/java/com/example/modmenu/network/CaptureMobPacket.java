package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class CaptureMobPacket {
    private final int entityId;
    private final boolean isExact;
    private final String lootTableId;

    public CaptureMobPacket(int entityId, boolean isExact) {
        this.entityId = entityId;
        this.isExact = isExact;
        this.lootTableId = null;
    }

    public CaptureMobPacket(String lootTableId) {
        this.entityId = -1;
        this.isExact = false;
        this.lootTableId = lootTableId;
    }

    public CaptureMobPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isExact = buf.readBoolean();
        this.lootTableId = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(isExact);
        buf.writeBoolean(lootTableId != null);
        if (lootTableId != null) buf.writeUtf(lootTableId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (data.chambers.size() >= data.unlockedChambers) {
                    player.displayClientMessage(Component.literal("\u00A7cVirtual Containment is full! Unlock more chambers in the menu."), true);
                    return;
                }

                if (lootTableId != null) {
                    // Excavation Capture: Requires 4 fragments
                    net.minecraft.world.item.Item fragment = com.example.modmenu.registry.ItemRegistry.LOOT_DATA_FRAGMENT.get();
                    int fragmentCount = 0;
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.is(fragment)) fragmentCount += stack.getCount();
                    }

                    if (fragmentCount < 4) {
                        player.displayClientMessage(Component.literal("\u00A7cYou need 4 Loot Data Fragments to initialize excavation!"), true);
                        return;
                    }

                    BigDecimal cost = new BigDecimal("10000");
                    if (data.totalSP.subtract(data.spentSP).compareTo(cost) >= 0) {
                        // Consume fragments
                        int toRemove = 4;
                        for (ItemStack stack : player.getInventory().items) {
                            if (stack.is(fragment)) {
                                int take = Math.min(toRemove, stack.getCount());
                                stack.shrink(take);
                                toRemove -= take;
                                if (toRemove <= 0) break;
                            }
                        }

                        data.spentSP = data.spentSP.add(cost);
                        StorePriceManager.ChamberData chamber = new StorePriceManager.ChamberData();
                        chamber.isExcavation = true;
                        chamber.lootTableId = lootTableId;
                        chamber.mobId = "minecraft:chest"; // Display placeholder
                        chamber.lastHarvestTime = player.level().getGameTime();
                        data.chambers.add(chamber);
                        player.displayClientMessage(Component.literal("\u00A76[Containment] \u00A7aExcavation Protocol Initialized! \u00A7dCost: " + cost + " SP"), true);
                        StorePriceManager.markDirty(player.getUUID());
                        StorePriceManager.sync(player);
                    } else {
                        player.displayClientMessage(Component.literal("\u00A7cNot enough SP to initialize excavation!"), true);
                    }
                    return;
                }

                Entity target = player.level().getEntity(entityId);
                if (target instanceof PartEntity<?> part) {
                    target = (Entity) part.getParent();
                }
                
                if (target instanceof LivingEntity living) {
                    // Security: Distance check
                    if (player.distanceToSqr(target) > 256) { // 16 blocks radius
                        return;
                    }
                    
                    BigDecimal cost = StorePriceManager.safeBD(living.getMaxHealth()).multiply(BigDecimal.valueOf(isExact ? 100 : 10));
                    
                    if (data.totalSP.subtract(data.spentSP).compareTo(cost) >= 0) {
                        data.spentSP = data.spentSP.add(cost);
                        
                        StorePriceManager.ChamberData chamber = new StorePriceManager.ChamberData();
                        net.minecraft.resources.ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
                        chamber.mobId = key != null ? key.toString() : "minecraft:pig";
                        chamber.customName = living.getCustomName() != null ? living.getCustomName().getString() : null;
                        chamber.isExact = isExact;
                        chamber.lastHarvestTime = player.level().getGameTime();
                        if (isExact) {
                            chamber.nbt = living.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                        }
                        
                        data.chambers.add(chamber);
                        living.discard();
                        
                        player.displayClientMessage(Component.literal("\u00A76[Containment] \u00A7aMob Captured! \u00A7dCost: " + cost + " SP"), true);
                        StorePriceManager.markDirty(player.getUUID());
                        StorePriceManager.sync(player);
                    } else {
                        player.displayClientMessage(Component.literal("\u00A7cNot enough Skill Points to capture!"), true);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
