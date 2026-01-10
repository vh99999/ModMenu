package com.example.modmenu.store.pricing;

import com.example.modmenu.store.DefaultPrices;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DefaultPriceProvider implements PriceProvider {
    private final Map<String, Integer> defaultPrices = new HashMap<>();

    public DefaultPriceProvider() {
        DefaultPrices.populate(defaultPrices);
    }

    @Override
    public Optional<Long> getPrice(Item item, PricingContext context) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        if (defaultPrices.containsKey(id)) {
            return Optional.of((long) defaultPrices.get(id));
        }
        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    public String getName() {
        return "DefaultPrices";
    }
}
