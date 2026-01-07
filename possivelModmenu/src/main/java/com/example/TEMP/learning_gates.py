from enum import Enum, auto
from typing import List, Dict, Any
from learning_freeze import LEARNING_STATE


# === LEARNING STATE RULES ===
# FROZEN        → No learning of ActivePolicy
# SHADOW_ONLY   → ShadowPolicy may learn
# LIVE_ALLOWED  → Explicit future mode (not used now)

VALID_LEARNING_STATES = {"FROZEN", "SHADOW_ONLY"}
assert LEARNING_STATE in VALID_LEARNING_STATES


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
    """
    Gate system that NEVER allows ActivePolicy learning.
    ShadowPolicy learning is conditionally evaluated.
    """

    def evaluate_all(self, experience: Dict[str, Any]) -> List[LearningGateDecision]:
        decisions = []

        decisions.append(self._gate_authority(experience))
        decisions.append(self._gate_trust_boundary(experience))
        decisions.append(self._gate_integrity(experience))
        decisions.append(self._gate_governance(experience))
        decisions.append(self._gate_learning_state())

        return decisions

    # =========================
    # Gate 1 — Authority Gate
    # =========================
    def _gate_authority(self, experience: Dict[str, Any]) -> LearningGateDecision:
        lineage = experience.get("lineage", {})
        learning_allowed = lineage.get("learning_allowed", False)

        if learning_allowed:
            return LearningGateDecision(
                "Authority Gate",
                LearningGateResult.PASS,
                "learning_allowed explicitly True"
            )

        return LearningGateDecision(
            "Authority Gate",
            LearningGateResult.BLOCKED,
            "learning_allowed is False"
        )

    # =========================
    # Gate 2 — Trust Boundary Gate
    # =========================
    def _gate_trust_boundary(self, experience: Dict[str, Any]) -> LearningGateDecision:
        lineage = experience.get("lineage", {})
        trust_boundary = lineage.get("trust_boundary")

        # ACCEPTED TRUST BOUNDARIES FOR SHADOW LEARNING
        allowed = {
            "SANDBOX",
            "INTERNAL_VERIFIED",
            "HUMAN_PLAY_SESSION"
        }

        if trust_boundary in allowed:
            return LearningGateDecision(
                "Trust Boundary Gate",
                LearningGateResult.PASS,
                f"Boundary accepted: {trust_boundary}"
            )

        return LearningGateDecision(
            "Trust Boundary Gate",
            LearningGateResult.FAIL,
            f"Invalid trust boundary: {trust_boundary}"
        )

    # =========================
    # Gate 3 — Integrity Gate
    # =========================
    def _gate_integrity(self, experience: Dict[str, Any]) -> LearningGateDecision:
        violations = experience.get("violations", [])
        high_severity = [
            v for v in violations
            if v.get("severity") in {"HIGH", "CRITICAL"}
        ]

        if high_severity:
            return LearningGateDecision(
                "Integrity Gate",
                LearningGateResult.FAIL,
                f"High severity violations: {len(high_severity)}"
            )

        return LearningGateDecision(
            "Integrity Gate",
            LearningGateResult.PASS,
            "No high severity integrity violations"
        )

    # =========================
    # Gate 4 — Governance Gate
    # =========================
    def _gate_governance(self, experience: Dict[str, Any]) -> LearningGateDecision:
        if experience.get("policy_mode") == "SHADOW":
            return LearningGateDecision(
                "Governance Gate",
                LearningGateResult.PASS,
                "Shadow mode bypass"
            )

        policy_authority = experience.get("policy_authority")

        allowed = {
            "EXPLICIT_LEARNING_PERMIT",
            "SHADOW_LEARNING_PERMIT"
        }

        if policy_authority in allowed:
            return LearningGateDecision(
                "Governance Gate",
                LearningGateResult.PASS,
                f"Authority accepted: {policy_authority}"
            )

        return LearningGateDecision(
            "Governance Gate",
            LearningGateResult.BLOCKED,
            f"Invalid policy authority: {policy_authority}"
        )

    # =========================
    # Gate 5 — Learning State Gate
    # =========================
    def _gate_learning_state(self) -> LearningGateDecision:
        if LEARNING_STATE == "SHADOW_ONLY":
            return LearningGateDecision(
                "Learning State Gate",
                LearningGateResult.PASS,
                "Shadow learning permitted"
            )

        return LearningGateDecision(
            "Learning State Gate",
            LearningGateResult.BLOCKED,
            f"Learning state is {LEARNING_STATE}"
        )

    # =========================
    # FINAL DECISION
    # =========================
    @staticmethod
    def get_final_decision(decisions: List[LearningGateDecision]) -> str:
        for d in decisions:
            if d.result in {LearningGateResult.FAIL, LearningGateResult.BLOCKED}:
                return "LEARNING_BLOCKED_BY_GATE_SYSTEM"

        return "SHADOW_LEARNING_ALLOWED"
