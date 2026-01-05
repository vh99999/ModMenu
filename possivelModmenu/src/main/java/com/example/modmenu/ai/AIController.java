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
    
    public enum ControlMode {
        AI,        // AI is in active control
        HUMAN,     // Human is in control, AI observes (Shadow Learning)
        HEURISTIC, // Heuristic logic is in control (if implemented)
        DISABLED   // AI system is completely off
    }

    private final AIClient client;
    private final ExecutorService executor;
    private final AIStateCollector stateCollector;
    private final IntentExecutor intentExecutor;
    private final HumanIntentDetector humanDetector;
    
    private ControlMode mode = ControlMode.HUMAN;
    private final ConcurrentLinkedQueue<IntentType> intentQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    
    private IntentType lastIntentTaken = IntentType.STOP;
    private double lastConfidence = 1.0;
    private ExecutionResult lastExecutionResult = ExecutionResult.success();

    // Result tracking state
    private float lastHealth = -1;
    private float damageReceivedAcc = 0;
    private float damageDealtAcc = 0;
    
    // Explicit Fallback Intent (Non-negotiable)
    private static final IntentType FALLBACK_INTENT = IntentType.STOP;

    public AIController(String host, int port) {
        this.client = new AIClient(host, port);
        this.stateCollector = new AIStateCollector(net.minecraft.client.Minecraft.getInstance());
        this.intentExecutor = new IntentExecutor(net.minecraft.client.Minecraft.getInstance());
        this.humanDetector = new HumanIntentDetector(net.minecraft.client.Minecraft.getInstance());
        
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
        if (mode == ControlMode.DISABLED) {
            intentExecutor.releaseAllInputs();
            return;
        }

        // 1. Detect Human Intent (Passive observation for shadow learning)
        IntentType humanIntent = humanDetector.detect();

        // 2. Collect Results since last tick
        updateResultTrackers(player);
        JsonObject result = lastExecutionResult.toJsonObject();
        JsonObject outcomes = new JsonObject();
        outcomes.addProperty("damage_dealt", damageDealtAcc);
        outcomes.addProperty("damage_received", damageReceivedAcc);
        outcomes.addProperty("is_alive", player.isAlive());
        outcomes.addProperty("action_wasted", lastExecutionResult.status() == ExecutionStatus.FAILURE); 
        result.add("outcomes", outcomes);

        // 3. Collect State (Sensor)
        JsonObject state = stateCollector.collect(player);

        // 4. Orchestrate (Send report / Request next intent)
        // We report what was ACTUALLY taken in the previous cycle
        if (orchestrate(state, lastIntentTaken, mode, result)) {
            // Reset accumulators ONLY if orchestration accepted the payload
            damageDealtAcc = 0;
            damageReceivedAcc = 0;
        }

        // 5. Execution Selection
        if (mode == ControlMode.AI) {
            IntentType nextIntent = intentQueue.poll();
            if (nextIntent != null) {
                lastIntentTaken = nextIntent;
            } else {
                // Explicit fallback if queue is empty (e.g. timeout or server lag)
                lastIntentTaken = FALLBACK_INTENT;
            }
            
            LOGGER.info("[EXECUTION_ATTEMPT] Intent: {}", lastIntentTaken);
            lastExecutionResult = intentExecutor.execute(player, lastIntentTaken);
            LOGGER.info("[EXECUTION_RESULT] Status: {}, Reason: {}", lastExecutionResult.status(), lastExecutionResult.failureReason());
            
        } else if (mode == ControlMode.HUMAN) {
            // Shadow Learning: Observe and report human intent.
            // MUST NOT execute anything or interfere with human inputs.
            lastIntentTaken = humanIntent;
            lastConfidence = 1.0; // Factual observation
            lastExecutionResult = ExecutionResult.success();
        } else {
            // Other modes (e.g. HEURISTIC or DISABLED)
            intentExecutor.releaseAllInputs();
            lastIntentTaken = FALLBACK_INTENT;
            lastExecutionResult = ExecutionResult.success();
        }
    }

    private void updateResultTrackers(net.minecraft.world.entity.player.Player player) {
        float currentHealth = player.getHealth();
        if (lastHealth != -1 && currentHealth < lastHealth) {
            damageReceivedAcc += (lastHealth - currentHealth);
        }
        lastHealth = currentHealth;
        // Damage dealt tracking is complex in client-side without mixins; kept as placeholder.
    }

    public void setMode(ControlMode mode) {
        LOGGER.info("AI Control Mode changed to: {}", mode);
        this.mode = mode;
        intentQueue.clear();
        intentExecutor.releaseAllInputs(); // Safety: release all inputs when changing mode
    }

    public ControlMode getMode() {
        return mode;
    }

    /**
     * Legacy support for toggle key
     */
    public void setEnabled(boolean enabled) {
        setMode(enabled ? ControlMode.AI : ControlMode.HUMAN);
    }

    public boolean isEnabled() {
        return mode == ControlMode.AI;
    }

    /**
     * Main orchestration entry point. 
     * Handles both active AI control and passive Shadow Learning.
     * @return true if the cycle was accepted for processing, false if busy.
     */
    public boolean orchestrate(JsonObject state, IntentType intentTaken, ControlMode controller, JsonObject result) {
        if (mode == ControlMode.DISABLED) return false;

        // Shadow learning requirement: Even when human is in control, we must send data.
        if (!isProcessing.compareAndSet(false, true)) {
            return false;
        }

        JsonObject payload = new JsonObject();
        // protocol_version is added by AIClient
        payload.add("state", state);
        payload.addProperty("intent_taken", intentTaken != null ? intentTaken.name() : "STOP");
        payload.addProperty("controller", controller.name());
        payload.add("result", result);
        payload.addProperty("last_confidence", lastConfidence); 

        executor.submit(() -> {
            try {
                AIClient.Response response = client.getNextIntent(payload);
                
                if (response.isSuccess()) {
                    LOGGER.info("[INTENT_RECEIVED] Raw: {}", response.intent);
                    lastConfidence = response.confidence;
                    if (mode == ControlMode.AI) {
                        intentQueue.add(response.intent);
                    }
                } else {
                    // Fail-safe requirement: Trigger explicit fallback intent
                    LOGGER.warn("[CONTRACT_DEVIATION] AI Server error: {}. Using explicit fallback: {}", response.errorMessage, FALLBACK_INTENT);
                    lastConfidence = 0.0;
                    if (mode == ControlMode.AI) {
                        intentQueue.add(FALLBACK_INTENT);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Orchestration error: {}", e.getMessage());
                lastConfidence = 0.0;
                if (mode == ControlMode.AI) {
                    intentQueue.add(FALLBACK_INTENT);
                }
            } finally {
                isProcessing.set(false);
            }
        });
        return true;
    }

    public IntentType pollIntent() {
        return intentQueue.poll();
    }

    public void shutdown() {
        intentExecutor.releaseAllInputs(); // Safety: release all inputs on shutdown
        executor.shutdownNow();
    }
}
