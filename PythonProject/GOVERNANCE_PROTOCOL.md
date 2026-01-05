# GOVERNANCE PROTOCOL SPECIFICATION (v1.0.0)

This document defines the formal governance requirements for model management, traceability, and operational safety in the AI system.

## 1. VERSION IDENTIFIERS (THE MODEL FINGERPRINT)

Every model MUST have a unique, immutable version identifier string. 
Format: `M-{model_id}-D{training_date}-H{dataset_hash_short}-R{reward_ver}-C{code_hash_short}`

| Component | Description | Example |
| :--- | :--- | :--- |
| `model_id` | Alphanumeric name of the model architecture. | `CombatCNN` |
| `training_date` | YYYYMMDD format. | `20260105` |
| `dataset_hash_short` | First 8 chars of the SHA-256 hash of the training dataset. | `a1b2c3d4` |
| `reward_ver` | Version of the `REWARD_PROTOCOL` used during training. | `1.0.0` |
| `code_hash_short` | First 8 chars of the Git commit/hash of the training code. | `f5e4d3c2` |

Full ID Example: `M-CombatCNN-D20260105-Ha1b2c3d4-R1.0.0-Cf5e4d3c2`

---

## 2. MODEL REGISTRY LIFECYCLE

The `ModelManager` is the authoritative registry.

### A) Registration
- New models MUST be registered with their full metadata.
- Registration involves creating a `model_card.json` in the model directory.
- Metadata MUST match the fingerprint components.

### B) Activation
- A model is NOT active upon registration.
- Activation MUST be an explicit command.
- **Silent Upgrades are FORBIDDEN.** The system must log a `GOVERNANCE_EVENT` when the active model changes.

### C) Deprecation & Archiving
- Deprecated models are kept for rollback but cannot be selected for NEW deployments.
- Archived models are moved to long-term storage and removed from the active registry.

---

## 3. RUNTIME SELECTION & FALLBACK

### A) Selection Logic
1. The `AIServer` starts with a designated `PRIMARY` model version.
2. If the `PRIMARY` fails to load, the system MUST fall back to the `GOLDEN_HEURISTIC`.

### B) Emergency Rollback
- A `ROLLBACK` command MUST instantly revert to the previous `KNOWN_GOOD` version.
- Rollback must be lossless (no state corruption).
- The `AIServer` MUST maintain a `last_good_version` pointer.

---

## 4. AUDIT TRAIL & TRACEABILITY

Every single decision cycle MUST include model provenance in the audit log.

| Audit Field | Requirement |
| :--- | :--- |
| `model_version` | The full fingerprint of the model that provided the suggestion. |
| `arbitration_path` | The path taken through the `PolicyArbitrator` (e.g., `ML_SUGGESTION`, `HEURISTIC_VETO`). |
| `fallback_reason` | If ML was bypassed, the explicit reason (e.g., `LOW_CONFIDENCE`, `TIMEOUT`, `SOFT_DEGRADE`). |

---

## 5. FORBIDDEN GOVERNANCE SHORTCUTS

1. **NEVER** use "latest" or "stable" tags for model selection. Always use full fingerprints.
2. **NEVER** allow Java to suggest or enforce a model version.
3. **NEVER** change model weights in-place on a registered model.
4. **NEVER** delete a model version that has been used in production without an archiving period (Audit integrity).
5. **NEVER** perform a model swap without a heartbeat-confirmed verification cycle.

---

## 6. NON-NEGOTIABLE CONTRACT SUMMARY
- All models are **IMMUTABLE** once registered.
- Every decision is **TRACEABLE** to a specific fingerprint.
- Safety and Heuristics remain the **FINAL AUTHORITY** regardless of model version.
