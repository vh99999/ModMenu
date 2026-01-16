package com.example.modmenu.ai;

import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import net.minecraft.world.entity.player.Player;

@Ignore("MOCKITO_BAN: Cannot mock Minecraft classes (Player)")
public class ControlAuthorityTest {
    private AIClient mockClient;
    private AIStateCollector mockStateCollector;
    private IntentExecutor mockIntentExecutor;
    private HumanIntentDetector mockHumanDetector;
    private AIController controller;
    private Player mockPlayer;

    @Before
    public void setup() {
        mockClient = mock(AIClient.class);
        mockStateCollector = mock(AIStateCollector.class);
        mockIntentExecutor = mock(IntentExecutor.class);
        mockHumanDetector = mock(HumanIntentDetector.class);
        mockPlayer = mock(Player.class);
        
        when(mockHumanDetector.detect()).thenReturn(ParameterizedIntent.stop());
        
        controller = new AIController(mockClient, mockStateCollector, mockIntentExecutor, mockHumanDetector);
        controller.forceHumanMode(); // Ensure start state is HUMAN
    }

    @Test
    public void testDefaultModeIsHuman() {
        assertEquals(ControlMode.HUMAN, controller.getMode());
    }

    @Test
    public void testToggleBehavior() {
        // Toggle to AI
        controller.attemptModeToggle();
        assertEquals(ControlMode.AI, controller.getMode());
        verify(mockIntentExecutor, atLeastOnce()).releaseAllInputs();

        // Toggle back to HUMAN
        controller.attemptModeToggle();
        assertEquals(ControlMode.HUMAN, controller.getMode());
    }

    @Test
    public void testExclusiveAuthorityGatingAI() {
        controller.setMode(ControlMode.AI);
        
        controller.onTickStart(mockPlayer);
        controller.onTickEnd(mockPlayer);
        
        // Should block human input
        verify(mockIntentExecutor, atLeastOnce()).releaseAllInputs();
        // Should NOT detect human intent for execution
        verify(mockHumanDetector, never()).detect();
    }

    @Test
    public void testExclusiveAuthorityGatingHuman() {
        controller.setMode(ControlMode.HUMAN);
        
        controller.onTickStart(mockPlayer);
        controller.onTickEnd(mockPlayer);
        
        // Should detect human intent
        verify(mockHumanDetector).detect();
    }

    @Test
    public void testFailSafeOnException() {
        controller.setMode(ControlMode.AI);
        when(mockStateCollector.collect(any())).thenThrow(new RuntimeException("Crash"));
        
        controller.onTickStart(mockPlayer);
        controller.onTickEnd(mockPlayer);
        
        assertEquals(ControlMode.HUMAN, controller.getMode());
    }
}
