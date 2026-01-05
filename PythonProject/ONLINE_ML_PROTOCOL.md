# ONLINE ML INFERENCE PROTOCOL (v1.0.0)

This document defines the formal safety, fallback, and monitoring requirements for running ML models in a live decision environment.

## 1. INFERENCE SAFETY ENVELOPE

Every ML inference call MUST be wrapped in a safety envelope that guarantees system stability.

### A) Time Bounding (Timeout)
- **Hard Limit**: ML inference MUST NOT exceed 50ms.
- **Enforcement**: If inference exceeds the limit, the call is aborted and the system MUST fallback to the **HEURISTIC** policy immediately.
- **Reporting**: Every timeout must be logged as a `ML_TIMEOUT` event.

### B) Output Validation
- **Canonical Check**: The suggested intent MUST be one of the canonical intents.
- **Sanity Check**: The suggestion MUST NOT violate basic physical constraints (e.g., trying to `JUMP` while already in a state where jump is impossible, though Java handles execution, Python should avoid obviously bad suggestions if possible).
- **Confidence Sanity**: `self_reported_confidence` MUST be a float between [0.0, 1.0]. If outside this range, the suggestion is rejected.

### C) Intent Allowlists
- Certain intents may be restricted for ML usage in specific high-risk states (e.g., ML may be forbidden from issuing `RELEASE` during a critical hold maneuver).

---

## 2. FALLBACK STRATEGY

The system MUST maintain a cascading fallback chain to ensure a decision is ALWAYS made.

### A) Cascade Order
1. **HEURISTIC VETO**: If heuristic identifies a critical survival condition.
2. **ML SUGGESTION**: If enabled, within time bounds, and high confidence.
3. **HEURISTIC FALLBACK**: If ML fails, times out, or is uncertain.
4. **RANDOM FALLBACK**: If all else fails.

### B) Oscillation Prevention
- **Cooldown**: If ML is disabled due to a timeout or failure, it MUST remain disabled for a minimum of 10 cycles (Soft Degrade).
- **Hysteresis**: ML confidence must exceed `THRESHOLD + 0.05` to re-engage after a fallback event.

---

## 3. LIVE MONITORING & METRICS

The following metrics MUST be tracked in real-time to detect model degradation.

| Metric | Definition | Threshold | Action |
| :--- | :--- | :--- | :--- |
| `ML Divergence` | % of cycles where ML and Heuristic disagree significantly. | > 40% | Warning |
| `ML Failure Rate` | % of ML calls resulting in timeout or rejection. | > 5% | SOFT DEGRADE |
| `Inference Latency` | Avg time taken for ML inference. | > 30ms | Warning |
| `Confidence Drift` | Significant change in avg ML confidence over 100 cycles. | ± 20% | Warning |

---

## 4. EMERGENCY CONTROLS

### A) Hard Kill-Switch
- **Remote Command**: `SET_ML_ENABLED = False`.
- **Latency**: MUST take effect within the VERY NEXT cycle.
- **Persistence**: Policy remains disabled until manually re-enabled.

### B) Soft Degrade Mode
- **Auto-Trigger**: High failure rate or repeated timeouts.
- **Behavior**: System automatically switches `active_policy` to `HEURISTIC` but continues to run ML in "shadow mode" (logging but not acting) to monitor recovery.

---

## 5. FORBIDDEN ONLINE ML PRACTICES

1. **NEVER** block the main server loop for ML inference.
2. **NEVER** allow ML to override a critical survival heuristic.
3. **NEVER** retry a timed-out ML inference in the same cycle.
4. **NEVER** ignore a rising failure rate; automate the degrade.
5. **NEVER** assume a "new" model is safe without shadow-mode validation.

---

## 6. NON-NEGOTIABLE COMPLIANCE CHECKLIST
- [ ] ML inference is wrapped in a timer.
- [ ] Fallback to Heuristic is atomic and tested.
- [ ] Failure rates are reported in `HEARTBEAT`.
- [ ] Kill-switch is functional and immediate.
