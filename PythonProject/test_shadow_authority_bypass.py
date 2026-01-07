import unittest
import types
from learning_gates import LearningGateSystem, LearningGateResult

class TestShadowAuthorityBypass(unittest.TestCase):
    def setUp(self):
        self.system = LearningGateSystem()

    def test_shadow_mode_passes_all_gates(self):
        """
        Verify that in Shadow mode, ALL gates PASS or at least don't block.
        Specifically Governance Gate should PASS even without permits.
        """
        policy = types.SimpleNamespace(
            mode="SHADOW",
            lineage={
                "learning_allowed": False,
                "trust_boundary": "INVALID" # Would fail Trust Boundary Gate
            },
            violations=[{"severity": "HIGH"}], # Would fail Integrity Gate
            policy_authority="NONE" # Would fail Governance Gate
        )
        
        decisions = self.system.evaluate_all(policy)
        
        for d in decisions:
            self.assertEqual(d.result, LearningGateResult.PASS, 
                             f"{d.gate_name} should PASS for SHADOW mode")

    def test_active_mode_still_blocked_by_authority_gate(self):
        """
        Verify that in Active mode, Authority Gate still BLOCKS if learning_allowed is False.
        """
        policy = types.SimpleNamespace(
            mode="ACTIVE",
            lineage={
                "learning_allowed": False
            },
            violations=[],
            policy_authority="NONE"
        )
        
        decisions = self.system.evaluate_all(policy)
        authority_decision = next(d for d in decisions if d.gate_name == "Authority Gate")
        
        self.assertEqual(authority_decision.result, LearningGateResult.BLOCKED, 
                         "Authority Gate should BLOCK for ACTIVE mode if learning_allowed is False")

if __name__ == '__main__':
    unittest.main()
