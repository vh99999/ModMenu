import logging
import time
import hashlib
import json
from typing import Dict, Any, Tuple

logger = logging.getLogger(__name__)

def can_learn(sample: Dict[str, Any], context: Dict[str, Any]) -> Tuple[bool, str]:
    """
    Returns:
      (False, reason) if learning is blocked
      (True, "ALLOWED") if learning would be allowed
      
    Architecture Goal:
    This is the explicit Learning Control Plane that governs whether learning 
    is allowed. By default, learning is DISABLED to prevent accidental 
    data persistence or model updates.
    """
    
    # 1. Default behavior: LEARNING IS DISABLED
    # This is a hard-coded governance decision.
    is_allowed = False
    reason = "LEARNING_DISABLED"
    
    # 2. Auditing & Incident Generation
    # If learning is attempted (i.e., this function is called), we must audit it.
    _emit_incident(
        incident_type="LEARNING_ATTEMPT_BLOCKED",
        reason=reason,
        sample=sample,
        caller=context.get("caller", "UNKNOWN")
    )
    
    # 3. Log the decision
    logger.info(f"[LEARNING_CONTROL] Decision: {is_allowed}, Reason: {reason}, Caller: {context.get('caller', 'UNKNOWN')}")
    
    return is_allowed, reason

def _emit_incident(incident_type: str, reason: str, sample: Dict[str, Any], caller: str) -> None:
    """
    Emits a structured incident for auditing.
    """
    sample_hash = _calculate_sample_hash(sample)
    
    incident = {
        "incident_type": incident_type,
        "timestamp": time.time(),
        "reason": reason,
        "sample_hash": sample_hash,
        "caller_subsystem": caller
    }
    
    # Structured logging of the incident
    # This makes it obvious in the logs that learning was blocked intentionally.
    logger.error(f"[INCIDENT] {json.dumps(incident)}")

def _calculate_sample_hash(sample: Dict[str, Any]) -> str:
    """
    Generates a deterministic hash for the sample for auditing.
    """
    try:
        sample_json = json.dumps(sample, sort_keys=True)
        return hashlib.sha256(sample_json.encode('utf-8')).hexdigest()
    except Exception:
        return "HASH_FAILURE"
