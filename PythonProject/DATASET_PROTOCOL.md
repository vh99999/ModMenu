# DATASET & ML PIPELINE PROTOCOL (v1.0.0)

This document defines the formal requirements for transforming raw cycles into training datasets for ML policies.

## 1. EXPERIENCE FILTERING (THE GATEKEEPER)

The dataset pipeline MUST NOT accept any experience that fails the `DataQualityGate`.

### Inclusion Criteria:
- **Audit Pass**: `DataQualityGate.is_pass(entry)` must be TRUE.
- **State Integrity**: `state_version` matches the current supported range.
- **Intent Validity**: Intent MUST be one of the canonical intents defined in `INTENT_PROTOCOL.md`.
- **Result Determinism**: Execution result must be valid and conform to the schema.
- **Reward Stability**: Reward MUST be within the bounded range [-2.0, 2.0].

### Rejection Reasons (Hard Blockers):
- `CRITICAL` or `HIGH` severity violations in the audit trail.
- `NaN` or `Inf` found in normalized state or reward (even after parser attempt).
- `controller` is `UNKNOWN`.
- Missing `reward_breakdown`.

---

## 2. DATASET SCHEMA (ML-AGNOSTIC)

Every experience in the dataset is represented as a structured record.

### A) Feature Vector (Inputs)
| Index | Feature | Type | Range | Description |
| :--- | :--- | :--- | :--- | :--- |
| 0 | `health` | float | [0, 1] | Normalized health. |
| 1 | `energy` | float | [0, 1] | Normalized energy. |
| 2 | `target_distance` | float | [0, 1] | Normalized distance (0 to 1000m). |
| 3 | `is_colliding` | float | {0, 1} | Boolean collision flag. |

### B) Labels (Outputs)
- `intent_index`: Integer index of the intent in the canonical list.
- `intent_onehot`: One-hot encoded vector of the intent.

### C) Metadata & Audit
- `timestamp`: Unix timestamp of the cycle.
- `audit_hash`: SHA-256 hash to reference the original audit log.
- `reward`: The scalar reward assigned.
- `policy_source`: The policy that generated the action.
- `controller`: The active controller (HUMAN/HEURISTIC/AI).

---

## 3. DATA NORMALIZATION GUARANTEES

- **Stability**: Feature vectors MUST be generated using the `StateParser._normalize` method to ensure consistency across versions.
- **Intent Encoding**: Intent indices MUST follow the alphabetical order of the `Intent` Enum values.
- **Zero-Centering**: No internal zero-centering in the pipeline; ML models handle their own normalization layers if needed, but the data source is [0, 1] bounded.

---

## 4. SANITY CHECKS & METRICS

Before a dataset is finalized, it MUST pass these checks:

| Check | Threshold | Severity |
| :--- | :--- | :--- |
| `Intent Imbalance` | No intent > 80% total | WARNING |
| `Reward Skew` | Mean Reward < -1.5 or > 1.5 | WARNING |
| `Zero-Reward Ratio`| > 90% of samples | WARNING (Potential sparse reward issue) |
| `Duplicate State Hash` | > 10% duplicates | WARNING (Potential sampling bias) |
| `Temporal Correlation`| Autocorrelation > 0.9 | WARNING (Over-sampling same episodes) |

---

## 5. DATASET SPLITTING RULES

- **Temporal Separation**: Data MUST be split chronologically to prevent future-state leakage. 
  - `TRAIN`: Oldest 80%
  - `VAL`: Next 10%
  - `TEST`: Newest 10%
- **Reproducibility**: Splitting must use a fixed seed for any stochastic operations (though temporal split is deterministic).
- **Anti-Leakage**: Sequences from the same "episode" (determined by session/death) must stay within the same split.

---

## 6. FORBIDDEN TRAINING DATA PRACTICES

1. **NEVER** include experiences with `CRITICAL` audit violations.
2. **NEVER** include data where the `controller` and `intent_taken` are mismatched in the audit record.
3. **NEVER** "balance" the dataset by duplicating rare failing intents (upsampling the signal of a bug).
4. **NEVER** include data from multiple major `state_version`s in the same training tensor without a version-indicator feature.
5. **NEVER** mask or drop "boring" `STOP` intents if they were legitimate policy decisions.
