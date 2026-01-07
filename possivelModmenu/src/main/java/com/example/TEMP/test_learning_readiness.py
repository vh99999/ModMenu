import unittest
import json
import os
from learning_readiness import LearningReadinessAnalyzer
from failure_manager import FailureManager, DisasterMode, FailureType, FailureSeverity
from learning_gates import LearningGateSystem, LearningGateResult

class TestLearningReadiness(unittest.TestCase):
    def setUp(self):
        self.failure_manager = FailureManager(log_dir="test_failures")
        self.analyzer = LearningReadinessAnalyzer(self.failure_manager)
        self.gate_system = LearningGateSystem()

    def tearDown(self):
        # Cleanup test failures
        if os.path.exists("test_failures"):
            for f in os.listdir("test_failures"):
                os.remove(os.path.join("test_failures", f))
            os.rmdir("test_failures")

    def test_no_mutation(self):
        """Verify the analyzer does not mutate the input experience."""
        experience = {
            "experience_id": "test-123",
            "reward": 1.0,
            "confidence": 0.9,
            "violations": []
        }
        experience_json_before = json.dumps(experience, sort_keys=True)
        
        self.analyzer.analyze(experience, [])
        
        experience_json_after = json.dumps(experience, sort_keys=True)
        self.assertEqual(experience_json_before, experience_json_after, "Analyzer mutated the experience data!")

    def test_schema_compliance(self):
        """Verify the readiness report matches the mandatory schema."""
        experience = {
            "reward": 0.5,
            "confidence": 0.8,
            "violations": []
        }
        report = self.analyzer.analyze(experience, [])
        
        expected_keys = [
            "would_learn", "confidence_sufficient", "data_quality_ok",
            "distribution_stable", "reward_signal_valid", "policy_disagreement_rate",
            "sample_volume_window", "blocked_by"
        ]
        for key in expected_keys:
            self.assertIn(key, report)
        
        self.assertFalse(report["would_learn"], "would_learn MUST always be False")

    def test_gate_denial_blocks_would_learn(self):
        """Verify that if gates fail, the system reflects this in the report."""
        experience = {
            "reward": 1.0,
            "confidence": 1.0,
            "violations": []
        }
        
        # Simulate failed gates
        class MockGateDecision:
            def __init__(self, result):
                self.result = result
        
        gate_results = [MockGateDecision(LearningGateResult.BLOCKED)]
        
        report = self.analyzer.analyze(experience, gate_results)
        self.assertIn("LEARNING_GATE_DISABLED", report["blocked_by"])
        self.assertFalse(report["would_learn"])

    def test_incident_readiness_suggests_learning_despite_gates_closed(self):
        """
        Verify that a HIGH incident is logged if readiness criteria are met but gates are closed.
        Note: The analyzer itself doesn't record this specific incident (server does), 
        but we test the logic that triggers it.
        """
        experience = {
            "reward": 1.0,
            "confidence": 1.0,
            "violations": [],
            "reward_breakdown": {"test": 1.0}
        }
        
        class MockGateDecision:
            def __init__(self, result):
                self.result = result
        
        gate_results = [MockGateDecision(LearningGateResult.BLOCKED)]
        
        report = self.analyzer.analyze(experience, gate_results)
        
        # Test the logic used in server.py
        is_ready = report["confidence_sufficient"] and report["data_quality_ok"] and report["reward_signal_valid"]
        gates_closed = any(d.result != LearningGateResult.PASS for d in gate_results)
        
        if is_ready and gates_closed:
            self.failure_manager.record_incident(
                FailureType.LEARNING_READINESS_VIOLATION,
                severity=FailureSeverity.HIGH,
                details="Test: Readiness suggests learning despite gates closed"
            )
            
        self.assertEqual(len(self.failure_manager.incidents), 1)
        self.assertEqual(self.failure_manager.incidents[0]["type"], FailureType.LEARNING_READINESS_VIOLATION)
        self.assertEqual(self.failure_manager.incidents[0]["severity"], FailureSeverity.HIGH)

    def test_mutation_attempt_triggers_critical(self):
        """Verify that any attempt to mutate the experience triggers a CRITICAL incident."""
        # We need to subvert the _perform_analysis method to actually mutate the experience
        original_perform_analysis = self.analyzer._perform_analysis
        
        def mutating_perform_analysis(experience, gate_results):
            experience["mutated"] = True
            return original_perform_analysis(experience, gate_results)
            
        self.analyzer._perform_analysis = mutating_perform_analysis
        
        experience = {"reward": 1.0, "confidence": 1.0, "violations": []}
        
        with self.assertRaises(Exception) as cm:
            self.analyzer.analyze(experience, [])
            
        self.assertIn("Mutation attempt detected", str(cm.exception))
        self.assertEqual(self.failure_manager.mode, DisasterMode.LOCKDOWN)
        
        # Restore original method
        self.analyzer._perform_analysis = original_perform_analysis

    def test_hard_separation_assertion(self):
        """Verify that importing trainer triggers a violation."""
        # We can't easily test the 'import' itself in a unit test without complex mocking,
        # but we can test the _trigger_violation method.
        with self.assertRaises(Exception) as cm:
            self.analyzer._trigger_violation("Manual Trigger", "CRITICAL")
        
        self.assertIn("LEARNING_READINESS_VIOLATION", str(cm.exception))
        self.assertEqual(self.failure_manager.mode, DisasterMode.LOCKDOWN)

if __name__ == "__main__":
    unittest.main()
