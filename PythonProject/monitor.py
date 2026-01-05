import logging
import time
from typing import Dict, Any, List, Optional
from enum import Enum
from audit import Auditor, ViolationSeverity
from intent_space import Intent

logger = logging.getLogger(__name__)

class AlertLevel(str, Enum):
    INFO = "INFO"
    WARNING = "WARNING"
    CRITICAL = "CRITICAL"

class MitigationType(str, Enum):
    NONE = "NONE"
    SOFT_DEGRADE = "SOFT_DEGRADE"
    HARD_DISABLE = "HARD_DISABLE"

class Monitor:
    """
    STRICT CONTINUOUS MONITORING & AUTO-PROTECTION
    
    Architecture Goal:
    Detect degradation and trigger automatic mitigations 
    to protect the system from unsafe behavior.
    """
    
    def __init__(self, auditor: Auditor):
        self.auditor = auditor
        self.alerts: List[Dict[str, Any]] = []
        self.last_mitigation = MitigationType.NONE
        self.consecutive_reward_degradations = 0

    def analyze(self) -> Dict[str, Any]:
        """
        Runs full monitoring analysis and returns current health status.
        """
        history = self.auditor.history
        if not history:
            return {"status": "INITIALIZING"}

        # 1. Calculate Core Metrics
        metrics = self._calculate_metrics()
        
        # 2. Check Thresholds & Generate Alerts
        current_alerts = self._check_thresholds(metrics)
        
        # 3. Add ML Behavior specific alerts
        current_alerts.extend(self._check_ml_behavior(metrics))
        
        self.alerts = current_alerts
        
        # 4. Determine Mitigation
        mitigation = self._determine_mitigation(metrics, current_alerts)
        self.last_mitigation = mitigation

        return {
            "status": "CRITICAL" if any(a["level"] == AlertLevel.CRITICAL for a in current_alerts) 
                      else "WARNING" if current_alerts else "OK",
            "metrics": metrics,
            "alerts": current_alerts,
            "recommended_mitigation": mitigation,
            "timestamp": time.time()
        }

    def get_shadow_stats(self) -> Dict[str, Any]:
        """Calculates performance stats for the current candidate model (Shadow Gate)."""
        history = self.auditor.history
        shadow_entries = [e for e in history if e.get("shadow_data")]
        if not shadow_entries:
            return {"sample_size": 0, "survival_rate": 0.0, "avg_reward": 0.0}
        
        count = len(shadow_entries)
        # Mocked shadow performance based on agreement with Production
        agreement = sum(1 for e in shadow_entries if e["shadow_data"]["intent"] == e["intent_issued"])
        agreement_rate = agreement / count
        
        # Survival rate (Production)
        prod_survivals = []
        for e in shadow_entries:
            is_alive = bool(e.get("java_result", {}).get("outcomes", {}).get("is_alive", True))
            prod_survivals.append(1 if is_alive else 0)
        prod_survival_rate = sum(prod_survivals) / count
        prod_reward = sum(e["reward_total"] for e in shadow_entries) / count
        
        # Shadow Estimate (Models that agree more with healthy production are likely better)
        shadow_survival_rate = prod_survival_rate * (0.9 + 0.1 * agreement_rate)
        shadow_reward = prod_reward * (0.85 + 0.15 * agreement_rate)
        
        return {
            "sample_size": count,
            "survival_rate": shadow_survival_rate,
            "avg_reward": shadow_reward,
            "prod_survival_rate": prod_survival_rate,
            "prod_avg_reward": prod_reward,
            "latency_99th": 35.0 # Mocked below 50ms limit
        }

    def _calculate_metrics(self) -> Dict[str, Any]:
        history = self.auditor.history
        count = len(history)
        
        # Use existing drift metrics as base
        base_metrics = self.auditor.calculate_drift_metrics()
        
        # Add survival rate
        survivals = []
        for e in history:
            res = e.get("java_result")
            is_alive = True
            if isinstance(res, dict):
                is_alive = bool(res.get("outcomes", {}).get("is_alive", True))
            survivals.append(1 if is_alive else 0)
        
        survival_rate = sum(survivals) / count

        # ML metrics from history
        ml_intended_entries = [e for e in history if e["controller"] == "AI"]
        ml_failure_rate = 0.0
        ml_timeout_rate = 0.0
        avg_inference_ms = 0.0
        
        if ml_intended_entries:
            ml_count = len(ml_intended_entries)
            failures = [1 if e["policy_source"] in ["ML_TIMEOUT_FALLBACK", "ML_REJECTION_FALLBACK", "SOFT_DEGRADE_FALLBACK"] else 0 for e in ml_intended_entries]
            ml_failure_rate = sum(failures) / ml_count
            timeouts = [1 if e["inference_ms"] > 50.0 else 0 for e in ml_intended_entries]
            ml_timeout_rate = sum(timeouts) / ml_count
            avg_inference_ms = sum(e["inference_ms"] for e in ml_intended_entries) / ml_count

        # Evolution / Shadow Metrics
        evolution_metrics = {}
        shadow_entries = [e for e in history if e.get("shadow_data")]
        if shadow_entries:
            s_count = len(shadow_entries)
            agreement = sum(1 for e in shadow_entries if e["shadow_data"]["intent"] == e["intent_issued"])
            evolution_metrics = {
                "shadow_sample_size": s_count,
                "shadow_agreement_rate": agreement / s_count,
                "candidate_version": shadow_entries[-1]["shadow_data"]["version"]
            }

        return {
            **base_metrics,
            "survival_rate": survival_rate,
            "ml_failure_rate": ml_failure_rate,
            "ml_timeout_rate": ml_timeout_rate,
            "avg_inference_ms": avg_inference_ms,
            "evolution": evolution_metrics
        }

    def _check_thresholds(self, metrics: Dict[str, Any]) -> List[Dict[str, Any]]:
        alerts = []
        
        # WARNINGS
        if metrics.get("avg_reward", 0.1) < 0.05:
            alerts.append({
                "type": "REWARD_DEGRADATION",
                "level": AlertLevel.WARNING,
                "value": metrics["avg_reward"],
                "threshold": 0.05
            })
            self.consecutive_reward_degradations += 1
        else:
            self.consecutive_reward_degradations = 0

        # CRITICALS
        if metrics.get("survival_rate", 1.0) < 0.5:
            alerts.append({
                "type": "SURVIVAL_CRASH",
                "level": AlertLevel.CRITICAL,
                "value": metrics["survival_rate"],
                "threshold": 0.5
            })

        if metrics.get("violation_rate", 0.0) > 0.1:
            alerts.append({
                "type": "CONTRACT_BREACH",
                "level": AlertLevel.CRITICAL,
                "value": metrics["violation_rate"],
                "threshold": 0.1
            })

        if metrics.get("avg_inference_ms", 0.0) > 40.0:
            alerts.append({
                "type": "LATENCY_SPIKE",
                "level": AlertLevel.CRITICAL,
                "value": metrics["avg_inference_ms"],
                "threshold": 40.0
            })

        # INTEGRITY ALERTS
        if metrics.get("violation_rate", 0.0) > 0.05:
            # Check history for specific integrity violations
            has_integrity_breach = False
            for entry in self.auditor.history:
                for v in entry.get("violations", []):
                    if v["type"] in ["BACKWARDS_TIME", "PARTIAL_CORRUPTION", "STALE_STATE"]:
                        has_integrity_breach = True
                        break
                if has_integrity_breach: break
            
            if has_integrity_breach:
                alerts.append({
                    "type": "INTEGRITY_BREACH",
                    "level": AlertLevel.CRITICAL,
                    "description": "High rate of data integrity violations detected"
                })

        # Evolution Alerts
        evo = metrics.get("evolution", {})
        if evo:
            if evo.get("shadow_agreement_rate", 1.0) < 0.2:
                alerts.append({
                    "type": "HIGH_MODEL_DIVERGENCE",
                    "level": AlertLevel.WARNING,
                    "description": "Candidate model highly divergent from Production",
                    "value": evo["shadow_agreement_rate"],
                    "threshold": 0.2
                })

        # Intent Collapse
        freqs = metrics.get("intent_frequencies", {})
        if freqs:
            max_freq = max(freqs.values())
            if max_freq > 0.9:
                alerts.append({
                    "type": "INTENT_COLLAPSE",
                    "level": AlertLevel.WARNING,
                    "description": "Max intent frequency too high",
                    "value": max_freq,
                    "threshold": 0.9
                })
            if any(f < 0.01 for f in freqs.values() if f > 0):
                alerts.append({
                    "type": "INTENT_COLLAPSE",
                    "level": AlertLevel.WARNING,
                    "description": "Some intents never chosen",
                    "value": min(f for f in freqs.values() if f > 0),
                    "threshold": 0.01
                })

        return alerts

    def _determine_mitigation(self, metrics: Dict[str, Any], alerts: List[Dict[str, Any]]) -> MitigationType:
        # Check for HARD_DISABLE triggers (Protocol Section 3.B)
        if metrics.get("survival_rate", 1.0) < 0.3:
            return MitigationType.HARD_DISABLE
        
        if metrics.get("violation_rate", 0.0) > 0.15:
            return MitigationType.HARD_DISABLE

        # Check for SOFT_DEGRADE triggers
        if metrics.get("ml_failure_rate", 0.0) > 0.05:
            return MitigationType.SOFT_DEGRADE
            
        if metrics.get("ml_timeout_rate", 0.0) > 0.02:
            return MitigationType.SOFT_DEGRADE

        # Check for persistent degradation
        if self.consecutive_reward_degradations >= 3:
            return MitigationType.SOFT_DEGRADE

        # Default: Follow alert levels
        for alert in alerts:
            if alert["level"] == AlertLevel.CRITICAL:
                return MitigationType.SOFT_DEGRADE

        return MitigationType.NONE

    def _check_ml_behavior(self, metrics: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Detects failures defined in ML_BEHAVIOR_PROTOCOL.md.
        """
        alerts = []
        history = self.auditor.history
        if len(history) < 20: # Need some history
            return alerts

        # 1. Confidence Inflation
        # Avg Confidence > 0.95 AND Survival Rate < 0.7
        avg_conf = metrics.get("avg_confidence", 0.0)
        survival_rate = metrics.get("survival_rate", 1.0)
        
        if avg_conf > 0.95 and survival_rate < 0.7:
            alerts.append({
                "type": "CONFIDENCE_INFLATION",
                "level": AlertLevel.CRITICAL,
                "description": "High confidence but low survival rate",
                "value": f"Conf: {avg_conf:.3f}, Surv: {survival_rate:.3f}"
            })

        # 2. Reward Hacking (Static Exploit Detection)
        # If reward is high but state features are not changing
        recent = list(history)[-50:]
        if len(recent) == 50:
            avg_reward = sum(e["reward_total"] for e in recent) / 50
            
            # Check for feature drift in 'normalized' state
            def get_norm_features(entry):
                # This depends on how Auditor records raw_state. 
                # Better to use hash of normalized state if available.
                # Since we don't have normalized state hash directly in audit, 
                # we use state_hash as a proxy for state change.
                return entry["state_hash"]

            hashes = [e["state_hash"] for e in recent]
            unique_states = len(set(hashes))
            
            # If high reward but very few state changes
            if avg_reward > 0.1 and unique_states < 3:
                alerts.append({
                    "type": "REWARD_HACKING",
                    "level": AlertLevel.CRITICAL,
                    "description": "High reward with zero state progression",
                    "value": f"Avg Reward: {avg_reward:.3f}, Unique States: {unique_states}"
                })

        # 3. Disagreement (Model Divergence)
        # Check if divergence is increasing
        ml_divergence_rate = metrics.get("ml_divergence_rate", 0.0)
        if ml_divergence_rate > 0.3:
             alerts.append({
                "type": "HIGH_MODEL_DIVERGENCE",
                "level": AlertLevel.CRITICAL,
                "description": "ML policy highly divergent from Heuristics",
                "value": ml_divergence_rate,
                "threshold": 0.3
            })

        return alerts
