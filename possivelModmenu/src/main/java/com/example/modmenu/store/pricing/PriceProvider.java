package com.example.modmenu.store.pricing;

import net.minecraft.world.item.Item;
import java.math.BigDecimal;
import java.util.Optional;

public interface PriceProvider {
    /**
     * Attempts to provide a price for the given item.
     * @param item The item to price.
     * @param context The pricing context.
     * @return An Optional containing the price if available, otherwise empty.
     */
    Optional<BigDecimal> getPrice(Item item, PricingContext context);

    /**
     * The priority of this provider. Higher values are processed first.
     */
    int getPriority();

    /**
     * A name for this provider for logging purposes.
     */
    String getName();
}
