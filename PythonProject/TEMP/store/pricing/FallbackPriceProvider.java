package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import java.util.Optional;

public class FallbackPriceProvider implements PriceProvider {
    @Override
    public Optional<Long> getPrice(Item item, PricingContext context) {
        return Optional.of(1_000_000L);
    }

    @Override
    public int getPriority() {
        return 0; // Lowest priority
    }

    @Override
    public String getName() {
        return "Fallback";
    }
}
