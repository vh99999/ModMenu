# RESILIENCE & ADVERSARIAL DEFENSE PROTOCOL (v1.0.0)

This document defines the server's strategy for maintaining stability, safety, and observability under adversarial conditions, including network stress, time disorder, and hostile payloads.

## 1. STRESS ASSUMPTIONS & THRESHOLDS

The system is designed to handle the following stress conditions without silent failure or undefined behavior:

| Category | Threshold | Mitigation Strategy |
| :--- | :--- | :--- |
| **Request Rate** | > 500 req/s | `FLOOD_DETECTION` incident; fallback to `SAFE_MODE`. |
| **Latency (TTL)** | > 100ms | `STALE_STATE` violation; fallback to `STOP`. |
| **Clock Drift** | > 1.0s | `CLOCK_JUMP` incident; `TEMPORAL_LOCK`. |
| **Time Disorder** | Backwards | `TIME_INTEGRITY_VIOLATION`; `TEMPORAL_LOCK`. |

---

## 2. ABUSE HANDLING (HOSTILE PAYLOADS)

The system treats specific payload patterns as adversarial attempts to bypass safety controls.

### A) Hostile Field Detection
The Auditor inspects every experience payload for forbidden control flags:
- `allow_learning_if`
- `force_learning`
- `suppress_incidents`
- `bypass_gates`

**Action**: Detection of any hostile field triggers a `CRITICAL` incident (`HOSTILE_PAYLOAD`) and immediate system `LOCKDOWN`.

### B) Policy Interference
Attempts to override the active policy for an `AI` controller from an experience payload are flagged.
- **Action**: `POLICY_INTERFERENCE` violation; High severity incident.

---

## 3. SAFE DEGRADATION BEHAVIOR

Under extreme pressure, the system follows a deterministic degradation path rather than failing silently.

1. **SAFE_MODE**: Triggered by floods or mild integrity issues.
   - Inference continues using the `HEURISTIC` policy only.
   - ML model usage is suspended to reduce risk/compute.
   
2. **READ_ONLY**: Triggered by contract breaches or hostile data.
   - All actions are forced to `STOP`.
   - The system remains observable but non-functional for gameplay.

3. **TEMPORAL_LOCK**: Triggered by clock anomalies.
   - Processing is suspended until time integrity can be re-verified.
   
4. **LOCKDOWN**: Triggered by critical safety breaches (e.g., learning freeze bypass attempts).
   - Immediate crash or total refusal of any non-handshake API calls.
   - Manual operator intervention required for release.

---

## 4. OBSERVABILITY UNDER STRESS

Adversarial conditions are made visible through:
- **Granular Drift Metrics**: `stale_rate`, `duplicate_rate`, `fallback_rate` exposed in `HEARTBEAT`.
- **Incident Trail**: All mitigations are recorded as persistent incidents in the `failures/` directory.
- **Provenance Logging**: Every response includes `policy_source` and `fallback_reason` explaining why a safe degradation path was taken.

---

## 5. GUARANTEES

- **Learning remains impossible**: Even if `bypass_gates` is sent, the `LEARNING_STATE` constant and hard assertions in `Trainer` prevent learning.
- **Inference is deterministic**: Given identical (valid) state, the system always produces the same intent.
- **No silent crashes**: Unexpected errors are caught, logged as incidents, and result in a safe `STOP` response.
