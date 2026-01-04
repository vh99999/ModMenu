package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class UsagePriceProvider implements PriceProvider {
    private final PricingEngine engine;
    private final Map<String, List<Recipe<?>>> itemUsages = new HashMap<>();
    private boolean builtUsages = false;

    public UsagePriceProvider(PricingEngine engine) {
        this.engine = engine;
    }

    private void buildUsages(PricingContext context) {
        for (Recipe<?> recipe : context.getRecipeManager().getRecipes()) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    itemUsages.computeIfAbsent(id, k -> new ArrayList<>()).add(recipe);
                }
            }
        }
        builtUsages = true;
    }

    @Override
    public Optional<Long> getPrice(Item item, PricingContext context) {
        if (!builtUsages) {
            buildUsages(context);
        }

        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        List<Recipe<?>> usages = itemUsages.get(id);
        
        if (usages == null || usages.isEmpty()) return Optional.empty();

        long totalValue = 0;
        int count = 0;

        for (Recipe<?> recipe : usages) {
            ItemStack resultStack = recipe.getResultItem(context.getRegistryAccess());
            if (resultStack.isEmpty()) continue;

            // To avoid infinite recursion, we only use already resolved prices or basic prices
            Long resultPrice = context.getCache().get(ForgeRegistries.ITEMS.getKey(resultStack.getItem()).toString());
            if (resultPrice != null && resultPrice > 0) {
                // Estimate ingredient value: result price / number of ingredients
                int ingredientCount = recipe.getIngredients().size();
                if (ingredientCount > 0) {
                    totalValue += (resultPrice * resultStack.getCount()) / ingredientCount;
                    count++;
                }
            }
        }

        if (count > 0) {
            return Optional.of(totalValue / count);
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
