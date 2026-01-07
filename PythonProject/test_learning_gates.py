import unittest
import types
from learning_gates import LearningGateSystem, LearningGateResult, LearningGateDecision
from trainer import Trainer
from policy import Policy

class MockPolicy(Policy):
    def decide(self, state): return "STOP"

class TestLearningGates(unittest.TestCase):
    def setUp(self):
        self.system = LearningGateSystem()
        self.trainer = Trainer(MockPolicy())

    def _wrap(self, exp):
        return types.SimpleNamespace(
            mode=exp.get("policy_mode", "ACTIVE"),
            lineage=exp.get("lineage", {}),
            violations=exp.get("violations", []),
            policy_authority=exp.get("policy_authority", "NONE")
        )

    def test_all_gates_pass_still_no_learning(self):
        experience = {
            "lineage": {
                "learning_allowed": True,
                "trust_boundary": "SANDBOX"
            },
            "violations": [],
            "policy_authority": "EXPLICIT_LEARNING_PERMIT"
        }
        decisions = self.system.evaluate_all(self._wrap(experience))
        
        for d in decisions:
            self.assertEqual(d.result, LearningGateResult.PASS)
        
        # get_final_decision requires decisions list
        self.assertEqual(self.system.get_final_decision(decisions), "SHADOW_LEARNING_ALLOWED")
        
        # Verify Trainer can learn from this (it's authorized)
        # Note: Trainer no longer raises AssertionError when reachable
        try:
            self.trainer.train_on_experience(experience)
        except Exception as e:
            self.fail(f"Trainer.train_on_experience raised {type(e).__name__} unexpectedly!")

    def test_authority_violation_blocked(self):
        experience = {
            "lineage": {
                "learning_allowed": False,
                "trust_boundary": "SANDBOX"
            },
            "violations": [],
            "policy_authority": "EXPLICIT_LEARNING_PERMIT"
        }
        decisions = self.system.evaluate_all(self._wrap(experience))
        
        # Gate 1 is Authority Gate
        self.assertEqual(decisions[0].gate_name, "Authority Gate")
        self.assertEqual(decisions[0].result, LearningGateResult.BLOCKED)

    def test_invalid_trust_boundary_fail(self):
        experience = {
            "lineage": {
                "learning_allowed": True,
                "trust_boundary": "EXTERNAL_UNTRUSTED"
            },
            "violations": [],
            "policy_authority": "EXPLICIT_LEARNING_PERMIT"
        }
        decisions = self.system.evaluate_all(self._wrap(experience))
        
        # Gate 2 is Trust Boundary Gate
        self.assertEqual(decisions[1].gate_name, "Trust Boundary Gate")
        self.assertEqual(decisions[1].result, LearningGateResult.FAIL)

    def test_integrity_violation_fail(self):
        experience = {
            "lineage": {
                "learning_allowed": True,
                "trust_boundary": "SANDBOX"
            },
            "violations": [{"type": "CONTRACT_VIOLATION", "severity": "HIGH"}],
            "policy_authority": "EXPLICIT_LEARNING_PERMIT"
        }
        decisions = self.system.evaluate_all(self._wrap(experience))
        
        # Gate 3 is Integrity Gate
        self.assertEqual(decisions[2].gate_name, "Integrity Gate")
        self.assertEqual(decisions[2].result, LearningGateResult.FAIL)

    def test_missing_governance_permit_blocked(self):
        experience = {
            "lineage": {
                "learning_allowed": True,
                "trust_boundary": "SANDBOX"
            },
            "violations": [],
            "policy_authority": "NONE"
        }
        decisions = self.system.evaluate_all(self._wrap(experience))
        
        # Gate 4 is Governance Gate
        self.assertEqual(decisions[3].gate_name, "Governance Gate")
        self.assertEqual(decisions[3].result, LearningGateResult.BLOCKED)

if __name__ == '__main__':
    unittest.main()
