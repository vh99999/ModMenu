package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class CaptureMobPacket {
    private final int entityId;
    private final boolean isExact;

    public CaptureMobPacket(int entityId, boolean isExact) {
        this.entityId = entityId;
        this.isExact = isExact;
    }

    public CaptureMobPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isExact = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(isExact);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity target = player.level().getEntity(entityId);
                if (target instanceof LivingEntity living) {
                    // Security: Distance check
                    if (player.distanceToSqr(target) > 256) { // 16 blocks radius
                        return;
                    }
                    StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                    
                    if (data.chambers.size() >= data.unlockedChambers) {
                        player.displayClientMessage(Component.literal("§cVirtual Containment is full! Unlock more chambers in the menu."), true);
                        return;
                    }

                    BigDecimal cost = BigDecimal.valueOf(living.getMaxHealth()).multiply(BigDecimal.valueOf(isExact ? 100 : 10));
                    
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
                        
                        player.displayClientMessage(Component.literal("§6[Containment] §aMob Captured! §dCost: " + cost + " SP"), true);
                        StorePriceManager.sync(player);
                    } else {
                        player.displayClientMessage(Component.literal("§cNot enough Skill Points to capture!"), true);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
