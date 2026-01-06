import json
import logging
import time
from typing import Dict, Any, List, Optional, Tuple

logger = logging.getLogger(__name__)

class DriftVerifier:
    """
    STRICT DETERMINISTIC DRIFT DETECTION
    
    Replays identical state inputs and ensures identical inference outputs.
    Follows Phase 9 Integrity requirements.
    """
    def __init__(self):
        self.golden_pairs: Dict[str, Dict[str, Any]] = {}
        self.drift_logs: List[Dict[str, Any]] = []

    def register_golden(self, state_hash: str, input_state: Any, expected_output: Dict[str, Any]):
        """
        Registers a state and its corresponding inference output as 'Golden'.
        """
        if state_hash not in self.golden_pairs:
            self.golden_pairs[state_hash] = {
                "input": input_state,
                "output": {
                    "intent": expected_output.get("intent"),
                    "confidence": expected_output.get("confidence"),
                    "policy_source": expected_output.get("policy_source")
                },
                "timestamp": time.time()
            }
            logger.info(f"DRIFT_VERIFIER: Registered golden state {state_hash[:8]}")

    def verify_drift(self, state_hash: str, current_output: Dict[str, Any]) -> Tuple[bool, Optional[str]]:
        """
        Compares current inference against golden reference.
        Returns (is_drifted, reason)
        """
        if state_hash not in self.golden_pairs:
            return False, None

        golden = self.golden_pairs[state_hash]["output"]
        
        deviations = []
        if current_output.get("intent") != golden["intent"]:
            deviations.append(f"Intent mismatch: {current_output.get('intent')} != {golden['intent']}")
        
        if current_output.get("confidence") != golden["confidence"]:
             deviations.append(f"Confidence mismatch: {current_output.get('confidence')} != {golden['confidence']}")
             
        if current_output.get("policy_source") != golden["policy_source"]:
             deviations.append(f"Policy mismatch: {current_output.get('policy_source')} != {golden['policy_source']}")

        if deviations:
            reason = "; ".join(deviations)
            logger.critical(f"CRITICAL_DRIFT DETECTED for state {state_hash[:8]}: {reason}")
            return True, reason

        return False, None

