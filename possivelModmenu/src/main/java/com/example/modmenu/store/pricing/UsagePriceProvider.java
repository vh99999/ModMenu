package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class UsagePriceProvider implements PriceProvider {
    private final PricingEngine engine;
    private final Map<String, List<Recipe<?>>> itemUsages = new ConcurrentHashMap<>();
    private volatile boolean builtUsages = false;

    public UsagePriceProvider(PricingEngine engine) {
        this.engine = engine;
    }

    private synchronized void buildUsages(PricingContext context) {
        if (builtUsages) return;
        for (Recipe<?> recipe : context.getRecipeManager().getRecipes()) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    itemUsages.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(recipe);
                }
            }
        }
        builtUsages = true;
    }

    @Override
    public Optional<BigDecimal> getPrice(Item item, PricingContext context) {
        if (!builtUsages) {
            buildUsages(context);
        }

        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        List<Recipe<?>> usages = itemUsages.get(id);
        
        if (usages == null || usages.isEmpty()) return Optional.empty();

        BigDecimal totalValue = BigDecimal.ZERO;
        int count = 0;

        for (Recipe<?> recipe : usages) {
            ItemStack resultStack = recipe.getResultItem(context.getRegistryAccess());
            if (resultStack.isEmpty()) continue;

            // To avoid infinite recursion, we only use already resolved prices or basic prices
            BigDecimal resultPrice = context.getCache().get(ForgeRegistries.ITEMS.getKey(resultStack.getItem()).toString());
            if (resultPrice != null && resultPrice.compareTo(BigDecimal.ZERO) > 0) {
                // Estimate ingredient value: result price / number of ingredients
                int ingredientCount = recipe.getIngredients().size();
                if (ingredientCount > 0) {
                    totalValue = totalValue.add(resultPrice.multiply(BigDecimal.valueOf(resultStack.getCount()))
                            .divide(BigDecimal.valueOf(ingredientCount), 10, java.math.RoundingMode.HALF_UP));
                    count++;
                }
            }
        }

        if (count > 0) {
            return Optional.of(totalValue.divide(BigDecimal.valueOf(count), 0, java.math.RoundingMode.HALF_UP));
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return 50; // Lower priority than direct recipes
    }

    @Override
    public String getName() {
        return "UsageInheritance";
    }
}
