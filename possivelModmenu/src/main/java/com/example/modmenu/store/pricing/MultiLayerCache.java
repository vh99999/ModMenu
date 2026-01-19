package com.example.modmenu.store.pricing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiLayerCache {
    private final Map<String, BigDecimal> persistentCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> sessionCache = new ConcurrentHashMap<>();

    public void putPersistent(String itemId, BigDecimal price) {
        persistentCache.put(itemId, price);
    }

    public void putSession(String itemId, BigDecimal price) {
        sessionCache.put(itemId, price);
    }

    public BigDecimal get(String itemId) {
        if (persistentCache.containsKey(itemId)) {
            return persistentCache.get(itemId);
        }
        return sessionCache.get(itemId);
    }

    public void clearSession() {
        sessionCache.clear();
    }

    public void clearAll() {
        persistentCache.clear();
        sessionCache.clear();
    }

    public Map<String, BigDecimal> getPersistentCache() {
        return persistentCache;
    }
}
