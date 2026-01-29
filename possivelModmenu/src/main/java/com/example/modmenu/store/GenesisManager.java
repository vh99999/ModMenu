package com.example.modmenu.store;

import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.SyncSkillsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

@Mod.EventBusSubscriber(modid = "modmenu")
public class GenesisManager {
    public static final ResourceKey<Level> GENESIS_DIM = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse("modmenu:genesis"));
    private static final List<Runnable> scheduledTasks = new ArrayList<>();

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo().equals(GENESIS_DIM) && event.getEntity() instanceof ServerPlayer player) {
            StorePriceManager.GenesisConfig config = getConfig(player.serverLevel());
            if (config != null) {
                if (config.fullResetRequested) {
                    config.fullResetRequested = false;
                    StorePriceManager.markDirty(null);
                    player.displayClientMessage(Component.literal("\u00A7a[Genesis Hub] Your dimension has been fully regenerated!"), false);
                }
                if (!config.joinMessage.isEmpty()) {
                    player.displayClientMessage(Component.literal(config.joinMessage.replace("&", "\u00A7")), false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(GENESIS_DIM)) {
            StorePriceManager.GenesisConfig config = getConfig(player.level());
            if (config != null && config.fullResetRequested) {
                config.fullResetRequested = false;
                StorePriceManager.markDirty(null);
                player.displayClientMessage(Component.literal("\u00A7a[Genesis Hub] Your dimension has been fully regenerated!"), false);
            }
        }
    }

    private static MinecraftServer staticServer;

    private static double snapshottedDimensionScale = 1.0;

    private static ResourceKey<net.minecraft.world.level.dimension.DimensionType> GENESIS_TYPE_KEY = null;

    @SubscribeEvent
    public static void onServerAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        staticServer = event.getServer();
        com.mojang.logging.LogUtils.getLogger().info("[Genesis Hub] Server initialized, Genesis Manager active.");
        
        GENESIS_TYPE_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.tryParse("modmenu:genesis"));

        // Check for full reset request
        java.io.File worldDir = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        checkAndPerformFullReset(worldDir);
    }

    public static boolean isGenesisType(net.minecraft.world.level.dimension.DimensionType type) {
        if (staticServer == null) return type.logicalHeight() == 385;
        Optional<ResourceKey<net.minecraft.world.level.dimension.DimensionType>> key = staticServer.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getResourceKey(type);
        return key.isPresent() && key.get().equals(GENESIS_TYPE_KEY);
    }

    public static void snapshotSessionConfig() {
        StorePriceManager.GenesisConfig config = getServerConfig();
        if (config != null) {
            snapshottedDimensionScale = config.dimensionScale;
            com.mojang.logging.LogUtils.getLogger().info("[Genesis Hub] Session config snapshotted (Dimension Scale: " + snapshottedDimensionScale + ")");
        }
    }

    public static double getSnapshottedDimensionScale() {
        return snapshottedDimensionScale;
    }

    public static Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> getNoiseSettings(net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> key) {
        if (staticServer == null) return null;
        return staticServer.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolder(key).orElse(null);
    }

    public static Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> getSettingsForGenerator(Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> original) {
        StorePriceManager.GenesisConfig config = getServerConfig();
        if (config == null) return original;
        
        ResourceKey<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> key = switch (config.genType) {
            case "Amplified" -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.AMPLIFIED;
            case "Large Biomes" -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.LARGE_BIOMES;
            case "Floating islands original", "Floating Islands" -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.FLOATING_ISLANDS;
            case "Caves" -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.NETHER;
            case "End" -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.END;
            default -> null;
        };
        
        if (key != null) {
            Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> h = getNoiseSettings(key);
            if (h != null) return h;
        }
        return original;
    }

    private static void checkAndPerformFullReset(java.io.File worldDir) {
        java.io.File globalFile = new java.io.File(worldDir, "modmenu_data/global.json");
        boolean resetRequested = false;

        if (globalFile.exists()) {
            try (java.io.FileReader reader = new java.io.FileReader(globalFile)) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("genesisConfig")) {
                    com.google.gson.JsonObject config = json.getAsJsonObject("genesisConfig");
                    if (config.has("fullResetRequested") && config.get("fullResetRequested").getAsBoolean()) {
                        resetRequested = true;
                    }
                }
            } catch (Exception e) {}
        }

        // Fallback: check player files (Legacy or if global check failed)
        if (!resetRequested) {
            java.io.File playerDir = new java.io.File(worldDir, "modmenu_data/players");
            if (playerDir.exists()) {
                java.io.File[] files = playerDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (java.io.File f : files) {
                        try (java.io.FileReader reader = new java.io.FileReader(f)) {
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                            if (json.has("skills")) {
                                com.google.gson.JsonObject skills = json.getAsJsonObject("skills");
                                if (skills.has("genesisConfig")) {
                                    com.google.gson.JsonObject config = skills.getAsJsonObject("genesisConfig");
                                    if (config.has("fullResetRequested") && config.get("fullResetRequested").getAsBoolean()) {
                                        resetRequested = true;
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        }

        if (resetRequested) {
            java.io.File dimDir = new java.io.File(worldDir, "dimensions/modmenu/genesis");
            if (dimDir.exists()) {
                deleteDirectory(dimDir);
                com.mojang.logging.LogUtils.getLogger().info("[Genesis Hub] Full reset: Deleted Genesis dimension folder.");
            }
            
            // Clear flag from global file
            if (globalFile.exists()) {
                try {
                    com.google.gson.JsonObject json;
                    try (java.io.FileReader reader = new java.io.FileReader(globalFile)) {
                        json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    }
                    if (json.has("genesisConfig")) {
                        com.google.gson.JsonObject config = json.getAsJsonObject("genesisConfig");
                        if (config.has("fullResetRequested")) {
                            config.addProperty("fullResetRequested", false);
                            try (java.io.FileWriter writer = new java.io.FileWriter(globalFile)) {
                                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
                            }
                        }
                    }
                } catch (Exception e) {}
            }

            // Clear flag from all player files to prevent infinite wipe loop
            java.io.File playerDir = new java.io.File(worldDir, "modmenu_data/players");
            if (playerDir.exists()) {
                java.io.File[] files = playerDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (java.io.File f : files) {
                        try {
                            com.google.gson.JsonObject json;
                            try (java.io.FileReader reader = new java.io.FileReader(f)) {
                                json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                            }
                            if (json.has("skills")) {
                                com.google.gson.JsonObject skills = json.getAsJsonObject("skills");
                                if (skills.has("genesisConfig")) {
                                    com.google.gson.JsonObject config = skills.getAsJsonObject("genesisConfig");
                                    if (config.has("fullResetRequested")) {
                                        config.addProperty("fullResetRequested", false);
                                        try (java.io.FileWriter writer = new java.io.FileWriter(f)) {
                                            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        }
    }

    private static void deleteDirectory(java.io.File file) {
        java.io.File[] contents = file.listFiles();
        if (contents != null) {
            for (java.io.File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    public static StorePriceManager.GenesisConfig getServerConfig() {
        return StorePriceManager.getGlobalGenesisConfig();
    }

    public static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getBiomeHolder(String id) {
        if (staticServer == null) return null;
        try {
            return staticServer.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.BIOME, ResourceLocation.tryParse(id))
            );
        } catch (Exception e) {
            // Fallback to plains if biome not found
            return staticServer.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.BIOME, ResourceLocation.tryParse("minecraft:plains"))
            );
        }
    }

    public static void teleportToGenesis(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(GENESIS_DIM);
        if (level == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7c[Genesis Hub] Dimension not initialized! (Mod Internal Error)"), true);
            return;
        }
        
        // Save current location before entering
        StorePriceManager.SavedLocation currentLoc = new StorePriceManager.SavedLocation(
            player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot(),
            player.level().dimension().location().toString()
        );
        StorePriceManager.setLastLocation(player.getUUID(), currentLoc);
        
        BlockPos spawnPos = level.getSharedSpawnPos();
        BlockPos safePos = findSafePos(level, spawnPos);
        
        player.teleportTo(level, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, 0, 0);
    }

    public static void leaveGenesis(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        StorePriceManager.SavedLocation lastLoc = StorePriceManager.getLastLocation(player.getUUID());
        if (lastLoc != null) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(lastLoc.dim);
            ServerLevel targetLevel = null;
            if (dimLoc != null) {
                targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
            }
            if (targetLevel != null) {
                player.teleportTo(targetLevel, lastLoc.x, lastLoc.y, lastLoc.z, lastLoc.yaw, lastLoc.pitch);
                return;
            }
        }
        
        ResourceKey<Level> targetDim = player.getRespawnDimension();
        if (targetDim.equals(GENESIS_DIM)) {
            targetDim = Level.OVERWORLD;
        }
        
        ServerLevel targetLevel = server.getLevel(targetDim);
        if (targetLevel == null) targetLevel = server.getLevel(Level.OVERWORLD);
        
        if (targetLevel != null) {
            BlockPos respawnPos = player.getRespawnPosition();
            if (respawnPos != null) {
                player.teleportTo(targetLevel, respawnPos.getX() + 0.5, respawnPos.getY(), respawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            } else {
                BlockPos spawnPos = targetLevel.getSharedSpawnPos();
                player.teleportTo(targetLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            }
        }
    }

    public static void regenerateGenesis(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(GENESIS_DIM);
        if (level == null) return;

        StorePriceManager.GenesisConfig config = getConfig(level);
        if (config == null) return;

        // Set full reset flag for next restart
        config.fullResetRequested = true;
        StorePriceManager.markDirty(null);

        // Teleport everyone to spawn and clear area
        for (ServerPlayer p : level.players()) {
            p.teleportTo(level, 0.5, 102, 0.5, 0, 0);
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7e[Genesis Hub] Dimension reset in progress..."), true);
        }

        // Clear all entities in the 64x64 spawn area
        int radius = 32;
        net.minecraft.world.phys.AABB spawnAABB = new net.minecraft.world.phys.AABB(-radius, level.getMinBuildHeight(), -radius, radius, level.getMaxBuildHeight(), radius);
        level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, spawnAABB).forEach(e -> {
            if (!(e instanceof ServerPlayer)) e.discard();
        });
        
        // Start asynchronous clearing
        synchronized (scheduledTasks) {
            scheduledTasks.add(new BlockClearTask(level, radius));
        }
        
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7aGenesis Dimension spawn area reset started."), true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7e[Full Reset] \u00A77A complete world wipe is scheduled for the next server restart."), false);
    }

    private static class BlockClearTask implements Runnable {
        private final ServerLevel level;
        private final int radius;
        private int currentX;
        private int currentZ;
        private boolean finished = false;

        public BlockClearTask(ServerLevel level, int radius) {
            this.level = level;
            this.radius = radius;
            this.currentX = -radius;
            this.currentZ = -radius;
        }

        @Override
        public void run() {
            int columnsProcessed = 0;
            while (columnsProcessed < 64 && !finished) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    level.setBlock(new BlockPos(currentX, y, currentZ), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
                level.setBlock(new BlockPos(currentX, 100, currentZ), Blocks.GRASS_BLOCK.defaultBlockState(), 2 | 16);
                level.setBlock(new BlockPos(currentX, 99, currentZ), Blocks.DIRT.defaultBlockState(), 2 | 16);
                level.setBlock(new BlockPos(currentX, 98, currentZ), Blocks.BEDROCK.defaultBlockState(), 2 | 16);

                columnsProcessed++;
                currentZ++;
                if (currentZ >= radius) {
                    currentZ = -radius;
                    currentX++;
                    if (currentX >= radius) {
                        finished = true;
                    }
                }
            }
            if (!finished) {
                synchronized (scheduledTasks) {
                    scheduledTasks.add(this);
                }
            }
        }
    }

    private static double timeAccumulator = 0;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;

        if (!scheduledTasks.isEmpty()) {
            List<Runnable> tasksToRun;
            synchronized (scheduledTasks) {
                tasksToRun = new ArrayList<>(scheduledTasks);
                scheduledTasks.clear();
            }
            for (Runnable task : tasksToRun) {
                task.run();
            }
        }

        if (event.level.dimension().equals(GENESIS_DIM)) {
            ServerLevel level = (ServerLevel) event.level;
            if (level.players().isEmpty()) return;

            StorePriceManager.GenesisConfig config = getConfig(level);
            if (config != null) {
                // Slower frequency for non-critical config application
                if (level.getGameTime() % 20 == 0) {
                    applyConfig(level, config);
                }
                
                // Celestial Sync
                if (config.celestialSync && !config.tickFreeze) {
                    ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
                    if (overworld != null) {
                        level.setDayTime(overworld.getDayTime());
                    }
                } else if (!config.tickFreeze) {
                    // Temporal Velocity & Day/Night Ratio
                    long time = level.getDayTime();
                    long timeInDay = Math.abs(time % 24000);
                    boolean isDay = timeInDay < 12000;
                    
                    double multiplier = config.temporalVelocity;
                    if (isDay) {
                        multiplier *= (0.5 / Math.max(0.01, config.dayNightRatio));
                    } else {
                        multiplier *= (0.5 / Math.max(0.01, 1.0 - config.dayNightRatio));
                    }
                    
                    timeAccumulator += (multiplier - 1.0);
                    if (timeAccumulator >= 1.0) {
                        int toAdd = (int) timeAccumulator;
                        level.setDayTime(time + toAdd);
                        timeAccumulator -= toAdd;
                    } else if (timeAccumulator <= -1.0) {
                        int toSub = (int) Math.abs(timeAccumulator);
                        level.setDayTime(time - toSub);
                        timeAccumulator += toSub;
                    }
                }
            }
        }
    }

    private static void applyConfig(ServerLevel level, StorePriceManager.GenesisConfig config) {
        // Time
        if (config.tickFreeze) {
            level.setDayTime(config.frozenTime);
        }

        // Weather
        if (config.persistentWeather.equals("Clear")) {
            level.setWeatherParameters(6000, 0, false, false);
        } else if (config.persistentWeather.equals("Rain")) {
            level.setWeatherParameters(0, 6000, true, false);
        } else if (config.persistentWeather.equals("Thunder")) {
            level.setWeatherParameters(0, 6000, true, true);
        }

        // Reality Persistence (Keep spawn loaded)
        if (config.realityPersistence) {
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, new net.minecraft.world.level.ChunkPos(0, 0), 2, new net.minecraft.world.level.ChunkPos(0, 0));
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getLevel().getLevel().dimension().equals(GENESIS_DIM)) {
            ServerLevel level = event.getLevel().getLevel();
            StorePriceManager.GenesisConfig config = getConfig(level);
            if (config != null) {
                net.minecraft.world.entity.Mob mob = event.getEntity();
                boolean isHostile = mob instanceof net.minecraft.world.entity.monster.Monster;
                boolean isPassive = mob instanceof net.minecraft.world.entity.animal.Animal;
                boolean isNeutral = !isHostile && !isPassive;

                // Spawn Rate Control
                if (config.mobSpawnRate < 1.0 && level.random.nextDouble() > config.mobSpawnRate) {
                    event.setSpawnCancelled(true);
                    return;
                }

                if (isHostile && !config.spawnHostile) event.setSpawnCancelled(true);
                else if (isPassive && !config.spawnPassive) event.setSpawnCancelled(true);
                else if (isNeutral && !config.spawnNeutral) event.setSpawnCancelled(true);

                // Mutation logic
                if (!event.isSpawnCancelled() && config.mobMutationRate > 0 && level.random.nextDouble() < config.mobMutationRate) {
                    if (isHostile) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 1000000, 1));
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 1000000, 1));
                        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
                    }
                }
            }
        }
    }

    public static StorePriceManager.GenesisConfig getConfig(net.minecraft.world.level.Level level) {
        return StorePriceManager.getGlobalGenesisConfig();
    }

    private static ServerPlayer getOwner(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (StorePriceManager.getUnlockedHouses(p.getUUID()).contains("dimension_configurator")) {
                return p;
            }
        }
        return null;
    }

    private static BlockPos findSafePos(ServerLevel level, BlockPos startPos) {
        // 1. Try starting from shared spawn and looking down/up
        for (int yOffset = 0; yOffset < 100; yOffset++) {
            // Check down
            BlockPos posDown = startPos.below(yOffset);
            if (posDown.getY() > level.getMinBuildHeight() && isSafe(level, posDown)) return posDown.above();
            
            // Check up
            BlockPos posUp = startPos.above(yOffset);
            if (posUp.getY() < level.getMaxBuildHeight() - 2 && isSafe(level, posUp)) return posUp.above();
        }
        
        // 2. Spiral search if vertical fails
        for (int r = 1; r < 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos p = startPos.offset(dx, 0, dz);
                    // Search vertically at this spot
                    for (int y = startPos.getY() + 20; y > level.getMinBuildHeight(); y--) {
                        BlockPos pos = new BlockPos(p.getX(), y, p.getZ());
                        if (isSafe(level, pos)) return pos.above();
                    }
                }
            }
        }
        
        return startPos;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        BlockState above2 = level.getBlockState(pos.above(2));
        
        // 1. Sturdy floor
        if (!state.isFaceSturdy(level, pos, Direction.UP)) return false;
        
        // 2. Clear head space
        if (!above.isAir() || !above2.isAir()) return false;
        
        // 3. Hazard checks (Feet and Head level)
        if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return false;
        if (above.is(Blocks.LAVA) || above.is(Blocks.MAGMA_BLOCK) || above.is(Blocks.FIRE) || above.is(Blocks.SOUL_FIRE)) return false;
        
        // 4. Fluid check (unless it's the sea level fluid and player explicitly chose it, 
        // but generally we want dry land for spawn)
        if (!above.getFluidState().isEmpty()) return false;

        // 5. Check surrounding blocks for immediate danger (lava pools)
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = level.getBlockState(pos.above().relative(dir));
            if (neighbor.is(Blocks.LAVA) || neighbor.is(Blocks.FIRE)) return false;
            
            BlockState neighborFeet = level.getBlockState(pos.relative(dir));
            if (neighborFeet.is(Blocks.LAVA) || neighborFeet.is(Blocks.FIRE)) return false;
        }
        
        return true;
    }
}
