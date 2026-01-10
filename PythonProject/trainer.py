import logging
import random
import json
import time
import os
import hashlib
import math
import threading
import queue
from typing import Dict, Any, List, Optional, Tuple

from policy import Policy, LearnedPolicy, MLPolicy, RandomWeightedPolicy, HeuristicCombatPolicy
import learning_freeze

# UNCONDITIONAL FREEZE CHECK REMOVED - SHADOW LEARNING ALLOWED
assert learning_freeze.LEARNING_STATE in {"FROZEN", "SHADOW_ONLY"}
from dataset import DatasetPipeline
from reward import RewardCalculator
from intent_space import Intent, MovementIntent
from execution_mode import ExecutionMode, enforce_mode

logger = logging.getLogger(__name__)

class Trainer:
    """
    Handles learning from experiences (Online/Shadow Learning).
    """
    def __init__(self, policy: Policy):
        self.policy = policy
        self.passive_store_path = os.path.join("shadow", "passive_store.json")
        if not os.path.exists("shadow"):
            os.makedirs("shadow")
        self.passive_data = {
            "session_id": 0,
            "visitation_counts": {},
            "valid_actions": {},
            "transitions": {},
            "invariant_violations": [],
            "blocked_resolutions": {}, # state_hash -> {resolution_action: count}
            "combat_movement_patterns": {} # target_direction -> {movement_type: count}
        }
        self.last_state_per_episode = {} # episode_id -> (state_hash, action)
        self.active_holds = {} # episode_id -> {action, state_hash, duration, cooldown}
        self.last_pos_per_episode = {} # episode_id -> (x, y, z)
        self.blocked_since_tick = {} # episode_id -> start_tick
        self.blocked_state_hash = {} # episode_id -> state_hash
        self.tick_counts = {} # episode_id -> current_tick
        self._load_passive_store()
        
        # Async logging and training (Requirement 3)
        self.experience_queue = queue.Queue()
        self.worker_thread = threading.Thread(target=self._worker, daemon=True)
        self.worker_thread.start()

    def _worker(self):
        """Background thread for processing experiences and persisting data."""
        while True:
            experience = self.experience_queue.get()
            if experience is None:
                break
            try:
                # Track Passive Observational Data (MANDATORY)
                self._update_passive_data(experience)

                # Training logic (IL/RL)
                is_shadow = hasattr(self.policy, 'role') and getattr(self.policy, 'role') == "SHADOW"
                
                # Trust Boundary Enforcement (Previously in train_on_experience)
                lineage = experience.get("lineage", {})
                if not lineage:
                    if not is_shadow: continue
                elif not lineage.get("learning_allowed"):
                    if not is_shadow: continue
                
                allowed_boundaries = {"SANDBOX", "INTERNAL_VERIFIED", "HUMAN_PLAY_SESSION"}
                if lineage and lineage.get("trust_boundary") not in allowed_boundaries:
                    if not is_shadow: continue

                controller = experience.get("controller", "AI")
                if controller == "HUMAN":
                    self._train_imitation(experience)
                elif controller in ["AI", "HEURISTIC"]:
                    self._train_reinforcement(experience)
            except Exception as e:
                logger.error(f"ASYNC TRAINER ERROR: {e}")
            finally:
                self.experience_queue.task_done()

    def _load_passive_store(self):
        if os.path.exists(self.passive_store_path):
            try:
                with open(self.passive_store_path, 'r') as f:
                    data = json.load(f)
                    if isinstance(data, dict):
                        self.passive_data.update(data)
                    if "combat_movement_patterns" not in self.passive_data:
                        self.passive_data["combat_movement_patterns"] = {}
                logger.info(f"[OK] Passive store loaded: {len(self.passive_data['visitation_counts'])} states")
            except Exception as e:
                logger.error(f"[ERROR] Failed to load passive store: {e}")

    def _save_passive_store(self):
        temp_path = self.passive_store_path + ".tmp"
        try:
            with open(temp_path, 'w') as f:
                json.dump(self.passive_data, f, indent=2)
            os.replace(temp_path, self.passive_store_path)
        except Exception as e:
            logger.error(f"[ERROR] Atomic write failed for passive store: {e}")

    def train_on_experience(self, experience: Dict[str, Any]) -> None:
        """
        Main entry point for online learning.
        Buffers experience for async processing to guarantee sub-10ms tick.
        """
        if learning_freeze.LEARNING_STATE == "FROZEN":
            return
        
        # Push to buffer immediately (Task 3)
        self.experience_queue.put(experience)

    def _train_imitation(self, experience: Dict[str, Any]) -> None:
        """Imitation Learning (IL) Path."""
        if isinstance(self.policy, LearnedPolicy):
            # ARCHITECTURAL HOOK: Update weights based on expert demonstration
            self.policy.update_weights([experience])

    def _train_reinforcement(self, experience: Dict[str, Any]) -> None:
        """Reinforcement Learning (RL) Path."""
        if isinstance(self.policy, LearnedPolicy):
            # ARCHITECTURAL HOOK: Update weights based on reward signal
            self.policy.update_weights([experience])

    def _update_passive_data(self, experience: Dict[str, Any]) -> None:
        """Updates passive observational data incrementally."""
        state_hash = experience.get("state_hash")
        episode_id = experience.get("episode_id")
        action = experience.get("intent")
        intent_params = experience.get("intent_params", {})
        hold_duration = experience.get("hold_duration_ticks", 1)
        cooldown = experience.get("cooldown_ticks_observed", 0)
        violations = experience.get("violations", [])
        state = experience.get("state", {})
        derived = state.get("derived", {})
        target_dir = derived.get("target_direction", "UNKNOWN")

        if not state_hash:
            return

        # NEW: Flush other episodes' holds if this is a new one
        # (Assuming only one active episode at a time per session for simplicity)
        to_flush = [eid for eid in self.active_holds if eid != episode_id]
        for eid in to_flush:
            last_hold = self.active_holds.pop(eid)
            self._record_action_observation(
                last_hold["state_hash"], 
                last_hold["action"], 
                last_hold["duration"], 
                last_hold["cooldown"],
                params=last_hold.get("params")
            )
            logger.info(f"SHADOW_OBSERVATION: Flushed final action '{last_hold['action']}' for episode {eid}")

        # 0. Displacement & Block Tracking
        raw = state.get("raw", {})
        pos = (raw.get("pos_x", 0.0), raw.get("pos_y", 0.0), raw.get("pos_z", 0.0))
        
        # Incremental tick counter per episode
        self.tick_counts[episode_id] = self.tick_counts.get(episode_id, 0) + 1
        current_tick = self.tick_counts[episode_id]
        
        last_pos = self.last_pos_per_episode.get(episode_id)
        displacement = 0.0
        if last_pos:
            displacement = math.sqrt(sum((a - b) ** 2 for a, b in zip(pos, last_pos)))
        
        self.last_pos_per_episode[episode_id] = pos
        
        is_moving_intent = (action == Intent.MOVE)
        EPSILON = 0.01
        
        was_blocked = episode_id in self.blocked_since_tick
        is_blocked = is_moving_intent and (displacement < EPSILON)
        
        if is_blocked:
            if not was_blocked:
                self.blocked_since_tick[episode_id] = current_tick
                self.blocked_state_hash[episode_id] = state_hash
                logger.info(f"SHADOW_OBSERVATION: Blocked state STARTED at tick {current_tick} (Hash: {state_hash[:8]})")
        elif was_blocked:
            # Block RESOLVED
            start_tick = self.blocked_since_tick.pop(episode_id)
            b_hash = self.blocked_state_hash.pop(episode_id)
            blocked_duration = current_tick - start_tick
            
            # Record the resolution
            resolutions = self.passive_data["blocked_resolutions"]
            if b_hash not in resolutions:
                resolutions[b_hash] = {}
            resolutions[b_hash][action] = resolutions[b_hash].get(action, 0) + 1
            
            logger.info(f"SHADOW_OBSERVATION: Block RESOLVED by '{action}' after {blocked_duration} ticks (Displacement: {displacement:.4f})")

        # 1. Visitation Counts
        v_counts = self.passive_data["visitation_counts"]
        v_counts[state_hash] = v_counts.get(state_hash, 0) + 1

        # 2. Hold Aggregation & Valid Actions
        last_hold = self.active_holds.get(episode_id)
        
        # Determine if current action is a continuation of the last hold
        # A continuation has same action AND incremental duration
        is_continuation = (
            last_hold and 
            last_hold["action"] == action and 
            hold_duration > last_hold["duration"]
        )
        
        # For MOVE, also check if vector or sprinting changed
        if is_continuation and action == Intent.MOVE:
            last_params = last_hold.get("params", {})
            if last_params.get("vector") != intent_params.get("vector") or \
               last_params.get("sprinting") != intent_params.get("sprinting"):
                is_continuation = False

        if last_hold and not is_continuation:
            # Previous hold ended. Record it.
            self._record_action_observation(
                last_hold["state_hash"], 
                last_hold["action"], 
                last_hold["duration"], 
                last_hold["cooldown"],
                params=last_hold.get("params")
            )
            if last_hold["action"] == Intent.MOVE:
                lp = last_hold.get("params", {})
                target_dir = last_hold.get("target_direction", "UNKNOWN")
                logger.info(f"SHADOW_OBSERVATION: Movement Intent '{lp.get('vector')}' (sprint: {lp.get('sprinting')}, relative to target: {target_dir}) held for {last_hold['duration']} ticks")
            else:
                logger.info(f"SHADOW_OBSERVATION: Action '{last_hold['action']}' held for {last_hold['duration']} ticks (cooldown: {last_hold['cooldown']})")

        # Start or update current hold
        if not is_continuation:
            self.active_holds[episode_id] = {
                "action": action,
                "state_hash": state_hash,
                "duration": hold_duration,
                "cooldown": cooldown,
                "params": intent_params,
                "target_direction": target_dir
            }
            if action == Intent.MOVE:
                logger.info(f"SHADOW_OBSERVATION: Movement Intent STARTED: {intent_params.get('vector')} (sprint: {intent_params.get('sprinting')}, relative to target: {derived.get('target_direction', 'UNKNOWN')})")
        else:
            # Update existing hold with latest data
            last_hold["duration"] = hold_duration
            last_hold["cooldown"] = max(last_hold["cooldown"], cooldown)

        # 3. Transition Frequencies
        if episode_id in self.last_state_per_episode:
            prev_state, prev_action = self.last_state_per_episode[episode_id]
            transitions = self.passive_data["transitions"]
            if prev_state not in transitions:
                transitions[prev_state] = {}
            if prev_action not in transitions[prev_state]:
                transitions[prev_state][prev_action] = {}
            
            transitions[prev_state][prev_action][state_hash] = transitions[prev_state][prev_action].get(state_hash, 0) + 1
        
        self.last_state_per_episode[episode_id] = (state_hash, action)

        # 4. Invariant Violations
        if violations:
            for v in violations:
                if v.get("severity") in ["HIGH", "CRITICAL"]:
                    self.passive_data["invariant_violations"].append({
                        "experience_id": experience.get("experience_id"),
                        "state_hash": state_hash,
                        "action": action,
                        "type": v.get("type"),
                        "timestamp": time.time()
                    })

        # 5. Combat Movement Patterns
        if action in [Intent.MOVE, Intent.STOP]:
            cmp = self.passive_data.get("combat_movement_patterns", {})
            if target_dir not in cmp:
                cmp[target_dir] = {"MOVE_CLOSER": 0, "STRAFE": 0, "STOP": 0, "OTHER": 0}
            
            if action == Intent.STOP:
                cmp[target_dir]["STOP"] += 1
            else:
                m_type = self._categorize_movement(target_dir, intent_params.get("vector", [0,0,0]))
                cmp[target_dir][m_type] += 1
            self.passive_data["combat_movement_patterns"] = cmp

        # Persist incrementally
        self._save_passive_store()

    def _categorize_movement(self, target_dir: str, vector: List[float]) -> str:
        if not vector or len(vector) < 3:
            return "STOP"
            
        vx, vy, vz = vector
        if abs(vx) < 0.1 and abs(vz) < 0.1:
            return "STOP"
            
        # Target-relative logic
        # Assumes +X is Forward, +/-Z is Side
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

    def _record_action_observation(self, state_hash: str, action_name: str, duration: int, cooldown: int, params: Optional[Dict[str, Any]] = None) -> None:
        """Records an observed action with its temporal properties."""
        if not action_name:
            return
            
        v_actions = self.passive_data["valid_actions"]
        if state_hash not in v_actions:
            v_actions[state_hash] = []
            
        found = False
        for entry in v_actions[state_hash]:
            if isinstance(entry, dict) and entry.get("action_name") == action_name:
                # Update with latest observed values
                entry["hold_duration_ticks"] = max(entry.get("hold_duration_ticks", 1), duration)
                entry["cooldown_ticks_observed"] = max(entry.get("cooldown_ticks_observed", 0), cooldown)
                if params:
                    entry["params"] = params
                found = True
                break
            elif isinstance(entry, str) and entry == action_name:
                # Migrate legacy entry
                v_actions[state_hash].remove(entry)
                new_entry = {
                    "action_name": action_name,
                    "hold_duration_ticks": duration,
                    "cooldown_ticks_observed": cooldown
                }
                if params:
                    new_entry["params"] = params
                v_actions[state_hash].append(new_entry)
                found = True
                break
                
        if not found:
            new_entry = {
                "action_name": action_name,
                "hold_duration_ticks": duration,
                "cooldown_ticks_observed": cooldown
            }
            if params:
                new_entry["params"] = params
            v_actions[state_hash].append(new_entry)

class OfflineTrainer:
    """
    STRICT OFFLINE TRAINING PIPELINE
    
    Architecture Goal:
    Ensure reproducibility, stability, and objective evaluation 
    of ML models before deployment.
    """
    def __init__(self, ml_policy: MLPolicy, random_seed: int = 42):
        enforce_mode([ExecutionMode.OFFLINE_TRAINING])
        self.ml_policy = ml_policy
        self.random_seed = random_seed
        self.reward_calc = RewardCalculator()
        self.baselines = {
            "RANDOM": RandomWeightedPolicy(),
            "HEURISTIC": HeuristicCombatPolicy()
        }
        self._seed_everything()

    def _seed_everything(self):
        """Enforces determinism across all stochastic components."""
        random.seed(self.random_seed)
        # In a real PyTorch environment, we would also seed torch, cuda, and numpy.
        logger.info(f"TRAINER: Seeding all components with {self.random_seed}")

    def run_full_pipeline(self, 
                          dataset_path: str, 
                          version: str, 
                          hyperparams: Dict[str, Any]) -> Dict[str, Any]:
        """
        Executes the full offline training and evaluation flow.
        """
        try:
            # 1. LOAD & PREPARE DATA
            logger.info(f"TRAINER: Starting pipeline for version {version}")
            if not os.path.exists(dataset_path):
                raise FileNotFoundError(f"Dataset not found: {dataset_path}")

            with open(dataset_path, 'r') as f:
                raw_data = json.load(f)
            
            # Use DatasetPipeline to process and split
            processed = DatasetPipeline.process_experiences(raw_data)
            if len(processed) < 10: # Minimum threshold for mock
                raise ValueError(f"Dataset too small: {len(processed)} samples.")
                
            splits = DatasetPipeline.split(processed)
            train_set = splits["train"]
            val_set = splits["val"]
            test_set = splits["test"]

            # 2. TRAINING LOOP
            epochs = hyperparams.get("epochs", 5)
            batch_size = hyperparams.get("batch_size", 32)
            
            training_metrics = []
            for epoch in range(epochs):
                # Shuffling (Seeded)
                random.shuffle(train_set)
                
                epoch_loss = 0.0
                # Batching
                for i in range(0, len(train_set), batch_size):
                    batch = train_set[i : i + batch_size]
                    loss = self.ml_policy.update_weights(batch)
                    epoch_loss += loss
                
                avg_loss = epoch_loss / max(1, (len(train_set) // batch_size))
                
                # Validation
                val_acc = self._calculate_accuracy(val_set)
                training_metrics.append({"epoch": epoch, "loss": avg_loss, "val_acc": val_acc})
                logger.info(f"Epoch {epoch}: loss={avg_loss:.4f}, val_acc={val_acc:.4f}")

                # Check for NaN in loss
                if math.isnan(avg_loss) or math.isinf(avg_loss):
                    raise ValueError(f"NaN/Inf loss detected at epoch {epoch}")

            # 3. EVALUATION
            eval_results = self._evaluate_model(test_set)
            
            # 4. ACCEPTANCE CHECK
            is_accepted, reason = self._check_acceptance(training_metrics, eval_results)
            
            # 5. PACKAGING
            model_card = {
                "version": version,
                "status": "ACCEPTED" if is_accepted else "REJECTED",
                "rejection_reason": reason if not is_accepted else None,
                "dataset_v_hash": self._calculate_file_hash(dataset_path),
                "reward_config_version": "v1.0.0", # Current Reward Protocol version
                "code_hash": self._calculate_file_hash(__file__),
                "hyperparameters": hyperparams,
                "training_history": training_metrics,
                "evaluation_metrics": eval_results,
                "timestamp": time.time()
            }

            if is_accepted:
                self._save_model(version, model_card)
            else:
                self._quarantine_model(version, model_card)

            return model_card

        except Exception as e:
            logger.error(f"TRAINING ABORTED: {e}", exc_info=True)
            return {"status": "ABORTED", "reason": str(e)}

    def _calculate_accuracy(self, dataset: List[Dict[str, Any]]) -> float:
        """Calculates how often ML policy matches the 'expert' label."""
        if not dataset:
            return 0.0
        correct = 0
        for item in dataset:
            # Reconstruct state from features (Mock)
            state = {"normalized": {
                "health": item["features"][0],
                "energy": item["features"][1],
                "target_distance": item["features"][2],
                "is_colliding": item["features"][3] > 0.5
            }}
            intent, _ = self.ml_policy.decide(state)
            if Intent.has_value(intent) and Intent(intent) == Intent(DatasetPipeline.IDX_TO_INTENT[item["label"]]):
                correct += 1
        return correct / len(dataset)

    def _evaluate_model(self, test_set: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Compares ML policy against baselines on the test set."""
        ml_acc = self._calculate_accuracy(test_set)
        
        # In a real scenario, we would also evaluate "Reward Potential" 
        # by running the policy through a simulator. 
        # Here we simulate Reward Potential.
        ml_reward_pot = 0.5 + (ml_acc * 0.5) 
        h_reward_pot = 0.6 # Baseline Heuristic performance
        
        return {
            "ml_accuracy": ml_acc,
            "ml_reward_potential": ml_reward_pot,
            "heuristic_reward_baseline": h_reward_pot,
            "random_reward_baseline": 0.0
        }

    def _check_acceptance(self, history: List[Dict[str, Any]], evals: Dict[str, Any]) -> Tuple[bool, str]:
        """Stricly enforces TRAINING_PROTOCOL.md acceptance criteria."""
        # 1. Regression Check
        if evals["ml_reward_potential"] < evals["heuristic_reward_baseline"]:
            return False, "Performance below HEURISTIC baseline"
            
        # 2. Overfitting Check
        final_val_acc = history[-1]["val_acc"]
        if final_val_acc < 0.5:
            return False, f"Validation accuracy too low: {final_val_acc:.4f}"

        # 3. Loss Sanity
        if any(math.isnan(h["loss"]) for h in history):
            return False, "NaN detected in loss history"

        return True, ""

    def _save_model(self, version: str, model_card: Dict[str, Any]):
        """Saves accepted model artifacts."""
        path = os.path.join(self.ml_policy.manager.models_dir, version)
        if not os.path.exists(path):
            os.makedirs(path)
            
        with open(os.path.join(path, "model_card.json"), 'w') as f:
            json.dump(model_card, f, indent=2)
        
        # Mock weight saving
        with open(os.path.join(path, "weights.pth"), 'w') as f:
            f.write("MOCK_WEIGHTS_DATA")
            
        logger.info(f"MODEL ACCEPTED: Version {version} saved to {path}")

    def _quarantine_model(self, version: str, model_card: Dict[str, Any]):
        """Saves rejected model artifacts for inspection."""
        q_dir = os.path.join(self.ml_policy.manager.models_dir, "quarantine")
        if not os.path.exists(q_dir):
            os.makedirs(q_dir)
            
        path = os.path.join(q_dir, f"{version}_REJECTED")
        with open(f"{path}_card.json", 'w') as f:
            json.dump(model_card, f, indent=2)
            
        logger.warning(f"MODEL REJECTED: Version {version} moved to quarantine. Reason: {model_card['rejection_reason']}")

    def _calculate_file_hash(self, filepath: str) -> str:
        """SHA-256 of the dataset file."""
        sha256_hash = hashlib.sha256()
        with open(filepath, "rb") as f:
            for byte_block in iter(lambda: f.read(4096), b""):
                sha256_hash.update(byte_block)
        return sha256_hash.hexdigest()
