from enum import Enum
from typing import Dict, Any, Optional, List
import logging
import math

logger = logging.getLogger(__name__)

class Intent(str, Enum):
    """
    Fixed set of high-level semantic intents.
    These are independent of specific game mechanics or keybinds.
    """
    PRIMARY_ATTACK = "PRIMARY_ATTACK"
    EVADE = "EVADE"
    MOVE = "MOVE"
    HOLD = "HOLD"
    RELEASE = "RELEASE"
    STOP = "STOP"
    JUMP = "JUMP"

    @property
    def metadata(self) -> Dict[str, Any]:
        """
        Returns architectural metadata for each intent.
        """
        registry = {
            Intent.PRIMARY_ATTACK: {
                "params": [],
                "priority": 3,
                "is_combat": True
            },
            Intent.EVADE: {
                "params": [],
                "priority": 4,
                "is_combat": False
            },
            Intent.MOVE: {
                "params": ["vector"],
                "priority": 1,
                "is_combat": False
            },
            Intent.HOLD: {
                "params": [],
                "priority": 2,
                "is_combat": False
            },
            Intent.RELEASE: {
                "params": [],
                "priority": 2,
                "is_combat": False
            },
            Intent.STOP: {
                "params": [],
                "priority": 5,
                "is_combat": False
            },
            Intent.JUMP: {
                "params": [],
                "priority": 2,
                "is_combat": False
            }
        }
        return registry.get(self, {})

    @classmethod
    def has_value(cls, value: str) -> bool:
        return value in cls._value2member_map_

class ExecutionStatus(str, Enum):
    SUCCESS = "SUCCESS"
    FAILURE = "FAILURE"
    PARTIAL = "PARTIAL"

class FailureReason(str, Enum):
    NONE = "NONE"
    COOLDOWN = "COOLDOWN"
    BLOCKED = "BLOCKED"
    INVALID_STATE = "INVALID_STATE"
    RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED"
    UNKNOWN = "UNKNOWN"

class IntentValidator:
    """
    STRICT validator for Intent Execution Results from Java.
    """
    
    @staticmethod
    def validate_result(raw_result: Any) -> Dict[str, Any]:
        """
        Enforces the EXECUTION RESULT CONTRACT.
        Returns a sanitized result dictionary or a safe-fallback FAILURE result.
        """
        fallback = {
            "status": ExecutionStatus.FAILURE.value,
            "failure_reason": FailureReason.UNKNOWN.value,
            "partial_execution": False,
            "safety_flags": {
                "is_blocked": False,
                "on_cooldown": False,
                "invalid_environment": False
            },
            "outcomes": {
                "damage_dealt": 0.0,
                "damage_received": 0.0,
                "is_alive": True,
                "action_wasted": False
            },
            "metadata": {}
        }

        if not isinstance(raw_result, dict):
            logger.error("CONTRACT VIOLATION: Result must be a dictionary.")
            return fallback

        # 1. Validate Status
        status = raw_result.get("status")
        if status not in [e.value for e in ExecutionStatus]:
            logger.error(f"CONTRACT VIOLATION: Invalid status '{status}'")
            return fallback

        # 2. Validate Failure Reason
        reason = raw_result.get("failure_reason", "UNKNOWN")
        if reason not in [e.value for e in FailureReason]:
            logger.warning(f"CONTRACT WARNING: Unknown failure reason '{reason}'. Defaulting to UNKNOWN.")
            reason = FailureReason.UNKNOWN.value

        # 3. Type Enforcement & Sanitization
        raw_outcomes = raw_result.get("outcomes")
        if not isinstance(raw_outcomes, dict):
            logger.warning("CONTRACT WARNING: Missing or invalid 'outcomes' dictionary. Using defaults.")
            raw_outcomes = {}

        def safe_float(val, default=0.0):
            try:
                f = float(val)
                return f if not math.isnan(f) and not math.isinf(f) else default
            except (ValueError, TypeError):
                return default

        def safe_bool(val, default=False):
            if isinstance(val, bool):
                return val
            if isinstance(val, str):
                return val.lower() in ["true", "1", "yes"]
            return bool(val)

        sanitized = {
            "status": status,
            "failure_reason": reason,
            "partial_execution": safe_bool(raw_result.get("partial_execution")),
            "safety_flags": {
                "is_blocked": safe_bool(raw_result.get("safety_flags", {}).get("is_blocked")),
                "on_cooldown": safe_bool(raw_result.get("safety_flags", {}).get("on_cooldown")),
                "invalid_environment": safe_bool(raw_result.get("safety_flags", {}).get("invalid_environment"))
            },
            "outcomes": {
                "damage_dealt": safe_float(raw_outcomes.get("damage_dealt")),
                "damage_received": safe_float(raw_outcomes.get("damage_received")),
                "is_alive": safe_bool(raw_outcomes.get("is_alive", True), True),
                "action_wasted": safe_bool(raw_outcomes.get("action_wasted"))
            },
            "metadata": raw_result.get("metadata", {})
        }

        return sanitized
