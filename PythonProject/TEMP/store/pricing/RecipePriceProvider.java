package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

public class RecipePriceProvider implements PriceProvider {
    private RecipeDependencyGraph graph;
    private PricingEngine engine;

    public RecipePriceProvider(PricingEngine engine) {
        this.engine = engine;
    }

    @Override
    public Optional<Long> getPrice(Item item, PricingContext context) {
        if (graph == null) {
            graph = new RecipeDependencyGraph();
            graph.build(context);
        }

        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        List<Recipe<?>> recipes = graph.getRecipesForItem(id);
        
        if (recipes.isEmpty()) return Optional.empty();

        long minCost = Long.MAX_VALUE;
        boolean foundValidRecipe = false;

        for (Recipe<?> recipe : recipes) {
            long recipeCost = 0;
            boolean allIngredientsPriced = true;
            
            StringBuilder chain = new StringBuilder();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                
                long bestIngredientPrice = -1;
                String bestIngredientId = "unknown";
                for (ItemStack stack : ingredient.getItems()) {
                    long price = engine.resolvePrice(stack.getItem(), context);
                    if (price > 0) {
                        if (bestIngredientPrice == -1 || price < bestIngredientPrice) {
                            bestIngredientPrice = price;
                            bestIngredientId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                        }
                    }
                }
                
                if (bestIngredientPrice > 0) {
                    recipeCost += bestIngredientPrice;
                    if (chain.length() > 0) chain.append(", ");
                    chain.append(bestIngredientId).append("(").append(bestIngredientPrice).append(")");
                } else {
                    allIngredientsPriced = false;
                    break;
                }
            }

            if (allIngredientsPriced && recipeCost > 0) {
                ItemStack result = recipe.getResultItem(context.getRegistryAccess());
                long perItemCost = recipeCost / result.getCount();
                if (perItemCost < minCost) {
                    minCost = perItemCost;
                    foundValidRecipe = true;
                    // We can log the chain here or store it in context/engine
                }
            }
        }

        return foundValidRecipe ? Optional.of(minCost) : Optional.empty();
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
