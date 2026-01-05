import unittest
from learning_gates import LearningGateSystem, LearningGateResult, LearningGateDecision
from trainer import Trainer
from policy import Policy

class MockPolicy(Policy):
    def decide(self, state): return "STOP"

class TestLearningGates(unittest.TestCase):
    def setUp(self):
        self.system = LearningGateSystem()
        self.trainer = Trainer(MockPolicy())

    def test_all_gates_pass_still_no_learning(self):
        experience = {
            "lineage": {
                "learning_allowed": True,
                "trust_boundary": "SANDBOX"
            },
            "violations": [],
            "policy_authority": "EXPLICIT_LEARNING_PERMIT"
        }
        decisions = self.system.evaluate_all(experience)
        
        for d in decisions:
            self.assertEqual(d.result, LearningGateResult.PASS)
        
        self.assertEqual(self.system.get_final_decision(), "LEARNING_BLOCKED_BY_GATE_SYSTEM")
        
        # Verify Trainer is still unreachable/fails if reached
        with self.assertRaises(AssertionError) as cm:
            self.trainer.train_on_experience(experience)
        self.assertEqual(str(cm.exception), "Trainer reached while learning is disabled")

    def test_authority_violation_blocked(self):
        experience = {
            "lineage": {
                "learning_allowed": False,
                "trust_boundary": "SANDBOX"
            },
            "violations": [],
            "policy_authority": "EXPLICIT_LEARNING_PERMIT"
        }
        decisions = self.system.evaluate_all(experience)
        
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
        decisions = self.system.evaluate_all(experience)
        
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
        decisions = self.system.evaluate_all(experience)
        
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
        decisions = self.system.evaluate_all(experience)
        
        # Gate 4 is Governance Gate
        self.assertEqual(decisions[3].gate_name, "Governance Gate")
        self.assertEqual(decisions[3].result, LearningGateResult.BLOCKED)

if __name__ == '__main__':
    unittest.main()
