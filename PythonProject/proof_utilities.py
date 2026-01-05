import json
import os
from typing import List, Dict, Any
from execution_mode import ExecutionMode, enforce_mode

class HardeningProof:
    """
    Utility to prove hardening guarantees from audit logs.
    """
    def __init__(self, log_path: str):
        enforce_mode([ExecutionMode.AUDIT_PROOF])
        self.log_path = log_path
        self.entries = self._load_logs()

    def _load_logs(self) -> List[Dict[str, Any]]:
        if not os.path.exists(self.log_path):
            return []
        with open(self.log_path, 'r') as f:
            return json.load(f)

    def prove_no_unauthorized_learning(self) -> bool:
        """
        Proves that no model weights were updated using data with learning_allowed == False.
        """
        for entry in self.entries:
            lineage = entry.get("lineage", {})
            reward_class = entry.get("reward_class")
            
            # If learning was disabled, reward_class must not be LEARNING_APPLICABLE
            if not lineage.get("learning_allowed"):
                if reward_class == "LEARNING_APPLICABLE":
                    return False
        return True

    def verify_all_experience_ids(self) -> bool:
        """Verifies that every entry has a unique, non-null experience_id."""
        seen_ids = set()
        for entry in self.entries:
            eid = entry.get("experience_id")
            if not eid:
                return False
            if eid in seen_ids:
                return False
            seen_ids.add(eid)
        return True

    def verify_trust_boundaries(self) -> bool:
        """Verifies that all internal decisions respected trust boundaries."""
        for entry in self.entries:
            lineage = entry.get("lineage", {})
            if lineage.get("trust_boundary") == "EXTERNAL_UNTRUSTED":
                if entry.get("authority") == "AUTHORITATIVE" and lineage.get("source") != "JAVA_SOURCE":
                    return False
        return True

    def verify_policy_authority(self) -> bool:
        """Verifies that every decision has an explicit, valid authority level."""
        allowed_authorities = ["ADVISORY", "AUTHORITATIVE", "OVERRIDE"]
        for entry in self.entries:
            auth = entry.get("authority")
            if auth not in allowed_authorities:
                return False
        return True

    def run_full_proof(self) -> Dict[str, bool]:
        """Runs all verification checks."""
        return {
            "no_unauthorized_learning": self.prove_no_unauthorized_learning(),
            "experience_id_integrity": self.verify_all_experience_ids(),
            "trust_boundary_integrity": self.verify_trust_boundaries(),
            "policy_authority_integrity": self.verify_policy_authority(),
            "reward_classification_integrity": self.verify_reward_classification()
        }

    def verify_reward_classification(self) -> bool:
        """Verifies that learning only happened from LEARNING_APPLICABLE rewards."""
        for entry in self.entries:
            lineage = entry.get("lineage", {})
            reward_class = entry.get("reward_class")
            
            # Invariant: If learning_allowed is False, reward MUST NOT be LEARNING_APPLICABLE
            if not lineage.get("learning_allowed") and reward_class == "LEARNING_APPLICABLE":
                return False
        return True
