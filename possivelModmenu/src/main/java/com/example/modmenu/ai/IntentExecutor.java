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
     */
    public void execute(Player player, IntentType intent) {
        if (player == null || intent == null) return;

        switch (intent) {
            case PRIMARY_ATTACK:
                performPrimaryAttack(player);
                break;
            case EVADE:
                performEvade(player);
                break;
            case MOVE:
                // PLACEHOLDER: MOVE is mapped to walking forward.
                // In a final implementation, MOVE might involve complex pathfinding 
                // or direction parameters not yet present in the Intent JSON.
                setKeyState(mc.options.keyUp, true);
                break;
            case JUMP:
                performJump(player);
                break;
            case HOLD:
                // PLACEHOLDER: HOLD is mapped to the 'use' key (right click).
                setKeyState(mc.options.keyUse, true);
                break;
            case RELEASE:
                releaseAllInputs();
                break;
            case STOP:
                performStop(player);
                break;
        }
    }

    private void performPrimaryAttack(Player player) {
        if (mc.hitResult instanceof EntityHitResult entityHitResult) {
            mc.gameMode.attack(player, entityHitResult.getEntity());
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    private void performEvade(Player player) {
        // PLACEHOLDER: EVADE is mapped to a simple backward motion.
        // The game decides HOW to evade based on this intent.
        player.setDeltaMovement(player.getLookAngle().reverse().multiply(1.0, 0.0, 1.0));
    }

    private void performJump(Player player) {
        // PLACEHOLDER: JUMP is mapped to the jump key.
        setKeyState(mc.options.keyJump, true);
    }

    private void performStop(Player player) {
        // PLACEHOLDER: STOP is mapped to releasing movement keys.
        setKeyState(mc.options.keyUp, false);
        setKeyState(mc.options.keyDown, false);
        setKeyState(mc.options.keyLeft, false);
        setKeyState(mc.options.keyRight, false);
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
