import random
from abc import ABC, abstractmethod
from typing import Dict, Any, Tuple, Optional
from intent_space import Intent

class Policy(ABC):
    """
    Abstract base interface for decision policies.
    Ensures all policies (heuristic or ML) follow the same contract.
    """
    
    @abstractmethod
    def decide(self, state: Dict[str, Any]) -> Tuple[str, float]:
        """
        Returns a selected Intent string and its associated confidence [0.0, 1.0].
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
    def decide(self, state: Dict[str, Any]) -> Tuple[str, float]:
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
            return Intent.EVADE.value, 0.95

        # 2. Obstruction Handling: If trying to move but hitting something, jump
        if is_obstructed and distance_cat != "CLOSE":
            return Intent.JUMP.value, 0.85

        # 3. Aggression: If close and have energy, attack
        if distance_cat == "CLOSE" and can_attack:
            return Intent.PRIMARY_ATTACK.value, 0.8

        # 4. Engagement: If far, move closer
        if distance_cat == "FAR":
            return Intent.MOVE.value, 0.75

        # 5. Tactical Positioning: If medium distance
        if distance_cat == "MEDIUM":
            # If we have lots of energy, maybe jump to reposition or close in
            if normalized.get("energy", 0.0) > 0.8:
                return Intent.JUMP.value, 0.6
            # Otherwise hold and recover energy
            return Intent.HOLD.value, 0.7

        # Default fallback: Stop
        return Intent.STOP.value, 1.0

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

    def decide(self, state: Dict[str, Any]) -> Tuple[str, float]:
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
            return chosen_intent.value, confidence

        # Weighted selection based on policy knowledge
        chosen_intent = random.choices(intents, weights=normalized_probs, k=1)[0]
        
        # Confidence is the probability assigned by the policy
        confidence = self.weights.get(chosen_intent, 0.0) / total_weight
        
        return chosen_intent.value, float(confidence)
