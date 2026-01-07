import math
import logging
from typing import Dict, Any, Tuple

logger = logging.getLogger(__name__)

class RewardCalculator:
    """
    Computes a continuous reward signal based on the outcome of an action.
    
    STRICT REWARD ARCHITECTURE (v1.0.0):
    - Pure, deterministic logic.
    - No hidden state.
    - Strict component isolation.
    - Death dominance enforcement.
    """

    # CONFIGURATION (Magic numbers explained)
    # These determine the AI's priorities.
    WEIGHTS = {
        "damage_dealt": 0.5,      # Aggression: Incentive for damage dealt
        "damage_received": -0.7,  # Safety: Disincentive for damage received
        "survival": 0.1,          # Baseline: Reward for staying alive
        "wasted_action": -0.02,   # Efficiency: Penalty for pointless actions
        "death_penalty": -1.0      # Dominance: Severe penalty for dying
    }

    @classmethod
    def calculate(cls, result: Dict[str, Any], learning_allowed: bool = False, is_evaluative: bool = False) -> Tuple[float, Dict[str, float], str]:
        """
        Calculates a continuous scalar reward and its breakdown.
        'result' is a dictionary containing outcome metrics.
        Returns (total_reward, breakdown, classification).
        
        Guarantees:
        - Bounded result within [-2.0, 2.0].
        - Deterministic output.
        - Dominance (Death) enforcement.
        - NaN/Inf immunity.
        - Reward classification (DIAGNOSTIC | EVALUATIVE | LEARNING_APPLICABLE).
        """
        # Determine classification
        if learning_allowed:
            classification = "LEARNING_APPLICABLE"
        elif is_evaluative:
            classification = "EVALUATIVE"
        else:
            classification = "DIAGNOSTIC"

        # INVARIANT: If learning_allowed is false, classification cannot be LEARNING_APPLICABLE
        if not learning_allowed and classification == "LEARNING_APPLICABLE":
            classification = "DIAGNOSTIC"

        try:
            # 1. Input Validation (Contract Integrity)
            if not isinstance(result, dict):
                logger.error("REWARD FAILURE: Input result is not a dictionary.")
                return 0.0, {"error_invalid_input": 0.0}, "DIAGNOSTIC"

            outcomes = result.get("outcomes")
            if not isinstance(outcomes, dict):
                logger.warning("REWARD WARNING: Missing or invalid 'outcomes' in result. Using defaults.")
                outcomes = {}

            # 2. Extract & Sanitize (NaN/Inf Immunity + Non-negativity)
            def sanitize(val: Any, default: float = 0.0) -> float:
                try:
                    f_val = float(val)
                    if not math.isfinite(f_val):
                        return default
                    return max(0.0, f_val) # Enforce non-negativity for damage
                except (ValueError, TypeError):
                    return default

            damage_dealt = sanitize(outcomes.get("damage_dealt"))
            damage_received = sanitize(outcomes.get("damage_received"))
            is_alive = bool(outcomes.get("is_alive", True))
            wasted = bool(outcomes.get("action_wasted", False))

            # 3. Component Calculation (Isolation)
            comp_dealt = damage_dealt * cls.WEIGHTS["damage_dealt"]
            comp_received = damage_received * cls.WEIGHTS["damage_received"]
            comp_survival = cls.WEIGHTS["survival"] if is_alive else cls.WEIGHTS["death_penalty"]
            comp_efficiency = cls.WEIGHTS["wasted_action"] if wasted else 0.0

            # 4. Dominance (Death) Rule
            # If dead, we don't care about damage dealt; the outcome is a failure.
            if not is_alive:
                total_reward = cls.WEIGHTS["death_penalty"]
                # Adjust penalty if they also took massive damage, but cap it at protocol bounds
                total_reward += min(0.0, comp_received)
                breakdown = {
                    "damage_dealt_reward": 0.0, # Dominated
                    "damage_received_penalty": comp_received,
                    "survival_reward": cls.WEIGHTS["death_penalty"],
                    "efficiency_penalty": comp_efficiency
                }
            else:
                total_reward = comp_dealt + comp_received + comp_survival + comp_efficiency
                breakdown = {
                    "damage_dealt_reward": comp_dealt,
                    "damage_received_penalty": comp_received,
                    "survival_reward": comp_survival,
                    "efficiency_penalty": comp_efficiency
                }

            # 5. Final Safety Clamp (Boundedness)
            # R MUST be in range [-2.0, 2.0]
            clamped_reward = max(-2.0, min(2.0, float(total_reward)))
            
            # PROTOCOL VERIFICATION HOOK
            assert -2.0 <= clamped_reward <= 2.0, f"Protocol Violation: Reward {clamped_reward} out of bounds"
            
            return clamped_reward, breakdown, classification
            
        except Exception as e:
            logger.error(f"REWARD CRITICAL: Unexpected failure: {e}", exc_info=True)
            return 0.0, {"error_system_failure": 0.0}, "DIAGNOSTIC"
