package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class RecipeDependencyGraph {
    private final Map<String, List<Recipe<?>>> itemRecipes = new HashMap<>();
    private final Map<String, Set<String>> dependencies = new HashMap<>();

    public void build(PricingContext context) {
        for (Recipe<?> recipe : context.getRecipeManager().getRecipes()) {
            ItemStack resultStack = recipe.getResultItem(context.getRegistryAccess());
            if (resultStack.isEmpty()) continue;

            Item resultItem = resultStack.getItem();
            String resultId = ForgeRegistries.ITEMS.getKey(resultItem).toString();

            itemRecipes.computeIfAbsent(resultId, k -> new ArrayList<>()).add(recipe);

            Set<String> deps = dependencies.computeIfAbsent(resultId, k -> new HashSet<>());
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    deps.add(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
                }
            }
        }
    }

    public List<Recipe<?>> getRecipesForItem(String itemId) {
        return itemRecipes.getOrDefault(itemId, Collections.emptyList());
    }

    public Set<String> getDependencies(String itemId) {
        return dependencies.getOrDefault(itemId, Collections.emptySet());
    }
}
