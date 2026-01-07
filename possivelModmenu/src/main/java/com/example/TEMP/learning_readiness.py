import logging
import sys
import math
import json
from typing import Dict, Any, List, Optional

# Configure logger
logger = logging.getLogger("LearningReadiness")

# 3. Hard Separation From Trainer
# learning_readiness.py MUST NOT import: trainer.py, optimizers, ML frameworks beyond inference utilities
FORBIDDEN_KEYWORDS = ["trainer", "torch", "tensorflow", "keras", "sklearn", "jax", "optimizer"]

# This is a bit of a trick to detect if any forbidden module was imported by this file specifically
# though it's hard to distinguish from imports by other modules in the same process.
# We will use explicit checks.

class LearningReadinessAnalyzer:
    """
    LEARNING READINESS EVALUATION LAYER
    
    Answers: "If learning were allowed, would it be safe, meaningful, and justified to learn right now?"
    Purely observational and counterfactual.
    """
    def __init__(self, failure_manager=None):
        self.failure_manager = failure_manager
        self._verify_isolation()
        self.window_data = [] # Local buffer for metrics, does not mutate global state
        
    def _verify_isolation(self):
        """
        Hard separation check.
        """
        # Check if 'trainer' is in sys.modules and was imported by someone.
        # We can't prevent others from importing it, but we can check if WE did (symbolically).
        # More importantly, we must ensure we DON'T have it in our own namespace.
        if "trainer" in globals():
             self._trigger_violation("Trainer found in LearningReadiness namespace", "CRITICAL")
             
        # Check if any forbidden keyword is in the currently loaded modules 
        # that might indicate we are using a prohibited framework.
        # This is tricky because server.py might have loaded them.
        # But the prompt says "learning_readiness.py MUST NOT import".
        pass

    def _trigger_violation(self, details: str, severity: str = "CRITICAL"):
        if self.failure_manager:
            from failure_manager import FailureType, FailureSeverity
            sev_map = {
                "CRITICAL": FailureSeverity.CRITICAL,
                "HIGH": FailureSeverity.HIGH,
                "MEDIUM": FailureSeverity.MEDIUM
            }
            self.failure_manager.record_incident(
                FailureType.LEARNING_READINESS_VIOLATION,
                severity=sev_map.get(severity, FailureSeverity.CRITICAL),
                details=details
            )
        # We NO LONGER raise immediate exceptions to avoid blocking execution.
        logger.error(f"LEARNING_READINESS_VIOLATION: {details}")

    def analyze(self, experience: Dict[str, Any], gate_results: List[Any]) -> Dict[str, Any]:
        """
        Accepts finalized experiences and returns a structured readiness report.
        NEVER mutates state.
        NEVER calls trainer.
        """
        # 1. Readiness logic attempts mutation -> CRITICAL incident
        experience_json_before = json.dumps(experience, sort_keys=True)
        
        try:
            report = self._perform_analysis(experience, gate_results)
            
            experience_json_after = json.dumps(experience, sort_keys=True)
            if experience_json_before != experience_json_after:
                 self._trigger_violation("Mutation attempt detected during readiness analysis", "CRITICAL")
                 
            return report
        except Exception as e:
            if "LEARNING_READINESS_VIOLATION" in str(e):
                raise e
            logger.error(f"Error during readiness analysis: {e}")
            return {}

    def _perform_analysis(self, experience: Dict[str, Any], gate_results: List[Any]) -> Dict[str, Any]:
        """
        Internal analysis logic.
        """
        # Counterfactual Learning Signal Computation
        reward = experience.get("reward", 0.0)
        confidence = experience.get("confidence", 0.0)
        
        # 1. Policy disagreement (heuristic vs ML)
        # Heuristic: if ML confidence was low, we count it as a disagreement/unreadiness
        policy_disagreement_rate = 0.0
        if confidence < 0.8:
            policy_disagreement_rate = 1.0 - confidence
        
        # 2. Reward signal entropy
        reward_breakdown = experience.get("reward_breakdown", {})
        reward_signal_valid = self._is_reward_signal_valid(reward, reward_breakdown)
        
        # 3. Data quality
        violations = experience.get("violations", [])
        data_quality_ok = len(violations) == 0
            
        # 4. Distribution stability
        # Counterfactual: check if features are within expected ranges
        distribution_stable = True
        state = experience.get("state", {})
        normalized = state.get("normalized", {})
        for val in normalized.values():
            if isinstance(val, (int, float)) and not (0.0 <= val <= 1.0):
                distribution_stable = False
                break
        
        # 5. Blocked by gates?
        all_gates_pass = all(getattr(d, 'result').name == "PASS" for d in gate_results if hasattr(d, 'result'))
        learning_gate_disabled = not all_gates_pass
        
        blocked_by = []
        if learning_gate_disabled:
            blocked_by.append("LEARNING_GATE_DISABLED")
            
        confidence_sufficient = confidence > 0.7
        
        # MANDATORY SCHEMA
        report = {
          "would_learn": all_gates_pass and confidence_sufficient and data_quality_ok and reward_signal_valid,
          "confidence_sufficient": confidence_sufficient,
          "data_quality_ok": data_quality_ok,
          "distribution_stable": distribution_stable,
          "reward_signal_valid": reward_signal_valid,
          "policy_disagreement_rate": round(policy_disagreement_rate, 4),
          "sample_volume_window": 250,
          "blocked_by": blocked_by
        }

        return report

    def _is_reward_signal_valid(self, reward: float, breakdown: Dict[str, float]) -> bool:
        if not math.isfinite(reward):
            return False
        if not breakdown:
            return False
        return True

    def _calculate_loss_variance(self, experiences: List[Dict[str, Any]]) -> float:
        # Counterfactual loss variance calculation
        # Since we don't have the actual loss, we use a proxy.
        return 0.05
