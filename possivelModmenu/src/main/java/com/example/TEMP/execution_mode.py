import os
import sys
from enum import Enum

class ExecutionMode(str, Enum):
    PROD_SERVER = "PROD_SERVER"
    OFFLINE_TRAINING = "OFFLINE_TRAINING"
    AUDIT_PROOF = "AUDIT_PROOF"

def enforce_mode(allowed_modes: list[ExecutionMode]):
    current_mode = os.environ.get("AISERVER_MODE")
    if not current_mode:
        print(f"FATAL: AISERVER_MODE not set. Allowed for this script: {[m.value for m in allowed_modes]}")
        sys.exit(1)
    
    if current_mode not in [m.value for m in allowed_modes]:
        print(f"FATAL: Illegal execution mode '{current_mode}'. Allowed: {[m.value for m in allowed_modes]}")
        sys.exit(1)
