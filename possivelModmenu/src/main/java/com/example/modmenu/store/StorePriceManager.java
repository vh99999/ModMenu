package com.example.modmenu.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.example.modmenu.store.pricing.PricingEngine;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StorePriceManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(net.minecraft.world.item.ItemStack.class, new GsonAdapters.ItemStackAdapter())
            .registerTypeAdapter(net.minecraft.nbt.CompoundTag.class, new GsonAdapters.CompoundTagAdapter())
            .registerTypeAdapterFactory(new GsonAdapters.OptionalAdapterFactory())
            .create();
    private static final File PRICES_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "store_prices.json");
    private static File DATA_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "store_data.json");
    private static final PricingEngine pricingEngine = new PricingEngine();
    
    public static final BigDecimal BD_100 = BigDecimal.valueOf(100);
    public static final BigDecimal BD_1000 = BigDecimal.valueOf(1000);
    public static final BigDecimal BD_BILLION = new BigDecimal("1000000000");
    
    private static Map<String, BigDecimal> itemBuyPrices = new HashMap<>();
    private static Map<String, BigDecimal> itemSellPrices = new HashMap<>();
    private static Map<String, Long> totalSoldVolume = new HashMap<>(); // Dynamic Economy
    private static Map<String, BigDecimal> enchantPrices = new HashMap<>();
    private static Map<String, BigDecimal> effectBasePrices = new HashMap<>();
    private static Set<String> editedPrices = new HashSet<>();
    private static Map<UUID, BigDecimal> playerMoneyMap = new HashMap<>();
    private static Map<UUID, Map<String, Integer>> activeEffects = new HashMap<>();
    private static Map<UUID, AbilitySettings> playerAbilities = new HashMap<>();
    private static Map<UUID, Set<String>> unlockedHousesMap = new HashMap<>();
    private static Map<UUID, Map<String, Double>> playerAttributeBonuses = new HashMap<>();
    private static Map<UUID, SkillData> playerSkills = new HashMap<>();
    private static Map<UUID, String> playerReturnDimension = new HashMap<>();
    private static Map<UUID, double[]> playerReturnPosition = new HashMap<>();
    private static Set<UUID> editors = new HashSet<>();
    public static boolean globalSkillsDisabled = false;
    
    // Client-side current player info
    public static BigDecimal playerMoney = BigDecimal.ZERO;
    public static BigDecimal playerDrain = BigDecimal.ZERO;
    public static boolean isEditor = false;
    public static SkillData clientSkills = new SkillData();
    public static Map<String, Integer> clientActiveEffects = new HashMap<>();
    public static AbilitySettings clientAbilities = new AbilitySettings();
    public static Set<String> clientUnlockedHouses = new HashSet<>();
    public static Map<String, BigDecimal> clientBuyPrices = new HashMap<>();
    public static Map<String, BigDecimal> clientSellPrices = new HashMap<>();
    public static Map<String, Long> clientSoldVolume = new HashMap<>();
    public static Map<String, BigDecimal> clientEnchantPrices = new HashMap<>();
    public static Map<String, BigDecimal> clientEffectPrices = new HashMap<>();
    public static Map<String, Double> clientAttributeBonuses = new HashMap<>();

    public static void setClientPrices(Map<String, BigDecimal> buy, Map<String, BigDecimal> sell, Map<String, Long> volume, Map<String, BigDecimal> enchants, Map<String, BigDecimal> effects) {
        clientBuyPrices = buy;
        clientSellPrices = sell;
        clientSoldVolume = volume;
        clientEnchantPrices = enchants;
        clientEffectPrices = effects;
    }

    public static void clearClientData() {
        playerMoney = BigDecimal.ZERO;
        playerDrain = BigDecimal.ZERO;
        isEditor = false;
        clientSkills = new SkillData();
        clientActiveEffects.clear();
        clientAbilities = new AbilitySettings();
        clientUnlockedHouses.clear();
        clientBuyPrices.clear();
        clientSellPrices.clear();
        clientSoldVolume.clear();
        clientEnchantPrices.clear();
        clientEffectPrices.clear();
        clientAttributeBonuses.clear();
    }

    public static class AbilitySettings {
        public boolean miningActive = false;
        public int miningRange = 16;
        public boolean autoSell = false;
        public boolean autoSellerIsBlacklist = false;
        public boolean useEnchantments = false;
        public Map<String, Integer> miningEnchants = new HashMap<>();
        public List<String> miningBlacklist = new ArrayList<>();
        public List<String> selectedEnchantments = new ArrayList<>(); 
        
        public boolean focusedMiningActive = false;
        public int focusedMiningRange = 16;

        public boolean repairActive = false;
        
        public boolean itemMagnetActive = false;
        public int itemMagnetRange = 16;
        public int itemMagnetOpsPerTick = 1;
        
        public boolean xpMagnetActive = false;
        public int xpMagnetRange = 16;
        public int xpMagnetOpsPerTick = 1;
        
        public boolean autoSellerActive = false;
        public List<String> autoSellerWhitelist = new ArrayList<>();
        public boolean sellAllWhitelistActive = false;
        public List<String> sellAllWhitelist = new ArrayList<>();
        public List<String> sellAllBlacklist = new ArrayList<>();

        public List<String> smelterWhitelist = new ArrayList<>();

        public boolean chestHighlightActive = false;
        public int chestHighlightRange = 16;
        
        public boolean trapHighlightActive = false;
        public int trapHighlightRange = 16;
        
        public boolean entityESPActive = false;
        public int entityESPRange = 16;
        
        public boolean damageCancelActive = false;
        public double damageCancelMultiplier = 100.0; 

        public boolean stepAssistActive = false;
        public float stepAssistHeight = 1.0f;
        
        public boolean areaMiningActive = false;
        public int areaMiningSize = 3;
        
        public boolean captureActive = false; // Capture toggle

        public boolean flightActive = false;
        public boolean sureKillActive = false;
        public boolean noAggroActive = false;

        public boolean spawnBoostActive = false;
        public double spawnBoostMultiplier = 2.0;
        public List<String> spawnBoostTargets = new ArrayList<>();
        public List<String> disabledSpawnConditions = new ArrayList<>();

        public boolean growCropsActive = false;
        public int growCropsRange = 5;

        public void copyFrom(AbilitySettings other) {
            this.miningActive = other.miningActive;
            this.miningRange = other.miningRange;
            this.autoSell = other.autoSell;
            this.autoSellerIsBlacklist = other.autoSellerIsBlacklist;
            this.useEnchantments = other.useEnchantments;
            this.miningEnchants.clear();
            this.miningEnchants.putAll(other.miningEnchants);
            this.miningBlacklist.clear();
            this.miningBlacklist.addAll(other.miningBlacklist);
            this.selectedEnchantments.clear();
            this.selectedEnchantments.addAll(other.selectedEnchantments);
            this.focusedMiningActive = other.focusedMiningActive;
            this.focusedMiningRange = other.focusedMiningRange;
            this.repairActive = other.repairActive;
            this.itemMagnetActive = other.itemMagnetActive;
            this.itemMagnetRange = other.itemMagnetRange;
            this.itemMagnetOpsPerTick = other.itemMagnetOpsPerTick;
            this.xpMagnetActive = other.xpMagnetActive;
            this.xpMagnetRange = other.xpMagnetRange;
            this.xpMagnetOpsPerTick = other.xpMagnetOpsPerTick;
            this.autoSellerActive = other.autoSellerActive;
            this.autoSellerWhitelist.clear();
            this.autoSellerWhitelist.addAll(other.autoSellerWhitelist);
            this.sellAllWhitelistActive = other.sellAllWhitelistActive;
            this.sellAllWhitelist.clear();
            this.sellAllWhitelist.addAll(other.sellAllWhitelist);
            this.sellAllBlacklist.clear();
            this.sellAllBlacklist.addAll(other.sellAllBlacklist);
            this.smelterWhitelist.clear();
            this.smelterWhitelist.addAll(other.smelterWhitelist);
            this.chestHighlightActive = other.chestHighlightActive;
            this.chestHighlightRange = other.chestHighlightRange;
            this.trapHighlightActive = other.trapHighlightActive;
            this.trapHighlightRange = other.trapHighlightRange;
            this.entityESPActive = other.entityESPActive;
            this.entityESPRange = other.entityESPRange;
            this.damageCancelActive = other.damageCancelActive;
            this.damageCancelMultiplier = other.damageCancelMultiplier;
            this.stepAssistActive = other.stepAssistActive;
            this.stepAssistHeight = other.stepAssistHeight;
            this.areaMiningActive = other.areaMiningActive;
            this.areaMiningSize = other.areaMiningSize;
            this.captureActive = other.captureActive;
            this.flightActive = other.flightActive;
            this.sureKillActive = other.sureKillActive;
            this.noAggroActive = other.noAggroActive;
            this.spawnBoostActive = other.spawnBoostActive;
            this.spawnBoostMultiplier = other.spawnBoostMultiplier;
            this.spawnBoostTargets.clear();
            this.spawnBoostTargets.addAll(other.spawnBoostTargets);
            this.disabledSpawnConditions.clear();
            this.disabledSpawnConditions.addAll(other.disabledSpawnConditions);
            this.growCropsActive = other.growCropsActive;
            this.growCropsRange = other.growCropsRange;
        }
    }

    public static class SkillData {
        public BigDecimal totalSP = BigDecimal.ZERO;
        public BigDecimal spentSP = BigDecimal.ZERO;
        public Map<String, Integer> skillRanks = new ConcurrentHashMap<>();
        public Map<String, Integer> unlockedRanks = new ConcurrentHashMap<>();
        public Set<String> activeToggles = Collections.newSetFromMap(new ConcurrentHashMap<>());
        public Map<String, Float> mobSatiety = new ConcurrentHashMap<>();
        public List<String> branchOrder = new ArrayList<>();
        public Map<String, BigDecimal> permanentAttributes = new ConcurrentHashMap<>();
        public Map<String, Long> lastCaptureTimes = new ConcurrentHashMap<>(); // For Chamber harvesting
        public List<ChamberData> chambers = new ArrayList<>();
        public int unlockedChambers = 1;
        public Set<String> blacklistedSpecies = Collections.newSetFromMap(new ConcurrentHashMap<>());
        public int overclockKillsRemaining = 0;
        
        // Diagnostics & Progress
        public BigDecimal totalKills = BigDecimal.ZERO;
        public BigDecimal damageReflected = BigDecimal.ZERO;
        public BigDecimal damageHealed = BigDecimal.ZERO;

        public void copyFrom(SkillData other) {
            this.totalSP = other.totalSP;
            this.spentSP = other.spentSP;
            this.skillRanks.clear();
            this.skillRanks.putAll(other.skillRanks);
            this.unlockedRanks.clear();
            this.unlockedRanks.putAll(other.unlockedRanks);
            this.activeToggles.clear();
            this.activeToggles.addAll(other.activeToggles);
            this.mobSatiety.clear();
            this.mobSatiety.putAll(other.mobSatiety);
            this.branchOrder.clear();
            this.branchOrder.addAll(other.branchOrder);
            this.permanentAttributes.clear();
            this.permanentAttributes.putAll(other.permanentAttributes);
            this.lastCaptureTimes.clear();
            this.lastCaptureTimes.putAll(other.lastCaptureTimes);
            this.chambers.clear();
            this.chambers.addAll(other.chambers);
            this.unlockedChambers = other.unlockedChambers;
            this.blacklistedSpecies.clear();
            this.blacklistedSpecies.addAll(other.blacklistedSpecies);
            this.overclockKillsRemaining = other.overclockKillsRemaining;
            this.totalKills = other.totalKills;
            this.damageReflected = other.damageReflected;
            this.damageHealed = other.damageHealed;
        }

        public SkillData snapshot() {
            SkillData snap = new SkillData();
            snap.totalSP = this.totalSP;
            snap.spentSP = this.spentSP;
            snap.skillRanks.putAll(this.skillRanks);
            snap.unlockedRanks.putAll(this.unlockedRanks);
            snap.activeToggles.addAll(this.activeToggles);
            snap.mobSatiety.putAll(this.mobSatiety);
            snap.branchOrder.addAll(this.branchOrder);
            snap.permanentAttributes.putAll(this.permanentAttributes);
            snap.lastCaptureTimes.putAll(this.lastCaptureTimes);
            for (ChamberData c : this.chambers) {
                snap.chambers.add(c.snapshot());
            }
            snap.unlockedChambers = this.unlockedChambers;
            snap.blacklistedSpecies.addAll(this.blacklistedSpecies);
            snap.overclockKillsRemaining = this.overclockKillsRemaining;
            snap.totalKills = this.totalKills;
            snap.damageReflected = this.damageReflected;
            snap.damageHealed = this.damageHealed;
            return snap;
        }
    }

    public static class FilterRule {
        public String matchType = "ID"; // "ID", "TAG", "NBT"
        public String matchValue = "";
        public net.minecraft.nbt.CompoundTag nbtSample;
        public int action = 0; // 0: KEEP, 1: VOID, 2: LIQUIDATE

        public FilterRule snapshot() {
            FilterRule snap = new FilterRule();
            snap.matchType = this.matchType;
            snap.matchValue = this.matchValue;
            snap.nbtSample = this.nbtSample != null ? this.nbtSample.copy() : null;
            snap.action = this.action;
            return snap;
        }
    }

    public static class ChamberData {
        public String mobId;
        public String customName;
        public net.minecraft.nbt.CompoundTag nbt;
        public boolean isExact;
        public List<ItemStack> storedLoot = new ArrayList<>();
        public BigDecimal storedXP = BigDecimal.ZERO;
        public long lastHarvestTime = 0;
        public ItemStack killerWeapon = ItemStack.EMPTY;
        public int rerollCount = 0;
        public boolean paused = false;
        public long lastOfflineProcessingTime = 0;
        public List<String> voidFilter = new ArrayList<>();
        public int updateVersion = 0;

        // New Advanced Features
        public boolean barteringMode = false;
        public List<ItemStack> inputBuffer = new ArrayList<>();
        public int condensationMode = 0; // 0: OFF, 1: SAFE, 2: ALL
        public Map<String, Integer> yieldTargets = new HashMap<>();
        public int speedSlider = 1;
        public int threadSlider = 1;
        public List<FilterRule> advancedFilters = new ArrayList<>();
        public boolean isExcavation = false;
        public String lootTableId = null;

        public ChamberData snapshot() {
            ChamberData snap = new ChamberData();
            snap.mobId = this.mobId;
            snap.customName = this.customName;
            snap.nbt = this.nbt != null ? this.nbt.copy() : null;
            snap.isExact = this.isExact;
            for (ItemStack stack : this.storedLoot) {
                snap.storedLoot.add(stack.copy());
            }
            snap.storedXP = this.storedXP;
            snap.lastHarvestTime = this.lastHarvestTime;
            snap.killerWeapon = this.killerWeapon.copy();
            snap.rerollCount = this.rerollCount;
            snap.paused = this.paused;
            snap.lastOfflineProcessingTime = this.lastOfflineProcessingTime;
            snap.voidFilter.addAll(this.voidFilter);
            snap.updateVersion = this.updateVersion;

            snap.barteringMode = this.barteringMode;
            for (ItemStack stack : this.inputBuffer) {
                snap.inputBuffer.add(stack.copy());
            }
            snap.condensationMode = this.condensationMode;
            snap.yieldTargets.putAll(this.yieldTargets);
            snap.speedSlider = this.speedSlider;
            snap.threadSlider = this.threadSlider;
            for (FilterRule rule : this.advancedFilters) {
                snap.advancedFilters.add(rule.snapshot());
            }
            snap.isExcavation = this.isExcavation;
            snap.lootTableId = this.lootTableId;
            return snap;
        }
    }

    public static class FormulaConfig {
        public BigDecimal stepAssistCostPerAssist = BigDecimal.valueOf(50);
        public BigDecimal areaMiningCostBase = BigDecimal.valueOf(100);
        public BigDecimal flightCostPerSecond = BigDecimal.valueOf(500);
        public BigDecimal sureKillBaseCost = BigDecimal.valueOf(5000);
        public double sureKillHealthMultiplier = 100.0;
        public BigDecimal noAggroCostPerCancel = BigDecimal.valueOf(200);
        public BigDecimal noAggroMaintenance = BigDecimal.valueOf(300);
        public double damageCancelMultiplier = 100.0;
        public BigDecimal damageCancelMaintenance = BigDecimal.valueOf(50);
        public int repairCostPerPoint = 10;
        public BigDecimal repairMaintenance = BigDecimal.valueOf(100);
        public BigDecimal chestHighlightMaintenancePerRange = BigDecimal.valueOf(2);
        public BigDecimal trapHighlightMaintenancePerRange = BigDecimal.valueOf(2);
        public BigDecimal entityESPMaintenancePerRange = BigDecimal.valueOf(2);
        public BigDecimal itemMagnetMaintenancePerRangeOps = BigDecimal.valueOf(1);
        public BigDecimal xpMagnetMaintenancePerRangeOps = BigDecimal.valueOf(1);
        public BigDecimal autoSellerMaintenance = BigDecimal.valueOf(150);
        public BigDecimal spawnBoostMaintenance = BigDecimal.valueOf(500);
        public BigDecimal spawnBoostPerSpawnBase = BigDecimal.valueOf(1000);
        public BigDecimal growCropsMaintenance = BigDecimal.valueOf(200);
        public BigDecimal growCropsPerOperation = BigDecimal.valueOf(100);
        public double spMultiplier = 1.0;
    }

    public static FormulaConfig formulas = new FormulaConfig();
    private static final File FORMULA_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "store_formulas.json");

    private static class PlayerData {
        Map<String, BigDecimal> money = new HashMap<>();
        List<String> editors = new ArrayList<>();
        Map<String, Map<String, Integer>> activeEffects = new HashMap<>();
        Map<String, AbilitySettings> abilities = new HashMap<>();
        Map<String, SkillData> skills = new HashMap<>();
        Map<String, List<String>> unlockedHouses = new HashMap<>();
        Map<String, Map<String, Double>> attributeBonuses = new HashMap<>();
        Map<String, String> returnDimension = new HashMap<>();
        Map<String, double[]> returnPosition = new HashMap<>();
        Map<String, Long> soldVolume = new HashMap<>();
    }

    private static class StorePrices {
        Map<String, BigDecimal> itemsBuy = new HashMap<>();
        Map<String, BigDecimal> itemsSell = new HashMap<>();
        Map<String, BigDecimal> enchantments = new HashMap<>();
        Map<String, BigDecimal> effects = new HashMap<>();
        Set<String> edited = new HashSet<>();
    }

    public static void load() {
        loadPrices();
        loadFormulas();
    }

    public static void initWorldData(File worldDir) {
        DATA_FILE = new File(worldDir, "store_data.json");
        playerMoneyMap.clear();
        totalSoldVolume.clear();
        editors.clear();
        activeEffects.clear();
        playerAbilities.clear();
        playerSkills.clear();
        unlockedHousesMap.clear();
        playerAttributeBonuses.clear();
        playerReturnDimension.clear();
        playerReturnPosition.clear();
        loadData();
    }

    public static void clearWorldData() {
        playerMoneyMap.clear();
        totalSoldVolume.clear();
        editors.clear();
        activeEffects.clear();
        playerAbilities.clear();
        playerSkills.clear();
        unlockedHousesMap.clear();
        playerAttributeBonuses.clear();
        playerReturnDimension.clear();
        playerReturnPosition.clear();
        DATA_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "store_data.json"); // Reset to default
    }

    private static void loadFormulas() {
        if (!FORMULA_FILE.exists()) {
            saveFormulas();
            return;
        }
        try (FileReader reader = new FileReader(FORMULA_FILE)) {
            FormulaConfig loaded = GSON.fromJson(reader, FormulaConfig.class);
            if (loaded != null) formulas = loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveFormulas() {
        try (FileWriter writer = new FileWriter(FORMULA_FILE)) {
            GSON.toJson(formulas, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadPrices() {
        if (!PRICES_FILE.exists()) {
            savePrices();
            return;
        }
        try (FileReader reader = new FileReader(PRICES_FILE)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            
            if (jsonObject.has("itemsBuy") || jsonObject.has("itemsSell") || jsonObject.has("enchantments") || jsonObject.has("effects") || jsonObject.has("edited")) {
                if (jsonObject.has("itemsBuy")) {
                    Type type = new TypeToken<Map<String, BigDecimal>>(){}.getType();
                    Map<String, BigDecimal> loaded = GSON.fromJson(jsonObject.get("itemsBuy"), type);
                    if (loaded != null) itemBuyPrices = loaded;
                }
                if (jsonObject.has("itemsSell")) {
                    Type type = new TypeToken<Map<String, BigDecimal>>(){}.getType();
                    Map<String, BigDecimal> loaded = GSON.fromJson(jsonObject.get("itemsSell"), type);
                    if (loaded != null) itemSellPrices = loaded;
                }
                if (jsonObject.has("enchantments")) {
                    Type type = new TypeToken<Map<String, BigDecimal>>(){}.getType();
                    Map<String, BigDecimal> loaded = GSON.fromJson(jsonObject.get("enchantments"), type);
                    if (loaded != null) enchantPrices = loaded;
                }
                if (jsonObject.has("effects")) {
                    Type type = new TypeToken<Map<String, BigDecimal>>(){}.getType();
                    Map<String, BigDecimal> loaded = GSON.fromJson(jsonObject.get("effects"), type);
                    if (loaded != null) effectBasePrices = loaded;
                }
                if (jsonObject.has("edited")) {
                    Type type = new TypeToken<Set<String>>(){}.getType();
                    Set<String> loaded = GSON.fromJson(jsonObject.get("edited"), type);
                    if (loaded != null) editedPrices = loaded;
                }
            } else if (jsonObject.has("items")) {
                Type type = new TypeToken<Map<String, BigDecimal>>(){}.getType();
                Map<String, BigDecimal> loaded = GSON.fromJson(jsonObject.get("items"), type);
                if (loaded != null) {
                    itemBuyPrices = new HashMap<>(loaded);
                    itemSellPrices = new HashMap<>(loaded);
                }
            } else {
                Type type = new TypeToken<Map<String, BigDecimal>>(){}.getType();
                Map<String, BigDecimal> loaded = GSON.fromJson(jsonObject, type);
                if (loaded != null) {
                    itemBuyPrices = new HashMap<>(loaded);
                    itemSellPrices = new HashMap<>(loaded);
                }
            }
        } catch (Exception e) {
            System.err.println("[StorePriceManager] Failed to load prices: " + e.getMessage());
        }
        DefaultPrices.populate(itemBuyPrices);
        DefaultPrices.populate(itemSellPrices);
        initEffectPrices();
    }

    private static void initEffectPrices() {
        if (effectBasePrices.isEmpty()) {
            effectBasePrices.put("minecraft:speed", BigDecimal.valueOf(10));
            effectBasePrices.put("minecraft:strength", BigDecimal.valueOf(50));
            effectBasePrices.put("minecraft:resistance", BigDecimal.valueOf(40));
            effectBasePrices.put("minecraft:regeneration", BigDecimal.valueOf(60));
            effectBasePrices.put("minecraft:night_vision", BigDecimal.valueOf(5));
            effectBasePrices.put("minecraft:haste", BigDecimal.valueOf(20));
            effectBasePrices.put("minecraft:fire_resistance", BigDecimal.valueOf(15));
            effectBasePrices.put("minecraft:invisibility", BigDecimal.valueOf(25));
            effectBasePrices.put("minecraft:water_breathing", BigDecimal.valueOf(10));
            effectBasePrices.put("minecraft:luck", BigDecimal.valueOf(20));
            effectBasePrices.put("minecraft:health_boost", BigDecimal.valueOf(50));
            effectBasePrices.put("minecraft:absorption", BigDecimal.valueOf(30));
            effectBasePrices.put("minecraft:jump_boost", BigDecimal.valueOf(10));
            effectBasePrices.put("minecraft:slow_falling", BigDecimal.valueOf(10));
            effectBasePrices.put("minecraft:saturation", BigDecimal.valueOf(100));
        }
    }

    public static void resetToDefaults(net.minecraft.world.level.Level level) {
        editedPrices.clear();
        itemBuyPrices.clear();
        itemSellPrices.clear();
        enchantPrices.clear();
        addAllItems(level);
    }

    public static void addAllEffects() {
        BigDecimal maxPrice = BigDecimal.ZERO;
        for (BigDecimal price : effectBasePrices.values()) {
            if (price.compareTo(maxPrice) > 0) maxPrice = price;
        }
        if (maxPrice.compareTo(BigDecimal.ZERO) <= 0) maxPrice = BigDecimal.valueOf(100);

        boolean added = false;
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) {
            String id = ForgeRegistries.MOB_EFFECTS.getKey(effect).toString();
            if (editedPrices.contains("effect:" + id)) continue;
            
            if (!effectBasePrices.containsKey(id)) {
                effectBasePrices.put(id, maxPrice);
                added = true;
            }
        }
        if (added) savePrices();
    }

    private static void loadData() {
        if (!DATA_FILE.exists()) {
            saveData();
            return;
        }
        try (FileReader reader = new FileReader(DATA_FILE)) {
            PlayerData data = GSON.fromJson(reader, PlayerData.class);
            if (data != null) {
                if (data.money != null) {
                    data.money.forEach((uuidStr, money) -> {
                        try { playerMoneyMap.put(UUID.fromString(uuidStr), money); } catch (Exception ignored) {}
                    });
                }
                if (data.editors != null) {
                    data.editors.forEach(uuidStr -> {
                        try { editors.add(UUID.fromString(uuidStr)); } catch (Exception ignored) {}
                    });
                }
                if (data.activeEffects != null) {
                    data.activeEffects.forEach((uuidStr, effects) -> {
                        try { activeEffects.put(UUID.fromString(uuidStr), effects); } catch (Exception ignored) {}
                    });
                }
                if (data.abilities != null) {
                    data.abilities.forEach((uuidStr, ability) -> {
                        try { playerAbilities.put(UUID.fromString(uuidStr), ability); } catch (Exception ignored) {}
                    });
                }
                if (data.skills != null) {
                    data.skills.forEach((uuidStr, skill) -> {
                        try { playerSkills.put(UUID.fromString(uuidStr), skill); } catch (Exception ignored) {}
                    });
                }
                if (data.unlockedHouses != null) {
                    data.unlockedHouses.forEach((uuidStr, houses) -> {
                        try { unlockedHousesMap.put(UUID.fromString(uuidStr), new HashSet<>(houses)); } catch (Exception ignored) {}
                    });
                }
                if (data.attributeBonuses != null) {
                    data.attributeBonuses.forEach((uuidStr, bonuses) -> {
                        try { playerAttributeBonuses.put(UUID.fromString(uuidStr), bonuses); } catch (Exception ignored) {}
                    });
                }
                if (data.returnDimension != null) {
                    data.returnDimension.forEach((uuidStr, dim) -> {
                        try { playerReturnDimension.put(UUID.fromString(uuidStr), dim); } catch (Exception ignored) {}
                    });
                }
                if (data.returnPosition != null) {
                    data.returnPosition.forEach((uuidStr, pos) -> {
                        try { playerReturnPosition.put(UUID.fromString(uuidStr), pos); } catch (Exception ignored) {}
                    });
                }
                if (data.soldVolume != null) {
                    totalSoldVolume = data.soldVolume;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        savePrices();
        saveFormulas();
        saveData();
    }

    private static void savePrices() {
        try (FileWriter writer = new FileWriter(PRICES_FILE)) {
            StorePrices sp = new StorePrices();
            sp.itemsBuy = itemBuyPrices;
            sp.itemsSell = itemSellPrices;
            sp.enchantments = enchantPrices;
            sp.effects = effectBasePrices;
            sp.edited = editedPrices;
            GSON.toJson(sp, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveData() {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            PlayerData data = new PlayerData();
            playerMoneyMap.forEach((uuid, money) -> data.money.put(uuid.toString(), money));
            editors.forEach(uuid -> data.editors.add(uuid.toString()));
            activeEffects.forEach((uuid, effects) -> data.activeEffects.put(uuid.toString(), effects));
            playerAbilities.forEach((uuid, ability) -> data.abilities.put(uuid.toString(), ability));
            playerSkills.forEach((uuid, skill) -> data.skills.put(uuid.toString(), skill));
            unlockedHousesMap.forEach((uuid, houses) -> data.unlockedHouses.put(uuid.toString(), new ArrayList<>(houses)));
            playerAttributeBonuses.forEach((uuid, bonuses) -> data.attributeBonuses.put(uuid.toString(), bonuses));
            playerReturnDimension.forEach((uuid, dim) -> data.returnDimension.put(uuid.toString(), dim));
            playerReturnPosition.forEach((uuid, pos) -> data.returnPosition.put(uuid.toString(), pos));
            data.soldVolume = totalSoldVolume;
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static BigDecimal getMoney(UUID uuid) {
        return playerMoneyMap.getOrDefault(uuid, BigDecimal.ZERO);
    }

    public static void setMoney(UUID uuid, BigDecimal amount) {
        playerMoneyMap.put(uuid, amount);
        saveData();
    }

    public static void addMoney(UUID uuid, long amount) {
        addMoney(uuid, BigDecimal.valueOf(amount));
    }

    public static void addMoney(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            SkillData skillData = getSkills(uuid);
            // 1. Currency Singularity (Branch A)
            int singularityRank = SkillManager.getActiveRank(skillData, "WEALTH_SINGULARITY");
            if (singularityRank > 0) {
                BigDecimal balance = getMoney(uuid);
                BigDecimal divisor = new BigDecimal("10000000000"); // 10B
                BigDecimal threshold = new BigDecimal("100000000000000"); // 100T
                
                BigDecimal multiplier;
                if (balance.compareTo(threshold) <= 0) {
                    multiplier = BigDecimal.ONE.add(balance.divide(divisor, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(singularityRank)));
                } else {
                    // Beyond 100T, slow down using square root
                    BigDecimal multiplierAtThreshold = BigDecimal.ONE.add(threshold.divide(divisor, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(singularityRank)));
                    BigDecimal excess = balance.subtract(threshold);
                    double excessSqrt = Math.sqrt(excess.divide(divisor, 10, RoundingMode.HALF_UP).doubleValue());
                    multiplier = multiplierAtThreshold.add(BigDecimal.valueOf(excessSqrt).multiply(BigDecimal.valueOf(singularityRank)));
                }
                amount = amount.multiply(multiplier);
            }
            // 2. Wealth Overflow (Interest Keystone)
            if (SkillManager.getActiveRank(skillData, "WEALTH_INTEREST_UNLIMIT") > 0) {
                amount = amount.multiply(BigDecimal.valueOf(2));
            }
        }
        setMoney(uuid, getMoney(uuid).add(amount));
    }

    public static boolean canAfford(UUID uuid, long cost) {
        return canAfford(uuid, BigDecimal.valueOf(cost));
    }

    public static boolean canAfford(UUID uuid, BigDecimal cost) {
        SkillData skillData = getSkills(uuid);
        int creditRank = SkillManager.getActiveRank(skillData, "WEALTH_INFINITE_CREDIT");
        if (creditRank >= 3) return true; // Absolute Debt
        
        BigDecimal money = getMoney(uuid);
        if (creditRank == 1 && money.subtract(cost).compareTo(new BigDecimal("-1000000000")) >= 0) return true;
        if (creditRank == 2 && money.subtract(cost).compareTo(new BigDecimal("-100000000000")) >= 0) return true;
        
        return money.compareTo(cost) >= 0;
    }

    public static boolean isEditor(UUID uuid) {
        return editors.contains(uuid);
    }

    public static void setEditor(UUID uuid, boolean editor) {
        if (editor) editors.add(uuid);
        else editors.remove(uuid);
        saveData();
    }

    public static Map<String, Integer> getActiveEffects(UUID uuid) {
        return activeEffects.computeIfAbsent(uuid, k -> new HashMap<>());
    }

    public static void toggleEffect(UUID uuid, String effectId, int level) {
        Map<String, Integer> effects = activeEffects.computeIfAbsent(uuid, k -> new HashMap<>());
        if (level <= 0) {
            effects.remove(effectId);
        } else {
            effects.put(effectId, level);
        }
        saveData();
    }

    public static BigDecimal getEffectBasePrice(String effectId) {
        return effectBasePrices.getOrDefault(effectId, BigDecimal.valueOf(50));
    }

    public static Map<String, BigDecimal> getAllEffectBasePrices() {
        if (net.minecraftforge.api.distmarker.Dist.CLIENT == net.minecraftforge.fml.loading.FMLEnvironment.dist && !clientEffectPrices.isEmpty()) {
            return clientEffectPrices;
        }
        return effectBasePrices;
    }

    public static void setEffectBasePrice(String effectId, BigDecimal price) {
        effectBasePrices.put(effectId, price);
        editedPrices.add("effect:" + effectId);
        savePrices();
    }

    public static BigDecimal getBuyPrice(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        Map<String, BigDecimal> buyPrices = (net.minecraftforge.api.distmarker.Dist.CLIENT == net.minecraftforge.fml.loading.FMLEnvironment.dist && !clientBuyPrices.isEmpty()) ? clientBuyPrices : itemBuyPrices;
        
        // Absolute Monopoly Keystone (Wealth Branch)
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            if (clientSkills.activeToggles.contains("WEALTH_KEYSTONE_MONOPOLY")) return BigDecimal.ZERO;
        }

        if (buyPrices.containsKey(id)) {
            BigDecimal price = buyPrices.get(id);
            if (price.compareTo(BigDecimal.ZERO) > 0) return applyBuyDiscounts(price);
        }

        for (Map.Entry<String, BigDecimal> entry : buyPrices.entrySet()) {
            if (entry.getKey().startsWith("tag:")) {
                String tagId = entry.getKey().substring(4);
                if (hasTag(item, tagId)) {
                    return applyBuyDiscounts(entry.getValue());
                }
            }
        }
        return applyBuyDiscounts(BigDecimal.valueOf(100));
    }

    private static BigDecimal applyBuyDiscounts(BigDecimal price) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            int inflationRank = SkillManager.getActiveRank(clientSkills, "WEALTH_INFLATION_CONTROL");
            if (inflationRank > 0) {
                price = price.multiply(BigDecimal.valueOf(1.0 - 0.02 * inflationRank));
            }
        }
        return price.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : price.setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal getSellPrice(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        Map<String, BigDecimal> sellPrices = (net.minecraftforge.api.distmarker.Dist.CLIENT == net.minecraftforge.fml.loading.FMLEnvironment.dist && !clientSellPrices.isEmpty()) ? clientSellPrices : itemSellPrices;
        
        BigDecimal baseSellPrice = BigDecimal.valueOf(10);
        if (sellPrices.containsKey(id)) {
            baseSellPrice = sellPrices.get(id);
        } else {
            for (Map.Entry<String, BigDecimal> entry : sellPrices.entrySet()) {
                if (entry.getKey().startsWith("tag:")) {
                    String tagId = entry.getKey().substring(4);
                    if (hasTag(item, tagId)) {
                        baseSellPrice = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (baseSellPrice.compareTo(BigDecimal.ZERO) <= 0) baseSellPrice = BigDecimal.valueOf(10);

        // Market Manipulation (Branch A)
        // Client-side only for UI display, or handled in packets for server-side
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            int manipulationRank = SkillManager.getActiveRank(clientSkills, "WEALTH_MARKET_MANIPULATION");
            if (manipulationRank > 0) {
                baseSellPrice = baseSellPrice.multiply(BigDecimal.valueOf(1.0 + 0.5 * manipulationRank));
                if (manipulationRank >= 10) return baseSellPrice.setScale(0, RoundingMode.HALF_UP); // Ignore decay
            }
        }

        // Dynamic Economy Algorithm (Supply and Demand)
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER || 
            net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            
            Map<String, Long> volumes = (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) ? clientSoldVolume : totalSoldVolume;
            long volume = volumes.getOrDefault(id, 0L);
            if (volume > 1000) {
                // Drop price exponentially: P = P0 * 0.95 ^ (volume / 1000)
                double factor = Math.pow(0.95, volume / 1000.0);
                baseSellPrice = baseSellPrice.multiply(BigDecimal.valueOf(factor));
                return baseSellPrice.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : baseSellPrice.setScale(0, RoundingMode.HALF_UP);
            }
        }

        return baseSellPrice.setScale(0, RoundingMode.HALF_UP);
    }

    public static void recordSale(Item item, java.math.BigDecimal count) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        long oldVolume = totalSoldVolume.getOrDefault(id, 0L);
        long newVolume = oldVolume + count.longValue();
        totalSoldVolume.put(id, newVolume);
        
        if (newVolume / 100 > oldVolume / 100) { // Save every 100
            saveData();
        }
        
        if (newVolume / 10000 > oldVolume / 10000) { // Sync every 10k
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                    syncPrices(player);
                }
            }
        }
    }

    public static BigDecimal getPrice(Item item) {
        return getBuyPrice(item);
    }

    private static BigDecimal getInternalPrice(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        if (itemBuyPrices.containsKey(id)) {
            BigDecimal price = itemBuyPrices.get(id);
            if (price.compareTo(BigDecimal.ZERO) > 0) return price;
        }
        for (Map.Entry<String, BigDecimal> entry : itemBuyPrices.entrySet()) {
            if (entry.getKey().startsWith("tag:")) {
                String tagId = entry.getKey().substring(4);
                if (hasTag(item, tagId)) {
                    return entry.getValue();
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static boolean hasTag(Item item, String tagId) {
        try {
            return item.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().toString().equals(tagId));
        } catch (Exception e) {
            return false;
        }
    }

    public static void setPrice(Item item, BigDecimal price) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        itemBuyPrices.put(id, price);
        itemSellPrices.put(id, idToSellPrice(id, price));
        editedPrices.add(id);
        savePrices();
    }

    public static boolean isOre(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        return id.endsWith("_ore") || (id.contains("deepslate_") && id.contains("_ore")) || id.equals("minecraft:ancient_debris");
    }

    public static List<String> getDefaultBlacklist() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:oak_log");
        list.add("minecraft:spruce_log");
        list.add("minecraft:birch_log");
        list.add("minecraft:jungle_log");
        list.add("minecraft:acacia_log");
        list.add("minecraft:dark_oak_log");
        list.add("minecraft:mangrove_log");
        list.add("minecraft:cherry_log");
        list.add("minecraft:oak_leaves");
        list.add("minecraft:spruce_leaves");
        list.add("minecraft:birch_leaves");
        list.add("minecraft:jungle_leaves");
        list.add("minecraft:acacia_leaves");
        list.add("minecraft:dark_oak_leaves");
        list.add("minecraft:mangrove_leaves");
        list.add("minecraft:cherry_leaves");
        list.add("minecraft:dandelion");
        list.add("minecraft:poppy");
        list.add("minecraft:blue_orchid");
        list.add("minecraft:allium");
        list.add("minecraft:azure_bluet");
        list.add("minecraft:red_tulip");
        list.add("minecraft:orange_tulip");
        list.add("minecraft:white_tulip");
        list.add("minecraft:pink_tulip");
        list.add("minecraft:oxeye_daisy");
        list.add("minecraft:cornflower");
        list.add("minecraft:lily_of_the_valley");
        list.add("minecraft:sunflower");
        list.add("minecraft:lilac");
        list.add("minecraft:rose_bush");
        list.add("minecraft:peony");
        list.add("minecraft:grass");
        list.add("minecraft:tall_grass");
        list.add("minecraft:fern");
        list.add("minecraft:large_fern");
        return list;
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount.abs().compareTo(new BigDecimal("1000000000000000")) >= 0) {
            return String.format(java.util.Locale.US, "%.2e", amount);
        }
        return java.text.NumberFormat.getInstance(java.util.Locale.GERMANY).format(amount);
    }

    public static String formatCurrency(long amount) {
        return formatCurrency(BigDecimal.valueOf(amount));
    }

    public static BigDecimal getDrain(UUID uuid) {
        SkillData skillData = getSkills(uuid);
        Map<String, Integer> active = getActiveEffects(uuid);
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entry : active.entrySet()) {
            BigDecimal basePrice = getEffectBasePrice(entry.getKey());
            int level = entry.getValue();
            // Use BigDecimal.pow for safety against large levels
            totalCost = totalCost.add(basePrice.multiply(BigDecimal.valueOf(2).pow(level - 1)));
        }
        
        AbilitySettings abilities = getAbilities(uuid);
        if (abilities.chestHighlightActive) {
            totalCost = totalCost.add(BigDecimal.valueOf(abilities.chestHighlightRange).multiply(formulas.chestHighlightMaintenancePerRange));
        }
        if (abilities.trapHighlightActive) {
            totalCost = totalCost.add(BigDecimal.valueOf(abilities.trapHighlightRange).multiply(formulas.trapHighlightMaintenancePerRange));
        }
        if (abilities.entityESPActive) {
            totalCost = totalCost.add(BigDecimal.valueOf(abilities.entityESPRange).multiply(formulas.entityESPMaintenancePerRange));
        }
        if (abilities.damageCancelActive) {
            totalCost = totalCost.add(formulas.damageCancelMaintenance);
        }
        if (abilities.repairActive) {
            totalCost = totalCost.add(formulas.repairMaintenance);
        }
        if (abilities.itemMagnetActive) {
            totalCost = totalCost.add(BigDecimal.valueOf(abilities.itemMagnetRange).multiply(BigDecimal.valueOf(abilities.itemMagnetOpsPerTick)).multiply(formulas.itemMagnetMaintenancePerRangeOps));
        }
        if (abilities.xpMagnetActive) {
            totalCost = totalCost.add(BigDecimal.valueOf(abilities.xpMagnetRange).multiply(BigDecimal.valueOf(abilities.xpMagnetOpsPerTick)).multiply(formulas.xpMagnetMaintenancePerRangeOps));
        }
        if (abilities.autoSellerActive) {
            totalCost = totalCost.add(formulas.autoSellerMaintenance);
        }
        if (abilities.flightActive) {
            totalCost = totalCost.add(formulas.flightCostPerSecond);
        }
        if (abilities.noAggroActive) {
            totalCost = totalCost.add(formulas.noAggroMaintenance);
        }
        if (abilities.spawnBoostActive) {
            totalCost = totalCost.add(formulas.spawnBoostMaintenance);
        }
        if (abilities.growCropsActive) {
            totalCost = totalCost.add(formulas.growCropsMaintenance);
        }

        // Virtual Containment Drain
        for (ChamberData chamber : skillData.chambers) {
            if (chamber.paused) continue;
            BigDecimal chamberBaseDrain = chamber.isExcavation ? BigDecimal.valueOf(5000) : BigDecimal.valueOf(100);
            
            // Scale by speed and threads
            double speedFactor = Math.pow(1.5, chamber.speedSlider - 1);
            double threadFactor = Math.pow(1.2, chamber.threadSlider - 1);
            
            totalCost = totalCost.add(chamberBaseDrain.multiply(BigDecimal.valueOf(speedFactor)).multiply(BigDecimal.valueOf(threadFactor)));
        }

        // Efficiency Core (Branch B)
        int efficiencyRank = SkillManager.getActiveRank(skillData, "COMBAT_EFFICIENCY_CORE");
        if (efficiencyRank > 0) {
            double reduction = 0.2 * efficiencyRank; // 20% per rank
            if (efficiencyRank >= 5 && getMoney(uuid).compareTo(new BigDecimal("500000000")) >= 0) {
                reduction = 1.0; // 100% reduction (Zero Point Energy)
            }
            totalCost = totalCost.multiply(BigDecimal.valueOf(1.0 - reduction)).setScale(0, RoundingMode.HALF_UP);
        }

        return totalCost;
    }

    public static SkillData getSkills(UUID uuid) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            return clientSkills;
        }
        return playerSkills.computeIfAbsent(uuid, k -> new SkillData());
    }

    public static BigDecimal getBranchMultiplier(UUID uuid, String branch) {
        SkillData data = getSkills(uuid);
        int index = data.branchOrder.indexOf(branch);
        if (index < 0) return BigDecimal.ONE;
        return BigDecimal.TEN.pow(index);
    }

    public static AbilitySettings getAbilities(UUID uuid) {
        return playerAbilities.computeIfAbsent(uuid, k -> {
            AbilitySettings s = new AbilitySettings();
            s.miningBlacklist.addAll(getDefaultBlacklist());
            return s;
        });
    }

    public static Map<String, Double> getAttributeBonuses(UUID uuid) {
        return playerAttributeBonuses.computeIfAbsent(uuid, k -> new HashMap<>());
    }

    public static void setAttributeBonus(UUID uuid, String attribute, double value) {
        getAttributeBonuses(uuid).put(attribute, value);
        saveData();
    }

    public static void applyAllAttributes(net.minecraft.server.level.ServerPlayer player) {
        Map<String, Double> baseBonuses = getAttributeBonuses(player.getUUID());
        SkillData skillData = getSkills(player.getUUID());
        BigDecimal money = getMoney(player.getUUID());

        // All attributes that can be modified by the mod
        String[] trackedAttributes = {
            "minecraft:generic.max_health",
            "minecraft:generic.movement_speed",
            "minecraft:generic.attack_damage",
            "minecraft:generic.attack_speed",
            "forge:reach_distance"
        };

        for (String id : trackedAttributes) {
            // 1. Apply Base Attribute Upgrades (isSkill = false)
            double baseBonus = baseBonuses.getOrDefault(id, 0.0);
            applyAttribute(player, id, baseBonus, false);

            // 2. Aggregate Skill-based Bonuses (isSkill = true)
            double skillBonus = 0;

            // Soul Reap Permanent Stats
            skillBonus += skillData.permanentAttributes.getOrDefault(id, BigDecimal.ZERO).doubleValue();

            // Neural Link (Combat Branch)
            int neuralRank = SkillManager.getActiveRank(skillData, "COMBAT_NEURAL_LINK");
            if (neuralRank > 0) {
                double billions = money.divide(BD_BILLION, 10, RoundingMode.HALF_UP).doubleValue();
                if (billions > 0) {
                    if (id.equals("minecraft:generic.movement_speed")) skillBonus += billions * 0.1 * neuralRank;
                    if (id.equals("minecraft:generic.attack_speed")) skillBonus += billions * 0.1 * neuralRank;
                    if (id.equals("forge:reach_distance")) skillBonus += billions * neuralRank;
                }
            }

            // Economic Ego (Wealth Branch)
            int egoRank = SkillManager.getActiveRank(skillData, "WEALTH_ECONOMIC_EGO");
            if (egoRank > 0) {
                int zeroes = 0;
                String s = money.toPlainString();
                for (char c : s.toCharArray()) if (c == '0') zeroes++;
                if (zeroes > 0) {
                    if (id.equals("minecraft:generic.movement_speed")) skillBonus += zeroes * 0.05 * egoRank;
                    if (id.equals("minecraft:generic.attack_damage")) skillBonus += zeroes * 2.0 * egoRank;
                    if (id.equals("forge:reach_distance")) skillBonus += zeroes * 5.0 * egoRank;
                }
            }

            // Exponential Bounty (Wealth Branch) - Combat Dividend
            int bountyRank = SkillManager.getActiveRank(skillData, "WEALTH_EXPONENTIAL_BOUNTY");
            if (bountyRank > 0) {
                if (id.equals("minecraft:generic.attack_damage")) skillBonus += 2.0 * bountyRank;
            }

            // System Sovereignty (Combat Keystone)
            if (skillData.activeToggles.contains("COMBAT_KEYSTONE_SOVEREIGNTY") && money.compareTo(BD_BILLION) >= 0) {
                if (id.equals("minecraft:generic.movement_speed")) skillBonus += 0.5;
                if (id.equals("forge:reach_distance")) skillBonus += 100.0;
            }

            // System Overclock (Utility Branch)
            int overclockRank = SkillManager.getActiveRank(skillData, "UTILITY_SYSTEM_OVERCLOCK");
            if (overclockRank > 0) {
                if (id.equals("minecraft:generic.attack_speed")) {
                    double multiplier = Math.pow(2, overclockRank);
                    // Base attack speed is 4.0.
                    skillBonus += (multiplier - 1) * 4.0;
                }
            }

            // Apply aggregate skill bonus (correctly handles removal if skillBonus == 0)
            // Engine Safety Protocols (Hard Caps & Infinity Guard)
            if (Double.isInfinite(skillBonus)) skillBonus = 1e18;

            if (id.equals("forge:reach_distance")) skillBonus = Math.min(skillBonus, 16.0);
            if (id.equals("minecraft:generic.attack_speed")) skillBonus = Math.min(skillBonus, 20.0);
            if (id.equals("minecraft:generic.movement_speed")) skillBonus = Math.min(skillBonus, 1.0);
            if (id.equals("minecraft:generic.attack_damage")) skillBonus = Math.min(skillBonus, 1e15);
            if (id.equals("minecraft:generic.max_health")) skillBonus = Math.min(skillBonus, 1e12);

            applyAttribute(player, id, skillBonus, true);
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void applyAttribute(net.minecraft.server.level.ServerPlayer player, String id, double bonus, boolean isSkill) {
        net.minecraft.world.entity.ai.attributes.Attribute attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(net.minecraft.resources.ResourceLocation.tryParse(id));
        if (attr != null) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                java.util.UUID modifierUuid = java.util.UUID.nameUUIDFromBytes(("modmenu_" + (isSkill ? "skill_" : "attr_") + id).getBytes());
                inst.removeModifier(modifierUuid);
                if (bonus != 0) {
                    double actualBonus = bonus;
                    if (id.equals("minecraft:generic.movement_speed")) {
                        actualBonus = isSkill ? bonus : bonus * 0.01;
                    }
                    if (id.equals("minecraft:generic.max_health")) {
                        actualBonus = isSkill ? bonus : bonus * 2.0;
                    }
                    
                    inst.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(modifierUuid, "ModMenu Bonus", actualBonus, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                }
            }
        }
    }

    public static void setAbilities(UUID uuid, AbilitySettings settings) {
        playerAbilities.put(uuid, settings);
        saveData();
    }

    public static void sync(net.minecraft.server.level.ServerPlayer player) {
        com.example.modmenu.network.PacketHandler.sendToPlayer(
            new com.example.modmenu.network.SyncMoneyPacket(
                getMoney(player.getUUID()), 
                isEditor(player.getUUID()), 
                getActiveEffects(player.getUUID()),
                getDrain(player.getUUID()),
                getAbilities(player.getUUID()),
                getUnlockedHouses(player.getUUID()),
                getAttributeBonuses(player.getUUID())
            ), 
            player
        );
        com.example.modmenu.network.PacketHandler.sendToPlayer(
            new com.example.modmenu.network.SyncSkillsPacket(getSkills(player.getUUID())),
            player
        );
    }

    public static void syncPrices(net.minecraft.server.level.ServerPlayer player) {
        com.example.modmenu.network.PacketHandler.sendToPlayer(
            new com.example.modmenu.network.SyncPricesPacket(
                itemBuyPrices,
                itemSellPrices,
                totalSoldVolume,
                enchantPrices,
                effectBasePrices
            ),
            player
        );
    }

    public static Set<String> getUnlockedHouses(UUID uuid) {
        return unlockedHousesMap.computeIfAbsent(uuid, k -> new HashSet<>());
    }

    public static void unlockHouse(UUID uuid, String houseId) {
        getUnlockedHouses(uuid).add(houseId);
        saveData();
    }

    public static boolean isHouseUnlocked(UUID uuid, String houseId) {
        return getUnlockedHouses(uuid).contains(houseId);
    }

    public static void setReturnPoint(UUID uuid, String dimension, double x, double y, double z) {
        playerReturnDimension.put(uuid, dimension);
        playerReturnPosition.put(uuid, new double[]{x, y, z});
        saveData();
    }

    public static String getReturnDimension(UUID uuid) {
        return playerReturnDimension.get(uuid);
    }

    public static double[] getReturnPosition(UUID uuid) {
        return playerReturnPosition.get(uuid);
    }

    public static BigDecimal getEnchantPrice(Enchantment enchantment) {
        String id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment).toString();
        return enchantPrices.getOrDefault(id, BigDecimal.valueOf(2000));
    }

    public static void setEnchantPrice(Enchantment enchantment, BigDecimal price) {
        String id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment).toString();
        enchantPrices.put(id, price);
        editedPrices.add("enchant:" + id);
        savePrices();
    }

    public static Map<String, BigDecimal> getAllPrices() {
        return itemBuyPrices;
    }
    
    public static Map<String, BigDecimal> getAllBuyPrices() { return itemBuyPrices; }
    public static Map<String, BigDecimal> getAllSellPrices() { return itemSellPrices; }

    public static Map<String, BigDecimal> getAllEnchantPrices() {
        if (net.minecraftforge.api.distmarker.Dist.CLIENT == net.minecraftforge.fml.loading.FMLEnvironment.dist && !clientEnchantPrices.isEmpty()) {
            return clientEnchantPrices;
        }
        return enchantPrices;
    }

    public static void addAllEnchantments() {
        for (Enchantment enchant : ForgeRegistries.ENCHANTMENTS) {
            String id = ForgeRegistries.ENCHANTMENTS.getKey(enchant).toString();
            if (editedPrices.contains("enchant:" + id)) continue;
            
            if (!enchantPrices.containsKey(id)) {
                BigDecimal price = BigDecimal.valueOf(2000);
                if (id.contains("sharpness") || id.contains("protection") || id.contains("unbreaking")) {
                    price = BigDecimal.valueOf(5000);
                } else if (id.contains("mending") || id.contains("infinity") || id.contains("silk_touch")) {
                    price = BigDecimal.valueOf(20000);
                } else if (id.contains("efficiency") || id.contains("fortune") || id.contains("looting")) {
                    price = BigDecimal.valueOf(8000);
                }
                enchantPrices.put(id, price);
            }
        }
        savePrices();
    }


    private static void estimatePricesByRarity() {
        Map<net.minecraft.world.item.Rarity, List<BigDecimal>> rarityPrices = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            String id = ForgeRegistries.ITEMS.getKey(item).toString();
            BigDecimal price = itemBuyPrices.getOrDefault(id, BigDecimal.ZERO);
            if (price.compareTo(BigDecimal.ZERO) > 0) {
                net.minecraft.world.item.Rarity rarity = item.getRarity(new ItemStack(item));
                rarityPrices.computeIfAbsent(rarity, k -> new ArrayList<>()).add(price);
            }
        }

        Map<net.minecraft.world.item.Rarity, BigDecimal> avgPrices = new HashMap<>();
        rarityPrices.forEach((rarity, prices) -> {
            if (!prices.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal p : prices) sum = sum.add(p);
                avgPrices.put(rarity, sum.divide(BigDecimal.valueOf(prices.size()), 0, RoundingMode.HALF_UP));
            }
        });

        // Default averages if none found
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.COMMON, BigDecimal.valueOf(10));
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.UNCOMMON, BigDecimal.valueOf(100));
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.RARE, BigDecimal.valueOf(1000));
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.EPIC, BigDecimal.valueOf(10000));

        for (Item item : ForgeRegistries.ITEMS) {
            String id = ForgeRegistries.ITEMS.getKey(item).toString();
            if (!itemBuyPrices.containsKey(id) || itemBuyPrices.get(id).compareTo(BigDecimal.ZERO) <= 0) {
                net.minecraft.world.item.Rarity rarity = item.getRarity(new ItemStack(item));
                BigDecimal price = avgPrices.getOrDefault(rarity, BigDecimal.valueOf(10));
                itemBuyPrices.put(id, price);
                itemSellPrices.put(id, id.startsWith("minecraft:") ? price : BigDecimal.valueOf(10));
            }
        }
    }

    public static void addAllItems(net.minecraft.world.level.Level level) {
        if (level == null) return;

        pricingEngine.computeAllPrices(level);
        Map<String, BigDecimal> newPrices = pricingEngine.getPrices();

        // Keep edited prices
        Map<String, BigDecimal> savedEditedBuy = new HashMap<>();
        Map<String, BigDecimal> savedEditedSell = new HashMap<>();
        for (String id : editedPrices) {
            if (!id.startsWith("enchant:")) {
                if (itemBuyPrices.containsKey(id)) savedEditedBuy.put(id, itemBuyPrices.get(id));
                if (itemSellPrices.containsKey(id)) savedEditedSell.put(id, itemSellPrices.get(id));
            }
        }

        itemBuyPrices.clear();
        itemSellPrices.clear();

        for (Map.Entry<String, BigDecimal> entry : newPrices.entrySet()) {
            String id = entry.getKey();
            if (savedEditedBuy.containsKey(id)) {
                itemBuyPrices.put(id, savedEditedBuy.get(id));
                itemSellPrices.put(id, savedEditedSell.get(id));
            } else {
                BigDecimal price = entry.getValue();
                itemBuyPrices.put(id, price);
                itemSellPrices.put(id, idToSellPrice(id, price));
            }
        }

        addAllEnchantments();
        addAllEffects();
        
        savePrices();
    }

    private static boolean isNonSurvivalItem(String id) {
        return id.contains("command_block") || id.equals("minecraft:barrier") || id.equals("minecraft:bedrock") || 
               id.equals("minecraft:structure_block") || id.equals("minecraft:jigsaw") || id.equals("minecraft:structure_void") || 
               id.contains("spawn_egg") || id.contains("debug_stick") || id.equals("minecraft:knowledge_book") ||
               id.equals("minecraft:bundle") || id.contains("operator_only");
    }

    public static BigDecimal idToSellPrice(String id, BigDecimal buyPrice) {
        if (id.startsWith("minecraft:")) return buyPrice;
        return BigDecimal.valueOf(10); // Modded items added via add all can only be sold for 10
    }
}
