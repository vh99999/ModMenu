package com.example.modmenu.ai;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Orchestrates the AI lifecycle: Collect -> Send -> Receive -> Execute -> Report.
 * 
 * ARCHITECTURE ROLE:
 * Single source of truth for the AI bridge's lifecycle and mode management.
 * - Decides when to query Python vs. when to observe (Shadow Learning).
 * - Manages explicit fallback decisions when the server is unavailable.
 * - Java MUST NOT replicate Python's learning or decision logic.
 */
public class AIController {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static volatile ControlMode controlMode = ControlMode.HUMAN;
    private static volatile boolean autoPromoteEnabled = false;
    private int promoteTimer = 0;
    private static final int PROMOTE_INTERVAL_TICKS = 12000; // 10 minutes
    
    private final AIClient client;
    private final ExecutorService executor;
    private final AIStateCollector stateCollector;
    private final IntentExecutor intentExecutor;
    private final HumanIntentDetector humanDetector;
    
    private final ConcurrentLinkedQueue<AIClient.Response> intentQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean isHalted = new AtomicBoolean(false);
    
    private AIClient.Response lastIntentTaken = new AIClient.Response(IntentType.STOP, 1.0, new JsonObject(), null, -1);
    private double lastConfidence = 1.0;
    private ExecutionResult lastExecutionResult = ExecutionResult.success();

    private int maxConsecutiveFailures = 5;
    private int consecutiveFailures = 0;
    private boolean isDegradedMode = false;
    private long lastWarningTime = 0;

    private void logRateLimitedWarning(String message, Object... args) {
        long now = System.currentTimeMillis();
        if (now - lastWarningTime > 1000) {
            LOGGER.warn(message, args);
            lastWarningTime = now;
        }
    }

    // Result tracking state
    private float lastHealth = -1;
    private float damageReceivedAcc = 0;
    private float damageDealtAcc = 0;
    
    // Explicit Fallback Intent (STRICT COMPLIANCE)
    private static final IntentType FALLBACK_INTENT = IntentType.STOP;

    public AIController(String host, int port) {
        this(new AIClient(host, port), 
             new AIStateCollector(net.minecraft.client.Minecraft.getInstance()),
             new IntentExecutor(net.minecraft.client.Minecraft.getInstance()),
             new HumanIntentDetector(net.minecraft.client.Minecraft.getInstance()));
    }

    public AIController(AIClient client, AIStateCollector stateCollector, IntentExecutor intentExecutor, HumanIntentDetector humanDetector) {
        this.client = client;
        this.stateCollector = stateCollector;
        this.intentExecutor = intentExecutor;
        this.humanDetector = humanDetector;
        
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "AI-Orchestrator-Thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Actuation Phase: Override human inputs and apply AI intent (Phase.START)
     */
    public void onTickStart(net.minecraft.world.entity.player.Player player) {
        if (controlMode == ControlMode.AI) {
            AIClient.Response nextIntent = intentQueue.poll();
            if (nextIntent != null) {
                lastIntentTaken = nextIntent;
                consecutiveFailures = 0;
                if (isDegradedMode) {
                    isDegradedMode = false;
                    LOGGER.info("[DEGRADED_AI] AI recovered and synchronized.");
                }
            } else {
                // No new intent this tick -> decide if we should fallback
                if (consecutiveFailures > 20) { // Allow some slack for network
                    lastIntentTaken = new AIClient.Response(FALLBACK_INTENT, 0.0, new JsonObject(), "DEGRADED_MODE", -1);
                    if (!isDegradedMode) {
                        isDegradedMode = true;
                        logRateLimitedWarning("[DEGRADED_AI] AI response missing/late. Falling back to SAFE STOP.");
                    }
                }
                consecutiveFailures++;
            }

            // FLUID ROTATION TRACKING: Update lastIntentTaken.params with latest target info
            // This ensures we keep turning even if the server is slow to respond.
            JsonObject latestState = stateCollector.collect(player);
            
            // Prioritize target from server, fallback to local target
            int targetId = lastIntentTaken.targetId != -1 ? lastIntentTaken.targetId : (latestState.has("target_id") ? latestState.get("target_id").getAsInt() : -1);

            if (targetId != -1) {
                net.minecraft.world.entity.Entity target = player.level().getEntity(targetId);
                if (target != null) {
                    double dx = target.getX() - player.getX();
                    double dy = (target.getY() + target.getBbHeight() * 0.5) - (player.getY() + player.getEyeHeight());
                    double dz = target.getZ() - player.getZ();
                    
                    float yawAngle = (float) (net.minecraft.util.Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
                    float pitchAngle = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));

                    JsonObject updatedParams = lastIntentTaken.params.deepCopy();
                    updatedParams.addProperty("yaw", yawAngle);
                    updatedParams.addProperty("pitch", pitchAngle);
                    
                    // Update the response object with new params for execution
                    lastIntentTaken = new AIClient.Response(
                        lastIntentTaken.intent, 
                        lastIntentTaken.confidence, 
                        updatedParams, 
                        lastIntentTaken.errorMessage,
                        targetId
                    );
                }
            }

            lastExecutionResult = intentExecutor.execute(player, lastIntentTaken.intent, lastIntentTaken.params);
        }
    }

    /**
     * Perception Phase: Collect results and request next move (Phase.END)
     */
    public void onTickEnd(net.minecraft.world.entity.player.Player player) {
        try {
            if (autoPromoteEnabled) {
                promoteTimer++;
                if (promoteTimer >= PROMOTE_INTERVAL_TICKS) {
                    promoteTimer = 0;
                    triggerPromotion();
                }
            }

            if (controlMode == ControlMode.AI) {
                collectAndOrchestrateAI(player);
            } else {
                executeHumanInput(player);
            }
        } catch (Exception e) {
            LOGGER.error("Exception in AI control path, reverting to HUMAN", e);
            forceHumanMode();
        }
    }

    private void collectAndOrchestrateAI(net.minecraft.world.entity.player.Player player) {
        // 1. Collect Outcomes since last tick
        updateResultTrackers(player);
        JsonObject result = lastExecutionResult.toJsonObject();
        JsonObject outcomes = new JsonObject();
        outcomes.addProperty("damage_dealt", damageDealtAcc);
        outcomes.addProperty("damage_received", damageReceivedAcc);
        outcomes.addProperty("is_alive", player != null ? player.isAlive() : true);
        outcomes.addProperty("action_wasted", lastExecutionResult.status() == ExecutionStatus.FAILURE);
        result.add("outcomes", outcomes);

        // 1.1 Add Metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("engine_timestamp", System.currentTimeMillis());
        metadata.addProperty("execution_time_ms", 0.0);
        result.add("metadata", metadata);

        // 2. Collect State (Full snapshot)
        JsonObject state = stateCollector.collect(player);

        // 3. Orchestrate (Send exactly ONE request per tick)
        if (orchestrate(state, lastIntentTaken.intent, controlMode, result, lastIntentTaken.params)) {
            damageDealtAcc = 0;
            damageReceivedAcc = 0;
        }
    }

    private void executeHumanInput(net.minecraft.world.entity.player.Player player) {
        // Shadow Learning path
        ParameterizedIntent humanIntent = humanDetector.detect();
        updateResultTrackers(player);
        
        JsonObject result = ExecutionResult.success().toJsonObject();
        JsonObject outcomes = new JsonObject();
        outcomes.addProperty("damage_dealt", damageDealtAcc);
        outcomes.addProperty("damage_received", damageReceivedAcc);
        outcomes.addProperty("is_alive", player != null ? player.isAlive() : true);
        outcomes.addProperty("action_wasted", false);
        result.add("outcomes", outcomes);

        // Add Metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("engine_timestamp", System.currentTimeMillis());
        metadata.addProperty("execution_time_ms", 0.0);
        result.add("metadata", metadata);

        JsonObject state = stateCollector.collect(player);
        
        if (orchestrate(state, humanIntent.type(), controlMode, result, humanIntent.params())) {
            damageDealtAcc = 0;
            damageReceivedAcc = 0;
        }
        
        lastIntentTaken = new AIClient.Response(humanIntent.type(), 1.0, humanIntent.params(), null, -1);
        lastExecutionResult = ExecutionResult.success();
    }

    void updateResultTrackers(net.minecraft.world.entity.player.Player player) {
        if (player == null) return;
        float currentHealth = player.getHealth();
        if (lastHealth != -1 && currentHealth < lastHealth) {
            damageReceivedAcc += (lastHealth - currentHealth);
        }
        lastHealth = currentHealth;
    }

    public void forceHumanMode() {
        if (controlMode != ControlMode.HUMAN) {
            LOGGER.warn("FORCE FAIL-SAFE: Reverting to HUMAN control mode.");
            setMode(ControlMode.HUMAN);
            syncControlModeWithServer();
        }
    }

    public void attemptModeToggle() {
        setMode((controlMode == ControlMode.HUMAN) ? ControlMode.AI : ControlMode.HUMAN);
        LOGGER.info("Control mode toggled via GUI to: {}", controlMode);
        syncControlModeWithServer();
    }

    public void toggleAutoPromote() {
        autoPromoteEnabled = !autoPromoteEnabled;
        LOGGER.info("Auto-Promote toggled via GUI to: {}", autoPromoteEnabled);
        if (!autoPromoteEnabled) {
            promoteTimer = 0;
        }
    }

    public boolean isAutoPromoteEnabled() {
        return autoPromoteEnabled;
    }

    public void triggerPromotion() {
        executor.submit(() -> {
            try {
                JsonObject payload = createCommandPayload("PROMOTE_SHADOW");
                LOGGER.info("[AI_CONTROLLER] Requesting auto-promotion from server");
                JsonObject response = client.sendCommand(payload);
                if (response.has("status") && "SUCCESS".equals(response.get("status").getAsString())) {
                    LOGGER.info("[AI_CONTROLLER] Auto-promotion successful.");
                } else {
                    LOGGER.warn("[AI_CONTROLLER] Auto-promotion failed: {}", 
                        response.has("error") ? response.get("error").getAsString() : "UNKNOWN");
                }
            } catch (Exception e) {
                LOGGER.error("[AI_CONTROLLER] Failed to trigger auto-promotion", e);
            }
        });
    }

    public void syncControlModeWithServer() {
        executor.submit(() -> {
            try {
                JsonObject payload = createCommandPayload("CONTROL_MODE");
                payload.addProperty("mode", controlMode == ControlMode.AI ? "AI" : "HUMAN");
                payload.addProperty("source", "GUI");
                
                LOGGER.info("[AI_CONTROLLER] Syncing control mode with server: {}", payload.get("mode").getAsString());
                client.sendCommand(payload);
            } catch (Exception e) {
                LOGGER.error("[AI_CONTROLLER] Failed to sync control mode with server", e);
            }
        });
    }

    public void reloadKnowledge() {
        executor.submit(() -> {
            try {
                JsonObject payload = createCommandPayload("RELOAD_KNOWLEDGE");
                LOGGER.info("[AI_CONTROLLER] Requesting knowledge reload from server");
                JsonObject response = client.sendCommand(payload);
                if (response.has("status") && "SUCCESS".equals(response.get("status").getAsString())) {
                    LOGGER.info("[AI_CONTROLLER] Knowledge reloaded successfully. Version: {}", 
                        response.has("version") ? response.get("version").getAsString() : "UNKNOWN");
                } else {
                    LOGGER.warn("[AI_CONTROLLER] Knowledge reload failed or returned error");
                }
            } catch (Exception e) {
                LOGGER.error("[AI_CONTROLLER] Failed to reload knowledge", e);
            }
        });
    }

    public void resetPassiveStore() {
        executor.submit(() -> {
            try {
                JsonObject payload = createCommandPayload("RESET_PASSIVE_STORE");
                LOGGER.info("[AI_CONTROLLER] Requesting passive store reset from server");
                JsonObject response = client.sendCommand(payload);
                if (response.has("status") && "SUCCESS".equals(response.get("status").getAsString())) {
                    LOGGER.info("[AI_CONTROLLER] Passive store reset successfully.");
                } else {
                    LOGGER.warn("[AI_CONTROLLER] Passive store reset failed or returned error");
                }
            } catch (Exception e) {
                LOGGER.error("[AI_CONTROLLER] Failed to reset passive store", e);
            }
        });
    }

    private JsonObject createCommandPayload(String type) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        payload.addProperty("experience_id", "CMD-" + UUID.randomUUID().toString());
        payload.addProperty("authority", Authority.AUTHORITATIVE.name());
        payload.addProperty("protocol_version", 1);
        return payload;
    }

    public void reset() {
        isHalted.set(false);
        isProcessing.set(false);
        consecutiveFailures = 0;
        isDegradedMode = false;
        intentQueue.clear();
        lastIntentTaken = new AIClient.Response(IntentType.STOP, 1.0, new JsonObject(), null, -1);
        lastConfidence = 1.0;
        LOGGER.info("[AI_CONTROLLER] Lifecycle Reset: Bridge unhalted and state cleared.");
    }

    public ControlMode getMode() {
        return controlMode;
    }

    public void setMode(ControlMode mode) {
        controlMode = mode;
        intentQueue.clear();
        intentExecutor.releaseAllInputs();
        consecutiveFailures = 0;
        isDegradedMode = false;
        isHalted.set(false);
    }

    public void setMaxConsecutiveFailures(int max) {
        this.maxConsecutiveFailures = max;
    }

    public void setTimeouts(int connect, int read) {
        AIClient.setTimeouts(connect, read);
    }

    /**
     * Main orchestration entry point. 
     */
    public boolean orchestrate(JsonObject state, IntentType intentTaken, ControlMode controller, JsonObject result, JsonObject intentParams) {
        if (isHalted.get()) {
            return false;
        }
        if (!isProcessing.compareAndSet(false, true)) {
            // Ensures exactly ONE request per tick (avoids multiple in-flight)
            return false;
        }

        // AUTHORITY FIELD (STABLE IDENTIFIER - MANDATORY)
        // authority MUST NOT be a lifecycle or mode value.
        Authority authority = Authority.AUTHORITATIVE;
        String decisionAuthority = (controller == ControlMode.AI) ? "ML_MODEL" : "HUMAN";
        String policyAuthority = (controller == ControlMode.AI) ? "ACTIVE_LEARNING_PERMIT" : "SHADOW_LEARNING_PERMIT";

        JsonObject payload = new JsonObject();
        payload.addProperty("experience_id", UUID.randomUUID().toString());
        payload.add("state", state);
        payload.addProperty("intent_taken", intentTaken != null ? intentTaken.name() : "STOP");
        payload.add("intent_params", intentParams != null ? intentParams : new JsonObject());
        payload.addProperty("controller", controller.name());
        payload.addProperty("authority", authority.name());
        payload.addProperty("policy_authority", policyAuthority);
        payload.addProperty("last_confidence", lastConfidence);
        payload.add("result", result);
        payload.addProperty("protocol_version", 1);

        // DATA LINEAGE (STRICT COMPLIANCE)
        JsonObject lineage = new JsonObject();
        lineage.addProperty("source", "JAVA_SOURCE");
        lineage.addProperty("trust_boundary", "INTERNAL_VERIFIED");
        lineage.addProperty("learning_allowed", true);
        lineage.addProperty("decision_authority", decisionAuthority);
        payload.add("lineage", lineage);

        // PRE-SEND VALIDATION (REQUIRED)
        if (!validatePayload(payload)) {
            isHalted.set(true);
            isProcessing.set(false);
            return false;
        }

        // Dedicated thread handles blocking network I/O to avoid freezing game thread.
        executor.submit(() -> {
            try {
                // client.getNextIntent blocks until response is received (Requirement 6)
                AIClient.Response response = client.getNextIntent(payload);
                
                if (response.isSuccess()) {
                    lastConfidence = response.confidence;
                    if (controlMode == ControlMode.AI) {
                        intentQueue.add(response);
                    }
                    
                    if (response.intent == IntentType.STOP) {
                        // STOP is a valid action (do nothing). In previous versions this halted the bridge,
                        // but it must remain active for shadow learning (HUMAN mode).
                        LOGGER.debug("[PROTOCOL] STOP intent received. Continuing...");
                    }
                } else {
                    logRateLimitedWarning("[CONTRACT_DEVIATION] AI Server error: {}. Halting bridge.", response.errorMessage);
                    lastConfidence = 0.0;
                    isHalted.set(true);
                }
            } catch (Exception e) {
                logRateLimitedWarning("Orchestration error: {}. Halting bridge.", e.getMessage());
                lastConfidence = 0.0;
                isHalted.set(true);
            } finally {
                isProcessing.set(false);
            }
        });
        return true;
    }

    private boolean validatePayload(JsonObject payload) {
        try {
            // 1. Assert required root fields
            String[] requiredRoot = {"experience_id", "state", "intent_taken", "intent_params", "controller", "authority", "policy_authority", "last_confidence", "result", "lineage", "protocol_version"};
            for (String field : requiredRoot) {
                if (!payload.has(field) || payload.get(field).isJsonNull()) {
                    LOGGER.error("[VALIDATOR] Rejecting request: Missing root field '{}'.", field);
                    return false;
                }
            }
            
            // 2. Assert authority is NOT a mode value (Requirement 7)
            String authority = payload.get("authority").getAsString();
            if (authority.equals("ACTIVE") || authority.equals("SHADOW") || authority.equals("HUMAN") || authority.equals("UNKNOWN")) {
                LOGGER.error("[VALIDATOR] Rejecting request: Authority '{}' is a mode value or UNKNOWN.", authority);
                return false;
            }
            
            // 3. Assert intent_taken is NOT NO_OP (Requirement 8)
            if (payload.get("intent_taken").getAsString().equals("NO_OP")) {
                LOGGER.error("[VALIDATOR] Rejecting request: Intent 'NO_OP' is not allowed as feedback.");
                return false;
            }

            // 4. Assert lineage fields
            JsonObject lineage = payload.getAsJsonObject("lineage");
            String[] requiredLineage = {"source", "trust_boundary", "learning_allowed", "decision_authority"};
            for (String field : requiredLineage) {
                if (!lineage.has(field) || lineage.get(field).isJsonNull()) {
                    LOGGER.error("[VALIDATOR] Rejecting request: Missing lineage field '{}'.", field);
                    return false;
                }
            }

            // 5. Assert state validity
            JsonObject state = payload.getAsJsonObject("state");
            if (state.has("health") && state.get("health").getAsFloat() < 0.0) {
                 LOGGER.error("[VALIDATOR] Rejecting request: Negative health detected.");
                 return false;
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.error("[VALIDATOR] Unexpected exception during validation: {}", e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        intentExecutor.releaseAllInputs();
        executor.shutdownNow();
    }
}
