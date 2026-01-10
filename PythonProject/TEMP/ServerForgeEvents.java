package com.example.modmenu;

import com.example.modmenu.store.StorePriceManager;
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

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "modmenu", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerForgeEvents {
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            if (settings.damageCancelActive) {
                float damage = event.getAmount();
                long cost = (long) (damage * StorePriceManager.formulas.damageCancelMultiplier);
                long currentMoney = StorePriceManager.getMoney(player.getUUID());
                
                if (currentMoney >= cost) {
                    StorePriceManager.addMoney(player.getUUID(), -cost);
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("§bDamage Cancelled! Cost: §e$" + StorePriceManager.formatCurrency(cost)), true);
                    StorePriceManager.sync(player);
                }
            }
        }
    }

    private static boolean processingSureKill = false;

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (processingSureKill) return;
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            if (settings.sureKillActive) {
                LivingEntity victim = event.getEntity();
                float health = victim.getHealth();
                long cost = (long) (StorePriceManager.formulas.sureKillBaseCost + (health * StorePriceManager.formulas.sureKillHealthMultiplier));
                long currentMoney = StorePriceManager.getMoney(player.getUUID());

                if (currentMoney >= cost) {
                    StorePriceManager.addMoney(player.getUUID(), -cost);
                    processingSureKill = true;
                    victim.hurt(event.getSource(), Float.MAX_VALUE);
                    processingSureKill = false;
                    player.displayClientMessage(Component.literal("§4Sure Kill Active! Cost: §e$" + StorePriceManager.formatCurrency(cost)), true);
                    StorePriceManager.sync(player);
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player) {
            StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
            if (settings.noAggroActive) {
                long currentMoney = StorePriceManager.getMoney(player.getUUID());
                long cost = StorePriceManager.formulas.noAggroCostPerCancel;
                if (currentMoney >= cost) {
                    StorePriceManager.addMoney(player.getUUID(), -cost);
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

    private static void performAreaMining(ServerPlayer player, StorePriceManager.AbilitySettings settings, BlockPos pos, BlockState state) {
        int size = settings.areaMiningSize;
        if (size <= 1) return;
        
        int radius = (size - 1) / 2;
        ServerLevel level = player.serverLevel();
        long currentMoney = StorePriceManager.getMoney(player.getUUID());
        long totalCost = 0;
        List<BlockPos> toBreak = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos p = pos.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.getBlock() == Blocks.BEDROCK) continue;
                    if (isMiningBlacklisted(s, settings.miningBlacklist)) continue;
                    
                    long cost = StorePriceManager.formulas.areaMiningCostBase;
                    if (currentMoney >= totalCost + cost) {
                        totalCost += cost;
                        toBreak.add(p);
                    }
                }
            }
        }

        if (!toBreak.isEmpty()) {
            StorePriceManager.addMoney(player.getUUID(), -totalCost);
            for (BlockPos p : toBreak) {
                level.destroyBlock(p, true, player);
            }
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
                         long cost = StorePriceManager.formulas.stepAssistCostPerAssist;
                         if (StorePriceManager.getMoney(player.getUUID()) >= cost) {
                             StorePriceManager.addMoney(player.getUUID(), -cost);
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

    private static boolean pricesCalculated = false;

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!pricesCalculated) {
                StorePriceManager.addAllItems(player.level());
                pricesCalculated = true;
            }
            StorePriceManager.applyAllAttributes(player);
            StorePriceManager.sync(player);
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
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            LivingEntity victim = event.getEntity();
            float maxHealth = victim.getMaxHealth();
            long reward = Math.round(maxHealth * 5);
            
            if (reward > 0) {
                StorePriceManager.addMoney(player.getUUID(), reward);
                StorePriceManager.sync(player);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) return;
        
        ServerPlayer player = (ServerPlayer) event.getEntity();
        StorePriceManager.AbilitySettings settings = StorePriceManager.getAbilities(player.getUUID());
        
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
        if (targetState.isAir() || targetState.getBlock() == Blocks.BEDROCK) return;
        if (isMiningBlacklisted(targetState, settings.miningBlacklist)) {
            player.displayClientMessage(Component.literal("§cBlacklisted Item"), true);
            return;
        }
        
        Block targetBlock = targetState.getBlock();
        BlockPos center = player.blockPosition();
        int radius = settings.focusedMiningRange;
        long currentMoney = StorePriceManager.getMoney(player.getUUID());
        
        long totalCost = 0;
        List<BlockPos> blocksToMine = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() != targetBlock) continue;

                    int price = StorePriceManager.getPrice(state.getBlock().asItem());
                    int effectivePrice = StorePriceManager.isOre(state.getBlock().asItem()) ? 1000 : price;
                    
                    double distance = Math.sqrt(pos.distSqr(center));
                    double costFactor = 1.0 + (distance / 10.0);
                    long cost = (long) ((effectivePrice / 20.0) * costFactor);
                    
                    if (settings.useEnchantments) {
                        int enchantMultiplier = 1;
                        for (int lvl : settings.miningEnchants.values()) {
                            enchantMultiplier += lvl;
                        }
                        cost *= enchantMultiplier;
                    }
                    
                    if (totalCost + cost <= currentMoney) {
                        totalCost += cost;
                        blocksToMine.add(pos);
                    }
                }
            }
        }
        
        executeMining(player, settings, blocksToMine, totalCost);
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
        int radius = settings.miningRange;
        long currentMoney = StorePriceManager.getMoney(player.getUUID());
        
        long totalCost = 0;
        List<BlockPos> blocksToMine = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getBlock() == Blocks.BEDROCK) continue;
                    
                    if (isMiningBlacklisted(state, settings.miningBlacklist)) continue;

                    int price = StorePriceManager.getPrice(state.getBlock().asItem());
                    int effectivePrice = StorePriceManager.isOre(state.getBlock().asItem()) ? 1000 : price;
                    
                    if (effectivePrice > 10) { // Valued block
                        double distance = Math.sqrt(pos.distSqr(center));
                        double costFactor = 1.0 + (distance / 10.0);
                        long cost = (long) ((effectivePrice / 20.0) * costFactor); // Cost is 5% of price * distance factor
                        
                        if (settings.useEnchantments) {
                            int enchantMultiplier = 1;
                            for (int lvl : settings.miningEnchants.values()) {
                                enchantMultiplier += lvl;
                            }
                            cost *= enchantMultiplier;
                        }
                        
                        if (totalCost + cost <= currentMoney) {
                            totalCost += cost;
                            blocksToMine.add(pos);
                        }
                    }
                }
            }
        }
        
        executeMining(player, settings, blocksToMine, totalCost);
    }

    private static void executeMining(ServerPlayer player, StorePriceManager.AbilitySettings settings, List<BlockPos> blocksToMine, long totalCost) {
        ServerLevel level = player.serverLevel();
        long currentMoney = StorePriceManager.getMoney(player.getUUID());
        
        if (blocksToMine.isEmpty()) {
            player.displayClientMessage(Component.literal("§cNo suitable blocks found or not enough money!"), true);
            return;
        }

        int minedCount = 0;
        long totalValue = 0;
        
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
            
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            minedCount++;
            
            for (ItemStack drop : drops) {
                if (settings.autoSell) {
                    totalValue += (long) StorePriceManager.getPrice(drop.getItem()) * drop.getCount();
                } else {
                    if (!player.getInventory().add(drop)) {
                        player.drop(drop, false);
                    }
                }
            }
        }
        
        StorePriceManager.setMoney(player.getUUID(), currentMoney - totalCost + totalValue);
        player.displayClientMessage(Component.literal("§aMined " + minedCount + " blocks. Cost: §e$" + StorePriceManager.formatCurrency(totalCost) + (settings.autoSell ? " §aEarned: §e$" + StorePriceManager.formatCurrency(totalValue) : "")), true);
        StorePriceManager.sync(player);
    }

}
