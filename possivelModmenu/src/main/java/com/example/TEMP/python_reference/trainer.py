import logging
from typing import Dict, Any
from policy import Policy, LearnedPolicy
from learning_control import can_learn

logger = logging.getLogger(__name__)

class Trainer:
    """
    Handles learning from experiences.
    Updates the policy based on rewards (RL) or human demonstrations (Imitation).
    
    Training Flow:
    1. EXPERIENCE ACQUISITION: Receive (S, A, R, Controller) tuple.
    2. LABELING: Identify if the action was taken by HUMAN, HEURISTIC, or AI.
    3. ROUTING: 
        - If HUMAN: Route to Imitation Learning path (Supervised).
        - If AI or HEURISTIC: Route to Reinforcement Learning path (Value-based).
          Note: HEURISTIC experiences are treated as expert RL trajectories.
    4. OPTIMIZATION (Future): Update policy weights to maximize performance.
    """
    def __init__(self, policy: Policy):
        self.policy = policy

    def train_on_experience(self, experience: Dict[str, Any]) -> None:
        """
        Main entry point for online learning.
        Routes experience to either Imitation or Reinforcement learning paths.
        
        Shadow Learning Note: This method is called regardless of who is 
        currently in control, allowing the system to learn from human 
        demonstrations even when the AI is not active.
        """
        # LEARNING ENTRY POINT (DISABLED BY DESIGN)
        allowed, reason = can_learn(experience, {"caller": "Trainer.train_on_experience"})
        if not allowed:
            logger.debug(f"Learning blocked by Control Plane: {reason}")
            return

        controller = experience.get("controller", "AI")
        
        if controller == "HUMAN":
            self._train_imitation(experience)
        elif controller in ["AI", "HEURISTIC"]:
            self._train_reinforcement(experience)
        else:
            logger.warning(f"Unknown controller type: {controller}. Skipping training.")

    def _train_imitation(self, experience: Dict[str, Any]) -> None:
        """
        Imitation Learning (IL) Path:
        Goal: Learn 'what would a human do?'
        
        This path is crucial for 'Shadow Learning' where the AI observes 
        and mimics expert behavior without having to explore randomly.
        """
        intent = experience.get("intent")
        logger.debug(f"IL: Registering human demonstration of intent: {intent}")
        
        if isinstance(self.policy, LearnedPolicy):
            # ARCHITECTURAL HOOK:
            # Here we would calculate Cross-Entropy loss between the 
            # policy's predicted distribution and the one-hot human action.
            # loss = self.policy.update_weights(experience)
            pass

    def _train_reinforcement(self, experience: Dict[str, Any]) -> None:
        """
        Reinforcement Learning (RL) Path:
        Goal: Learn 'what actions lead to the best rewards?'
        
        This path uses the computed Reward (decomposed) to adjust 
        policy probabilities.
        """
        reward = experience.get("reward", 0.0)
        logger.debug(f"RL: Processing experience with reward: {reward}")
        
        if isinstance(self.policy, LearnedPolicy):
            # ARCHITECTURAL HOOK:
            # Here we would implement PPO, DQN, or other RL update logic.
            # loss = self.policy.update_weights(experience)
            pass
