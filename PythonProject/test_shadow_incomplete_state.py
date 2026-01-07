import unittest
import json
import socket
import threading
import time
import os
import uuid

# Set environment before imports
os.environ["AISERVER_MODE"] = "PROD_SERVER"

from server import AIServer
from intent_space import Intent
from learning_freeze import LEARNING_STATE

class TestShadowIncompleteState(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Use a unique port
        cls.port = 5020
        cls.server = AIServer(host='127.0.0.1', port=cls.port)
        cls.server_thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.server_thread.start()
        time.sleep(1)

    def _send_request(self, payload):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect(('127.0.0.1', self.port))
            s.sendall(json.dumps(payload).encode('utf-8'))
            data = s.recv(16384)
            if not data:
                return None
            return json.loads(data.decode('utf-8'))

    def test_shadow_incomplete_state_observation(self):
        """
        Verify that incomplete state for Shadow:
        - Is marked as incomplete.
        - Is not invalidated by quality gate.
        - Reward is marked appropriately.
        """
        # 1. Prepare incomplete state (missing energy)
        payload = {
            "state": {
                "health": 0.8,
                "target_distance": 100.0,
                # "energy" is missing
                "is_colliding": False,
                "state_version": 1
            },
            "intent_taken": "MOVE",
            "controller": "AI",
            "result": {
                "status": "SUCCESS",
                "failure_reason": "NONE",
                "outcomes": {"is_alive": True, "damage_dealt": 0.0, "damage_received": 0.0}
            },
            "policy_authority": "SHADOW_LEARNING_PERMIT"
        }

        response = self._send_request(payload)
        self.assertIsNotNone(response)
        
        # Verify response contains the new info
        self.assertEqual(response["reward_class"], "LEARNING_APPLICABLE_INCOMPLETE")
        self.assertIn("incomplete_state_marker", response["reward_breakdown"])
        
        # Check audit entry in server (internal check)
        audit_entry = self.server.auditor.history[-1]
        self.assertTrue(audit_entry["is_incomplete"])
        self.assertEqual(audit_entry["missing_fields"], 1)
        
        # Verify violations
        violations = [v for v in audit_entry["violations"] if v["type"] == "PARTIAL_CORRUPTION"]
        self.assertTrue(len(violations) > 0)
        self.assertEqual(violations[0]["severity"], "INFO") # Should be INFO for Shadow
        
        # Verify it passed quality gate
        is_pass = self.server.auditor.check_quality_gate(audit_entry)
        self.assertTrue(is_pass, "Quality gate should pass for incomplete Shadow experience")

    def test_active_incomplete_state_strictness(self):
        """
        Verify that incomplete state for Active (non-learning):
        - Quality gate FAILS if many fields are missing.
        """
        # 2. Prepare very incomplete state (missing 3 fields)
        payload = {
            "state": {
                "health": 0.5,
                # energy, target_distance, is_colliding missing
                "state_version": 1
            },
            "intent_taken": "STOP",
            "controller": "AI",
            "result": {
                "status": "SUCCESS",
                "failure_reason": "NONE",
                "outcomes": {"is_alive": True}
            }
            # policy_authority missing -> learning_allowed will be False
        }

        response = self._send_request(payload)
        self.assertIsNotNone(response)
        
        # Should be DIAGNOSTIC (or EVALUATIVE) but NOT LEARNING_APPLICABLE
        self.assertEqual(response["reward_class"], "DIAGNOSTIC")
        
        # Check audit entry
        audit_entry = self.server.auditor.history[-1]
        self.assertTrue(audit_entry["is_incomplete"])
        self.assertEqual(audit_entry["missing_fields"], 3)
        
        # Verify violations
        violations = [v for v in audit_entry["violations"] if v["type"] == "PARTIAL_CORRUPTION"]
        self.assertTrue(len(violations) > 0)
        self.assertEqual(violations[0]["severity"], "HIGH") # Should be HIGH for Active with 3 missing
        
        # Verify it FAILS quality gate
        is_pass = self.server.auditor.check_quality_gate(audit_entry)
        self.assertFalse(is_pass, "Quality gate should FAIL for highly incomplete Active experience")

    def test_shadow_no_silent_zero_reward(self):
        """
        Verify that malformed result in Shadow returns baseline survival reward, not 0.0.
        """
        payload = {
            "state": {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False},
            "intent_taken": "MOVE",
            "controller": "AI",
            "result": "NOT_A_DICTIONARY", # Malformed!
            "policy_authority": "SHADOW_LEARNING_PERMIT"
        }
        
        response = self._send_request(payload)
        # RewardCalculator.WEIGHTS["survival"] is 0.1
        self.assertEqual(response["reward_calculated"], 0.1)
        self.assertEqual(response["reward_class"], "LEARNING_APPLICABLE_INCOMPLETE") # It becomes incomplete due to missing outcomes

if __name__ == '__main__':
    unittest.main()
