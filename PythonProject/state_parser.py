from typing import Dict, Any, Optional, List, Tuple
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
            raw_extracted, missing_count = cls._extract_and_validate(raw_payload)
            
            # 4. Normalization (To [0, 1] range)
            normalized = cls._normalize(raw_extracted)
            
            # 5. Derivation (Interpretation happens ONLY here)
            derived = cls._derive(normalized)

            return {
                "version": version,
                "raw": raw_extracted,
                "normalized": normalized,
                "derived": derived,
                "missing_fields": missing_count
            }

        except Exception as e:
            # Catch-all to prevent bubbling up to gameplay/server loop
            logger.error(f"STATE CRITICAL: Unexpected failure in StateParser: {e}", exc_info=True)
            return None

    @classmethod
    def get_feature_vector(cls, normalized_state: Dict[str, Any]) -> List[float]:
        """
        Converts a normalized state dictionary into a fixed-order feature vector
        suitable for ML inference and training.
        
        Order: health, energy, target_distance, is_colliding
        """
        return [
            float(normalized_state.get("health", 0.0)),
            float(normalized_state.get("energy", 0.0)),
            float(normalized_state.get("target_distance", 0.0)),
            float(1.0 if normalized_state.get("is_colliding") else 0.0)
        ]

    @classmethod
    def _extract_and_validate(cls, data: Dict[str, Any]) -> Tuple[Dict[str, Any], int]:
        """
        Extracts fields from the raw data, enforces types, 
        checks for NaN/Inf, and applies defaults.
        Returns (validated_data, missing_field_count)
        """
        validated = {}
        missing_count = 0
        for field, (f_type, f_default, f_min, f_max) in cls.SCHEMA.items():
            val = data.get(field)
            
            # 3.1 Handle Missing Data (Explicit Default Injection)
            if val is None:
                logger.warning(f"STATE WARNING: Field '{field}' missing. Using default: {f_default}")
                validated[field] = f_default
                missing_count += 1
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
                    # STRICT Boolean Parsing
                    if isinstance(val, bool):
                        validated[field] = val
                    elif isinstance(val, str):
                        low_val = val.lower()
                        if low_val in ["true", "1", "yes", "on"]:
                            validated[field] = True
                        elif low_val in ["false", "0", "no", "off"]:
                            validated[field] = False
                        else:
                            logger.warning(f"STATE WARNING: Field '{field}' has ambiguous string value '{val}'. Using default: {f_default}")
                            validated[field] = f_default
                    elif isinstance(val, (int, float)):
                        validated[field] = bool(val)
                    else:
                        logger.warning(f"STATE WARNING: Field '{field}' invalid boolean type {type(val)}. Using default: {f_default}")
                        validated[field] = f_default
                
                elif f_type == int:
                    i_val = int(val)
                    if f_min is not None and f_max is not None:
                        i_val = max(f_min, min(f_max, i_val))
                    validated[field] = i_val
                
                else:
                    validated[field] = val

            except (ValueError, TypeError):
                logger.warning(f"STATE WARNING: Field '{field}' invalid value or type {type(val)}. Using default: {f_default}")
                validated[field] = f_default
                missing_count += 1
        
        return validated, missing_count

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
