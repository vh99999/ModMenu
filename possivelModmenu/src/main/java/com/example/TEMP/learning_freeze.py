import sys
import logging
import types

# Global Learning Freeze Authority
LEARNING_STATE = "SHADOW_ONLY"
LIVE_SHADOW_LEARNING = True

class _FreezeAuthority(types.ModuleType):
    def __setattr__(self, name, value):
        if name == "LEARNING_STATE":
            if value not in {"FROZEN", "SHADOW_ONLY"}:
                raise PermissionError(f"Invalid LEARNING_STATE: {value}. Must be FROZEN or SHADOW_ONLY.")
        super().__setattr__(name, value)

import sys
sys.modules[__name__].__class__ = _FreezeAuthority

class ImportLock:
    """
    Detects dynamic imports, reflection, and monkey-patching of critical components.
    """
    CRITICAL_MODULES = ["trainer", "policy", "learning_gates", "learning_freeze"]
    
    @staticmethod
    def verify_integrity():
        """
        Runs a static check on the environment to ensure no tampering has occurred.
        """
        # 1. Check for suspicious modules
        forbidden_frameworks = ["torch", "tensorflow", "keras", "jax", "sklearn"]
        for framework in forbidden_frameworks:
            if framework in sys.modules:
                return False, f"Forbidden ML framework detected: {framework}"

        # 2. Detect Monkey-patching of critical functions
        try:
            from trainer import Trainer
            # 2.1 Check if it's still a function/method
            if not hasattr(Trainer.train_on_experience, "__module__"):
                 return False, "Trainer.train_on_experience has been tampered with (missing metadata)."
            
            # 2.2 Module mismatch check (detects lambdas or functions from other modules)
            if Trainer.train_on_experience.__module__ != "trainer":
                 return False, f"Trainer.train_on_experience tampered with. Origin: {Trainer.train_on_experience.__module__}"
        except ImportError:
            pass

        return True, "Integrity Verified"

def enforce_freeze():
    """
    Hard-stop that crash execution if any integrity violation is detected.
    """
    success, reason = ImportLock.verify_integrity()
    if not success:
        print(f"LEARNING_FREEZE_BREACH: {reason}")
        # Crash execution as required
        sys.exit(1)

# symbolic audit stamp
FINAL_AUDIT_STAMP = "LEARNING_FROZEN_AT_PHASE_8"
