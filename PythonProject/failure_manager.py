import logging
import time
import uuid
import json
import os
from enum import Enum
from typing import Dict, Any, List, Optional

logger = logging.getLogger(__name__)

class DisasterMode(str, Enum):
    NORMAL = "NORMAL"
    SAFE_MODE = "SAFE_MODE" # Heuristic only
    READ_ONLY = "READ_ONLY" # STOP only
    FREEZE = "FREEZE"       # No learning
    READ_ONLY_DISK = "READ_ONLY_DISK" # No logging/writing
    TEMPORAL_LOCK = "TEMPORAL_LOCK"   # Suspend time logic
    ISOLATED = "ISOLATED"             # No network
    LOCKDOWN = "LOCKDOWN"             # READ_ONLY + FREEZE + No API changes

class FailureType(str, Enum):
    CONFIDENCE_COLLAPSE = "CONFIDENCE_COLLAPSE"
    MODEL_DIVERGENCE = "MODEL_DIVERGENCE"
    CONTRACT_VIOLATION = "CONTRACT_VIOLATION"
    REWARD_INVARIANT = "REWARD_INVARIANT"
    CORRUPTION_NAN = "CORRUPTION_NAN"
    VERSION_CONFLICT = "VERSION_CONFLICT"
    TIMEOUT = "TIMEOUT"
    MALFORMED_JSON = "MALFORMED_JSON"
    DISK_FULL = "DISK_FULL"
    CLOCK_JUMP = "CLOCK_JUMP"
    NETWORK_DROP = "NETWORK_DROP"
    CRASH_RECOVERY = "CRASH_RECOVERY"
    HUMAN_ERROR = "HUMAN_ERROR"
    OPERATOR_ACTION = "OPERATOR_ACTION"
    DATA_LINEAGE_VIOLATION = "DATA_LINEAGE_VIOLATION"
    LEARNING_GATE_BLOCK = "LEARNING_GATE_BLOCK"
    LEARNING_READINESS_VIOLATION = "LEARNING_READINESS_VIOLATION"
    LEARNING_FREEZE_BREACH = "LEARNING_FREEZE_BREACH"

class FailureSeverity(str, Enum):
    INFO = "INFO"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

class FailureManager:
    """
    STRICT FAILURE MANAGEMENT LAYER
    
    Handles disaster modes, incident response, and recovery workflows.
    FOLLOWS FAILURE_PROTOCOL.md.
    """
    def __init__(self, log_dir: str = "failures"):
        self.mode = DisasterMode.NORMAL
        self.log_dir = log_dir
        self.incidents: List[Dict[str, Any]] = []
        
        if not os.path.exists(log_dir):
            os.makedirs(log_dir)

    def set_mode(self, mode: DisasterMode):
        if self.mode != mode:
            logger.warning(f"FAILURE_EVENT: System entering {mode} mode.")
            self.mode = mode

    def record_incident(self, 
                        failure_type: FailureType, 
                        severity: FailureSeverity = FailureSeverity.MEDIUM,
                        experience_id: Optional[str] = None,
                        audit_entry: Optional[Dict[str, Any]] = None,
                        details: Optional[str] = None):
        """
        Records a failure incident and generates a report.
        """
        incident_id = str(uuid.uuid4())
        
        # Automatic LOCKDOWN on CRITICAL severity
        if severity == FailureSeverity.CRITICAL:
            logger.critical(f"CRITICAL INCIDENT DETECTED: {failure_type}. Triggering system LOCKDOWN.")
            self.set_mode(DisasterMode.LOCKDOWN)

        report = {
            "incident_id": incident_id,
            "experience_id": experience_id or (audit_entry.get("experience_id") if audit_entry else "SYSTEM"),
            "timestamp": time.time(),
            "type": failure_type.value,
            "severity": severity.value,
            "details": details,
            "metadata": {
                "audit_hash": audit_entry.get("state_hash") if audit_entry else None,
                "state_snapshot": audit_entry.get("java_result") if audit_entry else None
            }
        }
        
        self.incidents.append(report)
        
        if self.mode == DisasterMode.READ_ONLY_DISK:
            logger.warning(f"FAILURE_MANAGER: Suppressing report write for {incident_id[:8]} (READ_ONLY_DISK mode).")
            return

        # Persist report
        filepath = os.path.join(self.log_dir, f"incident_{incident_id[:8]}.json")
        try:
            with open(filepath, 'w') as f:
                json.dump(report, f, indent=2)
        except (IOError, OSError) as e:
            # Detect Disk Full (Error 28 on Linux, but we are on Windows)
            # On Windows it might be "No space left on device" in the error message
            if "space" in str(e).lower() or (hasattr(e, 'errno') and e.errno == 28):
                logger.critical("SYSTEM CRITICAL: Disk is full! Entering READ_ONLY_DISK mode.")
                self.set_mode(DisasterMode.READ_ONLY_DISK)
            else:
                logger.error(f"FAILURE_MANAGER: Failed to persist incident report: {e}")

        # Automatic Containment Logic
        # LOCKDOWN takes precedence over all other modes
        if self.mode != DisasterMode.LOCKDOWN:
            self._trigger_containment(failure_type)

    def _trigger_containment(self, failure_type: FailureType):
        """
        Deterministic containment based on failure type.
        """
        if failure_type in [FailureType.REWARD_INVARIANT, FailureType.CONTRACT_VIOLATION]:
            self.set_mode(DisasterMode.READ_ONLY)
        elif failure_type in [FailureType.CORRUPTION_NAN, FailureType.VERSION_CONFLICT]:
            self.set_mode(DisasterMode.FREEZE)
        elif failure_type in [FailureType.TIMEOUT, FailureType.CONFIDENCE_COLLAPSE]:
            if self.mode == DisasterMode.NORMAL:
                self.set_mode(DisasterMode.SAFE_MODE)
        elif failure_type == FailureType.DISK_FULL:
            self.set_mode(DisasterMode.READ_ONLY_DISK)
        elif failure_type == FailureType.CLOCK_JUMP:
            self.set_mode(DisasterMode.TEMPORAL_LOCK)
        elif failure_type == FailureType.NETWORK_DROP:
            self.set_mode(DisasterMode.ISOLATED)

    def get_status(self) -> Dict[str, Any]:
        return {
            "mode": self.mode.value,
            "incident_count": len(self.incidents),
            "last_failure": self.incidents[-1]["type"] if self.incidents else None
        }
