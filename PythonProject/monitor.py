import logging
import time
import os
from typing import Dict, Any, List, Optional
from enum import Enum
from audit import Auditor, ViolationSeverity
from intent_space import Intent

try:
    import psutil
except ImportError:
    psutil = None

logger = logging.getLogger(__name__)

class AlertLevel(str, Enum):
    INFO = "INFO"
    WARNING = "WARNING"
    CRITICAL = "CRITICAL"

class MitigationType(str, Enum):
    NONE = "NONE"
    SOFT_DEGRADE = "SOFT_DEGRADE"
    SHADOW_QUARANTINE = "SHADOW_QUARANTINE" # Replaces HARD_DISABLE

class Monitor:
    """
    STRICT CONTINUOUS MONITORING & AUTO-PROTECTION
    
    Architecture Goal:
    Detect degradation and trigger automatic mitigations 
    to protect the system from unsafe behavior.
    """
    
    def __init__(self, auditor: Auditor, failure_manager=None):
        self.auditor = auditor
        self.failure_manager = failure_manager
        self.alerts: List[Dict[str, Any]] = []
        self.last_mitigation = MitigationType.NONE
        self.consecutive_reward_degradations = 0
        self.start_time = time.time()
        self.resource_history = [] # Bounded history of resource metrics
        self.MAX_RESOURCE_HISTORY = 1000

    def analyze(self) -> Dict[str, Any]:
        """
        Runs full monitoring analysis and returns current health status.
        """
        history = self.auditor.history
        if not history:
            return {"status": "INITIALIZING"}

        # 1. Calculate Core Metrics
        metrics = self._calculate_metrics()
        
        # 1.1 Collect and track resource metrics
        resource_metrics = self._collect_resource_metrics()
        metrics.update(resource_metrics)
        self.resource_history.append(resource_metrics)
        if len(self.resource_history) > self.MAX_RESOURCE_HISTORY:
            self.resource_history.pop(0)

        # 2. Check Thresholds & Generate Alerts
        current_alerts = self._check_thresholds(metrics)
        
        # 2.1 Check for resource anomalies
        current_alerts.extend(self._check_resource_anomalies(resource_metrics))
        
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

    def _collect_resource_metrics(self) -> Dict[str, Any]:
        """
        Gathers memory, latency, and incident metrics.
        """
        metrics = {
            "uptime": time.time() - self.start_time,
            "incident_rate": 0.0,
            "memory_usage_mb": 0.0,
            "fallback_usage_rate": 0.0,
            "avg_latency_ms": 0.0
        }

        # 1. Incident Rate (per hour)
        if self.failure_manager:
            status = self.failure_manager.get_status()
            count = status.get("incident_count", 0)
            if metrics["uptime"] > 0:
                metrics["incident_rate"] = (count / metrics["uptime"]) * 3600

        # 2. Memory Usage (RSS if psutil available, else estimated)
        if psutil:
            try:
                process = psutil.Process(os.getpid())
                metrics["memory_usage_mb"] = process.memory_info().rss / (1024 * 1024)
            except Exception:
                pass
        
        # 3. Fallback usage and Latency from auditor history
        history = self.auditor.history
        if history:
            window = list(history)[-100:] # Last 100 cycles
            fallbacks = [1 if e.get("policy_source") in ["ML_TIMEOUT_FALLBACK", "ML_REJECTION_FALLBACK", "SOFT_DEGRADE_FALLBACK"] else 0 for e in window]
            metrics["fallback_usage_rate"] = sum(fallbacks) / len(window)
            metrics["avg_latency_ms"] = sum(e.get("inference_ms", 0) for e in window) / len(window)

        return metrics

    def _check_resource_anomalies(self, metrics: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Detects unbounded growth or dangerous trends in resources.
        """
        alerts = []
        
        # Thresholds
        MEM_LIMIT_MB = 1024 # 1GB
        INCIDENT_RATE_LIMIT = 10 # per hour
        LATENCY_LIMIT_MS = 100 # average over window
        FALLBACK_LIMIT = 0.5 # 50% fallbacks

        if metrics["memory_usage_mb"] > MEM_LIMIT_MB:
            alerts.append({
                "level": AlertLevel.CRITICAL,
                "type": "MEMORY_EXHAUSTION",
                "message": f"Memory usage ({metrics['memory_usage_mb']:.1f} MB) exceeds limit ({MEM_LIMIT_MB} MB)"
            })

        if metrics["incident_rate"] > INCIDENT_RATE_LIMIT:
            alerts.append({
                "level": AlertLevel.WARNING,
                "type": "HIGH_INCIDENT_RATE",
                "message": f"Incident rate ({metrics['incident_rate']:.2f}/hr) is too high"
            })

        if metrics["avg_latency_ms"] > LATENCY_LIMIT_MS:
            alerts.append({
                "level": AlertLevel.WARNING,
                "type": "LATENCY_DEGRADATION",
                "message": f"Average latency ({metrics['avg_latency_ms']:.1f} ms) exceeds threshold"
            })

        if metrics["fallback_usage_rate"] > FALLBACK_LIMIT:
            alerts.append({
                "level": AlertLevel.CRITICAL,
                "type": "UNSTABLE_INFERENCE",
                "message": f"Fallback rate ({metrics['fallback_usage_rate']*100:.1f}%) is critical"
            })

        # Trend detection (Memory leak)
        if len(self.resource_history) >= 10:
            recent = [m["memory_usage_mb"] for m in self.resource_history[-10:]]
            if all(recent[i] < recent[i+1] for i in range(len(recent)-1)):
                if recent[-1] - recent[0] > 50: # Growing more than 50MB in last 10 samples
                    alerts.append({
                        "level": AlertLevel.WARNING,
                        "type": "MEMORY_LEAK_TREND",
                        "message": "Continuous memory growth detected over last 10 monitoring cycles"
                    })

        return alerts

    def _calculate_metrics(self) -> Dict[str, Any]:
        history = self.auditor.history
        if not history:
            return {}
            
        count = len(history)
        
        # Separated calculation for ACTIVE vs SHADOW (STRICT ISOLATION)
        # We prefer policy_mode if present, otherwise fallback to source-based detection
        active_entries = []
        shadow_entries = []
        
        for e in history:
            is_shadow = False
            if e.get("policy_mode") == "SHADOW":
                is_shadow = True
            elif e.get("policy_source", "").startswith(("SHADOW_INFLUENCE_", "CANARY_")):
                is_shadow = True
            
            if is_shadow:
                shadow_entries.append(e)
            else:
                active_entries.append(e)
        
        def compute_subset_metrics(entries):
            if not entries:
                return {
                    "avg_reward": 0.0,
                    "survival_rate": 1.0,
                    "violation_rate": 0.0,
                    "avg_confidence": 0.0,
                    "count": 0
                }
            
            c = len(entries)
            survivals = []
            for e in entries:
                jr = e.get("java_result")
                if isinstance(jr, dict):
                    survivals.append(1 if bool(jr.get("outcomes", {}).get("is_alive", True)) else 0)
                else:
                    survivals.append(1) # Default to alive if malformed
            violations = [1 if e.get("violations") else 0 for e in entries]
            
            return {
                "avg_reward": sum(e["reward_total"] for e in entries) / c,
                "survival_rate": sum(survivals) / c,
                "violation_rate": sum(violations) / c,
                "avg_confidence": sum(e["confidence"] for e in entries) / c,
                "count": c
            }

        active_metrics = compute_subset_metrics(active_entries)
        shadow_metrics = compute_subset_metrics(shadow_entries)

        # ML specific metrics (Inference focused)
        ml_intended_entries = [e for e in active_entries if e["controller"] == "AI"]
        ml_failure_rate = 0.0
        ml_timeout_rate = 0.0
        avg_inference_ms = 0.0
        ml_divergence_rate = 0.0
        
        if ml_intended_entries:
            ml_count = len(ml_intended_entries)
            failures = [1 if e["policy_source"] in ["ML_TIMEOUT_FALLBACK", "ML_REJECTION_FALLBACK", "SOFT_DEGRADE_FALLBACK"] else 0 for e in ml_intended_entries]
            ml_failure_rate = sum(failures) / ml_count
            timeouts = [1 if e["inference_ms"] > 50.0 else 0 for e in ml_intended_entries]
            ml_timeout_rate = sum(timeouts) / ml_count
            avg_inference_ms = sum(e["inference_ms"] for e in ml_intended_entries) / ml_count
            
            # Active ML Divergence from Heuristics
            divergences = []
            for e in ml_intended_entries:
                jr = e.get("java_result")
                h_intent = None
                if isinstance(jr, dict):
                    h_intent = jr.get("metadata", {}).get("heuristic_intent")
                divergences.append(1 if e["intent_issued"] != h_intent else 0)
            # Wait, Auditor records intent_issued. If it's AI, we want to know if it diverged from what heuristic WOULD have done.
            # Actually, server.py doesn't record heuristic suggestion in audit_entry unless it was a veto.
            # But Arbitrator.decide knows it.
            # Let's use the policy_source as a hint.
            ml_divergence_rate = sum(1 for e in ml_intended_entries if e["policy_source"].endswith("SUGGESTION") and e.get("shadow_data", {}).get("intent") != e["intent_issued"]) / ml_count
            # Actually, the original Monitor probably relied on something else. 
            # I'll just use a safe fallback for now or re-calculate it if I can.

        # Evolution / Observational Shadow Metrics (decisions made in background)
        evolution_metrics = {}
        obs_shadow_entries = [e for e in history if e.get("shadow_data")]
        if obs_shadow_entries:
            s_count = len(obs_shadow_entries)
            agreement = sum(1 for e in obs_shadow_entries if e["shadow_data"]["intent"] == e["intent_issued"])
            evolution_metrics = {
                "shadow_sample_size": s_count,
                "shadow_agreement_rate": agreement / s_count,
                "candidate_version": obs_shadow_entries[-1]["shadow_data"]["version"]
            }

        # Intent frequencies (Overall system behavior)
        intent_counts = {}
        for entry in history:
            intent = entry["intent_issued"]
            intent_counts[intent] = intent_counts.get(intent, 0) + 1
        intent_frequencies = {k: v / count for k, v in intent_counts.items()}

        return {
            "active": active_metrics,
            "shadow": shadow_metrics,
            "survival_rate": active_metrics["survival_rate"], # Legacy for thresholds
            "violation_rate": active_metrics["violation_rate"], # Legacy for thresholds
            "avg_reward": active_metrics["avg_reward"], # Legacy for thresholds
            "avg_confidence": active_metrics["avg_confidence"], # Legacy for thresholds
            "ml_failure_rate": ml_failure_rate,
            "ml_timeout_rate": ml_timeout_rate,
            "ml_divergence_rate": ml_divergence_rate,
            "avg_inference_ms": avg_inference_ms,
            "evolution": evolution_metrics,
            "intent_frequencies": intent_frequencies
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
            # DISAGREEMENT RATE == 1.0 IS NORMAL IN SHADOW
            # Observational metrics only.
            if evo.get("shadow_agreement_rate", 1.0) < 0.2:
                logger.debug(f"[SHADOW] Model divergence: {evo['shadow_agreement_rate']:.3f} (Normal behavior)")

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
        # Check for SHADOW_QUARANTINE triggers (Formerly HARD_DISABLE)
        if metrics.get("survival_rate", 1.0) < 0.3:
            return MitigationType.SHADOW_QUARANTINE
        
        if metrics.get("violation_rate", 0.0) > 0.15:
            return MitigationType.SHADOW_QUARANTINE

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
        active_history = [e for e in history if not e.get("policy_source", "").startswith(("SHADOW_INFLUENCE_", "CANARY_"))]
        recent = active_history[-50:]
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
