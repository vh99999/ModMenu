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
    String logs // For structured logging
) {
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("status", status.name());
        json.addProperty("failure_reason", failureReason.name());
        json.addProperty("partial_execution", partialExecution);
        
        // Safety flags (Java factual reporting)
        JsonObject safetyFlags = new JsonObject();
        safetyFlags.addProperty("is_blocked", failureReason == FailureReason.BLOCKED);
        safetyFlags.addProperty("on_cooldown", failureReason == FailureReason.COOLDOWN);
        safetyFlags.addProperty("invalid_environment", failureReason == FailureReason.INVALID_STATE);
        json.add("safety_flags", safetyFlags);

        return json;
    }

    public static ExecutionResult success() {
        return new ExecutionResult(ExecutionStatus.SUCCESS, FailureReason.NONE, false, "Execution successful");
    }

    public static ExecutionResult failure(FailureReason reason, String log) {
        return new ExecutionResult(ExecutionStatus.FAILURE, reason, false, log);
    }
}
