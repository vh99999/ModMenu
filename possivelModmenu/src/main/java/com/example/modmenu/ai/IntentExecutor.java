package com.example.modmenu.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.KeyMapping;

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
    public ExecutionResult execute(Player player, IntentType intent) {
        if (player == null) return ExecutionResult.failure(FailureReason.UNKNOWN_STATE, "PLAYER_NULL");
        if (intent == null) return ExecutionResult.failure(FailureReason.UNKNOWN_STATE, "INTENT_NULL");

        try {
            return switch (intent) {
                case PRIMARY_ATTACK -> performPrimaryAttack(player);
                case EVADE -> performEvade(player);
                case MOVE -> performMove(player);
                case JUMP -> performJump(player);
                case HOLD -> performHold(player);
                case RELEASE -> performRelease(player);
                case STOP -> performStop(player);
            };
        } catch (Exception e) {
            releaseAllInputs(); // Safety: release all inputs on failure
            return ExecutionResult.failure(FailureReason.UNKNOWN_STATE, "EXECUTION_EXCEPTION");
        }
    }

    private ExecutionResult performPrimaryAttack(Player player) {
        if (mc.hitResult instanceof EntityHitResult entityHitResult) {
            if (mc.gameMode != null) {
                mc.gameMode.attack(player, entityHitResult.getEntity());
                player.swing(InteractionHand.MAIN_HAND);
                return ExecutionResult.success();
            }
        }
        player.swing(InteractionHand.MAIN_HAND);
        return new ExecutionResult(ExecutionStatus.PARTIAL, FailureReason.BLOCKED, true, "SWUNG_ONLY_NO_TARGET");
    }

    private ExecutionResult performEvade(Player player) {
        // 1:1 Map to backward movement logic
        player.setDeltaMovement(player.getLookAngle().reverse().multiply(1.0, 0.0, 1.0));
        return ExecutionResult.success();
    }

    private ExecutionResult performMove(Player player) {
        if (mc.options == null) return ExecutionResult.failure(FailureReason.INVALID_STATE, "OPTIONS_NULL");
        setKeyState(mc.options.keyUp, true);
        return ExecutionResult.success();
    }

    private ExecutionResult performJump(Player player) {
        if (mc.options == null) return ExecutionResult.failure(FailureReason.INVALID_STATE, "OPTIONS_NULL");
        setKeyState(mc.options.keyJump, true);
        return ExecutionResult.success();
    }

    private ExecutionResult performHold(Player player) {
        if (mc.options == null) return ExecutionResult.failure(FailureReason.INVALID_STATE, "OPTIONS_NULL");
        setKeyState(mc.options.keyUse, true);
        return ExecutionResult.success();
    }

    private ExecutionResult performRelease(Player player) {
        releaseAllInputs();
        return ExecutionResult.success();
    }

    private ExecutionResult performStop(Player player) {
        if (mc.options == null) return ExecutionResult.failure(FailureReason.INVALID_STATE, "OPTIONS_NULL");
        setKeyState(mc.options.keyUp, false);
        setKeyState(mc.options.keyDown, false);
        setKeyState(mc.options.keyLeft, false);
        setKeyState(mc.options.keyRight, false);
        setKeyState(mc.options.keyJump, false);
        setKeyState(mc.options.keyUse, false);
        setKeyState(mc.options.keyAttack, false);
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
