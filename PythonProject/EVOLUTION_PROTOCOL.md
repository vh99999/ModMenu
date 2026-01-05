# SAFE EVOLUTION PROTOCOL (v1.0.0)

This document defines the formal requirements for model evolution, ensuring that improvements are proven and regressions are prevented.

## 1. EVOLUTION PIPELINE

The evolution of a model follows a strict multi-stage pipeline:

| Stage | Mode | Traffic | Description |
| :--- | :--- | :--- | :--- |
| **STAGING** | Offline | 0% | Model is evaluated on the `TEST` split of the offline dataset. |
| **CANDIDATE** | Shadow | 0% (Decision influence) | Model runs in parallel with Production. Decisions are logged but NOT executed. |
| **CANARY** | A/B | 1-10% | Model executes a small fraction of decisions to verify real-world impact. |
| **PRODUCTION**| Active | 90-100% | Model is the primary decision-maker. |

## 2. ACCEPTANCE CRITERIA

A model MUST pass all criteria at each stage to be promoted.

### Staging -> Candidate (Offline Gate)
- **Loss Stability**: Validation loss must be lower than Heuristic baseline.
- **Accuracy**: Must achieve > 75% agreement with Heuristic/Expert on survival-critical states.
- **Reward Potential**: Predicted reward on test set must be >= current Production model.

### Candidate -> Canary (Shadow Gate)
- **Survival Invariant**: Shadow survival rate MUST be >= Production survival rate over 1000 cycles.
- **Divergence Check**: If shadow intent differs from production, shadow reward delta must be positive in > 50% of cases (estimated).
- **Latency**: 99th percentile inference time MUST be < 40ms.

### Canary -> Production (Active Gate)
- **Regression Check**: Survival rate MUST NOT drop by more than 5% compared to the previous Production model.
- **Improvement**: Mean reward MUST show a statistically significant improvement (> 5%) or non-inferiority with 95% confidence.

## 3. PROMOTION & DEMOTION RULES

- **Atomic Promotion**: Promotion to Production must be a single, logged command.
- **Instant Demotion**: If a Production model breaches a CRITICAL monitoring threshold, it is automatically demoted to `CANDIDATE`, and the system rolls back to the `last_good_version`.
- **No Stale Models**: Models older than 30 days without a performance review are flagged for re-evaluation or archiving.

## 4. DATA HYGIENE

- **Data Inclusion**: Only experiences passing `DataQualityGate` are used for training.
- **Stale Data Handling**: Training datasets must not include data older than 3 major `state_version` increments.
- **Bias Detection**: Intent distribution in training data must be checked for "Collapse" (single intent > 90%).

## 5. ANTI-REGRESSION CHECKLIST

Before promoting to Production, the operator MUST verify:
- [ ] Model fingerprint matches the `CANDIDATE` version.
- [ ] Survival rate invariant has been maintained for the last 10,000 cycles in Shadow mode.
- [ ] No `CRITICAL` audit violations were triggered by the model in Shadow mode.
- [ ] Rollback target (current Production) is healthy.

## 6. FORBIDDEN EVOLUTION PRACTICES

1. **NEVER** promote a model that has not completed the `CANDIDATE` (Shadow) phase.
2. **NEVER** allow a model to stay in Production if it performs worse than the `HEURISTIC` baseline.
3. **NEVER** skip evaluation windows to "speed up" deployment.
4. **NEVER** reuse a `model_id` for different architectures or datasets; use strict versioning.
5. **NEVER** allow Java to influence the promotion/demotion decision.

## 7. PROMOTION CONTRACT

Every promotion event MUST be logged with:
- `timestamp`
- `old_production_version`
- `new_production_version`
- `candidate_metrics` (Survival, Reward, Latency)
- `promotion_reason`
- `operator_id` (or SYSTEM)
