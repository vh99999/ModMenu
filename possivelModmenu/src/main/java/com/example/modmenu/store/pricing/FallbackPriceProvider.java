package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import java.math.BigDecimal;
import java.util.Optional;

public class FallbackPriceProvider implements PriceProvider {
    @Override
    public Optional<BigDecimal> getPrice(Item item, PricingContext context) {
        return Optional.of(BigDecimal.valueOf(1_000_000L));
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
