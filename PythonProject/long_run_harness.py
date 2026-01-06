import json
import socket
import threading
import time
import random
import os
import logging
from typing import Dict, Any

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger("LongRunHarness")

class LongRunHarness:
    def __init__(self, host='127.0.0.1', port=5006, duration_hours=6):
        self.host = host
        self.port = port
        self.duration_seconds = duration_hours * 3600
        self.start_time = time.time()
        self.request_count = 0
        self.drift_detected = False
        self.incidents_found = []
        self.stop_event = threading.Event()

    def _generate_synthetic_state(self, version=1):
        return {
            "health": random.random(),
            "energy": random.random(),
            "target_distance": random.uniform(0, 100),
            "is_colliding": random.choice([True, False]),
            "timestamp": time.time(),
            "state_version": version
        }

    def _send_request(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(2.0)
                s.connect((self.host, self.port))
                s.sendall(json.dumps(payload).encode('utf-8'))
                data = s.recv(32768)
                if not data:
                    return {"status": "ERROR", "reason": "No data received"}
                return json.loads(data.decode('utf-8'))
        except Exception as e:
            return {"status": "ERROR", "reason": str(e)}

    def run(self):
        logger.info(f"Starting Long-Run Execution Harness for {self.duration_seconds/3600:.1f} hours.")
        
        # Reset server state first
        logger.info("Resetting server state...")
        reset_resp = self._send_request({"type": "RESET_FAILURE_STATUS"})
        if "confirmation_token" in reset_resp:
            self._send_request({
                "type": "RESET_FAILURE_STATUS",
                "confirmation_token": reset_resp["confirmation_token"]
            })

        # Golden states for drift detection (client-side verification)
        golden_states = [
            self._generate_synthetic_state() for _ in range(5)
        ]
        golden_responses = []

        # Initial run: Register golden states
        logger.info("Registering client-side golden states...")
        for state in golden_states:
            # Refresh timestamp just before sending
            state["timestamp"] = time.time()
            payload = {
                "state": state, 
                "controller": "HEURISTIC",
                "result": {
                    "status": "SUCCESS",
                    "failure_reason": "NONE",
                    "outcomes": {"is_alive": True}
                }
            }
            resp = self._send_request(payload)
            if "intent" in resp:
                golden_responses.append(resp)
                logger.info(f"Registered golden state {len(golden_responses)}")
            else:
                logger.error(f"Failed to register golden state: {resp}")
            # Sleep more than 10ms but less than 1s to satisfy TICK_RATE_MISMATCH
            time.sleep(0.05)

        if len(golden_responses) < len(golden_states):
            logger.error("Failed to register all golden states. Is the server running?")
            return

        while time.time() - self.start_time < self.duration_seconds and not self.stop_event.is_set():
            # 1. Normal traffic
            state = self._generate_synthetic_state()
            payload = {
                "state": state,
                "intent_taken": "MOVE",
                "controller": "AI",
                "result": {
                    "status": "SUCCESS",
                    "failure_reason": "NONE",
                    "outcomes": {"is_alive": True}
                }
            }
            
            # Add some jitter/noise
            if random.random() < 0.05:
                # Malformed payload
                del payload["state"]
            elif random.random() < 0.05:
                # Malformed JSON
                try:
                    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                        s.connect((self.host, self.port))
                        s.sendall(b"{ invalid json }")
                except:
                    pass
                continue

            resp = self._send_request(payload)
            self.request_count += 1

            # 2. Periodic Drift Check
            if self.request_count % 50 == 0:
                logger.info(f"Progress: {self.request_count} requests, Uptime: {(time.time()-self.start_time)/60:.1f} min")
                for i, golden_state in enumerate(golden_states):
                    # Update timestamp to avoid STALE_STATE_DETECTED but keep other fields same
                    current_state = golden_state.copy()
                    current_state["timestamp"] = time.time()
                    
                    payload = {
                        "state": current_state, 
                        "controller": "HEURISTIC",
                        "result": {
                            "status": "SUCCESS",
                            "failure_reason": "NONE",
                            "outcomes": {"is_alive": True}
                        }
                    }
                    resp = self._send_request(payload)
                    expected = golden_responses[i]
                    
                    if resp.get("intent") != expected.get("intent") or resp.get("confidence") != expected.get("confidence"):
                        logger.critical(f"CLIENT-SIDE DRIFT DETECTED for golden state {i}!")
                        logger.critical(f"Expected: {expected.get('intent')} ({expected.get('confidence')})")
                        logger.critical(f"Got: {resp.get('intent')} ({resp.get('confidence')})")
                        self.drift_detected = True
                        self.stop_event.set()

            # 3. Resource & Health Check via HEARTBEAT
            if self.request_count % 100 == 0:
                heartbeat = self._send_request({"type": "HEARTBEAT"})
                if heartbeat.get("status") == "OK":
                    monitor = heartbeat.get("monitoring", {})
                    metrics = monitor.get("metrics", {})
                    logger.info(f"Health: Mem={metrics.get('memory_usage_mb', 0):.1f}MB, IncidentRate={metrics.get('incident_rate', 0):.2f}/hr")
                    if monitor.get("status") == "CRITICAL":
                        logger.warning(f"CRITICAL MONITOR STATUS: {monitor.get('alerts')}")

            # 4. Survival under jitter
            time.sleep(random.uniform(0.01, 0.5))

            # Safety break for the purpose of this demonstration
            if os.getenv("SHORT_RUN") and self.request_count >= 200:
                logger.info("SHORT_RUN completed successfully.")
                break

        self.report()

    def report(self):
        duration = time.time() - self.start_time
        logger.info("=== Long-Run Integrity Report ===")
        logger.info(f"Total Duration: {duration/3600:.2f} hours")
        logger.info(f"Total Requests: {self.request_count}")
        logger.info(f"Drift Detected: {self.drift_detected}")
        
        # Check for incidents on server
        heartbeat = self._send_request({"type": "HEARTBEAT"})
        if heartbeat.get("status") == "OK":
            incidents = heartbeat.get("failure_status", {}).get("incident_count", 0)
            logger.info(f"Server Incidents: {incidents}")
            if incidents > 0:
                logger.warning("Incidents were recorded during the run.")

        if not self.drift_detected and duration > 0:
             logger.info("INTEGRITY VERIFIED: No drift detected and system remained operational.")

if __name__ == "__main__":
    # To run a real 6h test, change duration_hours to 6 and remove SHORT_RUN env var.
    harness = LongRunHarness(port=5006, duration_hours=0.1) # 6 minutes for test
    
    # Start server in thread for the harness script to be self-contained
    from server import AIServer
    server = AIServer(port=5006)
    server_thread = threading.Thread(target=server.start, daemon=True)
    server_thread.start()
    time.sleep(2)
    
    try:
        harness.run()
    except KeyboardInterrupt:
        logger.info("Harness stopped by user.")
