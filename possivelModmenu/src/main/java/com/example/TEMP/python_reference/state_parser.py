from typing import Dict, Any, Optional, List
import logging
import math

logger = logging.getLogger(__name__)

class StateParser:
    """
    STRICT, VERSIONED STATE PARSER
    
    Architecture Goal:
    Maintain a stable feature vector format that won't change 
    when moving from heuristic-based to ML-based policies.
    
    Guarantees:
    - Never crashes on malformed input.
    - No NaN / Infinity propagation.
    - Strict range enforcement (clamping).
    - Explicit default injection for missing data.
    - Separation of RAW, NORMALIZED, and DERIVED state.
    - NO reward calculation.
    """
    
    CURRENT_VERSION = 1
    SUPPORTED_VERSIONS = [0, 1] # 0 is legacy/unknown
    
    # SCHEMA DEFINITION
    # field: (type, default_if_missing, range_min, range_max)
    # Range is used for both clamping and normalization.
    SCHEMA = {
        "health": (float, 1.0, 0.0, 1.0),
        "energy": (float, 1.0, 0.0, 1.0),
        "target_distance": (float, 1000.0, 0.0, 1000.0),
        "is_colliding": (bool, False, None, None)
    }

    @classmethod
    def parse(cls, raw_payload: Any) -> Optional[Dict[str, Any]]:
        """
        Parses raw JSON data into a structured state dictionary.
        Returns None only on non-recoverable errors.
        """
        try:
            # 1. Type Check Root
            if not isinstance(raw_payload, dict):
                logger.error("STATE FAILURE: Root payload must be a dictionary.")
                return None

            # 2. Version Validation
            # Java is dumb, might not send version yet. Fallback to 0.
            version = raw_payload.get("state_version", 0)
            
            if version < cls.CURRENT_VERSION:
                # Backward compatibility check
                if version not in cls.SUPPORTED_VERSIONS:
                    logger.error(f"STATE FAILURE: Legacy version {version} is not supported.")
                    return None
                logger.warning(f"STATE WARNING: Processing legacy version {version}.")
            
            elif version > cls.CURRENT_VERSION:
                # Forward compatibility: Ignore unknown, parse known
                logger.warning(f"STATE WARNING: Newer version {version} detected. Forward compatibility mode active.")

            # 3. Extraction, Type Enforcement & Sanity (NaN/Inf)
            raw_extracted = cls._extract_and_validate(raw_payload)
            
            # 4. Normalization (To [0, 1] range)
            normalized = cls._normalize(raw_extracted)
            
            # 5. Derivation (Interpretation happens ONLY here)
            derived = cls._derive(normalized)

            return {
                "version": version,
                "raw": raw_extracted,
                "normalized": normalized,
                "derived": derived
            }

        except Exception as e:
            # Catch-all to prevent bubbling up to gameplay/server loop
            logger.error(f"STATE CRITICAL: Unexpected failure in StateParser: {e}", exc_info=True)
            return None

    @classmethod
    def _extract_and_validate(cls, data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Extracts fields from the raw data, enforces types, 
        checks for NaN/Inf, and applies defaults.
        """
        validated = {}
        for field, (f_type, f_default, f_min, f_max) in cls.SCHEMA.items():
            val = data.get(field)
            
            # 3.1 Handle Missing Data (Explicit Default Injection)
            if val is None:
                logger.warning(f"STATE WARNING: Field '{field}' missing. Using default: {f_default}")
                validated[field] = f_default
                continue

            # 3.2 Type Enforcement & Sanity (NaN/Inf)
            try:
                if f_type == float:
                    # Explicit cast and sanity check
                    f_val = float(val)
                    if not math.isfinite(f_val):
                        logger.warning(f"STATE WARNING: Field '{field}' is NaN/Inf. Using default: {f_default}")
                        validated[field] = f_default
                    else:
                        # 3.3 Range Enforcement (Clamping)
                        clamped = max(f_min, min(f_max, f_val))
                        if clamped != f_val:
                            logger.debug(f"STATE: Field '{field}' value {f_val} clamped to {clamped}")
                        validated[field] = clamped
                
                elif f_type == bool:
                    # Generic truthy check for boolean observations
                    validated[field] = bool(val)
                
                elif f_type == int:
                    i_val = int(val)
                    if f_min is not None and f_max is not None:
                        i_val = max(f_min, min(f_max, i_val))
                    validated[field] = i_val
                
                else:
                    validated[field] = val

            except (ValueError, TypeError):
                logger.warning(f"STATE WARNING: Field '{field}' invalid type {type(val)}. Using default: {f_default}")
                validated[field] = f_default
        
        return validated

    @classmethod
    def _normalize(cls, raw: Dict[str, Any]) -> Dict[str, Any]:
        """Binds and normalizes numeric values to [0.0, 1.0]."""
        normalized = {}
        for field, (f_type, _, f_min, f_max) in cls.SCHEMA.items():
            val = raw[field]
            if f_type == float and f_min is not None and f_max is not None:
                denominator = f_max - f_min
                normalized[field] = (val - f_min) / denominator if denominator > 0 else 0.0
            elif f_type == bool:
                normalized[field] = 1.0 if val else 0.0
            else:
                normalized[field] = val
        return normalized

    @classmethod
    def _derive(cls, normalized: Dict[str, Any]) -> Dict[str, Any]:
        """
        Computes explicit semantic interpretations from normalized data.
        These are used by policies for decision making.
        """
        derived = {}
        
        # 5.1 Distance categories (Generic thresholds)
        # target_distance is normalized based on 0-1000 range.
        actual_dist = normalized["target_distance"] * 1000.0
        
        if actual_dist < 3.0:
            derived["distance_category"] = "CLOSE"
        elif actual_dist < 10.0:
            derived["distance_category"] = "MEDIUM"
        else:
            derived["distance_category"] = "FAR"

        # 5.2 Combat & Survival Semantics
        derived["can_attack"] = normalized["energy"] > 0.2
        derived["is_threatened"] = normalized["health"] < 0.5 or normalized["is_colliding"] > 0.5
        derived["is_obstructed"] = normalized["is_colliding"] > 0.5
        
        return derived
