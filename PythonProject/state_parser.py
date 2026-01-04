from typing import Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)

class StateParser:
    """
    Validates, normalizes and categorizes incoming state data.
    Ensures the AI only sees generic, semantic information.
    
    Architecture Goal:
    Maintain a stable feature vector format that won't change 
    when moving from heuristic-based to ML-based policies.
    """
    STATE_VERSION = 1
    MAX_DISTANCE = 100.0

    @classmethod
    def parse(cls, raw_data: Any) -> Optional[Dict[str, Any]]:
        """
        Parses raw JSON data into a structured state dictionary.
        Returns None if data is malformed.
        
        Guarantees:
        - Never crashes on malformed input.
        - Returns a consistent structure with versioning.
        - All numeric values are bounded and normalized.
        - Explicitly separates RAW, NORMALIZED, and DERIVED state.
        """
        try:
            if not isinstance(raw_data, dict):
                logger.error("State data must be a dictionary")
                return None

            raw = cls._extract_raw(raw_data)
            normalized = cls._normalize(raw)
            derived = cls._derive(normalized)

            return {
                "version": cls.STATE_VERSION,
                "raw": raw,
                "normalized": normalized,
                "derived": derived
            }
        except Exception as e:
            logger.error(f"Critical failure in StateParser: {e}", exc_info=True)
            return None

    @staticmethod
    def _extract_raw(data: Dict[str, Any]) -> Dict[str, Any]:
        """Filters and cleans raw input data, ensuring key stability."""
        expected_keys = {"health", "energy", "target_distance", "is_colliding"}
        raw = {}
        for key in expected_keys:
            val = data.get(key)
            if val is None:
                # Default values for missing keys to ensure downstream stability
                if key == "is_colliding":
                    raw[key] = False
                elif key == "health" or key == "energy":
                    raw[key] = 1.0 # Assume healthy/full energy if unknown
                else:
                    raw[key] = 0.0
            else:
                raw[key] = val
        return raw

    @classmethod
    def _normalize(cls, raw: Dict[str, Any]) -> Dict[str, Any]:
        """Binds and normalizes numeric values to [0.0, 1.0]."""
        normalized = {}
        
        def _clamp_norm(val: Any, min_val: float, max_val: float, default: float) -> float:
            try:
                f_val = float(val)
                # Clamp first to avoid extrapolation
                clamped = max(min_val, min(max_val, f_val))
                # Normalize
                return (clamped - min_val) / (max_val - min_val) if max_val > min_val else default
            except (ValueError, TypeError):
                return default

        normalized["health"] = _clamp_norm(raw.get("health"), 0.0, 1.0, 1.0)
        normalized["energy"] = _clamp_norm(raw.get("energy"), 0.0, 1.0, 1.0)
        normalized["target_distance"] = _clamp_norm(raw.get("target_distance"), 0.0, cls.MAX_DISTANCE, 0.0)
        normalized["is_colliding"] = 1.0 if bool(raw.get("is_colliding")) else 0.0
        
        return normalized

    @classmethod
    def _derive(cls, normalized: Dict[str, Any]) -> Dict[str, Any]:
        """Computes explicit semantic interpretations from normalized data."""
        derived = {}
        
        # Distance categories (Explicit thresholds)
        # Using normalized value to derive actual distance for thresholding
        actual_dist = normalized["target_distance"] * cls.MAX_DISTANCE
        
        if actual_dist < 3.0:
            derived["distance_category"] = "CLOSE"
        elif actual_dist < 10.0:
            derived["distance_category"] = "MEDIUM"
        else:
            derived["distance_category"] = "FAR"

        # Combat readiness (Semantic flags)
        derived["can_attack"] = normalized["energy"] > 0.2
        derived["is_threatened"] = normalized["health"] < 0.5 or normalized["is_colliding"] > 0.5
        
        # Movement freedom
        derived["is_obstructed"] = normalized["is_colliding"] > 0.5

        return derived
