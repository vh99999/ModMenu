import json
import time
import hashlib
import logging
from typing import Dict, Any, List, Optional, Tuple
from enum import Enum
from collections import deque

from intent_space import Intent, ExecutionStatus, FailureReason

logger = logging.getLogger(__name__)

class ViolationSeverity(str, Enum):
    INFO = "INFO"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

class Auditor:
    """
    Central Auditor for recording and validating request/response cycles.
    Maintains a sliding window for drift detection.
    """
    def __init__(self, window_size: int = 1000):
        self.window_size = window_size
        self.history = deque(maxlen=window_size)
        self.quarantine: List[Dict[str, Any]] = []

    def record_cycle(self, 
                     experience_id: str,
                     raw_state: Any,
                     state_version: int,
                     intent_issued: str,
                     confidence: float,
                     policy_source: str,
                     controller: str,
                     raw_java_result: Any,
                     reward_total: float,
                     reward_breakdown: Dict[str, float],
                     fallback_reason: Optional[str] = None,
                     model_version: Optional[str] = None,
                     inference_ms: float = 0.0,
                     shadow_data: Optional[Dict[str, Any]] = None,
                     java_timestamp: Optional[float] = None,
                     missing_fields: int = 0,
                     lineage: Optional[Dict[str, Any]] = None,
                     authority: str = "UNKNOWN",
                     reward_class: str = "DIAGNOSTIC",
                     full_payload: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Records a single cycle, runs validation, and returns the audit entry.
        """
        # PROTOCOL VERIFICATION HOOK
        assert experience_id, "Protocol Violation: experience_id missing in audit record"
        assert authority in ["ADVISORY", "AUTHORITATIVE", "OVERRIDE"], f"Protocol Violation: Illegal authority '{authority}'"
        
        try:
            state_str = json.dumps(raw_state, sort_keys=True)
            state_hash = hashlib.sha256(state_str.encode('utf-8')).hexdigest()
        except (TypeError, ValueError, OverflowError):
            logger.error("AUDIT ERROR: Failed to serialize raw_state for hashing. Using fallback hash.")
            state_hash = hashlib.sha256(str(raw_state).encode('utf-8')).hexdigest()

        # Enforce mandatory lineage metadata
        if lineage is None:
            lineage = {
                "source": "UNKNOWN",
                "trust_boundary": "EXTERNAL_UNTRUSTED",
                "learning_allowed": False,
                "decision_authority": "UNKNOWN"
            }

        entry = {
            "experience_id": experience_id,
            "timestamp": time.time(),
            "java_timestamp": java_timestamp,
            "state_hash": state_hash,
            "raw_state": raw_state, # Preserving raw sensor values as received
            "state_version": state_version,
            "intent_issued": intent_issued,
            "confidence": confidence,
            "policy_source": policy_source,
            "authority": authority,
            "controller": controller,
            "fallback_reason": fallback_reason,
            "model_version": model_version,
            "inference_ms": inference_ms,
            "shadow_data": shadow_data, # Record evolution shadow decisions
            "java_result": raw_java_result,
            "reward_total": reward_total,
            "reward_breakdown": reward_breakdown,
            "reward_class": reward_class,
            "missing_fields": missing_fields,
            "lineage": lineage,
            "full_payload": full_payload,
            "violations": []
        }

        # 1. Detect Contract Violations
        violations = ViolationDetector.detect(entry, self.history)
        entry["violations"] = violations

        # 2. Add to history for drift detection
        self.history.append(entry)

        # 3. Log high-severity violations
        for v in violations:
            if v["severity"] in [ViolationSeverity.HIGH, ViolationSeverity.CRITICAL]:
                logger.error(f"AUDIT VIOLATION [{v['severity']}]: {v['type']} - {v.get('field', '')}")

        return entry

    def check_quality_gate(self, entry: Dict[str, Any]) -> bool:
        """
        Returns True if the experience passes all quality gates and can be used for training.
        """
        is_valid = DataQualityGate.is_pass(entry)
        if not is_valid:
            self.quarantine.append(entry)
            logger.warning(f"AUDIT: Experience quarantined due to quality gate failure. Hash: {entry['state_hash'][:8]}")
        return is_valid

    def calculate_drift_metrics(self) -> Dict[str, Any]:
        """
        Calculates drift metrics over the current sliding window.
        """
        if not self.history:
            return {}

        count = len(self.history)
        intent_counts = {}
        total_conf = 0.0
        total_reward = 0.0
        violation_count = 0
        
        # Granular Metrics
        duplicates = 0
        stales = 0
        missing_fields_total = 0
        fallbacks = 0

        for entry in self.history:
            intent = entry["intent_issued"]
            intent_counts[intent] = intent_counts.get(intent, 0) + 1
            total_conf += entry["confidence"]
            total_reward += entry["reward_total"]
            
            v_types = [v["type"] for v in entry.get("violations", [])]
            if entry["violations"]:
                violation_count += 1
            
            if "DUPLICATE_STATE" in v_types:
                duplicates += 1
            if "STALE_STATE" in v_types:
                stales += 1
            
            missing_fields_total += entry.get("missing_fields", 0)
            
            if entry.get("fallback_reason") and entry.get("fallback_reason") != "NONE":
                fallbacks += 1

        metrics = {
            "window_size": count,
            "intent_frequencies": {k: v / count for k, v in intent_counts.items()},
            "avg_confidence": total_conf / count,
            "avg_reward": total_reward / count,
            "violation_rate": violation_count / count,
            "duplicate_rate": duplicates / count,
            "stale_rate": stales / count,
            "avg_missing_fields": missing_fields_total / count,
            "fallback_rate": fallbacks / count
        }

        # Check for collapses
        for intent, freq in metrics["intent_frequencies"].items():
            if freq < 0.01:
                logger.warning(f"DRIFT WARNING: Intent frequency collapse for '{intent}': {freq:.4f}")
        
        if metrics["avg_confidence"] > 0.99:
            logger.warning(f"DRIFT WARNING: Confidence inflation detected: {metrics['avg_confidence']:.4f}")
        
        if metrics["violation_rate"] > 0.05:
            logger.error(f"DRIFT ERROR: High violation rate: {metrics['violation_rate']:.4f}")

        return metrics

class ViolationDetector:
    """
    STRICT contract violation detection.
    """
    @staticmethod
    def detect(entry: Dict[str, Any], history: deque = None) -> List[Dict[str, Any]]:
        violations = []
        res = entry["java_result"]
        
        # 0. INTEGRITY CHECKS (New)
        # 0.1 Deduplication
        if history and len(history) > 0:
            last_entry = history[-1]
            if entry["state_hash"] == last_entry["state_hash"]:
                violations.append({
                    "type": "DUPLICATE_STATE",
                    "severity": ViolationSeverity.LOW,
                    "description": "State hash matches immediately preceding cycle"
                })

        # 0.2 Time-To-Live (TTL)
        if entry["java_timestamp"] is not None:
            age = entry["timestamp"] - entry["java_timestamp"]
            if age > 0.1: # 100ms threshold
                violations.append({
                    "type": "STALE_STATE",
                    "severity": ViolationSeverity.MEDIUM,
                    "description": f"State is {age*1000:.1f}ms old (Limit: 100ms)"
                })
            
            # 0.3 Monotonicity
            if history and len(history) > 0:
                last_jt = history[-1].get("java_timestamp")
                if last_jt and entry["java_timestamp"] < last_jt:
                    violations.append({
                        "type": "BACKWARDS_TIME",
                        "severity": ViolationSeverity.HIGH,
                        "description": "Java timestamp moved backwards"
                    })
                
                # 0.4 Tick-rate Mismatch (New)
                if last_jt:
                    dt = entry["java_timestamp"] - last_jt
                    # Expected 50ms (0.05s). Allow 20ms-500ms range for "normal" jitter.
                    # If it's outside this, or exactly 0 (without being a duplicate), it's weird.
                    if (dt < 0.01 or dt > 1.0) and entry["state_hash"] != last_entry["state_hash"]:
                        violations.append({
                            "type": "TICK_RATE_MISMATCH",
                            "severity": ViolationSeverity.MEDIUM,
                            "description": f"Abnormal tick interval: {dt*1000:.1f}ms"
                        })

        # 0.4 Partial Corruption
        if entry["missing_fields"] > 2: # More than 50% of 4 core fields (health, energy, dist, colliding)
            violations.append({
                "type": "PARTIAL_CORRUPTION",
                "severity": ViolationSeverity.HIGH,
                "description": f"{entry['missing_fields']} fields missing/defaulted"
            })

        # 0.5 DATA LINEAGE VALIDATION
        lineage = entry.get("lineage", {})
        required_lineage = ["source", "trust_boundary", "learning_allowed", "decision_authority"]
        for field in required_lineage:
            if field not in lineage or lineage[field] == "UNKNOWN":
                violations.append({
                    "type": "DATA_LINEAGE_VIOLATION",
                    "severity": ViolationSeverity.HIGH,
                    "field": field,
                    "description": f"Mandatory lineage field '{field}' is missing or UNKNOWN"
                })

        # 0.6 HOSTILE PAYLOAD DETECTION
        payload = entry.get("full_payload")
        if payload:
            hostile_fields = ["allow_learning_if", "force_learning", "suppress_incidents", "bypass_gates"]
            for field in hostile_fields:
                if field in payload:
                    violations.append({
                        "type": "HOSTILE_PAYLOAD",
                        "severity": ViolationSeverity.CRITICAL,
                        "field": field,
                        "description": f"Hostile field detected in experience payload: {field}"
                    })
            
            # Check for policy_override not matching expected management flow
            # (In experience payloads, we allow policy_override but it should be audited)
            if payload.get("policy_override") and payload.get("controller") == "AI":
                violations.append({
                    "type": "POLICY_INTERFERENCE",
                    "severity": ViolationSeverity.HIGH,
                    "description": "Experience payload attempted to override policy for AI controller"
                })

        if not isinstance(res, dict):
            violations.append({
                "type": "MALFORMED_PAYLOAD",
                "severity": ViolationSeverity.HIGH,
                "description": "Java result is not a dictionary"
            })
            return violations

        # 1. Unknown Enums
        status = res.get("status")
        if status not in [e.value for e in ExecutionStatus]:
            violations.append({
                "type": "UNKNOWN_ENUM",
                "severity": ViolationSeverity.MEDIUM,
                "field": "status",
                "value": str(status)
            })

        reason = res.get("failure_reason")
        if reason and reason not in [e.value for e in FailureReason]:
            violations.append({
                "type": "UNKNOWN_ENUM",
                "severity": ViolationSeverity.MEDIUM,
                "field": "failure_reason",
                "value": str(reason)
            })

        # 2. Missing Fields
        mandatory = ["status", "failure_reason", "outcomes"]
        for field in mandatory:
            if field not in res:
                violations.append({
                    "type": "MISSING_FIELD",
                    "severity": ViolationSeverity.HIGH,
                    "field": field
                })

        # 3. Intent Mismatch (If Java reports what it did, but here we only have the result)
        # Assuming metadata might contain the intent Java thought it was executing
        java_intent = res.get("metadata", {}).get("intent_executed")
        if java_intent and java_intent != entry["intent_issued"]:
            violations.append({
                "type": "INTENT_MISMATCH",
                "severity": ViolationSeverity.CRITICAL,
                "expected": entry["intent_issued"],
                "actual": java_intent
            })

        # 4. Partial Execution without explanation
        if res.get("partial_execution") is True:
            flags = res.get("safety_flags", {})
            if not any(flags.values()):
                violations.append({
                    "type": "EXPLANATION_MISSING",
                    "severity": ViolationSeverity.MEDIUM,
                    "description": "partial_execution set but no safety flags are True"
                })

        # 5. Reward Invariants Verification
        reward = entry["reward_total"]
        if not (-2.0 <= reward <= 2.0):
            violations.append({
                "type": "REWARD_BOUNDS_VIOLATION",
                "severity": ViolationSeverity.CRITICAL,
                "value": reward
            })

        outcomes = res.get("outcomes", {})
        if outcomes.get("is_alive") is False and reward > -1.0:
            violations.append({
                "type": "REWARD_DOMINANCE_VIOLATION",
                "severity": ViolationSeverity.CRITICAL,
                "description": "is_alive is false but reward > -1.0"
            })

        return violations

class DataQualityGate:
    """
    HARD BLOCKERS for training data.
    """
    @staticmethod
    def is_pass(entry: Dict[str, Any]) -> bool:
        # 1. Critical Violations are immediate fail
        for v in entry["violations"]:
            if v["severity"] == ViolationSeverity.CRITICAL:
                return False
            if v["type"] in ["MALFORMED_PAYLOAD", "MISSING_FIELD", "PARTIAL_CORRUPTION", "BACKWARDS_TIME", "STALE_STATE", "DATA_LINEAGE_VIOLATION"]:
                return False
            # Deduplication: We don't train on duplicates
            if v["type"] == "DUPLICATE_STATE":
                return False

        # 2. Missing Reward Breakdown
        if not entry.get("reward_breakdown"):
            return False

        # 3. Unknown Intent
        if not Intent.has_value(entry["intent_issued"]):
            return False

        return True
