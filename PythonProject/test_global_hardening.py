import os
import json
import unittest
import uuid
from audit import Auditor, ViolationSeverity
from failure_manager import FailureManager, DisasterMode, FailureType, FailureSeverity
from memory import MemoryBuffer
from reward import RewardCalculator
from dataset import DatasetPipeline
from execution_mode import ExecutionMode

# Set execution mode for testing
os.environ["AISERVER_MODE"] = ExecutionMode.PROD_SERVER.value

class TestGlobalHardening(unittest.TestCase):
    def setUp(self):
        self.auditor = Auditor()
        self.failure_manager = FailureManager()
        self.memory = MemoryBuffer()
        self.reward_calc = RewardCalculator()

    def test_experience_id_propagation(self):
        experience_id = str(uuid.uuid4())
        lineage = {
            "source": "JAVA_SOURCE",
            "trust_boundary": "INTERNAL_VERIFIED",
            "learning_allowed": True,
            "decision_authority": "ML_MODEL"
        }
        
        # 1. Audit
        entry = self.auditor.record_cycle(
            experience_id=experience_id,
            raw_state={"health": 1.0},
            state_version=1,
            intent_issued="MOVE",
            confidence=0.8,
            policy_source="ML",
            controller="AI",
            raw_java_result={"status": "SUCCESS", "failure_reason": "NONE", "outcomes": {}},
            reward_total=0.1,
            reward_breakdown={},
            lineage=lineage,
            authority="AUTHORITATIVE"
        )
        self.assertEqual(entry["experience_id"], experience_id)
        
        # 2. Memory
        self.memory.push(
            experience_id=experience_id,
            state={"version": 1},
            intent="MOVE",
            confidence=0.8,
            result={"status": "SUCCESS"},
            reward=0.1,
            reward_breakdown={},
            controller="AI",
            lineage=lineage
        )
        self.assertEqual(self.memory.buffer[0]["experience_id"], experience_id)

    def test_data_lineage_enforcement(self):
        experience_id = str(uuid.uuid4())
        # Missing learning_allowed
        bad_lineage = {
            "source": "JAVA_SOURCE",
            "trust_boundary": "EXTERNAL_UNTRUSTED",
            "decision_authority": "UNKNOWN"
        }
        
        entry = self.auditor.record_cycle(
            experience_id=experience_id,
            raw_state={"health": 1.0},
            state_version=1,
            intent_issued="MOVE",
            confidence=0.8,
            policy_source="ML",
            controller="AI",
            raw_java_result={"status": "SUCCESS", "failure_reason": "NONE", "outcomes": {}},
            reward_total=0.1,
            reward_breakdown={},
            lineage=bad_lineage,
            authority="AUTHORITATIVE"
        )
        
        # Should have DATA_LINEAGE_VIOLATION
        self.assertTrue(any(v["type"] == "DATA_LINEAGE_VIOLATION" for v in entry["violations"]))
        # Quality gate should fail
        self.assertFalse(self.auditor.check_quality_gate(entry))

    def test_memory_buffer_hardening(self):
        experience_id = str(uuid.uuid4())
        with self.assertRaises(ValueError):
            self.memory.push(
                experience_id=experience_id,
                state={},
                intent="MOVE",
                confidence=0.8,
                result={},
                reward=0.0,
                reward_breakdown={},
                controller="AI",
                lineage=None # REJECTED
            )

    def test_critical_incident_lockdown(self):
        self.failure_manager.record_incident(
            FailureType.CONTRACT_VIOLATION,
            severity=FailureSeverity.CRITICAL,
            details="TEST CRITICAL"
        )
        self.assertEqual(self.failure_manager.mode, DisasterMode.LOCKDOWN)

    def test_reward_tagging(self):
        # learning_allowed = False -> DIAGNOSTIC
        r, b, c = self.reward_calc.calculate({"outcomes": {}}, learning_allowed=False)
        self.assertEqual(c, "DIAGNOSTIC")
        
        # learning_allowed = True -> LEARNING_APPLICABLE
        r, b, c = self.reward_calc.calculate({"outcomes": {}}, learning_allowed=True)
        self.assertEqual(c, "LEARNING_APPLICABLE")

    def test_execution_mode_enforcement(self):
        from execution_mode import enforce_mode
        # Current mode is PROD_SERVER
        enforce_mode([ExecutionMode.PROD_SERVER]) # Should pass
        
        with self.assertRaises(SystemExit):
            enforce_mode([ExecutionMode.OFFLINE_TRAINING])

if __name__ == "__main__":
    unittest.main()
