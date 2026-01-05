from enum import Enum, auto
from typing import List, Dict, Any
from learning_freeze import LEARNING_STATE

# UNCONDITIONAL FREEZE CHECK
assert LEARNING_STATE == "FROZEN"

class LearningGateResult(Enum):
    PASS = auto()
    FAIL = auto()
    BLOCKED = auto()

class LearningGateDecision:
    def __init__(self, gate_name, result, reason):
        self.gate_name = gate_name
        self.result = result
        self.reason = reason

    def to_dict(self) -> Dict[str, Any]:
        return {
            "gate_name": self.gate_name,
            "result": self.result.name,
            "reason": self.reason
        }

class LearningGateSystem:
    def __init__(self):
        # By construction, learning is impossible.
        # These gates provide an auditable evaluation layer.
        pass

    def evaluate_all(self, experience: Dict[str, Any]) -> List[LearningGateDecision]:
        """
        Evaluates all gates in a fixed order:
        Gate 1: Authority Gate
        Gate 2: Trust Boundary Gate
        Gate 3: Integrity Gate
        Gate 4: Governance Gate
        """
        decisions = []
        
        # Gate 1: Authority Gate
        decisions.append(self._gate_authority(experience))
        
        # Gate 2: Trust Boundary Gate
        decisions.append(self._gate_trust_boundary(experience))
        
        # Gate 3: Integrity Gate
        decisions.append(self._gate_integrity(experience))
        
        # Gate 4: Governance Gate
        decisions.append(self._gate_governance(experience))
        
        return decisions

    def _gate_authority(self, experience: Dict[str, Any]) -> LearningGateDecision:
        # Gate 1 — Authority Gate
        # learning_allowed == False → BLOCKED
        # learning_allowed == True → PASS (does not enable learning)
        lineage = experience.get("lineage", {})
        learning_allowed = lineage.get("learning_allowed", False)
        
        if learning_allowed is True:
            return LearningGateDecision("Authority Gate", LearningGateResult.PASS, "learning_allowed is True")
        else:
            return LearningGateDecision("Authority Gate", LearningGateResult.BLOCKED, "learning_allowed is False")

    def _gate_trust_boundary(self, experience: Dict[str, Any]) -> LearningGateDecision:
        # Gate 2 — Trust Boundary Gate
        # trust_boundary != "SANDBOX" → FAIL
        # SANDBOX → PASS
        lineage = experience.get("lineage", {})
        trust_boundary = lineage.get("trust_boundary")
        
        if trust_boundary == "SANDBOX":
            return LearningGateDecision("Trust Boundary Gate", LearningGateResult.PASS, "Boundary is SANDBOX")
        else:
            return LearningGateDecision("Trust Boundary Gate", LearningGateResult.FAIL, f"Invalid trust boundary: {trust_boundary}")

    def _gate_integrity(self, experience: Dict[str, Any]) -> LearningGateDecision:
        # Gate 3 — Integrity Gate
        # Any integrity violation with severity ≥ HIGH → FAIL
        violations = experience.get("violations", [])
        # We check both string severity and enum-like if applicable, but server passes list of dicts from Auditor
        high_severity_violations = [v for v in violations if v.get("severity") in ["HIGH", "CRITICAL"]]
        
        if high_severity_violations:
            return LearningGateDecision("Integrity Gate", LearningGateResult.FAIL, f"High severity integrity violations: {len(high_severity_violations)}")
        return LearningGateDecision("Integrity Gate", LearningGateResult.PASS, "No high severity integrity violations")

    def _gate_governance(self, experience: Dict[str, Any]) -> LearningGateDecision:
        # Gate 4 — Governance Gate
        # policy_authority != "EXPLICIT_LEARNING_PERMIT" → BLOCKED
        policy_authority = experience.get("policy_authority")
        
        if policy_authority == "EXPLICIT_LEARNING_PERMIT":
            return LearningGateDecision("Governance Gate", LearningGateResult.PASS, "EXPLICIT_LEARNING_PERMIT present")
        else:
            return LearningGateDecision("Governance Gate", LearningGateResult.BLOCKED, f"Invalid policy authority: {policy_authority}")

    @staticmethod
    def get_final_decision() -> str:
        return "LEARNING_BLOCKED_BY_GATE_SYSTEM"
