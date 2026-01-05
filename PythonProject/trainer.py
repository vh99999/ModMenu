import logging
import random
import json
import time
import os
import hashlib
import math
from typing import Dict, Any, List, Optional, Tuple

from policy import Policy, LearnedPolicy, MLPolicy, RandomWeightedPolicy, HeuristicCombatPolicy
from dataset import DatasetPipeline
from reward import RewardCalculator
from intent_space import Intent

logger = logging.getLogger(__name__)

class Trainer:
    """
    Handles learning from experiences (Online/Shadow Learning).
    """
    def __init__(self, policy: Policy):
        self.policy = policy

    def train_on_experience(self, experience: Dict[str, Any]) -> None:
        """
        Main entry point for online learning.
        Routes experience to either Imitation or Reinforcement learning paths.
        """
        controller = experience.get("controller", "AI")
        
        if controller == "HUMAN":
            self._train_imitation(experience)
        elif controller in ["AI", "HEURISTIC"]:
            self._train_reinforcement(experience)
        else:
            logger.warning(f"Unknown controller type: {controller}. Skipping training.")

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

class OfflineTrainer:
    """
    STRICT OFFLINE TRAINING PIPELINE
    
    Architecture Goal:
    Ensure reproducibility, stability, and objective evaluation 
    of ML models before deployment.
    """
    def __init__(self, ml_policy: MLPolicy, random_seed: int = 42):
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
