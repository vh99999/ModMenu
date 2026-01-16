package com.example.modmenu.ai;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Responsible ONLY for observing player inputs and inferring human intent for shadow learning.
 * 
 * ARCHITECTURE ROLE:
 * This is a passive observer. It produces semantic intent strings that Python understands.
 * Used only when controller == HUMAN.
 * - MUST NOT modify game state
 * - MUST NOT have side effects
 */
public class HumanIntentDetector {
    private final Minecraft mc;

    public HumanIntentDetector(Minecraft mc) {
        this.mc = mc;
    }

    /**
     * Detects current human intent based on keyboard/mouse state.
     * @return The inferred ParameterizedIntent.
     */
    public ParameterizedIntent detect() {
        if (mc.options == null) return ParameterizedIntent.stop();

        IntentType type = IntentType.STOP;
        JsonObject params = new JsonObject();

        if (mc.options.keyAttack.isDown()) {
            type = IntentType.PRIMARY_ATTACK;
        } else if (mc.options.keyUse.isDown()) {
            type = IntentType.HOLD;
        } else if (mc.options.keyJump.isDown()) {
            type = IntentType.JUMP;
        } else if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || 
                   mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
            type = IntentType.MOVE;
        }

        // RELATIVE ROTATION LEARNING: Record angle to target instead of absolute world yaw
        // This allows the AI to learn "how to face an enemy" rather than "how to look West".
        float yawToRecord = 0.0f;
        float pitchToRecord = 0.0f;

        if (mc.player != null) {
            // Check if there's a valid target
            net.minecraft.world.entity.Entity target = null;
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr) {
                target = ehr.getEntity();
            }

            if (target != null) {
                double dx = target.getX() - mc.player.getX();
                double dz = target.getZ() - mc.player.getZ();
                float angle = (float) (net.minecraft.util.Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
                yawToRecord = net.minecraft.util.Mth.wrapDegrees(angle - mc.player.getYRot());
                pitchToRecord = mc.player.getXRot(); // Pitch is usually already relative to horizon
            } else {
                // If no target, we still record absolute for baseline, but target-based is preferred
                yawToRecord = mc.player.getYRot();
                pitchToRecord = mc.player.getXRot();
            }
        }

        params.addProperty("yaw", yawToRecord);
        params.addProperty("pitch", pitchToRecord);
        
        if (type == IntentType.MOVE) {
            params.addProperty("sprinting", mc.player != null && mc.player.isSprinting());
            
            com.google.gson.JsonArray vector = new com.google.gson.JsonArray();
            float forward = 0;
            float strafe = 0;
            if (mc.options.keyUp.isDown()) forward += 1.0f;
            if (mc.options.keyDown.isDown()) forward -= 1.0f;
            if (mc.options.keyLeft.isDown()) strafe += 1.0f;
            if (mc.options.keyRight.isDown()) strafe -= 1.0f;
            
            vector.add(forward);
            vector.add(0.0f);
            vector.add(strafe);
            params.add("vector", vector);
        }

        return new ParameterizedIntent(type, params);
    }
}
