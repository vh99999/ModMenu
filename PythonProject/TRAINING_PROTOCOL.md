# OFFLINE TRAINING PROTOCOL (v1.0.0)

This document defines the formal requirements for the offline training pipeline, ensuring reproducibility, stability, and objective evaluation of ML models.

## 1. TRAINING PIPELINE REQUIREMENTS

### A) Determinism & Reproducibility
- **Seeding**: Every training run MUST accept a `random_seed`. This seed MUST be applied to all stochastic components (shuffling, weight initialization, dropout).
- **Environment**: Training MUST NOT depend on any live state or network resources.
- **Fixed Dataset**: Training MUST use a versioned, static dataset snapshot as defined in `DATASET_PROTOCOL.md`.

### B) Process Flow
1. **Loading**: Load the pre-processed dataset from disk.
2. **Splitting**: Apply the temporal splitting rules (80/10/10) with episode-awareness.
3. **Batching**: Use fixed batch sizes. Shuffling is allowed only within the training set and MUST be seeded.
4. **Optimization**: Record the optimizer type, learning rate, and loss function used.
5. **Checkpointing**: Save intermediate models and their associated metrics.

---

## 2. EVALUATION PROTOCOL

Every trained model MUST be evaluated against three baselines:
1. **RANDOM**: A purely stochastic policy.
2. **HEURISTIC**: The hard-coded safety policy.
3. **LEGACY_ML**: The previous "best" version of the model.

### Acceptance Metrics:
| Metric | Definition | Threshold |
| :--- | :--- | :--- |
| `Loss` | Optimization loss (e.g., Cross-Entropy) | MUST be decreasing over epochs. |
| `Validation Accuracy` | Prediction match against HUMAN/EXPERT actions | > 70% (Baseline for Imitation Learning). |
| `Reward Potential` | Estimated reward using the Reward System on Val set | MUST be > HEURISTIC reward. |
| `Regression Check` | Performance drop compared to LEGACY_ML | < 5% drop on core survival metrics. |

---

## 3. MODEL VERSIONING & METADATA

Each `model.pth` (or equivalent) MUST be accompanied by a `model_card.json` containing:
- `version`: Semantic version (e.g., `v1.2.0`).
- `dataset_v_hash`: SHA-256 hash of the training dataset.
- `reward_config_version`: Version of the reward weights used.
- `code_hash`: Git commit or hash of the training script.
- `hyperparameters`: {`batch_size`, `lr`, `epochs`, `seed`, `architecture`}.
- `metrics`: Final results from the Evaluation Protocol.

---

## 4. FAILURE & QUARANTINE CONDITIONS

### Training Aborts if:
- Loss becomes `NaN` or `Inf`.
- Data leakage is detected between Train/Val sets.
- Dataset size is below the minimum threshold (e.g., < 1000 samples).

### Model Rejected if:
- Performance is worse than the `HEURISTIC` baseline on survival metrics.
- Validation accuracy is significantly lower than training accuracy (> 15% gap, indicating overfitting).
- Model fails the sanity check (e.g., suggests `PRIMARY_ATTACK` when `distance_cat == FAR`).

Rejected models MUST be moved to `models/quarantine/` with their failure reason logged.

---

## 5. FORBIDDEN TRAINING PRACTICES

1. **NEVER** use "live" data from the current server session for offline training.
2. **NEVER** evaluate a model on data it has seen during training (even partially).
3. **NEVER** hard-code model weights into the code; always load from versioned artifacts.
4. **NEVER** ignore `NaN` values in the loss function; stop immediately.
5. **NEVER** deploy a model that has not been compared against the `HEURISTIC` baseline.

---

## 6. NON-NEGOTIABLE CONTRACT SUMMARY
- Training is **REPRODUCIBLE**.
- Evaluation is **STRICT**.
- Rejection is **AUTOMATIC**.
- Metadata is **MANDATORY**.
