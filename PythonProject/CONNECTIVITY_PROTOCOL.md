# EXTERNAL CONNECTIVITY PROTOCOL (v1.0.0)

This document defines the requirements and guarantees for the Python AI Server when connected to the external Java bridge (Minecraft).

## 1. OBJECTIVE
Enable real-world reality exposure by connecting the AI Server to live game state while maintaining an absolute, irreversible learning freeze.

## 2. INBOUND FLOW (JAVA -> PYTHON)
- **Direct Acceptance**: All payloads from the Java bridge are accepted exactly as received.
- **State Preservation**: The original `raw_state` (sensor values) and `java_timestamp` are preserved in the audit logs for forensic analysis.
- **Non-Intervention**: No pre-cleaning or "fixing" of payloads occurs before the `StateParser` layer.

## 3. OUTBOUND FLOW (PYTHON -> JAVA)
Every decision response MUST include:
- `intent`: The selected action.
- `confidence`: Statistical certainty of the decision.
- `policy_source`: The specific logic path used (e.g., ML, HEURISTIC, FALLBACK).
- `state_version`: The schema version used for parsing.
- `learning_state`: Always set to `FROZEN`.

## 4. NOISE & INTEGRITY HANDLING
The system is hardened against real-world network and sensor noise:
- **Missing Fields**: Handled via explicit default injection in `StateParser`.
- **NaN / Non-numeric**: Clamped or defaulted to prevent propagation.
- **Tick-rate Mismatch**: Detected and logged as `TICK_RATE_MISMATCH` if intervals are abnormal.
- **Duplicate States**: Detected; triggers an `INTEGRITY_DUPLICATE_BYPASS` fallback to `STOP`.
- **Stale States**: Detected (age > 100ms); triggers an `INTEGRITY_STALE_FALLBACK` to `STOP`.
- **Out-of-order Timestamps**: Detected as `BACKWARDS_TIME` violations.

## 5. LEARNING FREEZE GUARANTEES
- **Structural Impossibility**: Connectivity does NOT enable learning.
- **Observational Readiness**: Learning readiness metrics are strictly counterfactual and observational.
- **No Action on PASS**: Even if all learning gates PASS, the Trainer remains unreachable.
- **Cryptographic/Logical Lock**: The `LEARNING_STATE` constant is immutable at runtime.

## 6. OBSERVABILITY METRICS
The system exposes live metrics for:
- Duplicate rate
- Stale rate
- Parser default injection rate
- Fallback rate
- Violation rate

## 7. POLICY AUTHORITY
At this stage, no policy has autonomous learning authority. All ML suggestions are advisory or subject to the frozen production model parameters.
