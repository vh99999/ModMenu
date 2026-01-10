package com.example.modmenu.commands;

import com.example.modmenu.store.StorePriceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

public class EMCDumpCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static int dumpEmc(CommandSourceStack source) {
        if (!ModList.get().isLoaded("projecte")) {
            source.sendFailure(Component.literal("ProjectE is not loaded! This command requires ProjectE."));
            return 0;
        }

        try {
            source.sendSuccess(() -> Component.literal("Starting EMC dump..."), false);
            
            File dir = new File("emc_dumps");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "emc_dump.json");

            // Reflection for ProjectE API
            Class<?> apiClass = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            Object proxy = apiClass.getMethod("getEMCProxy").invoke(null);
            Method getValueMethod = proxy.getClass().getMethod("getValue", ItemStack.class);
            
            Map<String, Long> emcValues = new TreeMap<>();
            int count = 0;

            for (Item item : BuiltInRegistries.ITEM) {
                if (item == net.minecraft.world.item.Items.AIR) continue;
                ItemStack stack = new ItemStack(item);
                try {
                    long emc = (Long) getValueMethod.invoke(proxy, stack);
                    if (emc > 0) {
                        emcValues.put(BuiltInRegistries.ITEM.getKey(item).toString(), emc);
                        count++;
                    }
                } catch (Exception ignored) {}
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(emcValues, writer);
            }

            final File dumpFile = file;
            final int finalCount = count;
            source.sendSuccess(() -> Component.literal("EMC dump generated: " + dumpFile.getPath() + " (" + finalCount + " items)"), true);
            source.sendSuccess(() -> Component.literal("Use '/applyemc' to apply these values to the store."), true);

        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to generate EMC dump: " + e.getMessage()));
            e.printStackTrace();
        }

        return 1;
    }

    public static int applyEmc(CommandSourceStack source) {
        File file = new File("emc_dumps/emc_dump.json");
        if (!file.exists()) {
            source.sendFailure(Component.literal("EMC dump file not found! Run '/emcdump' first."));
            return 0;
        }

        try {
            source.sendSuccess(() -> Component.literal("Applying EMC values to store prices..."), false);
            java.io.FileReader reader = new java.io.FileReader(file);
            Map<String, Double> emcValues = GSON.fromJson(reader, new com.google.gson.reflect.TypeToken<Map<String, Double>>(){}.getType());
            reader.close();

            int count = 0;
            for (Map.Entry<String, Double> entry : emcValues.entrySet()) {
                String id = entry.getKey();
                int price = (int) Math.round(entry.getValue());
                if (price > 0) {
                    Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(id));
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        StorePriceManager.getAllBuyPrices().put(id, price);
                        StorePriceManager.getAllSellPrices().put(id, StorePriceManager.idToSellPrice(id, price));
                        count++;
                    }
                }
            }

            StorePriceManager.save();
            final int finalCountApplied = count;
            source.sendSuccess(() -> Component.literal("Applied EMC values to " + finalCountApplied + " items in the store."), true);
            
            // Sync with players
            if (source.getServer() != null) {
                for (net.minecraft.server.level.ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                    StorePriceManager.sync(player);
                }
            }

        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to apply EMC dump: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }
}
