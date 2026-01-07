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

    // Result tracking state
    private float lastHealth = -1;
    private float damageReceivedAcc = 0;
    private float damageDealtAcc = 0;
    
    // Explicit Fallback Intent (Non-negotiable)
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
        outcomes.addProperty("is_alive", player.isAlive());
        result.add("outcomes", outcomes);

        // 2. Collect State (Full snapshot)
        JsonObject state = stateCollector.collect(player);

        // 3. Orchestrate (Send state/intent/controller)
        if (orchestrate(state, lastIntentTaken, controlMode, result)) {
            damageDealtAcc = 0;
            damageReceivedAcc = 0;
        }

        // 4. Execute AI Intent
        IntentType nextIntent = intentQueue.poll();
        lastIntentTaken = (nextIntent != null) ? nextIntent : FALLBACK_INTENT;
        
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
        outcomes.addProperty("is_alive", player.isAlive());
        result.add("outcomes", outcomes);

        JsonObject state = stateCollector.collect(player);
        
        if (orchestrate(state, humanIntent, controlMode, result)) {
            damageDealtAcc = 0;
            damageReceivedAcc = 0;
        }
        
        lastIntentTaken = humanIntent;
        lastExecutionResult = ExecutionResult.success();
    }

    private void updateResultTrackers(net.minecraft.world.entity.player.Player player) {
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
    }

    /**
     * Main orchestration entry point. 
     */
    public boolean orchestrate(JsonObject state, IntentType intentTaken, ControlMode controller, JsonObject result) {
        if (!isProcessing.compareAndSet(false, true)) {
            return false;
        }

        JsonObject payload = new JsonObject();
        payload.add("state", state);
        payload.addProperty("intent_taken", intentTaken != null ? intentTaken.name() : "STOP");
        payload.addProperty("controller", controller.name());
        payload.add("result", result);

        executor.submit(() -> {
            try {
                AIClient.Response response = client.getNextIntent(payload);
                
                if (response.isSuccess()) {
                    lastConfidence = response.confidence;
                    if (controlMode == ControlMode.AI) {
                        intentQueue.add(response.intent);
                    }
                } else {
                    LOGGER.warn("[CONTRACT_DEVIATION] AI Server error: {}. Using explicit fallback: {}", response.errorMessage, FALLBACK_INTENT);
                    lastConfidence = 0.0;
                    if (controlMode == ControlMode.AI) {
                        intentQueue.add(FALLBACK_INTENT);
                    }
                    if (response.errorMessage != null && (response.errorMessage.contains("COMMUNICATION_FAILURE") || response.errorMessage.contains("MALFORMED"))) {
                        forceHumanMode();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Orchestration error: {}", e.getMessage());
                lastConfidence = 0.0;
                forceHumanMode();
            } finally {
                isProcessing.set(false);
            }
        });
        return true;
    }

    public void shutdown() {
        intentExecutor.releaseAllInputs();
        executor.shutdownNow();
    }
}
