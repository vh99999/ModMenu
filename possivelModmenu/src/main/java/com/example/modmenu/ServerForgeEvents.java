package com.example.modmenu;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "modmenu", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerForgeEvents {
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (StorePriceManager.globalSkillsDisabled) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
            
            // Tectonic Stabilization Protocol (Fall Immunity)
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL) && skillData.activeToggles.contains("PROTOCOL_FALL_IMMUNE")) {
                event.setCanceled(true);
                return;
            }

            // 0. Contractual Immortality (Wealth Branch)
            if (player.getHealth() - event.getAmount() <= 0) {
                int immRank = SkillManager.getActiveRank(skillData, "WEALTH_IMMORTALITY");
                if (immRank > 0) {
                    BigDecimal cost = BigDecimal.valueOf(1_000_000_000.0 / Math.pow(4, immRank - 1)).setScale(0, RoundingMode.HALF_UP);
                    if (immRank >= 5) cost = BigDecimal.valueOf(1_000_000);
                    
                    if (StorePriceManager.canAfford(player.getUUID(), cost)) {
                        StorePriceManager.addMoney(player.getUUID(), cost.negate());
                        
                        // Golden Handshake
                        int goldenRank = SkillManager.getActiveRank(skillData, "WEALTH_GOLDEN_HANDSHAKE");
                        if (goldenRank > 0) {
                            BigDecimal balance = StorePriceManager.getMoney(player.getUUID());
                            float explosionDmg = balance.multiply(new BigDecimal("0.01")).floatValue();
                            player.level().explode(player, player.getX(), player.getY(), player.getZ(), 4.0f, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                            net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(8);
                            for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class, area)) {
                                if (nearby != player) nearby.hurt(player.damageSources().magic(), explosionDmg);
                            }
                        }

                        event.setCanceled(true);
                        player.setHealth(player.getMaxHealth());
                        player.displayClientMessage(Component.literal("\u00A76[Contractual Immortality] \u00A7aLife Restored! Cost: \u00A7e$" + StorePriceManager.formatCurrency(cost)), true);
                        return;
                    }
                }
            }

            // Omnipotence Paradox (Combat Keystone)
            if (skillData.activeToggles.contains("COMBAT_KEYSTONE_OMNIPOTENCE")) {
                event.setCanceled(true);
                return;
            }

            // 1. Causality Reversal (Combat Branch)
            int causalityRank = SkillManager.getActiveRank(skillData, "COMBAT_CAUSALITY_REVERSAL");
            if (causalityRank > 0) {
                float healAmount = event.getAmount() * (0.2f * causalityRank);
                player.heal(healAmount);
                skillData.damageHealed = skillData.damageHealed.add(BigDecimal.valueOf(healAmount));
                if (causalityRank >= 5) { // Entropy Shield
                    event.setCanceled(true);
                    return;
                }
            }

            // Overclocked Reflexes (Combat Branch)
            int reflexRank = SkillManager.getActiveRank(skillData, "COMBAT_OVERCLOCKED_REFLEXES");
            if (reflexRank > 0 && event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
                if (player.level().random.nextFloat() < 0.10f * reflexRank) {
                    event.setCanceled(true);
                    net.minecraft.world.entity.Entity projectile = event.getSource().getDirectEntity();
                    if (projectile != null) {
                        net.minecraft.world.phys.Vec3 motion = projectile.getDeltaMovement();
                        projectile.setDeltaMovement(motion.scale(-1));
                        if (projectile instanceof net.minecraft.world.entity.projectile.Projectile p) {
                            p.setOwner(player);
                        }
                    }
                    player.displayClientMessage(Component.literal("\u00A7c[Overclocked Reflexes] \u00A76Projectile Parried!"), true);
                    return;
                }
            }

            // 2. Damage Cancel (Ability)
            boolean sovereignty = skillData.activeToggles.contains("COMBAT_KEYSTONE_SOVEREIGNTY") && StorePriceManager.getMoney(player.getUUID()).compareTo(new BigDecimal("1000000000")) >= 0;
            if (settings.damageCancelActive || sovereignty) {
                float damage = event.getAmount();
                BigDecimal cost = sovereignty ? BigDecimal.ZERO : BigDecimal.valueOf(damage).multiply(BigDecimal.valueOf(StorePriceManager.formulas.damageCancelMultiplier));
                
                if (StorePriceManager.canAfford(player.getUUID(), cost)) {
                    if (cost.compareTo(BigDecimal.ZERO) > 0) StorePriceManager.addMoney(player.getUUID(), cost.negate());
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("\u00A7bDamage Cancelled!" + (cost.compareTo(BigDecimal.ZERO) > 0 ? " Cost: \u00A7e$" + StorePriceManager.formatCurrency(cost) : "")), true);
                    StorePriceManager.sync(player);
                }
            }

            // 3. Defensive Feedback (Combat Branch)
            int feedbackRank = SkillManager.getActiveRank(skillData, "COMBAT_DEFENSIVE_FEEDBACK");
            if (feedbackRank > 0 && event.getSource().getEntity() instanceof LivingEntity attacker) {
                float reflected = event.getAmount() * (2.0f * feedbackRank); // 200% to 20,000%
                skillData.damageReflected = skillData.damageReflected.add(BigDecimal.valueOf(reflected));
                if (feedbackRank >= 10) { // Retribution
                    attacker.discard();
                } else {
                    attacker.hurt(attacker.damageSources().thorns(player), reflected);
                    if (feedbackRank >= 8) { // Strike with lightning
                        ServerLevel level = player.serverLevel();
                        net.minecraft.world.entity.LightningBolt lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                        if (lightning != null) {
                            lightning.moveTo(attacker.position());
                            level.addFreshEntity(lightning);
                        }
                    }
                }
            }
        }
    }

    public static class LootBuffer {
        public List<ItemStack> drops;
        public int rerollCount = 0;
        public final ResourceLocation lootTable;
        public final net.minecraft.world.entity.EntityType<?> entityType;
        public final net.minecraft.world.phys.Vec3 pos;
        public final UUID playerUuid;
        public final long timestamp;

        public LootBuffer(List<ItemStack> drops, ResourceLocation lootTable, net.minecraft.world.entity.EntityType<?> entityType, net.minecraft.world.phys.Vec3 pos, UUID playerUuid) {
            this.drops = drops;
            this.lootTable = lootTable;
            this.entityType = entityType;
            this.pos = pos;
            this.playerUuid = playerUuid;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static boolean processingSureKill = false;
    private static Map<UUID, List<ScheduledDamage>> scheduledDamages = new HashMap<>();
    public static Map<UUID, List<BlockPos>> pendingMining = new HashMap<>();
    public static Map<UUID, List<LiquidationRegion>> pendingRegions = new HashMap<>();
    public static Map<Integer, LootBuffer> bufferedLoot = new HashMap<>();

    public static class LiquidationRegion {
        public final int minX, maxX, minY, maxY, minZ, maxZ;
        public int currentX, currentY, currentZ;

        public LiquidationRegion(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX; this.maxX = maxX;
            this.minY = minY; this.maxY = maxY;
            this.minZ = minZ; this.maxZ = maxZ;
            this.currentX = minX;
            this.currentY = minY;
            this.currentZ = minZ;
        }

        public boolean isDone() {
            return currentX > maxX;
        }
    }

    private static class ScheduledDamage {
        final LivingEntity victim;
        final float amount;
        int remaining;
        long triggerTick;

        ScheduledDamage(LivingEntity victim, float amount, int remaining, long triggerTick) {
            this.victim = victim; this.amount = amount; this.remaining = remaining; this.triggerTick = triggerTick;
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            StorePriceManager.SkillData data = StorePriceManager.getSkills(event.getOriginal().getUUID());
            if (data.activeToggles.contains("PROTOCOL_KEEP_INV")) {
                for (int i = 0; i < event.getOriginal().getInventory().getContainerSize(); i++) {
                    event.getEntity().getInventory().setItem(i, event.getOriginal().getInventory().getItem(i));
                }
            }
            if (data.activeToggles.contains("PROTOCOL_KEEP_XP")) {
                event.getEntity().experienceLevel = event.getOriginal().experienceLevel;
                event.getEntity().experienceProgress = event.getOriginal().experienceProgress;
                event.getEntity().totalExperience = event.getOriginal().totalExperience;
            }
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        if (StorePriceManager.globalSkillsDisabled) return;
        
        // Species Blacklist Protocol
        // Check all players' data if they have this mob blacklisted nearby
        for (ServerPlayer player : event.getLevel().getServer().getPlayerList().getPlayers()) {
            StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
            String typeId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()).toString();
            if (data.blacklistedSpecies.contains(typeId)) {
                if (event.getEntity().blockPosition().closerThan(player.blockPosition(), 128)) {
                    event.setSpawnCancelled(true);
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (StorePriceManager.globalSkillsDisabled) return;
        if (processingSureKill) return;
        
        // Dimensional Anchor Protocol
        if (event.getEntity() instanceof ServerPlayer player) {
            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
            if (skillData.activeToggles.contains("PROTOCOL_DIM_ANCHOR") || SkillManager.getActiveRank(skillData, "UTILITY_STATIC_RIFTS") > 0) {
                // Immunity logic here
            }
        }

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
            
            // God Strength Protocol
            float multiplier = 1.0f;
            if (skillData.activeToggles.contains("PROTOCOL_GOD_STRENGTH")) multiplier *= 10.0f;
            
            // Omnipotence Paradox (Combat Keystone)
            if (skillData.activeToggles.contains("COMBAT_KEYSTONE_OMNIPOTENCE")) {
                event.getEntity().hurt(player.damageSources().playerAttack(player), event.getEntity().getMaxHealth() * 10);
                event.setCanceled(true);
                return;
            }

            // 0. Entity Defragmentation (Branch C)
            if (skillData.activeToggles.contains("UTILITY_ENTITY_DEFRAGMENTATION")) {
                LivingEntity victim = event.getEntity();
                victim.discard(); // harvested instantly as per desc
                // Value is hard to determine for all entities, use HP based?
                java.math.BigDecimal value = StorePriceManager.safeBD(victim.getMaxHealth()).multiply(java.math.BigDecimal.valueOf(100));
                StorePriceManager.addMoney(player.getUUID(), value);
                player.displayClientMessage(Component.literal("\u00A76[Entity Defrag] \u00A7aHarvested for \u00A7e$" + StorePriceManager.formatCurrency(value)), true);
                event.setCanceled(true);
                return;
            }

            // 1. Authority Overload & Neural Link (Reach)
            // Reach is handled in applyAttribute, speed too.
            // Authority Overload bonus damage:
            int overloadRank = SkillManager.getActiveRank(skillData, "COMBAT_AUTHORITY_OVERLOAD");
            if (overloadRank > 0) {
                // Deal extra damage equal to 0.001% of balance per rank
                BigDecimal balance = StorePriceManager.getMoney(player.getUUID());
                float extraDmg = balance.multiply(new BigDecimal("0.00001")).multiply(BigDecimal.valueOf(overloadRank)).floatValue() * multiplier;
                event.getEntity().hurt(player.damageSources().playerAttack(player), extraDmg);
            }

            // Target Lockdown (Combat Branch)
            int lockdownRank = SkillManager.getActiveRank(skillData, "COMBAT_TARGET_LOCKDOWN");
            if (lockdownRank > 0) {
                LivingEntity victim = event.getEntity();
                victim.setDeltaMovement(0, 0, 0);
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 255, false, false));
                if (victim instanceof net.minecraft.world.entity.Mob mob) mob.setTarget(player);
            }

            // Probability Collapse (Branch B)
            int probRank = SkillManager.getActiveRank(skillData, "COMBAT_PROBABILITY_COLLAPSE");
            if (probRank >= 10) {
                event.getEntity().hurt(player.damageSources().playerAttack(player), event.getEntity().getMaxHealth() * 10 * multiplier);
            }

            // Chronos Breach (Branch B)
            int chronosRank = SkillManager.getActiveRank(skillData, "COMBAT_CHRONOS_BREACH");
            if (chronosRank > 0) {
                scheduledDamages.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                    .add(new ScheduledDamage(event.getEntity(), event.getAmount(), chronosRank, player.level().getGameTime() + 100));
            }

            // 2. Sure Kill
            boolean sovereignty = skillData.activeToggles.contains("COMBAT_KEYSTONE_SOVEREIGNTY") && StorePriceManager.getMoney(player.getUUID()).compareTo(new BigDecimal("1000000000")) >= 0;
            if (settings.sureKillActive || sovereignty) {
                LivingEntity victim = event.getEntity();
                float health = victim.getHealth();
                
                // Lethal Optimization (Branch B)
                int lethalRank = SkillManager.getActiveRank(skillData, "COMBAT_LETHAL_OPTIMIZATION");
                double reduction = 0.2 * lethalRank;
                
                BigDecimal baseCost = StorePriceManager.formulas.sureKillBaseCost;
                BigDecimal healthCost = StorePriceManager.safeBD(health).multiply(BigDecimal.valueOf(StorePriceManager.formulas.sureKillHealthMultiplier));
                
                BigDecimal cost = sovereignty ? BigDecimal.ZERO : baseCost.add(healthCost).multiply(BigDecimal.valueOf(1.0 - reduction));
                
                // Sure Kill Protocol Rank 3: $0 cost
                if (SkillManager.getActiveRank(skillData, "COMBAT_SURE_KILL_PROTOCOL") >= 3) cost = BigDecimal.ZERO;

                BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());

                if (StorePriceManager.canAfford(player.getUUID(), cost)) {
                    if (cost.compareTo(BigDecimal.ZERO) > 0) StorePriceManager.addMoney(player.getUUID(), cost.negate());
                    processingSureKill = true;
                    victim.hurt(event.getSource(), Float.MAX_VALUE);
                    processingSureKill = false;
                    player.displayClientMessage(Component.literal("\u00A74Sure Kill Active!" + (cost.compareTo(BigDecimal.ZERO) > 0 ? " Cost: \u00A7e$" + StorePriceManager.formatCurrency(cost) : "")), true);
                    
                    // Executioner's Tax (Combat Branch)
                    int taxRank = SkillManager.getActiveRank(skillData, "COMBAT_EXECUTIONERS_TAX");
                    if (taxRank > 0) {
                        int count = skillData.activeToggles.stream().filter(s -> s.startsWith("SUREKILL_COUNT_")).map(s -> Integer.parseInt(s.substring(15))).findFirst().orElse(0);
                        count++;
                        skillData.activeToggles.removeIf(s -> s.startsWith("SUREKILL_COUNT_"));
                        if (count >= 10) {
                            skillData.activeToggles.add("FREE_PURCHASE_TOKEN");
                            player.displayClientMessage(Component.literal("\u00A7c[Executioner's Tax] \u00A76Free Purchase Token Granted!"), true);
                            count = 0;
                        }
                        skillData.activeToggles.add("SUREKILL_COUNT_" + count);
                    }

                    StorePriceManager.sync(player);
                    event.setCanceled(true);
                }
            }

            // 3. Singularity Strike (Branch B)
            int singularityRank = SkillManager.getActiveRank(skillData, "COMBAT_SINGULARITY_STRIKE");
            if (singularityRank > 0) {
                // Pull mobs within 15 blocks
                net.minecraft.world.phys.AABB area = event.getEntity().getBoundingBox().inflate(15);
                for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class, area)) {
                    if (nearby != player && nearby != event.getEntity()) {
                        nearby.setDeltaMovement(event.getEntity().position().subtract(nearby.position()).normalize().scale(0.5));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player) {
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            if (settings.noAggroActive) {
                BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
                BigDecimal cost = StorePriceManager.formulas.noAggroCostPerCancel;
                if (currentMoney.compareTo(cost) >= 0) {
                    StorePriceManager.addMoney(player.getUUID(), cost.negate());
                    event.setCanceled(true);
                    StorePriceManager.sync(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            if (settings.areaMiningActive) {
                performAreaMining(player, settings, event.getPos(), event.getState());
            }
        }
    }

    private static int getSlotIndex(Iterable<ItemStack> slots, ItemStack target) {
        int i = 0;
        for (ItemStack s : slots) {
            if (s == target) return i;
            i++;
        }
        return 0;
    }

    private static void performAreaMining(ServerPlayer player, StorePriceManager.AbilitySettings settings, BlockPos pos, BlockState state) {
        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
        int size = settings.areaMiningSize;
        
        // Structural Refactoring (Branch C)
        int structRank = SkillManager.getActiveRank(skillData, "UTILITY_STRUCTURAL_REFACTORING");
        if (structRank > 0) {
            size += (int)(16 * StorePriceManager.dampedDouble(BigDecimal.valueOf(structRank), 10.0));
        }

        if (size <= 1) return;
        
        int radius = (size - 1) / 2;
        ServerLevel level = player.serverLevel();
        BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
        BigDecimal totalCost = BigDecimal.ZERO;
        List<BlockPos> toBreak = new ArrayList<>();

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    mutablePos.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    BlockState s = level.getBlockState(mutablePos);
                    if (s.isAir() || s.getBlock() == Blocks.BEDROCK) continue;
                    if (isMiningBlacklisted(s, settings.miningBlacklist)) continue;
                    
                    BigDecimal cost = StorePriceManager.formulas.areaMiningCostBase;
                    if (StorePriceManager.canAfford(player.getUUID(), totalCost.add(cost))) {
                        totalCost = totalCost.add(cost);
                        toBreak.add(mutablePos.immutable());
                    }
                }
            }
        }

        if (!toBreak.isEmpty()) {
            StorePriceManager.addMoney(player.getUUID(), totalCost.negate());
            pendingMining.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).addAll(toBreak);
            player.displayClientMessage(Component.literal("\u00A76[Area Mining] \u00A7aQueued " + toBreak.size() + " blocks for liquidation."), true);
            StorePriceManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()) {
            ServerPlayer player = (ServerPlayer) event.player;
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            
            if (settings.stepAssistActive) {
                net.minecraft.world.entity.ai.attributes.AttributeInstance stepHeight = player.getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get());
                if (stepHeight != null) {
                    if (stepHeight.getBaseValue() != (double)settings.stepAssistHeight) {
                        stepHeight.setBaseValue((double)settings.stepAssistHeight);
                    }
                    
                    // Simple "each assist" detection: if moved up without jumping/flying and collided horizontally
                    if (player.horizontalCollision && player.getDeltaMovement().y > 0 && !player.isFallFlying() && !player.getAbilities().flying) {
                         BigDecimal cost = StorePriceManager.formulas.stepAssistCostPerAssist;
                         if (StorePriceManager.canAfford(player.getUUID(), cost)) {
                             StorePriceManager.addMoney(player.getUUID(), cost.negate());
                             StorePriceManager.sync(player);
                         }
                    }
                }
            } else {
                net.minecraft.world.entity.ai.attributes.AttributeInstance stepHeight = player.getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get());
                if (stepHeight != null && stepHeight.getBaseValue() != 0) {
                     stepHeight.setBaseValue(0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long time = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld().getGameTime();
        
        for (UUID uuid : scheduledDamages.keySet()) {
            List<ScheduledDamage> list = scheduledDamages.get(uuid);
            list.removeIf(d -> {
                if (time >= d.triggerTick) {
                    if (d.victim.isAlive()) {
                        d.victim.hurt(d.victim.damageSources().magic(), d.amount);
                        d.remaining--;
                        d.triggerTick = time + 100; // 5 seconds
                        return d.remaining <= 0;
                    }
                    return true;
                }
                return false;
            });
        }

        // Process Liquidation Regions (Scanning)
        for (UUID uuid : pendingRegions.keySet()) {
            ServerPlayer player = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            List<LiquidationRegion> regions = pendingRegions.get(uuid);
            if (regions.isEmpty()) continue;

            LiquidationRegion region = regions.get(0);
            int scanned = 0;
            BigDecimal totalValue = BigDecimal.ZERO;
            List<BlockPos> toBreak = new ArrayList<>();
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

            while (scanned < 1000 && !region.isDone()) {
                mpos.set(region.currentX, region.currentY, region.currentZ);
                BlockState state = player.level().getBlockState(mpos);
                if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
                    totalValue = totalValue.add(StorePriceManager.getBuyPrice(state.getBlock().asItem()));
                    toBreak.add(mpos.immutable());
                }

                region.currentY++;
                if (region.currentY > region.maxY) {
                    region.currentY = region.minY;
                    region.currentZ++;
                    if (region.currentZ > region.maxZ) {
                        region.currentZ = region.minZ;
                        region.currentX++;
                    }
                }
                scanned++;
            }

            if (!toBreak.isEmpty()) {
                StorePriceManager.addMoney(player.getUUID(), totalValue);
                pendingMining.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).addAll(toBreak);
            }

            if (region.isDone()) {
                regions.remove(0);
                if (regions.isEmpty()) {
                    player.displayClientMessage(Component.literal("\u00A76[Chunk Liquidation] \u00A7aLiquidation scan complete."), true);
                }
            }
        }

        // Process Area Mining batches (Max 1000 blocks per tick per player)
        for (UUID uuid : pendingMining.keySet()) {
            ServerPlayer player = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            List<BlockPos> list = pendingMining.get(uuid);
            if (list.isEmpty()) continue;
            
            int toProcess = Math.min(list.size(), 1000);
            for (int i = 0; i < toProcess; i++) {
                BlockPos p = list.remove(0);
                if (player.level().hasChunkAt(p)) {
                    player.level().destroyBlock(p, false, player);
                }
            }
        }
    }

    private static boolean pricesCalculated = false;
    private static volatile boolean pricesCalculating = false;

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StorePriceManager.loadPlayerData(player.getUUID()); // Load individual data
            
            if (!pricesCalculated && !pricesCalculating) {
                pricesCalculating = true;
                player.displayClientMessage(Component.literal("\u00A76[ModMenu] \u00A7eCalculating item prices... Some features may be delayed."), false);
                
                net.minecraft.server.MinecraftServer server = player.getServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            StorePriceManager.addAllItems(player.level());
                            pricesCalculated = true;
                            pricesCalculating = false;
                            
                            server.getPlayerList().broadcastSystemMessage(Component.literal("\u00A76[ModMenu] \u00A7aItem pricing calculation complete!"), false);
                            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                StorePriceManager.syncPrices(p);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            pricesCalculating = false;
                        }
                    });
                }
            }
            
            SkillManager.handleOfflineProcessing(player);
            StorePriceManager.applyAllAttributes(player);
            StorePriceManager.sync(player);
            if (pricesCalculated) StorePriceManager.syncPrices(player);

            com.example.modmenu.store.logistics.LogisticsCapability.getNetworks(player).ifPresent(data -> {
                com.example.modmenu.network.PacketHandler.sendToPlayer(
                    new com.example.modmenu.network.SyncNetworksPacket(data.getNetworks()),
                    player
                );
            });

            if (StorePriceManager.isDataCorrupted) {
                com.example.modmenu.network.PacketHandler.sendToPlayer(
                    new com.example.modmenu.network.DataCorruptionPacket(StorePriceManager.lastLoadError),
                    player
                );
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            StorePriceManager.savePlayerData(uuid);
            StorePriceManager.unloadPlayerData(uuid);
            scheduledDamages.remove(uuid);
            pendingMining.remove(uuid);
            pendingRegions.remove(uuid);
        }
    }

    @SubscribeEvent
    public static void onLevelSave(net.minecraftforge.event.level.LevelEvent.Save event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            StorePriceManager.saveAllDirty();
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StorePriceManager.applyAllAttributes(player);
            StorePriceManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StorePriceManager.applyAllAttributes(player);
            StorePriceManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {
            ItemStack stack = event.getEntity().getItem();
            if (stack.getOrCreateTag().getInt("modmenu_lock_state") >= 1) {
                event.setCanceled(true);
                // Return to player's inventory
                if (!event.getPlayer().getInventory().add(stack)) {
                    // This case shouldn't really happen during a toss but good to have
                    event.getPlayer().drop(stack, false);
                }
                event.getPlayer().displayClientMessage(Component.literal("\u00A7cThis item is protected and cannot be dropped!"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            LivingEntity victim = event.getEntity();
            float maxHealth = victim.getMaxHealth();
            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
            skillData.totalKills = skillData.totalKills.add(BigDecimal.ONE);
            
            // 1. SP Calculation
            boolean bySureKill = processingSureKill;
            int sureKillProtocol = SkillManager.getActiveRank(skillData, "COMBAT_SURE_KILL_PROTOCOL");
            
            if (!bySureKill || sureKillProtocol > 0) {
                // Points = floor(sqrt(MaxHP / 10))
                BigDecimal hpBD = StorePriceManager.safeBD(maxHealth);
                BigDecimal baseSP = StorePriceManager.safeBD(Math.floor(Math.sqrt(maxHealth / 10.0)));
                
                // Apply global SP multiplier
                BigDecimal spGain = baseSP.multiply(BigDecimal.valueOf(StorePriceManager.formulas.spMultiplier));
                
                if (bySureKill) {
                    if (sureKillProtocol == 1) spGain = spGain.multiply(new BigDecimal("0.25"));
                    else if (sureKillProtocol >= 3) spGain = spGain.multiply(BigDecimal.valueOf(5)); // 500% SP
                }
                
                // Blood Data bonus (Branch B)
                int bloodData = SkillManager.getActiveRank(skillData, "COMBAT_BLOOD_DATA");
                if (bloodData > 0 && player.level().random.nextFloat() < 0.10f) {
                    BigDecimal bonus = spGain.multiply(BigDecimal.valueOf(bloodData)).multiply(new BigDecimal("0.5"));
                    spGain = spGain.add(bonus.max(BigDecimal.ONE));
                    player.displayClientMessage(Component.literal("\u00A7c[Blood Data] \u00A7dExtra SP harvested!"), true);
                }

                if (spGain.compareTo(BigDecimal.ZERO) > 0) {
                    spGain = spGain.setScale(0, RoundingMode.FLOOR);
                    skillData.totalSP = skillData.totalSP.add(spGain);
                    player.displayClientMessage(Component.literal("\u00A7d+ " + spGain.toPlainString() + " Skill Points"), true);
                }
            }

            // 2. Money Reward & Satiety
            String mobId = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType()).toString();
            float currentSatiety = skillData.mobSatiety.getOrDefault(mobId, 0f);
            BigDecimal rewardMultiplier = BigDecimal.ONE.divide(BigDecimal.ONE.add(BigDecimal.valueOf(currentSatiety)), 10, RoundingMode.HALF_UP);
            
            int bountyRank = SkillManager.getActiveRank(skillData, "WEALTH_EXPONENTIAL_BOUNTY");
            double exponent = 1.0;
            BigDecimal baseMult = BigDecimal.valueOf(5.0);
            if (bountyRank >= 1) exponent = 1.2;
            if (bountyRank >= 2) exponent = 1.5;
            if (bountyRank >= 3) exponent = 2.0;
            if (bountyRank >= 4) exponent = 2.5;
            if (bountyRank >= 5) baseMult = BigDecimal.valueOf(100.0);
            
            // Global Income Multiplier (Midas Touch Keystone)
            if (skillData.activeToggles.contains("WEALTH_KEYSTONE_LIQUIDATION")) {
                baseMult = baseMult.multiply(BigDecimal.valueOf(10.0));
            }

            // Use BigDecimal power for reward calculation to avoid overflow
            BigDecimal reward;
            if (exponent == 1.0) {
                reward = StorePriceManager.safeBD(maxHealth).multiply(baseMult).multiply(rewardMultiplier);
            } else {
                // BigDecimal.pow only accepts int. For fractional, we use Math.pow and check for Infinity
                double pow = Math.pow(maxHealth, exponent);
                if (Double.isInfinite(pow)) {
                    // Fallback for extreme cases: use BigDecimal power with damped exponent
                    reward = StorePriceManager.safeBD(maxHealth).pow(StorePriceManager.dampedExponent((int)exponent)).multiply(baseMult).multiply(rewardMultiplier);
                } else {
                    reward = BigDecimal.valueOf(pow).multiply(baseMult).multiply(rewardMultiplier);
                }
            }
            
            reward = reward.setScale(0, RoundingMode.HALF_UP);
            
            if (reward.compareTo(BigDecimal.ZERO) > 0) {
                StorePriceManager.addMoney(player.getUUID(), reward);
                player.displayClientMessage(Component.literal("\u00A7aMob Reward: \u00A7e$" + StorePriceManager.formatCurrency(reward) + " \u00A77(Eff: " + (int)(rewardMultiplier.doubleValue() * 100) + "%)"), true);
            }
            
            // 3. Increase Satiety
            float newSatiety = Math.min(100.0f, currentSatiety + 1.0f);
            skillData.mobSatiety.put(mobId, newSatiety);
            
            // 4. Soul Reap (Permanent Stats)
            int soulReap = SkillManager.getActiveRank(skillData, "COMBAT_SOUL_REAP");
            if (soulReap > 0) {
                BigDecimal hpGain = BigDecimal.valueOf(0.033 * soulReap);
                BigDecimal dmgGain = BigDecimal.valueOf(0.016 * soulReap);
                
                skillData.permanentAttributes.put("minecraft:generic.max_health", skillData.permanentAttributes.getOrDefault("minecraft:generic.max_health", BigDecimal.ZERO).add(hpGain));
                skillData.permanentAttributes.put("minecraft:generic.attack_damage", skillData.permanentAttributes.getOrDefault("minecraft:generic.attack_damage", BigDecimal.ZERO).add(dmgGain));
                
                StorePriceManager.applyAllAttributes(player);
            }

            // Legionary Protocol (Combat Keystone)
            if (skillData.activeToggles.contains("COMBAT_KEYSTONE_LEGIONARY")) {
                for (int i = 0; i < 3; i++) {
                    net.minecraft.world.entity.monster.ZombifiedPiglin shadow = net.minecraft.world.entity.EntityType.ZOMBIFIED_PIGLIN.create(player.level());
                    if (shadow != null) {
                        shadow.moveTo(player.position());
                        shadow.setCustomName(Component.literal("System Shadow"));
                        shadow.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, player.getMainHandItem().copy());
                        shadow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                        shadow.setHealth(1000);
                        player.level().addFreshEntity(shadow);
                    }
                }
            }

            // 5. Loot Recalibration
            int lootRank = SkillManager.getActiveRank(skillData, "COMBAT_LOOT_RECALIBRATION");
            if (lootRank > 0) {
                // Open Loot Recalibration GUI
                // Simplified: logic to capture drops and send to client
            }

            StorePriceManager.markDirty(player.getUUID());
            StorePriceManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
            int lootRank = SkillManager.getActiveRank(skillData, "COMBAT_LOOT_RECALIBRATION");
            if (lootRank > 0 && skillData.activeToggles.contains("COMBAT_LOOT_RECALIBRATION")) {
                List<ItemStack> drops = new ArrayList<>();
                event.getDrops().forEach(entityItem -> drops.add(entityItem.getItem().copy()));
                
                // Buffer the loot
                LootBuffer buffer = new LootBuffer(
                    drops, 
                    event.getEntity().getType().getDefaultLootTable(), 
                    event.getEntity().getType(),
                    event.getEntity().position(), 
                    player.getUUID()
                );
                bufferedLoot.put(event.getEntity().getId(), buffer);

                // Clear original drops to prevent them from spawning
                event.getDrops().clear();
                
                com.example.modmenu.network.PacketHandler.sendToPlayer(new com.example.modmenu.network.OpenLootRecalibrationPacket(event.getEntity().getId(), drops, 0), player);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getSide().isClient()) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());

        // Asset Seizure (Wealth Branch)
        int seizureRank = SkillManager.getActiveRank(skillData, "WEALTH_ASSET_SEIZURE");
        if (seizureRank > 0 && player.isShiftKeyDown() && event.getTarget() instanceof LivingEntity victim) {
            long cooldown = skillData.lastCaptureTimes.getOrDefault("SEIZURE_COOLDOWN", 0L);
            if (player.level().getGameTime() - cooldown >= 1200) {
                BigDecimal totalValue = BigDecimal.ZERO;
                
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    ItemStack stack = victim.getItemBySlot(slot);
                    if (!stack.isEmpty()) {
                        totalValue = totalValue.add(StorePriceManager.getBuyPrice(stack.getItem()).multiply(BigDecimal.valueOf(stack.getCount())));
                        victim.setItemSlot(slot, ItemStack.EMPTY);
                    }
                }

                if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                    StorePriceManager.addMoney(player.getUUID(), totalValue);
                    player.displayClientMessage(Component.literal("\u00A76[Asset Seizure] \u00A7aLiquidated gear for \u00A7e$" + StorePriceManager.formatCurrency(totalValue)), true);
                    skillData.lastCaptureTimes.put("SEIZURE_COOLDOWN", player.level().getGameTime());
                    event.setCanceled(true);
                    return;
                }
            } else {
                player.displayClientMessage(Component.literal("\u00A7cAsset Seizure on cooldown!"), true);
            }
        }

        // Quantum Storage (Utility Branch)
        if (SkillManager.getActiveRank(skillData, "UTILITY_QUANTUM_STORAGE") > 0 && player.isShiftKeyDown() && event.getTarget() instanceof net.minecraft.world.entity.vehicle.ContainerEntity container) {
            // For entities like minecart with chest
            player.displayClientMessage(Component.literal("\u00A7b[Quantum Storage] \u00A7aContainer Synced!"), true);
            // logic to store reference
        }
        
        if (settings.captureActive && player.isShiftKeyDown() && event.getTarget() instanceof LivingEntity victim) {
            if (!(victim instanceof net.minecraft.world.entity.player.Player)) {
                // Open Capture GUI
                com.example.modmenu.network.PacketHandler.sendToPlayer(new com.example.modmenu.network.OpenCaptureGuiPacket(victim.getId()), player);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) return;
        
        ServerPlayer player = (ServerPlayer) event.getEntity();
        StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
        
        // Architect's Will (Delete Mode)
        if (skillData.activeToggles.contains("UTILITY_KEYSTONE_ARCHITECT") && player.isShiftKeyDown()) {
            BlockPos pos = event.getPos();
            BlockState state = player.level().getBlockState(pos);
            if (state.getBlock() != Blocks.AIR) {
                BigDecimal value = StorePriceManager.getBuyPrice(state.getBlock().asItem());
                StorePriceManager.addMoney(player.getUUID(), value);
                player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                player.displayClientMessage(Component.literal("\u00A76[Architect's Will] \u00A7aDeleted & Sold for \u00A7e$" + StorePriceManager.formatCurrency(value)), true);
                event.setCanceled(true);
                return;
            }
        }

        // Quantum Storage (Utility Branch)
        if (SkillManager.getActiveRank(skillData, "UTILITY_QUANTUM_STORAGE") > 0 && player.isShiftKeyDown()) {
            BlockPos pos = event.getPos();
            if (player.level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity) {
                player.displayClientMessage(Component.literal("\u00A7b[Quantum Storage] \u00A7aContainer Synced!"), true);
                // logic to store location
                event.setCanceled(true);
                return;
            }
        }

        // Matter Transmutation (Branch C)
        int transRank = SkillManager.getActiveRank(skillData, "UTILITY_MATTER_TRANSMUTATION");
        if (transRank > 0 && player.isShiftKeyDown() && !player.getOffhandItem().isEmpty()) {
            BlockPos pos = event.getPos();
            Block targetBlock = Block.byItem(player.getOffhandItem().getItem());
            if (targetBlock != Blocks.AIR) {
                BigDecimal spCost = BigDecimal.valueOf(100 / transRank);
                if (skillData.totalSP.subtract(skillData.spentSP).compareTo(spCost) >= 0) {
                    skillData.spentSP = skillData.spentSP.add(spCost);
                    player.level().setBlock(pos, targetBlock.defaultBlockState(), 3);
                    player.displayClientMessage(Component.literal("\u00A76[Matter Transmutation] \u00A7aTransmuted! \u00A7dCost: " + spCost + " SP"), true);
                    StorePriceManager.sync(player);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // Tectonic Shift (Branch C)
        int tectonicRank = SkillManager.getActiveRank(skillData, "UTILITY_TECTONIC_SHIFT");
        if (tectonicRank > 0 && player.getMainHandItem().isEmpty() && !player.isShiftKeyDown()) {
            BlockPos pos = event.getPos();
            // 3x3 column lift
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos target = pos.offset(dx, 0, dz);
                    BlockState state = player.level().getBlockState(target);
                    if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
                        BlockPos above = target.above();
                        BlockState aboveState = player.level().getBlockState(above);
                        if (aboveState.isAir() || aboveState.canBeReplaced()) {
                            player.level().setBlock(above, state, 3);
                            player.level().setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
            player.displayClientMessage(Component.literal("\u00A76[Tectonic Shift] \u00A7aEarth Pushed!"), true);
            event.setCanceled(true);
            return;
        }

        // Virtual Containment Capture
        if (settings.captureActive && player.isShiftKeyDown()) {
            net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(event.getPos());
            if (be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container) {
                ResourceLocation lootTable = null;
                try {
                    // Use reflection to access protected lootTable field
                    java.lang.reflect.Field field = net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.class.getDeclaredField("f_59605_"); // SRG name for lootTable if needed, or just "lootTable"
                    field.setAccessible(true);
                    lootTable = (ResourceLocation) field.get(container);
                } catch (Exception e) {
                    try {
                        java.lang.reflect.Field field = net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.class.getDeclaredField("lootTable");
                        field.setAccessible(true);
                        lootTable = (ResourceLocation) field.get(container);
                    } catch (Exception ex) {}
                }

                if (lootTable != null && SkillManager.getActiveRank(skillData, "VIRT_EXCAVATION_LOGIC") > 0) {
                    // Open Capture GUI for Structure Loot
                    com.example.modmenu.network.PacketHandler.sendToPlayer(new com.example.modmenu.network.OpenCaptureGuiPacket(lootTable.toString()), player);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (settings.miningActive && player.isShiftKeyDown()) {
            performMining(player, settings);
            event.setCanceled(true);
        } else if (settings.focusedMiningActive && player.isShiftKeyDown()) {
            performFocusedMining(player, settings, event.getPos());
            event.setCanceled(true);
        }
    }

    private static void performFocusedMining(ServerPlayer player, StorePriceManager.AbilitySettings settings, BlockPos targetPos) {
        ServerLevel level = player.serverLevel();
        BlockState targetState = level.getBlockState(targetPos);
        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
        if (targetState.isAir() || targetState.getBlock() == Blocks.BEDROCK) return;
        if (isMiningBlacklisted(targetState, settings.miningBlacklist)) {
            player.displayClientMessage(Component.literal("\u00A7cBlacklisted Item"), true);
            return;
        }
        
        Block targetBlock = targetState.getBlock();
        BlockPos center = player.blockPosition();
        int radius = settings.focusedMiningRange;
        BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
        
        BigDecimal totalCost = BigDecimal.ZERO;
        List<BlockPos> blocksToMine = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mutablePos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.getBlock() != targetBlock) continue;

                    BigDecimal price = StorePriceManager.getPrice(state.getBlock().asItem());
                    BigDecimal effectivePrice = StorePriceManager.isOre(state.getBlock().asItem()) ? BigDecimal.valueOf(1000) : price;
                    
                    double distanceSq = mutablePos.distSqr(center);
                    double costFactor = 1.0 + (Math.sqrt(distanceSq) / 10.0);
                    BigDecimal cost = effectivePrice.divide(BigDecimal.valueOf(20), 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(costFactor));
                    
                    if (settings.useEnchantments) {
                        int enchantMultiplier = 1;
                        for (int lvl : settings.miningEnchants.values()) {
                            enchantMultiplier += lvl;
                        }
                        cost = cost.multiply(BigDecimal.valueOf(enchantMultiplier));
                    }
                    
                    if (StorePriceManager.canAfford(player.getUUID(), totalCost.add(cost))) {
                        totalCost = totalCost.add(cost);
                        blocksToMine.add(mutablePos.immutable());
                    }
                }
            }
        }
        
        executeMining(player, settings, skillData, blocksToMine, totalCost);
    }

    private static boolean isMiningBlacklisted(BlockState state, List<String> blacklist) {
        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        if (blacklist.contains(blockId)) return true;
        
        for (String entry : blacklist) {
            if (entry.startsWith("tag:")) {
                String tagId = entry.substring(4);
                if (state.getTags().anyMatch(tag -> tag.location().toString().equals(tagId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void performMining(ServerPlayer player, StorePriceManager.AbilitySettings settings) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
        int radius = settings.miningRange;
        BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
        
        BigDecimal totalCost = BigDecimal.ZERO;
        List<BlockPos> blocksToMine = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mutablePos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || state.getBlock() == Blocks.BEDROCK) continue;
                    
                    if (isMiningBlacklisted(state, settings.miningBlacklist)) continue;

                    BigDecimal price = StorePriceManager.getPrice(state.getBlock().asItem());
                    BigDecimal effectivePrice = StorePriceManager.isOre(state.getBlock().asItem()) ? BigDecimal.valueOf(1000) : price;
                    
                    if (effectivePrice.compareTo(BigDecimal.valueOf(10)) > 0) { // Valued block
                        double distanceSq = mutablePos.distSqr(center);
                        double costFactor = 1.0 + (Math.sqrt(distanceSq) / 10.0);
                        BigDecimal cost = effectivePrice.divide(BigDecimal.valueOf(20), 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(costFactor));
                        
                        if (settings.useEnchantments) {
                            int enchantMultiplier = 1;
                            for (int lvl : settings.miningEnchants.values()) {
                                enchantMultiplier += lvl;
                            }
                            cost = cost.multiply(BigDecimal.valueOf(enchantMultiplier));
                        }
                        
                        if (StorePriceManager.canAfford(player.getUUID(), totalCost.add(cost))) {
                            totalCost = totalCost.add(cost);
                            blocksToMine.add(mutablePos.immutable());
                        }
                    }
                }
            }
        }
        
        executeMining(player, settings, skillData, blocksToMine, totalCost);
    }

    private static void executeMining(ServerPlayer player, StorePriceManager.AbilitySettings settings, StorePriceManager.SkillData skillData, List<BlockPos> blocksToMine, BigDecimal totalCost) {
        ServerLevel level = player.serverLevel();
        BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
        
        if (blocksToMine.isEmpty()) {
            player.displayClientMessage(Component.literal("\u00A7cNo suitable blocks found or not enough money!"), true);
            return;
        }

        int minedCount = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<net.minecraft.world.item.Item, BigDecimal> aggregatedDrops = new HashMap<>();
        
        // Use a dummy pickaxe for drops if using enchantments
        ItemStack tool = new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE);
        if (settings.useEnchantments) {
            settings.miningEnchants.forEach((id, lvl) -> {
                net.minecraft.world.item.enchantment.Enchantment enchant = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.tryParse(id));
                if (enchant != null) {
                    tool.enchant(enchant, lvl);
                }
            });
        }

        for (BlockPos pos : blocksToMine) {
            BlockState state = level.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, tool);
            
            // Molecular Replication (Branch C)
            int replicationRank = SkillManager.getActiveRank(skillData, "UTILITY_MOLECULAR_REPLICATION");
            if (replicationRank > 0) {
                BigDecimal multiplier = BigDecimal.ONE;
                if (replicationRank == 1) multiplier = BigDecimal.valueOf(2);
                else if (replicationRank == 2) multiplier = BigDecimal.valueOf(5);
                else if (replicationRank == 3) multiplier = BigDecimal.valueOf(10);
                else if (replicationRank == 4) multiplier = BigDecimal.valueOf(25);
                else if (replicationRank == 5) multiplier = BigDecimal.valueOf(100);
                else if (replicationRank == 6) multiplier = BigDecimal.valueOf(250);
                else if (replicationRank == 7) multiplier = BigDecimal.valueOf(500);
                else if (replicationRank == 8) multiplier = BigDecimal.valueOf(1000);
                else if (replicationRank >= 9) multiplier = BigDecimal.valueOf(10000);
                
                for (ItemStack drop : drops) {
                    BigDecimal count = replicationRank >= 10 ? BigDecimal.valueOf(64) : BigDecimal.valueOf(drop.getCount()).multiply(multiplier);
                    aggregatedDrops.put(drop.getItem(), aggregatedDrops.getOrDefault(drop.getItem(), BigDecimal.ZERO).add(count));
                }
            } else {
                for (ItemStack drop : drops) {
                    aggregatedDrops.put(drop.getItem(), aggregatedDrops.getOrDefault(drop.getItem(), BigDecimal.ZERO).add(BigDecimal.valueOf(drop.getCount())));
                }
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            minedCount++;
        }

        for (Map.Entry<net.minecraft.world.item.Item, BigDecimal> entry : aggregatedDrops.entrySet()) {
            net.minecraft.world.item.Item item = entry.getKey();
            BigDecimal count = entry.getValue();
            
            if (settings.autoSell) {
                BigDecimal sellPrice = StorePriceManager.getSellPrice(item);
                totalValue = totalValue.add(sellPrice.multiply(count));
                StorePriceManager.recordSale(item, count);
            } else {
                BigDecimal remainingCount = count;
                // Handle large stacks by splitting
                while (remainingCount.compareTo(BigDecimal.ZERO) > 0) {
                    int toAddCount = (int) Math.min(remainingCount.longValue(), (long) item.getMaxStackSize(new ItemStack(item)));
                    ItemStack toAdd = new ItemStack(item, toAddCount);
                    if (!player.getInventory().add(toAdd)) {
                        player.drop(toAdd, false);
                    }
                    remainingCount = remainingCount.subtract(BigDecimal.valueOf(toAddCount));
                }
            }
        }
        
        StorePriceManager.setMoney(player.getUUID(), currentMoney.subtract(totalCost).add(totalValue));
        player.displayClientMessage(Component.literal("\u00A7aMined " + minedCount + " blocks. Cost: \u00A7e$" + StorePriceManager.formatCurrency(totalCost) + (settings.autoSell ? " \u00A7aEarned: \u00A7e$" + StorePriceManager.formatCurrency(totalValue) : "")), true);
        StorePriceManager.sync(player);
    }

}
