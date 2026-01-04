from typing import Dict, Any, Tuple

class RewardCalculator:
    """
    Computes a continuous reward signal based on the outcome of an action.
    
    Reward Shaping Philosophy:
    1. Smoothness: Avoid massive terminal spikes (e.g. death) that can 
       destabilize gradients in future RL models.
    2. Dense Signals: Reward small positive steps (damage dealt) and 
       penalize small negative steps (damage received) to provide 
       frequent feedback.
    3. Scale: Keep rewards within a roughly [-1.0, 1.0] range per step 
       to ensure statistical stability.
    4. Decomposability: Rewards are broken down into components for 
       better debuggability and multi-objective optimization.
    5. Determinism: Reward logic must be deterministic and inspectable.
    """

    # CONFIGURATION (Magic numbers explained)
    # These determine the AI's priorities.
    WEIGHTS = {
        "damage_dealt": 0.5,      # High value on aggressive success
        "damage_received": -0.7,  # Higher penalty for taking damage (safety first)
        "survival": 0.1,          # Small constant reward for staying alive
        "wasted_action": -0.02,   # Tiny penalty for actions that do nothing
        "death_penalty": -1.0      # Smooth terminal penalty
    }

    @classmethod
    def calculate(cls, result: Dict[str, Any]) -> Tuple[float, Dict[str, float]]:
        """
        Calculates a continuous scalar reward and its breakdown.
        'result' is a dictionary containing outcome metrics.
        Returns (total_reward, breakdown).
        
        Guarantees:
        - Bounded result within [-2.0, 2.0].
        - Deterministic output.
        - Full breakdown of contributing factors.
        """
        try:
            # Extract metrics with safe defaults
            damage_dealt = float(result.get("damage_dealt", 0.0))
            damage_received = float(result.get("damage_received", 0.0))
            is_alive = bool(result.get("is_alive", True))
            wasted = bool(result.get("action_wasted", False))

            # Breakdown calculation
            breakdown = {
                "damage_dealt_reward": damage_dealt * cls.WEIGHTS["damage_dealt"],
                "damage_received_penalty": damage_received * cls.WEIGHTS["damage_received"],
                "survival_reward": cls.WEIGHTS["survival"] if is_alive else cls.WEIGHTS["death_penalty"],
                "efficiency_penalty": cls.WEIGHTS["wasted_action"] if wasted else 0.0
            }

            total_reward = sum(breakdown.values())

            # Final safety clamp to prevent extreme reward poisoning
            # Scale is kept roughly around [-1, 1], but clamped at [-2, 2] for safety.
            clamped_reward = max(-2.0, min(2.0, float(total_reward)))
            
            return clamped_reward, breakdown
            
        except (ValueError, TypeError):
            # Fallback for malformed results
            return 0.0, {"error_parsing_result": 0.0}
