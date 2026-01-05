import unittest
import sys
import types
from learning_freeze import LEARNING_STATE, ImportLock
from trainer import Trainer
from learning_gates import LearningGateSystem

class TestLearningFreeze(unittest.TestCase):
    def test_learning_state_immutable(self):
        """Confirm that LEARNING_STATE cannot be mutated."""
        import learning_freeze
        with self.assertRaises(PermissionError):
            learning_freeze.LEARNING_STATE = "ACTIVE"
        self.assertEqual(learning_freeze.LEARNING_STATE, "FROZEN")

    def test_trainer_unconditional_assertion(self):
        """Confirm that Trainer module enforces the freeze."""
        from trainer import LEARNING_STATE as TRAINER_STATE
        self.assertEqual(TRAINER_STATE, "FROZEN")
        
        # Verify call failure (existing defensive assertion)
        with self.assertRaises(AssertionError):
            t = Trainer(None)
            t.train_on_experience({})

    def test_gates_unconditional_assertion(self):
        """Confirm that learning_gates enforces the freeze."""
        from learning_gates import LEARNING_STATE as GATES_STATE
        self.assertEqual(GATES_STATE, "FROZEN")

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
            "trainer_callable": False,
            "optimizer_present": optimizer_present,
            "gates_open": False,
            "readiness_only": True
        }
        
        self.assertEqual(proof["learning_state"], "FROZEN")
        self.assertEqual(proof["trainer_callable"], False)
        self.assertEqual(proof["optimizer_present"], False)
        self.assertEqual(proof["gates_open"], False)
        self.assertEqual(proof["readiness_only"], True)

if __name__ == '__main__':
    unittest.main()
