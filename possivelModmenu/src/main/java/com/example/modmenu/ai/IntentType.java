package com.example.modmenu.ai;

/**
 * Represents high-level semantic intents decided by the AI.
 * The game is responsible for deciding how to execute these intents.
 */
public enum IntentType {
    PRIMARY_ATTACK,
    EVADE,
    MOVE,
    HOLD,
    RELEASE,
    STOP,
    JUMP,
    SWITCH_ITEM,
    PLACE_BLOCK,
    NO_OP
}
