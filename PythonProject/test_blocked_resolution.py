import unittest
import json
import socket
import threading
import time
import os
import uuid
import math

# Set environment before imports
os.environ["AISERVER_MODE"] = "PROD_SERVER"

from server import AIServer
from intent_space import Intent

class TestBlockedResolution(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Use a different port to avoid conflicts
        cls.server = AIServer(host='127.0.0.1', port=5007)
        cls.server_thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.server_thread.start()
        time.sleep(1) # Wait for server to start

    def _send_request(self, payload):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect(('127.0.0.1', 5007))
            s.sendall(json.dumps(payload).encode('utf-8'))
            data = s.recv(16384)
            if not data:
                return None
            return json.loads(data.decode('utf-8'))

    def test_blocked_and_resolved_shadow_learning(self):
        print("\n[STEP 0] Resetting server state and passive store...")
        self._send_request({"type": "RESET_FAILURE_STATUS", "operator_id": "TEST"})
        self._send_request({"type": "RESET_PASSIVE_STORE"})
        self._send_request({"type": "CONTROL_MODE", "mode": "HUMAN", "source": "GUI"})
        
        valid_result = {"status": "SUCCESS", "failure_reason": "NONE", "outcomes": {"is_alive": True}}
        
        # Fixed state for the "blocked" situation
        state_blocked = {
            "health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": True,
            "pos_x": 0.0, "pos_y": 0.0, "pos_z": 0.0, "state_version": 1,
            "situation": "STUCK"
        }
        
        # Intermediary dummy state
        state_dummy = state_blocked.copy()
        state_dummy["situation"] = "DUMMY"
        
        move_params = {"vector": [1.0, 0.0, 0.0], "sprinting": True}
        episode_id = str(uuid.uuid4())
        
        print("[STEP 1] Establishing baseline position for HUMAN...")
        self._send_request({
            "state": state_blocked, "intent_taken": "MOVE", "intent_params": move_params,
            "controller": "HUMAN", "episode_id": episode_id, "result": valid_result
        })
        
        print("[STEP 2] Simulating blocked MOVE (zero displacement)...")
        # Ensure block detection
        self._send_request({
            "state": state_dummy, "intent_taken": "MOVE", "intent_params": move_params,
            "controller": "HUMAN", "episode_id": episode_id, "result": valid_result
        })
        self._send_request({
            "state": state_blocked, "intent_taken": "MOVE", "intent_params": move_params,
            "controller": "HUMAN", "episode_id": episode_id, "result": valid_result
        })

        print("[STEP 3] Simulating resolution action (JUMP) with displacement...")
        state_resolved = state_blocked.copy()
        state_resolved["pos_y"] = 1.0 # Successful jump
        
        self._send_request({
            "state": state_resolved, "intent_taken": "JUMP",
            "controller": "HUMAN", "episode_id": episode_id, "result": valid_result
        })

        print("[STEP 3.1] Triggering flush via new episode...")
        self._send_request({
            "state": state_resolved, "intent_taken": "STOP",
            "controller": "HUMAN", "episode_id": str(uuid.uuid4()), "result": valid_result
        })

        print("[STEP 4] Promoting shadow data...")
        from promote.run import run_promotion
        run_promotion()

        print("[STEP 5] Reloading knowledge in AI Server...")
        resp = self._send_request({"type": "RELOAD_KNOWLEDGE"})
        self.assertEqual(resp["status"], "SUCCESS")
        
        print("[STEP 6] Switching to AI mode...")
        self._send_request({"type": "CONTROL_MODE", "mode": "AI", "source": "GUI"})
        
        print("[STEP 7] Verifying Active policy replays JUMP when blocked...")
        # Request 1: established position baseline
        print("  - Request 1: Establishing AI position baseline...")
        self._send_request({
            "state": state_dummy, "controller": "AI", "result": valid_result,
            "intent_taken": "STOP"
        })
        
        # Request 2: Clear cooldowns
        print("  - Request 2: Clearing cooldowns...")
        self._send_request({
            "state": state_dummy, "controller": "AI", "result": valid_result,
            "intent_taken": "STOP"
        })
        
        # Request 3: Established first MOVE
        print("  - Request 3: Establishing first AI MOVE...")
        resp1 = self._send_request({
            "state": state_blocked, "controller": "AI", "result": valid_result,
            "intent_taken": "STOP"
        })
        # It should decide MOVE (distance far)
        if resp1.get("intent") != "MOVE":
             print(f"WARNING: Expected MOVE, got {resp1.get('intent')} (Source: {resp1.get('policy_source')})")
        
        # Request 4: Same state, previous intent was MOVE. Block detected.
        print("  - Request 4: AI should detect block and JUMP...")
        resp2 = self._send_request({
            "state": state_blocked, "controller": "AI", "result": valid_result,
            "intent_taken": "MOVE" 
        })
        
        print(f"AI Decision while blocked: {resp2.get('intent')} (Source: {resp2.get('policy_source')})")
        
        self.assertEqual(resp2.get("intent"), "JUMP")
        self.assertEqual(resp2.get("policy_source"), "ML_PROMOTED_JUMP")

if __name__ == '__main__':
    unittest.main()
