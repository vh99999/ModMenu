package com.example.modmenu;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("modmenu")
public class modmenu
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "modmenu";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab

    public modmenu(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        com.example.modmenu.network.PacketHandler.register();
        com.example.modmenu.store.StorePriceManager.load();
        com.example.modmenu.registry.WorldGenRegistry.register(modEventBus);
        com.example.modmenu.registry.BlockRegistry.register(modEventBus);
        com.example.modmenu.registry.BlockEntityRegistry.register(modEventBus);
        com.example.modmenu.registry.ItemRegistry.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.OP_BLOCKS || event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(com.example.modmenu.registry.ItemRegistry.VIRTUAL_PROXY);
            event.accept(com.example.modmenu.registry.ItemRegistry.LOOT_DATA_FRAGMENT);
            event.accept(com.example.modmenu.registry.ItemRegistry.LOGISTICS_TOOL);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call

    @SubscribeEvent
    public void onServerAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event)
    {
        java.io.File worldDir = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        com.example.modmenu.store.StorePriceManager.initWorldData(worldDir);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
        com.example.modmenu.commands.StoreCommands.register(event.getServer().getCommands().getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event)
    {
        com.example.modmenu.store.StorePriceManager.save();
        com.example.modmenu.store.StorePriceManager.clearWorldData();
        com.example.modmenu.store.SkillManager.clearCaches();
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = "modmenu", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(com.example.modmenu.client.KeyMappings.OPEN_MENU_KEY);
            event.register(com.example.modmenu.client.KeyMappings.REMOTE_ACCESS_KEY);
        }
    }
}
