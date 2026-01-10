package com.example.modmenu.ai;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

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
    
    // Explicit Fallback Intent (Non-negotiable)
    private static final IntentType FALLBACK_INTENT = IntentType.NO_OP;

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
        // This prevents human key presses from leaking into AI control
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
        result.add("outcomes", outcomes);

        // 1.1 Add Metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("engine_timestamp", System.currentTimeMillis());
        result.add("metadata", metadata);

        // 2. Collect State (Full snapshot)
        JsonObject state = stateCollector.collect(player);

        // 3. Orchestrate (Send state/intent/controller)
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
            // No new intent this tick -> SAFE NO-OP Fallback
            if (controlMode == ControlMode.AI) {
                lastIntentTaken = FALLBACK_INTENT; 
                consecutiveFailures++;
                if (!isDegradedMode) {
                    isDegradedMode = true;
                    logRateLimitedWarning("[DEGRADED_AI] AI response missing/late. Falling back to SAFE NO-OP.");
                }
            }
        }
        
        lastExecutionResult = intentExecutor.execute(player, lastIntentTaken);
    }

    private void blockAIIntent() {
        // No-op for now as Shadow Learning needs to report what happened
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
        result.add("outcomes", outcomes);

        // Add Metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("engine_timestamp", System.currentTimeMillis());
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
        controlMode = (controlMode == ControlMode.HUMAN) ? ControlMode.AI : ControlMode.HUMAN;
        intentQueue.clear();
        intentExecutor.releaseAllInputs();
        LOGGER.info("Control mode toggled via GUI to: {}", controlMode);
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
        if (!isProcessing.compareAndSet(false, true)) {
            return false;
        }

        // AUTHORITY FIELD (CRITICAL)
        // Use ONLY valid enum values supported by Python: ADVISORY, AUTHORITATIVE, OVERRIDE
        Authority authority = (controller == ControlMode.AI) ? Authority.AUTHORITATIVE : Authority.ADVISORY;
        String policyAuthority = (controller == ControlMode.AI) ? "ACTIVE_LEARNING_PERMIT" : "SHADOW_LEARNING_PERMIT";

        JsonObject payload = new JsonObject();
        payload.add("state", state);
        payload.addProperty("intent_taken", intentTaken != null ? intentTaken.name() : "STOP");
        payload.addProperty("controller", controller.name());
        payload.addProperty("authority", authority.name());
        payload.addProperty("policy_authority", policyAuthority);
        payload.add("result", result);

        // DATA LINEAGE (REQUIRED for Audit)
        JsonObject lineage = new JsonObject();
        lineage.addProperty("source", "JAVA_MOD");
        lineage.addProperty("trust_boundary", "TRUSTED_LOCAL");
        lineage.addProperty("learning_allowed", true);
        lineage.addProperty("decision_authority", authority.name());
        payload.add("lineage", lineage);

        // VALIDATION LAYER (REQUIRED)
        if (!validatePayload(payload)) {
            isProcessing.set(false);
            return false;
        }

        // Explicit Logging (Required)
        float healthLog = -1.0f;
        if (state != null && state.has("health") && !state.get("health").isJsonNull()) {
            healthLog = state.get("health").getAsFloat();
        }
        LOGGER.info("[AI_NET] Outbound request: Authority={}, Health={}", authority, healthLog);

        executor.submit(() -> {
            try {
                AIClient.Response response = client.getNextIntent(payload);
                
                if (response.isSuccess()) {
                    lastConfidence = response.confidence;
                    if (controlMode == ControlMode.AI) {
                        intentQueue.add(response.intent);
                    }
                } else {
                    logRateLimitedWarning("[CONTRACT_DEVIATION] AI Server error: {}.", response.errorMessage);
                    lastConfidence = 0.0;
                }
            } catch (Exception e) {
                logRateLimitedWarning("Orchestration error: {}", e.getMessage());
                lastConfidence = 0.0;
            } finally {
                isProcessing.set(false);
            }
        });
        return true;
    }

    private boolean validatePayload(JsonObject payload) {
        try {
            if (!payload.has("state") || payload.get("state").isJsonNull()) {
                LOGGER.error("[VALIDATOR] Rejecting request: Missing state.");
                return false;
            }
            
            JsonObject state = payload.getAsJsonObject("state");
            // health MUST be > 0 when agent is alive (already ensured in collector, but double check)
            if (state.has("health") && state.get("health").getAsFloat() < 0.0) {
                 LOGGER.error("[VALIDATOR] Rejecting request: Negative health detected.");
                 return false;
            }
            
            if (!payload.has("authority") || payload.get("authority").getAsString().equals("UNKNOWN")) {
                LOGGER.error("[VALIDATOR] Rejecting request: Authority is missing or UNKNOWN.");
                return false;
            }
            
            if (!payload.has("controller") || payload.get("controller").isJsonNull()) {
                LOGGER.error("[VALIDATOR] Rejecting request: Controller field is missing.");
                return false;
            }

            if (!payload.has("lineage")) {
                LOGGER.error("[VALIDATOR] Rejecting request: Lineage field is missing.");
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
