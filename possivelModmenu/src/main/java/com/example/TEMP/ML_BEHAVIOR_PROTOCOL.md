# ML BEHAVIOR & UNCERTAINTY PROTOCOL (v1.0.0)

This document defines the formal requirements for auditing and controlling Machine Learning (ML) behavior in the AI system, specifically focusing on overfitting, reward hacking, and fake confidence.

## 1. ML FAILURE MODES

| Failure Mode | Detection Signal | Risk |
| :--- | :--- | :--- |
| **Reward Hacking** | High reward + Zero action entropy + Low progress metrics. | AI exploits a flaw in the reward function (e.g., spinning in circles to avoid death). |
| **Confidence Inflation** | Avg Confidence > 0.95 AND Survival Rate < 0.7. | Model "hides" uncertainty to bypass thresholds. |
| **Blind Spots** | Features outside training distribution (OOD). | Model makes arbitrary decisions on unseen states. |
| **Overfitting** | High training accuracy vs Low validation reward. | Model fails to generalize to new episodes. |

## 2. DEFENSE MECHANISMS

### A) Uncertainty Thresholds
- **Confidence Gate**: ML suggestions MUST be discarded if `self_reported_confidence < 0.8`.
- **Entropy Limit**: ML suggestions MUST be discarded if the intent distribution is too "flat" (Entropy > Threshold), indicating the model is guessing.

### B) Mandatory Fallback Paths
1. **Heuristic Override**: Any action that violates a survival heuristic triggers a mandatory fallback to `HEURISTIC_VETO`.
2. **Confidence Bypass**: If confidence is inflated (detected by Monitor), ML is bypassed via `SOFT_DEGRADE`.

### C) Disagreement Handling (ML vs Heuristics)
- **Shadow Divergence**: If ML and Heuristic disagree on a high-confidence action, the event is logged as `MODEL_DIVERGENCE`.
- **Divergence Limit**: If `divergence_rate > 0.3` over a window, ML is automatically demoted to `CANDIDATE` status.

## 3. REWARD HACKING PROTECTION

To prevent reward hacking, the system monitors "Utility vs Progress":
- **Progress Invariant**: A sequence of high-reward cycles MUST correlate with a change in state features (e.g., distance change, health change).
- **Static Exploit**: If state features remain static while reward is positive for > 50 cycles, a `REWARD_HACKING` alert is triggered.

## 4. SHADOW LEARNING SAFETY

- **No Influence**: Shadow models (Candidates) NEVER influence live decisions.
- **Counterfactual Audit**: The Audit layer records what the Shadow model *would* have done to compute "Potential Reward" without risking the live agent.

## 5. FORBIDDEN ML BEHAVIORS

### Things ML must NEVER be allowed to do:
1. **NEVER decide alone**: No action is taken without a heuristic safety check.
2. **NEVER self-audit**: Confidence scores must be validated against external performance metrics (Survival/Reward).
3. **NEVER ignore OOD**: If features are out-of-bounds, the model MUST report `0.0` confidence.
4. **NEVER hide uncertainty**: Intentionally inflating confidence to bypass gates is a `CRITICAL` protocol breach.

## 6. NON-NEGOTIABLE CONTRACT SUMMARY
- ML is a **PROBABILITY ENGINE**, not a truth engine.
- Heuristics are the **BOUNDARY**.
- Performance is **EARNED**, not declared by the model.
