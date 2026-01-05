import json
import logging
import math
import hashlib
from typing import Dict, Any, List, Tuple
from enum import Enum

from intent_space import Intent
from state_parser import StateParser

logger = logging.getLogger(__name__)

class DatasetPipeline:
    """
    STRICT DATASET PIPELINE
    
    Transforms validated experiences into ML-ready tensors.
    Enforces DATASET_PROTOCOL.md invariants.
    """
    
    # Canonical Intent Order (Alphabetical for stability)
    INTENT_ORDER = sorted([i.value for i in Intent])
    INTENT_TO_IDX = {intent: i for i, intent in enumerate(INTENT_ORDER)}
    IDX_TO_INTENT = {i: intent for intent, i in INTENT_TO_IDX.items()}

    @classmethod
    def process_experiences(cls, experiences: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Processes a list of experiences: filters, vectorizes, and attaches metadata.
        """
        processed = []
        for exp in experiences:
            try:
                # 1. Verification (Double-check everything)
                if not cls._validate_experience(exp):
                    continue
                
                # 2. Vectorization
                state_vec = StateParser.get_feature_vector(exp["state"]["normalized"])
                intent_idx = cls.INTENT_TO_IDX[exp["intent"]]
                
                # 3. Create Record
                record = {
                    "features": state_vec,
                    "label": intent_idx,
                    "metadata": {
                        "timestamp": exp.get("timestamp"),
                        "reward": exp.get("reward"),
                        "controller": exp.get("controller"),
                        "state_version": exp.get("state_version"),
                        "episode_id": exp.get("episode_id", 0),
                        "audit_hash": cls._calculate_hash(exp)
                    }
                }
                processed.append(record)
            except Exception as e:
                logger.error(f"DATASET ERROR: Failed to process experience: {e}")
                
        return processed

    @classmethod
    def _validate_experience(cls, exp: Dict[str, Any]) -> bool:
        """Enforces hard blockers for training data."""
        # Check mandatory fields
        required = ["state", "intent", "reward", "controller"]
        for field in required:
            if field not in exp:
                return False
        
        # Check for stale data (Protocol 4: no more than 3 versions old)
        # Note: using CURRENT_VERSION - 2 allows CURRENT_VERSION, CURRENT_VERSION-1, CURRENT_VERSION-2.
        if exp.get("state_version", 0) < StateParser.CURRENT_VERSION - 2:
            logger.warning(f"DATASET REJECT: Stale state version {exp.get('state_version')}")
            return False

        # Check intent validity
        if exp["intent"] not in cls.INTENT_TO_IDX:
            logger.warning(f"DATASET REJECT: Invalid intent '{exp['intent']}'")
            return False
            
        # Check reward bounds
        if not (-2.0 <= exp["reward"] <= 2.0):
            logger.warning(f"DATASET REJECT: Reward {exp['reward']} out of bounds")
            return False
            
        # Check for NaN/Inf in features
        normalized_state = exp["state"].get("normalized", {})
        for k, v in normalized_state.items():
            if isinstance(v, (float, int)) and not math.isfinite(v):
                logger.warning(f"DATASET REJECT: Feature '{k}' is NaN/Inf")
                return False
        
        return True

    @classmethod
    def _calculate_hash(cls, exp: Dict[str, Any]) -> str:
        """Generates a unique hash for the experience for audit tracking."""
        data_str = json.dumps(exp, sort_keys=True)
        return hashlib.sha256(data_str.encode('utf-8')).hexdigest()

    @classmethod
    def split(cls, data: List[Dict[str, Any]], train_ratio=0.8, val_ratio=0.1) -> Dict[str, List[Dict[str, Any]]]:
        """
        Performs a temporal split of the dataset, ensuring no episode is split.
        """
        if not data:
            return {"train": [], "val": [], "test": []}

        # 1. Group by episode_id
        episodes = {}
        for d in data:
            eid = d["metadata"]["episode_id"]
            if eid not in episodes:
                episodes[eid] = []
            episodes[eid].append(d)
        
        # 2. Sort episodes by the timestamp of their first sample
        sorted_episode_ids = sorted(episodes.keys(), key=lambda eid: episodes[eid][0]["metadata"]["timestamp"])
        
        # 3. Split episode IDs
        n = len(sorted_episode_ids)
        train_end = int(n * train_ratio)
        val_end = train_end + int(n * val_ratio)
        
        train_ids = sorted_episode_ids[:train_end]
        val_ids = sorted_episode_ids[train_end:val_end]
        test_ids = sorted_episode_ids[val_end:]
        
        # 4. Reconstruct datasets
        def flatten(ids):
            res = []
            for eid in ids:
                res.extend(episodes[eid])
            return res

        return {
            "train": flatten(train_ids),
            "val": flatten(val_ids),
            "test": flatten(test_ids)
        }

    @classmethod
    def get_metrics(cls, data: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Calculates sanity metrics for the processed dataset."""
        if not data:
            return {}
            
        n = len(data)
        intent_counts = {}
        total_reward = 0.0
        zero_reward_count = 0
        
        for d in data:
            label = d["label"]
            intent_counts[label] = intent_counts.get(label, 0) + 1
            reward = d["metadata"]["reward"]
            total_reward += reward
            if abs(reward) < 1e-5:
                zero_reward_count += 1
                
        metrics = {
            "sample_count": n,
            "intent_distribution": {cls.IDX_TO_INTENT[k]: v/n for k, v in intent_counts.items()},
            "avg_reward": total_reward / n,
            "zero_reward_ratio": zero_reward_count / n
        }
        
        # Check thresholds (WARNINGS)
        for intent, freq in metrics["intent_distribution"].items():
            if freq > 0.80:
                logger.warning(f"SANITY CHECK: Intent imbalance detected for '{intent}': {freq:.2f}")
        
        if abs(metrics["avg_reward"]) > 1.5:
            logger.warning(f"SANITY CHECK: Reward skew detected: {metrics['avg_reward']:.2f}")
            
        if metrics["zero_reward_ratio"] > 0.90:
            logger.warning(f"SANITY CHECK: High zero-reward ratio: {metrics['zero_reward_ratio']:.2f}")
            
        return metrics
