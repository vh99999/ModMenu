package com.example.modmenu.ai;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
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

        // 2. Observations (Normalized as per STATE_PROTOCOL.md)
        float maxHealth = player.getMaxHealth();
        float currentHealth = player.getHealth();
        float healthPercent = (maxHealth > 0) ? (currentHealth / maxHealth) : 0.0f;
        
        // health MUST be > 0 when agent is alive
        if (player.isAlive() && healthPercent <= 0.0f) {
            healthPercent = 0.01f;
        }
        state.addProperty("health", sanitize(healthPercent));
        
        float energyPercent = player.getFoodData().getFoodLevel() / 20.0f;
        state.addProperty("energy", sanitize(energyPercent));

        state.addProperty("pos_x", sanitize((float)player.getX()));
        state.addProperty("pos_y", sanitize((float)player.getY()));
        state.addProperty("pos_z", sanitize((float)player.getZ()));

        state.addProperty("is_colliding", player.horizontalCollision || player.verticalCollision);
        
        Entity target = getTarget();
        state.addProperty("target_id", target != null ? target.getId() : -1);
        state.addProperty("target_distance", target != null ? sanitize(player.distanceTo(target)) : 1000.0);
        
        if (target != null) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            // Calculate yaw towards target
            float angle = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            float relativeYaw = Mth.wrapDegrees(angle - player.getYRot());
            state.addProperty("target_yaw", sanitize(relativeYaw));
        } else {
            state.addProperty("target_yaw", 0.0f);
        }

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
