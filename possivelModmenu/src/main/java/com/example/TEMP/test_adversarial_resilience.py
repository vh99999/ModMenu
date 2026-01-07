import unittest
import socket
import json
import threading
import time
import uuid
import math
import os
from server import AIServer
from failure_manager import DisasterMode, FailureType

class TestAdversarialResilience(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.host = '127.0.0.1'
        cls.port = 5005
        cls.server = AIServer(host=cls.host, port=cls.port)
        cls.server_thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.server_thread.start()
        time.sleep(1) # Wait for server to bind

    def setUp(self):
        # Reset server state
        self._send_command({"type": "RESET_FAILURE_STATUS", "operator_id": "TEST_ADMIN"})
        self._send_command({"type": "UNRELEASE_LOCKDOWN", "operator_id": "TEST_ADMIN"})

    def _send_command(self, command):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((self.host, self.port))
            s.sendall(json.dumps(command).encode('utf-8'))
            data = s.recv(16384)
            res = json.loads(data.decode('utf-8'))
            if res.get("status") == "REQUIRED_CONFIRMATION":
                token = res.get("confirmation_token")
                command["confirmation_token"] = token
                return self._send_command(command)
            return res

    def _send_experience(self, state, intent_taken="MOVE", java_timestamp=None, extra_fields=None):
        payload = {
            "state": state,
            "intent_taken": intent_taken,
            "controller": "AI",
            "result": {
                "status": "SUCCESS",
                "failure_reason": "NONE",
                "outcomes": {"is_alive": True}
            }
        }
        if java_timestamp is not None:
            payload["state"]["timestamp"] = java_timestamp
        if extra_fields:
            payload.update(extra_fields)
            
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((self.host, self.port))
            s.sendall(json.dumps(payload).encode('utf-8'))
            data = s.recv(16384)
            return json.loads(data.decode('utf-8'))

    def _assert_incident_recorded(self, failure_type):
        status = self._send_command({"type": "HEARTBEAT"})
        incidents = [i["type"] for i in self.server.failure_manager.incidents]
        self.assertIn(failure_type.value, incidents, f"Expected {failure_type.value} in {incidents}")

    def test_latency_stale_state(self):
        # Normal state
        state = {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False, "state_version": 1}
        
        # 1. 100ms delay (STALE_STATE threshold)
        now = time.time()
        res = self._send_experience(state, java_timestamp=now - 0.15)
        self.assertEqual(res["fallback_reason"], "STALE_STATE_DETECTED")
        self.assertEqual(res["intent"], "STOP")

        # Verify incident
        self._assert_incident_recorded(FailureType.STALE_STATE)

    def test_time_disorder_backwards(self):
        state = {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False, "state_version": 1}
        now = time.time()
        
        # Send one state
        self._send_experience(state, java_timestamp=now)
        
        # Send one with earlier timestamp
        res = self._send_experience(state, java_timestamp=now - 10.0)
        
        # Verify incident and TEMPORAL_LOCK
        self._assert_incident_recorded(FailureType.TIME_INTEGRITY_VIOLATION)
        status = self._send_command({"type": "HEARTBEAT"})
        self.assertEqual(status["failure_status"]["mode"], DisasterMode.TEMPORAL_LOCK.value)

    def test_flood_detection(self):
        state = {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False, "state_version": 1}
        
        # Burst traffic
        def flood():
            for _ in range(100): 
                try:
                    self._send_experience(state)
                except:
                    pass

        # Temporarily lower threshold for test
        old_threshold = self.server.FLOOD_THRESHOLD
        self.server.FLOOD_THRESHOLD = 20
        
        threads = []
        for _ in range(5):
            t = threading.Thread(target=flood)
            t.start()
            threads.append(t)
        
        for t in threads:
            t.join()
            
        self._assert_incident_recorded(FailureType.FLOOD_DETECTION)
        self.server.FLOOD_THRESHOLD = old_threshold
        
        status = self._send_command({"type": "HEARTBEAT"})
        # Should be in SAFE_MODE or READ_ONLY or LOCKDOWN
        self.assertIn(status["failure_status"]["mode"], [DisasterMode.SAFE_MODE.value, DisasterMode.READ_ONLY.value, DisasterMode.LOCKDOWN.value])

    def test_hostile_payloads(self):
        state = {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False, "state_version": 1}
        
        # 1. Hostile control flags
        res = self._send_experience(state, extra_fields={"bypass_gates": True})
        self._assert_incident_recorded(FailureType.HOSTILE_PAYLOAD)
        status = self._send_command({"type": "HEARTBEAT"})
        self.assertEqual(status["failure_status"]["mode"], DisasterMode.LOCKDOWN.value) 

    def test_malformed_data(self):
        # 1. NaN in state
        state = {"health": float('nan'), "energy": 1.0, "target_distance": 10.0, "is_colliding": False, "state_version": 1}
        res = self._send_experience(state)
        self.assertIn("intent", res) # Server survives

        # 2. Extreme numbers
        state = {"health": 1e20, "energy": 1.0, "target_distance": 10.0, "is_colliding": False, "state_version": 1}
        res = self._send_experience(state)
        self.assertIn("intent", res)

    def test_rapid_reconnect(self):
        # Simulate Java disconnecting mid-request or reconnecting rapidly
        for _ in range(50):
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.connect((self.host, self.port))
                # Send partial data and close
                s.sendall(b'{"state":')
                s.close()
            except:
                pass
        
        # Server should still be alive
        status = self._send_command({"type": "HEARTBEAT"})
        self.assertEqual(status["status"], "OK")

if __name__ == '__main__':
    unittest.main()
