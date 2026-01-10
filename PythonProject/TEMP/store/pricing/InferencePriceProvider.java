package com.example.modmenu.store.pricing;

import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

public class InferencePriceProvider implements PriceProvider {
    @Override
    public Optional<Long> getPrice(Item item, PricingContext context) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        
        // 1. Infer from class
        long basePrice = 100; // Default base for inferred items

        if (item instanceof TieredItem tieredItem) {
            Tier tier = tieredItem.getTier();
            basePrice = inferFromTier(tier);
        } else if (item instanceof ArmorItem armorItem) {
            ArmorMaterial material = armorItem.getMaterial();
            basePrice = inferFromArmorMaterial(material);
        } else if (item instanceof BlockItem blockItem) {
            basePrice = 16; // Basic blocks
        }

        // 2. Adjust by stats if applicable
        if (item instanceof DiggerItem diggerItem) {
            basePrice += (long) (diggerItem.getAttackDamage() * 10);
        }

        // 3. Rarity modifiers (already applied in PricingEngine, but we can add more here if needed)
        
        return Optional.of(basePrice);
    }

    private long inferFromTier(Tier tier) {
        if (tier == Tiers.WOOD) return 32;
        if (tier == Tiers.STONE) return 64;
        if (tier == Tiers.IRON) return 256;
        if (tier == Tiers.GOLD) return 2048;
        if (tier == Tiers.DIAMOND) return 8192;
        if (tier == Tiers.NETHERITE) return 50000;
        return 1000;
    }

    private long inferFromArmorMaterial(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) return 64;
        if (material == ArmorMaterials.CHAIN) return 128;
        if (material == ArmorMaterials.IRON) return 256;
        if (material == ArmorMaterials.GOLD) return 2048;
        if (material == ArmorMaterials.DIAMOND) return 8192;
        if (material == ArmorMaterials.NETHERITE) return 50000;
        return 1000;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getName() {
        return "Inference";
    }
}
