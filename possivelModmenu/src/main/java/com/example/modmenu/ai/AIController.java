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
    
    private final AIClient client;
    private final ExecutorService executor;
    private final AIStateCollector stateCollector;
    private final IntentExecutor intentExecutor;
    private final HumanIntentDetector humanDetector;
    
    private final ConcurrentLinkedQueue<IntentType> intentQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean isHalted = new AtomicBoolean(false);
    
    private IntentType lastIntentTaken = IntentType.STOP;
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
     * Orchestrates one full control cycle per tick.
     */
    public void onTick(net.minecraft.world.entity.player.Player player) {
        try {
            if (controlMode == ControlMode.AI) {
                blockHumanInput();
                executeAIIntent(player);
            } else {
                blockAIIntent();
                executeHumanInput(player);
            }
        } catch (Exception e) {
            LOGGER.error("Exception in AI control path, reverting to HUMAN", e);
            forceHumanMode();
        }
    }

    private void blockHumanInput() {
        // Enforce exclusive authority: release all human-bound inputs
        intentExecutor.releaseAllInputs();
    }

    private void executeAIIntent(net.minecraft.world.entity.player.Player player) {
        // 1. Collect Outcomes since last tick
        updateResultTrackers(player);
        JsonObject result = lastExecutionResult.toJsonObject();
        JsonObject outcomes = new JsonObject();
        outcomes.addProperty("damage_dealt", damageDealtAcc);
        outcomes.addProperty("damage_received", damageReceivedAcc);
        outcomes.addProperty("is_alive", player != null ? player.isAlive() : true);
        outcomes.addProperty("action_wasted", false);
        result.add("outcomes", outcomes);

        // 1.1 Add Metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("engine_timestamp", System.currentTimeMillis());
        metadata.addProperty("execution_time_ms", 0.0); // Placeholder or track if needed
        result.add("metadata", metadata);

        // 2. Collect State (Full snapshot)
        JsonObject state = stateCollector.collect(player);

        // 3. Orchestrate (Send exactly ONE request per tick)
        if (orchestrate(state, lastIntentTaken, controlMode, result)) {
            damageDealtAcc = 0;
            damageReceivedAcc = 0;
        }

        // 4. Execute AI Intent
        IntentType nextIntent = intentQueue.poll();
        if (nextIntent != null) {
            lastIntentTaken = nextIntent;
            consecutiveFailures = 0;
            if (isDegradedMode) {
                isDegradedMode = false;
                LOGGER.info("[DEGRADED_AI] AI recovered and synchronized.");
            }
        } else {
            // No new intent this tick -> SAFE STOP Fallback
            if (controlMode == ControlMode.AI) {
                lastIntentTaken = FALLBACK_INTENT; 
                consecutiveFailures++;
                if (!isDegradedMode) {
                    isDegradedMode = true;
                    logRateLimitedWarning("[DEGRADED_AI] AI response missing/late. Falling back to SAFE STOP.");
                }
            }
        }
        
        lastExecutionResult = intentExecutor.execute(player, lastIntentTaken);
    }

    private void blockAIIntent() {
        // No-op
    }

    private void executeHumanInput(net.minecraft.world.entity.player.Player player) {
        // Shadow Learning path
        IntentType humanIntent = humanDetector.detect();
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
        
        if (orchestrate(state, humanIntent, controlMode, result)) {
            damageDealtAcc = 0;
            damageReceivedAcc = 0;
        }
        
        lastIntentTaken = humanIntent;
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

    public static void forceHumanMode() {
        if (controlMode != ControlMode.HUMAN) {
            LOGGER.warn("FORCE FAIL-SAFE: Reverting to HUMAN control mode.");
            controlMode = ControlMode.HUMAN;
        }
    }

    public void attemptModeToggle() {
        setMode((controlMode == ControlMode.HUMAN) ? ControlMode.AI : ControlMode.HUMAN);
        LOGGER.info("Control mode toggled via GUI to: {}", controlMode);
    }

    public void reset() {
        isHalted.set(false);
        isProcessing.set(false);
        consecutiveFailures = 0;
        isDegradedMode = false;
        intentQueue.clear();
        lastIntentTaken = IntentType.STOP;
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
    public boolean orchestrate(JsonObject state, IntentType intentTaken, ControlMode controller, JsonObject result) {
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
                        intentQueue.add(response.intent);
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
            String[] requiredRoot = {"experience_id", "state", "intent_taken", "controller", "authority", "policy_authority", "last_confidence", "result", "lineage", "protocol_version"};
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
