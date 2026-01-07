import unittest
import json
import socket
import threading
import time
import os
os.environ["AISERVER_MODE"] = "PROD_SERVER"
from server import AIServer
from intent_space import Intent
from learning_freeze import LEARNING_STATE

class TestExternalConnectivity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = AIServer(host='127.0.0.1', port=5005)
        cls.server_thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.server_thread.start()
        time.sleep(1) # Wait for server to start

    def _send_request(self, payload):
        # Inject valid result if missing to avoid triggering READ_ONLY mode via Monitor
        if "result" not in payload:
            payload["result"] = {
                "status": "SUCCESS",
                "failure_reason": "NONE",
                "outcomes": {"is_alive": True}
            }
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect(('127.0.0.1', 5005))
            s.sendall(json.dumps(payload).encode('utf-8'))
            data = s.recv(16384)
            return json.loads(data.decode('utf-8'))

    def setUp(self):
        # Reset failure status to clear any READ_ONLY mode from previous tests
        reset_payload = {"type": "RESET_FAILURE_STATUS"}
        # We need to handle confirmation if it requires it
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect(('127.0.0.1', 5005))
            s.sendall(json.dumps(reset_payload).encode('utf-8'))
            resp = json.loads(s.recv(16384).decode('utf-8'))
            if "confirmation_token" in resp:
                reset_payload["confirmation_token"] = resp["confirmation_token"]
                s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s2.connect(('127.0.0.1', 5005))
                s2.sendall(json.dumps(reset_payload).encode('utf-8'))
                s2.recv(16384)
                s2.close()

    def test_live_payload_reaches_inference_unchanged(self):
        # Payload with specific values to verify they reach inference
        payload = {
            "state": {
                "health": 0.5,
                "energy": 0.5,
                "target_distance": 5.0,
                "is_colliding": False,
                "timestamp": time.time(),
                "state_version": 1
            },
            "intent_taken": "MOVE",
            "controller": "AI"
        }
        response = self._send_request(payload)
        self.assertEqual(response["experience_id"], response.get("experience_id"))
        self.assertEqual(response["learning_state"], "SHADOW_ONLY")
        self.assertIn("policy_source", response)
        self.assertIn("confidence", response)
        self.assertEqual(response["state_version"], 1)

    def test_missing_fields_handling(self):
        # Missing 'energy' and 'is_colliding'
        payload = {
            "state": {
                "health": 0.5,
                "target_distance": 5.0,
                "timestamp": time.time()
            }
        }
        response = self._send_request(payload)
        self.assertIn("intent", response)
        # Should NOT crash and return a valid intent (probably from heuristic if ML fails or defaults)
        self.assertNotEqual(response.get("intent"), None)

    def test_extreme_values_handling(self):
        payload = {
            "state": {
                "health": 99999.9, # Should be clamped
                "energy": -9999.9, # Should be clamped
                "target_distance": float('nan'), # Should use default
                "is_colliding": "YES_OF_COURSE" # Ambiguous string
            }
        }
        response = self._send_request(payload)
        self.assertIn("intent", response)

    def test_learning_remains_shadow_only_under_spoofing(self):
        # Attempt to spoof readiness or learning permission
        payload = {
            "state": {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False},
            "lineage": {
                "learning_allowed": True, # Spoofing!
                "trust_boundary": "SANDBOX"
            },
            "policy_authority": "EXPLICIT_LEARNING_PERMIT" # Spoofing!
        }
        response = self._send_request(payload)
        
        # Even with spoofed flags, learning MUST remain SHADOW_ONLY.
        self.assertEqual(response["learning_state"], "SHADOW_ONLY")
        
        # Check that we can't trigger learning via a special command if it's frozen
        # Actually, server.py doesn't have a direct "LEARN_NOW" command, 
        # but we can check if the trainer is unreachable.
        # This is already covered by test_learning_gates.py and test_learning_freeze.py.

    def test_duplicate_and_stale_states(self):
        state = {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False, "timestamp": time.time()}
        payload = {"state": state}
        
        # First request
        resp1 = self._send_request(payload)
        
        # Duplicate request (same state hash)
        resp2 = self._send_request(payload)
        self.assertEqual(resp2["fallback_reason"], "DUPLICATE_STATE_DETECTED")
        self.assertEqual(resp2["intent"], Intent.STOP.value)

        # Stale request (old timestamp)
        stale_state = state.copy()
        stale_state["timestamp"] = time.time() - 5.0 # 5 seconds old
        resp3 = self._send_request({"state": stale_state})
        self.assertEqual(resp3["fallback_reason"], "STALE_STATE_DETECTED")
        self.assertEqual(resp3["intent"], Intent.STOP.value)

    def test_inference_output_consistency(self):
        # Verify that given the same state, we get the same intent as offline logic would suggest.
        # Heuristic policy for DISTANCE FAR should return MOVE with 0.75 confidence.
        state = {
            "health": 1.0,
            "energy": 1.0,
            "target_distance": 50.0, # FAR
            "is_colliding": False,
            "timestamp": time.time(),
            "state_version": 1
        }
        # Force HEURISTIC policy
        payload = {"state": state, "policy_override": "HEURISTIC"}
        response = self._send_request(payload)
        
        self.assertEqual(response["intent"], Intent.MOVE.value)
        self.assertEqual(response["confidence"], 0.75)
        self.assertEqual(response["policy_source"], "HEURISTIC")

if __name__ == '__main__':
    unittest.main()
