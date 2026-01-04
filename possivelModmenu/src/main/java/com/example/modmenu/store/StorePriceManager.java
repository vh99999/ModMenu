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
import java.util.*;

public class StorePriceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File PRICES_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "store_prices.json");
    private static File DATA_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "store_data.json");
    private static final PricingEngine pricingEngine = new PricingEngine();
    
    private static Map<String, Integer> itemBuyPrices = new HashMap<>();
    private static Map<String, Integer> itemSellPrices = new HashMap<>();
    private static Map<String, Integer> enchantPrices = new HashMap<>();
    private static Map<String, Integer> effectBasePrices = new HashMap<>();
    private static Set<String> editedPrices = new HashSet<>();
    private static Map<UUID, Long> playerMoneyMap = new HashMap<>();
    private static Map<UUID, Map<String, Integer>> activeEffects = new HashMap<>();
    private static Map<UUID, AbilitySettings> playerAbilities = new HashMap<>();
    private static Map<UUID, Set<String>> unlockedHousesMap = new HashMap<>();
    private static Map<UUID, Map<String, Double>> playerAttributeBonuses = new HashMap<>();
    private static Map<UUID, String> playerReturnDimension = new HashMap<>();
    private static Map<UUID, double[]> playerReturnPosition = new HashMap<>();
    private static Set<UUID> editors = new HashSet<>();
    
    // Client-side current player info
    public static long playerMoney = 0;
    public static long playerDrain = 0;
    public static boolean isEditor = false;
    public static Map<String, Integer> clientActiveEffects = new HashMap<>();
    public static AbilitySettings clientAbilities = new AbilitySettings();
    public static Set<String> clientUnlockedHouses = new HashSet<>();
    public static Map<String, Integer> clientBuyPrices = new HashMap<>();
    public static Map<String, Integer> clientSellPrices = new HashMap<>();
    public static Map<String, Integer> clientEnchantPrices = new HashMap<>();
    public static Map<String, Integer> clientEffectPrices = new HashMap<>();
    public static Map<String, Double> clientAttributeBonuses = new HashMap<>();

    public static void setClientPrices(Map<String, Integer> buy, Map<String, Integer> sell, Map<String, Integer> enchants, Map<String, Integer> effects) {
        clientBuyPrices = buy;
        clientSellPrices = sell;
        clientEnchantPrices = enchants;
        clientEffectPrices = effects;
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

    public static class FormulaConfig {
        public int stepAssistCostPerAssist = 50;
        public int areaMiningCostBase = 100;
        public int flightCostPerSecond = 500;
        public int sureKillBaseCost = 5000;
        public double sureKillHealthMultiplier = 100.0;
        public int noAggroCostPerCancel = 200;
        public int noAggroMaintenance = 300;
        public double damageCancelMultiplier = 100.0;
        public int damageCancelMaintenance = 50;
        public int repairCostPerPoint = 10;
        public int repairMaintenance = 100;
        public int chestHighlightMaintenancePerRange = 2;
        public int trapHighlightMaintenancePerRange = 2;
        public int entityESPMaintenancePerRange = 2;
        public int itemMagnetMaintenancePerRangeOps = 1;
        public int xpMagnetMaintenancePerRangeOps = 1;
        public int autoSellerMaintenance = 150;
        public int spawnBoostMaintenance = 500;
        public int spawnBoostPerSpawnBase = 1000;
        public int growCropsMaintenance = 200;
        public int growCropsPerOperation = 100;
    }

    public static FormulaConfig formulas = new FormulaConfig();
    private static final File FORMULA_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "store_formulas.json");

    private static class PlayerData {
        Map<String, Long> money = new HashMap<>();
        List<String> editors = new ArrayList<>();
        Map<String, Map<String, Integer>> activeEffects = new HashMap<>();
        Map<String, AbilitySettings> abilities = new HashMap<>();
        Map<String, List<String>> unlockedHouses = new HashMap<>();
        Map<String, Map<String, Double>> attributeBonuses = new HashMap<>();
        Map<String, String> returnDimension = new HashMap<>();
        Map<String, double[]> returnPosition = new HashMap<>();
    }

    private static class StorePrices {
        Map<String, Integer> itemsBuy = new HashMap<>();
        Map<String, Integer> itemsSell = new HashMap<>();
        Map<String, Integer> enchantments = new HashMap<>();
        Map<String, Integer> effects = new HashMap<>();
        Set<String> edited = new HashSet<>();
    }

    public static void load() {
        loadPrices();
        loadFormulas();
    }

    public static void initWorldData(File worldDir) {
        DATA_FILE = new File(worldDir, "store_data.json");
        playerMoneyMap.clear();
        editors.clear();
        activeEffects.clear();
        playerAbilities.clear();
        unlockedHousesMap.clear();
        playerAttributeBonuses.clear();
        playerReturnDimension.clear();
        playerReturnPosition.clear();
        loadData();
    }

    public static void clearWorldData() {
        playerMoneyMap.clear();
        editors.clear();
        activeEffects.clear();
        playerAbilities.clear();
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
                    Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                    Map<String, Integer> loaded = GSON.fromJson(jsonObject.get("itemsBuy"), type);
                    if (loaded != null) itemBuyPrices = loaded;
                }
                if (jsonObject.has("itemsSell")) {
                    Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                    Map<String, Integer> loaded = GSON.fromJson(jsonObject.get("itemsSell"), type);
                    if (loaded != null) itemSellPrices = loaded;
                }
                if (jsonObject.has("enchantments")) {
                    Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                    Map<String, Integer> loaded = GSON.fromJson(jsonObject.get("enchantments"), type);
                    if (loaded != null) enchantPrices = loaded;
                }
                if (jsonObject.has("effects")) {
                    Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                    Map<String, Integer> loaded = GSON.fromJson(jsonObject.get("effects"), type);
                    if (loaded != null) effectBasePrices = loaded;
                }
                if (jsonObject.has("edited")) {
                    Type type = new TypeToken<Set<String>>(){}.getType();
                    Set<String> loaded = GSON.fromJson(jsonObject.get("edited"), type);
                    if (loaded != null) editedPrices = loaded;
                }
            } else if (jsonObject.has("items")) {
                Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loaded = GSON.fromJson(jsonObject.get("items"), type);
                if (loaded != null) {
                    itemBuyPrices = new HashMap<>(loaded);
                    itemSellPrices = new HashMap<>(loaded);
                }
            } else {
                Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loaded = GSON.fromJson(jsonObject, type);
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
            effectBasePrices.put("minecraft:speed", 10);
            effectBasePrices.put("minecraft:strength", 50);
            effectBasePrices.put("minecraft:resistance", 40);
            effectBasePrices.put("minecraft:regeneration", 60);
            effectBasePrices.put("minecraft:night_vision", 5);
            effectBasePrices.put("minecraft:haste", 20);
            effectBasePrices.put("minecraft:fire_resistance", 15);
            effectBasePrices.put("minecraft:invisibility", 25);
            effectBasePrices.put("minecraft:water_breathing", 10);
            effectBasePrices.put("minecraft:luck", 20);
            effectBasePrices.put("minecraft:health_boost", 50);
            effectBasePrices.put("minecraft:absorption", 30);
            effectBasePrices.put("minecraft:jump_boost", 10);
            effectBasePrices.put("minecraft:slow_falling", 10);
            effectBasePrices.put("minecraft:saturation", 100);
            savePrices();
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
        int maxPrice = 0;
        for (int price : effectBasePrices.values()) {
            if (price > maxPrice) maxPrice = price;
        }
        if (maxPrice <= 0) maxPrice = 100;

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
            unlockedHousesMap.forEach((uuid, houses) -> data.unlockedHouses.put(uuid.toString(), new ArrayList<>(houses)));
            playerAttributeBonuses.forEach((uuid, bonuses) -> data.attributeBonuses.put(uuid.toString(), bonuses));
            playerReturnDimension.forEach((uuid, dim) -> data.returnDimension.put(uuid.toString(), dim));
            playerReturnPosition.forEach((uuid, pos) -> data.returnPosition.put(uuid.toString(), pos));
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static long getMoney(UUID uuid) {
        return playerMoneyMap.getOrDefault(uuid, 0L);
    }

    public static void setMoney(UUID uuid, long amount) {
        playerMoneyMap.put(uuid, amount);
        saveData();
    }

    public static void addMoney(UUID uuid, long amount) {
        setMoney(uuid, getMoney(uuid) + amount);
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

    public static int getEffectBasePrice(String effectId) {
        return effectBasePrices.getOrDefault(effectId, 50);
    }

    public static Map<String, Integer> getAllEffectBasePrices() {
        if (net.minecraftforge.api.distmarker.Dist.CLIENT == net.minecraftforge.fml.loading.FMLEnvironment.dist && !clientEffectPrices.isEmpty()) {
            return clientEffectPrices;
        }
        return effectBasePrices;
    }

    public static void setEffectBasePrice(String effectId, int price) {
        effectBasePrices.put(effectId, price);
        editedPrices.add("effect:" + effectId);
        savePrices();
    }

    public static int getBuyPrice(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        Map<String, Integer> buyPrices = (net.minecraftforge.api.distmarker.Dist.CLIENT == net.minecraftforge.fml.loading.FMLEnvironment.dist && !clientBuyPrices.isEmpty()) ? clientBuyPrices : itemBuyPrices;
        if (buyPrices.containsKey(id)) {
            int price = buyPrices.get(id);
            if (price > 0) return price;
        }

        for (Map.Entry<String, Integer> entry : buyPrices.entrySet()) {
            if (entry.getKey().startsWith("tag:")) {
                String tagId = entry.getKey().substring(4);
                if (hasTag(item, tagId)) {
                    return entry.getValue();
                }
            }
        }
        return 100;
    }

    public static int getSellPrice(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        Map<String, Integer> sellPrices = (net.minecraftforge.api.distmarker.Dist.CLIENT == net.minecraftforge.fml.loading.FMLEnvironment.dist && !clientSellPrices.isEmpty()) ? clientSellPrices : itemSellPrices;
        if (sellPrices.containsKey(id)) {
            int price = sellPrices.get(id);
            if (price > 0) return price;
        }

        for (Map.Entry<String, Integer> entry : sellPrices.entrySet()) {
            if (entry.getKey().startsWith("tag:")) {
                String tagId = entry.getKey().substring(4);
                if (hasTag(item, tagId)) {
                    return entry.getValue();
                }
            }
        }
        return 10;
    }

    public static int getPrice(Item item) {
        return getBuyPrice(item);
    }

    private static int getInternalPrice(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        if (itemBuyPrices.containsKey(id)) {
            int price = itemBuyPrices.get(id);
            if (price > 0) return price;
        }
        for (Map.Entry<String, Integer> entry : itemBuyPrices.entrySet()) {
            if (entry.getKey().startsWith("tag:")) {
                String tagId = entry.getKey().substring(4);
                if (hasTag(item, tagId)) {
                    return entry.getValue();
                }
            }
        }
        return 0;
    }

    private static boolean hasTag(Item item, String tagId) {
        try {
            return item.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().toString().equals(tagId));
        } catch (Exception e) {
            return false;
        }
    }

    public static void setPrice(Item item, int price) {
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

    public static String formatCurrency(long amount) {
        return java.text.NumberFormat.getInstance(java.util.Locale.GERMANY).format(amount);
    }

    public static long getDrain(UUID uuid) {
        Map<String, Integer> active = getActiveEffects(uuid);
        long totalCost = 0;
        for (Map.Entry<String, Integer> entry : active.entrySet()) {
            int basePrice = getEffectBasePrice(entry.getKey());
            int level = entry.getValue();
            totalCost += (long) (basePrice * Math.pow(2, level - 1));
        }
        
        AbilitySettings abilities = getAbilities(uuid);
        if (abilities.chestHighlightActive) {
            totalCost += (long) abilities.chestHighlightRange * formulas.chestHighlightMaintenancePerRange;
        }
        if (abilities.trapHighlightActive) {
            totalCost += (long) abilities.trapHighlightRange * formulas.trapHighlightMaintenancePerRange;
        }
        if (abilities.entityESPActive) {
            totalCost += (long) abilities.entityESPRange * formulas.entityESPMaintenancePerRange;
        }
        if (abilities.damageCancelActive) {
            totalCost += formulas.damageCancelMaintenance;
        }
        if (abilities.repairActive) {
            totalCost += formulas.repairMaintenance;
        }
        if (abilities.itemMagnetActive) {
            totalCost += (long) abilities.itemMagnetRange * abilities.itemMagnetOpsPerTick * formulas.itemMagnetMaintenancePerRangeOps;
        }
        if (abilities.xpMagnetActive) {
            totalCost += (long) abilities.xpMagnetRange * abilities.xpMagnetOpsPerTick * formulas.xpMagnetMaintenancePerRangeOps;
        }
        if (abilities.autoSellerActive) {
            totalCost += formulas.autoSellerMaintenance;
        }
        if (abilities.flightActive) {
            totalCost += formulas.flightCostPerSecond;
        }
        if (abilities.noAggroActive) {
            totalCost += formulas.noAggroMaintenance;
        }
        if (abilities.spawnBoostActive) {
            totalCost += formulas.spawnBoostMaintenance;
        }
        if (abilities.growCropsActive) {
            totalCost += formulas.growCropsMaintenance;
        }
        return totalCost;
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
        Map<String, Double> bonuses = getAttributeBonuses(player.getUUID());
        bonuses.forEach((id, bonus) -> applyAttribute(player, id, bonus));
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void applyAttribute(net.minecraft.server.level.ServerPlayer player, String id, double bonus) {
        net.minecraft.world.entity.ai.attributes.Attribute attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(net.minecraft.resources.ResourceLocation.tryParse(id));
        if (attr != null) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                java.util.UUID modifierUuid = java.util.UUID.nameUUIDFromBytes(("modmenu_attr_" + id).getBytes());
                inst.removeModifier(modifierUuid);
                if (bonus != 0) {
                    double actualBonus = bonus;
                    if (id.equals("minecraft:generic.movement_speed")) actualBonus = bonus * 0.01;
                    if (id.equals("minecraft:generic.luck")) actualBonus = bonus * 1.0;
                    if (id.equals("minecraft:generic.attack_damage")) actualBonus = bonus * 1.0;
                    if (id.equals("minecraft:generic.armor")) actualBonus = bonus * 1.0;
                    if (id.equals("minecraft:generic.max_health")) actualBonus = bonus * 2.0;
                    
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
            new com.example.modmenu.network.SyncPricesPacket(
                itemBuyPrices,
                itemSellPrices,
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

    public static int getEnchantPrice(Enchantment enchantment) {
        String id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment).toString();
        return enchantPrices.getOrDefault(id, 2000);
    }

    public static void setEnchantPrice(Enchantment enchantment, int price) {
        String id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment).toString();
        enchantPrices.put(id, price);
        editedPrices.add("enchant:" + id);
        savePrices();
    }

    public static Map<String, Integer> getAllPrices() {
        return itemBuyPrices;
    }
    
    public static Map<String, Integer> getAllBuyPrices() { return itemBuyPrices; }
    public static Map<String, Integer> getAllSellPrices() { return itemSellPrices; }

    public static Map<String, Integer> getAllEnchantPrices() {
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
                int price = 2000;
                if (id.contains("sharpness") || id.contains("protection") || id.contains("unbreaking")) {
                    price = 5000;
                } else if (id.contains("mending") || id.contains("infinity") || id.contains("silk_touch")) {
                    price = 20000;
                } else if (id.contains("efficiency") || id.contains("fortune") || id.contains("looting")) {
                    price = 8000;
                }
                enchantPrices.put(id, price);
            }
        }
        savePrices();
    }


    private static void estimatePricesByRarity() {
        Map<net.minecraft.world.item.Rarity, List<Integer>> rarityPrices = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            String id = ForgeRegistries.ITEMS.getKey(item).toString();
            int price = itemBuyPrices.getOrDefault(id, 0);
            if (price > 0) {
                net.minecraft.world.item.Rarity rarity = item.getRarity(new ItemStack(item));
                rarityPrices.computeIfAbsent(rarity, k -> new ArrayList<>()).add(price);
            }
        }

        Map<net.minecraft.world.item.Rarity, Integer> avgPrices = new HashMap<>();
        rarityPrices.forEach((rarity, prices) -> {
            if (!prices.isEmpty()) {
                long sum = 0;
                for (int p : prices) sum += p;
                avgPrices.put(rarity, (int) (sum / prices.size()));
            }
        });

        // Default averages if none found
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.COMMON, 10);
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.UNCOMMON, 100);
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.RARE, 1000);
        avgPrices.putIfAbsent(net.minecraft.world.item.Rarity.EPIC, 10000);

        for (Item item : ForgeRegistries.ITEMS) {
            String id = ForgeRegistries.ITEMS.getKey(item).toString();
            if (!itemBuyPrices.containsKey(id) || itemBuyPrices.get(id) <= 0) {
                net.minecraft.world.item.Rarity rarity = item.getRarity(new ItemStack(item));
                int price = avgPrices.getOrDefault(rarity, 10);
                itemBuyPrices.put(id, price);
                itemSellPrices.put(id, id.startsWith("minecraft:") ? price : 10);
            }
        }
    }

    public static void addAllItems(net.minecraft.world.level.Level level) {
        if (level == null) return;

        pricingEngine.computeAllPrices(level);
        Map<String, Long> newPrices = pricingEngine.getPrices();

        // Keep edited prices
        Map<String, Integer> savedEditedBuy = new HashMap<>();
        Map<String, Integer> savedEditedSell = new HashMap<>();
        for (String id : editedPrices) {
            if (!id.startsWith("enchant:")) {
                if (itemBuyPrices.containsKey(id)) savedEditedBuy.put(id, itemBuyPrices.get(id));
                if (itemSellPrices.containsKey(id)) savedEditedSell.put(id, itemSellPrices.get(id));
            }
        }

        itemBuyPrices.clear();
        itemSellPrices.clear();

        for (Map.Entry<String, Long> entry : newPrices.entrySet()) {
            String id = entry.getKey();
            if (savedEditedBuy.containsKey(id)) {
                itemBuyPrices.put(id, savedEditedBuy.get(id));
                itemSellPrices.put(id, savedEditedSell.get(id));
            } else {
                int price = (int) Math.min(Integer.MAX_VALUE, entry.getValue());
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

    public static int idToSellPrice(String id, int buyPrice) {
        if (id.startsWith("minecraft:")) return buyPrice;
        return 10; // Modded items added via add all can only be sold for 10
    }
}
