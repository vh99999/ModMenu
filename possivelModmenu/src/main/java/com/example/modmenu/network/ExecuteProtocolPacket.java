package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class ExecuteProtocolPacket {
    private final int protocolId;

    public ExecuteProtocolPacket(int protocolId) {
        this.protocolId = protocolId;
    }

    public ExecuteProtocolPacket(FriendlyByteBuf buf) {
        this.protocolId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(protocolId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                BigDecimal cost = getCost(protocolId);
                
                if (data.totalSP.subtract(data.spentSP).compareTo(cost) >= 0) {
                    boolean success = execute(player, protocolId, data);
                    if (success) {
                        data.spentSP = data.spentSP.add(cost);
                        StorePriceManager.sync(player);
                    }
                } else {
                    player.displayClientMessage(Component.literal("§cNot enough SP!"), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private BigDecimal getCost(int id) {
        long base = switch (id) {
            case 0 -> 1000;
            case 1 -> 5000;
            case 2 -> 500;
            case 3 -> 25000;
            case 4 -> 10000;
            case 5 -> 5000;
            case 6 -> 5000;
            case 7 -> 100000;
            case 8 -> 2500;
            case 9 -> 7500;
            case 10 -> 15000;
            case 11 -> 50;
            case 12 -> 10000;
            case 13 -> 250000;
            case 14 -> 500000;
            case 15 -> 1000000;
            default -> -1;
        };
        if (base == -1) return new BigDecimal("1e100"); // Extremely expensive fallback
        return BigDecimal.valueOf(base);
    }

    private boolean execute(ServerPlayer player, int id, StorePriceManager.SkillData data) {
        net.minecraft.world.phys.HitResult hit = player.pick(5.0D, 0.0F, false);
        net.minecraft.world.phys.EntityHitResult entityHit = null;
        // Check for entity hit specifically
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 view = player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 end = eye.add(view.scale(5.0D));
        entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, player.getBoundingBox().expandTowards(view.scale(5.0D)).inflate(1.0D), e -> true);

        switch (id) {
            case 0 -> { // Personal Nexus
                player.setRespawnPosition(player.level().dimension(), player.blockPosition(), player.getYRot(), true, true);
                player.displayClientMessage(Component.literal("§6[Root] §aPersonal Nexus Synchronized!"), true);
                return true;
            }
            case 1 -> { // Sector Zero
                player.serverLevel().setDefaultSpawnPos(player.blockPosition(), player.getYRot());
                player.displayClientMessage(Component.literal("§6[Root] §aWorld Spawn Realigned to Sector Zero!"), true);
                return true;
            }
            case 2 -> { // Dimensional Anchor
                data.activeToggles.add("PROTOCOL_DIM_ANCHOR");
                player.displayClientMessage(Component.literal("§6[Root] §aDimensional Anchor Active!"), true);
                return true;
            }
            case 3 -> { // Inventory Preservation
                data.activeToggles.add("PROTOCOL_KEEP_INV");
                player.displayClientMessage(Component.literal("§6[Root] §aInventory Preservation Active!"), true);
                return true;
            }
            case 4 -> { // Neural XP Backup
                data.activeToggles.add("PROTOCOL_KEEP_XP");
                player.displayClientMessage(Component.literal("§6[Root] §aNeural Experience Backup Active!"), true);
                return true;
            }
            case 5 -> { // Anti-Griefing Aura
                data.activeToggles.add("PROTOCOL_ANTI_GRIEF");
                player.displayClientMessage(Component.literal("§6[Root] §aAnti-Griefing Aura Active!"), true);
                return true;
            }
            case 6 -> { // Emergency System Restore (Handled on death screen, just unlock here?)
                data.activeToggles.add("PROTOCOL_SYSTEM_RESTORE");
                player.displayClientMessage(Component.literal("§6[Root] §aEmergency System Restore Protocol Enabled!"), true);
                return true;
            }
            case 7 -> { // Global Registry Purge
                data.mobSatiety.clear();
                player.displayClientMessage(Component.literal("§6[Root] §aGlobal Satiety Registry Purged!"), true);
                return true;
            }
            case 8 -> { // Chronos Lock
                data.activeToggles.add("PROTOCOL_CHRONOS_LOCK");
                player.displayClientMessage(Component.literal("§6[Root] §aChronos Lock Active!"), true);
                return true;
            }
            case 9 -> { // Tectonic Stabilization
                data.activeToggles.add("PROTOCOL_FALL_IMMUNE");
                player.displayClientMessage(Component.literal("§6[Root] §aTectonic Stabilization Active!"), true);
                return true;
            }
            case 10 -> { // Species Blacklist
                if (entityHit != null && entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
                    String typeId = ForgeRegistries.ENTITY_TYPES.getKey(living.getType()).toString();
                    data.blacklistedSpecies.add(typeId);
                    living.discard();
                    player.displayClientMessage(Component.literal("§6[Root] §cBlacklisted Species: §e" + typeId), true);
                    return true;
                }
                player.displayClientMessage(Component.literal("§cLook at a mob to blacklist its species!"), true);
                return false;
            }
            case 11 -> { // Substrate Injection
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !player.getOffhandItem().isEmpty()) {
                    net.minecraft.core.BlockPos pos = ((net.minecraft.world.phys.BlockHitResult)hit).getBlockPos();
                    net.minecraft.world.level.block.Block target = net.minecraft.world.level.block.Block.byItem(player.getOffhandItem().getItem());
                    if (target != net.minecraft.world.level.block.Blocks.AIR) {
                        player.level().setBlock(pos, target.defaultBlockState(), 3);
                        player.displayClientMessage(Component.literal("§6[Root] §aSubstrate Injected!"), true);
                        return true;
                    }
                }
                player.displayClientMessage(Component.literal("§cLook at a block and hold target material in off-hand!"), true);
                return false;
            }
            case 12 -> { // Loot Table Overclock
                data.overclockKillsRemaining += 100;
                player.displayClientMessage(Component.literal("§6[Root] §aLoot Table Overclocked for 100 kills!"), true);
                return true;
            }
            case 13 -> { // Registry Editor
                if (entityHit != null && entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
                    // Open simple UI to select new type? For now, cycle or random?
                    // User said "Turn Warden into a Pig".
                    // Let's use whatever is in off-hand (if it's a spawn egg?) or just cycle.
                    // Implementation: turn into Pig for now as example or based on off-hand.
                    net.minecraft.world.item.ItemStack off = player.getOffhandItem();
                    if (off.getItem() instanceof net.minecraft.world.item.SpawnEggItem egg) {
                        EntityType<?> newType = egg.getType(off.getTag());
                        net.minecraft.world.entity.Entity newEntity = newType.create(player.level());
                        if (newEntity != null) {
                            newEntity.moveTo(living.position());
                            living.discard();
                            player.level().addFreshEntity(newEntity);
                            player.displayClientMessage(Component.literal("§6[Root] §aEntity type overwritten!"), true);
                            return true;
                        }
                    }
                }
                player.displayClientMessage(Component.literal("§cLook at a mob and hold a Spawn Egg in off-hand!"), true);
                return false;
            }
            case 14 -> { // Code Optimization
                data.activeToggles.add("PROTOCOL_SP_REDUCTION");
                player.displayClientMessage(Component.literal("§6[Root] §aCode Optimized! SP costs reduced by 15%."), true);
                return true;
            }
            case 15 -> { // God Strength
                data.activeToggles.add("PROTOCOL_GOD_STRENGTH");
                player.displayClientMessage(Component.literal("§6[Root] §aGod Strength Protocol Initialized!"), true);
                return true;
            }
        }
        return false;
    }
}
