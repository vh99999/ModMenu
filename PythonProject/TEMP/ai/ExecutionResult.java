package com.example.modmenu.ai;

import com.google.gson.JsonObject;

/**
 * Structured result of an intent execution.
 * MUST match the Python IntentValidator contract.
 */
public record ExecutionResult(
    ExecutionStatus status,
    FailureReason failureReason,
    boolean partialExecution,
    JsonObject safetyFlags,
    String logs // For structured logging
) {
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("status", status.name());
        json.addProperty("failure_reason", failureReason.name());
        json.addProperty("partial_execution", partialExecution);
        json.add("safety_flags", safetyFlags != null ? safetyFlags : createDefaultSafetyFlags());
        
        return json;
    }

    private static JsonObject createDefaultSafetyFlags() {
        JsonObject flags = new JsonObject();
        flags.addProperty("is_blocked", false);
        flags.addProperty("on_cooldown", false);
        flags.addProperty("invalid_environment", false);
        return flags;
    }

    public static ExecutionResult success() {
        return new ExecutionResult(ExecutionStatus.SUCCESS, FailureReason.NONE, false, createDefaultSafetyFlags(), "Execution successful");
    }

    public static ExecutionResult failure(FailureReason reason, String log) {
        return new ExecutionResult(ExecutionStatus.FAILURE, reason, false, createDefaultSafetyFlags(), log);
    }
}
