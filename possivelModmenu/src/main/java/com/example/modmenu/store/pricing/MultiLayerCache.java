package com.example.modmenu.store.pricing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiLayerCache {
    private final Map<String, Long> persistentCache = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionCache = new ConcurrentHashMap<>();

    public void putPersistent(String itemId, long price) {
        persistentCache.put(itemId, price);
    }

    public void putSession(String itemId, long price) {
        sessionCache.put(itemId, price);
    }

    public Long get(String itemId) {
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

    public Map<String, Long> getPersistentCache() {
        return persistentCache;
    }
}
