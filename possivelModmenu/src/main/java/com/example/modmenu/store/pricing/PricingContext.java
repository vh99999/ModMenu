package com.example.modmenu.store.pricing;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.RegistryAccess;
import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class PricingContext {
    private final Level level;
    private final RecipeManager recipeManager;
    private final RegistryAccess registryAccess;
    private final Map<String, BigDecimal> resolvedPrices;
    private final Set<String> visiting;
    private final MultiLayerCache cache;

    private int depth = 0;
    private static final int MAX_DEPTH = 50;

    public PricingContext(Level level, MultiLayerCache cache) {
        this.level = level;
        this.recipeManager = level.getRecipeManager();
        this.registryAccess = level.registryAccess();
        this.resolvedPrices = new HashMap<>();
        this.visiting = new HashSet<>();
        this.cache = cache;
    }

    public boolean push() {
        if (depth >= MAX_DEPTH) return false;
        depth++;
        return true;
    }

    public void pop() {
        depth--;
    }

    public Level getLevel() {
        return level;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public RegistryAccess getRegistryAccess() {
        return registryAccess;
    }

    public Map<String, BigDecimal> getResolvedPrices() {
        return resolvedPrices;
    }

    public boolean startVisiting(String itemId) {
        return visiting.add(itemId);
    }

    public void stopVisiting(String itemId) {
        visiting.remove(itemId);
    }

    public boolean isVisiting(String itemId) {
        return visiting.contains(itemId);
    }

    public MultiLayerCache getCache() {
        return cache;
    }
}
