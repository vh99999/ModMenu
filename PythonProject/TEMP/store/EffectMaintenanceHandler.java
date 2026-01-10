package com.example.modmenu.store;

import com.example.modmenu.modmenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import net.minecraftforge.event.level.LevelEvent;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = "modmenu")
public class EffectMaintenanceHandler {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer player : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                StorePriceManager.AbilitySettings abilitySettings = StorePriceManager.getAbilities(uuid);
                
                if (abilitySettings.itemMagnetActive) {
                    handleItemMagnet(player, abilitySettings);
                }
                if (abilitySettings.xpMagnetActive) {
                    handleXpMagnet(player, abilitySettings);
                }
                if (abilitySettings.growCropsActive) {
                    handleGrowCrops(player, abilitySettings);
                }
                if (abilitySettings.spawnBoostActive) {
                    handleSpawnBoostManual(player, abilitySettings);
                }
            }
        }

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            tick();
        }
    }

    private static void handleSpawnBoostManual(ServerPlayer player, StorePriceManager.AbilitySettings settings) {
        if (settings.spawnBoostMultiplier <= 1.0 || settings.spawnBoostTargets.isEmpty()) return;
        
        ServerLevel level = player.serverLevel();
        double multiplier = settings.spawnBoostMultiplier;
        int attempts = (int) (multiplier / 20.0);
        if (level.random.nextDouble() * 20.0 < (multiplier % 20.0)) {
            attempts++;
        }
        
        if (attempts <= 0) return;
        
        long costPerAttempt = StorePriceManager.formulas.spawnBoostPerSpawnBase / 20; 
        
        for (int i = 0; i < attempts; i++) {
            String targetId = settings.spawnBoostTargets.get(level.random.nextInt(settings.spawnBoostTargets.size()));
            if (targetId.startsWith("mod:")) continue; 

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(targetId));
            if (type != null) {
                double range = 32.0;
                double px = player.getX() + (level.random.nextDouble() * range * 2) - range;
                double pz = player.getZ() + (level.random.nextDouble() * range * 2) - range;
                double py = player.getY();
                
                if (settings.disabledSpawnConditions.contains("SURFACE")) {
                    py = player.getY() + (level.random.nextDouble() * 32.0) - 16.0;
                }
                
                BlockPos spawnPos = new BlockPos((int)px, (int)py, (int)pz);
                
                if (!settings.disabledSpawnConditions.contains("SURFACE")) {
                    spawnPos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawnPos);
                }
                
                boolean canSpawn = settings.disabledSpawnConditions.contains("SPACE") || 
                                  level.noCollision(type.getAABB(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));
                
                if (canSpawn) {
                    if (StorePriceManager.getMoney(player.getUUID()) >= costPerAttempt) {
                        net.minecraft.world.entity.Entity entity = type.create(level);
                        if (entity != null) {
                            entity.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360F, 0);
                            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                                if (settings.disabledSpawnConditions.contains("LIGHT")) {
                                    // By default finalizeSpawn might check light, we can't easily change internal check 
                                    // but we can just skip it if we want.
                                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
                                } else {
                                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
                                }
                            }
                            level.addFreshEntity(entity);
                            StorePriceManager.addMoney(player.getUUID(), -costPerAttempt);
                        }
                    }
                }
            }
        }
    }

    private static void tick() {
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null) return;
        
        for (ServerPlayer player : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            Map<String, Integer> active = StorePriceManager.getActiveEffects(uuid);
            StorePriceManager.AbilitySettings abilitySettings = StorePriceManager.getAbilities(uuid);
            
            if (active.isEmpty() && !abilitySettings.chestHighlightActive && !abilitySettings.trapHighlightActive && !abilitySettings.entityESPActive && !abilitySettings.damageCancelActive && !abilitySettings.itemMagnetActive && !abilitySettings.xpMagnetActive && !abilitySettings.repairActive && !abilitySettings.autoSellerActive && !abilitySettings.flightActive && !abilitySettings.noAggroActive && !abilitySettings.spawnBoostActive && !abilitySettings.growCropsActive) {
                if (!player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                continue;
            }

            long totalCost = StorePriceManager.getDrain(uuid);
            long money = StorePriceManager.getMoney(uuid);

            if (money >= totalCost) {
                StorePriceManager.addMoney(uuid, -totalCost);
                applyEffects(player, active);
                
                if (abilitySettings.repairActive) {
                    handleRepair(player);
                }

                if (abilitySettings.flightActive) {
                    if (!player.getAbilities().mayfly) {
                        player.getAbilities().mayfly = true;
                        player.onUpdateAbilities();
                    }
                } else if (!player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                
                StorePriceManager.sync(player);
            } else {
                active.clear();
                abilitySettings.chestHighlightActive = false;
                abilitySettings.trapHighlightActive = false;
                abilitySettings.entityESPActive = false;
                abilitySettings.damageCancelActive = false;
                abilitySettings.repairActive = false;
                abilitySettings.itemMagnetActive = false;
                abilitySettings.xpMagnetActive = false;
                abilitySettings.autoSellerActive = false;
                abilitySettings.flightActive = false;
                abilitySettings.noAggroActive = false;
                abilitySettings.spawnBoostActive = false;
                abilitySettings.growCropsActive = false;

                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }

                StorePriceManager.save();
                StorePriceManager.sync(player);
            }
        }
    }

    private static void handleRepair(ServerPlayer player) {
        int repairCostPerPoint = StorePriceManager.formulas.repairCostPerPoint;
        long currentMoney = StorePriceManager.getMoney(player.getUUID());
        long totalRepaired = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.isDamaged() && stack.getItem().canBeDepleted()) {
                int damage = stack.getDamageValue();
                long cost = (long) damage * repairCostPerPoint;
                
                if (currentMoney >= cost) {
                    stack.setDamageValue(0);
                    currentMoney -= cost;
                    totalRepaired += damage;
                } else {
                    int pointsToRepair = (int) (currentMoney / repairCostPerPoint);
                    if (pointsToRepair > 0) {
                        stack.setDamageValue(damage - pointsToRepair);
                        currentMoney -= (long) pointsToRepair * repairCostPerPoint;
                        totalRepaired += pointsToRepair;
                    }
                }
            }
            if (currentMoney <= 0) break;
        }

        if (totalRepaired > 0) {
            StorePriceManager.setMoney(player.getUUID(), currentMoney);
        }
    }

    private static void handleItemMagnet(ServerPlayer player, StorePriceManager.AbilitySettings settings) {
        net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(settings.itemMagnetRange);
        java.util.List<net.minecraft.world.entity.item.ItemEntity> items = player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
        
        for (net.minecraft.world.entity.item.ItemEntity itemEntity : items) {
            if (!itemEntity.isAlive()) continue;
            
            ItemStack stack = itemEntity.getItem();
            String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();

            boolean shouldSell = settings.autoSellerWhitelist.contains(id);
            if (settings.autoSellerIsBlacklist) shouldSell = !shouldSell;

            if (settings.autoSellerActive && shouldSell) {
                int price = StorePriceManager.getSellPrice(stack.getItem());
                if (price > 0) {
                    long totalGain = (long) price * stack.getCount();
                    StorePriceManager.addMoney(player.getUUID(), totalGain);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[AutoSeller] §aSold " + stack.getCount() + "x " + stack.getHoverName().getString() + " for §e$" + StorePriceManager.formatCurrency(totalGain)), true);
                    itemEntity.discard();
                    continue;
                }
            }
            
            if (player.getInventory().add(stack)) {
                if (stack.isEmpty()) {
                    itemEntity.discard();
                }
            } else {
                itemEntity.setPos(player.getX(), player.getY(), player.getZ());
                itemEntity.setNoPickUpDelay();
            }
        }
    }

    private static void handleXpMagnet(ServerPlayer player, StorePriceManager.AbilitySettings settings) {
        net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(settings.xpMagnetRange);
        java.util.List<net.minecraft.world.entity.ExperienceOrb> orbs = player.level().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area);
        
        for (net.minecraft.world.entity.ExperienceOrb orb : orbs) {
            if (!orb.isAlive()) continue;
            
            player.giveExperiencePoints(orb.getValue());
            orb.discard();
        }
    }

    private static void handleGrowCrops(ServerPlayer player, StorePriceManager.AbilitySettings settings) {
        int range = settings.growCropsRange;
        long opCost = StorePriceManager.formulas.growCropsPerOperation;
        
        BlockPos pos = player.blockPosition();
        ServerLevel level = (ServerLevel) player.level();
        
        int grown = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-range, -2, -range), pos.offset(range, 2, range))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof BonemealableBlock growable) {
                if (growable.isValidBonemealTarget(level, p, state, false)) {
                    if (StorePriceManager.getMoney(player.getUUID()) >= opCost) {
                        growable.performBonemeal(level, level.random, p, state);
                        StorePriceManager.addMoney(player.getUUID(), -opCost);
                        grown++;
                    }
                }
            } else if (state.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE) || state.is(net.minecraft.world.level.block.Blocks.CACTUS)) {
                BlockPos topPos = p.above();
                if (level.isEmptyBlock(topPos)) {
                    int height = 1;
                    BlockPos below = p.below();
                    while (level.getBlockState(below).is(state.getBlock())) {
                        height++;
                        below = below.below();
                    }
                    if (height < 3) {
                        if (StorePriceManager.getMoney(player.getUUID()) >= opCost) {
                            level.setBlockAndUpdate(topPos, state.getBlock().defaultBlockState());
                            StorePriceManager.addMoney(player.getUUID(), -opCost);
                            grown++;
                        }
                    }
                }
            } else if (state.is(net.minecraft.world.level.block.Blocks.NETHER_WART)) {
                int age = state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE);
                if (age < 3) {
                    if (StorePriceManager.getMoney(player.getUUID()) >= opCost) {
                        level.setBlockAndUpdate(p, state.setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, age + 1));
                        StorePriceManager.addMoney(player.getUUID(), -opCost);
                        grown++;
                    }
                }
            } else if (state.is(net.minecraft.world.level.block.Blocks.CHORUS_FLOWER)) {
                int age = state.getValue(net.minecraft.world.level.block.ChorusFlowerBlock.AGE);
                if (age < 5) {
                    if (StorePriceManager.getMoney(player.getUUID()) >= opCost) {
                        // Chorus flower growth is more complex than just setting age, but this is a start
                        level.setBlockAndUpdate(p, state.setValue(net.minecraft.world.level.block.ChorusFlowerBlock.AGE, age + 1));
                        StorePriceManager.addMoney(player.getUUID(), -opCost);
                        grown++;
                    }
                }
            }
            if (grown > 5) break; 
        }
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        
        BlockPos pos = event.getPos();
        List<MobSpawnSettings.SpawnerData> list = event.getSpawnerDataList();
        
        // Check if list is mutable, if not try to replace with mutable one via reflection
        try {
            if (list.getClass().getName().contains("Immutable") || list.getClass().getName().contains("Unmodifiable")) {
                 throw new UnsupportedOperationException();
            }
        } catch (UnsupportedOperationException e) {
            try {
                Field field = LevelEvent.PotentialSpawns.class.getDeclaredField("list");
                field.setAccessible(true);
                list = new ArrayList<>(list);
                field.set(event, list);
            } catch (Exception ex) {
                return; // Cannot modify, skip to avoid crash
            }
        }

        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) < 64*64) {
                StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
                if (settings.spawnBoostActive && !settings.spawnBoostTargets.isEmpty()) {
                    double multiplier = settings.spawnBoostMultiplier;
                    
                    // Boost existing entries
                    for (int i = 0; i < list.size(); i++) {
                        MobSpawnSettings.SpawnerData data = list.get(i);
                        String id = ForgeRegistries.ENTITY_TYPES.getKey(data.type).toString();
                        String modId = ForgeRegistries.ENTITY_TYPES.getKey(data.type).getNamespace();
                        
                        if (settings.spawnBoostTargets.contains(id) || settings.spawnBoostTargets.contains("mod:" + modId)) {
                            int newWeight = (int) (data.getWeight().asInt() * multiplier);
                            list.set(i, new MobSpawnSettings.SpawnerData(data.type, Math.max(1, newWeight), data.minCount, data.maxCount));
                        }
                    }
                    
                    // Add missing target entries if they are of the correct category or if BIOME check is disabled
                    for (String targetId : settings.spawnBoostTargets) {
                        if (targetId.startsWith("mod:")) continue;
                        
                        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(targetId));
                        if (type != null) {
                            boolean ignoreBiome = settings.disabledSpawnConditions.contains("BIOME");
                            if (ignoreBiome || type.getCategory() == event.getMobCategory()) {
                                boolean found = false;
                                for (MobSpawnSettings.SpawnerData data : list) {
                                    if (data.type == type) { found = true; break; }
                                }
                                if (!found) {
                                    // Add with a base weight boosted by multiplier
                                    int weight = (int) (10 * multiplier);
                                    list.add(new MobSpawnSettings.SpawnerData(type, Math.max(1, weight), 1, 4));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        // We moved to rate-based boosting via PotentialSpawns and manual attempts.
        // Keeping this for a small additional effect if desired, but as per user request, 
        // we should not just multiply spawned entities.
        // We'll leave it empty or remove it.
    }

    private static void applyEffects(ServerPlayer player, Map<String, Integer> effects) {
        for (Map.Entry<String, Integer> entry : effects.entrySet()) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(entry.getKey()));
            if (effect != null) {
                player.addEffect(new MobEffectInstance(effect, 300, entry.getValue() - 1, false, false, true));
            }
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
        if (!settings.autoSellerActive) return;

        ItemStack stack = event.getItem().getItem();
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        
        boolean shouldSell = settings.autoSellerWhitelist.contains(id);
        if (settings.autoSellerIsBlacklist) shouldSell = !shouldSell;

        if (shouldSell) {
            int price = StorePriceManager.getSellPrice(stack.getItem());
            if (price > 0) {
                long totalGain = (long) price * stack.getCount();
                StorePriceManager.addMoney(player.getUUID(), totalGain);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[AutoSeller] §aSold " + stack.getCount() + "x " + stack.getHoverName().getString() + " for §e$" + StorePriceManager.formatCurrency(totalGain)), true);
                event.getItem().discard();
                event.setCanceled(true);
                StorePriceManager.sync(player);
            }
        }
    }
}
