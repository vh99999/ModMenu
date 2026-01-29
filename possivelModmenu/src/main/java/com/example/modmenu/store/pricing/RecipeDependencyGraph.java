package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecipeDependencyGraph {
    private final Map<String, List<Recipe<?>>> itemRecipes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();

    public void build(PricingContext context) {
        for (Recipe<?> recipe : context.getRecipeManager().getRecipes()) {
            ItemStack resultStack = recipe.getResultItem(context.getRegistryAccess());
            if (resultStack.isEmpty()) continue;

            Item resultItem = resultStack.getItem();
            String resultId = ForgeRegistries.ITEMS.getKey(resultItem).toString();

            itemRecipes.computeIfAbsent(resultId, k -> new CopyOnWriteArrayList<>()).add(recipe);

            Set<String> deps = dependencies.computeIfAbsent(resultId, k -> ConcurrentHashMap.newKeySet());
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

    public List<String> getTopologicalSort() {
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String item : itemRecipes.keySet()) {
            if (!visited.contains(item)) {
                dfs(item, visited, visiting, sorted);
            }
        }
        Collections.reverse(sorted);
        return sorted;
    }

    private void dfs(String item, Set<String> visited, Set<String> visiting, List<String> sorted) {
        visiting.add(item);
        Set<String> deps = dependencies.get(item);
        if (deps != null) {
            for (String dep : deps) {
                if (visiting.contains(dep)) continue; // Simple cycle break
                if (!visited.contains(dep)) {
                    dfs(dep, visited, visiting, sorted);
                }
            }
        }
        visiting.remove(item);
        visited.add(item);
        sorted.add(item);
    }
}
