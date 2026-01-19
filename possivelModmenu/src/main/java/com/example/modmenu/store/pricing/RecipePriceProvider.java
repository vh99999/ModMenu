package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

public class RecipePriceProvider implements PriceProvider {
    private RecipeDependencyGraph graph;
    private PricingEngine engine;

    public RecipePriceProvider(PricingEngine engine) {
        this.engine = engine;
    }

    @Override
    public Optional<BigDecimal> getPrice(Item item, PricingContext context) {
        if (graph == null) {
            graph = new RecipeDependencyGraph();
            graph.build(context);
        }

        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        List<Recipe<?>> recipes = graph.getRecipesForItem(id);
        
        if (recipes.isEmpty()) return Optional.empty();

        BigDecimal minCost = null;
        boolean foundValidRecipe = false;

        for (Recipe<?> recipe : recipes) {
            BigDecimal recipeCost = BigDecimal.ZERO;
            boolean allIngredientsPriced = true;
            
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                
                BigDecimal bestIngredientPrice = null;
                for (ItemStack stack : ingredient.getItems()) {
                    BigDecimal price = engine.resolvePrice(stack.getItem(), context);
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        if (bestIngredientPrice == null || price.compareTo(bestIngredientPrice) < 0) {
                            bestIngredientPrice = price;
                        }
                    }
                }
                
                if (bestIngredientPrice != null) {
                    recipeCost = recipeCost.add(bestIngredientPrice);
                } else {
                    allIngredientsPriced = false;
                    break;
                }
            }

            if (allIngredientsPriced && recipeCost.compareTo(BigDecimal.ZERO) > 0) {
                ItemStack result = recipe.getResultItem(context.getRegistryAccess());
                if (result.isEmpty()) continue;
                
                BigDecimal perItemCost = recipeCost.divide(BigDecimal.valueOf(result.getCount()), 10, java.math.RoundingMode.HALF_UP);
                
                // Add processing cost (heuristic)
                perItemCost = perItemCost.add(BigDecimal.valueOf(5)); // Flat processing fee
                
                if (minCost == null || perItemCost.compareTo(minCost) < 0) {
                    minCost = perItemCost;
                    foundValidRecipe = true;
                }
            }
        }

        return foundValidRecipe ? Optional.of(minCost.setScale(0, java.math.RoundingMode.HALF_UP)) : Optional.empty();
    }

    @Override
    public int getPriority() {
        return 800;
    }

    @Override
    public String getName() {
        return "Recipe";
    }
}
