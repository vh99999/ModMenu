import socket
import json
import logging
import threading
import time
from typing import Dict, Any, Tuple, Optional

from state_parser import StateParser
from reward import RewardCalculator
from policy import RandomWeightedPolicy, HeuristicCombatPolicy, Policy
from memory import MemoryBuffer
from trainer import Trainer
from intent_space import Intent, IntentValidator
from audit import Auditor
from dataset import DatasetPipeline

# Configure logging to be more descriptive
logging.basicConfig(
    level=logging.INFO, 
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s'
)
logger = logging.getLogger("AIServer")

class AIServer:
    """
    Non-blocking TCP server that acts as the bridge between 
    the game (Minecraft) and the semantic AI decision logic.
    
    Controller Modes supported:
    - HUMAN: AI observes only, does not control.
    - HEURISTIC: AI uses rule-based logic to decide.
    - AI: AI uses learned models (future) to decide.
    """
    def __init__(self, host: str = '127.0.0.1', port: int = 5001):
        self.host = host
        self.port = port
        self.version = "1.2.0"
        
        # Core Components
        self.policies: Dict[str, Policy] = {
            "RANDOM": RandomWeightedPolicy(),
            "HEURISTIC": HeuristicCombatPolicy()
        }
        self.active_policy_name = "HEURISTIC"
        
        self.memory = MemoryBuffer(capacity=5000)
        self.trainer = Trainer(self.policies["RANDOM"]) # Trainer can be updated to point to a LearnedPolicy later
        self.parser = StateParser()
        self.reward_calc = RewardCalculator()
        self.auditor = Auditor()
        
        self.episode_id = int(time.time()) # Use timestamp as base for episode IDs

    def handle_client(self, conn: socket.socket, addr: Tuple[str, int]) -> None:
        """
        Processes a single request-response cycle.
        Stateless per-request: does not rely on persistent client connections.
        """
        try:
            with conn:
                conn.settimeout(5.0)
                data = conn.recv(16384) 
                if not data:
                    return

                try:
                    request = json.loads(data.decode('utf-8'))
                except json.JSONDecodeError:
                    logger.warning(f"Malformed JSON received from {addr}")
                    self._send_error(conn, "Invalid JSON format")
                    return

                # 0. Handle Heartbeat / Healthcheck
                if request.get("type") == "HEARTBEAT":
                    # Calculate dataset metrics if we have data
                    dataset_metrics = {}
                    if len(self.memory) > 0:
                        processed = DatasetPipeline.process_experiences(list(self.memory.buffer))
                        dataset_metrics = DatasetPipeline.get_metrics(processed)

                    conn.sendall(json.dumps({
                        "status": "OK",
                        "version": self.version,
                        "timestamp": time.time(),
                        "active_policy": self.active_policy_name,
                        "audit_metrics": self.auditor.calculate_drift_metrics(),
                        "dataset_metrics": dataset_metrics
                    }).encode('utf-8'))
                    return

                # 1. Validate Payload structure
                if "state" not in request:
                    self._send_error(conn, "Missing 'state' in request")
                    return

                # 2. Parse and validate incoming state
                raw_state = request.get("state")
                normalized_state = self.parser.parse(raw_state)
                
                if normalized_state is None:
                    self._send_error(conn, "Malformed state data (Parser rejected)")
                    return

                # 3. Process experience (Shadow Learning)
                intent_taken = request.get("intent_taken")
                controller = request.get("controller", "AI").upper()
                last_confidence = request.get("last_confidence", 0.0)

                # STRICT VALIDATION: Java Result Contract
                raw_result = request.get("result", {})
                result = IntentValidator.validate_result(raw_result)

                # Validate controller mode
                if controller not in ["HUMAN", "HEURISTIC", "AI"]:
                    logger.warning(f"Unknown controller mode: {controller}. Defaulting to AI.")
                    controller = "AI"

                # Validate intent_taken (CONTRACT VIOLATION if invalid)
                if intent_taken and not Intent.has_value(intent_taken):
                    logger.error(f"CONTRACT VIOLATION: Invalid intent received from Java: {intent_taken}. Rejecting experience.")
                    intent_taken = None

                # Calculate reward for the action just taken
                reward, reward_breakdown = self.reward_calc.calculate(result)

                # 3.1 AUDIT RECORDING
                # We record what Java reports as having just happened.
                audit_entry = self.auditor.record_cycle(
                    raw_state=raw_state,
                    state_version=normalized_state["version"],
                    intent_issued=intent_taken if intent_taken else "NONE",
                    confidence=last_confidence,
                    policy_source=request.get("policy_source", "UNKNOWN"),
                    controller=controller,
                    raw_java_result=raw_result,
                    reward_total=reward,
                    reward_breakdown=reward_breakdown
                )

                # Store in memory and train if we have a valid experience and it passes QUALITY GATES
                if intent_taken and self.auditor.check_quality_gate(audit_entry):
                    logger.debug(f"Experience: intent={intent_taken}, controller={controller}, reward={reward:.2f}")
                    self.memory.push(
                        state=normalized_state,
                        intent=intent_taken,
                        confidence=last_confidence,
                        result=result,
                        reward=reward,
                        reward_breakdown=reward_breakdown,
                        controller=controller,
                        episode_id=self.episode_id
                    )
                    
                    # Trainer handles IL (HUMAN) vs RL (AI/HEURISTIC)
                    experience_for_trainer = {
                        "state": normalized_state,
                        "intent": intent_taken,
                        "reward": reward,
                        "controller": controller
                    }
                    self.trainer.train_on_experience(experience_for_trainer)

                # 3.2 Episode Management: If dead, next experience belongs to a new episode
                if result["outcomes"].get("is_alive") is False:
                    logger.info(f"Episode {self.episode_id} ended (Death detected). Incrementing ID.")
                    self.episode_id += 1

                # 4. Decide next intent
                next_intent = "STOP"
                confidence = 1.0

                if controller != "HUMAN":
                    requested_policy = request.get("policy_override", self.active_policy_name)
                    policy = self.policies.get(requested_policy, self.policies[self.active_policy_name])
                    next_intent, confidence = policy.decide(normalized_state)
                else:
                    # In HUMAN mode, we just observe, but we can still suggest what we WOULD do
                    suggested_intent, suggested_conf = self.policies[self.active_policy_name].decide(normalized_state)
                    logger.debug(f"Shadow Observation: Human took {intent_taken}, AI would take {suggested_intent}")

                # 5. Return response (Validate outgoing intent)
                if not Intent.has_value(next_intent):
                    logger.error(f"POLICY VIOLATION: Policy generated invalid intent '{next_intent}'. Falling back to STOP.")
                    next_intent = Intent.STOP.value
                    confidence = 0.0

                response = {
                    "intent": next_intent,
                    "confidence": float(confidence),
                    "reward_calculated": float(reward),
                    "reward_breakdown": reward_breakdown,
                    "state_version": normalized_state["version"],
                    "server_version": self.version
                }
                
                conn.sendall(json.dumps(response).encode('utf-8'))

        except socket.timeout:
            logger.debug(f"Connection timeout for {addr}")
        except Exception as e:
            logger.error(f"Unexpected error handling client {addr}: {e}", exc_info=True)
            self._send_error(conn, "Internal Server Error")

    def _send_error(self, conn: socket.socket, message: str) -> None:
        """Helper to send standard error responses."""
        try:
            response = {"error": message, "status": "ERROR"}
            conn.sendall(json.dumps(response).encode('utf-8'))
        except Exception:
            pass

    def start(self) -> None:
        """
        Starts the TCP server loop.
        """
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                s.bind((self.host, self.port))
                s.listen()
                logger.info(f"Generic AI Server established at {self.host}:{self.port}")

                while True:
                    conn, addr = s.accept()
                    # Non-blocking handling via threads ensures high throughput
                    client_thread = threading.Thread(
                        target=self.handle_client,
                        args=(conn, addr),
                        daemon=True
                    )
                    client_thread.start()
        except Exception as e:
            logger.critical(f"Server failure: {e}")

if __name__ == "__main__":
    server = AIServer()
    server.start()
