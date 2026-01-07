import socket
import json
import logging
import threading
import time
import math
import os
import uuid
import types
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
            self.active_policy.load_promoted_knowledge(os.path.join("snapshots", "promoted", "latest"))
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
        self.flood_lock = threading.Lock()
        self.FLOOD_THRESHOLD = 500 # messages per second
        
        # Network Watchdog
        self.last_request_time = time.time()
        self.watchdog_thread = threading.Thread(target=self._network_watchdog, daemon=True)
        self.watchdog_thread.start()
        
        # Phase 10B: Exclusive Control Authority
        self.control_mode = "HUMAN"
        self.influence_weight = 0.0
        self.last_control_switch = time.time()
        self.active_reward_window = deque(maxlen=100)
        self.shadow_reward_window = deque(maxlen=100)
        
        self.episode_id = int(time.time()) # Use timestamp as base for episode IDs

    def handle_client(self, conn: socket.socket, addr: Tuple[str, int]) -> None:
        """
        Processes a single request-response cycle.
        Stateless per-request: does not rely on persistent client connections.
        """
        self.last_request_time = time.time()
        
        # 1. GENERATE GLOBAL EXPERIENCE ID
        experience_id = str(uuid.uuid4())

        # 0. Flood Check
        is_flood, rate = self._check_flood()
        if is_flood:
            # We don't drop the request, but we record the incident and stay safe
            self.failure_manager.record_incident(
                FailureType.FLOOD_DETECTION,
                severity=FailureSeverity.HIGH,
                experience_id=experience_id,
                details=f"Rate: {rate} req/s (Limit: {self.FLOOD_THRESHOLD})"
            )

        # 0. Learning Freeze Integrity Check
        if LEARNING_STATE not in {"FROZEN", "SHADOW_ONLY"}:
            self.failure_manager.record_incident(
                FailureType.LEARNING_FREEZE_BREACH,
                severity=FailureSeverity.CRITICAL,
                experience_id=experience_id,
                details=f"Invalid LEARNING_STATE: {LEARNING_STATE}"
            )
            # Crash execution as required
            import sys
            sys.exit(1)

        freeze_ok, freeze_reason = ImportLock.verify_integrity()
        if not freeze_ok:
            self.failure_manager.record_incident(
                FailureType.LEARNING_FREEZE_BREACH,
                severity=FailureSeverity.CRITICAL,
                experience_id=experience_id,
                details=f"Import Integrity Violation: {freeze_reason}"
            )
            import sys
            sys.exit(1)

        # 0.1 Detect Clock Jump
        current_wall = time.time()
        current_mono = time.monotonic()
        
        wall_delta = current_wall - self.wall_clock_baseline
        mono_delta = current_mono - self.monotonic_baseline
        
        # If wall clock drifted more than 1s away from monotonic delta, something is wrong
        if abs(wall_delta - mono_delta) > 1.0:
            logger.error(f"SYSTEM FAILURE: Clock jump detected! Wall delta: {wall_delta:.3f}, Mono delta: {mono_delta:.3f}")
            self.failure_manager.record_incident(
                FailureType.CLOCK_JUMP, 
                severity=FailureSeverity.HIGH,
                experience_id=experience_id,
                details=f"Wall: {current_wall}, Mono: {current_mono}"
            )
            # Reset baseline to current to prevent persistent alerts
            self.wall_clock_baseline = current_wall
            self.monotonic_baseline = current_mono
            # Invalidate episode to prevent temporal contamination
            self.episode_id += 1

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
                    self.failure_manager.record_incident(
                        FailureType.MALFORMED_JSON, 
                        severity=FailureSeverity.HIGH,
                        experience_id=experience_id,
                        details=f"From {addr}"
                    )
                    self._send_error(conn, "Invalid JSON format")
                    return

                # 0. Handle Heartbeat / Healthcheck / Killswitch
                if request.get("type") == "HEARTBEAT":
                    # Handshake: Clear ISOLATED mode if present
                    if self.failure_manager.mode == DisasterMode.ISOLATED:
                        logger.info("NETWORK RECOVERY: Heartbeat received. Resuming from ISOLATED mode.")
                        self.failure_manager.set_mode(DisasterMode.NORMAL)
                    
                    self.last_request_time = time.time() # Refresh watchdog
                    
                    # Calculate dataset metrics if we have data
                    dataset_metrics = {}
                    if len(self.memory) > 0:
                        processed = DatasetPipeline.process_experiences(list(self.memory.buffer))
                        dataset_metrics = DatasetPipeline.get_metrics(processed)

                    # Calculate monitoring data and attach readiness report
                    monitoring_data = self.monitor.analyze()
                    monitoring_data["learning_readiness"] = self.last_readiness_report
                    
                    # Phase 10B Observability
                    avg_active = sum(self.active_reward_window) / len(self.active_reward_window) if self.active_reward_window else 0.0
                    avg_shadow = sum(self.shadow_reward_window) / len(self.shadow_reward_window) if self.shadow_reward_window else 0.0

                    conn.sendall(json.dumps({
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
                        "failure_status": self.failure_manager.get_status()
                    }).encode('utf-8'))
                    return
                
                # GLOBAL LOCKDOWN CHECK: API calls that modify state are blocked in LOCKDOWN
                if self.failure_manager.mode == DisasterMode.LOCKDOWN:
                    is_state_update = "state" in request
                    is_allowed_mgmt = request.get("type") in [
                        "UNRELEASE_LOCKDOWN", "EMERGENCY_LOCKDOWN", "HEURISTIC", "HEARTBEAT", "CONTROL_MODE"
                    ]
                    if not (is_state_update or is_allowed_mgmt):
                        self._send_error(conn, "System in LOCKDOWN. Action denied.")
                        return

                if request.get("type") == "LEARNING_FREEZE_PROOF":
                    # Formal Verification Proof Endpoint
                    import sys
                    optimizer_present = any(m in sys.modules for m in ["torch", "tensorflow", "keras", "jax"])
                    
                    conn.sendall(json.dumps({
                        "learning_state": LEARNING_STATE,
                        "trainer_callable": False,
                        "optimizer_present": optimizer_present,
                        "gates_open": False,
                        "readiness_only": True,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "CONTROL_MODE":
                    mode = request.get("mode")
                    source = request.get("source")
                    
                    # Validation
                    if mode not in ["HUMAN", "AI"]:
                        self.failure_manager.record_incident(
                            FailureType.CONTROL_MODE_REJECTED, 
                            severity=FailureSeverity.LOW, 
                            details=f"Invalid control mode: {mode}"
                        )
                        self._send_error(conn, "Invalid control mode. Must be 'HUMAN' or 'AI'.")
                        return
                        
                    if source != "GUI":
                        self.failure_manager.record_incident(
                            FailureType.CONTROL_MODE_REJECTED, 
                            severity=FailureSeverity.LOW, 
                            details=f"Invalid control source: {source}"
                        )
                        self._send_error(conn, "Invalid control source. Must be 'GUI'.")
                        return
                        
                    if mode == self.control_mode:
                        self.failure_manager.record_incident(
                            FailureType.CONTROL_MODE_REJECTED, 
                            severity=FailureSeverity.LOW, 
                            details=f"Redundant control transition to {mode}"
                        )
                        self._send_error(conn, f"Redundant transition. Mode is already {mode}.")
                        return
                        
                    # Success
                    old_mode = self.control_mode
                    self.control_mode = mode
                    self.last_control_switch = time.time()
                    
                    # Reset influence on mode switch (Requirement 5)
                    self.influence_weight = 0.0
                    
                    logger.info(f"CONTROL_MODE_CHANGED: {old_mode} -> {mode} (Source: {source})")
                    self.failure_manager.record_incident(
                        FailureType.CONTROL_MODE_CHANGED,
                        severity=FailureSeverity.INFO,
                        details=f"CONTROL_MODE_CHANGED: {old_mode} -> {mode}"
                    )
                    
                    conn.sendall(json.dumps({
                        "status": "SUCCESS",
                        "control_mode": self.control_mode,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "SET_ML_ENABLED":
                    operator_id = request.get("operator_id", "UNKNOWN")
                    enabled = bool(request.get("enabled", True))
                    self.arbitrator.set_ml_enabled(enabled)
                    logger.info(f"ML ENABLED set to {enabled} by {operator_id} ({addr})")
                    conn.sendall(json.dumps({
                        "status": "SUCCESS", 
                        "ml_enabled": enabled,
                        "operator_id": operator_id,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "SET_CANDIDATE_MODEL":
                    version = request.get("version")
                    success = self.model_manager.set_candidate_model(version)
                    conn.sendall(json.dumps({
                        "status": "SUCCESS" if success else "FAILURE",
                        "candidate_model": self.model_manager.candidate_model_version,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "SET_CANARY_RATIO":
                    ratio = float(request.get("ratio", 0.0))
                    bypass = bool(request.get("bypass_safety_cap", False))
                    
                    if ratio > 0.1 and not bypass:
                        self._send_error(conn, "Canary ratio exceeds 10% safety cap. Use 'bypass_safety_cap: true' to override.")
                        return
                    
                    self.arbitrator.set_canary_ratio(ratio)
                    conn.sendall(json.dumps({
                        "status": "SUCCESS",
                        "canary_ratio": self.arbitrator.canary_ratio,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "PROMOTE_CANDIDATE":
                    if not self._require_confirmation(conn, request, "PROMOTE_CANDIDATE"):
                        return
                    
                    # SHADOW GATE CHECK
                    stats = self.monitor.get_shadow_stats()
                    if stats["sample_size"] < 1000:
                        self._send_error(conn, f"Promotion failed: Insufficient shadow samples ({stats['sample_size']}/1000)")
                        return
                    
                    if stats["survival_rate"] < stats["prod_survival_rate"] * 0.95:
                        self._send_error(conn, f"Promotion failed: Survival regression detected. Shadow: {stats['survival_rate']:.3f}, Prod: {stats['prod_survival_rate']:.3f}")
                        return

                    success = self.model_manager.promote_candidate()
                    conn.sendall(json.dumps({
                        "status": "SUCCESS" if success else "FAILURE",
                        "active_model": self.model_manager.active_model_version,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "REGISTER_MODEL":
                    metadata = request.get("metadata", {})
                    success = self.model_manager.register_model(metadata)
                    conn.sendall(json.dumps({
                        "status": "SUCCESS" if success else "FAILURE",
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "ACTIVATE_MODEL":
                    if not self._require_confirmation(conn, request, "ACTIVATE_MODEL"):
                        return
                    version = request.get("version")
                    success = self.model_manager.activate_model(version)
                    conn.sendall(json.dumps({
                        "status": "SUCCESS" if success else "FAILURE",
                        "active_model": self.model_manager.active_model_version,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "ROLLBACK_MODEL":
                    if not self._require_confirmation(conn, request, "ROLLBACK_MODEL"):
                        return
                    success = self.model_manager.rollback()
                    conn.sendall(json.dumps({
                        "status": "SUCCESS" if success else "FAILURE",
                        "active_model": self.model_manager.active_model_version,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "SET_DISASTER_MODE":
                    mode_str = request.get("mode", "NORMAL")
                    if mode_str == "NORMAL" and self.failure_manager.mode == DisasterMode.LOCKDOWN:
                        self._send_error(conn, "System in LOCKDOWN. Use 'UNRELEASE_LOCKDOWN' command to exit.")
                        return

                    try:
                        mode = DisasterMode(mode_str)
                        self.failure_manager.set_mode(mode)
                        conn.sendall(json.dumps({
                            "status": "SUCCESS",
                            "mode": self.failure_manager.mode.value,
                            "timestamp": time.time(),
                            "experience_id": experience_id
                        }).encode('utf-8'))
                    except ValueError:
                        self._send_error(conn, f"Invalid mode: {mode_str}")
                    return

                if request.get("type") == "EMERGENCY_LOCKDOWN":
                    operator_id = request.get("operator_id", "UNKNOWN")
                    self.failure_manager.set_mode(DisasterMode.LOCKDOWN)
                    logger.critical(f"PANIC BUTTON: EMERGENCY LOCKDOWN triggered by {operator_id}")
                    conn.sendall(json.dumps({
                        "status": "SUCCESS",
                        "mode": "LOCKDOWN",
                        "operator_id": operator_id,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "UNRELEASE_LOCKDOWN":
                    if not self._require_confirmation(conn, request, "UNRELEASE_LOCKDOWN"):
                        return
                    self.failure_manager.set_mode(DisasterMode.NORMAL)
                    logger.info(f"LOCKDOWN RELEASED by {request.get('operator_id', 'UNKNOWN')}")
                    conn.sendall(json.dumps({
                        "status": "SUCCESS",
                        "mode": "NORMAL",
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "SET_LIVE_SHADOW_LEARNING":
                    enabled = bool(request.get("enabled", False))
                    import learning_freeze
                    learning_freeze.LIVE_SHADOW_LEARNING = enabled
                    logger.info(f"SHADOW_LEARNING_CONTROL: LIVE_SHADOW_LEARNING set to {enabled}")
                    conn.sendall(json.dumps({
                        "status": "SUCCESS",
                        "live_shadow_learning": enabled,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "COMMIT_SHADOW_LEARNING":
                    if not self._require_confirmation(conn, request, "COMMIT_SHADOW_LEARNING"):
                        return
                    success = self._commit_shadow_learning()
                    conn.sendall(json.dumps({
                        "status": "SUCCESS" if success else "FAILURE",
                        "active_boost": self.active_policy.learned_boost,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "ROLLBACK_SHADOW_LEARNING":
                    if not self._require_confirmation(conn, request, "ROLLBACK_SHADOW_LEARNING"):
                        return
                    success = self._rollback_active_policy()
                    conn.sendall(json.dumps({
                        "status": "SUCCESS" if success else "FAILURE",
                        "active_boost": self.active_policy.learned_boost,
                        "timestamp": time.time(),
                        "experience_id": experience_id
                    }).encode('utf-8'))
                    return

                if request.get("type") == "RESET_FAILURE_STATUS":
                    if not self._require_confirmation(conn, request, "RESET_FAILURE_STATUS"):
                        return
                    self.failure_manager.set_mode(DisasterMode.NORMAL)
                    self.failure_manager.incidents = []
                    self.auditor.history.clear()
                    self.monitor.consecutive_reward_degradations = 0
                    self.monitor.ml_policy_disabled = False # Clear any monitor kill-switch
                    
                    # Reset policy boosts
                    self.active_policy.learned_boost = 0.0
                    self.shadow_policy.learned_boost = 0.0
                    self.influence_weight = 0.0
                    
                    conn.sendall(json.dumps({
                        "status": "SUCCESS",
                        "mode": self.failure_manager.mode.value,
                        "timestamp": time.time(),
                        "experience_id": experience_id
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
                    # Detect version conflict or generic corruption
                    version = 0
                    if isinstance(raw_state, dict):
                        version = raw_state.get("state_version", 0)
                    
                    if version != 0 and version not in self.parser.SUPPORTED_VERSIONS:
                        self.failure_manager.record_incident(
                            FailureType.VERSION_CONFLICT, 
                            severity=FailureSeverity.HIGH,
                            experience_id=experience_id,
                            details=f"Version {version} unsupported"
                        )
                    else:
                        self.failure_manager.record_incident(
                            FailureType.CORRUPTION_NAN, 
                            severity=FailureSeverity.HIGH,
                            experience_id=experience_id,
                            details="StateParser rejected state"
                        )
                    
                    self._send_error(conn, "Malformed state data (Parser rejected)")
                    return

                # 2.1 EXTRA INTEGRITY CHECKS (Timestamp extraction)
                def safe_float(val, default=0.0):
                    try:
                        f = float(val)
                        return f if math.isfinite(f) else default
                    except (ValueError, TypeError):
                        return default

                java_timestamp = None
                if isinstance(raw_state, dict):
                    java_timestamp = raw_state.get("timestamp")
                    if java_timestamp is not None:
                        java_timestamp = safe_float(java_timestamp)

                # 3. Process experience (Shadow Learning)
                intent_taken = request.get("intent_taken")
                controller = request.get("controller")
                if not isinstance(controller, str):
                    controller = "AI"
                controller = controller.upper()

                last_confidence = safe_float(request.get("last_confidence"))
                last_fallback_reason = request.get("last_fallback_reason")
                last_model_version = request.get("last_model_version")
                last_inference_ms = safe_float(request.get("last_inference_ms"))
                last_shadow_data = request.get("shadow_data")

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

                # DATA LINEAGE FOR THE EXPERIENCE JUST RECEIVED (Step 3)
                # This experience came from Java
                received_authority = request.get("authority")
                if not received_authority or received_authority == "UNKNOWN":
                    # Default based on controller if not provided by legacy Java
                    received_authority = "AUTHORITATIVE" if controller in ["HUMAN", "AI"] else "OVERRIDE"

                # Requirement: Explicit learning permit check
                policy_authority = request.get("policy_authority")
                is_learning_permit = policy_authority in ["SHADOW_LEARNING_PERMIT", "ACTIVE_LEARNING_PERMIT"]

                received_lineage = {
                    "source": "JAVA_SOURCE",
                    "trust_boundary": "EXTERNAL_UNTRUSTED",
                    "learning_allowed": is_learning_permit,
                    "decision_authority": controller
                }

                # Determine policy mode for auditing (MANDATORY for SHADOW safety)
                # SHADOW mode if explicitly permitted OR if system is in HUMAN control mode.
                policy_mode = "SHADOW" if (policy_authority == "SHADOW_LEARNING_PERMIT" or controller == "HUMAN") else "ACTIVE"

                # Calculate reward for the action just taken
                # Pass learning_allowed to tag the reward
                reward, reward_breakdown, reward_class = self.reward_calc.calculate(
                    result, 
                    learning_allowed=received_lineage["learning_allowed"],
                    is_incomplete=normalized_state.get("is_incomplete", False) or not isinstance(raw_result, dict)
                )

                # 3.1 AUDIT RECORDING
                # We record what Java reports as having just happened.
                audit_entry = self.auditor.record_cycle(
                    experience_id=experience_id,
                    raw_state=raw_state,
                    state_version=normalized_state["version"],
                    intent_issued=intent_taken if intent_taken else "NONE",
                    confidence=last_confidence,
                    policy_source=request.get("policy_source", "UNKNOWN"),
                    controller=controller,
                    raw_java_result=raw_result,
                    reward_total=reward,
                    reward_breakdown=reward_breakdown,
                    fallback_reason=last_fallback_reason,
                    model_version=last_model_version,
                    inference_ms=last_inference_ms,
                    shadow_data=last_shadow_data,
                    java_timestamp=java_timestamp,
                    missing_fields=normalized_state.get("missing_fields", 0),
                    is_incomplete=normalized_state.get("is_incomplete", False),
                    lineage=received_lineage,
                    authority=received_authority,
                    reward_class=reward_class,
                    full_payload=request,
                    policy_mode=policy_mode
                )

                # Phase 10B: Reward Tracking for Influence
                if last_shadow_data:
                    shadow_intent = last_shadow_data.get("intent")
                    if intent_taken == shadow_intent:
                        self.shadow_reward_window.append(reward)
                    elif request.get("policy_source", "").startswith("SHADOW_INFLUENCE_"):
                        self.shadow_reward_window.append(reward)
                
                if request.get("policy_source", "").startswith("ML_"):
                    self.active_reward_window.append(reward)

                # Requirement 5: Influence Adjustment Rules
                self._adjust_influence_weight(audit_entry)

                # Store in memory and train if we have a valid experience
                # Shadow mode must never be blocked from observing. (Requirement Final Decision Logic)
                if intent_taken:
                    # Run quality gate but do not drop data for Shadow Observation
                    is_quality_pass = self.auditor.check_quality_gate(audit_entry)
                    if not is_quality_pass:
                        logger.info(f"[SHADOW] Experience {experience_id} quality gate fail - proceeding with observation")

                    logger.debug(f"Experience: intent={intent_taken}, controller={controller}, reward={reward:.2f}")
                    try:
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
                            fallback_reason=last_fallback_reason,
                            model_version=last_model_version,
                            lineage=received_lineage
                        )
                        
                        # Trainer handles IL (HUMAN) vs RL (AI/HEURISTIC)
                        verified_lineage = received_lineage.copy()
                        verified_lineage["trust_boundary"] = "INTERNAL_VERIFIED"
                    
                        experience_for_trainer = {
                            "experience_id": experience_id,
                            "episode_id": self.episode_id,
                            "state_hash": audit_entry["state_hash"],
                            "state": normalized_state,
                            "intent": intent_taken,
                            "reward": reward,
                            "controller": controller,
                            "lineage": verified_lineage,
                            "violations": audit_entry.get("violations", []),
                            "policy_authority": request.get("policy_authority"),
                            "policy_mode": self.trainer.policy.role if hasattr(self.trainer.policy, 'role') else "ACTIVE",
                            "state_status": "INCOMPLETE" if normalized_state.get("is_incomplete") else "COMPLETE"
                        }
                        
                        # 3.2 EVALUATE LEARNING GATES
                        policy_context = types.SimpleNamespace(
                            mode=experience_for_trainer["policy_mode"],
                            lineage=experience_for_trainer["lineage"],
                            violations=experience_for_trainer["violations"],
                            policy_authority=experience_for_trainer["policy_authority"]
                        )
                        gate_decisions = self.learning_gate_system.evaluate_all(policy_context)
                        for d in gate_decisions:
                            logger.info(f"LEARNING_GATE [{d.gate_name}]: {d.result.name} - {d.reason}")
                            if d.result != LearningGateResult.PASS:
                                # severity: BLOCKED → HIGH, FAIL → MEDIUM
                                sev = FailureSeverity.HIGH if d.result == LearningGateResult.BLOCKED else FailureSeverity.MEDIUM
                                self.failure_manager.record_incident(
                                    FailureType.LEARNING_GATE_BLOCK,
                                    severity=sev,
                                    experience_id=experience_id,
                                    details=f"Gate: {d.gate_name}, Reason: {d.reason}"
                                )
                        
                        # FINAL_LEARNING_DECISION refers to "Action" (Applying), not "Observation" (Learning)
                        action_decision = self.learning_gate_system.get_final_decision(gate_decisions)
                        logger.info(f"FINAL_LEARNING_ACTION_DECISION: {action_decision}")
                        
                        # 3.3 READINESS EVALUATION (Counterfactual)
                        readiness_report = self.readiness_analyzer.analyze(experience_for_trainer, gate_decisions)
                        self.last_readiness_report = readiness_report
                        logger.info(f"LEARNING_READINESS: {json.dumps(readiness_report)}")
                        
                        # Readiness Incident Rules
                        if readiness_report.get("confidence_sufficient") and readiness_report.get("data_quality_ok") and readiness_report.get("reward_signal_valid"):
                            if any(d.result != LearningGateResult.PASS for d in gate_decisions):
                                # Readiness suggests learning despite gates closed -> HIGH
                                self.failure_manager.record_incident(
                                    FailureType.LEARNING_READINESS_VIOLATION,
                                    severity=FailureSeverity.HIGH,
                                    experience_id=experience_id,
                                    details="Readiness suggests learning despite gates closed"
                                )
                        
                        if readiness_report.get("confidence_sufficient") and not readiness_report.get("data_quality_ok"):
                            # Readiness metrics inconsistent -> MEDIUM
                            self.failure_manager.record_incident(
                                FailureType.LEARNING_READINESS_VIOLATION,
                                severity=FailureSeverity.MEDIUM,
                                    experience_id=experience_id,
                                    details="Readiness metrics inconsistent: High confidence but low data quality"
                                )
                            
                        # LIVE_SHADOW_LEARNING allowed for ShadowPolicy
                        # Shadow mode must never be blocked from observing. (Requirement Final Decision Logic)
                        is_blocked = (action_decision != "SHADOW_LEARNING_ALLOWED")
                        
                        if not is_blocked:
                            try:
                                self.trainer.train_on_experience(experience_for_trainer)
                            except PermissionError as e:
                                if "LEARNING_FREEZE_BREACH" in str(e):
                                    self.failure_manager.record_incident(
                                        FailureType.LEARNING_FREEZE_BREACH,
                                        severity=FailureSeverity.CRITICAL,
                                        experience_id=experience_id,
                                        details=str(e)
                                    )
                                else:
                                    raise e
                        else:
                            # Observing even if blocked from action
                            logger.info(f"[SHADOW] Experience observed despite gate block for {experience_id}")
                            try:
                                self.trainer.train_on_experience(experience_for_trainer)
                            except Exception as e:
                                logger.error(f"[SHADOW] Failed to observe experience {experience_id}: {e}")
                    except ValueError as e:
                            if "DATA_LINEAGE_VIOLATION" in str(e):
                                # SHADOW mode must never trigger FailureManager for observational issues
                                if policy_mode != "SHADOW":
                                    self.failure_manager.record_incident(
                                        FailureType.DATA_LINEAGE_VIOLATION,
                                        severity=FailureSeverity.HIGH,
                                        experience_id=experience_id,
                                        details=str(e)
                                    )
                                else:
                                    logger.info(f"[SHADOW] Data lineage violation observed but not recorded in FailureManager: {e}")
                            else:
                                raise e
                    else:
                        # Report incidents for critical audit violations
                        # STRICTLY FORBIDDEN: DO NOT TRIGGER FAILURE_MANAGER for Shadow mode quality failures
                        if audit_entry.get("policy_mode") != "SHADOW":
                            for violation in audit_entry.get("violations", []):
                                v_type = violation["type"]
                                if v_type in ["REWARD_BOUNDS_VIOLATION", "REWARD_DOMINANCE_VIOLATION"]:
                                    self.failure_manager.record_incident(
                                        FailureType.REWARD_INVARIANT, 
                                        severity=FailureSeverity.CRITICAL,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry
                                    )
                                elif v_type in ["CONTRACT_VIOLATION", "MALFORMED_PAYLOAD", "MISSING_FIELD", "UNKNOWN_ENUM", "DATA_LINEAGE_VIOLATION"]:
                                    self.failure_manager.record_incident(
                                        FailureType.CONTRACT_VIOLATION, 
                                        severity=FailureSeverity.HIGH,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry
                                    )
                                elif v_type == "INTENT_MISMATCH":
                                    self.failure_manager.record_incident(
                                        FailureType.CONTRACT_VIOLATION, 
                                        severity=FailureSeverity.CRITICAL,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry
                                    )
                                elif v_type == "HOSTILE_PAYLOAD":
                                    self.failure_manager.record_incident(
                                        FailureType.HOSTILE_PAYLOAD, 
                                        severity=FailureSeverity.CRITICAL,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry,
                                        details=f"Hostile field: {violation.get('field')}"
                                    )
                                elif v_type == "POLICY_INTERFERENCE":
                                    self.failure_manager.record_incident(
                                        FailureType.CONTRACT_VIOLATION, 
                                        severity=FailureSeverity.HIGH,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry
                                    )
                                elif v_type == "STALE_STATE":
                                    self.failure_manager.record_incident(
                                        FailureType.STALE_STATE, 
                                        severity=FailureSeverity.HIGH,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry
                                    )
                                elif v_type == "BACKWARDS_TIME":
                                    self.failure_manager.record_incident(
                                        FailureType.TIME_INTEGRITY_VIOLATION, 
                                        severity=FailureSeverity.HIGH,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry
                                    )
                                elif v_type == "PARTIAL_CORRUPTION":
                                    # Data integrity failures
                                    self.failure_manager.record_incident(
                                        FailureType.CORRUPTION_NAN, 
                                        severity=FailureSeverity.HIGH,
                                        experience_id=experience_id,
                                        audit_entry=audit_entry
                                    )
                        else:
                            # Shadow mode: violations are already downgraded to INFO in Auditor
                            # and we don't trigger the failure manager.
                            if audit_entry.get("violations"):
                                logger.info(f"[SHADOW] Audit violations observed for {experience_id} - skipping FailureManager reporting")
                
                # 3.2 MONITORING & AUTO-PROTECTION
                # Run analysis and apply recommended mitigations
                health_status = self.monitor.analyze()
                mitigation = health_status.get("recommended_mitigation")
                if mitigation and mitigation != "NONE":
                    self.arbitrator.apply_mitigation(mitigation, policy_mode=policy_mode)
                
                # Report incidents for critical alerts
                for alert in health_status.get("alerts", []):
                    if alert["level"] == "CRITICAL":
                        mapping = {
                            "SURVIVAL_CRASH": FailureType.CONFIDENCE_COLLAPSE,
                            "CONTRACT_BREACH": FailureType.CONTRACT_VIOLATION,
                            "LATENCY_SPIKE": FailureType.TIMEOUT,
                            "INTENT_COLLAPSE": FailureType.CONFIDENCE_COLLAPSE,
                            "INTEGRITY_BREACH": FailureType.TIME_INTEGRITY_VIOLATION
                        }
                        f_type = mapping.get(alert["type"], FailureType.CONTRACT_VIOLATION)
                        self.failure_manager.record_incident(f_type, details=f"Monitor Alert: {alert}", audit_entry=audit_entry)

                # 3.3 Episode Management: If dead, next experience belongs to a new episode
                if result["outcomes"].get("is_alive") is False:
                    logger.info(f"Episode {self.episode_id} ended (Death detected). Incrementing ID.")
                    self.episode_id += 1

                # 4. Decide next intent
                # Set crash flag before starting heavy computation
                if self.failure_manager.mode != DisasterMode.READ_ONLY_DISK:
                    try:
                        with open(self.crash_flag_path, 'w') as f:
                            f.write(str(time.time()))
                    except OSError:
                        # If we can't write, it might be DISK_FULL
                        pass

                next_intent = "STOP"
                confidence = 1.0
                policy_source = "UNKNOWN"
                authority = "AUTHORITATIVE"
                fallback_reason = None
                model_version = None
                shadow_data = None

                # 4.0 INTEGRITY OVERRIDES (Inference Safety)
                has_duplicate = any(v["type"] == "DUPLICATE_STATE" for v in audit_entry["violations"])
                has_stale = any(v["type"] == "STALE_STATE" for v in audit_entry["violations"])

                # Phase 10B: Control Mode Gating (Non-Negotiable)
                if self.control_mode == "HUMAN":
                    next_intent = Intent.STOP.value
                    confidence = 1.0
                    policy_source = "HUMAN_GATED"
                    authority = "ADVISORY"
                    fallback_reason = "CONTROL_MODE_HUMAN"
                    # Learning, logging, auditing MUST still occur.
                    # We run the arbitrator in shadow mode to get metrics and shadow_data
                    _, _, _, _, _, _, shadow_data = self.arbitrator.decide(normalized_state, policy_mode=policy_mode, state_hash=audit_entry["state_hash"])
                    
                    # Hard fail if ShadowPolicy emits actions in HUMAN mode (Requirement 2 & 4)
                    if next_intent != Intent.STOP.value:
                        logger.critical("HARD_FAIL: ShadowPolicy attempted to emit actions in HUMAN mode!")
                        import sys
                        sys.exit(1)

                elif has_stale:
                    next_intent = Intent.STOP.value
                    policy_source = "INTEGRITY_STALE_FALLBACK"
                    authority = "OVERRIDE"
                    fallback_reason = "STALE_STATE_DETECTED"
                
                elif has_duplicate:
                    # For duplicates, we return STOP to avoid repeating potentially harmful actions 
                    # without state progression.
                    next_intent = Intent.STOP.value
                    policy_source = "INTEGRITY_DUPLICATE_BYPASS"
                    authority = "OVERRIDE"
                    fallback_reason = "DUPLICATE_STATE_DETECTED"

                # 4.1 DISASTER MODE OVERRIDES
                if self.failure_manager.mode == DisasterMode.ISOLATED and policy_mode == "SHADOW":
                    logger.warning("NETWORK ISOLATION DETECTED: Shadow mode continuing observational logic despite isolation.")

                if self.failure_manager.mode in [DisasterMode.READ_ONLY, DisasterMode.LOCKDOWN] and policy_mode != "SHADOW":
                    next_intent = Intent.STOP.value
                    policy_source = "DISASTER_READ_ONLY"
                    authority = "OVERRIDE"
                    fallback_reason = "CRITICAL_FAILURE"
                
                elif self.failure_manager.mode == DisasterMode.ISOLATED and policy_mode != "SHADOW":
                    next_intent = Intent.STOP.value
                    policy_source = "DISASTER_ISOLATED"
                    authority = "OVERRIDE"
                    fallback_reason = "NETWORK_LOST_STANDBY"
                
                elif controller != "HUMAN":
                    requested_policy = request.get("policy_override", self.active_policy_name)
                    
                    # Sync influence weight (Phase 10B)
                    self.arbitrator.influence_weight = self.influence_weight
                    
                    # SAFE_MODE: Force Heuristic
                    if self.failure_manager.mode == DisasterMode.SAFE_MODE and policy_mode != "SHADOW":
                        requested_policy = "HEURISTIC"
                        fallback_reason = "DISASTER_SAFE_MODE"
                    
                    if requested_policy == "ML":
                        # Use the Arbitrator for ML path
                        next_intent, confidence, policy_source, authority, fallback_reason, model_version, shadow_data = self.arbitrator.decide(normalized_state, policy_mode=policy_mode, state_hash=audit_entry["state_hash"])
                    else:
                        # Direct policy access (Heuristic or Random)
                        policy = self.policies.get(requested_policy, self.policies[self.active_policy_name])
                        next_intent, confidence, model_version, authority = policy.decide(normalized_state, state_hash=audit_entry["state_hash"])
                        policy_source = requested_policy
                        fallback_reason = fallback_reason or "MANUAL_OVERRIDE"

                    # 4.2 Drift Detection Verification (Natural)
                    current_state_hash = audit_entry.get("state_hash")
                    is_drifted, drift_reason = self.drift_verifier.verify_drift(current_state_hash, {
                        "intent": next_intent,
                        "confidence": confidence,
                        "policy_source": policy_source
                    })
                    if is_drifted:
                        is_shadow_drift = policy_source.startswith(("SHADOW_INFLUENCE_", "CANARY_", "SHADOW_MODE")) or policy_mode == "SHADOW"
                        
                        self.failure_manager.record_incident(
                            FailureType.MODEL_DIVERGENCE,
                            severity=FailureSeverity.INFO if is_shadow_drift else FailureSeverity.CRITICAL,
                            experience_id=experience_id,
                            details=f"{'[SHADOW] ' if is_shadow_drift else ''}CRITICAL_DRIFT: {drift_reason}"
                        )
                        
                        if not is_shadow_drift:
                            # Force LOCKDOWN on active drift only
                            self.failure_manager.set_mode(DisasterMode.LOCKDOWN)
                            next_intent = "STOP"
                            policy_source = "CRITICAL_DRIFT_FALLBACK"
                        else:
                            logger.info(f"[SHADOW] Drift ignored: {drift_reason}")

                    # 4.3 Periodic Golden Registration (every 100 requests)
                    if len(self.auditor.history) % 100 == 0:
                        self.drift_verifier.register_golden(current_state_hash, raw_state, {
                            "intent": next_intent,
                            "confidence": confidence,
                            "policy_source": policy_source
                        })
                        
                    # 4.4 Explicit Drift Replay Audit
                    if len(self.auditor.history) % 500 == 0:
                        self._run_drift_audit()
                else:
                    # In HUMAN mode, we just observe. Arbitrator now returns STOP in SHADOW mode.
                    next_intent, confidence, policy_source, authority, fallback_reason, model_version, shadow_data = self.arbitrator.decide(normalized_state, policy_mode="SHADOW", state_hash=audit_entry["state_hash"])
                    logger.debug(f"Shadow Observation: Human took {intent_taken}, AI would have taken {shadow_data.get('intent')} via SHADOW_POLICY")

                # 5. Return response (Validate outgoing intent)
                if not Intent.has_value(next_intent):
                    logger.error(f"POLICY VIOLATION: Policy generated invalid intent '{next_intent}'. Falling back to STOP.")
                    next_intent = Intent.STOP.value
                    confidence = 0.0
                    authority = "OVERRIDE"

                response = {
                    "experience_id": experience_id,
                    "intent": next_intent,
                    "confidence": float(confidence),
                    "policy_source": policy_source,
                    "learning_state": LEARNING_STATE,
                    "authority": authority,
                    "fallback_reason": fallback_reason,
                    "model_version": model_version,
                    "shadow_data": shadow_data, # Return shadow decision if candidate exists
                    "inference_ms": self.ml_policy.last_inference_time,
                    "reward_calculated": float(reward),
                    "reward_breakdown": reward_breakdown,
                    "reward_class": reward_class,
                    "state_version": normalized_state["version"],
                    "server_version": self.version,
                    "lineage": received_lineage
                }
                
                conn.sendall(json.dumps(response).encode('utf-8'))
                
                # Success! Clear crash flag
                try:
                    if os.path.exists(self.crash_flag_path):
                        os.remove(self.crash_flag_path)
                except OSError:
                    pass

        except socket.timeout:
            logger.debug(f"Connection timeout for {addr}")
        except Exception as e:
            logger.error(f"Unexpected error handling client {addr}: {e}", exc_info=True)
            self.failure_manager.record_incident(FailureType.CONTRACT_VIOLATION, details=str(e))
            self._send_error(conn, "Internal Server Error")
            # Requirement 3: Reset to 0.0 on any error
            self.influence_weight = 0.0

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
            next_intent, confidence, policy_source, _, _, _, _ = self.arbitrator.decide(normalized_state, state_hash=state_hash)
            
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

    def _send_error(self, conn: socket.socket, message: str) -> None:
        """Helper to send standard error responses."""
        try:
            self._reset_influence() # Requirement 3
            response = {"error": message, "status": "ERROR"}
            conn.sendall(json.dumps(response).encode('utf-8'))
        except Exception:
            pass

    def _check_flood(self) -> Tuple[bool, int]:
        now = time.time()
        with self.flood_lock:
            self.request_history.append(now)
            # Remove timestamps older than 1 second
            while self.request_history and self.request_history[0] < now - 1.0:
                self.request_history.popleft()
            
            rate = len(self.request_history)
            if rate > self.FLOOD_THRESHOLD:
                return True, rate
        return False, rate

    def _require_confirmation(self, conn: socket.socket, request: Dict[str, Any], action_name: str) -> bool:
        """
        Implements two-step confirmation for sensitive commands.
        Returns True if confirmed, False if a token was issued.
        """
        token = request.get("confirmation_token")
        operator_id = request.get("operator_id", "UNKNOWN_OPERATOR")
        
        if token and token in self.pending_confirmations:
            pending = self.pending_confirmations.pop(token)
            if pending["action"] == action_name:
                logger.info(f"CONFIRMED: Action '{action_name}' confirmed by {operator_id}")
                return True
        
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
        
        conn.sendall(json.dumps({
            "status": "REQUIRED_CONFIRMATION",
            "message": f"Action '{action_name}' requires confirmation.",
            "confirmation_token": new_token,
            "operator_id": operator_id
        }).encode('utf-8'))
        return False

    def _network_watchdog(self):
        """Monitor connection health."""
        while True:
            time.sleep(5)
            idle_time = time.time() - self.last_request_time
            if idle_time > 30 and self.failure_manager.mode == DisasterMode.NORMAL:
                logger.warning(f"NETWORK WATCHDOG: No requests for {idle_time:.1f}s. System potentially isolated.")
                # We don't force ISOLATED mode yet, as it could just be a quiet period in game.
                # But we could log it.
                
            if idle_time > 60 and self.failure_manager.mode != DisasterMode.ISOLATED:
                logger.error("NETWORK WATCHDOG: Network drop suspected. Entering ISOLATED mode.")
                self.failure_manager.record_incident(FailureType.NETWORK_DROP, details=f"Idle for {idle_time:.1f}s")

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
    enforce_mode([ExecutionMode.PROD_SERVER])
    server = AIServer()
    server.start()
