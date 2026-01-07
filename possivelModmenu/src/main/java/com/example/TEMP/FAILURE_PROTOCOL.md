# FAILURE MANAGEMENT PROTOCOL (v1.0.0)

This document defines the formal failure taxonomy, incident response playbooks, and recovery workflows for the Java ↔ Python AI system.

## 1. FAILURE TAXONOMY

| Category | Type | Description | Detection | Severity |
| :--- | :--- | :--- | :--- | :--- |
| **ML** | `CONFIDENCE_COLLAPSE` | ML confidence drops below 0.1 consistently. | Monitor | MEDIUM |
| **ML** | `MODEL_DIVERGENCE` | Candidate model deviates > 80% from Production. | Monitor | HIGH |
| **Logic** | `CONTRACT_VIOLATION` | Java returns unknown Enums or missing fields. | Auditor | HIGH |
| **Logic** | `REWARD_INVARIANT` | Reward bounds or dominance rules breached. | Auditor | CRITICAL |
| **Data** | `CORRUPTION_NAN` | NaN or Infinity detected in state/reward. | StateParser | HIGH |
| **Data** | `VERSION_CONFLICT` | Unsupported state_version received. | StateParser | HIGH |
| **Comm** | `TIMEOUT` | Inference or network latency > 50ms. | Arbitrator | MEDIUM |
| **Comm** | `MALFORMED_JSON` | Root payload is not valid JSON. | AIServer | HIGH |

## 2. DISASTER MODES

| Mode | Description | Action Taken | Entry Condition | Exit Condition |
| :--- | :--- | :--- | :--- | :--- |
| **NORMAL** | Standard operation. | ML + Heuristic active. | System Start | Failure Detected |
| **SAFE MODE** | Heuristic-only control. | ML disabled; Heuristic active. | ML Failure / Timeout | Manual Reset / Auto-Cooldown |
| **READ-ONLY** | Observational mode. | Policy returns `STOP`. Audit only. | Critical Contract Breach | Manual Intervention |
| **FREEZE** | Learning suspended. | Memory/Trainer blocked. | Data Corruption / Reward Hack | Manual Audit Pass |

## 3. INCIDENT RESPONSE PLAYBOOKS

### A) ML ANOMALY (Confidence/Divergence)
1. **Detection**: `Monitor` flags high failure rate or low confidence.
2. **Containment**: Trigger `SOFT_DEGRADE` (100-cycle bypass).
3. **Mitigation**: Switch to `SAFE MODE`.
4. **Recovery**: If metrics stabilize, auto-recover; otherwise, trigger `ROLLBACK_MODEL`.

### B) DATA CORRUPTION (NaN/Malformed)
1. **Detection**: `StateParser` or `Auditor` detects invalid types/structure.
2. **Containment**: Immediate `LEARNING FREEZE` for current cycle.
3. **Mitigation**: Inject safe defaults. If corruption > 5%, switch to `READ-ONLY`.
4. **Recovery**: Log cycle for inspection. Resume after 10 clean cycles.

### C) CRITICAL CONTRACT BREACH
1. **Detection**: `Auditor` flags `CRITICAL` severity violation.
2. **Containment**: Enter `READ-ONLY` mode immediately.
3. **Mitigation**: Issue `STOP` intent to Java to prevent unsafe gameplay.
4. **Recovery**: REQUIRES HUMAN INTERVENTION. Check Java-side logs.

## 4. POST-MORTEM PIPELINE

Every incident MUST generate a `failure_report.json` containing:
- `incident_id`: Unique UUID.
- `timestamp`: UTC.
- `failure_type`: From taxonomy.
- `audit_hash`: Link to the exact cycle in logs.
- `state_snapshot`: The raw state that caused the failure.
- `reproduction_steps`: How to trigger this in a test environment.

### Replay Mechanism:
1. Load `state_snapshot` into `StateParser`.
2. Execute `PolicyArbitrator` with the logged `model_version`.
3. Verify if output matches the failure condition.

## 5. FORBIDDEN BEHAVIOR UNDER FAILURE

1. **NEVER** silently ignore a contract violation.
2. **NEVER** allow ML to control the entity if Heuristic veto is failing.
3. **NEVER** update weights with quarantined data.
4. **NEVER** allow Java to dictate the recovery mode.
5. **NEVER** crash the Python server due to malformed Java input.

## 6. RECOVERY WORKFLOWS

### Automatic Recovery (Level 1)
- Triggered for: `TIMEOUT`, `ML_REJECTION`.
- Action: Bypassing failing component for N cycles, then retrying.

### Guided Recovery (Level 2)
- Triggered for: `MODEL_DIVERGENCE`, `SOFT_DEGRADE` persistence.
- Action: Trigger `ROLLBACK_MODEL` to `last_good_version`.

### Manual Recovery (Level 3)
- Triggered for: `REWARD_INVARIANT`, `READ-ONLY` mode.
- Action: Administrator must call `RESET_FAILURE_STATUS` after investigation.
