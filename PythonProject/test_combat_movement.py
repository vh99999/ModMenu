import unittest
import json
import socket
import threading
import time
import os
import uuid

os.environ["AISERVER_MODE"] = "PROD_SERVER"
from server import AIServer
from intent_space import Intent

class TestCombatMovement(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = AIServer(host='127.0.0.1', port=5008)
        cls.server_thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.server_thread.start()
        time.sleep(1)

    def _send_request(self, payload):
        if "state" in payload and "result" not in payload:
            payload["result"] = {"status": "SUCCESS", "failure_reason": "NONE", "outcomes": {"is_alive": True}}
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect(('127.0.0.1', 5008))
            s.sendall(json.dumps(payload).encode('utf-8'))
            data = s.recv(16384)
            return json.loads(data.decode('utf-8')) if data else {}

    def test_combat_movement_lifecycle(self):
        # 0. Reset
        self._send_request({"type": "RESET_PASSIVE_STORE"})
        self._send_request({"type": "CONTROL_MODE", "mode": "HUMAN", "source": "GUI"})

        # Step 1: Shadow observe MOVE while FAR from target (FRONT)
        # target_yaw 0 = FRONT
        state_far = {"health": 1.0, "energy": 1.0, "target_distance": 20.0, "target_yaw": 0.0, "pos_x": 0.0, "pos_y": 0.0, "pos_z": 0.0}
        self._send_request({
            "state": state_far,
            "intent_taken": "MOVE",
            "intent_params": {"vector": [1.0, 0.0, 0.0], "sprinting": True},
            "controller": "HUMAN"
        })
        
        # Step 2: Shadow observe STOP while CLOSE to target
        state_close = {"health": 1.0, "energy": 1.0, "target_distance": 2.0, "target_yaw": 0.0, "pos_x": 10.0, "pos_y": 0.0, "pos_z": 0.0}
        self._send_request({
            "state": state_close,
            "intent_taken": "STOP",
            "controller": "HUMAN"
        })

        # Step 3: Promote
        # Need to finish episodes and flush
        self._send_request({"state": state_close, "intent_taken": "STOP", "controller": "HUMAN", "result": {"status": "SUCCESS", "outcomes": {"is_alive": False}}})
        
        # Run promotion
        import promote.run
        promote.run.run_promotion()

        # Step 4: Reload and Switch to AI
        self._send_request({"type": "RELOAD_KNOWLEDGE"})
        self._send_request({"type": "CONTROL_MODE", "mode": "AI", "source": "GUI"})

        # Step 5: Verify Active behavior - MOVE when far
        resp_far = self._send_request({"state": state_far, "controller": "AI"})
        self.assertEqual(resp_far.get("intent"), "MOVE")
        print(f"AI Decision (FAR): {resp_far.get('intent')}")

        # Step 6: Verify Active behavior - STOP when close
        resp_close = self._send_request({"state": state_close, "controller": "AI"})
        self.assertEqual(resp_close.get("intent"), "STOP")
        print(f"AI Decision (CLOSE): {resp_close.get('intent')}")

        # Step 7: Verify safety gate
        # Mock repeated failure
        for i in range(6):
             self._send_request({
                 "state": state_far,
                 "intent_taken": "MOVE",
                 "controller": "AI",
                 "result": {"status": "FAILURE", "failure_reason": "BLOCKED"}
             })
        
        # The next decision after 5+ failures should be STOP (Safety Gate)
        resp_gate = self._send_request({"state": state_far, "controller": "AI"})
        # AIServer arbitrator handles displacement tracking.
        # But we need to ensure MLPolicy sees it as blocked repeatedly.
        # The test_combat_movement_lifecycle already sent 6 requests with same state_far.
        # Let's check the log for safety gate.
        # In fact, I'll add a specific test for it.

    def test_movement_safety_gate(self):
        self._send_request({"type": "RESET_PASSIVE_STORE"})
        self._send_request({"type": "CONTROL_MODE", "mode": "AI", "source": "GUI"})
        
        # We need some knowledge so it doesn't just return STOP/KNOWLEDGE_MISSING
        state = {"health": 1.0, "energy": 1.0, "target_distance": 20.0, "target_yaw": 0.0, "pos_x": 0.0, "pos_y": 0.0, "pos_z": 0.0}
        self._send_request({"state": state, "intent_taken": "MOVE", "controller": "HUMAN"})
        self._send_request({"state": state, "intent_taken": "MOVE", "controller": "HUMAN", "result": {"status": "SUCCESS", "outcomes": {"is_alive": False}}})
        
        import promote.run
        promote.run.run_promotion()
        self._send_request({"type": "RELOAD_KNOWLEDGE"})

        # Send same state repeatedly to trigger MOVEMENT_BLOCKED in Arbitrator
        # Arbitrator tracks displacement using self.last_active_pos
        for i in range(5):
            resp = self._send_request({"state": state, "controller": "AI"})
            print(f"Gate Trigger Loop {i}: {resp.get('intent')} ({resp.get('policy_source')})")
        
        # 6th request should trigger safety gate
        resp_final = self._send_request({"state": state, "controller": "AI"})
        print(f"Gate Final Decision: {resp_final.get('intent')} ({resp_final.get('policy_source')})")
        
        self.assertEqual(resp_final.get("intent"), "STOP")
        self.assertIn("SAFETY_GATE", resp_final.get("policy_source"))
        
if __name__ == "__main__":
    unittest.main()
