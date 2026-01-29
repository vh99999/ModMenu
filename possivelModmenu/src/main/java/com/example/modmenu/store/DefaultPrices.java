package com.example.modmenu.store;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Map;

public class DefaultPrices {
    private static final Gson GSON = new Gson();
    private static Map<String, BigDecimal> cachedPrices = null;

    public static void populate(Map<String, BigDecimal> map) {
        if (cachedPrices == null) {
            load();
        }
        if (cachedPrices != null) {
            map.putAll(cachedPrices);
        }
    }

    private static synchronized void load() {
        if (cachedPrices != null) return;
        try (InputStream is = DefaultPrices.class.getResourceAsStream("/default_prices.json")) {
            if (is != null) {
                cachedPrices = GSON.fromJson(new InputStreamReader(is), new TypeToken<Map<String, BigDecimal>>(){}.getType());
            } else {
                System.err.println("[DefaultPrices] Could not find default_prices.json in resources!");
            }
        } catch (Exception e) {
            System.err.println("[DefaultPrices] Failed to load default_prices.json");
            e.printStackTrace();
        }
    }
}
