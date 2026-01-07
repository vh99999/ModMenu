# ML POLICY PROTOCOL SPECIFICATION (v1.0.0)

This document defines the formal contract for integrating Machine Learning (ML) models into the hardened AI architecture. 

## 1. ML POLICY INTERFACE

All ML-based policies MUST implement the following interface:

- **Input**: A fixed-length feature vector as defined in `DATASET_PROTOCOL.md` (length 4).
- **Output**: 
    - `intent_probabilities`: A probability distribution (float array) over all canonical intents.
    - `self_reported_confidence`: A scalar float [0.0, 1.0] representing the model's certainty.
- **Invariance**: The policy MUST be fully deterministic during inference (i.e., same feature vector always produces the same output).

## 2. MODEL LIFECYCLE MANAGEMENT

Models are managed by a dedicated `ModelManager`.

### Lifecycle States:
1. **LOADING**: Reading model weights and metadata from disk.
2. **VALIDATING**: Verifying model signature and version compatibility.
3. **ACTIVE**: Model is currently available for suggestions.
4. **ROLLBACK**: Reverting to a previously known-good version if errors occur.

### Metadata Requirements:
Each model MUST be accompanied by a `metadata.json` containing:
- `model_version`: Semantic version (e.g., "1.0.2").
- `training_dataset_hash`: Hash of the data used for training.
- `input_schema_version`: Must match `STATE_PROTOCOL.CURRENT_VERSION`.
- `intent_order`: The exact ordering of intents in the output layer.

## 3. ARBITRATION DECISION FLOW

The system NEVER allows ML to act directly. All suggestions pass through an Arbitrator.

### Step-by-Step Decision Logic:
1. **VETO Check**: If a safety rule (Heuristic) detects a critical condition (e.g., `is_threatened` and `distance_cat == CLOSE`), the ML suggestion is ignored and the Heuristic vetoes with its action.
2. **BLIND SPOT Check**: If features are Out-of-Distribution (OOD), the model MUST report `0.0` confidence, triggering an automatic fallback.
3. **ENTROPY Check**: If the intent probability distribution is too uniform (Entropy > 1.5), the model is considered to be "guessing" and is bypassed.
4. **CONFIDENCE Threshold**: 
    - If `ML_confidence >= ML_THRESHOLD` (default 0.8), ML suggestion is considered.
    - If `ML_confidence < ML_THRESHOLD`, the system falls back to the **HEURISTIC** policy.
5. **HEURISTIC Fallback**: If Heuristic policy is also uncertain (low confidence), fallback to **RANDOM** policy.
6. **AUDIT HOOK**: Every arbitration decision (including which policy was chosen and why) MUST be logged by the Audit layer.

## 4. KILL-SWITCH DESIGN

A hardware-style "Kill-Switch" is implemented in the `AIServer`.
- **Function**: Instantly sets `ML_ENABLED = False`.
- **Effect**: All ML suggestions are bypassed; the Arbitrator skips the ML evaluation step entirely.
- **Safety**: Rollback is immediate. No residual ML state can influence the next decision.

## 5. FORBIDDEN ASSUMPTIONS

ML Policies are NEVER allowed to assume:
1. That the input feature vector is perfectly accurate (it may be a clamped default).
2. That their suggested intent will be executed by Java.
3. That a high confidence score guarantees a high reward.
4. That they can maintain state between different inference calls.
5. That they have access to the full `raw_state` (they only see the `normalized` feature vector).

## 6. NON-NEGOTIABLE CONTRACT SUMMARY
- ML is a **SUGGESTION ENGINE** only.
- Heuristics are **GUARDIANS**.
- The Arbitrator is the **FINAL JUDGE**.
- Safety over performance, always.
