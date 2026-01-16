package com.example.modmenu.ai;

import com.google.gson.JsonObject;

public record ParameterizedIntent(IntentType type, JsonObject params) {
    public static ParameterizedIntent stop() {
        return new ParameterizedIntent(IntentType.STOP, new JsonObject());
    }
}
