package com.example.modmenu.ai;

import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class AIHardeningLogicTest {
    private AIClient mockClient;
    private AIStateCollector mockStateCollector;
    private IntentExecutor mockIntentExecutor;
    private HumanIntentDetector mockHumanDetector;
    private AIController controller;

    @Before
    public void setup() {
        mockClient = mock(AIClient.class);
        mockStateCollector = mock(AIStateCollector.class);
        mockIntentExecutor = mock(IntentExecutor.class);
        mockHumanDetector = mock(HumanIntentDetector.class);
        
        AIController realController = new AIController(mockClient, mockStateCollector, mockIntentExecutor, mockHumanDetector);
        controller = spy(realController);
        doNothing().when(controller).updateResultTrackers(any());
        
        controller.forceHumanMode();
    }

    private JsonObject createValidState() {
        JsonObject state = new JsonObject();
        state.addProperty("health", 1.0);
        state.addProperty("energy", 1.0);
        state.addProperty("pos_x", 0.0);
        state.addProperty("pos_y", 0.0);
        state.addProperty("pos_z", 0.0);
        state.addProperty("is_colliding", false);
        state.addProperty("is_on_ground", true);
        state.addProperty("is_floor_ahead", true);
        state.addProperty("target_distance", 1000.0);
        state.addProperty("target_yaw", 0.0);
        state.addProperty("state_version", 1);
        return state;
    }

    @Test
    public void testSetTimeouts() {
        controller.setTimeouts(500, 500);
    }

    @Test
    public void testOrchestrateNonBlocking() {
        controller.setMode(ControlMode.AI);
        JsonObject state = createValidState();
        JsonObject result = new JsonObject();
        
        when(mockClient.getNextIntent(any())).thenAnswer(invocation -> {
            Thread.sleep(1000);
            return new AIClient.Response(IntentType.MOVE, 1.0, new JsonObject(), null, -1);
        });
        
        long start = System.currentTimeMillis();
        boolean submitted = controller.orchestrate(state, IntentType.STOP, ControlMode.AI, result, new JsonObject());
        long end = System.currentTimeMillis();
        
        assertTrue("Orchestrate should return true when submitted", submitted);
        assertTrue("Orchestrate should be non-blocking", (end - start) < 100);
    }

    @Test
    public void testOrchestrateFailureDoesNotStopImmediately() throws InterruptedException {
        controller.setMode(ControlMode.AI);
        JsonObject state = createValidState();
        JsonObject result = new JsonObject();
        
        when(mockClient.getNextIntent(any())).thenReturn(new AIClient.Response(null, 0.0, new JsonObject(), "COMMUNICATION_FAILURE", -1));
        
        controller.orchestrate(state, IntentType.STOP, ControlMode.AI, result, new JsonObject());
        
        Thread.sleep(200);
        
        assertEquals("Should still be in AI mode after single communication failure", ControlMode.AI, controller.getMode());
    }

    @Test
    public void testConsecutiveFailuresDoNotStopAI() throws Exception {
        controller.setMode(ControlMode.AI);
        
        when(mockStateCollector.collect(any())).thenReturn(createValidState());
        when(mockIntentExecutor.execute(any(), any(), any())).thenReturn(ExecutionResult.success());
        
        // Multiple failures (poll returns null)
        for (int i = 0; i < 30; i++) {
            controller.onTickStart(null);
            assertEquals("Should NEVER automatically stop AI mode", ControlMode.AI, controller.getMode());
        }
        // After enough failures (20 in current code), it should start calling STOP
        verify(mockIntentExecutor, atLeastOnce()).execute(any(), eq(IntentType.STOP), any());
    }

    @Test
    public void testSafeStopDuringDegradedMode() throws Exception {
        controller.setMode(ControlMode.AI);
        
        Field lastIntentField = AIController.class.getDeclaredField("lastIntentTaken");
        lastIntentField.setAccessible(true);
        lastIntentField.set(controller, new AIClient.Response(IntentType.MOVE, 1.0, new JsonObject(), null, -1));
        
        when(mockStateCollector.collect(any())).thenReturn(createValidState());
        when(mockIntentExecutor.execute(any(), any(), any())).thenReturn(ExecutionResult.success());
        
        // Trigger a failure by calling onTickStart many times without a response in queue
        for(int i=0; i<25; i++) {
            controller.onTickStart(null);
        }
        
        // Verify that IntentExecutor.execute was called with STOP eventually
        verify(mockIntentExecutor, atLeastOnce()).execute(any(), eq(IntentType.STOP), any());
        assertEquals("Should still be in AI mode", ControlMode.AI, controller.getMode());
    }

    @Test
    public void testContinueOnStopResponse() throws Exception {
        controller.setMode(ControlMode.AI);
        JsonObject state = createValidState();
        JsonObject result = new JsonObject();

        when(mockClient.getNextIntent(any())).thenReturn(new AIClient.Response(IntentType.STOP, 1.0, new JsonObject(), null, -1));

        // First call
        assertTrue(controller.orchestrate(state, IntentType.STOP, ControlMode.AI, result, new JsonObject()));
        
        Thread.sleep(200); // Wait for async processing

        // Second call - should NOT be blocked by isHalted anymore
        assertTrue("Should NOT be halted after STOP response", controller.orchestrate(state, IntentType.STOP, ControlMode.AI, result, new JsonObject()));
    }

    @Test
    public void testHaltOnErrorResponse() throws Exception {
        controller.setMode(ControlMode.AI);
        JsonObject state = createValidState();
        JsonObject result = new JsonObject();

        when(mockClient.getNextIntent(any())).thenReturn(new AIClient.Response(null, 0.0, new JsonObject(), "COMMUNICATION_FAILURE", -1));

        // First call
        assertTrue(controller.orchestrate(state, IntentType.STOP, ControlMode.AI, result, new JsonObject()));
        
        Thread.sleep(200);

        // Second call
        assertFalse("Should be halted after ERROR response", controller.orchestrate(state, IntentType.STOP, ControlMode.AI, result, new JsonObject()));
    }
}
