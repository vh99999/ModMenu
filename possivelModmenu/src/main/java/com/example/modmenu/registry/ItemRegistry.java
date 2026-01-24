package com.example.modmenu.registry;

import com.example.modmenu.modmenu;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, modmenu.MODID);

    public static final RegistryObject<Item> LOOT_DATA_FRAGMENT = ITEMS.register("loot_data_fragment", 
        () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> VIRTUAL_PROXY = ITEMS.register("virtual_proxy", 
        () -> new net.minecraft.world.item.BlockItem(BlockRegistry.VIRTUAL_PROXY.get(), new Item.Properties()));

    public static final RegistryObject<Item> LOGISTICS_TOOL = ITEMS.register("logistics_tool", 
        () -> new Item(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
