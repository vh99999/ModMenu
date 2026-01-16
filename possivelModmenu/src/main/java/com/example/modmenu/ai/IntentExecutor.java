package com.example.modmenu.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.KeyMapping;
import com.google.gson.JsonObject;

/**
 * Maps semantic intents to actual game behavior.
 * 
 * ARCHITECTURE ROLE:
 * This is a modular actuator (bridge).
 * - Executes intents exactly as received without interpretation.
 * - Must be modular/swappable.
 * - Java is responsible for HOW to execute, but Python decides WHAT to do.
 */
public class IntentExecutor {
    private final Minecraft mc;

    public IntentExecutor(Minecraft mc) {
        this.mc = mc;
    }

    /**
     * Executes the given intent.
     * @param player The player to act on.
     * @param intent The opaque semantic intent.
     * @return Structured result of the execution attempt.
     */
    public ExecutionResult execute(Player player, IntentType intent, JsonObject params) {
        if (player == null) return ExecutionResult.failure(FailureReason.UNKNOWN, "PLAYER_NULL");
        if (intent == null) return ExecutionResult.failure(FailureReason.UNKNOWN, "INTENT_NULL");
        if (params == null) params = new JsonObject();

        try {
            handleRotation(player, params);
            handleMovement(params);

            // Action reset: Most intents don't need persistent usage/attack
            if (intent != IntentType.HOLD && intent != IntentType.PLACE_BLOCK) {
                setKeyState(mc.options.keyUse, false);
            }
            if (intent != IntentType.PRIMARY_ATTACK) {
                setKeyState(mc.options.keyAttack, false);
            }
            if (intent != IntentType.JUMP && !params.has("jump_persistent")) {
                 setKeyState(mc.options.keyJump, false);
            }

            return switch (intent) {
                case PRIMARY_ATTACK -> performPrimaryAttack(player, params);
                case EVADE -> performEvade(player, params);
                case MOVE -> ExecutionResult.success();
                case JUMP -> performJump(player, params);
                case HOLD -> performHold(player, params);
                case RELEASE -> performRelease(player, params);
                case SWITCH_ITEM -> performSwitchItem(player, params);
                case PLACE_BLOCK -> performPlaceBlock(player, params);
                case STOP -> performStop(player, params);
                case NO_OP -> performStop(player, params);
            };
        } catch (Exception e) {
            releaseAllInputs(); // Safety: release all inputs on failure
            return ExecutionResult.failure(FailureReason.UNKNOWN, "EXECUTION_EXCEPTION");
        }
    }

    private ExecutionResult performSwitchItem(Player player, JsonObject params) {
        if (params.has("slot")) {
            int slot = params.get("slot").getAsInt();
            if (slot >= 0 && slot < 9) {
                player.getInventory().selected = slot;
                return ExecutionResult.success();
            }
        }
        return ExecutionResult.failure(FailureReason.INVALID_STATE, "INVALID_SLOT");
    }

    private ExecutionResult performPlaceBlock(Player player, JsonObject params) {
        if (mc.options == null) return ExecutionResult.failure(FailureReason.INVALID_STATE, "OPTIONS_NULL");
        setKeyState(mc.options.keyUse, true);
        return ExecutionResult.success();
    }

    private void handleRotation(Player player, JsonObject params) {
        if (params.has("yaw")) {
            float yaw = params.get("yaw").getAsFloat();
            player.setYRot(yaw);
            player.yRotO = yaw;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
            player.setYBodyRot(yaw);
        }
        if (params.has("pitch")) {
            float pitch = params.get("pitch").getAsFloat();
            player.setXRot(pitch);
            player.xRotO = pitch;
        }
    }

    private void handleMovement(JsonObject params) {
        if (mc.options == null) return;

        if (params.has("vector")) {
            com.google.gson.JsonArray vector = params.getAsJsonArray("vector");
            if (vector.size() >= 3) {
                float forward = vector.get(0).getAsFloat();
                float strafe = vector.get(2).getAsFloat();

                setKeyState(mc.options.keyUp, forward > 0.1);
                setKeyState(mc.options.keyDown, forward < -0.1);
                setKeyState(mc.options.keyLeft, strafe > 0.1);
                setKeyState(mc.options.keyRight, strafe < -0.1);
            }
        }

        if (params.has("sprinting")) {
            setKeyState(mc.options.keySprint, params.get("sprinting").getAsBoolean());
        }
    }

    private ExecutionResult performPrimaryAttack(Player player, JsonObject params) {
        // Enforce cooldown
        if (player.getAttackStrengthScale(0.5f) < 0.9f) {
            return ExecutionResult.failure(FailureReason.COOLDOWN, "ATTACK_ON_COOLDOWN");
        }

        if (mc.hitResult instanceof EntityHitResult entityHitResult) {
            if (mc.gameMode != null) {
                mc.gameMode.attack(player, entityHitResult.getEntity());
                player.swing(InteractionHand.MAIN_HAND);
                return ExecutionResult.success();
            }
        }
        player.swing(InteractionHand.MAIN_HAND);
        JsonObject flags = new JsonObject();
        flags.addProperty("is_blocked", true);
        flags.addProperty("on_cooldown", false);
        flags.addProperty("invalid_environment", false);
        return new ExecutionResult(ExecutionStatus.PARTIAL, FailureReason.BLOCKED, true, flags, "SWUNG_ONLY_NO_TARGET");
    }

    private ExecutionResult performEvade(Player player, JsonObject params) {
        // 1:1 Map to backward movement logic
        player.setDeltaMovement(player.getLookAngle().reverse().multiply(1.0, 0.0, 1.0));
        return ExecutionResult.success();
    }

    private ExecutionResult performJump(Player player, JsonObject params) {
        if (mc.options == null) return ExecutionResult.failure(FailureReason.INVALID_STATE, "OPTIONS_NULL");
        setKeyState(mc.options.keyJump, true);
        return ExecutionResult.success();
    }

    private ExecutionResult performHold(Player player, JsonObject params) {
        if (mc.options == null) return ExecutionResult.failure(FailureReason.INVALID_STATE, "OPTIONS_NULL");
        setKeyState(mc.options.keyUse, true);
        return ExecutionResult.success();
    }

    private ExecutionResult performRelease(Player player, JsonObject params) {
        releaseAllInputs();
        return ExecutionResult.success();
    }

    private ExecutionResult performStop(Player player, JsonObject params) {
        releaseAllInputs();
        return ExecutionResult.success();
    }

    public void releaseAllInputs() {
        if (mc.options == null) return;
        setKeyState(mc.options.keyUp, false);
        setKeyState(mc.options.keyDown, false);
        setKeyState(mc.options.keyLeft, false);
        setKeyState(mc.options.keyRight, false);
        setKeyState(mc.options.keyUse, false);
        setKeyState(mc.options.keyAttack, false);
        setKeyState(mc.options.keyJump, false);
        setKeyState(mc.options.keyShift, false);
        setKeyState(mc.options.keySprint, false);
    }

    private void setKeyState(KeyMapping key, boolean down) {
        if (key != null) {
            key.setDown(down);
        }
    }
}
