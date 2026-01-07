import random
import json
import time
from collections import deque
from typing import Dict, Any, List, Optional

class MemoryBuffer:
    """
    Stores recent experiences in a circular buffer for learning.
    Each experience is a semantic dictionary, safe for JSON serialization.
    
    Architecture Goal:
    Provide a robust, version-aware storage for experiences that 
    supports both online learning and offline replay.
    """
    def __init__(self, capacity: int = 1000):
        self.capacity = capacity
        self.buffer = deque(maxlen=capacity)

    def push(self, 
             experience_id: str,
             state: Dict[str, Any], 
             intent: str, 
             confidence: float,
             result: Dict[str, Any], 
             reward: float, 
             reward_breakdown: Dict[str, float],
             controller: str,
             episode_id: int = 0,
             fallback_reason: Optional[str] = None,
             model_version: Optional[str] = None,
             lineage: Optional[Dict[str, Any]] = None) -> None:
        """
        Adds a new experience to the buffer.
        """
        # HARDENED LINEAGE VALIDATION
        if lineage is None:
            raise ValueError(f"DATA_LINEAGE_VIOLATION: Experience {experience_id} rejected due to missing lineage metadata.")
        
        required_lineage = ["source", "trust_boundary", "learning_allowed", "decision_authority"]
        for field in required_lineage:
            if field not in lineage or lineage[field] == "UNKNOWN":
                raise ValueError(f"DATA_LINEAGE_VIOLATION: Experience {experience_id} missing lineage field '{field}'.")

        experience = {
            "experience_id": experience_id,
            "timestamp": time.time(),
            "state": state,
            "intent": intent,
            "confidence": float(confidence),
            "result": result,
            "reward": float(reward),
            "reward_breakdown": reward_breakdown,
            "controller": controller,
            "state_version": state.get("version", 0),
            "episode_id": episode_id,
            "fallback_reason": fallback_reason,
            "model_version": model_version,
            "lineage": lineage
        }
        self.buffer.append(experience)

    def sample(self, batch_size: int) -> List[Dict[str, Any]]:
        """
        Returns a random sample of experiences for replay learning.
        """
        actual_size = min(len(self.buffer), batch_size)
        if actual_size == 0:
            return []
        return random.sample(list(self.buffer), actual_size)

    def get_recent(self, count: int = 1) -> List[Dict[str, Any]]:
        """
        Returns the last 'count' experiences.
        """
        return list(self.buffer)[-count:]

    def save_to_json(self, filepath: str) -> None:
        """
        Persists the current memory to a JSON file.
        Uses deterministic formatting.
        """
        with open(filepath, 'w') as f:
            json.dump(list(self.buffer), f, indent=2, sort_keys=True)

    def __len__(self) -> int:
        return len(self.buffer)
