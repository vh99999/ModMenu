import random
import logging
import os
import time
import hashlib
import math
from abc import ABC, abstractmethod
from typing import Dict, Any, Tuple, Optional, List
from intent_space import Intent
from state_parser import StateParser

logger = logging.getLogger(__name__)

class Policy(ABC):
    """
    Abstract base interface for decision policies.
    Ensures all policies (heuristic or ML) follow the same contract.
    """
    
    @abstractmethod
    def decide(self, state: Dict[str, Any]) -> Tuple[str, float, Optional[str], str]:
        """
        Returns a selected Intent string, its associated confidence [0.0, 1.0],
        an optional version/provenance identifier, and the AUTHORITY level.
        Authority: ADVISORY | AUTHORITATIVE | OVERRIDE
        """
        pass

class LearnedPolicy(Policy):
    """
    Abstract base for policies that involve trainable weights (e.g. PyTorch).
    """
    @abstractmethod
    def update_weights(self, batch: Any) -> float:
        """
        Stub for weight updates. Returns the loss or delta.
        """
        pass

class HeuristicCombatPolicy(Policy):
    """
    A rule-based policy that makes decisions based on semantic state.
    Goal: Demonstrate a non-random, explainable, and deterministic decision process.
    """
    VERSION = "GOLDEN_HEURISTIC_v1.0.0"

    def decide(self, state: Dict[str, Any]) -> Tuple[str, float, Optional[str], str]:
        # Extract semantic interpretations
        derived = state.get("derived", {})
        normalized = state.get("normalized", {})
        
        distance_cat = derived.get("distance_category", "FAR")
        can_attack = derived.get("can_attack", False)
        is_threatened = derived.get("is_threatened", False)
        is_obstructed = derived.get("is_obstructed", False)

        # 1. Critical Self-preservation: If threatened and close, evade
        if is_threatened and distance_cat == "CLOSE":
            # High confidence because it's a critical safety rule
            return Intent.EVADE.value, 0.95, self.VERSION, "OVERRIDE"

        # 2. Obstruction Handling: If trying to move but hitting something, jump
        if is_obstructed and distance_cat != "CLOSE":
            return Intent.JUMP.value, 0.85, self.VERSION, "AUTHORITATIVE"

        # 3. Aggression: If close and have energy, attack
        if distance_cat == "CLOSE" and can_attack:
            return Intent.PRIMARY_ATTACK.value, 0.8, self.VERSION, "AUTHORITATIVE"

        # 4. Engagement: If far, move closer
        if distance_cat == "FAR":
            return Intent.MOVE.value, 0.75, self.VERSION, "AUTHORITATIVE"

        # 5. Tactical Positioning: If medium distance
        if distance_cat == "MEDIUM":
            # If we have lots of energy, maybe jump to reposition or close in
            if normalized.get("energy", 0.0) > 0.8:
                return Intent.JUMP.value, 0.6, self.VERSION, "AUTHORITATIVE"
            # Otherwise hold and recover energy
            return Intent.HOLD.value, 0.7, self.VERSION, "AUTHORITATIVE"

        # Default fallback: Stop
        return Intent.STOP.value, 1.0, self.VERSION, "AUTHORITATIVE"

class RandomWeightedPolicy(Policy):
    """
    A simple stochastic policy that picks intents based on fixed weights.
    Includes an exploration factor (entropy).
    
    This serves as a robust baseline and fallback.
    """
    def __init__(self, weights: Optional[Dict[Intent, float]] = None, exploration_rate: float = 0.1):
        # Default weights for intents if none provided
        self.weights = weights or {
            Intent.PRIMARY_ATTACK: 0.2,
            Intent.EVADE: 0.2,
            Intent.MOVE: 0.3,
            Intent.HOLD: 0.1,
            Intent.RELEASE: 0.05,
            Intent.STOP: 0.05,
            Intent.JUMP: 0.1
        }
        self.exploration_rate = exploration_rate

    VERSION = "STOCHASTIC_RANDOM_v1.0.0"

    def decide(self, state: Dict[str, Any]) -> Tuple[str, float, Optional[str], str]:
        """
        Returns a selected Intent and its associated confidence/probability.
        """
        # Ensure we are working with normalized weights
        intents = list(self.weights.keys())
        base_probs = list(self.weights.values())
        total_weight = sum(base_probs)
        normalized_probs = [p / total_weight for p in base_probs]

        # Simple exploration: pick completely at random sometimes
        if random.random() < self.exploration_rate:
            chosen_intent = random.choice(intents)
            # Confidence during exploration is 1/N but scaled by exploration rate
            # for more accurate statistical bookkeeping.
            confidence = 1.0 / len(intents)
            return chosen_intent.value, confidence, self.VERSION, "AUTHORITATIVE"

        # Weighted selection based on policy knowledge
        chosen_intent = random.choices(intents, weights=normalized_probs, k=1)[0]
        
        # Confidence is the probability assigned by the policy
        confidence = self.weights.get(chosen_intent, 0.0) / total_weight
        
        return chosen_intent.value, float(confidence), self.VERSION, "AUTHORITATIVE"

class MLPolicy(LearnedPolicy):
    """
    Deterministic ML Policy wrapper.
    Consumes fixed-length feature vectors and outputs intent distributions.
    """
    def __init__(self, model_manager: 'ModelManager'):
        self.manager = model_manager
        self.last_inference_time = 0.0

    def decide(self, state: Dict[str, Any]) -> Tuple[str, float, Optional[str], Dict[str, float], str]:
        """Wraps decide_version for the active production model."""
        return self.decide_version(state, self.manager.active_model_version)

    def decide_version(self, state: Dict[str, Any], version: Optional[str]) -> Tuple[str, float, Optional[str], Dict[str, float], str]:
        """
        Extracts features and queries a specific model for a suggestion.
        Includes safety envelope: timeout check and output validation.
        """
        start_time = time.monotonic()
        
        # Determine authority based on model's lifecycle state
        authority = "AUTHORITATIVE"
        if version == self.manager.candidate_model_version:
            authority = "ADVISORY"

        try:
            # 1. Extract feature vector (Strictly as per DATASET_PROTOCOL)
            normalized = state.get("normalized", {})
            features = StateParser.get_feature_vector(normalized)

            # 2. Get suggestion from specific model
            intent_str, confidence, actual_version, probs = self.manager.get_model_suggestion(features, version)
            
            # 3. Output Validation
            # 3.1 Canonical Check
            if not Intent.has_value(intent_str):
                logger.error(f"ML POLICY FAILURE: Model {version} suggested non-canonical intent: {intent_str}")
                return Intent.STOP.value, 0.0, actual_version, {}, authority
            
            # 3.2 Confidence Sanity
            if not (0.0 <= confidence <= 1.0):
                logger.error(f"ML POLICY FAILURE: Model {version} invalid confidence range: {confidence}")
                return intent_str, 0.0, actual_version, probs, authority

            # 3.3 BLIND SPOT DETECTION (OOD Features)
            # If health is extremely low, model might not have been trained here.
            if features[0] < 0.01:
                logger.warning(f"ML BLIND SPOT: OOD features detected (health={features[0]:.4f}). Forcing 0.0 confidence.")
                confidence = 0.0

            inference_ms = max(0.0, (time.monotonic() - start_time) * 1000.0)
            
            # 3.4 Timeout Check (Inference Flow)
            if inference_ms > 50.0:
                logger.error(f"ML_TIMEOUT: Inference for {version} took {inference_ms:.2f}ms (Limit: 50ms)")
            
            # We track the LAST inference time for the primary (active) model in the instance variable
            if version == self.manager.active_model_version:
                self.last_inference_time = inference_ms
            
            return intent_str, float(confidence), actual_version, probs, authority

        except Exception as e:
            logger.error(f"ML POLICY CRITICAL: Inference failure for model {version}: {e}")
            return Intent.STOP.value, 0.0, version, {}, authority

    def update_weights(self, batch: List[Dict[str, Any]]) -> float:
        """
        STUB for ML training logic.
        In a real implementation, this would run a forward/backward pass 
        on a PyTorch model and return the loss.
        """
        if not batch:
            return 0.0
            
        # Simulate loss: higher health/energy usually means lower loss 
        # for a good model in this mock environment.
        # Robust handling of different batch formats (raw experience vs dataset record)
        healths = []
        for item in batch:
            if "features" in item:
                healths.append(item["features"][0])
            elif "state" in item and "normalized" in item["state"]:
                healths.append(item["state"]["normalized"].get("health", 0.0))
            else:
                healths.append(0.0)

        avg_health = sum(healths) / len(healths)
        simulated_loss = max(0.01, 1.0 - avg_health)
        
        # In mock mode, we don't actually change weights, 
        # but we return a deterministic "loss" signal.
        return float(simulated_loss)

class ModelManager:
    """
    Handles model loading, versioning, and hot-swapping.
    Guarantees no global state.
    FOLLOWS GOVERNANCE_PROTOCOL.md.
    """
    def __init__(self, models_dir: str = "models"):
        self.models_dir = models_dir
        self.active_model_version = None # PRODUCTION
        self.candidate_model_version = None # CANDIDATE
        self.last_good_version = None
        self.registry: Dict[str, Dict[str, Any]] = {}
        self.intent_order = sorted([i.value for i in Intent])
        self.simulated_delay = 0.0 # For testing timeouts
        
        if not os.path.exists(models_dir):
            try:
                os.makedirs(models_dir)
            except OSError:
                pass

    def register_model(self, metadata: Dict[str, Any]) -> bool:
        """
        Registers a new model version with its metadata.
        """
        version = metadata.get("version")
        if not version:
            logger.error("GOVERNANCE FAILURE: Cannot register model without version identifier.")
            return False
        
        # Validate fingerprint format (loose check for mock)
        if not version.startswith("M-"):
            logger.warning(f"GOVERNANCE WARNING: Version '{version}' does not follow strict fingerprint format.")

        self.registry[version] = metadata
        logger.info(f"GOVERNANCE: Model version {version} registered successfully.")
        return True

    def activate_model(self, version: str) -> bool:
        """
        Explicitly sets a model as PRODUCTION.
        Silent upgrades are forbidden.
        """
        if version not in self.registry:
            logger.error(f"GOVERNANCE FAILURE: Cannot activate unregistered model version {version}.")
            return False
        
        old_version = self.active_model_version
        if old_version == version:
            return True
            
        self.last_good_version = old_version
        self.active_model_version = version
        
        logger.info(f"GOVERNANCE_EVENT: Model activated as PRODUCTION. OLD: {old_version} -> NEW: {version}")
        return True

    def set_candidate_model(self, version: str) -> bool:
        """
        Sets a model as CANDIDATE for shadow evaluation.
        """
        if version not in self.registry:
            logger.error(f"EVOLUTION FAILURE: Cannot set unregistered model {version} as CANDIDATE.")
            return False
        
        self.candidate_model_version = version
        logger.info(f"EVOLUTION_EVENT: Model {version} set as CANDIDATE for shadow evaluation.")
        return True

    def promote_candidate(self) -> bool:
        """
        Promotes the current CANDIDATE to PRODUCTION.
        """
        if not self.candidate_model_version:
            logger.error("EVOLUTION FAILURE: No CANDIDATE model available for promotion.")
            return False
        
        target = self.candidate_model_version
        logger.info(f"EVOLUTION_EVENT: Promoting CANDIDATE {target} to PRODUCTION.")
        
        success = self.activate_model(target)
        if success:
            self.candidate_model_version = None # Clear candidate after promotion
        return success

    def rollback(self) -> bool:
        """
        Emergency rollback to the last known good version.
        Demotes current production to CANDIDATE for inspection.
        """
        if not self.last_good_version:
            logger.error("GOVERNANCE FAILURE: No last_good_version available for rollback.")
            return False
        
        failed_version = self.active_model_version
        target = self.last_good_version
        
        logger.warning(f"GOVERNANCE_EVENT: EMERGENCY ROLLBACK triggered. Reverting to {target}. Demoting {failed_version} to CANDIDATE.")
        
        if self.activate_model(target):
            self.candidate_model_version = failed_version
            return True
        return False

    def get_model_suggestion(self, features: List[float], version: Optional[str]) -> Tuple[str, float, Optional[str], Dict[str, float]]:
        """
        Mock inference logic for a specific version.
        MUST be deterministic.
        Returns (intent, confidence, version, probabilities)
        """
        if self.simulated_delay > 0:
            time.sleep(self.simulated_delay)

        if not version or version not in self.registry:
            return Intent.STOP.value, 0.0, None, {}
            
        # Deterministic pseudo-randomness based on features AND version
        # to simulate different models giving different results.
        feature_sum = sum(features)
        version_hash = int(hashlib.md5(version.encode()).hexdigest(), 16) % 100 / 100.0
        
        combined_signal = feature_sum + version_hash
        
        # Simulate probability distribution
        probs = {}
        for intent in Intent:
            # Random-ish but deterministic probabilities
            h = int(hashlib.md5((version + intent.value).encode()).hexdigest(), 16) % 100
            probs[intent.value] = h / 100.0
        
        # Normalize probabilities
        total = sum(probs.values())
        probs = {k: v / total for k, v in probs.items()}

        if combined_signal > 2.5:
            intent_str = Intent.PRIMARY_ATTACK.value
        elif features[0] < 0.3 + (version_hash * 0.1):
            intent_str = Intent.EVADE.value
        else:
            intent_str = Intent.MOVE.value
            
        confidence = probs[intent_str]
        
        return intent_str, confidence, version, probs

    def get_active_model_suggestion(self, features: List[float]) -> Tuple[str, float, Optional[str], Dict[str, float]]:
        """Wraps get_model_suggestion for the active production model."""
        return self.get_model_suggestion(features, self.active_model_version)

class PolicyArbitrator:
    """
    The FINAL JUDGE of decisions.
    Choosing between ML, Heuristic, and Random.
    Implements ONLINE_ML_PROTOCOL for safety and fallback.
    """
    def __init__(self, 
                 ml_policy: MLPolicy, 
                 heuristic_policy: Policy, 
                 random_policy: Policy,
                 ml_threshold: float = 0.8):
        self.ml_policy = ml_policy
        self.heuristic_policy = heuristic_policy
        self.random_policy = random_policy
        self.ml_threshold = ml_threshold
        self.ml_enabled = True
        
        # EVOLUTION & CANARY
        self.canary_ratio = 0.0 # 0.0 to 1.0 (traffic to candidate)
        
        # ONLINE MONITORING & SOFT DEGRADE
        self.ml_failure_cooldown = 0
        self.metrics = {
            "ml_calls": 0,
            "ml_failures": 0,
            "ml_timeouts": 0,
            "ml_divergence_count": 0,
            "total_cycles": 0,
            "candidate_calls": 0,
            "canary_calls": 0
        }

    def set_canary_ratio(self, ratio: float):
        """Sets the fraction of traffic [0.0, 1.0] diverted to the CANDIDATE model."""
        self.canary_ratio = max(0.0, min(1.0, float(ratio)))
        logger.info(f"EVOLUTION: Canary ratio set to {self.canary_ratio:.2%}")

    def set_ml_enabled(self, enabled: bool):
        """The Kill-switch mechanism."""
        self.ml_enabled = enabled
        if not enabled:
            logger.warning("ARBITRATOR: ML Policy has been DISABLED via kill-switch.")
        else:
            logger.info("ARBITRATOR: ML Policy has been ENABLED.")
            self.ml_failure_cooldown = 0 # Reset cooldown on manual enable

    def decide(self, state: Dict[str, Any]) -> Tuple[str, float, str, str, Optional[str], Optional[str], Optional[Dict[str, Any]]]:
        """
        Arbitration Flow (per ONLINE_ML_PROTOCOL):
        1. HEURISTIC VETO (Survival Guardian)
        2. ML SUGGESTION (Learned path, with safety envelope)
        3. HEURISTIC FALLBACK (Safe path)
        4. RANDOM FALLBACK (Entropy path)
        
        Returns: (intent, confidence, source, authority, fallback_reason, model_version, shadow_data)
        """
        self.metrics["total_cycles"] += 1
        shadow_data = None
        candidate_ver = self.ml_policy.manager.candidate_model_version

        # 0. Shadow Evaluation (Always run if candidate exists and ML is enabled)
        if self.ml_enabled and candidate_ver and self.ml_failure_cooldown == 0:
            c_intent, c_conf, c_ver, c_probs, c_auth = self.ml_policy.decide_version(state, candidate_ver)
            shadow_data = {
                "version": c_ver,
                "intent": c_intent,
                "confidence": c_conf,
                "authority": c_auth
            }
            self.metrics["candidate_calls"] += 1
        
        # 1. Safety Veto Check (Heuristics are the guardians)
        h_intent, h_conf, h_ver, h_auth = self.heuristic_policy.decide(state)
        
        # If Heuristic is VERY confident (Safety rule triggered), it vetoes ML
        if h_conf >= 0.9:
            # We still run ML in shadow mode if enabled for divergence tracking
            if self.ml_enabled and self.ml_failure_cooldown == 0:
                self._run_shadow_ml(state, h_intent)
            return h_intent, h_conf, "HEURISTIC_VETO", h_auth, "SAFETY_RULE_TRIGGERED", h_ver, shadow_data

        # 2. ML Suggestion
        # Soft Degrade Check: If in cooldown, bypass ML
        if self.ml_failure_cooldown > 0:
            self.ml_failure_cooldown -= 1
            res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "SOFT_DEGRADE_FALLBACK", "ML_FAILURE_COOLDOWN"))
            res.append(shadow_data)
            return tuple(res)

        if self.ml_enabled:
            # Decide if we use Canary (Candidate) or Production
            use_candidate = False
            if candidate_ver and random.random() < self.canary_ratio:
                use_candidate = True
                self.metrics["canary_calls"] += 1
            
            target_version = candidate_ver if use_candidate else self.ml_policy.manager.active_model_version
            
            self.metrics["ml_calls"] += 1
            ml_intent, ml_conf, ml_ver, ml_probs, ml_auth = self.ml_policy.decide_version(state, target_version)
            
            # 2.1 Timeout Handling
            if self.ml_policy.last_inference_time > 50.0:
                self.metrics["ml_timeouts"] += 1
                self.metrics["ml_failures"] += 1
                self._trigger_soft_degrade("TIMEOUT")
                res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "ML_TIMEOUT_FALLBACK", "TIMEOUT_EXCEEDED"))
                res.append(shadow_data)
                return tuple(res)

            # 2.2 UNCERTAINTY & ENTROPY CHECK
            ml_entropy = self._calculate_entropy(ml_probs)
            if ml_entropy > 1.5: # Threshold for high uncertainty
                logger.warning(f"ML UNCERTAINTY: High entropy ({ml_entropy:.3f}) detected for model {ml_ver}. Falling back.")
                res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "ML_ENTROPY_FALLBACK", "HIGH_UNCERTAINTY"))
                res.append(shadow_data)
                return tuple(res)

            # 2.3 Confidence & Output Validation
            active_threshold = self.ml_threshold + 0.05 if self.ml_failure_cooldown > 0 else self.ml_threshold
            
            source_prefix = "CANARY_" if use_candidate else "ML_"
            
            if ml_conf >= active_threshold:
                # Track divergence
                if ml_intent != h_intent and h_conf > 0.5:
                    self.metrics["ml_divergence_count"] += 1
                
                return ml_intent, ml_conf, f"{source_prefix}SUGGESTION", ml_auth, None, ml_ver, shadow_data
            
            # ML was certain enough but didn't meet threshold
            if ml_conf < 0.1: # Extreme uncertainty or rejection
                self.metrics["ml_failures"] += 1
                res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, f"{source_prefix}REJECTION_FALLBACK", "LOW_CONFIDENCE"))
                res.append(shadow_data)
                return tuple(res)

            res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, f"{source_prefix}LOW_CONFIDENCE_FALLBACK", "LOW_CONFIDENCE"))
            res.append(shadow_data)
            return tuple(res)

        # 3. Heuristic Fallback
        res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "HEURISTIC_FALLBACK", "ML_DISABLED"))
        res.append(shadow_data)
        return tuple(res)

    def _heuristic_fallback(self, h_intent: str, h_conf: float, h_ver: str, h_auth: str, source: str, reason: str) -> Tuple[str, float, str, str, str, str]:
        if h_conf >= 0.5:
            return h_intent, h_conf, source, h_auth, reason, h_ver
        
        # 4. Random Fallback
        r_intent, r_conf, r_ver, r_auth = self.random_policy.decide({})
        return r_intent, r_conf, "RANDOM_FALLBACK", r_auth, f"{reason}_AND_HEURISTIC_UNCERTAIN", r_ver

    def _run_shadow_ml(self, state: Dict[str, Any], h_intent: str):
        """Runs ML without acting, for divergence and performance tracking."""
        ml_intent, ml_conf, ml_ver, ml_probs, ml_auth = self.ml_policy.decide(state)
        if ml_intent != h_intent:
            self.metrics["ml_divergence_count"] += 1

    def _calculate_entropy(self, probs: Dict[str, float]) -> float:
        """Calculates Shannon entropy of the probability distribution."""
        if not probs:
            return 0.0
        entropy = 0.0
        for p in probs.values():
            if p > 0:
                entropy -= p * math.log(p)
        return entropy

    def _trigger_soft_degrade(self, reason: str, cooldown: int = 10):
        """Disables ML for a specified number of cycles."""
        self.ml_failure_cooldown = cooldown
        logger.warning(f"ARBITRATOR: Soft Degrade triggered due to {reason}. ML bypassed for {cooldown} cycles.")

    def apply_mitigation(self, mitigation: str):
        """
        Applies a mitigation recommendation from the Monitor.
        'mitigation' should be a value from monitor.MitigationType
        """
        if mitigation == "SOFT_DEGRADE":
            if self.ml_failure_cooldown == 0:
                self._trigger_soft_degrade("MONITOR_RECOMMENDATION", cooldown=100)
        elif mitigation == "HARD_DISABLE":
            if self.ml_enabled:
                self.set_ml_enabled(False)
                logger.critical("ARBITRATOR: HARD DISABLE triggered by Monitor.")

    def get_online_metrics(self) -> Dict[str, Any]:
        """Calculates real-time performance metrics."""
        calls = self.metrics["ml_calls"]
        total = self.metrics["total_cycles"]
        if total == 0:
            return {}
            
        return {
            "ml_failure_rate": self.metrics["ml_failures"] / max(1, calls),
            "ml_timeout_rate": self.metrics["ml_timeouts"] / max(1, calls),
            "ml_divergence_rate": self.metrics["ml_divergence_count"] / total,
            "canary_ratio_active": self.canary_ratio,
            "canary_calls": self.metrics["canary_calls"],
            "candidate_calls": self.metrics["candidate_calls"],
            "avg_inference_ms": self.ml_policy.last_inference_time,
            "soft_degrade_active": self.ml_failure_cooldown > 0
        }
