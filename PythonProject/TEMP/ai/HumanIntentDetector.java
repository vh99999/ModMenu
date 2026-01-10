package com.example.modmenu.ai;

import net.minecraft.client.Minecraft;

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
     * @return The inferred IntentType.
     */
    public IntentType detect() {
        if (mc.options == null) return IntentType.STOP;

        // Note: This mapping is an inference for shadow learning.
        // It does not dictate what these keys MUST mean, only what we observe.
        
        if (mc.options.keyAttack.isDown()) return IntentType.PRIMARY_ATTACK;
        if (mc.options.keyUse.isDown()) return IntentType.HOLD;
        if (mc.options.keyJump.isDown()) return IntentType.JUMP;
        
        // If any movement key is pressed, we infer a MOVE intent.
        if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || 
            mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
            return IntentType.MOVE;
        }

        // We assume no input means the human's intent is to STOP.
        return IntentType.STOP;
    }
}
