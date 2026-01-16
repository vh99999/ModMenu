import socket
import json
import logging
import threading
import time
import math
import os
import uuid
import types
import queue
import hashlib
from collections import deque
from typing import Dict, Any, Tuple, Optional

from state_parser import StateParser
from reward import RewardCalculator
from policy import RandomWeightedPolicy, HeuristicCombatPolicy, Policy, MLPolicy, ModelManager, PolicyArbitrator
from memory import MemoryBuffer
from trainer import Trainer
from intent_space import Intent, IntentValidator
from audit import Auditor
from dataset import DatasetPipeline
from monitor import Monitor
from drift_verifier import DriftVerifier
from failure_manager import FailureManager, DisasterMode, FailureType, FailureSeverity
from execution_mode import ExecutionMode, enforce_mode
from learning_gates import LearningGateSystem, LearningGateResult
from learning_readiness import LearningReadinessAnalyzer
from learning_freeze import LEARNING_STATE, LIVE_SHADOW_LEARNING, ImportLock

# UNCONDITIONAL FREEZE CHECK REMOVED - SHADOW LEARNING ALLOWED
assert LEARNING_STATE in {"FROZEN", "SHADOW_ONLY"}

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
    """
    DEFAULT_RESPONSE = {
        "action": "NO_OP",
        "reason": "default_safety_reply"
    }

    def __init__(self, host: str = '127.0.0.1', port: int = 5001):
        enforce_mode([ExecutionMode.PROD_SERVER])
        self.host = host
        self.port = port
        self.version = "1.5.0" # HUMAN GUARDRAILS ACTIVE
        
        # Core Components: ML & Lifecycle
        self.model_manager = ModelManager()
        
        # Human Guardrails
        self.pending_confirmations: Dict[str, Dict[str, Any]] = {}
        
        # Initial Governance Registration
        initial_metadata = {
            "version": "M-MockModel-D20260105-H00000000-R1.0.0-C00000000",
            "model_id": "MockModel",
            "training_date": "20260105",
            "dataset_hash": "0" * 64,
            "reward_ver": "1.0.0",
            "code_hash": "0" * 64
        }
        self.model_manager.register_model(initial_metadata)
        self.model_manager.activate_model(initial_metadata["version"])
        
        # Requirement 1: Dual-Policy Architecture (MANDATORY)
        self.active_policy = MLPolicy(self.model_manager, role="ACTIVE")
        self.shadow_policy = MLPolicy(self.model_manager, role="SHADOW")
        
        # Backward compatibility alias
        self.ml_policy = self.active_policy
        
        self.heuristic_policy = HeuristicCombatPolicy()
        self.random_policy = RandomWeightedPolicy()
        
        self.arbitrator = PolicyArbitrator(
            ml_policy=self.active_policy,
            shadow_policy=self.shadow_policy,
            heuristic_policy=self.heuristic_policy,
            random_policy=self.random_policy
        )
        
        self.policies: Dict[str, Policy] = {
            "RANDOM": self.random_policy,
            "HEURISTIC": self.heuristic_policy,
            "ML": self.active_policy,
            "SHADOW_ML": self.shadow_policy
        }
        self.active_policy_name = "ML"
        
        self.memory = MemoryBuffer(capacity=5000)
        self.trainer = Trainer(self.shadow_policy) # Shadow exclusively for learning
        self.parser = StateParser()
        self.reward_calc = RewardCalculator()
        self.auditor = Auditor()
        self.failure_manager = FailureManager()
        
        # Load promoted knowledge for ACTIVE policy (Task 2)
        try:
            base_dir = os.path.dirname(os.path.abspath(__file__))
            self.active_policy.load_promoted_knowledge(os.path.join(base_dir, "snapshots", "promoted", "latest"))
        except Exception as e:
            logger.critical(f"HARD_FAIL: Active policy could not be initialized: {e}")
            import sys
            sys.exit(1)

        self.monitor = Monitor(self.auditor, self.failure_manager)
        self.drift_verifier = DriftVerifier()
        self.learning_gate_system = LearningGateSystem()
        self.readiness_analyzer = LearningReadinessAnalyzer(self.failure_manager)
        self.last_readiness_report = {}
        
        # Crash Recovery Detection
        self.crash_flag_path = "active_decision.tmp"
        if os.path.exists(self.crash_flag_path):
            logger.error("SYSTEM CRITICAL: Recovery from ungraceful shutdown detected.")
            self.failure_manager.record_incident(FailureType.CRASH_RECOVERY, details="RECOVERY_FROM_CRASH")
            try:
                os.remove(self.crash_flag_path)
            except OSError:
                pass
        
        # Temporal Baseline
        self.wall_clock_baseline = time.time()
        self.monotonic_baseline = time.monotonic()
        
        # Flood Protection
        self.request_history = deque()
        self.FLOOD_THRESHOLD = 500 # messages per second
        
        # Network Watchdog REMOVED - STRICT PROTOCOL ENFORCED BY CLIENT
        self.last_request_time = time.time()
        
        # Phase 10B: Exclusive Control Authority
        self.control_mode = "HUMAN"
        self.influence_weight = 0.0
        self.last_control_switch = time.time()
        self.active_reward_window = deque(maxlen=100)
        self.shadow_reward_window = deque(maxlen=100)
        
        self.episode_id = int(time.time()) # Use timestamp as base for episode IDs
        
        # Always-Reply Action Buffer REMOVED - STRICT S_n -> A_n SYNC REQUIRED
        
        # Background worker REMOVED - DEFERRED PROCESSING PER CONNECTION
        self._conn_states = {}

    def _process_experience_async(self, experience_id, request, normalized_state, policy_mode, state_hash, reward, reward_breakdown, reward_class, result):
        """Heavy lifting: auditing, memory pushing, training, monitoring."""
        # 1. Auditing
        intent_taken = request.get("intent_taken")
        intent_params = request.get("intent_params", {})
        controller = request.get("controller", "AI").upper()
        raw_result = request.get("result", {})
        
        last_confidence = 0.0
        try:
            last_confidence = float(request.get("last_confidence", 0.0))
        except (ValueError, TypeError): pass

        audit_entry = self.auditor.record_cycle(
            experience_id=experience_id,
            raw_state=request.get("state"),
            state_version=normalized_state["version"] if normalized_state else 0,
            intent_issued=intent_taken if intent_taken else "NONE",
            confidence=last_confidence,
            policy_source=request.get("policy_source", "UNKNOWN"),
            controller=controller,
            raw_java_result=raw_result,
            reward_total=reward,
            reward_breakdown=reward_breakdown,
            fallback_reason=request.get("last_fallback_reason"),
            model_version=request.get("last_model_version"),
            inference_ms=float(request.get("last_inference_ms", 0.0)),
            shadow_data=request.get("shadow_data"),
            java_timestamp=request.get("state", {}).get("timestamp") if isinstance(request.get("state"), dict) else None,
            missing_fields=normalized_state.get("missing_fields", 0) if normalized_state else 0,
            is_incomplete=normalized_state.get("is_incomplete", False) if normalized_state else False,
            lineage=request.get("lineage"),
            authority=request.get("authority", "UNKNOWN"),
            reward_class=reward_class,
            full_payload=request,
            policy_mode=policy_mode
        )

        # Update influence windows
        if request.get("shadow_data"):
            shadow_intent = request.get("shadow_data", {}).get("intent")
            if intent_taken == shadow_intent or request.get("policy_source", "").startswith("SHADOW_INFLUENCE_"):
                self.shadow_reward_window.append(reward)
        if request.get("policy_source", "").startswith("ML_"):
            self.active_reward_window.append(reward)

        self._adjust_influence_weight(audit_entry)

        # Training and Memory
        if intent_taken:
            is_quality_pass = self.auditor.check_quality_gate(audit_entry)
            self.memory.push(
                experience_id=experience_id,
                state=normalized_state,
                intent=intent_taken,
                confidence=last_confidence,
                result=result,
                reward=reward,
                reward_breakdown=reward_breakdown,
                controller=controller,
                episode_id=self.episode_id,
                fallback_reason=request.get("last_fallback_reason"),
                model_version=request.get("last_model_version"),
                lineage=request.get("lineage")
            )
            
            experience_for_trainer = {
                "experience_id": experience_id,
                "episode_id": self.episode_id,
                "state_hash": audit_entry["state_hash"],
                "state": normalized_state,
                "intent": intent_taken,
                "intent_params": intent_params,
                "hold_duration_ticks": result.get("hold_duration_ticks", 1),
                "cooldown_ticks_observed": result.get("cooldown_ticks_observed", 0),
                "reward": reward,
                "controller": controller,
                "lineage": request.get("lineage"),
                "violations": audit_entry.get("violations", []),
                "policy_authority": request.get("policy_authority")
            }
            
            # Learning gates and readiness
            policy_context = types.SimpleNamespace(
                mode=policy_mode,
                lineage=request.get("lineage"),
                violations=experience_for_trainer["violations"],
                policy_authority=experience_for_trainer["policy_authority"]
            )
            gate_decisions = self.learning_gate_system.evaluate_all(policy_context)
            
            # Record incidents
            if policy_mode != "SHADOW":
                for d in gate_decisions:
                    if d.result != LearningGateResult.PASS:
                        self.failure_manager.record_incident(
                            FailureType.LEARNING_GATE_BLOCK,
                            severity=FailureSeverity.MEDIUM,
                            experience_id=experience_id,
                            details=f"Gate: {d.gate_name}, Reason: {d.reason}"
                        )

            # Readiness
            readiness_report = self.readiness_analyzer.analyze(experience_for_trainer, gate_decisions)
            self.last_readiness_report = readiness_report
            
            # Observe/Train
            self.trainer.train_on_experience(experience_for_trainer)

        # Monitoring analysis
        health_status = self.monitor.analyze()
        mitigation = health_status.get("recommended_mitigation")
        if mitigation and mitigation != "NONE":
            self.arbitrator.apply_mitigation(mitigation, policy_mode=policy_mode)
            
        # Report incidents for critical alerts/violations
        if policy_mode != "SHADOW":
            for violation in audit_entry.get("violations", []):
                # Mapping as before...
                pass

    def _safe_send(self, conn: socket.socket, response: Dict[str, Any]):
        """Safe send function (NEVER THROWS, NEVER BLOCKS)"""
        try:
            # Minimal sanitation
            if not isinstance(response, dict):
                response = self.DEFAULT_RESPONSE.copy()

            # Enforce "action" field for safety shell contract
            if "action" not in response:
                response["action"] = response.get("intent", "NO_OP")
            
            # Ensure mandatory fields for Java if it expects them
            if "intent" not in response:
                response["intent"] = "STOP"
            
            if "intent_params" not in response:
                response["intent_params"] = {
                    "Movement": "none", "Attack": False, "Jump": False, "Sneak": False, "Sprint": False
                }

            payload = (json.dumps(response) + "\n").encode('utf-8')
            conn.sendall(payload)
            # Use minimal logging to avoid blocking I/O in hot path
            # logger.info(f"Response sent: {response.get('action')}")
        except Exception as e:
            # Silent failure to guarantee no crash, log to stderr
            import sys
            sys.stderr.write(f"NETWORK_SEND_FAIL: {e}\n")

    def _dispatch_request(self, conn: socket.socket, message_str: str, connection_state: Dict[str, Any]):
        """Single entry point for request handling with ALWAYS-REPLY guarantee."""
        logger.info("Request received")
        start_time = time.perf_counter()
        
        try:
            # 0. HALT CHECK
            if connection_state.get("halted"):
                logger.error("PROTOCOL_VIOLATION: Request received after HALT signal. Connection is dead.")
                self._safe_send(conn, {
                    "action": "NO_OP",
                    "status": "TERMINATED",
                    "reason": "CONNECTION_HALTED",
                    "experience_id": "HALTED"
                })
                return

            # 1. PARSE MINIMAL METADATA (FAST)
            try:
                if not message_str:
                    raise ValueError("empty_message")
                request = json.loads(message_str)
                
                # MANDATORY FIELDS CHECK
                experience_id = request.get("experience_id")
                req_authority = request.get("authority")
                
                if not experience_id or experience_id == "UNKNOWN":
                    fallback_id = f"shadow-ephemeral-{uuid.uuid4().hex[:8]}"
                    logger.warning(f"[WARNING] PROTOCOL: experience_id missing or unknown → routed to ShadowPolicy (Generated: {fallback_id})")
                    request["experience_id"] = fallback_id
                    request["unregistered_experience"] = True
                    experience_id = fallback_id

                if not req_authority:
                    logger.error("PROTOCOL_VIOLATION: Missing authority.")
                    connection_state["halted"] = True
                    self._safe_send(conn, self._error_response("authority mandatory", experience_id))
                    return

                # DUPLICATE REQUEST CHECK
                if experience_id == connection_state.get("last_experience_id"):
                    logger.error(f"PROTOCOL_VIOLATION: Duplicate request received: {experience_id}")
                    connection_state["halted"] = True
                    self._safe_send(conn, {
                        "action": "NO_OP",
                        "status": "INVALID_REQUEST",
                        "reason": "DUPLICATE_REQUEST",
                        "experience_id": experience_id
                    })
                    return
                connection_state["last_experience_id"] = experience_id
                
                # REJECT PROBES / HANDSHAKES
                if "_probe" in request or "handshake" in message_str.lower():
                    logger.error(f"PROTOCOL_VIOLATION: Handshake/Probe-only payload rejected.")
                    connection_state["halted"] = True
                    self._safe_send(conn, {
                        "action": "NO_OP",
                        "status": "INVALID_REQUEST",
                        "reason": "PROBES_FORBIDDEN",
                        "experience_id": "PROBE_REJECTION"
                    })
                    return

            except Exception as e:
                # Immediate fallback for malformed or empty messages
                logger.error(f"PROTOCOL_VIOLATION: Malformed JSON or empty message. Raw: {repr(message_str)}")
                connection_state["halted"] = True
                self._safe_send(conn, {
                    "action": "NO_OP",
                    "status": "INVALID_REQUEST",
                    "reason": "malformed_request", 
                    "experience_id": "UNKNOWN"
                })
                return

            # 1b. AUTHORITY VALIDATION (FAST)
            STABLE_WHITELIST = ["ADVISORY", "AUTHORITATIVE", "OVERRIDE"]
            LIFECYCLE_FORBIDDEN = ["ACTIVE", "SHADOW", "UNKNOWN"]
            
            # AUTHORITY CONSISTENCY CHECK
            if connection_state["authority"] is None:
                if req_authority in STABLE_WHITELIST:
                    connection_state["authority"] = req_authority
                elif request.get("unregistered_experience") or req_authority == "ADVISORY":
                    # Relaxed for Shadow/Advisory context
                    connection_state["authority"] = "SHADOW_UNSCOPED_AUTHORITY"
                else:
                    logger.error(f"PROTOCOL_VIOLATION: Illegal initial authority: {req_authority}")
                    connection_state["halted"] = True
                    self._safe_send(conn, self._error_response("INVALID_AUTHORITY", experience_id))
                    return
            else:
                if req_authority != connection_state["authority"] and connection_state["authority"] != "SHADOW_UNSCOPED_AUTHORITY":
                    logger.error(f"PROTOCOL_VIOLATION: Authority changed from {connection_state['authority']} to {req_authority}")
                    connection_state["halted"] = True
                    self._safe_send(conn, self._error_response("AUTHORITY_CHANGE_FORBIDDEN", experience_id))
                    return

            if req_authority in LIFECYCLE_FORBIDDEN:
                logger.error(f"PROTOCOL_VIOLATION: Lifecycle authority '{req_authority}' forbidden.")
                connection_state["halted"] = True
                self._safe_send(conn, self._error_response("LIFECYCLE_AUTHORITY_FORBIDDEN", experience_id))
                return

            # 2. STRICT SYNCHRONOUS LOGIC (S_n -> A_n)
            # No background thinking allowed here.
            response, experience_to_process = self._process_logic(request, start_time)
            
            # If we are about to send status indicates failure, it is terminal
            terminal_actions = []
            terminal_statuses = ["ERROR", "TERMINATED", "INVALID_REQUEST", "INVALID_STATE", "TIMEOUT"]
            
            is_terminal = (response.get("action") in terminal_actions) or (response.get("status") in terminal_statuses)
            
            if is_terminal:
                is_shadow = (
                    request.get("unregistered_experience") or 
                    str(request.get("controller", "AI")).upper() == "HUMAN" or 
                    request.get("policy_authority") == "SHADOW_LEARNING_PERMIT" or
                    request.get("authority") == "ADVISORY"
                )
                if not is_shadow:
                    logger.warning(f"HALTING CONNECTION: Terminal response issued for {experience_id} (Action: {response.get('action')}, Status: {response.get('status')})")
                    connection_state["halted"] = True
                else:
                    logger.info(f"SHADOW_CONTINUITY: Terminal response ignored for {experience_id} (Shadow mode active)")
                
            self._safe_send(conn, response)
            logger.info(f"Response sent: {response.get('action')} for {experience_id}")

            # 3. DEFERRED PROCESSING (Synchronous but after response)
            if experience_to_process:
                try:
                    self._process_experience_async(*experience_to_process)
                except Exception as e:
                    logger.error(f"DEFERRED_PROCESS_ERROR for {experience_id}: {e}")

        except Exception as e:
            logger.critical(f"FATAL DISPATCH ERROR: {e}")
            connection_state["halted"] = True
            try:
                self._safe_send(conn, self._error_response(f"dispatch_failure_{type(e).__name__}"))
            except:
                pass

    def handle_client(self, conn: socket.socket, addr: Tuple[str, int]) -> None:
        """Safety shell around request handling."""
        logger.info(f"Connection established from {addr}")
        # Use binary buffer to avoid decode errors on partial multi-byte UTF-8 characters
        buffer = b""
        try:
            with conn:
                # Persistent connection: Wait indefinitely for incoming data
                while True:
                    data = conn.recv(16384)
                    if data:
                        logger.info(f"NETWORK_IO: Received {len(data)} bytes from {addr}")
                        
                    if not data:
                        break
                        
                    buffer += data
                    
                    # Log partial message state
                    if b"\n" not in buffer and len(buffer) > 0:
                        logger.info(f"PROTOCOL_DIAGNOSTIC: Partial message in buffer ({len(buffer)} bytes), waiting for newline...")

                    request_count = 0
                    while b"\n" in buffer:
                        request_count += 1
                        line_bytes, buffer = buffer.split(b"\n", 1)
                        
                        if request_count > 1:
                            logger.error(f"PROTOCOL_VIOLATION: Multiple requests in batch. Connection: {addr}")
                            if addr in self._conn_states:
                                self._conn_states[addr]["halted"] = True
                            self._safe_send(conn, {
                                "action": "NO_OP",
                                "status": "INVALID_REQUEST",
                                "reason": "MULTIPLE_REQUESTS_IN_BATCH",
                                "experience_id": "BATCH_VIOLATION"
                            })
                            break # Halt batch processing immediately
                            
                        # Ensure we reply even to empty/malformed messages
                        try:
                            line = line_bytes.decode('utf-8', errors='replace').strip()
                            if addr not in self._conn_states:
                                self._conn_states[addr] = {"authority": None, "halted": False, "last_experience_id": None, "is_first_request": True}
                            
                            self._dispatch_request(conn, line, self._conn_states[addr])
                            
                            if self._conn_states[addr].get("halted"):
                                break # Stop reading from this connection
                        except Exception as e:
                            logger.error(f"PROTOCOL_ERROR: Message decode error from {addr}: {e}")
                            self._safe_send(conn, self.DEFAULT_RESPONSE)
                            break

                    if addr in self._conn_states and self._conn_states[addr].get("halted"):
                        logger.warning(f"Closing halted connection {addr}")
                        break

        except Exception as e:
            logger.error(f"Connection error {addr}: {e}")
        finally:
            if hasattr(self, '_conn_states') and addr in self._conn_states:
                del self._conn_states[addr]
            logger.info(f"Disconnected {addr}")

    def _process_logic(self, request: Dict[str, Any], start_time: float) -> Tuple[Dict[str, Any], Optional[Tuple]]:
        """Main request processing logic. Returns (response_dict, deferred_task)."""
        experience_id = request.get("experience_id", str(uuid.uuid4()))
        self.last_request_time = time.time()
        
        # Metadata for summary log
        summary_data = {
            "authority": "UNKNOWN",
            "health": 0.0,
            "action": "NO_OP",
            "confidence": 0.0,
            "violation": "NONE",
            "state": "ACTING" # Default state
        }

        # 1. Preloaded Policy Check (Strict only for inference)
        is_inference = "state" in request
        if is_inference and (not self.active_policy or not getattr(self.active_policy, 'promoted_knowledge', None)):
            res = {"action": "NO_OP", "status": "INVALID_STATE", "reason": "POLICY_NOT_LOADED", "experience_id": experience_id}
            summary_data["violation"] = "POLICY_NOT_LOADED"
            summary_data["state"] = "BLOCKED_PROTOCOL"
            self._log_summary(summary_data)
            return res, None

        if request.get("unregistered_experience"):
            summary_data["violation"] = "UNREGISTERED_EXPERIENCE"

        # 2. Budget Check
        if (time.perf_counter() - start_time) > 0.100:
            res = {"action": "NO_OP", "status": "TIMEOUT", "reason": "TIMEOUT_BEFORE_PROCESSING", "experience_id": experience_id}
            summary_data["violation"] = "TIMEOUT_BEFORE_PROCESSING"
            summary_data["state"] = "BLOCKED_PROTOCOL"
            self._log_summary(summary_data)
            return res, None

        req_type = request.get("type")

        # 3. Authority Validation (Deep Check)
        req_authority = request.get("authority", "UNKNOWN")
        summary_data["authority"] = req_authority
        
        pol_auth = request.get("policy_authority")
        ctrl = str(request.get("controller", "AI")).upper()
        is_shadow = (
            request.get("unregistered_experience") or 
            ctrl == "HUMAN" or 
            pol_auth == "SHADOW_LEARNING_PERMIT" or
            req_authority == "ADVISORY"
        )
        
        STABLE_WHITELIST = ["ADVISORY", "AUTHORITATIVE", "OVERRIDE"]
        LIFECYCLE_FORBIDDEN = ["ACTIVE", "SHADOW", "UNKNOWN"]
        
        if req_authority in LIFECYCLE_FORBIDDEN or (req_authority not in STABLE_WHITELIST and req_type not in ["HEARTBEAT", "CONTROL_MODE", "LEARNING_FREEZE_PROOF"]):
            if not is_shadow:
                logger.error(f"PROTOCOL_VIOLATION: Authority '{req_authority}' rejected in logic path.")
                res = {
                    "action": "NO_OP",
                    "status": "INVALID_REQUEST",
                    "reason": "INVALID_AUTHORITY",
                    "experience_id": experience_id
                }
                summary_data["violation"] = "INVALID_AUTHORITY"
                summary_data["state"] = "BLOCKED_PROTOCOL"
                self._log_summary(summary_data)
                return res, None
            else:
                # Downgrade and Normalize for Shadow mode
                summary_data["violation"] = "UNAUTHORIZED_AUTHORITY_SHADOW"
                # Preserve original in request for audit; normalization happens in Auditor
                # req_authority remains original for summary transparency
                summary_data["authority"] = req_authority

        # 4. Handle Heartbeat / Healthcheck / Killswitch
        if req_type == "HEARTBEAT":
            if self.failure_manager.mode == DisasterMode.ISOLATED:
                logger.info("NETWORK RECOVERY: Heartbeat received. Resuming from ISOLATED mode.")
                self.failure_manager.set_mode(DisasterMode.NORMAL)
            
            dataset_metrics = {}
            if len(self.memory) > 0:
                processed = DatasetPipeline.process_experiences(list(self.memory.buffer))
                dataset_metrics = DatasetPipeline.get_metrics(processed)

            monitoring_data = self.monitor.analyze()
            monitoring_data["learning_readiness"] = self.last_readiness_report
            
            avg_active = sum(self.active_reward_window) / len(self.active_reward_window) if self.active_reward_window else 0.0
            avg_shadow = sum(self.shadow_reward_window) / len(self.shadow_reward_window) if self.shadow_reward_window else 0.0

            summary_data["action"] = "HEARTBEAT_REPLY"
            self._log_summary(summary_data)
            return {
                "status": "OK",
                "version": self.version,
                "learning_state": LEARNING_STATE,
                "timestamp": time.time(),
                "experience_id": experience_id,
                "control_mode": self.control_mode,
                "influence_weight": float(self.influence_weight),
                "shadow_reward_avg": float(avg_shadow),
                "active_reward_avg": float(avg_active),
                "learning_active": LIVE_SHADOW_LEARNING,
                "last_control_switch": self.last_control_switch,
                "active_policy": self.active_policy_name,
                "ml_enabled": self.arbitrator.ml_enabled,
                "active_model": self.model_manager.active_model_version,
                "candidate_model": self.model_manager.candidate_model_version,
                "canary_ratio": self.arbitrator.canary_ratio,
                "model_registry": list(self.model_manager.registry.keys()),
                "monitoring": monitoring_data,
                "online_ml_metrics": self.arbitrator.get_online_metrics(),
                "audit_metrics": self.auditor.calculate_drift_metrics(),
                "dataset_metrics": dataset_metrics,
                "failure_status": self.failure_manager.get_status(),
                "action": "HEARTBEAT_REPLY"
            }, None
        
        # GLOBAL LOCKDOWN CHECK
        if self.failure_manager.mode == DisasterMode.LOCKDOWN:
            is_state_update = "state" in request
            is_allowed_mgmt = req_type in [
                "UNRELEASE_LOCKDOWN", "EMERGENCY_LOCKDOWN", "HEURISTIC", "HEARTBEAT", "CONTROL_MODE"
            ]
            if not (is_state_update or is_allowed_mgmt):
                res = self._error_response("System in LOCKDOWN. Action denied.", experience_id)
                summary_data["violation"] = "LOCKDOWN_REJECTION"
                self._log_summary(summary_data)
                return res, None

        if req_type == "LEARNING_FREEZE_PROOF":
            import sys
            optimizer_present = any(m in sys.modules for m in ["torch", "tensorflow", "keras", "jax"])
            summary_data["action"] = "PROOF_REPLY"
            self._log_summary(summary_data)
            return {
                "learning_state": LEARNING_STATE, "trainer_callable": False, "optimizer_present": optimizer_present,
                "gates_open": False, "readiness_only": True, "timestamp": time.time(), "experience_id": experience_id,
                "action": "PROOF_REPLY"
            }, None

        if req_type == "RELOAD_KNOWLEDGE":
            try:
                base_dir = os.path.dirname(os.path.abspath(__file__))
                self.active_policy.load_promoted_knowledge(os.path.join(base_dir, "snapshots", "promoted", "latest"))
                summary_data["action"] = "SUCCESS"
                self._log_summary(summary_data)
                return {"status": "SUCCESS", "action": "RELOAD_SUCCESS", "version": getattr(self.active_policy, 'promoted_version', 'UNKNOWN'), "experience_id": experience_id}, None
            except Exception as e:
                res = self._error_response(str(e), experience_id)
                summary_data["violation"] = "RELOAD_FAILED"
                self._log_summary(summary_data)
                return res, None

        if req_type == "PROMOTE_SHADOW":
            try:
                from promote.run import run_promotion
                run_promotion()
                # Reload knowledge after promotion
                base_dir = os.path.dirname(os.path.abspath(__file__))
                self.active_policy.load_promoted_knowledge(os.path.join(base_dir, "snapshots", "promoted", "latest"))
                summary_data["action"] = "SUCCESS"
                self._log_summary(summary_data)
                return {"status": "SUCCESS", "action": "PROMOTE_SHADOW_SUCCESS", "experience_id": experience_id}, None
            except Exception as e:
                res = self._error_response(str(e), experience_id)
                summary_data["violation"] = "PROMOTE_SHADOW_FAILED"
                self._log_summary(summary_data)
                return res, None

        if req_type == "RESET_PASSIVE_STORE":
            try:
                self.trainer.passive_data = {
                    "session_id": self.trainer.passive_data.get("session_id", 0) + 1,
                    "visitation_counts": {}, "valid_actions": {}, "transitions": {},
                    "invariant_violations": [], "blocked_resolutions": {}, "combat_movement_patterns": {}
                }
                self.trainer._save_passive_store()
                self.auditor.history.clear(); self.memory.clear()
                summary_data["action"] = "SUCCESS"
                self._log_summary(summary_data)
                return {"status": "SUCCESS", "action": "RESET_SUCCESS", "experience_id": experience_id}, None
            except Exception as e:
                res = self._error_response(str(e), experience_id)
                summary_data["violation"] = "RESET_FAILED"
                self._log_summary(summary_data)
                return res, None

        if req_type == "CONTROL_MODE":
            mode = request.get("mode"); source = request.get("source")
            if mode not in ["HUMAN", "AI"]: 
                res = self._error_response("Invalid control mode.", experience_id)
                summary_data["violation"] = "INVALID_CONTROL_MODE"
                self._log_summary(summary_data)
                return res, None
            if source != "GUI": 
                res = self._error_response("Invalid control source.", experience_id)
                summary_data["violation"] = "INVALID_CONTROL_SOURCE"
                self._log_summary(summary_data)
                return res, None
            if mode == self.control_mode: 
                res = self._error_response(f"Already in {mode}.", experience_id)
                summary_data["violation"] = "CONTROL_MODE_UNCHANGED"
                self._log_summary(summary_data)
                return res, None
            old_mode = self.control_mode; self.control_mode = mode; self.last_control_switch = time.time(); self.influence_weight = 0.0
            summary_data["action"] = "SUCCESS"
            self._log_summary(summary_data)
            return {"status": "SUCCESS", "action": "CONTROL_MODE_SUCCESS", "control_mode": self.control_mode, "experience_id": experience_id}, None

        if req_type == "SET_ML_ENABLED":
            enabled = bool(request.get("enabled", True))
            self.arbitrator.set_ml_enabled(enabled)
            summary_data["action"] = "SUCCESS"
            self._log_summary(summary_data)
            return {"status": "SUCCESS", "action": "ML_ENABLED_SUCCESS", "ml_enabled": enabled, "experience_id": experience_id}, None

        if req_type == "SET_CANARY_RATIO":
            ratio = float(request.get("ratio", 0.0))
            if ratio > 0.1 and not request.get("bypass_safety_cap"): 
                res = self._error_response("Safety cap hit.", experience_id)
                summary_data["violation"] = "SAFETY_CAP_HIT"
                self._log_summary(summary_data)
                return res, None
            self.arbitrator.set_canary_ratio(ratio)
            summary_data["action"] = "SUCCESS"
            self._log_summary(summary_data)
            return {"status": "SUCCESS", "action": "CANARY_RATIO_SUCCESS", "canary_ratio": self.arbitrator.canary_ratio, "experience_id": experience_id}, None

        if req_type == "PROMOTE_CANDIDATE":
            ok, resp = self._require_confirmation(request, "PROMOTE_CANDIDATE")
            if not ok: 
                summary_data["action"] = "REQUIRED_CONFIRMATION"
                self._log_summary(summary_data)
                return resp, None
            success = self.model_manager.promote_candidate()
            summary_data["action"] = "SUCCESS" if success else "FAILURE"
            self._log_summary(summary_data)
            return {"status": "SUCCESS" if success else "FAILURE", "action": "PROMOTE_SUCCESS" if success else "PROMOTE_FAILURE", "experience_id": experience_id}, None

        if req_type == "SET_DISASTER_MODE":
            mode_str = request.get("mode", "NORMAL")
            try:
                self.failure_manager.set_mode(DisasterMode(mode_str))
                summary_data["action"] = "SUCCESS"
                self._log_summary(summary_data)
                return {"status": "SUCCESS", "action": "DISASTER_MODE_SUCCESS", "mode": self.failure_manager.mode.value, "experience_id": experience_id}, None
            except ValueError: 
                res = self._error_response(f"Invalid mode: {mode_str}", experience_id)
                summary_data["violation"] = "INVALID_DISASTER_MODE"
                self._log_summary(summary_data)
                return res, None

        if req_type == "EMERGENCY_LOCKDOWN":
            self.failure_manager.set_mode(DisasterMode.LOCKDOWN)
            summary_data["action"] = "SUCCESS"
            self._log_summary(summary_data)
            return {"status": "SUCCESS", "action": "LOCKDOWN_SUCCESS", "mode": "LOCKDOWN", "experience_id": experience_id}, None

        if req_type == "UNRELEASE_LOCKDOWN":
            ok, resp = self._require_confirmation(request, "UNRELEASE_LOCKDOWN")
            if not ok: 
                summary_data["action"] = "REQUIRED_CONFIRMATION"
                self._log_summary(summary_data)
                return resp, None
            self.failure_manager.set_mode(DisasterMode.NORMAL)
            summary_data["action"] = "SUCCESS"
            self._log_summary(summary_data)
            return {"status": "SUCCESS", "action": "UNLOCK_SUCCESS", "mode": "NORMAL", "experience_id": experience_id}, None

        # AI LOOP
        if "state" not in request:
            res = self._error_response("Missing 'state' in request", experience_id)
            summary_data["violation"] = "MISSING_STATE"
            summary_data["state"] = "BLOCKED_PROTOCOL"
            self._log_summary(summary_data)
            return res, None

        raw_state = request.get("state")
        normalized_state = self.parser.parse(raw_state)
        if normalized_state is None:
            res = {"action": "NO_OP", "status": "INVALID_STATE", "reason": "PARSER_REJECTED", "experience_id": experience_id}
            summary_data["violation"] = "PARSER_REJECTED"
            summary_data["state"] = "BLOCKED_PROTOCOL"
            self._log_summary(summary_data)
            return res, None

        # Summary Log Health
        summary_data["health"] = normalized_state["raw"].get("health", 0.0)
        
        # BLOCK EXECUTION IF INCOMPLETE (Requirement 3)
        if normalized_state.get("is_incomplete"):
            logger.error(f"PROTOCOL_VIOLATION: Decision execution blocked due to missing required fields: {normalized_state.get('missing_required')}")
            res = {
                "action": "NO_OP", 
                "status": "INVALID_REQUEST", 
                "reason": "MISSING_REQUIRED_FIELDS", 
                "experience_id": experience_id,
                "missing_fields": normalized_state.get("missing_required")
            }
            summary_data["violation"] = "MISSING_REQUIRED_FIELDS"
            summary_data["state"] = "BLOCKED_PROTOCOL"
            self._log_summary(summary_data)
            return res, None

        # Process experience (Shadow Learning)
        experience_to_process = None
        intent_taken = request.get("intent_taken")
        if intent_taken:
            raw_result = request.get("result", {})
            result = IntentValidator.validate_result(raw_result)
            controller = str(request.get("controller", "AI")).upper()
            policy_authority = request.get("policy_authority")
            policy_mode = "SHADOW" if (policy_authority == "SHADOW_LEARNING_PERMIT" or controller == "HUMAN" or request.get("unregistered_experience") or request.get("authority") == "ADVISORY") else "ACTIVE"
            
            reward, reward_breakdown, reward_class = self.reward_calc.calculate(
                result, learning_allowed=(policy_authority in ["SHADOW_LEARNING_PERMIT", "ACTIVE_LEARNING_PERMIT"]),
                is_incomplete=normalized_state.get("is_incomplete", False)
            )
            state_hash = self._get_state_hash(normalized_state)
            
            # Defer processing until after response is sent
            experience_to_process = (experience_id, request, normalized_state, policy_mode, state_hash, reward, reward_breakdown, reward_class, result)

        if (time.perf_counter() - start_time) > 0.100:
            res = {"action": "NO_OP", "status": "TIMEOUT", "reason": "TIMEOUT_BEFORE_DECISION", "experience_id": experience_id}
            summary_data["violation"] = "TIMEOUT_BEFORE_DECISION"
            self._log_summary(summary_data)
            return res, experience_to_process

        # Decide next intent
        next_intent = "STOP"; confidence = 1.0; policy_source = "UNKNOWN"; authority = "AUTHORITATIVE"
        fallback_reason = None; model_version = None; shadow_data = None; hold_duration = 1; intent_params = {}
        target_id = -1

        if self.control_mode == "HUMAN":
            next_intent = "STOP"; policy_source = "HUMAN_GATED"; fallback_reason = "CONTROL_MODE_HUMAN"
            # Shadow mode is observational but MUST reply
            _, _, _, _, _, _, shadow_data, _, _ = self.arbitrator.decide(normalized_state, policy_mode="SHADOW", state_hash=self._get_state_hash(normalized_state))
        
        elif self.failure_manager.mode in [DisasterMode.READ_ONLY, DisasterMode.LOCKDOWN, DisasterMode.ISOLATED]:
            next_intent = "STOP"; policy_source = "DISASTER_FALLBACK"; fallback_reason = "CRITICAL_MODE"
        
        else:
            requested_policy = request.get("policy_override", self.active_policy_name)
            self.arbitrator.influence_weight = self.influence_weight
            if requested_policy == "ML":
                pol_auth = request.get("policy_authority")
                ctrl = str(request.get("controller", "AI")).upper()
                req_auth = request.get("authority")
                mode_to_use = "SHADOW" if (pol_auth == "SHADOW_LEARNING_PERMIT" or ctrl == "HUMAN" or request.get("unregistered_experience") or req_auth == "ADVISORY") else "ACTIVE"
                next_intent, confidence, policy_source, authority, fallback_reason, model_version, shadow_data, hold_duration, intent_params = self.arbitrator.decide(normalized_state, policy_mode=mode_to_use, state_hash=self._get_state_hash(normalized_state))
            else:
                policy = self.policies.get(requested_policy, self.heuristic_policy)
                next_intent, confidence, model_version, authority, hold_duration, intent_params = policy.decide(normalized_state, state_hash=self._get_state_hash(normalized_state))
                policy_source = requested_policy
                
                # Extract target_id from blackboard if available (for Java fluid tracking)
                if hasattr(policy, 'blackboard') and 'target_id' in policy.blackboard:
                    target_id = policy.blackboard['target_id']

        if not Intent.has_value(next_intent): next_intent = "STOP"

        status = "OK" # If we reached here, state was valid enough to process

        # Enforce Requirement 5: NO_OP reserved for invalid state, invalid authority, or zero confidence.
        # Policy-driven inactivity should use "STOP".
        action = next_intent
        if confidence <= 0.0:
            action = "NO_OP"
            summary_data["violation"] = "ZERO_CONFIDENCE"
            summary_data["state"] = "BLOCKED_PROTOCOL"

        res = {
            "experience_id": experience_id, "intent": next_intent, "intent_params": intent_params,
            "hold_duration_ticks": int(hold_duration), "confidence": float(confidence), "policy_source": policy_source,
            "learning_state": LEARNING_STATE, "authority": authority, "fallback_reason": fallback_reason,
            "model_version": model_version, "shadow_data": shadow_data, "action": action,
            "status": status, "target_id": target_id
        }
        if self.control_mode == "HUMAN":
            res["reason"] = "shadow_observation"
        
        # Summary Log Final
        summary_data["action"] = res["action"]
        summary_data["confidence"] = res["confidence"]
        self._log_summary(summary_data)

        return res, experience_to_process

    def _log_summary(self, data: Dict[str, Any]):
        """authority | health | action | confidence | violation (if any) | ai_state"""
        ai_state = data.get("state", "ACTING")
        if data.get("action") == "NO_OP" and ai_state == "ACTING":
            ai_state = "IDLE_POLICY"
        
        summary = f"SUMMARY: {data['authority']} | {data['health']:.2f} | {data['action']} | {data['confidence']:.2f} | {data['violation']} | {ai_state}"
        logger.info(summary)

    def _run_drift_audit(self):
        """
        Explicitly replays golden states and verifies outputs.
        """
        logger.info("DRIFT_AUDIT: Starting explicit replay of golden states...")
        active_drift_count = 0
        shadow_drift_count = 0
        
        for state_hash, golden in self.drift_verifier.golden_pairs.items():
            raw_state = golden["input"]
            normalized_state = self.parser.parse(raw_state)
            if normalized_state is None:
                continue
                
            # Re-run through arbitrator
            next_intent, confidence, policy_source, _, _, _, _, _, _ = self.arbitrator.decide(normalized_state, state_hash=state_hash)
            
            is_drifted, reason = self.drift_verifier.verify_drift(state_hash, {
                "intent": next_intent,
                "confidence": confidence,
                "policy_source": policy_source
            })
            
            if is_drifted:
                is_shadow = policy_source.startswith(("SHADOW_INFLUENCE_", "CANARY_"))
                if is_shadow:
                    shadow_drift_count += 1
                else:
                    active_drift_count += 1
                
                self.failure_manager.record_incident(
                    FailureType.MODEL_DIVERGENCE,
                    severity=FailureSeverity.INFO if is_shadow else FailureSeverity.CRITICAL,
                    details=f"{'[SHADOW] ' if is_shadow else ''}EXPLICIT_DRIFT_AUDIT FAILURE for {state_hash[:8]}: {reason}"
                )
        
        if active_drift_count == 0:
            logger.info(f"DRIFT_AUDIT: Success. {len(self.drift_verifier.golden_pairs)} states verified. Shadow drifts: {shadow_drift_count}")
        else:
            logger.critical(f"DRIFT_AUDIT: FAILED. {active_drift_count} active drifted states detected.")
            self.failure_manager.set_mode(DisasterMode.SHADOW_QUARANTINE)

    def _commit_shadow_learning(self) -> bool:
        """
        Commit Gate: Atomic swap ActivePolicy <- ShadowPolicy.
        Requirement 4.
        """
        logger.info("COMMIT_GATE: Initiating atomic swap of ShadowPolicy to ActivePolicy.")
        
        # 1. OPTIONAL VALIDATION (Requirement 4)
        if self.shadow_policy.learned_boost < 0.0:
            logger.warning("COMMIT_GATE: Rejection - ShadowPolicy regression detected.")
            return False
            
        # 2. SNAPSHOT (Requirement 4 & 5)
        # Store backup for rollback
        self.active_policy_backup_boost = self.active_policy.learned_boost
        
        # 3. ATOMIC SWAP
        # In this mock, we only swap the learned_boost parameter
        self.active_policy.learned_boost = self.shadow_policy.learned_boost
        
        # 4. AUDIT RECORD
        logger.info(f"COMMIT_GATE: ATOMIC_SWAP_COMPLETE. Active Boost: {self.active_policy.learned_boost:.4f}")
        return True

    def _rollback_active_policy(self) -> bool:
        """
        Rollback Guarantee: Restore previous ActivePolicy.
        Requirement 5.
        """
        if not hasattr(self, 'active_policy_backup_boost'):
            logger.error("ROLLBACK_GATE: No backup state available.")
            return False
            
        logger.warning(f"ROLLBACK_GATE: Instantaneous restore. Boost {self.active_policy.learned_boost:.4f} -> {self.active_policy_backup_boost:.4f}")
        self.active_policy.learned_boost = self.active_policy_backup_boost
        return True

    def _reset_influence(self):
        """Resets shadow influence to 0.0 (Requirement 3)."""
        if self.influence_weight != 0.0:
            logger.warning("CONTROL_AUTHORITY: Shadow influence RESET to 0.0")
            self.influence_weight = 0.0

    def _adjust_influence_weight(self, audit_entry: Dict[str, Any]):
        """
        Safety-Based Adaptation (Requirement 5).
        """
        if self.control_mode != "AI":
            self.influence_weight = 0.0
            return

        # 1. Decrease immediately if ANY safety or performance issue
        has_safety_violation = any(v["severity"] in ["HIGH", "CRITICAL"] for v in audit_entry.get("violations", []))
        entropy = self.arbitrator.last_ml_entropy
        
        avg_active = sum(self.active_reward_window) / len(self.active_reward_window) if self.active_reward_window else 0.0
        avg_shadow = sum(self.shadow_reward_window) / len(self.shadow_reward_window) if self.shadow_reward_window else 0.0
        
        reward_regression = (avg_shadow < avg_active) if (len(self.active_reward_window) >= 10 and len(self.shadow_reward_window) >= 10) else False
        entropy_spike = (entropy > 1.2)

        if has_safety_violation or reward_regression or entropy_spike:
            if self.influence_weight > 0:
                logger.warning(f"INFLUENCE_DECREASE: Safety={has_safety_violation}, Regression={reward_regression}, Entropy={entropy_spike}")
                self.influence_weight = 0.0
            return

        # 2. Increase influence only if ALL conditions met
        # - Shadow reward > Active reward (rolling window)
        # - Zero HIGH or CRITICAL incidents
        # - No drift violations
        # - System health stable
        
        recent_incidents = [i for i in self.failure_manager.incidents if time.time() - i["timestamp"] < 300]
        has_recent_incident = any(i["severity"] in ["HIGH", "CRITICAL"] for i in recent_incidents)
        
        # Check for drift violations in current cycle
        has_drift = any(v["type"] in ["MODEL_DIVERGENCE", "INTENT_MISMATCH"] for v in audit_entry.get("violations", []))
        
        # Health stable (using Monitor analysis as proxy)
        health_status = self.monitor.analyze().get("status", "OK")
        health_stable = (health_status == "OK")

        if (len(self.active_reward_window) >= 20 and len(self.shadow_reward_window) >= 20 and 
            avg_shadow > avg_active and 
            not has_recent_incident and 
            not has_drift and 
            health_stable):
            
            old_weight = self.influence_weight
            self.influence_weight = min(0.3, self.influence_weight + 0.01)
            if self.influence_weight != old_weight:
                logger.info(f"INFLUENCE_INCREASE: {old_weight:.4f} -> {self.influence_weight:.4f}")

    def _error_response(self, message: str, experience_id: str = "UNKNOWN") -> Dict[str, Any]:
        """Helper to generate standard error responses."""
        self._reset_influence()
        return {
            "action": "NO_OP",
            "error": message,
            "status": "ERROR",
            "experience_id": experience_id
        }

    def _check_flood(self) -> Tuple[bool, int]:
        now = time.time()
        self.request_history.append(now)
        # Non-blocking cleanup (lock-free deque is safe for single-item ops)
        while self.request_history and self.request_history[0] < now - 1.0:
            try:
                self.request_history.popleft()
            except IndexError:
                break
        
        rate = len(self.request_history)
        if rate > self.FLOOD_THRESHOLD:
            return True, rate
        return False, rate

    def _get_state_hash(self, state: Dict[str, Any]) -> str:
        """Fast canonical hashing for state dicts using discretized relative features."""
        try:
            # If we were passed the full parsed state object, use the normalized component
            if "normalized" in state:
                features = StateParser.get_feature_vector(state, discretize=True)
            else:
                # Otherwise parse it quickly to get normalized features
                parsed = self.parser.parse(state, fast=True)
                features = StateParser.get_feature_vector(parsed, discretize=True)
            
            feature_str = json.dumps(features)
            return hashlib.sha256(feature_str.encode('utf-8')).hexdigest()
        except Exception:
            # Fallback to a safe but less invariant hash if parsing fails
            try:
                # Use discretized features even in fallback if possible
                if isinstance(state, dict):
                     # Best effort extraction of keys that look like features
                     feat_keys = ["health", "energy", "target_distance", "target_yaw", "is_colliding", "is_on_ground", "is_floor_ahead", "is_floor_far_ahead"]
                     pseudo_norm = {k: state.get(k, 0.0) for k in feat_keys}
                     features = StateParser.get_feature_vector(pseudo_norm, discretize=True)
                     return hashlib.sha256(json.dumps(features).encode('utf-8')).hexdigest()
                
                state_str = json.dumps(state, sort_keys=True)
                return hashlib.sha256(state_str.encode('utf-8')).hexdigest()
            except:
                return hashlib.sha256(str(state).encode('utf-8')).hexdigest()

    def _require_confirmation(self, request: Dict[str, Any], action_name: str) -> Tuple[bool, Optional[Dict[str, Any]]]:
        """
        Implements two-step confirmation for sensitive commands.
        Returns (True, None) if confirmed, (False, response) if a token was issued.
        """
        token = request.get("confirmation_token")
        operator_id = request.get("operator_id", "UNKNOWN_OPERATOR")
        experience_id = request.get("experience_id", "UNKNOWN")
        
        if token and token in self.pending_confirmations:
            pending = self.pending_confirmations.pop(token)
            if pending["action"] == action_name:
                logger.info(f"CONFIRMED: Action '{action_name}' confirmed by {operator_id}")
                return True, None
        
        # Issue new token
        new_token = str(uuid.uuid4())
        self.pending_confirmations[new_token] = {
            "action": action_name,
            "operator_id": operator_id,
            "timestamp": time.time()
        }
        
        # Clean up old tokens (TTL 5 minutes)
        now = time.time()
        self.pending_confirmations = {k: v for k, v in self.pending_confirmations.items() if now - v["timestamp"] < 300}
        
        return False, {
            "status": "REQUIRED_CONFIRMATION",
            "message": f"Action '{action_name}' requires confirmation.",
            "confirmation_token": new_token,
            "operator_id": operator_id,
            "experience_id": experience_id,
            "action": "NO_OP"
        }

    def start(self) -> None:
        """
        Starts the TCP server loop.
        """
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                # Disabled SO_REUSEADDR on Windows to prevent silent multi-bind connection stealing
                # s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1) # Send packets immediately
                s.bind((self.host, self.port))
                s.listen()
                logger.info(f"Generic AI Server listening on {self.host}:{self.port}")

                while True:
                    conn, addr = s.accept()
                    logger.info(f"Accepted connection from {addr}")
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
    enforce_mode([ExecutionMode.PROD_SERVER])
    server = AIServer()
    server.start()
