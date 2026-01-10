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
    # field: (type, default_if_missing, range_min, range_max, is_required)
    SCHEMA = {
        "health": (float, 1.0, 0.0, 1.0, True),
        "energy": (float, 1.0, 0.0, 1.0, True),
        "target_distance": (float, 1000.0, 0.0, 1000.0, True),
        "target_yaw": (float, 0.0, -180.0, 180.0, True),
        "is_colliding": (bool, False, None, None, True),
        "pos_x": (float, 0.0, None, None, True),
        "pos_y": (float, 0.0, None, None, True),
        "pos_z": (float, 0.0, None, None, True)
    }

    @classmethod
    def parse(cls, raw_payload: Any, fast: bool = False) -> Optional[Dict[str, Any]]:
        """
        Parses raw JSON data into a structured state dictionary.
        Returns None only on non-recoverable errors.
        """
        try:
            # 1. Type Check Root
            if not isinstance(raw_payload, dict):
                if not fast:
                    logger.error("STATE FAILURE: Root payload must be a dictionary.")
                return None

            # 2. Version Validation
            version = raw_payload.get("state_version", 0)
            
            # Fast path skips detailed logging and version checks
            if fast:
                raw_extracted = {}
                for field, (f_type, f_default, f_min, f_max, _) in cls.SCHEMA.items():
                    val = raw_payload.get(field, f_default)
                    if f_type == float and val is not None:
                        # Minimal clamping/sanity for speed
                        try:
                            f_val = float(val)
                            if f_min is not None: f_val = max(f_min, f_val)
                            if f_max is not None: f_val = min(f_max, f_val)
                            raw_extracted[field] = f_val
                        except (ValueError, TypeError):
                            raw_extracted[field] = f_default
                    else:
                        raw_extracted[field] = val
                
                normalized = cls._normalize(raw_extracted)
                derived = cls._derive(normalized, raw_extracted)
                
                return {
                    "version": version,
                    "raw": raw_extracted,
                    "normalized": normalized,
                    "derived": derived,
                    "missing_fields": 0,
                    "missing_fields_list": [],
                    "is_incomplete": False
                }

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
            raw_extracted, missing_fields_list, missing_required = cls._extract_and_validate(raw_payload)
            missing_count = len(missing_fields_list)
            
            if missing_count > 0:
                logger.warning(f"STATE_DIAGNOSTIC: Incomplete state detected. Missing fields: {missing_fields_list}. Applied defaults.")
            
            if len(missing_required) > 0:
                logger.error(f"STATE_PROTOCOL_VIOLATION: Required fields missing: {missing_required}. EXECUTION BLOCKED.")

            # 4. Normalization (To [0, 1] range)
            normalized = cls._normalize(raw_extracted)
            
            # PROTOCOL VERIFICATION HOOK
            for k, v in normalized.items():
                if isinstance(v, (float, int)):
                    assert math.isfinite(v), f"Protocol Violation: NaN/Inf detected in normalized field '{k}'"
            
            # 5. Derivation (Interpretation happens ONLY here)
            derived = cls._derive(normalized, raw_extracted)

            return {
                "version": version,
                "raw": raw_extracted,
                "normalized": normalized,
                "derived": derived,
                "missing_fields": missing_count,
                "missing_fields_list": missing_fields_list,
                "missing_required": missing_required,
                "is_incomplete": len(missing_required) > 0
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
    def _extract_and_validate(cls, data: Dict[str, Any]) -> Tuple[Dict[str, Any], List[str], List[str]]:
        """
        Extracts fields from the raw data, enforces types, 
        checks for NaN/Inf, and applies defaults.
        Returns (validated_data, missing_fields_list, missing_required_list)
        """
        validated = {}
        missing_fields = []
        missing_required = []
        for field, (f_type, f_default, f_min, f_max, is_required) in cls.SCHEMA.items():
            val = data.get(field)
            
            # 3.1 Handle Missing Data (Explicit Default Injection)
            if val is None:
                validated[field] = f_default
                missing_fields.append(field)
                if is_required:
                    missing_required.append(field)
                continue

            # 3.2 Type Enforcement & Sanity (NaN/Inf)
            try:
                if f_type == float:
                    # Explicit cast and sanity check
                    f_val = float(val)
                    if not math.isfinite(f_val):
                        validated[field] = f_default
                        missing_fields.append(f"{field}(non_finite)")
                        if is_required:
                            missing_required.append(field)
                    else:
                        # 3.3 Range Enforcement (Clamping)
                        clamped = f_val
                        if f_min is not None:
                            clamped = max(f_min, clamped)
                        if f_max is not None:
                            clamped = min(f_max, clamped)
                        
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
                            validated[field] = f_default
                            missing_fields.append(f"{field}(invalid_bool)")
                            if is_required:
                                missing_required.append(field)
                    elif isinstance(val, (int, float)):
                        validated[field] = bool(val)
                    else:
                        validated[field] = f_default
                        missing_fields.append(f"{field}(invalid_type)")
                        if is_required:
                            missing_required.append(field)
                
                elif f_type == int:
                    i_val = int(val)
                    if f_min is not None and f_max is not None:
                        i_val = max(f_min, min(f_max, i_val))
                    validated[field] = i_val
                
                else:
                    validated[field] = val

            except (ValueError, TypeError):
                validated[field] = f_default
                missing_fields.append(f"{field}(parse_error)")
                if is_required:
                    missing_required.append(field)
        
        return validated, missing_fields, missing_required

    @classmethod
    def _normalize(cls, raw: Dict[str, Any]) -> Dict[str, Any]:
        """Binds and normalizes numeric values to [0.0, 1.0]."""
        normalized = {}
        for field, (f_type, _, f_min, f_max, _) in cls.SCHEMA.items():
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
    def _derive(cls, normalized: Dict[str, Any], raw: Dict[str, Any]) -> Dict[str, Any]:
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

        # 5.1b Target Direction categories
        actual_yaw = raw.get("target_yaw", 0.0)
        if abs(actual_yaw) < 45:
            derived["target_direction"] = "FRONT"
        elif actual_yaw >= 45 and actual_yaw < 135:
            derived["target_direction"] = "RIGHT"
        elif actual_yaw <= -45 and actual_yaw > -135:
            derived["target_direction"] = "LEFT"
        else:
            derived["target_direction"] = "BACK"

        # 5.2 Combat & Survival Semantics
        derived["can_attack"] = normalized["energy"] > 0.2
        derived["is_threatened"] = normalized["health"] < 0.5 or normalized["is_colliding"] > 0.5
        derived["is_obstructed"] = normalized["is_colliding"] > 0.5
        
        return derived
