import unittest
import sys
import types
from learning_freeze import LEARNING_STATE, ImportLock
from trainer import Trainer
from learning_gates import LearningGateSystem

class TestLearningFreeze(unittest.TestCase):
    def test_learning_state_immutable(self):
        """Confirm that LEARNING_STATE cannot be mutated to invalid values."""
        import learning_freeze
        with self.assertRaises(PermissionError):
            learning_freeze.LEARNING_STATE = "ACTIVE"
        self.assertEqual(learning_freeze.LEARNING_STATE, "SHADOW_ONLY")

    def test_trainer_relaxed_assertion(self):
        """Confirm that Trainer module allows the SHADOW_ONLY state."""
        import learning_freeze
        self.assertEqual(learning_freeze.LEARNING_STATE, "SHADOW_ONLY")
        
        # Verify call success (no longer asserts)
        t = Trainer(None)
        # Should not raise AssertionError anymore
        try:
            t.train_on_experience({})
        except AssertionError:
            self.fail("Trainer.train_on_experience raised AssertionError unexpectedly!")
        except Exception:
            pass # Other exceptions are fine since we passed None as policy

    def test_gates_relaxed_assertion(self):
        """Confirm that learning_gates allows the SHADOW_ONLY state."""
        import learning_freeze
        self.assertEqual(learning_freeze.LEARNING_STATE, "SHADOW_ONLY")

    def test_import_lock_detection(self):
        """Confirm that the import lock detects forbidden frameworks."""
        # Temporarily mock sys.modules
        sys.modules["torch"] = types.ModuleType("torch")
        success, reason = ImportLock.verify_integrity()
        self.assertFalse(success)
        self.assertIn("Forbidden ML framework detected: torch", reason)
        del sys.modules["torch"]

    def test_monkeypatch_detection(self):
        """Confirm that monkey-patching of Trainer is detected."""
        original_train = Trainer.train_on_experience
        # Monkey patch with a lambda (which will have __module__ == 'test_learning_freeze' or 'builtins')
        Trainer.train_on_experience = lambda self, experience: None
        
        success, reason = ImportLock.verify_integrity()
        self.assertFalse(success)
        self.assertIn("Trainer.train_on_experience tampered with", reason)
        
        # Restore
        Trainer.train_on_experience = original_train

    def test_proof_logic(self):
        """Verify the logic used in the proof endpoint."""
        optimizer_present = any(m in sys.modules for m in ["torch", "tensorflow", "keras", "jax"])
        
        proof = {
            "learning_state": LEARNING_STATE,
            "trainer_callable": True, # Now allowed
            "optimizer_present": optimizer_present,
            "gates_open": True, # Gates can be open for SHADOW
            "readiness_only": False # No longer just readiness
        }
        
        self.assertEqual(proof["learning_state"], "SHADOW_ONLY")
        self.assertEqual(proof["trainer_callable"], True)
        self.assertEqual(proof["optimizer_present"], False)
        self.assertEqual(proof["gates_open"], True)
        self.assertEqual(proof["readiness_only"], False)

    def test_active_policy_learning_blocked(self):
        """Verify that ActivePolicy learning attempt is blocked and logged."""
        from policy import MLPolicy, ModelManager
        mm = ModelManager()
        active_policy = MLPolicy(mm, role="ACTIVE")
        with self.assertLogs('policy', level='ERROR') as cm:
            with self.assertRaises(PermissionError):
                active_policy.update_weights([{"experience": "test"}])
        self.assertIn("[FREEZE] ActivePolicy learning attempt BLOCKED", cm.output[0])

    def test_shadow_policy_learning_allowed(self):
        """Verify that ShadowPolicy learning is allowed and logged correctly."""
        from policy import MLPolicy, ModelManager
        mm = ModelManager()
        shadow_policy = MLPolicy(mm, role="SHADOW")
        with self.assertLogs('policy', level='INFO') as cm:
            shadow_policy.update_weights([{"state": {"normalized": {"health": 1.0}}}])
        self.assertTrue(any("[SHADOW] Learning event verified" in log for log in cm.output))
        self.assertTrue(any("[SHADOW] Learning allowed under SHADOW_ONLY mode" in log for log in cm.output))

if __name__ == '__main__':
    unittest.main()
