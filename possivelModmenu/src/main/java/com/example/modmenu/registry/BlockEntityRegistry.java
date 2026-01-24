package com.example.modmenu.registry;

import com.example.modmenu.modmenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockEntityRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, modmenu.MODID);

    public static final RegistryObject<BlockEntityType<com.example.modmenu.block.entity.VirtualProxyBlockEntity>> VIRTUAL_PROXY_BE = BLOCK_ENTITIES.register("virtual_proxy", 
        () -> BlockEntityType.Builder.of(com.example.modmenu.block.entity.VirtualProxyBlockEntity::new, BlockRegistry.VIRTUAL_PROXY.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
