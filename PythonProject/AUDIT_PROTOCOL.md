# AUDIT & OBSERVABILITY PROTOCOL

This document defines the formal requirements for the Audit & Observability layer of the Java ↔ Python AI system. 
This layer is strictly non-interventional.

## 1. AUDIT DATA SCHEMA (Per Request/Response Cycle)

Every cycle MUST be recorded with the following structure:

| Field | Type | Description |
| :--- | :--- | :--- |
| `timestamp` | float | Unix timestamp of the request. |
| `state_hash` | string | SHA-256 hash of the RAW state received from Java. |
| `state_version` | integer | The version reported by the StateParser. |
| `intent_issued` | string | The intent decided by the active Python policy. |
| `confidence` | float | Policy's confidence in the issued intent [0.0, 1.0]. |
| `policy_source` | string | Enum: `RANDOM`, `HEURISTIC`, `FUTURE_ML`. |
| `controller` | string | Enum: `HUMAN`, `HEURISTIC`, `AI`. |
| `java_result` | object | The raw, unparsed result returned by Java. |
| `reward_total` | float | Final scalar reward calculated. |
| `reward_breakdown`| object | Decomposition of the reward components. |
| `violations` | array | List of detected contract violations in this cycle. |

## 2. VIOLATION TAXONOMY

Violations are categorized by severity and type.

| Category | Severity | Description |
| :--- | :--- | :--- |
| `UNKNOWN_ENUM` | MEDIUM | Java returned a status or failure reason not in the schema. |
| `MISSING_FIELD` | HIGH | Mandatory field missing in Java execution result. |
| `INTENT_MISMATCH` | CRITICAL | Java reported execution of an intent Python did not issue. |
| `EXPLANATION_MISSING`| MEDIUM | `partial_execution` is true but no explaining safety flags set. |
| `IMPOSSIBLE_TRANSITION`| CRITICAL | State change between t and t+1 violates physics/logic. |
| `MALFORMED_PAYLOAD` | HIGH | Root payload structure is invalid. |
| `VERSION_MISMATCH` | HIGH | Java using a state version not fully supported by Python. |

## 3. DATA QUALITY GATES (ANTI-POISON)

An experience is BLOCKED from training if any of these conditions are met:

- [ ] `MALFORMED_PAYLOAD` detected.
- [ ] `UNKNOWN_INTENT` (Python or Java side).
- [ ] `MISSING_REWARD_BREAKDOWN`.
- [ ] `CONTROLLER_MISMATCH` (Unexpected source for the intent).
- [ ] `STATE_VERSION_MISMATCH` (Major version jump without support).
- [ ] `CRITICAL` severity violation present.

Blocked experiences MUST be moved to a `quarantine` storage for manual inspection.

## 4. DRIFT & ANOMALY DETECTION

Metrics calculated over a sliding window (default: 1000 cycles):

| Metric | Threshold | Signal |
| :--- | :--- | :--- |
| `Intent Frequency Collapse` | Any intent < 1% | Policy has stopped exploring/considering an action. |
| `Confidence Inflation` | Avg Conf > 0.99 | Policy is overconfident (potential overfitting). |
| `Reward Distribution Drift` | 20% shift in Mean | Game mechanics changed or reward shaping is broken. |
| `Violation Rate` | > 5% of cycles | Contract is no longer being respected by Java. |

## 5. BLACK BOX REPLAY GUARANTEES

From the logs, it MUST be possible to:
1. Re-run the exact state through the parser to get the same feature vector.
2. Re-run the feature vector through the policy to get the same intent.
3. Re-calculate the reward from the Java result and state.

## 6. FORBIDDEN ASSUMPTIONS

The Audit Layer is NEVER allowed to assume:
1. That a log entry represents a "successful" game event.
2. That Java's `timestamp` is synchronized with Python's.
3. That missing data in a log means the data was zero (must be marked as `MISSING`).
4. That a high reward means the AI is "doing well" (could be reward drift).

## 7. EXAMPLE ENTRIES

### GOOD ENTRY
```json
{
  "timestamp": 1704403200.123,
  "state_hash": "e3b0c442...",
  "state_version": 1,
  "intent_issued": "MOVE",
  "confidence": 0.75,
  "policy_source": "HEURISTIC",
  "java_result": {
    "status": "SUCCESS",
    "failure_reason": "NONE",
    "outcomes": {"damage_dealt": 0.0, "is_alive": true}
  },
  "reward_total": 0.1,
  "violations": []
}
```

### BAD ENTRY (Contract Violation)
```json
{
  "timestamp": 1704403201.456,
  "state_hash": "a1b2c3d4...",
  "state_version": 1,
  "intent_issued": "PRIMARY_ATTACK",
  "confidence": 0.8,
  "policy_source": "HEURISTIC",
  "java_result": {
    "status": "SOMETHING_NEW", 
    "outcomes": {}
  },
  "reward_total": 0.0,
  "violations": [
    {"type": "UNKNOWN_ENUM", "severity": "MEDIUM", "field": "status", "value": "SOMETHING_NEW"},
    {"type": "MISSING_FIELD", "severity": "HIGH", "field": "failure_reason"}
  ]
}
```
