# GLOBAL HARDENING PROTOCOL (v1.0.0)

This document defines system-wide hardening requirements to eliminate ambiguity and ensure verifiable safety.

## 1. GLOBAL EXPERIENCE IDENTIFIER
Every cycle MUST be tagged with a unique, immutable `experience_id`.
- **Generation**: Generated once per request by the `AIServer`.
- **Propagation**: MUST be included in:
    - Audit logs
    - Intent results (metadata)
    - Memory buffer entries
    - Incident reports
    - Dataset records

## 2. DATA LINEAGE & TRUST BOUNDARIES
Every experience MUST declare its lineage.

| Field | Description | Allowed Values |
| :--- | :--- | :--- |
| `source` | Origin of the data. | `JAVA_SOURCE`, `SYNTHETIC`, `REPLAY` |
| `trust_boundary` | Security zone. | `EXTERNAL_UNTRUSTED`, `INTERNAL_VERIFIED` |
| `learning_allowed` | Permission to use for training. | `true`, `false` |
| `decision_authority` | Who made the final call. | `HUMAN`, `HEURISTIC`, `ML_MODEL` |

## 3. NORMALIZED INCIDENT SCHEMA
All incidents MUST follow this structure:

| Field | Type | Description |
| :--- | :--- | :--- |
| `incident_id` | UUID | Unique ID. |
| `experience_id`| UUID | Reference to the cycle. |
| `severity` | Enum | `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `type` | Enum | See `failure_manager.py` |
| `details` | String | Contextual information. |

**CRITICAL Trigger**: Any incident with `severity == CRITICAL` MUST trigger a `LOCKDOWN` mode.

## 4. POLICY AUTHORITY LEVELS
Every policy decision MUST be tagged with its authority level:
- `ADVISORY`: Suggestion only (e.g., Shadow models).
- `AUTHORITATIVE`: Standard decision (e.g., Production ML).
- `OVERRIDE`: Safety veto (e.g., Heuristic Guard).

## 5. REWARD CLASSIFICATION
Rewards MUST be tagged to prevent accidental learning from non-learning-applicable signals.
- `DIAGNOSTIC`: For monitoring only.
- `EVALUATIVE`: For model comparison (Shadow/Canary).
- `LEARNING_APPLICABLE`: Allowed to influence model weights.

**Invariant**: If `learning_allowed == false`, no `LEARNING_APPLICABLE` reward may be generated or used.

## 6. EXECUTION MODES
Scripts MUST declare and verify their execution mode:
- `PROD_SERVER`: Live decision making.
- `OFFLINE_TRAINING`: Weight updates only.
- `AUDIT_PROOF`: Read-only verification.

## 7. NON-NEGOTIABLE CONTRACT SUMMARY
- No implicit state or lineage.
- Explicit permission for every learning step.
- Verifiable traceability from result to source.
