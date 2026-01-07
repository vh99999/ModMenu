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

class TestShadowLearning(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Use a different port to avoid conflicts
        cls.server = AIServer(host='127.0.0.1', port=5006)
        cls.server_thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.server_thread.start()
        time.sleep(1) # Wait for server to start

    def _send_request(self, payload):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect(('127.0.0.1', 5006))
            s.sendall(json.dumps(payload).encode('utf-8'))
            data = s.recv(16384)
            if not data:
                return None
            return json.loads(data.decode('utf-8'))

    def _get_heartbeat(self):
        return self._send_request({"type": "HEARTBEAT"})

    def test_shadow_learning_flow(self):
        # 0. Reset server state to NORMAL and clear all boosts
        reset_payload = {"type": "RESET_FAILURE_STATUS"}
        resp_reset = self._send_request(reset_payload)
        if resp_reset and "confirmation_token" in resp_reset:
            reset_payload["confirmation_token"] = resp_reset["confirmation_token"]
            self._send_request(reset_payload)
        
        time.sleep(0.2) # Allow monitor to stabilize

        # 1. Enable Shadow Learning
        resp = self._send_request({"type": "SET_LIVE_SHADOW_LEARNING", "enabled": True})
        self.assertEqual(resp["status"], "SUCCESS")
        self.assertTrue(resp["live_shadow_learning"])

        # 2. Verify Initial State
        valid_result = {"status": "SUCCESS", "failure_reason": "NONE", "outcomes": {"is_alive": True}}
        
        # Use a state that is guaranteed unique with a UUID
        state1 = {
            "health": 0.999, "energy": 0.999, "target_distance": 10.0, 
            "is_colliding": False, "state_version": 1, "test_uuid": str(uuid.uuid4())
        }
        resp1 = self._send_request({
            "state": state1,
            "intent_taken": "MOVE",
            "controller": "AI",
            "result": valid_result
        })
        initial_confidence = resp1["confidence"]

        # 3. Send data to trigger learning in ShadowPolicy
        # We vary the state to avoid DUPLICATE_STATE and satisfy Quality Gate.
        # We send 200 requests to ensure any initial violations are diluted.
        for i in range(1, 201):
            s = {
                "health": 0.9 - (i * 0.001),
                "energy": 0.9 - (i * 0.001),
                "target_distance": 20.0 + i,
                "is_colliding": False,
                "state_version": 1,
                "test_uuid": str(uuid.uuid4())
            }
            self._send_request({
                "state": s,
                "intent_taken": "MOVE",
                "controller": "AI",
                "result": valid_result,
                "policy_authority": "SHADOW_LEARNING_PERMIT"
            })
            if i % 50 == 0:
                time.sleep(0.01)

        # 4. Verify ActivePolicy has NOT changed (Requirement 1)
        state_verify = {
            "health": 0.45, "energy": 0.45, "target_distance": 50.0, 
            "is_colliding": False, "state_version": 1, "test_uuid": str(uuid.uuid4())
        }
        resp2 = self._send_request({
            "state": state_verify,
            "result": valid_result,
            "controller": "AI"
        })
        self.assertEqual(resp2["confidence"], initial_confidence, "ActivePolicy MUST be immutable during learning")

        # 5. Commit Shadow Learning (Requirement 4)
        commit_payload = {"type": "COMMIT_SHADOW_LEARNING"}
        resp_commit = self._send_request(commit_payload)
        if resp_commit and "confirmation_token" in resp_commit:
            commit_payload["confirmation_token"] = resp_commit["confirmation_token"]
            resp_commit = self._send_request(commit_payload)
        
        self.assertEqual(resp_commit["status"], "SUCCESS")
        committed_boost = resp_commit["active_boost"]
        self.assertGreater(committed_boost, 0.0, "Commit should result in positive boost")

        # 6. Verify ActivePolicy HAS changed after commit
        state_verify_2 = {
            "health": 0.44, "energy": 0.44, "target_distance": 51.0, 
            "is_colliding": False, "state_version": 1, "test_uuid": str(uuid.uuid4())
        }
        resp3 = self._send_request({
            "state": state_verify_2,
            "result": valid_result,
            "controller": "AI"
        })
        
        # We check if the behavior changed.
        self.assertNotEqual(resp3["confidence"], initial_confidence, "ActivePolicy behavior should change after commit")
        
        # 7. Rollback (Requirement 5)
        rollback_payload = {"type": "ROLLBACK_SHADOW_LEARNING"}
        resp_rb = self._send_request(rollback_payload)
        if resp_rb and "confirmation_token" in resp_rb:
            rollback_payload["confirmation_token"] = resp_rb["confirmation_token"]
            resp_rb = self._send_request(rollback_payload)
        
        self.assertEqual(resp_rb["status"], "SUCCESS")
        self.assertEqual(resp_rb["active_boost"], 0.0)

        # 8. Verify ActivePolicy restored to initial
        state_verify_3 = {
            "health": 0.43, "energy": 0.43, "target_distance": 52.0, 
            "is_colliding": False, "state_version": 1, "test_uuid": str(uuid.uuid4())
        }
        resp4 = self._send_request({
            "state": state_verify_3,
            "result": valid_result,
            "controller": "AI"
        })
        self.assertEqual(resp4["confidence"], initial_confidence)

    def test_shadow_learning_disabled_by_default(self):
        # Reset server state to clear previous boosts and failure modes
        reset_payload = {"type": "RESET_FAILURE_STATUS"}
        resp_reset = self._send_request(reset_payload)
        if resp_reset and "confirmation_token" in resp_reset:
            reset_payload["confirmation_token"] = resp_reset["confirmation_token"]
            self._send_request(reset_payload)

        # Ensure shadow learning is disabled
        self._send_request({"type": "SET_LIVE_SHADOW_LEARNING", "enabled": False})
        
        state = {
            "health": 1.0, "energy": 1.0, "target_distance": 10.0, 
            "is_colliding": False, "state_version": 1
        }
        payload = {
            "state": state, 
            "intent_taken": "MOVE", 
            "controller": "AI",
            "result": {"status": "SUCCESS", "failure_reason": "NONE", "outcomes": {"is_alive": True}}
        }
        
        # Initial call
        resp1 = self._send_request(payload)
        initial_conf = resp1["confidence"]

        # Send data with learning disabled
        for i in range(5):
            s = state.copy()
            s["health"] = 1.0 - (i * 0.001)
            
            p = payload.copy()
            p["state"] = s
            self._send_request(p)

        # Commit (should have 0 boost)
        commit_payload = {"type": "COMMIT_SHADOW_LEARNING"}
        resp_commit = self._send_request(commit_payload)
        if resp_commit and "confirmation_token" in resp_commit:
            commit_payload["confirmation_token"] = resp_commit["confirmation_token"]
            resp_commit = self._send_request(commit_payload)
            
        self.assertEqual(resp_commit["active_boost"], 0.0)

if __name__ == '__main__':
    unittest.main()
