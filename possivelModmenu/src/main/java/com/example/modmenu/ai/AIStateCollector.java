package com.example.modmenu.ai;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Responsible for gathering raw Minecraft state.
 * 
 * ARCHITECTURE ROLE:
 * This is a "dumb" sensor. It must ONLY output raw primitives.
 * - NO normalization (e.g. don't divide health by max_health)
 * - NO thresholds (e.g. don't categorize distance as "CLOSE")
 * - NO semantics (e.g. don't rename food_level to "energy")
 * 
 * Python's StateParser is the canonical authority for state semantics.
 */
public class AIStateCollector {
    private final Minecraft mc;

    private static final int STATE_VERSION = 1;

    public AIStateCollector(Minecraft mc) {
        this.mc = mc;
    }

    /**
     * Collects raw Minecraft state.
     * MUST strictly adhere to the StateParser schema in Python.
     */
    public JsonObject collect(Player player) {
        JsonObject state = new JsonObject();
        
        // 1. Metadata
        state.addProperty("state_version", STATE_VERSION);

        // 2. Raw Primitives (Validated for NaN/Inf)
        state.addProperty("health", sanitize(player.getHealth()));
        state.addProperty("health_max", sanitize(player.getMaxHealth()));
        state.addProperty("food_level", player.getFoodData().getFoodLevel());
        state.addProperty("horizontal_collision", player.horizontalCollision);
        
        Entity target = getTarget();
        state.addProperty("target_id", target != null ? target.getId() : -1);
        state.addProperty("target_distance", target != null ? sanitize(player.distanceTo(target)) : 1000.0); // 1000.0 is the default in Python schema

        return state;
    }

    private float sanitize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return value;
    }

    private Entity getTarget() {
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            return entityHitResult.getEntity();
        }
        return null;
    }
}
