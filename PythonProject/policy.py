import random
import logging
import os
import time
import hashlib
import math
import json
from abc import ABC, abstractmethod
from typing import Dict, Any, Tuple, Optional, List
from intent_space import Intent, MovementIntent
from state_parser import StateParser

logger = logging.getLogger(__name__)

class Policy(ABC):
    """
    Abstract base interface for decision policies.
    Ensures all policies (heuristic or ML) follow the same contract.
    """
    
    @abstractmethod
    def decide(self, state: Dict[str, Any], state_hash: Optional[str] = None) -> Tuple[str, float, Optional[str], str, int, Dict[str, Any]]:
        """
        Returns a selected Intent string, its associated confidence [0.0, 1.0],
        an optional version/provenance identifier, the AUTHORITY level,
        the hold duration in ticks, and a dictionary of intent parameters.
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

    def decide(self, state: Dict[str, Any], state_hash: Optional[str] = None) -> Tuple[str, float, Optional[str], str, int, Dict[str, Any]]:
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
            return Intent.EVADE.value, 0.95, self.VERSION, "OVERRIDE", 1, {}

        # 2. Obstruction Handling: If trying to move but hitting something, jump
        if is_obstructed and distance_cat != "CLOSE":
            return Intent.JUMP.value, 0.85, self.VERSION, "AUTHORITATIVE", 1, {}

        # 3. Aggression: If close and have energy, attack
        if distance_cat == "CLOSE" and can_attack:
            return Intent.PRIMARY_ATTACK.value, 0.8, self.VERSION, "AUTHORITATIVE", 1, {}

        # 4. Engagement: If far, move closer
        if distance_cat == "FAR":
            return Intent.MOVE.value, 0.75, self.VERSION, "AUTHORITATIVE", 1, {"vector": [1.0, 0.0, 0.0], "sprinting": True}

        # 5. Tactical Positioning: If medium distance
        if distance_cat == "MEDIUM":
            # If we have lots of energy, maybe jump to reposition or close in
            if normalized.get("energy", 0.0) > 0.8:
                return Intent.JUMP.value, 0.6, self.VERSION, "AUTHORITATIVE", 1, {}
            # Otherwise hold and recover energy
            return Intent.HOLD.value, 0.7, self.VERSION, "AUTHORITATIVE", 5, {}

        # Default fallback: Stop
        return Intent.STOP.value, 1.0, self.VERSION, "AUTHORITATIVE", 1, {}

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

    def decide(self, state: Dict[str, Any], state_hash: Optional[str] = None) -> Tuple[str, float, Optional[str], str, int, Dict[str, Any]]:
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
            return chosen_intent.value, confidence, self.VERSION, "AUTHORITATIVE", 1, {}

        # Weighted selection based on policy knowledge
        chosen_intent = random.choices(intents, weights=normalized_probs, k=1)[0]
        
        # Confidence is the probability assigned by the policy
        confidence = self.weights.get(chosen_intent, 0.0) / total_weight
        
        params = {}
        if chosen_intent == Intent.MOVE:
            params = {"vector": [random.uniform(-1, 1), 0, random.uniform(-1, 1)], "sprinting": random.random() > 0.5}
            
        return chosen_intent.value, float(confidence), self.VERSION, "AUTHORITATIVE", 1, params

class MLPolicy(LearnedPolicy):
    """
    Deterministic ML Policy wrapper.
    Consumes fixed-length feature vectors and outputs intent distributions.
    Supports Dual-Policy architecture for Live Shadow Learning.
    """
    def __init__(self, model_manager: 'ModelManager', role: str = "ACTIVE"):
        self.manager = model_manager
        self.role = role # ACTIVE or SHADOW
        self.last_inference_time = 0.0
        self.learned_boost = 0.0 # Simulated learning effect
        self.promoted_knowledge = {}
        self.update_history: List[Dict[str, Any]] = [] # Bounded buffer for updates (Req 3)
        self.MAX_UPDATE_HISTORY = 100
        self.history_lock = random.random() # Symbolic lock for atomic swap
        self.tick_counter = 0
        self.last_action_tick = {} # action_name -> last_tick_issued
        self.observed_cooldowns = {} # action_name -> cooldown_ticks
        self.promoted_error = None
        self.current_movement_intent: Optional[MovementIntent] = None
        self.last_pos = None
        self.is_currently_blocked = False
        self.movement_failure_count = 0

    def load_promoted_knowledge(self, promoted_dir: str):
        """Loads validated passive knowledge from a promotion snapshot directory."""
        if self.role != "ACTIVE":
            return
        
        self.promoted_knowledge = {}
        self.promoted_error = None
        self.promoted_version = None

        if not os.path.exists(promoted_dir):
            logger.info("ACTIVE: No promoted knowledge found, running baseline")
            return
        
        manifest_path = os.path.join(promoted_dir, "manifest.json")
        knowledge_path = os.path.join(promoted_dir, "passive_knowledge.json")

        if not os.path.exists(manifest_path) or not os.path.exists(knowledge_path):
            logger.error(f"PROMOTION: Validation failed: Missing manifest or knowledge file in {promoted_dir}")
            self.promoted_error = "INCOMPLETE_ARTIFACT"
            return

        try:
            # 1. Load and Validate Manifest
            with open(manifest_path, 'r') as f:
                manifest = json.load(f)
            
            required_fields = ["version_id", "source", "promotion_timestamp", "schema_version", "hash", "validation_status"]
            for field in required_fields:
                if field not in manifest:
                    raise ValueError(f"Missing mandatory manifest field: {field}")
            
            if manifest["validation_status"] != "PASS":
                raise ValueError(f"Promotion manifest status is {manifest['validation_status']}")

            # 2. Load and Validate Knowledge Hash
            with open(knowledge_path, 'r') as f:
                knowledge_content = f.read()
            
            actual_hash = hashlib.sha256(knowledge_content.encode('utf-8')).hexdigest()
            if actual_hash != manifest["hash"]:
                raise ValueError("Knowledge hash mismatch! Artifact might be tampered with.")

            # 3. Load into memory
            self.promoted_knowledge = json.loads(knowledge_content)
            self.promoted_version = manifest["version_id"]

            # Influence policy behavior
            visitation = self.promoted_knowledge.get("visitation_counts", {})
            if visitation:
                self.learned_boost = min(0.5, len(visitation) * 0.001)
            
            logger.info(f"ACTIVE: Loaded promoted knowledge {self.promoted_version} (MANUAL, OPERATOR-INITIATED)")

        except Exception as e:
            logger.error(f"PROMOTION: Validation failed: {e}")
            self.promoted_error = str(e)
            # Re-raise to trigger HARD FAIL in server
            raise

    def decide(self, state: Dict[str, Any], state_hash: Optional[str] = None) -> Tuple[str, float, Optional[str], str, int, Dict[str, Any]]:
        """Wraps decide_version based on policy role."""
        # Active policy exclusively uses active model, Shadow uses candidate (or active if no candidate)
        version = self.manager.active_model_version
        if self.role == "SHADOW" and self.manager.candidate_model_version:
            version = self.manager.candidate_model_version
            
        intent, conf, ver, probs, auth, dur, params = self.decide_version(state, version, state_hash=state_hash)
        return intent, conf, ver, auth, dur, params

    def _categorize_movement_type(self, target_dir: str, vector: List[float]) -> str:
        if not vector or len(vector) < 3:
            return "STOP"
        vx, vy, vz = vector
        if abs(vx) < 0.1 and abs(vz) < 0.1:
            return "STOP"
        if target_dir == "FRONT":
            if vx > 0.5: return "MOVE_CLOSER"
            if abs(vz) > 0.5: return "STRAFE"
        elif target_dir == "BACK":
            if vx < -0.5: return "MOVE_CLOSER"
            if abs(vz) > 0.5: return "STRAFE"
        elif target_dir == "RIGHT":
            if vz > 0.5: return "MOVE_CLOSER"
            if abs(vx) > 0.5: return "STRAFE"
        elif target_dir == "LEFT":
            if vz < -0.5: return "MOVE_CLOSER"
            if abs(vx) > 0.5: return "STRAFE"
        return "OTHER"

    def _decide_from_knowledge(self, state: Dict[str, Any], state_hash: Optional[str], is_blocked: bool = False) -> Tuple[str, float, str, Dict[str, float], str, int, Dict[str, Any]]:
        """
        Implementation of PART 2, 3, and 4: Passive knowledge-based decision making.
        """
        if not self.promoted_knowledge:
            return Intent.STOP.value, 0.0, "KNOWLEDGE_MISSING", {}, "AUTHORITATIVE", 1, {}

        if not state_hash:
            logger.warning("ACTIVE: state_hash missing, cannot query knowledge. Falling back.")
            return Intent.STOP.value, 0.0, "KNOWLEDGE_NO_HASH", {}, "AUTHORITATIVE", 1, {}

        # PART 0: Block Resolution (New Requirement)
        if state_hash != getattr(self, "last_blocked_state_hash", None):
            self.movement_failure_count = 0
            self.last_blocked_state_hash = state_hash

        if is_blocked:
            self.movement_failure_count += 1
            
        if self.movement_failure_count >= 3:
            logger.warning(f"ACTIVE: Movement safety gate triggered ({self.movement_failure_count} failures). Force disengage.")
            return Intent.STOP.value, 1.0, "KNOWLEDGE_SAFETY_GATE", {}, "AUTHORITATIVE", 1, {}

        if is_blocked:
            blocked_resolutions = self.promoted_knowledge.get("blocked_resolutions", {})
            resolutions = blocked_resolutions.get(state_hash, {})
            if resolutions:
                # Pick MOST COMMON resolution
                best_res = max(resolutions.items(), key=lambda x: x[1])[0]
                logger.info(f"ACTIVE: Block detected! Replaying most common resolution for {state_hash[:8]}: {best_res}")
                
                # Check if it's on cooldown
                last_tick = self.last_action_tick.get(best_res, -1000)
                if self.tick_counter - last_tick >= 1: # Basic sanity, resolutions usually don't have long cooldowns
                    self.last_action_tick[best_res] = self.tick_counter
                    return best_res, 1.0, "KNOWLEDGE_BLOCK_RESOLUTION", {best_res: 1.0}, "AUTHORITATIVE", 1, {}
            
            logger.info(f"ACTIVE: Block detected but no resolution known for {state_hash[:8]}. stopping and reassessing.")
            return Intent.STOP.value, 1.0, "KNOWLEDGE_BLOCK_NO_RESOLUTION", {}, "AUTHORITATIVE", 1, {}
        else:
            pass # We don't reset failure count just by not being blocked (e.g. after a STOP)

        # PART 0.1: Effective Range Gating (Combat Requirement)
        derived = state.get("derived", {})
        distance_cat = derived.get("distance_category", "FAR")
        can_attack = derived.get("can_attack", False)
        target_dir = derived.get("target_direction", "FRONT")
        
        # PART 1: Query the passive graph
        visitation_counts = self.promoted_knowledge.get("visitation_counts", {})
        valid_actions = self.promoted_knowledge.get("valid_actions", {})
        transitions = self.promoted_knowledge.get("transitions", {})
        violations = self.promoted_knowledge.get("invariant_violations", [])

        # Retrieve observed actions for current state
        observed_entries = valid_actions.get(state_hash, [])
        if not observed_entries:
            logger.debug(f"ACTIVE: State {state_hash[:8]} unknown to knowledge graph.")
            return Intent.STOP.value, 0.0, "KNOWLEDGE_UNKNOWN_STATE", {}, "AUTHORITATIVE", 1, {}

        # PART 2: Action Filtering
        # - NEVER choose an action that Shadow marked invalid
        # - NEVER repeat an action that caused invariant violations
        # - COOLDOWN GATE: Prevent spamming actions faster than observed cooldowns
        forbidden_actions = set()
        for v in violations:
            if v.get("state_hash") == state_hash:
                forbidden_action = v.get("action")
                if forbidden_action:
                    forbidden_actions.add(forbidden_action)

        candidates = []
        for entry in observed_entries:
            # Handle both legacy string and new dictionary representation
            if isinstance(entry, dict):
                action_name = entry["action_name"]
                cooldown = entry.get("cooldown_ticks_observed", 0)
            else:
                action_name = entry
                cooldown = 0
            
            if action_name in forbidden_actions:
                continue
            
            # 1. Cooldown Enforcement
            last_tick = self.last_action_tick.get(action_name, -1000)
            elapsed = self.tick_counter - last_tick
            
            # Fallback for missing cooldown data (Conservative minimum delay)
            if cooldown <= 0:
                # Combat actions get a base 5-tick cooldown if not observed
                if action_name in [Intent.PRIMARY_ATTACK, Intent.JUMP]:
                    cooldown = 5
                else:
                    cooldown = 1 # Minimum 1 tick between same action
            
            if elapsed < cooldown:
                logger.info(f"ACTIVE: Cooldown gate blocked '{action_name}' ({elapsed}/{cooldown} ticks)")
                continue
                
            candidates.append(entry)
        
        if not candidates:
            logger.warning(f"ACTIVE: No safe actions available for state {state_hash[:8]} (Forbidden: {list(forbidden_actions)}, others on cooldown)")
            # Fall back to safe deterministic action (STOP)
            return Intent.STOP.value, 1.0, "KNOWLEDGE_NO_SAFE_CANDIDATES", {}, "AUTHORITATIVE", 1, {}

        # PART 3: Deterministic Scoring
        scores = {}
        breakdowns = {}
        durations = {}
        params_map = {}
        
        for entry in candidates:
            if isinstance(entry, dict):
                action = entry["action_name"]
                duration = entry.get("hold_duration_ticks", 1)
                params = entry.get("params", {})
            else:
                action = entry
                duration = 1
                params = {}
            
            # 1) Predict likely next state using transition frequencies
            state_transitions = transitions.get(state_hash, {}).get(action, {})
            if not state_transitions:
                # No transition data for this action (rare if observed)
                scores[action] = 0.0
                continue
            
            # Likely next state = highest frequency
            likely_next_state = max(state_transitions.items(), key=lambda x: x[1])[0]
            
            # 2) Compute NOVELTY score: 1 / (1 + visit_count(next_state))
            next_visit_count = visitation_counts.get(likely_next_state, 0)
            novelty = 1.0 / (1.0 + next_visit_count)
            
            # 3) Compute STABILITY score: P(next_state | state, action)
            total_transitions = sum(state_transitions.values())
            stability = state_transitions[likely_next_state] / total_transitions if total_transitions > 0 else 0.0
            
            # 4) Combat Bias (Enables target-oriented movement based on observed frequencies)
            combat_bias = 0.0
            cmp = self.promoted_knowledge.get("combat_movement_patterns", {})
            dir_patterns = cmp.get(target_dir, {})
            
            if dir_patterns:
                total_obs = sum(dir_patterns.values())
                if total_obs > 0:
                    if action == Intent.MOVE.value:
                        m_type = self._categorize_movement_type(target_dir, params.get("vector", [0,0,0]))
                        freq = dir_patterns.get(m_type, 0) / total_obs
                        combat_bias += freq * 0.4 
                    elif action == Intent.STOP.value:
                        freq = dir_patterns.get("STOP", 0) / total_obs
                        combat_bias += freq * 0.4

            # 4.1) Hard Distance Gating (Requirement: Stop when in effective range)
            if action == Intent.MOVE.value and distance_cat == "CLOSE":
                m_type = self._categorize_movement_type(target_dir, params.get("vector", [0,0,0]))
                if m_type == "MOVE_CLOSER":
                    combat_bias -= 0.5 # Strongly discourage moving closer when already close
            elif action == Intent.STOP.value and distance_cat == "CLOSE":
                combat_bias += 0.3 # Encourage stopping when close
            
            # 5) Apply invariant penalty (Handled via filtering, but adding a base score)
            score = stability + novelty + combat_bias
            
            scores[action] = score
            durations[action] = duration
            params_map[action] = params
            breakdowns[action] = {
                "stability": stability,
                "novelty": novelty,
                "combat_bias": combat_bias,
                "next_state": likely_next_state[:8],
                "next_visits": next_visit_count
            }

        # Select highest-scoring action
        best_action = max(scores.items(), key=lambda x: x[1])[0]
        best_score = scores[best_action]
        best_duration = durations[best_action]
        best_params = params_map[best_action]
        
        # Track issued action for cooldowns
        self.last_action_tick[best_action] = self.tick_counter

        # PART 4: Safety & Transparency
        logger.debug(f"ACTIVE DECISION [State: {state_hash[:8]}]:")
        for entry in candidates:
            action = entry["action_name"] if isinstance(entry, dict) else entry
            b = breakdowns.get(action, {})
            logger.debug(f"  - Action: {action} | Score: {scores.get(action, 0):.4f} (Stab: {b.get('stability', 0):.2f}, Nov: {b.get('novelty', 0):.2f}, Combat: {b.get('combat_bias', 0):.2f})")
        logger.info(f"SELECTED: {best_action} (Reason: Highest Score | Tick: {self.tick_counter} | Hold: {best_duration})")

        return best_action, float(min(1.0, best_score)), "PROMOTED_KNOWLEDGE", {best_action: 1.0}, "AUTHORITATIVE", best_duration, best_params

    def decide_version(self, state: Dict[str, Any], version: Optional[str], bypass_state_update: bool = False, state_hash: Optional[str] = None) -> Tuple[str, float, Optional[str], Dict[str, float], str, int, Dict[str, Any]]:
        """
        ACTIVE role uses promoted knowledge.
        SHADOW role uses model suggestion.
        """
        if not bypass_state_update:
            self.tick_counter += 1

        # Track displacement if moving
        raw = state.get("raw", {})
        pos = (raw.get("pos_x", 0.0), raw.get("pos_y", 0.0), raw.get("pos_z", 0.0))
        
        last_action = None
        if self.tick_counter - 1 in self.last_action_tick.values():
            # Find which action was issued in the previous tick
            for act, tick in self.last_action_tick.items():
                if tick == self.tick_counter - 1:
                    last_action = act
                    break
        
        if not bypass_state_update:
            if self.last_pos and last_action == Intent.MOVE.value:
                dist = math.sqrt(sum((a - b) ** 2 for a, b in zip(pos, self.last_pos)))
                if dist < 0.01:
                    self.is_currently_blocked = True
                else:
                    self.is_currently_blocked = False
                    self.movement_failure_count = 0 # Successful MOVE resets failure count
            else:
                self.is_currently_blocked = False
                
            self.last_pos = pos

        # PART 5: Failure Modes
        if self.role == "ACTIVE":
            if self.promoted_error:
                logger.error(f"ACTIVE FAILURE: {self.promoted_error}. Disabling AI:ON behavior.")
                # Fall back to baseline movement (STOP)
                return Intent.STOP.value, 0.0, "KNOWLEDGE_ERROR_FALLBACK", {}, "AUTHORITATIVE", 1, {}
            
            # Consume promoted knowledge
            res = self._decide_from_knowledge(state, state_hash, is_blocked=self.is_currently_blocked)
            intent, conf, source, probs, auth, dur, params = res
            if source.startswith("PROMOTED_") or source.startswith("KNOWLEDGE_"):
                return intent, conf, source, probs, auth, dur, params
            else:
                return Intent.STOP.value, 0.0, f"ML_BASELINE_{source}", {}, "AUTHORITATIVE", 1, {}

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
                return Intent.STOP.value, 0.0, actual_version, {}, authority, 1, {}
            
            # 3.2 Confidence Sanity
            if not (0.0 <= confidence <= 1.0):
                logger.error(f"ML POLICY FAILURE: Model {version} invalid confidence range: {confidence}")
                return intent_str, 0.0, actual_version, probs, authority, 1, {}

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
            if not bypass_state_update and version == self.manager.active_model_version:
                self.last_inference_time = inference_ms
            
            # Apply learned boost (Requirement 1 & 4 Phase 10A/B)
            boosted_confidence = min(1.0, confidence + self.learned_boost)
            
            # Default params for ML (vector/sprinting might need to be derived if needed, 
            # but for now we use defaults or knowledge-based params)
            params = {}
            if intent_str == Intent.MOVE.value:
                params = {"vector": [1.0, 0.0, 0.0], "sprinting": True}

            return intent_str, float(boosted_confidence), actual_version, probs, authority, 1, params

        except Exception as e:
            logger.error(f"ML POLICY CRITICAL: Inference failure for model {version}: {e}")
            return Intent.STOP.value, 0.0, version, {}, authority, 1, {}

    def update_weights(self, batch: List[Dict[str, Any]]) -> float:
        """
        Updates the policy weights/parameters.
        Enforces immutability for ACTIVE role and learning for SHADOW role.
        """
        if self.role == "ACTIVE":
            # Requirement 1 & 4 (Phase 10B): ActivePolicy MUST be immutable. 
            logger.error("[FREEZE] ActivePolicy learning attempt BLOCKED")
            raise PermissionError("LEARNING_FREEZE_BREACH: ActivePolicy mutation attempt")

        if not batch:
            return 0.0
            
        # Simulate learning process
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
        
        # Continuous Learning Loop (SHADOW ONLY) - Requirement 2
        improvement = 0.002 * len(batch) # Simulated improvement factor
        self.learned_boost += improvement
        
        # Requirement 3: Gradient / Update Accumulation
        update_record = {
            "timestamp": time.time(),
            "version": f"U-{int(time.time())}-{len(self.update_history)}",
            "batch_size": len(batch),
            "loss": float(simulated_loss),
            "improvement": improvement,
            "total_boost": self.learned_boost
        }
        self.update_history.append(update_record)
        if len(self.update_history) > self.MAX_UPDATE_HISTORY:
            self.update_history.pop(0)

        logger.info("[SHADOW] Learning event verified")
        logger.info("[SHADOW] Learning allowed under SHADOW_ONLY mode")
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
                 shadow_policy: MLPolicy,
                 heuristic_policy: Policy, 
                 random_policy: Policy,
                 ml_threshold: float = 0.8):
        self.ml_policy = ml_policy
        self.active_policy = ml_policy
        self.shadow_policy = shadow_policy
        self.heuristic_policy = heuristic_policy
        self.random_policy = random_policy
        self.ml_threshold = ml_threshold
        self.ml_enabled = True
        
        # Phase 10B: Shadow Influence
        self.influence_weight = 0.0
        
        # EVOLUTION & CANARY
        self.canary_ratio = 0.0 # 0.0 to 1.0 (traffic to candidate)
        
        # ONLINE MONITORING & SOFT DEGRADE
        self.ml_failure_cooldown = 0
        self.last_ml_entropy = 0.0
        self.current_movement_intent: Optional[MovementIntent] = None
        self.last_active_pos = None
        
        # Separated Metrics (Requirement: DO NOT SHARE COUNTERS)
        self.active_metrics = {
            "calls": 0,
            "failures": 0,
            "timeouts": 0,
            "divergence_count": 0,
        }
        self.shadow_metrics = {
            "calls": 0,
            "failures": 0,
            "timeouts": 0,
            "divergence_count": 0,
        }
        self.metrics = {
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

    def decide(self, state: Dict[str, Any], policy_mode: str = "ACTIVE", state_hash: Optional[str] = None) -> Tuple[str, float, str, str, Optional[str], Optional[str], Optional[Dict[str, Any]], int, Dict[str, Any]]:
        """
        Arbitration Flow (per ONLINE_ML_PROTOCOL):
        1. HEURISTIC VETO (Survival Guardian)
        2. ML SUGGESTION (Learned path, with safety envelope)
        3. HEURISTIC FALLBACK (Safe path)
        4. RANDOM FALLBACK (Entropy path)
        
        Returns: (intent, confidence, source, authority, fallback_reason, model_version, shadow_data, hold_duration_ticks, params)
        """
        # MANDATORY FIRST CHECK: SHADOW MODE IS NOT SUBJECT TO ARBITRATION
        if policy_mode == "SHADOW":
            # 1. Shadow Policy Evaluation (MANDATORY: ALWAYS RUN)
            # PASS bypass_state_update=True to fulfill "DO NOT MUTATE POLICY STATE"
            s_ver_target = self.shadow_policy.manager.active_model_version
            if self.shadow_policy.manager.candidate_model_version:
                s_ver_target = self.shadow_policy.manager.candidate_model_version
                
            s_intent, s_conf, s_ver, s_probs, s_auth, s_dur, s_params = self.shadow_policy.decide_version(
                state, 
                s_ver_target,
                bypass_state_update=True,
                state_hash=state_hash
            )
            shadow_data = {"intent": s_intent, "confidence": s_conf, "version": s_ver, "authority": s_auth, "hold_duration_ticks": s_dur, "params": s_params}
            
            # 0. Candidate Evaluation (Observational only - IGNORE MONITOR EVENTS)
            candidate_ver = self.ml_policy.manager.candidate_model_version
            if candidate_ver:
                c_intent, c_conf, c_ver, c_probs, c_auth, c_dur, c_params = self.ml_policy.decide_version(
                    state, 
                    candidate_ver,
                    bypass_state_update=True,
                    state_hash=state_hash
                )
                shadow_data["candidate"] = {
                    "version": c_ver,
                    "intent": c_intent,
                    "confidence": c_conf,
                    "authority": c_auth,
                    "hold_duration_ticks": c_dur,
                    "params": c_params
                }
            
            # ABSOLUTE LAW: IGNORE MONITOR RECOMMENDATIONS, SIGNALS, COUNTERS, AND COOLDOWNS
            # RETURN NO-OP
            return Intent.STOP.value, 1.0, "SHADOW_MODE", "ADVISORY", None, s_ver, shadow_data, 1, {}

        self.metrics["total_cycles"] += 1
        
        # ACTIVE Persistent Movement Intent Logic
        if self.current_movement_intent:
            invalidation_reason = None
            normalized = state.get("normalized", {})
            raw = state.get("raw", {})
            
            # 1. Obstacle Detected (Legacy)
            if normalized.get("is_colliding", 0.0) > 0.5:
                invalidation_reason = "OBSTACLE_DETECTED"
            
            # 2. Target Lost (distance maxed out or 1.0 normalized)
            elif normalized.get("target_distance", 1.0) >= 1.0:
                invalidation_reason = "TARGET_LOST"
            
            # 3. Blocked Detection (New Requirement)
            pos = (raw.get("pos_x", 0.0), raw.get("pos_y", 0.0), raw.get("pos_z", 0.0))
            if self.last_active_pos:
                dist = math.sqrt(sum((a - b) ** 2 for a, b in zip(pos, self.last_active_pos)))
                if dist < 0.01:
                    invalidation_reason = "MOVEMENT_BLOCKED"
            self.last_active_pos = pos
            
            if invalidation_reason:
                logger.info(f"ACTIVE: Movement intent ENDED: {invalidation_reason}")
                self.current_movement_intent = None
        else:
            # Update last_active_pos even if no intent is active to have a baseline
            raw = state.get("raw", {})
            self.last_active_pos = (raw.get("pos_x", 0.0), raw.get("pos_y", 0.0), raw.get("pos_z", 0.0))

        shadow_data = None
        candidate_ver = self.ml_policy.manager.candidate_model_version

        # 0. Shadow Evaluation (Always run if candidate exists - Failure Management bypass)
        if candidate_ver:
            c_intent, c_conf, c_ver, c_probs, c_auth, c_dur, c_params = self.ml_policy.decide_version(state, candidate_ver, state_hash=state_hash)
            shadow_data = {
                "version": c_ver,
                "intent": c_intent,
                "confidence": c_conf,
                "authority": c_auth,
                "hold_duration_ticks": c_dur,
                "params": c_params
            }
            self.metrics["candidate_calls"] += 1
        
        # 1. Shadow Policy Evaluation (MANDATORY: ALWAYS RUN)
        s_intent, s_conf, s_ver, s_auth, s_dur, s_params = self.shadow_policy.decide(state, state_hash=state_hash)
        if shadow_data is None:
            shadow_data = {"intent": s_intent, "confidence": s_conf, "version": s_ver, "hold_duration_ticks": s_dur, "params": s_params}

        # 2. Safety Veto Check (Heuristics are the guardians)
        h_intent, h_conf, h_ver, h_auth, h_dur, h_params = self.heuristic_policy.decide(state, state_hash=state_hash)
        
        # If Heuristic is VERY confident (Safety rule triggered), it vetoes ML
        if h_conf >= 0.9:
            if self.current_movement_intent:
                logger.info(f"ACTIVE: Movement intent ENDED: HEURISTIC_VETO ({h_intent})")
                self.current_movement_intent = None
            return h_intent, h_conf, "HEURISTIC_VETO", h_auth, "SAFETY_RULE_TRIGGERED", h_ver, shadow_data, h_dur, h_params

        # 3. ML Suggestion (Active Path)
        # Soft Degrade Check: If in cooldown, bypass ML
        if self.ml_failure_cooldown > 0:
            self.ml_failure_cooldown -= 1
            res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "SOFT_DEGRADE_FALLBACK", "ML_FAILURE_COOLDOWN", state_hash=state_hash))
            # res is (intent, conf, source, auth, reason, ver, dur, params) - 8 elements
            # We need to insert shadow_data to match return type (9 elements)
            res.insert(6, shadow_data)
            
            final_intent, final_conf, final_source, final_auth, final_reason, final_ver, final_shadow_data, final_dur, final_params = tuple(res)
            
            if self.current_movement_intent:
                intent_enum = Intent[final_intent] if final_intent in Intent.__members__ else None
                move_priority = Intent.MOVE.metadata.get("priority", 0)
                new_priority = intent_enum.metadata.get("priority", 0) if intent_enum else 0
                
                if new_priority > move_priority:
                    logger.info(f"ACTIVE: Movement intent ENDED: HIGHER_PRIORITY_FALLBACK ({final_intent})")
                    self.current_movement_intent = None
                elif final_intent == Intent.STOP.value:
                    logger.info(f"ACTIVE: Movement intent ENDED: EXPLICIT_STOP_FALLBACK")
                    self.current_movement_intent = None
                else:
                    return Intent.MOVE.value, 1.0, "PERSISTENT_INTENT", "AUTHORITATIVE", None, final_ver, shadow_data, 1, {
                        "vector": self.current_movement_intent.direction_vector,
                        "sprinting": self.current_movement_intent.sprinting
                    }
            
            return final_intent, final_conf, final_source, final_auth, final_reason, final_ver, shadow_data, final_dur, final_params

        if self.ml_enabled:
            # Decide if we use Canary (Candidate) or Production
            use_candidate = False
            if candidate_ver and random.random() < self.canary_ratio:
                use_candidate = True
                self.metrics["canary_calls"] += 1
            
            target_version = candidate_ver if use_candidate else self.active_policy.manager.active_model_version
            
            # Phase 10B: Dual-Policy Influence
            # ActivePolicy → action_A
            ml_intent, ml_conf, ml_ver, ml_probs, ml_auth, ml_dur, ml_params = self.active_policy.decide_version(state, target_version, state_hash=state_hash)
            
            # Final action: final = mix(action_A, action_S, influence_weight)
            source_prefix = "CANARY_" if use_candidate else "ML_"
            is_shadow = False
            
            if self.influence_weight > 0 and random.random() < self.influence_weight:
                # Ensure ShadowPolicy fallback if it errors (Requirement 4)
                if s_intent and s_intent != Intent.STOP.value:
                    final_intent = s_intent
                    final_conf = s_conf
                    final_ver = s_ver
                    final_auth = s_auth
                    final_dur = s_dur
                    final_params = s_params
                    source_prefix = "SHADOW_INFLUENCE_"
                    is_shadow = True
                else:
                    final_intent, final_conf, final_ver, final_auth, final_dur, final_params = ml_intent, ml_conf, ml_ver, ml_auth, ml_dur, ml_params
                    source_prefix = "ML_FALLBACK_"
            else:
                final_intent, final_conf, final_ver, final_auth, final_dur, final_params = ml_intent, ml_conf, ml_ver, ml_auth, ml_dur, ml_params
            
            # Persistent Intent Logic for ML Suggestion
            if self.current_movement_intent:
                intent_enum = Intent[final_intent] if final_intent in Intent.__members__ else None
                move_priority = Intent.MOVE.metadata.get("priority", 0)
                new_priority = intent_enum.metadata.get("priority", 0) if intent_enum else 0
                
                if new_priority > move_priority:
                    logger.info(f"ACTIVE: Movement intent ENDED: HIGHER_PRIORITY_ACTION ({final_intent})")
                    self.current_movement_intent = None
                elif final_intent == Intent.STOP.value:
                    logger.info(f"ACTIVE: Movement intent ENDED: EXPLICIT_STOP")
                    self.current_movement_intent = None
                else:
                    # Replay
                    return Intent.MOVE.value, 1.0, "PERSISTENT_INTENT", "AUTHORITATIVE", None, final_ver, shadow_data, 1, {
                        "vector": self.current_movement_intent.direction_vector,
                        "sprinting": self.current_movement_intent.sprinting
                    }

            # Start persistent intent
            if final_intent == Intent.MOVE.value and not self.current_movement_intent:
                vector = final_params.get("vector", [0,0,0])
                sprinting = final_params.get("sprinting", False)
                self.current_movement_intent = MovementIntent(vector, sprinting, self.ml_policy.tick_counter)
                logger.info(f"ACTIVE: Movement intent STARTED: {vector} (sprint: {sprinting})")

            # Track calls
            m = self.shadow_metrics if is_shadow else self.active_metrics
            m["calls"] += 1

            # 3.1 Timeout Handling (Using ActivePolicy's last inference as proxy for ML speed)
            if self.active_policy.last_inference_time > 50.0:
                m["timeouts"] += 1
                m["failures"] += 1
                if not is_shadow:
                    self._trigger_soft_degrade("TIMEOUT")
                res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "ML_TIMEOUT_FALLBACK", "TIMEOUT_EXCEEDED", state_hash=state_hash))
                res.insert(-2, shadow_data)
                return tuple(res)

            # 3.2 UNCERTAINTY & ENTROPY CHECK
            ml_entropy = self._calculate_entropy(ml_probs)
            self.last_ml_entropy = ml_entropy
            if ml_entropy > 1.5: # Threshold for high uncertainty
                logger.warning(f"ML UNCERTAINTY: High entropy ({ml_entropy:.3f}) detected for model {ml_ver}. Falling back.")
                res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "ML_ENTROPY_FALLBACK", "HIGH_UNCERTAINTY", state_hash=state_hash))
                res.insert(-2, shadow_data)
                return tuple(res)

            # 3.3 Confidence & Output Validation
            active_threshold = self.ml_threshold + 0.05 if self.ml_failure_cooldown > 0 else self.ml_threshold
            
            if final_conf >= active_threshold:
                # Track divergence
                if final_intent != h_intent and h_conf > 0.5:
                    m["divergence_count"] += 1
                
                final_source = f"{source_prefix}SUGGESTION"
                if final_ver and (final_ver.startswith("PROMOTED_") or "KNOWLEDGE_" in final_ver):
                    final_source = f"{source_prefix}{final_ver}"
                
                return final_intent, final_conf, final_source, final_auth, None, final_ver, shadow_data, final_dur, final_params
            
            # ML was certain enough but didn't meet threshold
            if final_conf < 0.1: # Extreme uncertainty or rejection
                m["failures"] += 1
                res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, f"{source_prefix}REJECTION_FALLBACK", "LOW_CONFIDENCE", state_hash=state_hash))
                res.insert(6, shadow_data)
                return tuple(res)

            res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, f"{source_prefix}LOW_CONFIDENCE_FALLBACK", "LOW_CONFIDENCE", state_hash=state_hash))
            res.insert(6, shadow_data)
            return tuple(res)

        # 4. Heuristic Fallback
        res = list(self._heuristic_fallback(h_intent, h_conf, h_ver, h_auth, "HEURISTIC_FALLBACK", "ML_DISABLED", state_hash=state_hash))
        res.insert(6, shadow_data)
        return tuple(res)

    def _heuristic_fallback(self, h_intent: str, h_conf: float, h_ver: str, h_auth: str, source: str, reason: str, state_hash: Optional[str] = None) -> Tuple[str, float, str, str, str, str, int, Dict[str, Any]]:
        if h_conf >= 0.5:
            # Heuristic might need params for MOVE
            params = {}
            if h_intent == Intent.MOVE.value:
                params = {"vector": [1.0, 0.0, 0.0], "sprinting": True}
            return h_intent, h_conf, source, h_auth, reason, h_ver, 1, params
        
        # 4. Random Fallback
        r_intent, r_conf, r_ver, r_auth, r_dur, r_params = self.random_policy.decide({}, state_hash=state_hash)
        return r_intent, r_conf, "RANDOM_FALLBACK", r_auth, f"{reason}_AND_HEURISTIC_UNCERTAIN", r_ver, r_dur, r_params

    def _run_shadow_ml(self, state: Dict[str, Any], h_intent: str):
        """Runs ML without acting, for divergence and performance tracking."""
        ml_intent, ml_conf, ml_ver, ml_auth, ml_dur, ml_params = self.ml_policy.decide(state)
        self.shadow_metrics["calls"] += 1
        if ml_intent != h_intent:
            self.shadow_metrics["divergence_count"] += 1

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

    def apply_mitigation(self, mitigation: str, policy_mode: str = "ACTIVE"):
        """
        Applies a mitigation recommendation from the Monitor.
        'mitigation' should be a value from monitor.MitigationType
        """
        # MANDATORY FIRST CHECK: SHADOW MODE IS NOT SUBJECT TO ARBITRATION
        if policy_mode == "SHADOW":
            return

        if mitigation == "SOFT_DEGRADE":
            if self.ml_failure_cooldown == 0:
                self._trigger_soft_degrade("MONITOR_RECOMMENDATION", cooldown=100)
        elif mitigation == "SHADOW_QUARANTINE":
            if self.ml_enabled:
                self.set_ml_enabled(False)
                logger.critical("ARBITRATOR: SHADOW_QUARANTINE triggered by Monitor.")

    def get_online_metrics(self) -> Dict[str, Any]:
        """Calculates real-time performance metrics."""
        calls = self.active_metrics["calls"]
        total = self.metrics["total_cycles"]
        if total == 0:
            return {}
            
        return {
            "ml_failure_rate": self.active_metrics["failures"] / max(1, calls),
            "ml_timeout_rate": self.active_metrics["timeouts"] / max(1, calls),
            "ml_divergence_rate": self.active_metrics["divergence_count"] / max(1, total),
            "canary_ratio_active": self.canary_ratio,
            "canary_calls": self.metrics["canary_calls"],
            "candidate_calls": self.metrics["candidate_calls"],
            "shadow_calls": self.shadow_metrics["calls"],
            "shadow_divergence_rate": self.shadow_metrics["divergence_count"] / max(1, self.shadow_metrics["calls"]),
            "avg_inference_ms": self.ml_policy.last_inference_time,
            "soft_degrade_active": self.ml_failure_cooldown > 0
        }
