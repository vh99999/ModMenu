package com.example.modmenu.store;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.common.ForgeHooks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Iterator;

public class SkillManager {
    
    public static BigDecimal getSkillCost(String skillId, int rank, BigDecimal branchMultiplier, UUID playerUuid) {
        // rank is the rank being PURCHASED (currentRank + 1)
        SkillDefinitions.SkillPath path = SkillDefinitions.ALL_SKILLS.get(skillId);
        if (path == null) return BigDecimal.valueOf(50);

        BigDecimal base = BigDecimal.valueOf(50);
        
        // Tiered Pricing based on OPness/Game-breaking nature
        if (skillId.contains("KEYSTONE")) {
            base = BigDecimal.valueOf(10000); // Tier 4
        } else if (skillId.contains("UNLIMIT") || skillId.contains("SINGULARITY") || skillId.contains("OMNIPOTENCE") || skillId.contains("SOVEREIGNTY") || skillId.contains("META_TRADING")) {
            base = BigDecimal.valueOf(1000);  // Tier 3
        } else if (path.maxRank <= 5) {
            base = BigDecimal.valueOf(250);   // Tier 2
        }
        
        BigDecimal cost = base.multiply(BigDecimal.valueOf(rank).pow(3));
        
        // Virtualization Safety Protocol (SP Cost Cap - Removed as per user request)
        // If growth is too fast, user will handle it

        BigDecimal finalCost = cost.multiply(branchMultiplier);
        
        // Portfolio Recursive Growth (Wealth Branch cost reduction)
        StorePriceManager.SkillData data = StorePriceManager.getSkills(playerUuid);
        if (path.branch == SkillDefinitions.Branch.WEALTH) {
            int recursiveRank = getActiveRank(data, "WEALTH_PORTFOLIO_RECURSIVE");
            if (recursiveRank > 0) {
                finalCost = finalCost.multiply(BigDecimal.valueOf(1.0 - 0.01 * recursiveRank));
            }
        }

        // Lobbyist Protocol (Wealth Branch)
        int lobbyistRank = getActiveRank(data, "WEALTH_LOBBYIST");
        if (lobbyistRank > 0) {
            finalCost = finalCost.multiply(BigDecimal.valueOf(1.0 - 0.05 * lobbyistRank));
        }

        return finalCost.setScale(0, RoundingMode.HALF_UP);
    }

    private static int secondTimer = 0;
    private static boolean chronosLockActive = false;
    private static Map<ResourceLocation, ResourceLocation> lootTableCache = new HashMap<>();
    private static Map<net.minecraft.world.item.Item, BigDecimal> itemPriceCache = new HashMap<>();

    private static final Map<ResourceLocation, net.minecraft.world.entity.Entity> dummyEntityCache = new HashMap<>();

    private static class CondensationRecipe {
        final net.minecraft.world.item.Item result;
        final int required;
        final boolean reversible;
        CondensationRecipe(net.minecraft.world.item.Item result, int required, boolean reversible) {
            this.result = result;
            this.required = required;
            this.reversible = reversible;
        }
    }
    private static final Map<net.minecraft.world.item.Item, CondensationRecipe> condensationCache = new HashMap<>();

    public static void clearCaches() {
        lootTableCache.clear();
        itemPriceCache.clear();
        dummyEntityCache.clear();
        condensationCache.clear();
        chunkValueCache.clear();
    }

    public static int getActiveRank(StorePriceManager.SkillData data, String skillId) {
        if (!data.activeToggles.contains(skillId)) return 0;
        return data.skillRanks.getOrDefault(skillId, 0);
    }

    public static void tick(ServerPlayer player) {
        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
        
        // Runs every tick (20 TPS)
        if (skillData.activeToggles.contains("PROTOCOL_CHRONOS_LOCK")) {
            chronosLockActive = true;
        }

        // Update last seen for persistent simulation
        long now = System.currentTimeMillis();
        for (StorePriceManager.ChamberData chamber : skillData.chambers) {
            chamber.lastOfflineProcessingTime = now;
        }
    }

    public static void handleOfflineProcessing(ServerPlayer player) {
        StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
        if (getActiveRank(data, "VIRT_KEYSTONE_PERSISTENCE") <= 0) return;
        
        long now = System.currentTimeMillis();
        int systemOverclock = getActiveRank(data, "UTILITY_SYSTEM_OVERCLOCK");
        int virtClockRank = getActiveRank(data, "VIRT_CLOCK_SPEED");
        long intervalTicks = (long) (1200 * Math.pow(0.8, virtClockRank) / (1 + systemOverclock));
        if (virtClockRank >= 20) intervalTicks = 1;
        if (intervalTicks < 1) intervalTicks = 1;
        
        long intervalMs = intervalTicks * 50; // 1 tick = 50ms
        
        int multiThreadRank = getActiveRank(data, "VIRT_MULTI_THREAD");
        BigDecimal batchSize = BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(multiThreadRank));

        for (StorePriceManager.ChamberData chamber : data.chambers) {
            if (chamber.paused || chamber.lastOfflineProcessingTime <= 0) continue;
            
            long elapsed = now - chamber.lastOfflineProcessingTime;
            long occurrences = elapsed / intervalMs;
            
            if (occurrences > 0) {
                // Limit offline processing to avoid huge spikes on login (e.g., max 7 days)
                long maxOccurrences = (7 * 24 * 3600 * 1000) / intervalMs;
                if (occurrences > maxOccurrences) occurrences = maxOccurrences;
                
                BigDecimal totalKills = BigDecimal.valueOf(occurrences).multiply(batchSize);
                
                // Optimized offline simulation: 
                // Instead of millions of loops, we simulate a representative sample and scale it.
                int sampleSize = (int) Math.min(totalKills.longValue(), 100); 
                if (sampleSize > 0) {
                    BigDecimal scale = totalKills.divide(BigDecimal.valueOf(sampleSize), 10, RoundingMode.HALF_UP);
                    for (int i = 0; i < sampleSize; i++) {
                        simulateMobKillInternal(player, chamber, scale);
                    }
                }
                
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Virtualization] §aOffline Processing: Simulating " + totalKills + " virtual kills."), false);
            }
            chamber.lastOfflineProcessingTime = now;
        }
        StorePriceManager.saveData();
    }

    public static void serverTickSecond() {
        secondTimer++;
        
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null) return;

        if (chronosLockActive) {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            server.overworld().setDayTime(6000); // Fixed at noon
            server.overworld().setWeatherParameters(6000, 0, false, false);
            chronosLockActive = false; // Reset for next second check
        }

        for (ServerPlayer player : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
            UUID uuid = player.getUUID();

            // 1. Satiety Decay
            if (secondTimer % 120 == 0) {
                data.mobSatiety.forEach((id, val) -> {
                    if (val > 0) data.mobSatiety.put(id, Math.max(0, val - 1.0f));
                });
            }

            // 2. Branch A: Wealth
            // CAPITALIST_AURA
            int capitalistRank = getActiveRank(data, "WEALTH_CAPITALIST_AURA");
            if (capitalistRank > 0 && secondTimer % 2 == 0) {
                net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(32.0);
                for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area)) {
                    if (mob.isAlive()) {
                        float dmg = mob.getMaxHealth() * 0.02f * capitalistRank; // 2% to 10% per tick (every 2s)
                        mob.hurt(player.damageSources().magic(), dmg);
                        StorePriceManager.addMoney(uuid, BigDecimal.valueOf(dmg).multiply(BigDecimal.valueOf(100)));
                    }
                }
            }

            // Philanthropy Protocol (formerly WEALTH_ENTANGLEMENT)
            int philanthropyRank = getActiveRank(data, "WEALTH_PHILANTHROPY");
            if (philanthropyRank > 0 && secondTimer % 10 == 0) {
                net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(32.0);
                for (net.minecraft.world.entity.animal.Animal animal : player.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, area)) {
                    if (animal.getAge() < 0) {
                        animal.setAge(animal.getAge() + 100 * philanthropyRank);
                    }
                    if (animal.isInLove()) {
                        // accelerate breeding if possible or just reduce cooldown
                    }
                }
            }

            // Market Speculation (formerly WEALTH_DIVIDEND)
            int speculationRank = getActiveRank(data, "WEALTH_DIVIDEND");
            if (speculationRank > 0 && secondTimer % 600 == 0) {
                // identify targeted resource
                List<net.minecraft.world.item.Item> items = new ArrayList<>(ForgeRegistries.ITEMS.getValues());
                net.minecraft.world.item.Item target = items.get(player.level().random.nextInt(items.size()));
                player.displayClientMessage(Component.literal("§6[Market Speculation] §aTargeted Resource identified: §e" + target.getDescriptionId() + "§a. Sell for 10x payout within 1 min!"), false);
                // logic to actually apply multiplier is in StorePriceManager or packets
            }

            // --- WEALTH BRANCH PASSIVE LOGIC ---
            int basicSavings = getActiveRank(data, "WEALTH_BASIC_SAVINGS");
            int interestRateRank = getActiveRank(data, "WEALTH_INTEREST_RATE");
            int interestCapRank = getActiveRank(data, "WEALTH_INTEREST_CAP");
            boolean unlimit = data.activeToggles.contains("WEALTH_INTEREST_UNLIMIT");

            if (basicSavings > 0 && secondTimer % 30 == 0) {
                BigDecimal currentMoney = StorePriceManager.getMoney(uuid);
                double rate = 0.01 + (0.01 * interestRateRank);
                BigDecimal gain = currentMoney.multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP);
                
                if (!unlimit) {
                    BigDecimal cap = new BigDecimal("100000000").add(BigDecimal.valueOf(interestCapRank).multiply(new BigDecimal("10000000000")));
                    if (gain.compareTo(cap) > 0) gain = cap;
                }
                
                if (gain.compareTo(BigDecimal.ZERO) > 0) StorePriceManager.addMoney(uuid, gain);
            }

            // Portfolio Path
            int portfolioBasic = getActiveRank(data, "WEALTH_PORTFOLIO_BASIC");
            if (portfolioBasic > 0 && secondTimer % 60 == 0) {
                BigDecimal currentMoney = StorePriceManager.getMoney(uuid);
                int yieldRank = getActiveRank(data, "WEALTH_PORTFOLIO_YIELD");
                
                BigDecimal baseDivisor = new BigDecimal("100000000000"); // $100B
                BigDecimal yieldDivisor = new BigDecimal("10000000000");  // $10B
                
                BigDecimal spGain = currentMoney.divide(baseDivisor, 0, RoundingMode.FLOOR);
                if (yieldRank > 0) {
                    spGain = spGain.add(currentMoney.divide(yieldDivisor, 0, RoundingMode.FLOOR).multiply(BigDecimal.valueOf(yieldRank)));
                }
                
                if (spGain.compareTo(BigDecimal.ZERO) > 0) {
                    data.totalSP = data.totalSP.add(spGain);
                }
            }

            // Universal Shareholder (Wealth Keystone)
            int shareholderRank = getActiveRank(data, "WEALTH_KEYSTONE_SHAREHOLDER");
            if (shareholderRank > 0 && secondTimer % 10 == 0) {
                int entityCount = player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, player.getBoundingBox().inflate(256)).size();
                StorePriceManager.addMoney(uuid, BigDecimal.valueOf(entityCount).multiply(BigDecimal.valueOf(1000)));
            }

            // 3. Branch B: Combat
            // System Lockdown (formerly SOVEREIGN_DOMAIN)
            int sovereignRank = getActiveRank(data, "COMBAT_SOVEREIGN_DOMAIN");
            if (sovereignRank > 0 && secondTimer % 2 == 0) {
                double range = 32.0 + (sovereignRank * 8.0);
                double hpLimit = 1000.0 * Math.pow(10, sovereignRank - 1);
                net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(range);
                for (net.minecraft.world.entity.LivingEntity nearby : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area)) {
                    if (nearby != player && nearby.isAlive() && nearby.getMaxHealth() <= hpLimit && !(nearby instanceof net.minecraft.world.entity.player.Player)) {
                        // Stasis effect
                        nearby.setDeltaMovement(0, 0, 0);
                        if (nearby instanceof net.minecraft.world.entity.Mob mob) {
                            mob.setTarget(null);
                        }
                    }
                }
            }

            // Entropy Field (formerly JUDGMENT_AURA)
            int judgmentRank = getActiveRank(data, "COMBAT_JUDGMENT_AURA");
            if (judgmentRank > 0) {
                double range = 16.0 + (judgmentRank * 8.0);
                net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(range);
                for (net.minecraft.world.entity.LivingEntity mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area)) {
                    if (mob != player && !(mob instanceof net.minecraft.world.entity.player.Player)) {
                        // Apply debuffs
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, judgmentRank, false, false));
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 40, judgmentRank, false, false));
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 40, judgmentRank, false, false));
                    }
                }
            }

            // 4. Branch C: Utility
            // BIOMETRIC_OPTIMIZATION
            int bioRank = getActiveRank(data, "UTILITY_BIOMETRIC_OPTIMIZATION");
            if (bioRank > 0) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 400, 0, false, false));
                if (bioRank >= 2) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SPEED, 400, 4, false, false));
                if (bioRank >= 3) {
                    if (!player.getAbilities().mayfly) {
                        player.getAbilities().mayfly = true;
                        player.onUpdateAbilities();
                    }
                }
            }

            // Matter Synthesis (Passive)
            int passiveSynthesisRank = getActiveRank(data, "UTILITY_MATTER_SYNTHESIS_PASSIVE");
            if (passiveSynthesisRank > 0 && secondTimer % 60 == 0) {
                String selectedItem = data.activeToggles.stream().filter(s -> s.startsWith("SYNTH_")).findFirst().orElse(null);
                if (selectedItem != null) {
                    net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(selectedItem.substring(6)));
                    if (item != null) {
                        ItemStack stack = new ItemStack(item, 64);
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                    }
                }
            }

            // Time Dilation Aura
            int dilationRank = getActiveRank(data, "UTILITY_TIME_DILATION_AURA");
            if (dilationRank > 0) {
                net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(16);
                for (net.minecraft.world.entity.LivingEntity entity : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area)) {
                    if (entity != player) {
                        entity.setDeltaMovement(entity.getDeltaMovement().scale(0.1));
                    }
                }
            }

            // SYSTEM_OVERCLOCK
            int overclockRank = getActiveRank(data, "UTILITY_SYSTEM_OVERCLOCK");
            if (overclockRank > 0) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 400, overclockRank - 1, false, false));
            }

            // World Root Access (Utility Keystone)
            if (data.activeToggles.contains("UTILITY_KEYSTONE_ROOT_ACCESS")) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 400, 4, false, false));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 400, 4, false, false));
            }

            // Status Override (Combat Branch)
            if (getActiveRank(data, "COMBAT_STATUS_OVERRIDE") > 0) {
                List<net.minecraft.world.effect.MobEffectInstance> toReplace = new ArrayList<>();
                for (net.minecraft.world.effect.MobEffectInstance effect : player.getActiveEffects()) {
                    if (!effect.getEffect().isBeneficial()) toReplace.add(effect);
                }
                for (net.minecraft.world.effect.MobEffectInstance effect : toReplace) {
                    player.removeEffect(effect.getEffect());
                    net.minecraft.world.effect.MobEffect replacement = net.minecraft.world.effect.MobEffects.REGENERATION;
                    if (effect.getEffect() == net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) replacement = net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED;
                    if (effect.getEffect() == net.minecraft.world.effect.MobEffects.WEAKNESS) replacement = net.minecraft.world.effect.MobEffects.DAMAGE_BOOST;
                    if (effect.getEffect() == net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN) replacement = net.minecraft.world.effect.MobEffects.DIG_SPEED;
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(replacement, effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible()));
                }
            }

            // WEALTH_KEYSTONE: Reality Liquidation
            if (data.activeToggles.contains("WEALTH_KEYSTONE_LIQUIDATION") && secondTimer % 5 == 0) {
                handleRealityLiquidation(player, data);
            }

            // Virtual Chamber Simulation
            handleChamberSimulation(player, data);
        }
        
        if (secondTimer % 60 == 0) {
            chunkValueCache.clear(); // Periodically clear cache to avoid stale values
            itemPriceCache.clear();
            // Cleanup buffered loot (older than 5 min)
            long now = System.currentTimeMillis();
            com.example.modmenu.ServerForgeEvents.bufferedLoot.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 300000);
        }
        if (secondTimer >= 3600) secondTimer = 0;
    }

    private static void handleChamberSimulation(ServerPlayer player, StorePriceManager.SkillData data) {
        if (data.chambers.isEmpty()) return;
        long time = player.level().getGameTime();
        
        int globalOverclock = getActiveRank(data, "UTILITY_SYSTEM_OVERCLOCK");
        int maxClockRank = getActiveRank(data, "VIRT_CLOCK_SPEED");
        int maxThreadRank = getActiveRank(data, "VIRT_MULTI_THREAD");
        
        for (StorePriceManager.ChamberData chamber : data.chambers) {
            if (chamber.paused) continue;
            
            // Use per-chamber sliders, capped by global unlocked rank
            int effectiveClock = Math.min(chamber.speedSlider, maxClockRank);
            int effectiveThread = Math.min(chamber.threadSlider, maxThreadRank);

            long interval = (long) (1200 * Math.pow(0.8, effectiveClock) / (1 + globalOverclock));
            if (effectiveClock >= 20) interval = 1;
            if (interval < 1) interval = 1;
            
            BigDecimal batchSize = BigDecimal.valueOf(2).pow(StorePriceManager.dampedExponent(effectiveThread));

            if (time - chamber.lastHarvestTime >= interval) {
                long elapsed = time - chamber.lastHarvestTime;
                long occurrences = elapsed / interval;
                if (occurrences > 10000) occurrences = 10000;
                
                chamber.lastHarvestTime += occurrences * interval;
                BigDecimal totalKills = batchSize.multiply(BigDecimal.valueOf(occurrences));
                
                int sampleSize = (int) Math.min(totalKills.longValue(), 50);
                if (sampleSize <= 0) continue;
                BigDecimal scale = totalKills.divide(BigDecimal.valueOf(sampleSize), 10, RoundingMode.HALF_UP);
                
                SimulationAccumulator acc = new SimulationAccumulator();
                for (int i = 0; i < sampleSize; i++) {
                    simulateMobKillInternal(player, chamber, scale, acc);
                }
                acc.apply(player, data, chamber);
                
                // Condensation Logic
                if (chamber.condensationMode > 0 && getActiveRank(data, "VIRT_LOOT_CONDENSATION") > 0) {
                    applyLootCondensation(player.serverLevel(), chamber);
                }

                if (chamber.storedLoot.size() > 500) {
                    chamber.storedLoot.sort((a, b) -> {
                        BigDecimal valA = StorePriceManager.getSellPrice(a.getItem()).multiply(BigDecimal.valueOf(a.getCount()));
                        BigDecimal valB = StorePriceManager.getSellPrice(b.getItem()).multiply(BigDecimal.valueOf(b.getCount()));
                        return valA.compareTo(valB);
                    });
                    while (chamber.storedLoot.size() > 500) chamber.storedLoot.remove(0);
                }
                // Stable sort for UI consistency
                chamber.storedLoot.sort((a, b) -> ForgeRegistries.ITEMS.getKey(a.getItem()).toString().compareTo(ForgeRegistries.ITEMS.getKey(b.getItem()).toString()));
                chamber.updateVersion++;
            }
        }
    }

    public static class SimulationAccumulator {
        public BigDecimal moneyGain = BigDecimal.ZERO;
        public BigDecimal xpGain = BigDecimal.ZERO;
        public BigDecimal totalKills = BigDecimal.ZERO;
        public BigDecimal hpGain = BigDecimal.ZERO;
        public BigDecimal dmgGain = BigDecimal.ZERO;

        public void apply(ServerPlayer player, StorePriceManager.SkillData data, StorePriceManager.ChamberData chamber) {
            if (moneyGain.compareTo(BigDecimal.ZERO) > 0) {
                StorePriceManager.addMoney(player.getUUID(), moneyGain);
            }
            if (totalKills.compareTo(BigDecimal.ZERO) > 0) {
                data.totalKills = data.totalKills.add(totalKills);
            }
            if (hpGain.compareTo(BigDecimal.ZERO) > 0) {
                data.permanentAttributes.put("minecraft:generic.max_health", data.permanentAttributes.getOrDefault("minecraft:generic.max_health", BigDecimal.ZERO).add(hpGain));
            }
            if (dmgGain.compareTo(BigDecimal.ZERO) > 0) {
                data.permanentAttributes.put("minecraft:generic.attack_damage", data.permanentAttributes.getOrDefault("minecraft:generic.attack_damage", BigDecimal.ZERO).add(dmgGain));
            }
            
            // Handle XP and SP conversion
            int neuralRank = getActiveRank(data, "VIRT_NEURAL_CONDENSATION");
            if (neuralRank > 0) {
                BigDecimal spCost = BigDecimal.valueOf(10000 / neuralRank);
                chamber.storedXP = chamber.storedXP.add(xpGain);
                if (chamber.storedXP.compareTo(spCost) >= 0) {
                    BigDecimal spToGain = chamber.storedXP.divide(spCost, 0, RoundingMode.FLOOR);
                    data.totalSP = data.totalSP.add(spToGain);
                    chamber.storedXP = chamber.storedXP.remainder(spCost);
                }
            } else {
                chamber.storedXP = chamber.storedXP.add(xpGain);
            }
            
            StorePriceManager.sync(player);
        }
    }

    public static void simulateMobKillInternal(ServerPlayer player, StorePriceManager.ChamberData chamber, BigDecimal scale) {
        simulateMobKillInternal(player, chamber, scale, null);
    }

    public static void simulateMobKillInternal(ServerPlayer player, StorePriceManager.ChamberData chamber, BigDecimal scale, SimulationAccumulator acc) {
        ServerLevel level = player.serverLevel();
        StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
        
        if (acc != null) acc.totalKills = acc.totalKills.add(scale);
        else data.totalKills = data.totalKills.add(scale);

        ResourceLocation lootTableLoc = null;
        net.minecraft.world.entity.EntityType<?> type = null;

        if (chamber.isExcavation) {
            if (chamber.lootTableId != null) {
                lootTableLoc = ResourceLocation.tryParse(chamber.lootTableId);
            }
        } else {
            ResourceLocation entityTypeLoc = ResourceLocation.tryParse(chamber.mobId);
            if (entityTypeLoc == null) return;
            
            type = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeLoc);
            if (type == null) return;

            lootTableLoc = lootTableCache.computeIfAbsent(entityTypeLoc, k -> {
                net.minecraft.world.entity.Entity dummyTable = ForgeRegistries.ENTITY_TYPES.getValue(k).create(level);
                if (dummyTable instanceof net.minecraft.world.entity.LivingEntity living) {
                    return living.getLootTable();
                }
                return null;
            });
        }
        
        if (lootTableLoc == null) return;

        ItemStack tool = chamber.killerWeapon != null && !chamber.killerWeapon.isEmpty() ? chamber.killerWeapon.copy() : ItemStack.EMPTY;
        
        net.minecraft.world.entity.Entity dummy = null;
        if (!chamber.isExcavation && type != null) {
            ResourceLocation typeKey = ForgeRegistries.ENTITY_TYPES.getKey(type);
            dummy = dummyEntityCache.computeIfAbsent(typeKey, k -> {
                net.minecraft.world.entity.EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(k);
                return et != null ? et.create(level) : null;
            });
            if (dummy != null && chamber.isExact && chamber.nbt != null) {
                dummy.load(chamber.nbt);
            }
        }

        // Virtual Bartering
        boolean isBartering = false;
        if (chamber.barteringMode && !chamber.isExcavation && type != null && type.toShortString().contains("piglin") && getActiveRank(data, "VIRT_BARTERING_PROTOCOL") > 0) {
            // Check for Gold Ingot in input buffer
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> barteringTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.tryParse("minecraft:piglin_bartering_items"));
            ItemStack goldStack = chamber.inputBuffer.stream()
                .filter(s -> s.is(barteringTag))
                .findFirst().orElse(null);
            
            if (goldStack != null && goldStack.getCount() > 0) {
                int toConsume = Math.min(goldStack.getCount(), scale.intValue());
                if (toConsume <= 0) toConsume = 1;
                
                goldStack.shrink(toConsume);
                if (toConsume < scale.intValue()) {
                    scale = BigDecimal.valueOf(toConsume);
                }
                
                chamber.inputBuffer.removeIf(ItemStack::isEmpty);
                lootTableLoc = ResourceLocation.tryParse("minecraft:gameplay/piglin_bartering");
                isBartering = true;
            } else {
                return; // No gold, no bartering simulation
            }
        }

        int recursiveLooting = getActiveRank(data, "VIRT_RECURSIVE_LOOTING");
        if (recursiveLooting > 0 && !isBartering) {
            if (tool.isEmpty()) tool = new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
            tool.enchant(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, recursiveLooting);
        }
        
        LootParams.Builder lootParamsBuilder = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, player.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
            .withParameter(LootContextParams.KILLER_ENTITY, player)
            .withParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
            .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
            .withParameter(LootContextParams.TOOL, tool);

        if (dummy != null) {
            lootParamsBuilder.withParameter(LootContextParams.THIS_ENTITY, dummy);
        } else if (!chamber.isExcavation && !isBartering) {
            return; // Cannot generate ENTITY-set loot without THIS_ENTITY
        }

        if (getActiveRank(data, "VIRT_LOOT_INJECTION") > 0) {
            lootParamsBuilder.withLuck(getActiveRank(data, "VIRT_LOOT_INJECTION") * 5.0f);
        }

        net.minecraft.world.level.storage.loot.LootTable lootTable = level.getServer().getLootData().getLootTable(lootTableLoc);
        List<ItemStack> drops = lootTable.getRandomItems(lootParamsBuilder.create(chamber.isExcavation ? net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST : LootContextParamSets.ENTITY));

        if (dummy instanceof net.minecraft.world.entity.LivingEntity living && !isBartering) {
            net.minecraft.world.damagesource.DamageSource src = level.damageSources().playerAttack(player);
            
            // Set entity state for modded listeners
            try {
                java.lang.reflect.Field f1 = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("lastHurtByPlayer");
                f1.setAccessible(true);
                f1.set(living, player);
                
                java.lang.reflect.Field f2 = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("lastHurtByPlayerTime");
                f2.setAccessible(true);
                f2.set(living, 100);
            } catch (Exception e) {
                // Fallback
            }
            
            living.setHealth(0); // Mark as dead
            living.setPos(player.getX(), player.getY(), player.getZ());

            // Firing LivingDeathEvent: Used by mods to initialize loot modifiers
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(living, src));

            // Determine looting level via event (respects modded looting modifiers)
            int lootingLevel = ForgeHooks.getLootingLevel(living, player, src);

            // Add physical equipment drops (Armor/Held items)
            if (living instanceof net.minecraft.world.entity.Mob mob) {
                addEquipmentDrops(mob, drops, level.random);
            }

            // Wrap drops into ItemEntities for the Drops Event
            java.util.Collection<ItemEntity> itemEntities = new java.util.ArrayList<>();
            for (ItemStack stack : drops) {
                if (!stack.isEmpty()) {
                    itemEntities.add(new ItemEntity(level, living.getX(), living.getY(), living.getZ(), stack));
                }
            }

            // Firing LivingDropsEvent: THIS is where most mods (Apotheosis, Draconic Evolution, etc.) inject loot
            LivingDropsEvent dropsEvent = new LivingDropsEvent(living, src, itemEntities, lootingLevel, true);
            MinecraftForge.EVENT_BUS.post(dropsEvent);

            // Re-collect the final list of drops
            drops.clear();
            for (ItemEntity ie : dropsEvent.getDrops()) {
                if (ie != null && !ie.getItem().isEmpty()) {
                    drops.add(ie.getItem().copy());
                }
            }

            // Experience Pipeline
            int baseXP = living.getExperienceReward();
            LivingExperienceDropEvent xpEvent = new LivingExperienceDropEvent(living, player, baseXP);
            MinecraftForge.EVENT_BUS.post(xpEvent);
            
            processSimulatedXP(player, data, chamber, xpEvent.getDroppedExperience(), scale, acc);

            if (getActiveRank(data, "VIRT_ISOLATED_SANDBOX") <= 0) {
                float currentSatiety = data.mobSatiety.getOrDefault(chamber.mobId, 0f);
                data.mobSatiety.put(chamber.mobId, (float) Math.min(100.0f, currentSatiety + (0.1f * scale.floatValue()))); 
            }

            int soulReap = getActiveRank(data, "COMBAT_SOUL_REAP");
            if (soulReap > 0) {
                BigDecimal hpGainBD = BigDecimal.valueOf(0.033 * soulReap).multiply(scale);
                BigDecimal dmgGainBD = BigDecimal.valueOf(0.016 * soulReap).multiply(scale);
                if (acc != null) {
                    acc.hpGain = acc.hpGain.add(hpGainBD);
                    acc.dmgGain = acc.dmgGain.add(dmgGainBD);
                } else {
                    data.permanentAttributes.put("minecraft:generic.max_health", data.permanentAttributes.getOrDefault("minecraft:generic.max_health", BigDecimal.ZERO).add(hpGainBD));
                    data.permanentAttributes.put("minecraft:generic.attack_damage", data.permanentAttributes.getOrDefault("minecraft:generic.attack_damage", BigDecimal.ZERO).add(dmgGainBD));
                }
            }
        }
        
        // Advanced Filtering
        if (getActiveRank(data, "VIRT_ADVANCED_FILTERING") > 0 && !chamber.advancedFilters.isEmpty()) {
            applyAdvancedFilters(chamber, drops, acc, player.getUUID());
        } else if (!chamber.voidFilter.isEmpty()) {
            drops.removeIf(stack -> chamber.voidFilter.contains(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()));
        }

        int marketLink = getActiveRank(data, "VIRT_MARKET_LINK");
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            
            String itemId = ForgeRegistries.ITEMS.getKey(drop.getItem()).toString();
            
            // Yield Targets
            if (chamber.yieldTargets.containsKey(itemId)) {
                int target = chamber.yieldTargets.get(itemId);
                int current = chamber.storedLoot.stream()
                    .filter(s -> ForgeRegistries.ITEMS.getKey(s.getItem()).toString().equals(itemId))
                    .mapToInt(ItemStack::getCount).sum();
                if (current >= target) continue;
            }

            BigDecimal countBD = BigDecimal.valueOf(drop.getCount()).multiply(scale);
            if (countBD.compareTo(BigDecimal.ZERO) <= 0) continue;
            
            if (marketLink > 0) {
                BigDecimal sellPrice = StorePriceManager.getSellPrice(drop.getItem(), player.getUUID());
                if (data.activeToggles.contains("WEALTH_KEYSTONE_MONOPOLY")) {
                    sellPrice = sellPrice.multiply(BigDecimal.valueOf(1.01));
                }
                BigDecimal gain = sellPrice.multiply(countBD);
                if (acc != null) acc.moneyGain = acc.moneyGain.add(gain);
                else StorePriceManager.addMoney(player.getUUID(), gain);
                
                StorePriceManager.recordSale(drop.getItem(), countBD);
            } else {
                boolean merged = false;
                for (ItemStack existing : chamber.storedLoot) {
                    if (ItemStack.isSameItemSameTags(existing, drop)) {
                        BigDecimal totalCountBD = BigDecimal.valueOf(existing.getCount()).add(countBD);
                        int newCount = totalCountBD.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0 ? Integer.MAX_VALUE : Math.max(1, totalCountBD.intValue());
                        existing.setCount(newCount);
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    ItemStack newStack = drop.copy();
                    int newCount = countBD.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0 ? Integer.MAX_VALUE : Math.max(1, countBD.intValue());
                    newStack.setCount(newCount);
                    if (!newStack.isEmpty()) {
                        chamber.storedLoot.add(newStack);
                    }
                }
            }
        }
        

        if (acc == null) {
            chamber.updateVersion++;
        }
    }

    private static void processSimulatedXP(ServerPlayer player, StorePriceManager.SkillData data, StorePriceManager.ChamberData chamber, int xpGainAmount, BigDecimal scale, SimulationAccumulator acc) {
        BigDecimal xpGain = BigDecimal.valueOf(xpGainAmount).multiply(scale);
        if (acc != null) acc.xpGain = acc.xpGain.add(xpGain);
        else {
            int neuralRank = getActiveRank(data, "VIRT_NEURAL_CONDENSATION");
            if (neuralRank > 0) {
                BigDecimal spCost = BigDecimal.valueOf(10000 / neuralRank);
                chamber.storedXP = chamber.storedXP.add(xpGain);
                if (chamber.storedXP.compareTo(spCost) >= 0) {
                    data.totalSP = data.totalSP.add(chamber.storedXP.divide(spCost, 0, RoundingMode.FLOOR));
                    chamber.storedXP = chamber.storedXP.remainder(spCost);
                }
            } else {
                chamber.storedXP = chamber.storedXP.add(xpGain);
            }
        }
    }

    private static void addEquipmentDrops(net.minecraft.world.entity.Mob mob, List<ItemStack> drops, net.minecraft.util.RandomSource random) {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                float chance = 0.085f;
                try {
                    java.lang.reflect.Method m = net.minecraft.world.entity.Mob.class.getDeclaredMethod("getEquipmentDropChance", net.minecraft.world.entity.EquipmentSlot.class);
                    m.setAccessible(true);
                    chance = (float) m.invoke(mob, slot);
                } catch (Exception e) {
                    // Fallback
                }
                if (random.nextFloat() < chance) {
                    drops.add(stack.copy());
                }
            }
        }
    }

    private static void applyAdvancedFilters(StorePriceManager.ChamberData chamber, List<ItemStack> drops, SimulationAccumulator acc, UUID playerUuid) {
        Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack.isEmpty()) continue;

            for (StorePriceManager.FilterRule rule : chamber.advancedFilters) {
                boolean match = false;
                switch (rule.matchType) {
                    case "ID" -> match = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(rule.matchValue);
                    case "TAG" -> match = stack.getTags().anyMatch(t -> t.location().toString().equals(rule.matchValue));
                    case "NBT" -> {
                        boolean idMatch = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(rule.matchValue);
                        boolean nbtMatch = rule.nbtSample != null && net.minecraft.nbt.NbtUtils.compareNbt(rule.nbtSample, stack.getTag(), true);
                        match = idMatch && nbtMatch;
                    }
                }

                if (match) {
                    if (rule.action == 1) { // VOID
                        it.remove();
                    } else if (rule.action == 2) { // LIQUIDATE
                        BigDecimal sellPrice = StorePriceManager.getSellPrice(stack.getItem(), playerUuid);
                        BigDecimal gain = sellPrice.multiply(BigDecimal.valueOf(stack.getCount()));
                        if (acc != null) acc.moneyGain = acc.moneyGain.add(gain);
                        else StorePriceManager.addMoney(playerUuid, gain);
                        StorePriceManager.recordSale(stack.getItem(), BigDecimal.valueOf(stack.getCount()));
                        it.remove();
                    }
                    break; // Rule applied
                }
            }
        }
    }

    private static void applyLootCondensation(ServerLevel level, StorePriceManager.ChamberData chamber) {
        // Try to condense items in storedLoot using cached recipes
        boolean changed = false;
        List<ItemStack> loot = chamber.storedLoot;
        
        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            if (stack.isEmpty()) continue;

            CondensationRecipe recipe = condensationCache.computeIfAbsent(stack.getItem(), item -> {
                net.minecraft.world.item.crafting.RecipeManager rm = level.getRecipeManager();
                for (net.minecraft.world.item.crafting.CraftingRecipe r : rm.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
                    if (r.getIngredients().size() == 9 || r.getIngredients().size() == 4) {
                        boolean allSame = true;
                        for (net.minecraft.world.item.crafting.Ingredient ing : r.getIngredients()) {
                            if (!ing.test(new ItemStack(item))) { allSame = false; break; }
                        }
                        
                        if (allSame) {
                            int req = r.getIngredients().size();
                            ItemStack res = r.getResultItem(level.registryAccess());
                            if (!res.isEmpty()) {
                                boolean rev = false;
                                for (net.minecraft.world.item.crafting.CraftingRecipe deR : rm.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
                                    if (deR.getIngredients().size() == 1 && deR.getIngredients().get(0).test(res)) {
                                        ItemStack deRes = deR.getResultItem(level.registryAccess());
                                        if (deRes.is(item) && deRes.getCount() == req) {
                                            rev = true;
                                            break;
                                        }
                                    }
                                }
                                return new CondensationRecipe(res.getItem(), req, rev);
                            }
                        }
                    }
                }
                return null;
            });

            if (recipe != null) {
                if (chamber.condensationMode == 1 && !recipe.reversible) continue;
                
                if (stack.getCount() >= recipe.required) {
                    int craftCount = stack.getCount() / recipe.required;
                    stack.shrink(craftCount * recipe.required);
                    ItemStack result = new ItemStack(recipe.result, craftCount);
                    
                    boolean merged = false;
                    for (ItemStack existing : loot) {
                        if (ItemStack.isSameItemSameTags(existing, result)) {
                            long newCount = (long)existing.getCount() + result.getCount();
                            existing.setCount(newCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)newCount);
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) loot.add(result);
                    changed = true;
                }
            }
        }
        
        if (changed) {
            loot.removeIf(ItemStack::isEmpty);
            chamber.updateVersion++;
        }
    }

    private static void handleRealityLiquidation(ServerPlayer player, StorePriceManager.SkillData data) {
        // Scan chunks in 128 block radius (8 chunks)
        // Optimization: Use chunk-based value caching
        BigDecimal totalValue = BigDecimal.ZERO;
        int radiusChunks = 8;
        int px = player.getBlockX() >> 4;
        int pz = player.getBlockZ() >> 4;
        
        for (int x = -radiusChunks; x <= radiusChunks; x++) {
            for (int z = -radiusChunks; z <= radiusChunks; z++) {
                totalValue = totalValue.add(getChunkValue(player.serverLevel(), px + x, pz + z, player.getUUID()));
            }
        }
        
        // 25% value spread over 1 minute (60s), called every 5s -> divide by 12
        BigDecimal gain = totalValue.multiply(BigDecimal.valueOf(0.25)).divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP);
        
        if (gain.compareTo(BigDecimal.ZERO) > 0) {
            StorePriceManager.addMoney(player.getUUID(), gain);
        }
    }

    private static Map<Long, BigDecimal> chunkValueCache = new HashMap<>();
    private static BigDecimal getChunkValue(net.minecraft.server.level.ServerLevel level, int cx, int cz, UUID playerUuid) {
        long key = ((long)cx << 32) | (cz & 0xFFFFFFFFL);
        // Clear cache periodically (once every minute) handled in tick
        if (chunkValueCache.containsKey(key)) return chunkValueCache.get(key);
        
        // Optimized scan: Subsample blocks to estimate chunk value
        BigDecimal value = BigDecimal.ZERO;
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
        
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += 16) {
            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    net.minecraft.world.item.Item item = chunk.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).getBlock().asItem();
                    if (item != net.minecraft.world.item.Items.AIR) {
                        BigDecimal price = StorePriceManager.getSellPrice(item, playerUuid);
                        value = value.add(price.multiply(BigDecimal.valueOf(256)));
                    }
                }
            }
        }
        chunkValueCache.put(key, value);
        return value;
    }

    public static void applyAllPermanentAttributes(ServerPlayer player) {
        StorePriceManager.SkillData skillData = StorePriceManager.getSkills(player.getUUID());
        skillData.permanentAttributes.forEach((attrId, bonus) -> {
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(attrId));
            if (attr != null) {
                AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) {
                    UUID uuid = UUID.nameUUIDFromBytes(("modmenu_perm_" + attrId).getBytes());
                    inst.removeModifier(uuid);
                    if (bonus.compareTo(BigDecimal.ZERO) > 0) {
                        inst.addPermanentModifier(new AttributeModifier(uuid, "Skill Tree Permanent", bonus.doubleValue(), AttributeModifier.Operation.ADDITION));
                    }
                }
            }
        });
    }
}
