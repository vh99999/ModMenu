package com.example.modmenu.store.pricing;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.math.BigDecimal;
import java.util.*;

public class PricingEngine {
    private static final Logger LOGGER = LogManager.getLogger();
    private final List<PriceProvider> providers = new ArrayList<>();
    private final MultiLayerCache cache = new MultiLayerCache();
    private final Map<String, String> priceSources = new HashMap<>();
    private final Map<String, List<String>> dependencyChains = new HashMap<>();
    private final Map<String, BigDecimal> appliedMultipliers = new HashMap<>();
    private final Map<String, Boolean> usedFallback = new HashMap<>();

    public PricingEngine() {
        registerProvider(new ProjectEPriceProvider());
        registerProvider(new DefaultPriceProvider());
        registerProvider(new RecipePriceProvider(this));
        registerProvider(new UsagePriceProvider(this));
        registerProvider(new InferencePriceProvider());
        registerProvider(new FallbackPriceProvider());
    }

    public void registerProvider(PriceProvider provider) {
        providers.add(provider);
        providers.sort(Comparator.comparingInt(PriceProvider::getPriority).reversed());
    }

    public void computeAllPrices(Level level) {
        LOGGER.info("Starting deterministic pricing engine...");
        PricingContext context = new PricingContext(level, cache);
        
        // Build dependency graph if needed (e.g. for RecipePriceProvider)
        // This could be done inside providers that need it

        for (Item item : ForgeRegistries.ITEMS) {
            String id = ForgeRegistries.ITEMS.getKey(item).toString();
            resolvePrice(item, context);
        }
        LOGGER.info("Pricing engine completed. Priced {} items.", cache.getPersistentCache().size());
    }

    public BigDecimal resolvePrice(Item item, PricingContext context) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        
        BigDecimal cached = cache.get(id);
        if (cached != null) return cached;

        if (context.isVisiting(id)) {
            // Loop detected
            return BigDecimal.valueOf(-1);
        }

        if (!context.push()) {
            // Depth overflow
            return BigDecimal.valueOf(-1);
        }

        context.startVisiting(id);
        try {
            for (PriceProvider provider : providers) {
                Optional<BigDecimal> price = provider.getPrice(item, context);
                if (price.isPresent()) {
                    BigDecimal multiplier = BigDecimal.valueOf(getMultiplier(item));
                    BigDecimal finalPrice = price.get().multiply(multiplier).setScale(0, java.math.RoundingMode.HALF_UP);
                    
                    cache.putPersistent(id, finalPrice);
                    priceSources.put(id, provider.getName());
                    appliedMultipliers.put(id, multiplier);
                    usedFallback.put(id, provider.getName().equals("Fallback"));
                    
                    LOGGER.info("Priced {}: {} [Source: {}, Multiplier: {}, Fallback: {}]", 
                        id, finalPrice, provider.getName(), multiplier, usedFallback.get(id));
                    
                    return finalPrice;
                }
            }
        } finally {
            context.stopVisiting(id);
            context.pop();
        }

        return BigDecimal.valueOf(-1);
    }

    private double getMultiplier(Item item) {
        double multiplier = 1.0;
        String id = ForgeRegistries.ITEMS.getKey(item).toString();

        if (isTool(item)) multiplier = 1.2;
        
        net.minecraft.world.item.Rarity rarity = item.getRarity(new ItemStack(item));
        switch (rarity) {
            case UNCOMMON -> multiplier = Math.max(multiplier, 1.2);
            case RARE -> multiplier = Math.max(multiplier, 1.5);
            case EPIC -> multiplier = Math.max(multiplier, 2.0);
        }

        // Apply rarity modifiers based on ID keywords (heuristic)
        if (id.contains("nether") || id.contains("quartz")) multiplier *= 1.2;
        if (id.contains("end") || id.contains("chorus") || id.contains("shulker")) multiplier *= 1.5;
        if (id.contains("magic") || id.contains("spell") || id.contains("relic")) multiplier *= 1.5;
        if (id.contains("ancient") || id.contains("legendary") || id.contains("mythic")) multiplier *= 2.0;
        if (id.contains("infinite") || id.contains("creative") || id.contains("debug")) multiplier *= 100.0;

        if (isExploitProne(item)) {
            multiplier *= 10.0;
        }
        return multiplier;
    }

    private BigDecimal applyModifiers(Item item, BigDecimal basePrice) {
        return basePrice.multiply(BigDecimal.valueOf(getMultiplier(item))).setScale(0, java.math.RoundingMode.HALF_UP);
    }

    private boolean isTool(Item item) {
        return item.getMaxDamage(new ItemStack(item)) > 0;
    }

    private boolean isExploitProne(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        return id.contains("generator") || id.contains("creative") || id.contains("debug") || id.contains("dupe");
    }

    public Map<String, BigDecimal> getPrices() {
        return cache.getPersistentCache();
    }

    public String getSource(String id) {
        return priceSources.getOrDefault(id, "Unknown");
    }
}
