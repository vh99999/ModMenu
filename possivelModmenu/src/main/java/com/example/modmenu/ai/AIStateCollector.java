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

    public AIStateCollector(Minecraft mc) {
        this.mc = mc;
    }

    public JsonObject collect(Player player) {
        JsonObject state = new JsonObject();
        
        // Raw values only. No normalization, no semantics.
        // Python StateParser will handle interpretation.
        state.addProperty("health", player.getHealth());
        state.addProperty("health_max", player.getMaxHealth());
        state.addProperty("food_level", player.getFoodData().getFoodLevel());
        state.addProperty("horizontal_collision", player.horizontalCollision);
        
        Entity target = getTarget();
        state.addProperty("target_id", target != null ? target.getId() : -1);
        state.addProperty("target_distance", target != null ? player.distanceTo(target) : -1.0);

        return state;
    }

    private Entity getTarget() {
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            return entityHitResult.getEntity();
        }
        return null;
    }
}
